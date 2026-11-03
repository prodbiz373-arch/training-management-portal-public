# DIFF-16 初期講師セットアップ検証（2026-10-18）

## 結果

初期講師登録用のローカル管理コマンドを追加した。空の専用隔離DBでパッケージ済みコマンドを実行し、初期講師を作成できた。実Chromeからその講師でログインし、メンバー管理画面で新人・講師を追加できた。開発用5432のpublic/test1へ接続・変更していない。既存未コミット変更は保持し、commit/push/mergeは行っていない。

## 既存実装の調査

安全な初期講師シード・管理コマンドは見つからなかった。既存のメンバーAPIは講師認証とCSRFを要求するため、最初の講師だけは作成できない。DB migrationに社員の固定資格情報は登録されていない。

/registerにはDBと連携しない旧モック登録フォームが残っていた。フォームはパスワードをsaveMembersへ渡していたが、保存関数は既にパスワードを除去していた。平文が実際に保存されると初期調査で述べた点は訂正する。DB認証には使用されず、登録成功と誤認させる経路だったため、ログイン画面へのリダイレクトに置き換えた。通常画面のデザインは変更していない。

## 実装・セキュリティ

- System.consoleを使う明示実行コマンド。端末がない場合は接続前に拒否。
- DBホスト127.0.0.1、DB名training_portal、DBユーザーtraining_portal_user、public/test1を許可値とし、ポート・schemaの指定と接続先文字列の再入力を要求。
- 接続後にDB名・ユーザー・schema・アドレス・ポートを照合。別環境へフォールバックしない。
- DBパスワード・初期講師パスワードは非表示入力。引数・固定環境変数・ソース・ログへ初期資格情報を保存しない。
- 既存MemberCreateRequest、KenshuMemberService、PasswordEncoderConfigを使用。社員区分2、BCrypt強度10、auth_version=0。
- テーブルEXCLUSIVEロック→社員0件確認→既存Service登録→コミットを同一トランザクションで実行。社員が1件でもいれば拒否。通常INSERTにもロックが競合する。
- 初期登録Serviceは通常起動時のBeanに登録されない。HTTP API、ApplicationRunner、自動シードは追加していない。
- 専用JPA構成はddl-auto=validateで、Flyway・DDLを実行しない。
- コマンドのライブラリログは無効化し、失敗は固定メッセージ。char配列を消去するが、既存DTO/JDBC由来Stringの完全消去は保証しない。

## 変更ファイル

- src/main/java/com/training/portal/setup/InitialInstructorCommand.java（新規）
- src/main/java/com/training/portal/setup/InitialInstructorService.java（新規）
- pom.xml（2つのmainを区別し、通常JAR起動先を既存TrainingPortalApplicationへ固定）
- frontend/app/register/page.tsx（自己登録モックを無効化）
- src/test/java/com/training/portal/setup/InitialInstructorCommandTest.java（新規）
- src/test/java/com/training/portal/setup/InitialInstructorApiTest.java（新規）
- src/test/java/com/training/portal/setup/InitialInstructorConcurrencyTest.java（新規）
- src/test/java/com/training/portal/auth/AuthApiTest.java（通常起動で初期登録Serviceが非公開であることを追加）
- frontend/tests/register-disabled.test.tsx（新規）
- docs/setup/initial-instructor-setup.md（新規）
- 本報告書（新規）

既存のDIFF-31等の未コミット変更を上書き・取り消していない。既存Flyway V1〜V5、通常設定、Excelは変更していない。依存追加なし。

## 専用隔離PostgreSQL

新規クラスタ：${NON_DISTRIBUTED_EVIDENCE_DIR}/archived-run-1。

- PostgreSQL17.11、127.0.0.1:59455/training_portal、test1。
- 既存の停止済みクラスタは再利用せず、新しいデータ領域・専用ソケットを使用。
- 既存V1〜V5の5件を空の隔離test1へ通常適用し、validate成功。
- 隔離publicは最後までテーブル・シーケンス0件。
- 開発DB5432への接続、migration、既存データ更新は実施していない。

## 自動テスト

| 検証 | 最終結果 |
| --- | --- |
| バックエンド | 204件成功、失敗0、エラー0、スキップ0 |
| フロントエンド | 15ファイル212件成功、失敗0 |
| TypeScript | 成功 |
| lint | 成功 |
| Webpack build | 成功 |
| Maven package | 成功 |
| git diff --check | 成功 |

./mvnw clean testに専用DB接続先、-Dspring.flyway.enabled=false、-Dtest=*,!IssueStatusMasterMigrationTestを指定した。migration用テスト2件は除外しており、無条件の全件実行とは扱わない。Migrationは専用クラスタ準備時に別途適用・validateした。

追加14件のバックエンド検証：コマンド引数・非端末拒否3件、初期講師API連携・二重実行拒否・既存新人による拒否・入力不正5ケース・未認証登録拒否の9件、独立トランザクションの並行登録1件、通常起動の非公開1件。

初期講師による認証、BCrypt照合、認証世代0、メンバー追加、CSRFなしの拒否を確認。既存の認証世代変更・複数セッション・区分変更並行テスト、メンバーCRUD、スケジュール・Progress・Home連携も含めて成功した。

フロントでは/registerがログインへリダイレクトし、ローカル登録を行わないテストを追加。既存211件も成功した。

初回のフロント実行はsandboxのDNS制限で依存取得に失敗し、許可付き再実行で成功。依存定義・lockファイルは変更していない。一時コピーへの初回ソース転送パスも訂正し、実際の修正済み/registerでWebpack buildとブラウザ確認を実施した。

## パッケージ済みコマンド・実ブラウザ

Java21のPropertiesLauncherで実際のJAR内の管理コマンドを起動した。テスト補助が専用疑似端末から入力し、System.consoleの非表示入力を通した。パスワードは実行時乱数をメモリ内で生成し、ソース・ログ・資格情報ファイルへ保存しなかった。端末出力にもDBパスワード・初期講師パスワードが含まれないことを確認した。

| ケース | 結果 |
| --- | --- |
| 空環境へBOOTCLIを作成 | 成功、終了0 |
| 別IDで初期登録を再実行 | 拒否、終了1、追加なし |
| /registerへ未ログインアクセス | ログインへ遷移 |
| 初期講師で画面ログイン | 成功 |
| メンバー管理画面で新人BOOTNEWを登録 | 成功 |
| 同じ画面で講師BOOTTEACHを登録 | 成功 |

実Chrome、実Next、実Spring Boot、実PostgreSQLを使用し、API単体結果を画面成功として流用していない。フロントは既存の一時コピーを隔離API59456向けにWebpack buildし、59457で起動した。開発用.env.local・通常設定は変更していない。

最終監査は合成社員3件、社員区分2/1/2、全員auth_version=0・update_count=0・BCrypt既存形式。test1のV1〜V5成功履歴は維持し、隔離publicは空のまま。

## 証跡・終了状態・残課題

専用ディレクトリにbackend-tests.log、command-result.json、browser-result.log、initial-instructor-browser-network.json、initial-instructor-members.png、final-audit.logを保持した。画像は合成データのみ。ネットワーク記録はAPIパスとステータスのみで、認証ヘッダー・Cookie・本文を記録していない。実行時の生成パスワードは記録していない。

フロントログ：${NON_DISTRIBUTED_EVIDENCE_DIR}/initial-front-retry.log、initial-types-retry.log、initial-lint-retry.log、initial-webpack.log。証跡は一時領域のため長期保存は保証しない。

公開環境でのOS権限・DB資格情報・HTTPS等の管理は運用上の前提。本コマンドはOS管理者の本人認証や、すべてのスキーマが空であることの判定までは行わない。明示的に指定したスキーマの空判定と接続先確認を実施する。既存環境で講師が不在になった場合の復旧コマンドは今回の対象外。

実ブラウザの検証アプリは正常停止し、専用Chrome・Nextも停止した。専用PostgreSQLも終了監査後にfast shutdownで正常停止した。合成データ・ログ・既存バックアップ・既存Dockerは削除しない。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
