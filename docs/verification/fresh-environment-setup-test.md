# 新規環境構築ランナー・セットアップ検証（2026-10-18）

## 1. 結果・確認範囲

作業ツリー内の新規ランナー・既存V1〜V5・JAR・初期講師コマンド・手順を使い、独立した空のPostgreSQLクラスタから構築を確認した。publicはV1/V2通常→V3省略登録→V4/V5通常、test1は別の空DBでV1〜V5通常適用に成功。実端末相当のPTYによる初期講師登録、通常target=2のSpring Boot起動、NextのWebpack build/start、実headless Chromeでのログインと4画面表示まで確認した。

ただし、Git cloneそのものや完全に空のMaven/pnpmキャッシュからの依存取得は今回実施していない。既存依存キャッシュ・インストール済みJava/PostgreSQL/Node/Chromeを使用した。「第三者の全OS/完全クリーン端末で確認済み」とは判定しない。未追跡の新規ファイルは公開対象へ含める必要がある。

developで作業。既存の未コミット変更を保持し、既存V4ツール・V1〜V5・通常設定・Excel・既存業務処理は変更していない。開発DBの5432/public/test1へ接続していない。外部公開/クラウド配置/commit/push/mergeなし。

## 2. 追加・変更ファイル

追加：

- src/main/java/com/training/portal/setup/FreshDatabaseCommand.java
- src/test/java/com/training/portal/setup/FreshDatabaseCommandTest.java
- src/test/java/com/training/portal/setup/FreshDatabaseCommandIntegrationTest.java
- tools/flyway/test-fresh-database.py
- tools/flyway/fresh-browser-check.cjs
- docs/setup/fresh-environment.md
- 本報告書

更新：

- README.md：新規構築の入口、現行のAPI/DB接続範囲、初期講師・移行手順へのリンク。
- frontend/README.md：旧モック/任意パスワードの説明を現在の認証・API・起動方法へ整理。
- docs/implementation/frontend-auth.md：メンバー等が未接続という旧説明を整理、初期講師/DIFF-31/新規DB導線を追記。
- docs/setup/initial-instructor-setup.md：新しいMigration手順へリンク。

pom.xmlには追加依存なし。既存Boot JARのPropertiesLauncherから別mainを明示実行する。通常mainは従来どおりで、自動実行するBean/HTTP APIは追加していない。

## 3. 新規構築ランナーの仕組み

引数でhost/port/database/schema/user/migrationsを必須とする。hostは127.0.0.1、portは1024〜65535の非5432、schemaはpublic/test1、userはtraining_portal_user。DB名は安全な識別子に制限しpostgres/template DBを拒否する。未指定・重複引数・余計な引数・非対話環境も拒否する。

接続先文字列の再入力とDBパスワードの非表示入力を要求。平文パスワードのコマンド引数・Git設定ファイルへの保存はない。Flyway/DB例外は固定の中止メッセージにし、資格情報や内部例外を出さない。char[]は消去するがJDBC用Stringの完全なメモリ消去までは保証しない。

DB接続前に正確なV1〜V5の5ファイルとSHA-256を確認し、検証済みバイトを専用一時ディレクトリへコピーして、そのコピーだけを適用する。実行時Flyway12.4.0も照合する。V5 checksumは1806942905。

接続ごとに実サーバーのアドレス・ポート、DB名・現在ユーザー・current_schema、PostgreSQL17、DB/スキーマ所有権、非superuser/非CREATEDB/非CREATEROLE/非REPLICATIONを確認する。スキーマの自動作成はしない。

事前検証は**DB全体**のユーザーリレーション・関数・型・追加Extension・想定外スキーマ・既存Flyway履歴・他の接続・prepared transactionを確認する。対象スキーマだけ空でも他スキーマに既存テーブルがあれば拒否する。したがってpublic構築済みDBへtest1を追加する用途には使えない。public用とtest1用は別々の空DBを用意する。

セッションアドバイザリロックを保持して同ランナー同士の競合を抑止する。各phaseでpending・SQL checksum・適用済み履歴のversion/rank/script/type/checksum/successを確認する。各Flyway設定は独立して作り、skip=trueをV4/V5へ引き継がない。

各phaseは別コミットであり、全工程一括トランザクションではない。途中失敗時は例外で後続へ進まず、repair/clean/自動リトライ/履歴削除を行わない。途中まで作られたDBは残る。空でなくなったDBへの再実行は拒否し、管理者が履歴を調査する。

共有/本番DBでは使わない。ローカル・空状態・他接続なし・所有者確認は、管理者がそのクラスタを専有していることの代替ではない。非協調的な管理者操作までアドバイザリロックで防ぐ設計ではない。

## 4. 隔離環境

PostgreSQL17.11の新規データ領域、専用UNIXソケット、127.0.0.1の空きポートを使用。既存資格情報を引き継がず、専用乱数パスワードを実行時に生成した。DBパスワード・初期講師パスワードはメモリ内で扱い、報告/ログ/Gitへ保存しない。

最終スモーク：DBポート50693、Spring Boot50694、Next50695。すべて5432と独立。通常のapplication.properties・Flyway target=2・フロントの.env.localは変更していない。

public用はfresh_public/public、test1用はfresh_test1/test1を新しい空DBとして用意。実JARの対話式セットアップ・初期講師登録・起動確認は同じ専用クラスタ内の新規training_portal/publicを使用した。既存training_portalとはクラスタが異なる。

フロントエンドは現在ソースの一時コピーでbuild/start。元の.env.localをコピーせず、専用API URLだけをbuild環境へ設定した。node_modulesは既存インストールから参照した。

## 5. Migration結果

| 対象 | 処理 | 実際の結果 |
| --- | --- | --- |
| public | target2 / skip=false / pending=[1,2] | V1/V2通常適用成功 |
| public | target3 / skip=true / pending=[3] | V3省略登録成功。直後も内容列NOT NULL |
| public | target4 / skip=false / pending=[4] | V4成功。内容列NULL許可 |
| public | target5 / skip=false / pending=[5] | V5成功。認証世代列追加 |
| test1（別DB） | target5 / skip=false / pending=[1,2,3,4,5] | 全5件通常適用成功。対象外publicに業務表なし |

両方で最終V1〜V5履歴、rank1〜5、type=SQL、success=true、checksum順1122476101/463837306/-676549352/1630550406/1806942905を検証した。Flyway validate成功、pendingなし、9テーブル（8業務＋履歴）の所有者、正式ステータス7件、初期業務データ0件を確認した。

schedule_contentはVARCHAR(50) NULL許可。auth_versionはBIGINT NOT NULL DEFAULT0、ck_kenshu_member_auth_versionの有効なCHECK>=0を検証。既存のV3はpublic実行を拒否するSQLのままであり、publicでV3段階が成功しNOT NULLが維持されたことがSQL未実行の根拠である。履歴のtype=SQLだけでは省略を識別できないことも手順へ記載した。

## 6. 初期講師・アプリ・ブラウザ確認

| ケース | 結果・範囲 |
| --- | --- |
| 実JARのFreshDatabaseCommand | PTYで接続先確認・非表示入力を実施、終了0 |
| 実JARのInitialInstructorCommand | 同じ新DBへ社員区分2の合成講師を作成、終了0 |
| BCrypt/世代 | 社員1件、区分2、auth_version=0、BCrypt形式を値非表示で確認 |
| Spring Boot通常起動 | target=2を変更せず起動成功。専用DB URL/HTTP portだけ起動時指定 |
| 実APIログイン | セッションCookie/CSRFを取得し、初期講師のパスワードでログイン成功 |
| 基本API | Home/Progress/Schedules/Membersが200 |
| Next | 一時コピーのWebpack build/start成功、ログインHTML取得成功 |
| 実headless Chrome | 画面フォームからログイン成功、Home/Progress/スケジュール一覧/メンバー管理のDOM表示を確認 |

ブラウザではメンバー一覧の合成講師表示も確認した。CDPと専用プロファイルだけを使用し、アクセシビリティ・System Events・OS画面収録権限を要求していない。API試験やHTML取得だけをブラウザ成功として数えていない。画面のピクセル単位レイアウト、キーボード/スクリーンリーダー、業務フロー全件は今回対象外。

## 7. 自動テスト件数

| 実行 | 成功 | 失敗/エラー | スキップ |
| --- | --- | --- | --- |
| 新規構築の関連JUnit（Fresh単体6＋Fresh隔離DB5＋初期コマンド単体3） | 14 | 0/0 | 0 |
| ./mvnw clean test（専用fresh_test1へ接続、217件） | 207 | 0/0 | 10 |
| Python AST構文・Node --check | 成功 | 0 | なし |
| Maven package / Next Webpack build | 成功 | 0 | なし |
| git diff --check | 成功 | 0 | なし |

上のJUnit2行には重複するケースがあり、成功数を単純合算しない。全217件が実行成功したという意味ではない。

全件実行のスキップ10件：FreshDatabaseCommandIntegrationTestの5件は専用fixture用環境変数を外したため（直前の関連試験で実行成功）、RoleSessionConcurrencyTest3件・InitialInstructorConcurrencyTest1件は既存の固定59455条件、ScheduleProgressHomeIntegrationTest1件は専用環境変数/DB名条件により未実施。今回の新しいDB名・ランダムポートへ既存テストの制約を勝手に緩めていない。認証・メンバー・スケジュール・Progress・Homeの通常API回帰、および既存のMigration試験2件は成功した。

最後のスモーク実行は--smoke-onlyを明示し、成功済みJUnitを繰り返していない。Migration・初期講師・起動・ブラウザ確認を実行した。フロントの業務コードは変更しておらず、Vitest/型チェック/lintの全件再実行は今回未実施。Next Webpack build内のTypeScript検査は成功した。

## 8. 安全ガードの検証

- 必須引数なし、remote/localhost、5432、管理用DB、不正DB名、許可外schema：DB接続前に拒否。
- Consoleなし：DB接続前に拒否。
- SQL追加/改変：DB接続前の件数/SHAチェックで拒否。
- 他スキーマに既存テーブルと1行の合成データ：拒否、当該行と対象空スキーマ不変。
- 空の既存Flyway履歴テーブルだけがあるDB：拒否、履歴0件を維持。
- 構築済みDBへの2回目実行：拒否、V1〜V5の履歴を維持。
- 他クライアント接続がある空DB：拒否、業務表を作らない。
- 既存V4ランナー・V1〜V5・通常設定・Excel：今回変更していない。

構築中の通信切断・ディスク不足・非協調的な別管理者のDDL競合の障害注入は未実施。各phase間で後続が例外停止するコードは確認したが、すべての障害経路を実証したとはしない。

## 9. 失敗試行と切り分け

最初のsandbox内実行はポートのbind権限で失敗し、PostgreSQL開始前に停止した。Pythonのpy_compileもOSキャッシュ領域への書込み制限に当たったため、構文確認にはファイルを書かないAST解析を使用した。許可された隔離検証実行ではDB/アプリを起動できた。

初回のブラウザなし構築検証は成功。ブラウザ付き試行では、補助スクリプトがHome見出しを「ホーム」と想定して失敗した。実際の既存表示は「おかえりなさい」。次の試行ではメンバー見出しの「メンバー」と実画面「メンバ登録」の不一致で失敗した。どちらもアプリの仕様を変更せず補助判定を修正した。ログイン到達・各画面遷移の履歴と固定stageログで切り分けた。

それらの試行はブラウザ全体成功として数えていない。最終の--smoke-only実行で実ブラウザ4画面まで成功した。段階失敗時は専用プロセスを停止し、開発DBへフォールバックしていない。

## 10. 証跡・DB保護・終了状態

- 関連JUnit/回帰/ビルド/初期構築を実施した記録：`${NON_DISTRIBUTED_EVIDENCE_DIR}/archived-run-1`。
- 最終スモーク・実ブラウザ成功記録：`${NON_DISTRIBUTED_EVIDENCE_DIR}/archived-run-2`。
- results.json、focused.log、regression.log、package.log、backend.log、frontend-build.log、browser.log等を各該当ディレクトリに保持。スモークのみのディレクトリにはJUnitログはない。
- 回帰を含む一括試行のresults.jsonはbrowser段階失敗によりsuccess=falseであり、その試行全体を成功としていない。最終スモークのsuccess=trueと区別する。過去の失敗試行も各専用ディレクトリに保持。DBデータ・Chromeプロファイルは機密扱いの一時領域であり、Gitへ追加しない。

全クラスタは検証後にfast shutdownで正常停止。専用Spring Boot/Next/Chromeも終了し、データ・ログを保持している。既存のPostgreSQL・アプリ・ブラウザ・Dockerへ停止/削除操作を行っていない。既存開発DB5432へ接続するコードパスは使用していない。

## 11. 残る確認事項・次の作業

1. 公開対象へ新規Java・テスト・ツール・手順を漏れなく含め、秘密値・一時データ・個人パスの公開レビューを行う。今回はstage/commitしない。
2. クリーンcheckoutと新規依存キャッシュでMaven/pnpm取得からの再現性を追加確認する。今回の試験は一時スクリプトに依存せずリポジトリ内ツールだけで行ったが、依存キャッシュは再作成していない。
3. Windows/他のJDKやPostgreSQL17 minor、低速・オフライン・証明書制限環境は未検証。対象環境を広げる場合に確認する。
4. 既存の固定ポート/DB名条件付き5テスト、および故障注入・全業務E2Eは今回未実施として残す。
5. V6以降を追加すると新規ランナーは意図的に拒否する。SQL集合・SHA・checksum・期待履歴・構造の見直しと再検証が必要。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
