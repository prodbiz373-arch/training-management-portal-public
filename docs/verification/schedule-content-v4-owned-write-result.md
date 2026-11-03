# 所有者・ACL保持復元と隔離Flyway V3/V4検証（2026-10-12）

## 結果

新規training-v4-owned-004だけでバックアップを所有者・ACL保持で復元し、既存ランナーのdry-run、V3省略登録、別プロセスのV4通常適用、validate、再migrateを完了した。
ホスト開発DB・既存検証DBへ接続していない。既存コンテナ/volumeを再利用・変更・削除していない。

## 使用環境・安全確認

- イメージ：training-v4-isolation:lifecycle-v1
- 固定ID：sha256:818fa78ae2a6145001eb46a2a53759dbca8c5b8d5931f088694702e7ac789531、linux/arm64
- 新規コンテナ：training-v4-owned-004
- 新規volume：training-v4-owned-004-data、training-v4-owned-004-evidence
- PostgreSQL17.11、PID1のPython管理方式、pg_isready成功

network none、追加ネットワークなし、ポート公開なし、非privileged、capability ALL削除、no-new-privileges、ルートread-onlyをinspectで確認。
共有は専用volume2個と指定バックアップread-only bind1個だけ。ホストDB/Dockerソケットや開発用認証情報を渡していない。
ホスト側とコンテナ内バックアップSHA-256一致、許可されたSource完全一致、固定Destination、RW=falseを確認後に開始合図を送った。ネットワークガードは初期化前とFlyway検証前に成功。
既存ランナー2個とV1〜V4はmanifest記録とハッシュ一致。

バックアップ：${PRIVATE_BACKUP_DIR}/training_portal-public-test1.dump
SHA-256：88c8507f025bc40eb1b0b87308d02eddb32cc2febc598a065519bc2f487fd94b。

## 復元方法・所有者/ACL

バックアップのschema-only SQLを読み取り調査した結果、OWNER TOの参照はtraining_portal_userとpg_database_ownerのみ。
GRANT/REVOKE/ALTER DEFAULT PRIVILEGESの明示SQLはなかった。このバックアップには追加作成が必要な参照ロールはない。
新規クラスタでlifecycleが作成した一般ロールtraining_portal_userと、PostgreSQLの組み込みpg_database_ownerの存在を確認。training_portalはtraining_portal_user所有。
publicスキーマのpg_database_owner所有を確認し、標準publicのCREATE SCHEMA項目1個だけをTOCから除外。

復元オプション：--exit-on-error --single-transaction --use-list。
以前の--no-ownerと--no-privilegesは復元から削除した。接続は隔離内postgresで、バックアップのOWNER/ACL設定を通常どおり適用。
全アーカイブ読み出し、復元とも成功。警告・エラーなし。
復元後の所有者/ACLを含むschema-only SQLがバックアップと一致。schedule_detailsとflyway_schema_historyはtraining_portal_user所有、publicはpg_database_owner所有。
これは当該バックアップ内の所有者/ACLの再現確認であり、クラスタ全ロールやバックアップ外の設定の完全再現ではない。

比較SHA：
- 所有者/ACLを含む正規化構造SQL：9f2702e853683d80b07c87c9be1994e07ddab1cca7bbe46d43f4e893b0b52ff8
- 順序正規化した全データ/履歴/シーケンスSQL：d7fc6242381edc662a569a8d4e465fcde79e7ec5fbc7dab127a54a97b11b371a

publicはV1/V2、test1はV1/V2/V3、全success=true、期待チェックサム一致。
内容カラムは両方VARCHAR(50)、public NOT NULL、test1 NULL許可。
全8業務テーブルの件数・全データ・シーケンス・構造がバックアップと一致。
COPYをテーブル/カラムごとに区別し、各行を重複保持で比較。COPY内データを削除・書き換えず、出力順序差だけを許容。

## Flyway実行

既存run-public-v4.py/PublicV4Runner.javaを変更せずコンテナ内/workspaceから実行した。
乱数の隔離専用資格情報をコンテナ内だけで環境へ読み込み、ホストへ出力・コピーしていない。
実行ユーザーはランナー固定training_portal_user、DB名training_portal、schema public、Flyway12.4.0。
SQLハッシュ、履歴チェックサム、他接続0、lock_timeout=5s、statement_timeout=30sを既存ランナーで検査。

| 段階 | target | skip | pending | 結果 |
| --- | --- | --- | --- | --- |
| dry-runのV3確認 | 3 | true | [3] | 成功、読み取り専用 |
| dry-runの次段階確認 | 4 | false | [3,4] | 登録前は両方pendingと確認 |
| V3省略登録 | 3 | true | [3] | 履歴1件登録、validate成功 |
| V4通常適用・別プロセス | 4 | false | [4] | 適用1件、validate成功 |
| 独立Java APIによる再migrate | 4 | false | [] | 適用0件、前後validate成功 |

dry-run時点ではV3未登録なのでtarget4のpending=[3,4]が正常。V3登録後のapply-v4でpending=[4]だけになることを確認した。
V3登録後に比較を実施し、publicの構造・NOT NULL・全業務データ・シーケンスは不変、既存履歴全フィールド不変でV3だけが追加。
V3はtest1専用ガードを持つSQLだが、publicでSQLを実行せず正式に省略登録できた。
V4はskip=falseの別プロセスで実行。publicのNULL許可への変化で実DDLを確認。

## 最終結果

| schema | V1 | V2 | V3 | V4 |
| --- | --- | --- | --- | --- |
| public | 1122476101 | 463837306 | -676549352 | 1630550406 |
| test1 | 1122476101 | 463837306 | -676549352 | 未適用 |

全登録行success=true。test1にはFlyway書き込みを実行していない。

- publicのV1/V2履歴は全フィールド不変。
- test1の全履歴・構造（所有者/ACLを含む）は不変。
- 両スキーマの全業務データ不変。主キー順の行JSONとPostgreSQL型情報を保存し、NULL/文字列の区別・欠落・重複を含め比較。
- data-only SQLもCOPY行とその他のSQLに分けて比較し、シーケンスsetval値・is_calledを含め一致。
- public構造差分は次の1行だけ。制約・インデックス・コメント・所有者/ACLに追加差分なし。

```diff
-    schedule_content character varying(50) NOT NULL,
+    schedule_content character varying(50),
```

再実行は既存ランナーのapply-v4をそのまま再使用していない（適用後は同ランナーの事前条件で停止する設計）。
補助ReplayCheckをコンテナ内でコンパイルし、無変更のPublicV4Runnerの接続ガード・固定ファイルハッシュ・固定Flyway設定を再利用。
pending=[]を必須とし、validate→migrate→validateで適用0件を確認。
再実行前後で構造・業務データ・シーケンス・履歴の追加変更がないことも比較した。

## 証跡・変更ファイル

- tools/flyway/docker-isolation/restore_isolated.py：所有者/ACL保持、参照ロール棚卸し、データ出力順序に依存しない比較、新規名。
- tools/flyway/docker-isolation/verify_owned_workflow.py（新規）：段階ごとの証跡保存・比較、既存ランナー実行、再実行確認。
- docs/verification/schedule-content-v4-owned-write-result.md（本報告）
- docs/verification/schedule-content-v4-docker-write-result.md：今回の成功報告への参照追記。

証跡は専用evidence volumeのrestore-result.json、role-inventory.json、write-verification、after-v3、after-v4、after-replay、replayに保存。
業務データを含む証跡は権限制限を維持。ホストの復元結果JSONは${NON_DISTRIBUTED_EVIDENCE_DIR}/。
ガード単体40件・マウント単体10件、Python構文、git diff --check成功。
MavenのDB統合テストは既存DB保護のため実行していない。今回のFlyway検証はコンテナ内の実DB統合検証。

## 残るリスク・適用可否

修正済み既存ランナーによるV3省略登録・V4通常適用は、所有者を正しく復元した隔離環境で成功した。開発用publicへの適用手順を検討する技術的根拠は得られた。
ただし開発用publicの実適用は未実施。適用承認、最新バックアップ、接続先・停止状態・他接続/長時間トランザクション・実効権限・履歴/SQLハッシュの再確認が必要。
実環境の同時アクセス/長時間ロック、アプリのNULL保存・再取得・Home/Progressは今回未検証。test1のschedule_detailsは0件。
バックアップは収録public/test1の時点情報であり、現在のホストDBとの全体比較は今回行っていない。
コンテナは最終確認でrunning、pg_isready成功として保持。停止後の非空領域を現在のlifecycleで再起動・再初期化しない。
既存Flywayランナー・V1〜V4・application.propertiesは変更していない。Spring Boot、repair/baseline/clean、commit/push/mergeは行っていない。

## 公開用コピーの匿名化

バックアップの個人環境パスのみを匿名表記へ置換した。`${PRIVATE_BACKUP_DIR}`は非配布バックアップの保管先を示し、新規構築に必要な環境変数ではない。元の検証結果・件数・未実施範囲は変更していない。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
