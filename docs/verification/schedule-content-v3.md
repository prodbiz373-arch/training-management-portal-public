# スケジュール内容任意化の検証（2026-10-10）

## 実装

分類・講師は必須のまま、内容を任意に変更。NULL・空文字・空白のみはDBのNULLへ正規化。
内容未入力でtaskFlg=trueの登録・更新APIは400。画面では内容未入力時に課題チェックを無効化し、内容を消すとチェックを外す。
新規登録・詳細編集の分類と講師ヘッダーに赤い必須マークを表示。

## 今回変更したファイル

既存の未コミット作業を保持し、以下のファイルに今回の変更を追加した。

- src/main/resources/application.properties
- src/test/resources/application.properties
- src/main/resources/db/migration/V3__allow_null_schedule_content.sql（新規）
- src/main/java/com/training/portal/schedule/ScheduleDetail.java
- src/main/java/com/training/portal/schedule/ScheduleService.java
- src/main/java/com/training/portal/schedule/dto/ScheduleCreateRequest.java
- src/main/java/com/training/portal/schedule/dto/ScheduleUpdateRequest.java
- frontend/components/schedule-editor.tsx
- frontend/components/schedule-detail.tsx
- frontend/lib/schedule-api.ts
- frontend/lib/home-api.ts
- src/test/java/com/training/portal/schedule/ScheduleApiTest.java
- src/test/java/com/training/portal/home/HomeApiTest.java
- src/test/java/com/training/portal/progress/ProgressApiTest.java
- frontend/tests/schedule-create-ui.test.tsx
- frontend/tests/schedule-detail-ui.test.tsx
- docs/api/schedules.md
- docs/verification/schedule-content-v3.md（本報告）

## DB分離と結果

指定PID 19253・19377をpsで再確認した時点では両方とも存在せず、killは実行していない。

V3作成前に通常起動のFlyway target=2を設定・確認。テストは接続URLのcurrentSchema、Flywayのdefault-schema/schemas、Hibernateのdefault_schemaをtest1に設定し、target=latestにする。
V3自身もcurrent_schema()がtest1以外ならDDL前に例外を発生させ、DDL対象をtest1.schedule_detailsと明示している。
通常アプリは起動せず、テスト起動のFlywayでtest1だけにV3を適用した。

| 確認項目 | public | test1 |
| --- | --- | --- |
| Flyway適用済み | V1・V2 | V1・V2・V3（成功） |
| schedule_contentのNULL許可 | NO（変更なし） | YES |

publicの構造・データをpg_dump --schema=publicで前後比較し、一致した。
比較時はpg_dumpが生成するランダムなrestrict/unrestrictキー行のみ除外した。
正規化後のSHA-256は前後とも以下。

d90e060c04fb29470410235efb0c992907bc8a0ee75f462e2a1e985faf651c5e

V1・V2は変更せず、作業前後のSHA-256一致も確認した。

## テスト

- ./mvnw clean test：148件成功、失敗・エラー・スキップ0。DB統合テストはtest1。
- pnpm test：13ファイル・171件成功。
- pnpm run typecheck：成功。
- pnpm exec next build --webpack：成功。
- git diff --check：成功。

標準のpnpm run build（Turbopack）は内部ポート作成のOperation not permittedで失敗したため、webpack方式で本番ビルドを確認した。
最初のバックエンドテストはサンドボックスのDB接続拒否で失敗したため、許可された再実行で全件成功した。

NULL・空文字・空白のみの登録と再取得、NULLへの更新と再編集、未入力課題の登録・更新400を確認。
HomeはNULLの通常行を返却でき、課題集計が変わらないことを検証。
ProgressはNULL内容の通常行を課題対象に含めず、既存APIテストが成功。
画面はNULL内容の表示・編集、課題チェックの無効化と自動解除、内容未入力での登録を検証。

## 残事項

publicへのV3適用は未実施・未承認。publicでは内容カラムのNOT NULLが維持されるため、任意入力のDB保存の検証範囲はtest1。
commit・push・mergeは実行していない。
