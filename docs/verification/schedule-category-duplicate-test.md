# DIFF-32：スケジュール編集時の分類名重複検証（2026-10-18）

## 結果

隔離PostgreSQLと更新APIで不具合を再現した後、最小限の修正を実施した。同一スケジュールの異なる分類番号に同名分類を指定する場合、前後空白を除くと同名になる場合の両方で、修正前はHTTP 200となった。修正後は400 INVALID_INPUTで更新全体を拒否する。

バックエンド173件、フロントエンド210件が成功。型チェック・lint・Webpack build・git diff --checkも成功。開発用DBのpublic/test1には接続していない。Excel・V1～V4・DB構造・通常設定は変更していない。

## 事前確認・基準

- develop、既存の未コミット変更を確認して保持した。名称編集・コメント編集・検索条件維持など、以前の変更を削除・上書きしていない。
- 基本詳細設計書のSCD_画面編集2 E15は「30文字以内、重複禁止、最大99件」、SCD_イベント内容2 E13も同じ条件。テーブル定義書では分類番号がスケジュール内の主キーを構成し、分類名称に一意制約はない。
- design-implementation-diff.mdのDIFF-32、docs/api/schedules.md、ScheduleService、ScheduleCreateRequest/UpdateRequest、ScheduleDetail、既存API/UIテストを確認した。
- 新規登録APIはcategoryName.trim()後のdistinct件数を比較する。大文字小文字を区別し、Unicode正規化はしない。新規登録画面も既存のtrim比較であり、今回変更していない。

## 原因と修正前の再現

新規登録に分類名の重複検証がある一方、更新のvalidateCategoryRequestは分類番号・更新カウンタ・削除指定を検証するだけで、名称の重複を検証していなかった。編集画面の分類追加・名称変更・保存時にも同名検証がなく、DBの主キーは番号なので保存が成功した。

実Spring Boot MockMvcと実PostgreSQLを接続した更新APIテストを追加し、アプリ本体を変更する前に次の2ケースを実行した。

| 分類No.1 | 分類No.2 | 期待 | 修正前の実際 |
| --- | --- | --- | --- |
| Java研修 | Java研修 | 400 | 200：不具合再現 |
| Java研修 | 前後に半角空白を付けたJava研修 | 400 | 200：不具合再現 |

再現ログ：${NON_DISTRIBUTED_EVIDENCE_DIR}/category-reproduce.log。2件実行・期待値に対する失敗2件・実行エラー0。テストはトランザクション内の合成データであり、テスト終了時にロールバックした。この失敗は修正前の再現証跡で、修正後の失敗件数には加算しない。

## 修正ファイルと実装

1. src/main/java/com/training/portal/schedule/ScheduleService.java
   - 名称一覧をtrim後に比較するvalidateCategoryNamesを追加。
   - 新規登録の既存チェックを同じ共通処理へ移動し、判定を変更せず維持。
   - 更新時にも、追加・名称変更・削除を反映した最終分類集合を検証する。
   - 重複は既存のScheduleInvalidInputExceptionから400 INVALID_INPUT。DB更新に入る前に拒否し、名称・権限・日別内容等を部分保存しない。
   - 比較対象はリクエスト内の当該スケジュールの分類だけ。他スケジュールを横断した重複検証は行わない。
2. frontend/components/schedule-detail.tsx
   - 更新ボタン押下時、分類名を既存のtrimルールで比較。
   - 重複時は既存の更新エラー表示領域に「同じ分類名は登録できません」を表示し、PUTを送信しない。
   - 入力を保持する。分類名修正後は既存のエラークリア処理で解除し、再保存できる。
   - 既存の表示位置・画面デザイン・権限・未保存変更制御は維持した。
3. src/test/java/com/training/portal/schedule/ScheduleApiTest.java
   - 後述の5ケースを追加。
4. frontend/tests/schedule-detail-ui.test.tsx
   - 後述の3ケースを追加。
5. docs/api/schedules.md
   - 登録・更新の同一スケジュール内の重複禁止、前後空白除去、大文字小文字区別、Unicode正規化なし、別スケジュール同名可、400と更新全体拒否を追記。
6. docs/verification/schedule-category-duplicate-test.md
   - 本報告。

分類名保存値の追加正規化やDB制約は導入していない。新規登録は従来どおりtrimして保存し、更新の保存値処理は従来のまま。今回の共通化は重複比較ルールを揃えるもの。大文字小文字の統一、Unicode正規化などの新しい同一視ルールは追加していない。

## 追加テスト・更新拒否時の不変性

バックエンド追加5ケース：

- 同名分類への更新を400で拒否。
- trim後に同名となる更新を400で拒否。
- 非重複の名称変更・分類追加・課題の分類付け替え・スケジュール名と研修内容の更新が成功。他スケジュールに同名があっても成功。Java研修/java研修と、Unicodeの合成形/分解形の異なる名称も独立した名称として許可する。
- 新規登録の同名分類を従来どおり拒否。
- 新規登録のtrim後同名分類を従来どおり拒否。

拒否2ケースでは、分類の重複と同時にスケジュール名・新人の編集権限・研修内容の変更を送信した。対象課題には提出状況とコメントも準備した。要求前後で次の6テーブルの対象スケジュール行について全カラムを比較し、不変を確認した。

- schedule
- schedule_can_edit
- schedule_bunrui
- schedule_details
- issue_submission_statuses
- issue_submission_statuses_comment

比較は安定した順序で実施し、更新カウンタ・監査列も比較対象に含めた。拒否前に書き込まず全体を拒否するため、部分更新はない。テスト終了時の外側トランザクションのロールバックだけで成功扱いせず、HTTP要求の直後に不変性を検証している。

フロントエンド追加3ケース：

- 編集で既存分類と同名にするとエラー表示・PUTなし・入力保持。重複解消後は送信可能。
- 前後空白付きの同名でも同じ制御。
- 既存分類と同名の分類追加も保存時に拒否。

正常な分類削除・追加・名称変更、参照中分類の削除制御、同一PUTで分類を付け替えた後の削除、新規スケジュール登録、権限・課題解除・名称編集・Progress/Home連携は既存の回帰テストを併せて実行した。

## 実行環境・結果

新しいDocker環境は構築せず、既存のE2E専用PostgreSQL17.11クラスタを使用した。

- 接続：127.0.0.1:59455/training_portal、schema=test1。
- データ領域：${NON_DISTRIBUTED_EVIDENCE_DIR}/archived-run-2。
- 隔離専用の認証情報だけを使用。認証情報は報告書・ログ・コマンド引数へ出力していない。
- Flywayは起動引数で無効化。Flywayを直接実行するIssueStatusMasterMigrationTestは除外した。
- テストクラスの既存current_schema=test1確認とトランザクションを維持。

バックエンド実行条件：

```text
./mvnw clean test
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:59455/training_portal?currentSchema=test1
  -Dspring.flyway.enabled=false
  -Dtest=*,!IssueStatusMasterMigrationTest
```

既存の隔離横断テストを有効化するINTEGRATION_TEST_PORT=59455も指定した。パスワードは隔離専用の環境変数から渡した。

| 検証 | 結果 |
| --- | --- |
| 修正前の再現API試験 | 2ケースとも期待400に対し200、不具合再現 |
| 修正後バックエンド | 173件成功、失敗0、エラー0、スキップ0 |
| フロントエンド | 14ファイル210件成功、失敗0 |
| TypeScript型チェック | 成功 |
| lint | 成功 |
| Webpack build | 成功 |
| git diff --check | 成功 |

バックエンドのスキップ0は、明示除外したmigration試験まで実行した意味ではない。移行試験1クラスは未実施。

ログ：${NON_DISTRIBUTED_EVIDENCE_DIR}/category-final.log、category-frontend.log、category-types.log、category-lint.log、category-build.log。Webpackは既存のフロントエンド一時コピーで実施し、今回のschedule-detail.tsxを反映した。隔離API URL向けのビルドであり、本番の環境変数設定まで検証したものではない。next/fontエラーは発生していない。

## DB影響・終了状態・残る確認事項

開発用DB5432のpublic/test1、既存検証DBへは接続していない。API検証はMockMvc経由で実Serviceと実隔離DBを使用し、HTTPネットワーク経由の実ブラウザ検証ではない。テスト用合成データはトランザクションでロールバックし、既存データの削除・上書きはしていない。Flyway V1～V4・DB構造・Excel・application.propertiesは変更していない。

検証に起動した専用PostgreSQLはfast shutdownで正常停止し、専用データ・ログは保持した。既存Dockerを削除・再構築していない。

DIFF-32の重複拒否と必須テストは確認できた。実ブラウザでのエラーの視認性・キーボード操作は今回未実施で、必要なら手動確認する。同名判定に大文字小文字無視やUnicode正規化を追加する場合は別仕様変更になるため、今回の範囲に含めていない。

既存design-implementation-diff.mdは調査当時の記録として保持し、本報告がDIFF-32の再現・修正結果となる。commit/push/mergeは行っていない。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
