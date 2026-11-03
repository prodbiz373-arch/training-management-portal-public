# Flyway V4適用後の起動・動作確認（2026-10-12）

## 結果

通常設定でSpring Boot起動成功。publicに専用テストユーザー・テストスケジュールを作成し、実HTTP APIでNULL登録/編集・入力チェック・Home/Progressを確認した。
テストデータ後片付け後、public/test1の構造・全データ・シーケンス・履歴・所有者・ACLを含むダンプが検証前と一致。
既存業務データ・提出状況・コメントを変更・削除していない。Flyway履歴・SQL・DB構造も変更していない。

## バックアップ永続保管

保存先：
`${PRIVATE_BACKUP_DIR}/training_portal-public-test1.dump`

SHA-256：
`9157826593804e0c20b1b4de1c4ab08dcfcb398bafc6886fb9b4b163649fbdde`

コピー前後のハッシュ一致。Git管理外のユーザー領域。専用ディレクトリ700、ファイル600。元の/private/tmpバックアップは保持。
これはV4適用前のバックアップであり、現在状態の新規バックアップとは区別する。別媒体への冗長化は未実施。

## 起動前・起動結果

必要なTRAINING_PORTAL_DB_PASSWORD環境変数の存在を値を出さず確認。読み取り専用接続でDB名training_portal・ユーザーtraining_portal_user、public履歴V1〜V4、test1履歴V1〜V3を確認。
既存の適用後検証に加え、通常application.propertiesのpublic/default-schema、target=2、Hibernate validateを確認。起動で4件validate成功、public up to date / No migration necessaryを記録。

./mvnw spring-boot:runによる通常起動に成功。最終アプリPID92727、Maven起動PID92605、HTTP localhost:8080。検証終了後も稼働状態で保持。
初回はSpring Boot起動と./mvnw clean testを並行実行したため、target配下クラス削除とクラスロードが競合しAuthController$CsrfResponseのClassNotFoundExceptionが発生した。実行順序の誤りであり、アプリコードは変更せず、テスト完了後に今回起動したプロセス群だけを終了して起動し直した。
2回目の起動が成功し、Flywayによる追加変更は両方ともなし。今後はcleanとアプリ起動を並行実行しない。

## public実HTTP API確認

既存アカウントの資格情報を利用・変更せず、乱数パスワードをBCrypt化した専用講師/新人各1人を作成。パスワード・セッション/CSRF情報はログへ出していない。
専用スケジュール4件だけを登録・編集。実行IDは権限制限したlive-api-result.jsonに記録。

- 内容NULL、空文字、空白/タブのみでPOST 201。
- GET再取得でcontent=null。DB SELECTでもschedule_content IS NULL=true。
- 内容あり・課題ありでPOST 201。
- 内容あり→空欄/課題falseへのPUT成功、NULLへ正規化。
- 空欄→内容再入力のPUT成功。
- taskFlg=trueかつNULL/空文字/空白のPOST、NULLのPUTは400。
- 分類未指定、講師未指定のPUTは400。
- NULLの通常行4件をHome APIが返却。taskFlg=false、テストユーザーの課題集計は0。
- 各テストスケジュールのProgress APIは200、items=[]。内容なしの行が課題にならない。
- テストスケジュールのissue_submission_statuses生成は0件。

後片付けは今回作成した4スケジュールだけをAPI DELETEで削除し、今回作成した専用ユーザー2人だけをIDとscreen_id=V4_VERIFYで限定削除。既存レコードは操作していない。

## UI・Home・Progress

新規/編集の既存コンポーネントテストで、内容未入力/空白時の課題チェック無効化、チェック後の内容消去による自動解除、NULL表示/編集、内容任意登録を確認。
分類/講師の必須チェックとヘッダー必須マークの既存実装も確認。APIの必須判定はpublic実APIでも検証済み。
Home UIに「NULL内容の通常行を表示しても課題リンクを作らず既存集計を維持する」回帰テストを追加し成功。
Progressはpublic実APIと既存画面/APIテストで確認。内容NULLの通常行を課題対象へ含めず、既存課題の表示・認可・状態/コメント処理のテストが成功。
画面確認はjsdomのReactコンポーネント試験。実ブラウザによるpublic API接続状態での描画・クリック操作は今回未実施。

提出状況のある既存課題の内容変更は実行せず、ScheduleService.validateDetailRequestを読解した。
内容未入力+課題trueは拒否され、提出状況がある行の課題フラグ変更も拒否される。既存提出状況/コメントを削除する処理を今回実行していない。

## 自動テスト

| 項目 | 結果 |
| --- | --- |
| ./mvnw clean test -Dspring.flyway.target=3 | 148件成功、失敗/エラー/スキップ0 |
| pnpm test（追加後の最終実行） | 13ファイル・172件成功 |
| pnpm run typecheck | 成功 |
| pnpm run lint | 成功 |
| pnpm exec next build --webpack | 本番ビルド成功 |
| git diff --check | 成功 |

バックエンドは既存test1を使用し、コマンドラインでtarget=3を指定。テスト設定のlatestではV4がtest1に適用されるため、今回禁止された履歴/構造変更を防ぐ目的で固定した。既存テストのFlyway再migrateも適用0件。設定ファイルは変更していない。
テストデータは既存トランザクショナルテストのロールバックで除去。検証後ダンプ一致で確認。

標準pnpm run buildは、初回はフォント取得ネットワーク制限で失敗。許可された再実行でもTurbopackの内部ポート作成Operation not permittedで失敗した。
webpack方式では本番ビルド成功。標準Turbopack方式の成功とは扱わない。アプリ仕様/実装の不具合は検出していないため製品コードの修正は不要と判断した。

## DB保護・証跡

検証前後にpublic/test1を含むplainダンプを取得。
比較除外はランダムrestrict/unrestrictキー行のみ。構造・全データ・シーケンス・所有者・ACL・Flyway履歴を含め一致した。
既存の課題/提出状況/コメント不変もこの比較に含まれる。

証跡：${NON_DISTRIBUTED_EVIDENCE_DIR}
- before.sql / after.sql（業務データを含むため公開しない）
- spring-boot.log / spring-process.json
- live-api-result.json
- backend-tests.log / frontend-final.log / typecheck-final.log / lint-final.log
- build.log / build-retry.log / build-webpack.log

ディレクトリ700。補助処理は${NON_DISTRIBUTED_EVIDENCE_DIR}/FixtureHash.javaに保存。認証情報そのものをファイルや報告に記載していない。

## 今回変更したリポジトリファイル

- frontend/tests/home-ui.test.tsx：NULL通常行の表示と課題リンク/集計の回帰テスト追加。
- docs/verification/schedule-content-v4-post-apply-test.md：本報告。

既存の未コミット作業は保持。V1〜V4、既存Flywayランナー、application.propertiesは変更していない。
DB構造変更、提出状況/コメント削除、Docker削除、バックアップ削除、commit/push/mergeは行っていない。

## 未解決・手動確認

- 標準Turbopack buildの内部ポート権限問題。webpackビルド成功済み。
- 実ブラウザで分類/講師の赤い必須マーク、未入力/空白のチェック無効化、自動解除、NULL編集の操作感を確認。
- public APIへ接続したHome/Progressの見た目・遷移を確認。
- 今後バックエンドテストを無指定で実行するとtest1のtarget=latestによりV4が適用され得る。履歴変更を許可しない検証では今回と同じtarget=3指定が必要。
- 通常target=2は起動可能と実証したが、新規環境構築手順との整合は別途運用として整理する。

## 公開用コピーの匿名化

バックアップの個人環境パスのみを匿名表記へ置換した。`${PRIVATE_BACKUP_DIR}`は非配布バックアップの保管先を示し、新規構築に必要な環境変数ではない。元の検証結果・件数・未実施範囲は変更していない。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
