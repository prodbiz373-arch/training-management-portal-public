# Flyway V4 適用前検証結果（2026-10-12）

## 結果

管理者が用意した **training_portal_v4_verify** だけで復元・マイグレーション・異常系試験を実施し、V3省略＋V4適用を確認した。
既存training_portalのpublic/test1は読み取り専用照会・pg_dumpだけを行い、前後の構造・全データ・シーケンス・Flyway履歴を含むダンプが一致した。

前回はDB作成権限不足で中止していた。今回は作成済みDBを使用し、DB作成・削除・権限変更は行っていない。検証用DBは削除せず残している。

## 接続先・空DB確認

復元前にcurrent_database()がtraining_portal_v4_verifyであること、所有者がtraining_portal_userであることを確認。
ユーザーリレーション0、ユーザールーチン0、標準public以外のユーザースキーマ0。復元直前にも接続先とpublic/test1のリレーション0件を確認した。
Flyway用のJavaランナーは検証用DB名を固定し、JDBC接続後のcurrent_database()/current_schema()確認に成功した場合だけFlywayへ進む。既存DBを指定する引数は用意していない。
秘密情報は環境変数から取得し、接続URL・パスワードをログへ出力していない。Flywayの標準ログは無出力のLogCreatorへ差し替え、非機密の結果だけ記録した。

## バックアップ・復元

PostgreSQL 17のpg_dumpで既存DBのpublic/test1をcustom形式で取得。pg_restore --listと全SQLの/dev/nullへの抽出に成功。

- 保存先：`${PRIVATE_BACKUP_DIR}/source.dump`
- アーカイブSHA-256：`b5ba520a748fb802a546fe9f9eee3ec1230bdb3732904dc74b284fb3ba93c3d2`
- 保存ディレクトリは700、ダンプファイルは600。
- DB全体ではなく、対象public/test1を収録。クラスタ共通ロール等は含まない。

復元先はtraining_portal_v4_verifyのみ。--exit-on-error/--single-transaction/--no-owner/--no-privilegesを使用。
最初は標準publicスキーマとCREATE SCHEMA publicが重複して失敗した。単一トランザクションでロールバックされ、空の状態を再確認した。
publicを削除せず、TOC一覧からpublicのSCHEMA作成項目1件だけを除外して再復元し成功した。SQLマイグレーションの内容は変更していない。
所有者・ACLの再現は今回の検証対象外。構造比較では所有者・ACLを除外して比較した。

復元後、両スキーマについて以下が元DBの作業前記録と一致した。

- テーブル・カラム・制約・索引・コメント等のスキーマダンプ。
- 全8業務テーブルの全データとシーケンスを含むデータダンプ。
- Flywayのversion/checksum/success。
- public内容カラムNOT NULL、test1内容カラムNULL許可。

## Flywayの実行方法・結果

Spring BootやCLIではなく、ローカルのflyway-core/flyway-database-postgresql **12.4.0** とJDBCドライバを使った独立Javaプロセスで実行。各段階で設定を新規生成した。
locationsはリポジトリ内のV1〜V4のみ。baselineOnMigrate=false、outOfOrder=false、validateOnMigrate=true、cleanDisabled=true、createSchemas=falseを指定。
lock_timeout=5秒、statement_timeout=30秒。コールバックは対象テーブルのロックをSELECTする観測用だけで、DDL/DMLなし。

| 実行段階 | スキーマ | target | skipExecutingMigrations | pending | 結果 |
| --- | --- | --- | --- | --- | --- |
| V3省略登録 | public | 3 | true | V3のみ | 1件の履歴登録、validate成功 |
| V4通常適用（別プロセス） | public | 4 | false | V4のみ | 1件適用、validate成功 |
| V4通常適用（別プロセス） | test1 | 4 | false | V4のみ | 1件適用、validate成功 |
| 再migrate | public | 4 | false | なし | 適用0件、validate成功 |
| 再migrate | test1 | 4 | false | なし | 適用0件、validate成功 |

V3省略登録直後もpublicの内容カラムはNOT NULL。test1のスキーマ・業務データも不変。test1限定ガード付きのV3をpublicで実行せず履歴登録できた。
V1・V2は再実行しておらず、既存履歴行はinstalled_on等を含め全フィールド一致。

V4ではpublicの内容カラムがNULL許可へ変化し、AFTER_EACH_MIGRATEで対象テーブルのAccessExclusiveLock取得も観測した。skip=falseでSQLが実行されたことを確認。
test1ではスキーマダンプが完全一致し、同コールバックで対象テーブルのAccessExclusiveLock取得なし。SQLのNULL許可済み分岐でDDLを行わず正常終了したことを確認した。

## 最終履歴と定義

| スキーマ | V1 | V2 | V3 | V4 |
| --- | --- | --- | --- | --- |
| 検証用public | 1122476101 | 463837306 | -676549352 | 1630550406 |
| 検証用test1 | 1122476101 | 463837306 | -676549352 | 1630550406 |

すべてsuccess=true。V3は元ファイル・適用済みtest1のチェックサムと一致。既存履歴行は全フィールド不変で、新しい履歴だけ追加された。
両スキーマのschedule_contentはVARCHAR(50)、NULL許可となった。

publicのスキーマ差分は次の1行だけ。test1のスキーマ差分はなし。

```diff
-    schedule_content character varying(50) NOT NULL,
+    schedule_content character varying(50),
```

## データ比較

件数だけでなく、Flyway履歴を除く全業務データ・シーケンスを含むデータダンプを、復元直後・V3省略後・V4適用後・最終時点で比較して一致した。
以下の件数は復元後から検証完了まで不変。

| テーブル | public | test1 |
| --- | ---: | ---: |
| kenshu_member | 6 | 0 |
| schedule | 5 | 0 |
| schedule_can_edit | 14 | 0 |
| schedule_bunrui | 3 | 0 |
| schedule_details | 8 | 0 |
| issue_statuses | 7 | 7 |
| issue_submission_statuses | 2 | 0 |
| issue_submission_statuses_comment | 2 | 0 |

データダンプの正規化後SHA-256：

- public：`99bf218591ee3e4302fefb66d15da6a392e088d301548fb1e88e8d01e631958f`
- test1：`708535f6b93bd2a3f238242b55609d131e31e5904536027c74f055fe4ed79db3`

## 異常系と再実行

検証用DBだけで、各ケースをBEGIN内で準備しV4を実行。期待した例外による接続終了時のロールバックを確認した。

- 想定外スキーマ：`V4 requires public or test1`で停止。
- VARCHAR(51)：VARCHAR(50)チェックで停止。
- 対象カラム欠落：存在チェックで停止。

public/test1でV4 SQLそのものを再実行して正常終了し、ROLLBACKした。
異常系試験後もスキーマ・業務データ・履歴は試験前と一致。検証用のスキーマ・型変更・リネームを残していない。
Flywayの再migrateが0件という確認と、SQL自体の再実行確認を区別して実施した。

## 既存DBが未変更であること

既存training_portalはDDL/DML・Flyway実行なし。作業前後にpublic/test1を含む完全なplainダンプを取得した。
比較から除外したのはpg_dumpが生成するランダムなrestrict/unrestrictキー行だけで、構造・所有者・ACL・データ・シーケンス・履歴を含め一致。
正規化後SHA-256は前後とも：

`93169556f27753067e027246125d0765941b7170e1977ddb454e0499af33974e`

## ファイルと実行制約

今回変更したリポジトリ内ファイルは`docs/verification/schedule-content-v4-test.md`だけ。
V1〜V4、通常・テスト用application.propertiesは作業前後SHA-256一致。
補助ランナー・比較記録は権限制限した`${NON_DISTRIBUTED_EVIDENCE_DIR}`に保存。環境の一時領域なので長期保管は保証しない。バックアップには既存データが含まれるため適切に保管する。
Spring Boot起動・再起動、既存DBへのFlyway実行、repair/baseline/clean、検証用DB削除、commit/push/mergeは行っていない。
./mvnw clean testは既存test1へ適用され得るため実行していない。今回の検証は独立ランナーによるDB統合検証。

## 残るリスク

- 実際のpublic適用は未実施・未承認。接続先を再確認して別途承認のもと実施する。
- 本番相当の同時アクセス・長時間トランザクション下のロック待ち、バックアップ復旧全体、アプリのNULL保存・再取得・Home/Progressの再検証は今回の対象外。
- test1のschedule_detailsは0件。NULL許可分岐・定義不変は確認したが、実データありのtest1に対する追加検証は未実施。
- 復元は所有者・ACLを再現していない。実適用の権限・接続・実効設定は適用前に再確認する。
- Java API経由のFlyway 12.4.0で検証した。手順書のCLIコマンドそのものは未検証。CLIを使う場合は同じ固定バージョン・設定で予行演習する。
- V3省略設定をV4へ引き継がないことが必須。今回と同様、別プロセスでskip=falseを明示する。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
