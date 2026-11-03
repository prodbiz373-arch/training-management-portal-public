# DIFF-31 Flyway V5 開発DB適用結果（2026-10-18）

## 1. 結果・実施範囲

ユーザーの適用承認に従い、127.0.0.1:5432/training_portalのtest1へV4・V5、publicへV5を通常適用した。両スキーマのV1〜V5はsuccess=true、Flyway validate成功、pendingなし。既存の全業務テーブルのデータ・シーケンス・既存制約・索引・所有者・ACLは比較で不変だった。既存社員6件のauth_versionはすべて0。

通常のapplication.propertiesとtarget=2を維持してSpring Bootの起動も成功した。起動時のFlywayは5件validate成功、publicの現在バージョン5、追加migrationなし。検証用アプリは正常停止済み。開発用PostgreSQL自体は停止していない。

今回のリポジトリ追加は本報告書のみ。V1〜V5、通常設定、既存ランナー、アプリ実装は変更していない。既存未コミット変更を保持し、commit/push/mergeは行っていない。

## 2. 適用前のDB状態

- develop、HEAD d63ebbaa。
- PostgreSQL17.11、127.0.0.1:5432、training_portal、training_portal_userを照合した。
- publicの履歴はV1〜V4、test1はV1〜V3で、全件成功・チェックサム一致。
- 両方のkenshu_memberは11カラムでauth_versionなし。所有者はtraining_portal_user。
- 社員件数はpublic 6件、test1 0件。
- 適用前に他のclient backend接続0、prepared transaction0、DB内の未取得ロック0を確認した。各適用の前にも他接続がないことを確認した。
- 移行用プロセス以外のDB接続は確認されなかった。既存の別プロセスを勝手に停止していない。
- 移行SQLは準備時のV1〜V5のSHA-256と照合し、各適用前・最終監査でも不変を確認した。

## 3. バックアップ・新規隔離DBへの復元試験

使用バックアップ：

`${PRIVATE_BACKUP_DIR}/training_portal-public-test1.dump`

- サイズ96,905バイト、custom形式。
- SHA-256：`9fc37acaccd54636f4d4f817ff1ee479954ad23b01a7d29ee9851e810be3f266`、取得済み値と一致。
- public/test1の両方を含み、所有者・ACLを省略していない。

新規の専用PostgreSQLクラスタを${NON_DISTRIBUTED_EVIDENCE_DIR}/archived-run-2へ作成した。待受は127.0.0.1の空きポート、専用UNIXソケット、開発DBとは別データ領域。Dockerは新規構築していない。新クラスタだけにtraining_portal_userとtraining_portalを作成し、開発用DBパスワードを隔離ロールに転用しなかった。

pg_restoreは--exit-on-error --single-transactionを使用した。標準publicスキーマの重複だけをTOCのSCHEMA publicエントリ除外で回避し、publicのコメントとpg_database_owner所有は維持した。--no-owner、--no-privileges、cleanは使用していない。復元エラーなし。

復元後、public/test1の各9テーブル（業務8＋Flyway履歴）の以下を開発DBの適用前スナップショットと比較し、一致した。

- 全行の内容と件数、Flyway履歴の全カラム。
- カラム型・NOT NULL・default・コメント・カラムACL。
- 制約、索引、テーブル所有者・ACL・コメント、スキーマ所有者・ACL。
- シーケンスの状態。

行はJSONの全行を安定順序で取得し、列型を別途保存した。NULLと文字列、重複行を維持して比較した。件数だけで一致とは判定していない。

復元成功後、開発DBを再取得して最初のスナップショットと完全一致することを確認してから適用に進んだ。隔離クラスタは試験後にfast shutdownで正常停止し、データ・ログは保持した。バックアップは削除していない。

## 4. test1の適用結果

接続先のcurrentSchema、Flyway schemas/defaultSchemaをすべてtest1に固定した別Javaプロセスを使用した。

- Flyway12.4.0。
- target=5、skipExecutingMigrations=false。
- 適用直前pending=[4,5]。
- V4通常実行：test1のNULL許可を確認する処理。既存業務テーブルのDDL変更なし。
- V5通常実行：test1.kenshu_memberへauth_versionを追加。
- migrationsExecuted=2、validate成功、pending=[]。
- test1検証完了時点でpublicのデータ・構造・履歴は完全に不変。

test1の比較成功を確認してからpublicを実行した。

## 5. publicの適用結果

接続先のcurrentSchema、Flyway schemas/defaultSchemaをすべてpublicに固定した別Javaプロセスを使用した。

- Flyway12.4.0。
- target=5、skipExecutingMigrations=false。
- 適用直前pending=[5]。
- V5通常実行、migrationsExecuted=1。
- validate成功、pending=[]。
- V3省略登録は再実施せず、V4も再適用していない。
- public適用時点で、適用済みtest1に追加差分なし。

## 6. 最終履歴・構造・データ比較

| version | checksum | public | test1 |
| --- | --- | --- | --- |
| 1 | 1122476101 | success=true・既存行不変 | success=true・既存行不変 |
| 2 | 463837306 | success=true・既存行不変 | success=true・既存行不変 |
| 3 | -676549352 | success=true・既存行不変 | success=true・既存行不変 |
| 4 | 1630550406 | success=true・既存行不変 | success=true・今回追加 |
| 5 | 1806942905 | success=true・今回追加 | success=true・今回追加 |

V5ファイルSHA-256：`a95cc12eaeb1714b6cc509cba81caff521a94a41b43a3d9ba895f78f7dd79307`。整数のFlywayチェックサムとは別の値。

両スキーマで確認したV5追加内容：

- auth_version BIGINT、NOT NULL、DEFAULT 0。
- ck_kenshu_member_auth_version：CHECK ((auth_version >= 0))。
- カラムコメント「認証世代番号」。
- publicの既存社員6件は全員0。test1は社員0件のため実レコードの初期値確認対象なし。default・制約の定義は確認済み。

比較では追加したauth_version列（値0）、当該CHECK、想定するFlyway履歴追加だけを許可した。それ以外の全スナップショットを一致確認した。

- 既存11カラムの社員情報、パスワードハッシュ、社員区分、更新カウンタ・監査値：不変。
- スケジュール、権限・ピン、分類、日別内容、ステータスマスタ、提出状況、コメントの全行：不変。
- シーケンス：不変。
- 既存主キー・外部キー・CHECK・索引・所有者・ACL・コメント：不変。
- 既存Flyway履歴はinstalled_on等を含む全カラム不変。
- test1適用時の対象外public、public適用時の対象外test1：不変。

最終の別プロセスで両スキーマのvalidate成功・pendingなしを確認した。target=2でのvalidate・pendingなしも両方成功した。

## 7. 通常設定でのアプリ起動確認

最新の未コミット実装を含むJARを./mvnw -DskipTests packageで作成し、成功した。通常application.propertiesのDB設定、publicスキーマ、Flyway target=2をそのまま使用した。テストプロファイルやDB接続先・schema・Flyway設定の上書きは行っていない。競合防止のためHTTP待受だけ127.0.0.1:62616へ指定した。

起動ログ：

- Successfully validated 5 migrations。
- Current version of schema public: 5。
- Schema public is up to date. No migration necessary。
- Started TrainingPortalApplication。

HTTP確認：

| API | 結果 |
| --- | --- |
| GET /api/auth/csrf | 200 |
| GET /api/auth/me（未認証） | 401 |
| GET /api/members（未認証） | 401 |
| GET /api/schedules（未認証） | 401 |
| GET /api/progress（未認証） | 401 |
| GET /api/home（未認証） | 401 |

確認後は検証用アプリだけをSIGTERMで停止し、Graceful shutdown complete、HikariPool Shutdown completedを確認した。起動後に再度全スナップショットを比較し、migration直後から追加変更がないことを確認した。

今回は実社員のパスワードを新規設定・推測したり、既存業務データを更新したりしていない。ログイン後のCRUD、区分変更、画面操作を開発DBで実行したわけではない。認証・メンバー・スケジュール・Progress・Homeの機能回帰は、同じ実装を使った直前の隔離検証でバックエンド190件、フロント211件成功済みだが、今回の適用作業で再実行した件数ではない。

## 8. 補助処理の停止・原因と対応

開発DB更新前に次の2点を検出した。

1. 新規の一時ランナーでVersionPrinterのパッケージ指定を誤り、コンパイル停止。既存ランナーの参照先に合わせて修正し、再コンパイル成功。アプリ・既存ランナー・SQLは変更していない。
2. 適用前のvalidateがV4・V5をRESOLVED_VERSIONED_MIGRATION_NOT_APPLIEDとして拒否した。読み取り専用調査で履歴とauth_version未追加を確認し、DB更新がないことを確認した。既存履歴やチェックサムの不一致ではなかった。適用前だけ未適用のmigrationを許容し、pendingの完全一致を別途必須にした。適用後はpendingが0件であることとvalidate成功を確認した。

いずれも開発DBへのDDL前に停止した。後続を無条件に実行せず原因と状態を確認してから、承認済みの適用を再開した。修復・履歴削除・自動復元は行っていない。

## 9. 証跡・残る確認事項

証跡：${NON_DISTRIBUTED_EVIDENCE_DIR}/archived-run-3

- before.json、restored.json：適用前・隔離復元比較。
- after-test1.json、after-public.json：各適用直後。
- after-startup.json、final-summary.json、success.json：最終監査。
- test1/publicのapply/checkログ。
- normal-startup.log、normal-startup-result.json。
- 隔離クラスタのデータ・postgres.log・restore.list。

スナップショットは実データ・パスワードハッシュを含むため、権限を制限した一時領域へ保存し、Gitには追加していない。認証パスワード、Cookie、トークンをログや報告書へ出力していない。一時領域の長期保存は保証されない。元バックアップはユーザー領域で保持している。

残る確認は実利用者によるログイン後の操作確認。本人区分変更による再ログイン、複数端末の旧セッション拒否、Home/Progress/スケジュール表示を、既存データを不用意に変更しない手順で確認する。今回、業務データ更新を伴う実社員の区分変更やCRUDは未実施。

新旧アプリを混在させると旧実装は認証世代を照合・増加しないため、DIFF-31の保証が成立しない。通常利用再開時は新コードへ統一する。認証世代の値を巻き戻したりV5の履歴を消したりしない。通常target=2のまま起動可能であることは今回実測した。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
