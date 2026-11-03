# 開発用public Flyway V3省略登録・V4適用結果（2026-10-12）

## 結果

ローカル開発用training_portalのpublicだけに、既存専用ランナーでV3省略登録とV4通常適用を実施し、検証に成功した。
public.schedule_details.schedule_contentはVARCHAR(50) NULL許可になった。
test1の構造・履歴、両スキーマの全業務データ・シーケンス・所有者・ACLは変更なし。
Spring Bootは起動していない。application.propertiesも変更していない。

## 適用前確認

接続はlocalhost:5432、サーバーアドレスIPv6 loopback ::1、DB名training_portal、current_schema=public、current_user=training_portal_user、server_version_num=170011。
照会とダンプにはdefault_transaction_read_only=onを設定。照会はBEGIN READ ONLY内で実施した。
プロセス情報から対象Spring Boot/Maven/Flywayが起動していないことを確認。実行直前にも再確認した。
他のclient backendは0、prepared transactionは0、当該DBの待機ロックは0。
対象テーブル所有者training_portal_user、public履歴V1/V2、test1履歴V1/V2/V3、チェックサム・success・型・NOT NULLが期待どおり。
既存ランナー2個とV1〜V4 SQLは隔離検証時run-005/manifest.jsonのSHAと一致。固定7JARのSHA検査にも成功。

最初の補助処理は、IPv6のCIDR付き表示と履歴照会の並び順カラム不足で変更前に停止した。補助処理を修正して再実行した。
その後の初回バックアップは成功したが、ホストPostgreSQL版表示にDebian接尾辞がないため証跡保存前に停止。版境界を確認する処理へ修正した。
いずれもFlyway書き込み前であり、その際のバックアップも保持。DBのロールバック/復元や既存ランナーの修正は行っていない。
最終実行では安全確認からやり直し、以下のバックアップを改めて取得した。

## 最新バックアップ

保存先：`${PRIVATE_BACKUP_DIR}/latest/training_portal-public-test1.dump`
SHA-256：`9157826593804e0c20b1b4de1c4ab08dcfcb398bafc6886fb9b4b163649fbdde`
サイズ：96,388 bytes。ファイル600、ディレクトリ700。

pg_dump custom形式、対象public/test1、業務データ・シーケンス・履歴・所有者・ACLを収録。
pg_dump成功、ファイル存在・非ゼロ、pg_restore --list成功、全SQLの/dev/nullへの読み出し成功、SHA取得成功。
Git管理外。バックアップは削除していない。クラスタ共通ロール等は含まない。
一時領域のため長期保管は保証されない。アクセス制限した永続領域へのコピーとハッシュ照合が今後の保管対応として必要。
この最新バックアップの別DBへの実復元は今回未実施（以前のバックアップの隔離復元は検証済み）。

前の取得済みバックアップも保持：
`${PRIVATE_BACKUP_DIR}/previous/training_portal-public-test1.dump`
SHA-256：a8da77729331fa8e0fcd076a3f9ef4ba44b99261655a34805c23a0919ee795a6。

## 実行結果

既存tools/flyway/run-public-v4.pyとPublicV4Runner.javaを無変更で使用。
Flyway12.4.0、接続先DB/schema/user固定ガード、SQLハッシュ、履歴、他接続0、lock_timeout=5s、statement_timeout=30sを確認。
V3とV4はそれぞれ独立したPython/Javaプロセスで実行した。V4へskip=trueは引き継いでいない。

| 段階 | target | skipExecutingMigrations | pending | 結果 |
| --- | --- | --- | --- | --- |
| dry-run V3確認 | 3 | true | [3] | 成功、読み取り専用 |
| dry-run 登録前V4確認 | 4 | false | [3,4] | 成功、V3登録後の確認は別途実施 |
| V3省略登録 | 3 | true | [3] | 登録1件、validate成功 |
| V4通常適用 | 4 | false | [4] | 適用1件、validate成功 |
| 再migrate | 4 | false | [] | 適用0件、前後validate成功 |

V3後：内容カラムはNOT NULLのまま。public構造は完全一致。業務データ・シーケンス・test1は不変。既存V1/V2履歴の全フィールド不変で、V3だけ追加。
V3のtest1専用SQLをpublicで実行せず、省略設定で履歴登録したことを実行設定と中間状態で確認。
V4後：内容カラムNULL許可へ変化。構造差分は下記1行だけ。

```diff
-    schedule_content character varying(50) NOT NULL,
+    schedule_content character varying(50),
```

## 最終履歴

| schema | version | checksum | success |
| --- | --- | --- | --- |
| public | 1 | 1122476101 | true |
| public | 2 | 463837306 | true |
| public | 3 | -676549352 | true |
| public | 4 | 1630550406 | true |
| test1 | 1 | 1122476101 | true |
| test1 | 2 | 463837306 | true |
| test1 | 3 | -676549352 | true |

test1へのマイグレーションは実行していない。

## データ・シーケンス・構造比較

実行前、V3後、V4後、再migrate後に両スキーマの証跡を取得。
業務テーブルは主キー順でJSON化し、PostgreSQL型情報・主キー・NULLを保持して比較。全行の欠落・重複を含め一致。
加えて履歴を除くdata-onlyダンプをCOPYブロックごとに、行の重複を保持して順序正規化し比較。シーケンスsetval値/is_calledも一致。
構造は所有者・ACLを含むschema-onlyダンプで比較。ランダムrestrict/unrestrictキーだけを除外し、publicの指定1行以外は一致。
既存履歴全フィールドとtest1全履歴も一致。

| 業務テーブル | public前後 | test1前後 |
| --- | ---: | ---: |
| kenshu_member | 6 | 0 |
| schedule | 5 | 0 |
| schedule_can_edit | 14 | 0 |
| schedule_bunrui | 3 | 0 |
| schedule_details | 8 | 0 |
| issue_statuses | 7 | 7 |
| issue_submission_statuses | 2 | 0 |
| issue_submission_statuses_comment | 2 | 0 |

再実行は、隔離検証済みReplayCheck補助処理をローカルパスに合わせてコンパイルし、無変更の既存ランナーの接続・版・ファイル・設定ガードを再利用。
pending=[]確認後、validate→migrate(0件)→validateを実施。再実行前後の全証跡にも差分なし。
既存apply-v4モードそのものは適用後の履歴を受け付けないため、再実行には使用していない。

## Spring Boot起動前

現在のapplication.propertiesはspring.flyway.target=2、schemas/default-schema=public、Hibernate ddl-auto=validate。
V4適用後、Flyway12.4.0の読み取り専用接続でtarget=2のvalidate成功、pending=[]を確認した。targetを下げても今回の履歴を巻き戻す操作は発生しない。
この設定だけを理由に必須の変更があるとは確認されなかった。ただしtarget=2はV4まで適用済みという運用状態を表さず、新規環境をV4まで構築する設定にもならない。
提案は、別途設定変更を許可された段階で通常targetを4へ更新すること。新規publicでは引き続きV3だけを省略登録する手順が必要で、単にtarget=4へ変えて初回起動してはならない。
今回設定は変更していない。Spring Boot起動全体・アプリの動作は未検証。

## 証跡・変更ファイル

証跡ディレクトリ：`${NON_DISTRIBUTED_EVIDENCE_DIR}`
backup.list、backup-sha256.txt、dry-run.log、skip-v3.log、apply-v4.log、replay.log、success.json、段階別write-verification/after-v3/after-v4/after-replayを保持。
主キー順JSON・ダンプには業務データを含むため、権限制限を維持し公開しない。

今回追加：
- tools/flyway/apply-public-v4-verified.py（バックアップ・事前確認・既存ランナー呼び出し・比較の補助処理）
- docs/verification/schedule-content-v4-public-apply-result.md（本報告）

既存Flywayランナー、V1〜V4、application.propertiesを変更していない。
Python構文とgit diff --check成功。MavenのDB統合テストはtest1への変更を避けるため実行していない。
repair/baseline/clean、自動ロールバック/復元、Spring Boot起動、Docker操作、commit/push/mergeは行っていない。

## 残るリスクと次の確認

- バックアップ/証跡の永続保管。
- 設定変更の要否を運用として確定し、別途許可された段階でSpring Boot起動を確認。
- 内容NULL/空文字/空白のみの保存・再取得・編集、未入力task_flg=1の400を確認。
- 分類/講師必須、課題チェックの無効化・自動解除、Home/Progressの表示・集計を確認。
- 実アクセス・長時間ロック下の挙動は今回未検証。

開発用publicへの今回のDB適用と整合性検証は完了。アプリの実動作検証は次段階。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
