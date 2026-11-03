# Flyway V5 開発DB適用準備（2026-10-18）

## 実施範囲・結論

今回は接続先確認、読み取り専用のDB調査、適用前バックアップ取得とファイル検証、適用・検証・復旧手順の作成だけを実施した。V5その他のmigration、Spring Boot、DB復元は実行していない。既存データ、Flyway履歴、V1〜V5、通常target設定は変更していない。既存未コミット変更は保持した。develop、HEAD d63ebbaa。

バックアップ取得とアーカイブ全内容の読み取りは成功した。ただし、今回取得したバックアップを実際に隔離DBへ復元する試験は未実施であり、完全な復元保証とはしない。開発DB適用前には最新状態の再確認、適用実行の承認、およびV5用の接続先ガードを備える実行手段の用意・検証が必要。

## 1. 接続先・調査方式

- PostgreSQL 17.11、127.0.0.1:5432。
- DB名training_portal、接続ユーザーtraining_portal_user。
- 調査接続のcurrent_schemaはpublic。test1は完全修飾名で参照した。
- default_transaction_read_only=onとBEGIN READ ONLYで調査した。パスワードは既存環境変数から取得し、引数・ログ・本報告へ出力していない。
- 最終調査時点の他クライアント接続は0件。ただし、今後の適用時点での無競合を保証する値ではない。
- public/test1のkenshu_memberとflyway_schema_historyはすべてtraining_portal_user所有。対象4テーブルのrelaclはNULLで、既定ACL。

読み取り専用の設定でもpg_dumpが一貫したスナップショットを作成できた。今回は業務更新を行っていない。

## 2. 現在のFlyway履歴

| version | public | test1 | checksum |
| --- | --- | --- | --- |
| 1 | success=true | success=true | 1122476101 |
| 2 | success=true | success=true | 463837306 |
| 3 | success=true | success=true | -676549352 |
| 4 | success=true | 未登録 | 1630550406 |
| 5 | 未登録 | 未登録 | 未登録 |

既存履歴のscript名とチェックサムは想定値に一致した。publicのV3省略登録・V4通常適用は過去の検証報告と整合している。ただし、Flyway履歴テーブルのsuccess=trueだけで省略登録とSQL実行を区別できるわけではない。今回V3の省略登録を再実行する必要はない。

## 3. V5適用前のkenshu_member

両スキーマのカラム定義は同じで、auth_versionは存在しない。

| カラム | 型 | NULL | default |
| --- | --- | --- | --- |
| syain_id | VARCHAR(10) | 不可 | なし |
| syain_kbn | SMALLINT | 不可 | なし |
| name | VARCHAR(50) | 不可 | なし |
| name_kana | VARCHAR(100) | 許可 | なし |
| password | VARCHAR(255) | 不可 | なし |
| screen_id | VARCHAR(100) | 不可 | なし |
| update_count | INTEGER | 不可 | 0 |
| create_user_cd | VARCHAR(100) | 不可 | なし |
| create_date | TIMESTAMP WITHOUT TIME ZONE | 不可 | なし |
| update_user_cd | VARCHAR(100) | 不可 | なし |
| update_date | TIMESTAMP WITHOUT TIME ZONE | 不可 | なし |

制約は主キーsyain_id、syain_kbn IN (1,2)、update_count>=0。社員件数はpublic 6件、test1 0件。社員名・パスワードハッシュ等の行データは報告書へ掲載しない。

## 4. V3・V4・V5の依存関係

V3はtest1限定でschedule_contentのNOT NULLを解除する。publicでSQL実行すると拒否される。publicには既に省略登録されているので、再実行しない。

V4はcurrent_schemaを確認する。publicでは対象カラムのNOT NULLを解除し、test1ではV3によってNULL許可になっていることを確認してDDLを実行せず終了する。test1のV4がDDLなしでも、通常のmigration実行による履歴登録は必要。

V5は非修飾名kenshu_memberにauth_version BIGINT NOT NULL DEFAULT 0と0以上のCHECK、日本語コメント「認証世代番号」を追加する。V3・V4のカラムへ直接依存しないが、Flywayのバージョン順で処理する。非修飾名のため、defaultSchema・schemas・JDBC currentSchemaをすべて同じ対象スキーマに固定することが重要。

- public：pending=[5]を期待する。
- test1：pending=[4,5]を期待する。V4をスキップ登録しない。

V5は既存社員にも0を与えるが、社員区分・更新カウンタ・監査項目・権限行の既存不整合を修復するmigrationではない。役割変更の業務処理は新アプリ側の責務。

## 5. バックアップ

保存先（Git管理外・ユーザー領域）：

`${PRIVATE_BACKUP_DIR}/training_portal-public-test1.dump`

- PostgreSQL custom形式。
- public/test1の構造、業務データ、シーケンス、Flyway履歴、所有者、権限を通常のpg_dumpで保存。
- --no-owner、--no-privilegesは使用していない。
- pg_dump終了コード0、サイズ96,905バイト。
- SHA-256：`9fc37acaccd54636f4d4f817ff1ee479954ad23b01a7d29ee9851e810be3f266`
- pg_restore --list成功。
- pg_restore --file=/dev/nullで全アーカイブの読み取り・SQL展開成功。
- 実際のDBへの復元試験は未実施。ロールの存在、復元先権限、オブジェクト競合まで保証した結果ではない。
- ディレクトリ700、ファイル600。既存バックアップは削除していない。
- 同じディレクトリへbackup.list、backup-sha256.txt、preflight.jsonを保存した。

検証補助の初回実行は「ACLエントリが必須」という誤った条件で停止した。pg_dump自体は正常終了していた。取得済みアーカイブの全展開検証は成功し、対象テーブルのカタログrelacl=NULL、明示ACLエントリ0件と整合することを確認した。ACL省略オプションによる欠落ではない。再バックアップやmigrationは行わず検証情報を保存した。

このバックアップはDB全体・クラスタ全体ではなく2スキーマのバックアップ。ロール定義、ロールのログイン資格情報、DB作成属性、他スキーマ、サーバー設定は含まない。バックアップ内には社員情報とパスワードハッシュが含まれ得るため、Git・チャットへアップロードしない。

## 6. 適用前の共通手順（今後実施する計画）

1. V5適用の承認を得る。今回の準備承認を適用承認と解釈しない。
2. 実際の適用までにデータ更新があれば、同じ保護条件で最新バックアップを再取得・検証する。今回のファイルも残す。
3. Spring Boot、テスト、他のFlywayを停止し、全アプリインスタンスの業務更新を止める。他接続・長時間トランザクション・待機ロック・prepared transactionを再調査する。
4. 最新のファイルハッシュ、履歴、所有者、kenshu_memberの構造を再確認する。auth_versionが既に存在する、履歴が変わった、チェックサム不一致、接続先相違なら停止する。repairで合わせない。
5. 全業務データを型・NULL・行の重複を区別して安定順序で比較できる形式に保存する。件数だけで一致扱いしない。シーケンス、構造、制約、索引、所有者、ACL、履歴も保存する。社員の資格情報をログへ出さず、比較用証跡を600で保管する。
6. Flyway12.4.0を固定する。接続ユーザーtraining_portal_user、DBtraining_portal、ループバック5432をガードする。schemas/defaultSchema/currentSchemaと履歴テーブルを明示して、他スキーマへのフォールバックを拒否する。
7. 実行時設定だけtarget=5、skipExecutingMigrations=false、cleanDisabled=true、createSchemas=false、baselineOnMigrate=falseとする。通常application.properties/targetは変更しない。
8. migrate前にvalidateとinfoを実施し、期待するpending以外なら停止する。実行用接続には読み取り専用設定を引き継がず、調査用接続は読み取り専用のまま分離する。パスワードは引数に渡さない。
9. lock_timeout等の短いDDL待機上限を適用用接続へ設定し、競合時は停止する。V5のALTER TABLEは排他ロックを必要とする。

既存tools/flyway/run-public-v4.pyとPublicV4Runner.javaはV3省略登録・V4適用専用であり、V5をサポートしない。V5用のガード付き実行手段を別途準備・隔離検証する。既存ランナーのtargetを書き換えて流用しない。SQL単独のpsql実行でFlyway履歴を迂回しない。新ランナーは少なくとも接続先・対象スキーマ・既存履歴・SQL SHA-256・Flywayチェックサム・pending・skip=falseを検証してからmigrateする。

Flyway Java APIの構成例（実行済みコードではなく設定例）：

```java
Flyway.configure()
    .dataSource(url, "training_portal_user", passwordFromEnvironment)
    .schemas(schema).defaultSchema(schema)
    .locations("filesystem:src/main/resources/db/migration")
    .target("5").skipExecutingMigrations(false)
    .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false)
    .load();
```

urlは`jdbc:postgresql://127.0.0.1:5432/training_portal?currentSchema=public`または末尾test1。schemaを自由入力のまま使用せず、public/test1の許可値と接続後のcurrent_schemaを照合する。

## 7. test1への適用手順

先にtest1を適用・検証することを推奨する。ただし既存テストデータの書き換えはしない。

1. test1専用の新プロセスで接続先とV1〜V3成功履歴を確認する。
2. schedule_details.schedule_contentがVARCHAR(50) NULL許可であることを再確認する。異なればV4を進めず停止する。
3. defaultSchema=test1、schemas=[test1]、currentSchema=test1、target=5、skip=falseでvalidate/info。pending=[4,5]だけを許可する。
4. V4・V5を通常適用する。V4は前提確認のみ、V5はtest1.kenshu_memberへの追加。
5. validate成功、test1のV1〜V5成功、publicが不変、期待する構造追加以外に差分なしを確認する。
6. V4とV5は別migrationのため、V4だけ成功してV5が失敗する可能性がある。1回のmigrate全体が一括ロールバックされるとは扱わない。失敗時はその場で停止し履歴・構造を読み取り確認する。

## 8. publicへの適用手順

1. test1の検証結果を確認後、別プロセスでpublicの最新状態を再確認する。
2. defaultSchema=public、schemas=[public]、currentSchema=public、target=5、skip=falseでvalidate/info。V1〜V4が既存の成功履歴で、pending=[5]だけを許可する。
3. V5のみ通常適用する。V3省略登録・V4再適用はしない。
4. validate成功、V1〜V4の既存行が不変、V5 success=true、test1不変を確認する。
5. 失敗時は追加変更をせず停止する。自動復元・repair・履歴編集はしない。

## 9. 適用後の検証

各スキーマの適用直後に次を確認する。

- auth_versionはBIGINT NOT NULL、default 0、CHECK(auth_version>=0)、日本語コメントあり。
- 既存社員全員のauth_versionが0。比較時点では区分変更等を実行しない。
- 既存の11カラムの値、社員件数、他の業務データ、シーケンス、既存カウンタ・監査値が不変。
- 所有者・ACL、既存主キー・外部キー・索引・CHECKが不変。
- 対象外スキーマの構造・履歴・全データが不変。
- Flyway履歴の既存行・チェックサム不変、追加分だけsuccess=true、validate成功、pendingなし。
- 再migrateを行う場合は別途許可の範囲で実行し、追加0件と比較不変を確認する。infoのpendingなしだけを実再実行成功と書かない。
- 新アプリ起動前にV5列の存在とtarget=2でのvalidate/infoを確認する。target設定は今回変更しない。新コードでの実ログイン・区分変更検証は別途合成データで行う。
- 全アプリを新コードへ切り替え、旧インスタンスを混在させない。旧実装は認証世代の増加・照合をしないため、混在中に今回の保証は成立しない。

## 10. 復旧手順

### V5が失敗した場合

1. 追加のmigration・アプリ更新を停止する。
2. 対象スキーマの履歴と列・制約を読み取り確認し、成功／失敗／部分状態を確定する。
3. V5はPostgreSQLでトランザクション内実行される通常のDDLであり、標準設定なら当該migrationの失敗はロールバックされる。ただし実際の状態を確認せず断定しない。test1のV4成功分は残り得る。
4. 原因を除去し、同じ未変更V5を再実行できるか判断する。履歴をrepair・手動DELETEで整える操作はこの計画に含めない。

### 適用成功後にアプリ不具合が判明した場合

まずアプリを停止し、DBのV5列と履歴を残したまま対応を検討する。追加カラムを残して旧アプリへ戻す方法はデータ保持には有利だが、DIFF-31の古い権限問題が再発する。旧アプリを再開するならセッションを全失効し、社員区分変更を停止する等の運用判断が必要。安全な方針が決まるまで停止を継続する。

auth_versionの削除・値の巻き戻し、既存V5の変更、成功履歴の削除は行わない。認証世代の巻き戻しは旧認証の復活を招き得る。後からのDDL変更が必要なら、新migrationと別途承認で対応する。

### バックアップ復元が必要な場合

自動復元しない。まず停止・証跡保存・復元範囲・バックアップ以降の更新損失を確認し、復元の承認を得る。

新しい隔離DBへ先に復元し、必要なロールを用意して所有者・ACLを保持する。pg_restoreは--exit-on-error --single-transactionを使用し、対象スキーマを指定する。標準publicスキーマの重複をTOCリスト等で調整し、pg_database_ownerの所有者指定は維持する。--no-owner --no-privilegesで省略しない。

復元後の履歴・構造・データ・シーケンス・所有者・ACLを検証する。開発DBへ上書きする操作やDROP/CLEANは、この準備作業では許可されていない。復元はV5以後の認証世代も戻すため、アプリ全停止・全セッション破棄・新しいログイン要求を含めて復旧計画を承認する。publicだけの復元で十分か、test1も戻す必要があるかを区別し、無関係なスキーマを巻き戻さない。

## 11. ファイル固定値と残確認

V5 SHA-256：`a95cc12eaeb1714b6cc509cba81caff521a94a41b43a3d9ba895f78f7dd79307`

V1〜V5の全SHA-256と調査カタログは専用バックアップディレクトリのpreflight.jsonへ保存した。Flywayの整数チェックサムとファイルSHA-256は別の値であり、同一視しない。

適用前に残る確認：

1. 今回のバックアップの隔離復元試験、復旧に必要なロールと権限の確認。
2. V5用実行手段の準備・隔離検証と、適用承認。
3. 適用時点の最新バックアップ、接続・履歴・カラム・ロックの再確認。
4. publicとtest1の適用順序、停止時間、旧新アプリ切替と全セッション再ログインの運用確認。
5. 変更前後の全データ比較用スナップショット取得。今回の社員件数だけで全データ不変を保証しない。

今回追加したリポジトリ成果物は本計画書のみ。DB更新、migration、設定変更、commit/push/mergeは行っていない。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
