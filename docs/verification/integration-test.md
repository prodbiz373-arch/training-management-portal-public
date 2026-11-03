# スケジュール・進捗・ホーム結合テスト

## 1. 対象・目的

スケジュール登録、進捗・課題更新、ホーム集計の連携を、実PostgreSQLとSpring Boot MockMvcで確認する。既存の個別API試験・フロントエンド試験を再利用し、不足していた横断フロー1件だけを追加した。アプリケーション本体・API仕様・設計書は変更していない。

## 2. 作業前確認・環境

- ブランチdevelop、HEAD `d63ebbaa177c52d48d2160c07faf787ad3951b15`、件名`feat: integrate home progress and schedule enhancements`を確認。
- 開始時の未コミット変更は`frontend/next-env.d.ts`とDocker関連の未追跡資料・補助スクリプト。変更・削除していない。
- 基本詳細設計書xlsx（39シート）、テーブル定義書xlsx（10シート）、schedules/progress/homeのAPI仕様、既存Controller/Service/Repositoryとテストを確認。テーブル定義書の実ファイル名はUnicode分解形の「テーブル定義書.xlsx」で、指定資料と同一名称として参照した。
- Dockerはユーザー承認後の読み取り確認でもdaemon socket不在。Docker起動・再構築は行っていない。
- 既存PostgreSQL 17.11バイナリで新規専用クラスタを作成。`127.0.0.1:49895`のみで待ち受け、専用ソケット・専用データ領域を使用。通常の5432へは接続していない。
- 隔離領域：`${NON_DISTRIBUTED_EVIDENCE_DIR}/archived-run-1`。専用ロールtraining_portal_user、DB training_portal、schema test1。隔離用ランダム資格情報を生成し、開発DBの資格情報を利用していない。
- 権限制限された既存バックアップのSHA-256 `9157826593804e0c20b1b4de1c4ab08dcfcb398bafc6886fb9b4b163649fbdde`を照合し、test1だけを復元。元バックアップは変更していない。
- 最初の復元は`--schema=test1`でSCHEMA作成項目が含まれず停止。single-transactionでロールバックされた。同じ新規隔離クラスタでtest1を明示作成して復元し直した。別の環境は増やしていない。
- 試験前に接続先training_portal/test1・ポート49895、test1履歴V1/V2/V3と期待チェックサム、隔離publicのテーブル・シーケンス0を確認。
- Flywayは`-Dspring.flyway.enabled=false`で無効化。`spring.flyway.target`は変更していない。Flywayを呼ぶIssueStatusMasterMigrationTestの2件は明示除外。

バックエンド実行条件：

```text
./mvnw clean test
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:49895/training_portal?currentSchema=test1
  -Dspring.flyway.enabled=false
  -Dtest=*,!IssueStatusMasterMigrationTest
```

パスワードは隔離専用の値を環境変数へ設定し、引数・報告書へ記載していない。新規横断テストにはINTEGRATION_TEST_PORTによる明示的な有効化条件、5432拒否、DB/schema/port照合を追加。クラス自体もFlyway無効化を指定する。通常実行ではこの1件をスキップし、開発DBへの書き込みを防ぐ。実フロー成功後にこの安全指定を追加し、環境変数未設定で1件スキップ・Springコンテキスト未起動を確認した。

## 3. ケース・期待結果・実際の結果

| 領域 | 期待結果 | 実際・判定 | 根拠 |
| --- | --- | --- | --- |
| スケジュール登録→進捗 | 課題ONだけが対象、登録新人・講師が参照可能 | 成功。通常研修は対象外、新人1件・講師2件 | 新規横断テスト |
| 提出状況なし | 区分1・未提出として返し、GETでINSERTしない | 成功。提出状況0件をDB照会で確認 | 新規横断テスト、ProgressApiTest |
| 進捗更新→Home | 完了の保存が本人・他新人・講師の範囲に応じ集計される | 成功。本人完了1、他新人未提出1、講師未提出1/完了1 | 新規横断テスト |
| コメントCRUD | 登録・取得・編集・本人削除が反映される | 成功。編集本文、削除後コメント0件 | 新規横断テスト、ProgressApiTest |
| 他人のコメント削除 | 編集権限のある講師でも403、データ保持 | 成功 | 新規横断テスト、ProgressApiTest |
| 解除A：全員未提出・コメントなし | 解除可能 | 成功。進捗0件、Home課題0件、空欄内容はDB NULL | 新規横断テスト、ScheduleApiTest |
| 解除B/C：非未提出またはコメントあり | 400 INVALID_INPUT、変更なし | 成功。完了＋コメント、未提出＋コメントを横断試験で確認。非未提出のみも既存パラメータ試験で確認 | 同上 |
| 解除D/E/F/G | レコードなしは未提出。空欄化・直接APIでも保護し、失敗時に関連データを保持 | 成功。詳細GET全体と提出状況・コメントのDB行を比較。NULLフラグ・行省略も既存試験で確認 | 同上 |
| 権限・レビュー者 | 新人本人範囲、講師の許可範囲、編集不可拒否、候補は編集権限者 | 成功 | ProgressApiTest、HomeApiTest、ScheduleApiTest |
| データ整合・エラー | 不正入力、存在なし、更新競合、未認証、CSRFを拒否し部分更新しない | 成功 | ScheduleApiTest、ProgressApiTest、AuthApiTest |
| セッション | ログアウトでセッション無効化、CSRF再取得、401処理 | 成功。実時間のアイドル期限切れ待ちは未実施 | AuthApiTest、フロント既存401試験 |
| フロントAPI・画面 | Cookie/CSRF・URL連携、表示範囲、編集制御、課題解除ポップアップ | 成功。fetchをモックしたコンポーネント/APIクライアント試験 | frontend/tests |
| 実ブラウザE2E | 実HTTP・セッション・画面遷移を一連で操作 | 未実施 | E2E構成・ブラウザ自動化ツールなし。既存テストで代替成功とは扱わない |

## 4. 実行結果

- バックエンド156件成功、失敗0、エラー0。既存155件＋横断1件。内訳：Schedule60、Progress16、Home8、Auth17、SessionCookie1、Member Controller24/Service23/Persistence2、休日3、context1、横断1。
- Flyway試験2件は未実施（実行対象から除外。SurefireのSkipped=0とは区別する）。
- フロントエンド14ファイル190件成功、失敗0。
- TypeScript型チェック、lint成功。
- Webpack buildは失敗。最初はGoogle Fonts取得制限、通信許可後はnext/font内部のTypeError。成功扱いしない。
- git diff --check成功。V1〜V4・通常/テストapplication.properties・既存未コミットnext-env.d.tsはSHA-256不変。
- 安全指定を加えた新規テストの通常実行確認：1件中スキップ1、失敗0。これは上記156件の実フロー成功に加算しない。

ログ：

- `${NON_DISTRIBUTED_EVIDENCE_DIR}/backend.log`
- `${NON_DISTRIBUTED_EVIDENCE_DIR}/integration-frontend-tests.log`
- `${NON_DISTRIBUTED_EVIDENCE_DIR}/integration-typecheck.log`
- `${NON_DISTRIBUTED_EVIDENCE_DIR}/integration-lint.log`
- `${NON_DISTRIBUTED_EVIDENCE_DIR}/integration-webpack-build.log`
- `${NON_DISTRIBUTED_EVIDENCE_DIR}/integration-opt-in-test.log`

一時領域のログは長期保管を保証しない。DB資格情報・データダンプはGitへ追加しない。

## 5. 発見事項・仕様差異

実行した業務API試験で新しい機能不具合は検出しなかった。ただし以下は未解決。

### Webpackビルド障害（優先度P2：ビルド確認を阻害）

- 再現：既存の未コミットnext-env.d.tsを保護するためfrontendを一時コピーし、同じnode_modulesで`pnpm exec next build --webpack`を実行。
- 期待：終了コード0で本番成果物を生成。
- 実際：app/layout.tsxのnext/fontで`TypeError: Cannot read properties of null (reading '1')`。
- 直接原因：Next16.3.3のGoogle font loaderがフォントURL末尾の拡張子正規表現にマッチしないまま`[1]`を参照（google/loader.js:122）。
- 原因候補：取得されたGoogle Fonts CSSのURL形式とloaderの前提の不一致。具体的な該当URLと再現条件は未確定。業務API不具合やTurbopackポート障害とは区別する。
- 修正案：取得CSSのURL形式と対応Nextバージョンを調査し、依存更新またはフォント配信方法変更の必要性を別途判断する。本体・依存関係は今回変更していない。
- 影響：本番フロントエンドビルドの完了確認ができない。実行済みコンポーネント/API試験は成功。

### 設計書と確定済みAPI・実装の差異（優先度P2：仕様文書の整合）

| 設計書位置 | 設計書 | 現API・実装 | 対応案 |
| --- | --- | --- | --- |
| 基本詳細設計書 PRM_画面編集1 E10 | レビュー者は講師社員のみ | 対象スケジュールの編集権限者、社員区分で限定しない | 確定仕様と設計書の整合を確認 |
| PRM_画面編集1 D15／PRM_イベント内容 E16 | 本人または編集権限者・対象閲覧権限によるコメント削除 | 最新指示/APIに従い投稿者本人だけ | 設計書へ本人限定条件を反映するか確認 |
| SCD_画面編集2 D10 | スケジュール名は新規・編集とも可 | 詳細画面では基本情報readonly、PUT対象外 | 意図を確認し、設計書または実装の修正を別途判断 |
| テーブル定義書 スケジュール詳細 R10 | schedule_content NOT NULL | 承認済みV3/V4対応後はVARCHAR(50) NULL許可 | 承認済み変更の定義書反映を確認 |

いずれも本体を勝手に修正していない。課題解除制御は最新の確定API仕様を試験の期待値とした。

## 6. DBへの影響・終了状態

- 開発用localhost:5432のtraining_portal/public・test1、既存検証DBへ接続していない。専用ポート49895以外に書き込みを実行していない。
- 隔離test1へ復元・テストデータ書き込みを実施。各DB統合試験は@Transactionalでロールバック。課題解除拒否などの要求内の不変性は個別に検証した。
- 試験前の隔離DBダンプSHA-256は`127d258b388c8339db4739e3ce8765150d90790fc35de3cfa620c161d80febca`。
- 試験後のダンプ取得はユーザー承認が得られず未実施。隔離DB全体・シーケンス・Flyway履歴の最終ダンプ前後一致は未確認であり、推測で一致とはしない。
- Flyway自動実行を無効化し、Flywayを直接実行する試験も除外。migration/repair/baseline/cleanは実行していない。target設定も変更していない。
- 新規隔離PostgreSQLのみpg_ctl fastで正常停止済み。専用データ・ログを保持。既存DB・Docker環境の停止・削除は行っていない。

## 7. 手動確認・総合評価

API→実DB→別APIの主業務フローと、既存のフロントAPI連携試験は成功。ブラウザから実サーバーを通した完全E2E、本番ビルド、隔離DB全体の最終ダンプ比較は未完了であり、結合確認すべて完了とはしない。

未実施はFlyway試験2件、実ブラウザE2E一式、最終ダンプ比較。実時間のセッション期限切れ・同時更新負荷は既存の無効セッション/古いupdateCount試験と別で、手動確認対象。

次はビルド障害の切り分けと設計差異の判断を行い、その後に実ブラウザでログイン→スケジュール登録→進捗更新→Home再表示、コメント操作、課題解除時の色・フォーカス、セッション期限切れを確認する。

今回の追加は新規横断テストと本報告のみ。既存未コミットファイル、アプリ本体、Flyway SQL、設定ファイルは変更していない。commit/push/merge未実施。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
