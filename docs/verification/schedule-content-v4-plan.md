# A案の適用準備・運用手順（2026-10-12）

## 状態と承認境界

準備のみ。V4を作成したがSQLは実行していない。DBへの接続、migrate/repair/baseline/clean、履歴変更、Spring Boot起動・再起動は行っていない。V1〜V3と既存設定も変更していない。
以下の実行手順は将来の承認後に使用するもので、今回の実行許可ではない。

前回の読み取り専用調査時点ではpublicはV1・V2、test1はV1・V2・V3適用済み。今回DBを照会しておらず、適用直前に再確認する。
通常アプリのFlywayはpublic/target=2。テストはtest1/target=latest。
**V4追加後に通常のDB統合テストを実行すると、test1へV4が自動適用され得る。準備段階では実行しない。**

## V4の処理

ファイル：`src/main/resources/db/migration/V4__allow_null_schedule_content_in_public.sql`

1. current_schema()がpublicまたはtest1であることを確認。NULLや他スキーマなら例外。
2. pg_catalogからそのスキーマの通常テーブルschedule_detailsと実在するschedule_contentを確認。存在しない場合、ビューやパーティションテーブル等の場合も停止。
3. 型が組み込みvarchar、長さ50であることを確認。PostgreSQLのatttypmod=50+4=54を使用し、ドメイン型も受け入れない。
4. test1：NULL許可ならDDLなしで終了。NOT NULLならV3適用状態と矛盾するため停止。
5. public：NOT NULLの場合だけ、public.schedule_detailsの当該カラムのNOT NULLを解除。NULL許可済みならDDLなし。

データのINSERT/UPDATE/DELETE、型変換、他カラム・制約の変更は行わない。test1からpublicを変更しない。
型検査はDDL前に行う。承認後の実行では他のDDL・デプロイと競合させない。
V4が揃えるのは対象カラムの定義。DB構造全体の一致は別途全テーブル・制約・コメントの比較で確認する。Flyway履歴や業務データ自体を両スキーマで同一にする目的ではない。

## Flyway 12.4.0の確認範囲

ローカルflyway-core-12.4.0.jarで以下を確認した。

- FluentConfigurationにtarget、defaultSchema、schemas、skipExecutingMigrations等のAPIが存在。
- DbMigrateがisSkipExecutingMigrationsを適用処理へ渡す実装を確認。
- Java 21によるDB未接続の設定生成が成功。target=3/skip=trueとtarget=4/skip=falseを独立生成し、取得値をassertで確認。
- 設定確認ではdatasource指定、load、migrate等を呼んでいない。

設定APIの利用可能性を確認したもので、SQL解析・DB上の履歴登録・ライセンスを含む実行時の全経路を実証したものではない。
下記CLIのインストール・実行確認も未実施。Spring BootにFlywayが含まれることとCLIが使えることは別。
実行担当者はFlyway CLI 12.4.0とPostgreSQL対応モジュールを用意し、別DBで手順全体を予行演習する。TeamsのcherryPickには依存しない。

## 管理ツールと接続の準備（承認後）

リポジトリのルートから実行。Flyway CLI 12.4.0を使用する。pom.xmlにはFlyway Maven pluginを追加していないため、裸の`./mvnw flyway:migrate`を本手順の実行方法としない。
Spring Bootを管理用ランナーとして起動しない。

- JDBC接続先は接続URLのcurrentSchemaも対象と一致させ、defaultSchema/schemasと照合する。
- 接続情報・パスワードは管理された秘密情報から取得。コマンド引数、ログ、資料へ値を記載しない。
- 下記`/secure/public-flyway.conf`は例示パス。接続情報のみを格納する権限制限済み管理ファイルを承認後に用意する。本作業では作成していない。
- 環境変数FLYWAY_*、外部設定、ユーザー設定等を確認し、接続情報を除く実効値だけを記録する。デバッグ出力や環境変数全表示は禁止。
- locationsはレビュー済みSQLだけを含む単一の固定場所を指定。R__、コールバック、Java migration、SQLの付随.confを確認し、想定外なら停止。target=3だけではこれらの副作用を排除できない。
- baselineOnMigrate=false、outOfOrder=false、validateOnMigrate=true、cleanDisabled=true、createSchemas=falseを固定。既存publicの非空スキーマをbaselineで通さない。
- 対象外スキーマへDDLできない専用の実行権限が望ましい。権限追加・変更は別途承認事項。

## 既存public：V3だけを省略してV4を適用

**全コマンドは未実行の計画。バックアップ・予行演習・実行前チェック・適用承認を済ませてから使用する。**
接続情報は引数に書かない。実際のCLIで設定優先順位を確認してから実施する。

### 段階1：対象をV3だけに絞る

```sh
flyway -configFiles=/secure/public-flyway.conf \
  -locations=filesystem:src/main/resources/db/migration \
  -defaultSchema=public -schemas=public -createSchemas=false \
  -target=3 -skipExecutingMigrations=true \
  -validateOnMigrate=true -baselineOnMigrate=false \
  -outOfOrder=false -cleanDisabled=true info
```

必須条件：V1・V2成功、履歴チェックサム一致、target以下の実行予定はV3の1件のみ。V4はtarget超過。V3以外が対象なら実行しない。V3が既に成功なら履歴・定義・作業記録を検証し、この段階を繰り返さず段階2へ進む。
infoの対象一覧と別途V1〜V3のチェックサム照合を行う。validate単独は未適用V3の状態により失敗し得るため、失敗を無条件に無視しない。migrateの事前検証は有効のままにする。

承認後、同じ引数の最後の`info`だけを`migrate`へ変更して1回実行する。
**V3 SQLは実行されず、Flyway自身が旧V3のチェックサムで成功履歴を登録する。履歴へ手動INSERT/UPDATEしない。**
通常の成功履歴だけで「SQLを省略した」とは判別しにくいため、実効設定・対象一覧・結果・担当者を別の作業記録へ保存する。
段階1直後はpublicの内容カラムがまだNOT NULLであること、V1・V2が不変であることを確認する。書き込みやアプリ起動は再開しない。

### 段階2：省略設定を解除してV4を通常実行

別プロセスで、設定オブジェクトも共有しない。FLYWAY_SKIP_EXECUTING_MIGRATIONSが残っていないことを確認し、引数でもfalseを明示。

```sh
flyway -configFiles=/secure/public-flyway.conf \
  -locations=filesystem:src/main/resources/db/migration \
  -defaultSchema=public -schemas=public -createSchemas=false \
  -target=4 -skipExecutingMigrations=false \
  -validateOnMigrate=true -baselineOnMigrate=false \
  -outOfOrder=false -cleanDisabled=true info
```

実行予定がV4だけであることを確認。承認後、最後の`info`を`migrate`へ変更して実行する。
V4成功後は同じ対象・locations・target=4/skip=falseでvalidate/infoを行う。
将来V5以降が存在しても今回の実行はtarget=4で止め、latestへ無条件に変更しない。

### 既存test1

V3を省略登録しない。既存V3の成功・チェックサム・NULL許可を確認し、test1専用の接続設定、defaultSchema=test1/schemas=test1、target=4/skip=falseでV4だけを通常適用する。
V4はDDLなしだが、test1のFlyway履歴へV4が追加される。これもDB変更なので今回実行していない。

## 将来の新規DB構築

承認された新規DB内で対象スキーマを用意する。通常の適用ではcreateSchemas=falseを維持し、スキーマ準備は別の明示的手順とする。

| 段階 | 新規public | 新規test1 |
| --- | --- | --- |
| 1 | target=2、skip=falseでV1・V2通常実行 | target=2、skip=falseでV1・V2通常実行 |
| 2 | target=3、skip=trueでV3のみ省略登録 | target=3、skip=falseでV3を実行 |
| 3 | target=4、skip=falseでV4通常実行 | target=4、skip=falseでV4通常実行（DDLなし） |
| 4 | V5以降は承認したtarget、skip=false | V5以降は承認したtarget、skip=false |

空DBで最初からskip=trueにしない。V1・V2も省略されてしまう。
publicとtest1を一括のschemas指定で扱わず、それぞれの履歴・defaultSchema・接続currentSchemaを持つ別実行にする。
V1〜V3を環境別の別内容に差し替えない。適用済みファイルは将来も固定する。

## バックアップ・別DB復元

承認後、全アプリ・バッチ・テスト・他の書き込み元と競合DDLを停止し、バックアップ完了から事後比較まで書き込み停止を維持。
PostgreSQL 17のクライアントを使用。接続は安全に用意したPGSERVICE等から解決し、値を表示しない。以下は対象DBへの接続設定済みの端末で使う例。

```sh
umask 077
pg_dump --format=custom --file=before-v4.dump
pg_restore --list before-v4.dump > before-v4.list
shasum -a 256 before-v4.dump
```

DB全体を取得しpublic/test1・Flyway履歴・シーケンス・関連オブジェクトを保存。ロール等のグローバル定義は別途保全・復元前提を確認。
既存DBへ上書きせず、承認済みの空の別DBを復元先に明示する。

```sh
pg_restore --exit-on-error --single-transaction \
  --dbname=service=isolated_restore before-v4.dump
```

`isolated_restore`は別DB専用サービスの例。実接続先と対象外DBへの不接続を確認してから実行。サービスは本作業では作成していない。
restore先でV1〜V3履歴、件数、制約、コメント、シーケンスを確認し、そこで適用・失敗・再実行・新規構築を予行演習する。public/test1の実DBへ復元しない。

## 実行前チェックリスト

- [ ] 実行承認がある。準備依頼を適用承認として扱っていない。
- [ ] 対象DBの識別を安全な管理画面等で確認し、接続情報を出力せず作業対象を照合した。
- [ ] 接続currentSchema、current_schema()、Flyway defaultSchema/schemasが対象1スキーマで一致。
- [ ] Spring Bootの実体をPID・起動場所・ポートから確認し停止済み。PIDの固定値を再利用しない。無関係なプロセスは停止しない。
- [ ] frontendのDB書き込み要求、バッチ、自動テスト、IDEの自動起動、他デプロイも停止。
- [ ] バックアップ取得・別DB復元確認・予行演習が成功。
- [ ] Flyway 12.4.0、PostgreSQL対応モジュール、実行権限を確認。
- [ ] V1〜V3のファイルとDB履歴チェックサムが既知値と一致。
- [ ] public=V1・V2、test1=V1・V2・V3の成功履歴。異なる場合は再計画。
- [ ] 対象カラムが通常テーブル上のVARCHAR(50)。public NOT NULL、test1 NULL許可を確認。
- [ ] 業務テーブル全件数・比較用データ・構造・コメント・制約を保全。
- [ ] effective target、skip、locations、schemas、defaultSchema、baseline、outOfOrder、validate、clean、createSchemas、executeInTransactionを秘密情報なしで確認。
- [ ] R__、コールバック、Java migration、付随.confなど想定外の実行対象がない。
- [ ] V3省略時の対象はV3のみ。V1・V2は成功済み、V4はtarget超過。
- [ ] V4実行は別プロセス、target=4、skip=false。対象はV4のみ。
- [ ] lock_timeout/statement_timeoutの値を保守時間と実データに応じて承認し、管理接続へ設定。DDLがロック待ちで業務を止め続けない。
- [ ] トランザクション実行を維持し、履歴書き換え・repair等で検証を回避しない。

## 適用後の検証

承認後、秘密情報を表示せず読み取り専用で実施。

```sql
BEGIN READ ONLY;
SELECT current_schema();
SELECT table_schema, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema IN ('public', 'test1')
  AND table_name = 'schedule_details' AND column_name = 'schedule_content'
ORDER BY table_schema;
SELECT 'public' AS schema_name, version, script, checksum, success
FROM public.flyway_schema_history
UNION ALL
SELECT 'test1', version, script, checksum, success
FROM test1.flyway_schema_history
ORDER BY schema_name, version;
SELECT count(*) AS row_count,
       count(*) FILTER (WHERE schedule_content IS NULL) AS null_count
FROM public.schedule_details;
COMMIT;
```

両スキーマの対象カラムがVARCHAR(50)/NULL許可。public/test1とも最終V4成功。V1〜V3は不変。
構造全体をスキーマ名の差を考慮して比較し、既存の対象外差異は自動修正せず報告する。
前後の業務データを全8テーブルで比較する。主キー順COPY等の決定的な出力を権限制限したファイルへ保存し、ハッシュ/バイト比較する。件数一致だけでデータ不変と判定しない。
Flyway履歴はV3/V4追加が予定差分なので業務データと分けて比較。test1はV4履歴追加以外、構造・データが不変。
構造差分はpublicの対象カラムのNOT NULL解除だけ。比較用ダンプのランダムrestrictキーは除外してよいが、他の差分は無条件に除外しない。
NULL保存・取得・編集、未入力課題400、Home/Progressは復元環境で検証する。実環境への書き込み検証は別途承認する。

## 失敗時の復旧

- V3登録前：停止し、履歴・定義・データ不変を確認。検証失敗をrepairで回避しない。
- V3省略登録後/V4失敗：V3履歴は別実行なので残る。V4がトランザクションでロールバックされたか履歴と定義を確認。原因を解消して同じ未適用V4をskip=falseで再実行する。V3履歴を消さない。
- V4成功後の不具合：まずアプリ書き込みを止める。必要なら新しい補償マイグレーションをレビュー・承認する。NOT NULLへ戻す場合はNULL行が0件であることが必須。NULLがある場合、勝手に補完・削除しない。
- 想定外の変更/トランザクション外の失敗：再実行せず停止。バックアップを別DBへ復元して差分を調査し、データ損失のない復旧範囲を承認する。public復旧のためにtest1まで巻き戻さない。
- 稼働再開後の全DB復元は新規データを失うため通常の復旧手段にしない。PITR等が必要なら復旧点と影響を別途合意。
- 成功済みV4の編集、履歴の手動変更、clean/baseline/repairは行わない。

## 準備段階の確認結果・未検証事項

- V1〜V3の作業前後SHA-256一致を確認済み。既知のFlyway CRC32：V1=1122476101、V2=463837306、V3=-676549352。
- `./mvnw -o clean compile`成功（アプリ起動・DB接続なし）。SQLの構文検証ではない。
- `git diff --check`成功。今回の追加ファイルも静的レビュー済み。
- V4は静的レビューのみ。PostgreSQLによる構文・型検査・ロック・失敗時の原子性・実データ保持は未検証。
- Flyway 12.4.0のDBなし設定生成は成功。CLIとDB上での省略登録、V4適用、validate、再実行は未検証。
- ./mvnw clean testはtest1を変更し得るため、ユーザーのDB変更禁止に従い未実行。Spring Bootも起動していない。
- 通常public target=2は維持。将来のアプリ運用設定は適用後に別途承認・検証する。
- 今回バックアップや復元は実施していない。実際の停止状態、実効接続設定、現時点のDB履歴も未確認。

## 参考仕様

- [Flyway skipExecutingMigrations](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-skip-executing-migrations-setting)：SQL省略時も履歴・コールバック等は処理される。
- [Flyway target](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-target-setting)：指定バージョン以下が対象。V3だけの選択にはV1・V2成功の前提が必要。
- [PostgreSQL 17 ALTER TABLE](https://www.postgresql.org/docs/17/sql-altertable.html)：NOT NULL解除とロック仕様。
