# スケジュール名編集の実装・検証（2026-10-14）

## 実装と互換性

developで開始し既存差分を確認した。基本詳細設計書SCD_画面編集2 D10「新規/編集とも可」、E10「必須、40文字以内」を確認し、名称だけを更新対象に追加した。Excelは変更していない。

ScheduleUpdateRequestに必須scheduleName（@NotBlank、@Size(max=40)）を追加。既存の認可・scheduleUpdateCount・楽観ロック・トランザクションの中で同じschedule行を更新する。関連データの削除・再作成や名称の画面別複製は追加していない。ID・開始終了日・休日設定の更新は認めない。

画面はcanEdit=trueの社員だけ名称編集可能。既存レイアウトを維持しmaxLength=40と保存時の必須・長さチェックを追加。名称を未保存変更比較・クリア・保存リクエストへ含め、保存成功後のGETで最新名称を反映する。失敗時は入力値と未保存警告を保持する。

API互換性上の変更：PUTのscheduleNameが新たな必須項目。旧形式の名称未指定リクエストは400 INVALID_INPUTとなる。名称を変更しない場合も現在の名称を送信する。リポジトリ内の呼び出し元・テストは更新済み。外部クライアントの存在は未確認のため、配布前に確認・同時更新が必要。

## 変更ファイル

- src/main/java/com/training/portal/schedule/Schedule.java
- src/main/java/com/training/portal/schedule/ScheduleService.java
- src/main/java/com/training/portal/schedule/dto/ScheduleUpdateRequest.java
- frontend/components/schedule-detail.tsx
- frontend/lib/schedule-api.ts
- src/test/java/com/training/portal/schedule/ScheduleApiTest.java
- src/test/java/com/training/portal/integration/ScheduleProgressHomeIntegrationTest.java（既存の未コミット結合テストへ、現在の内容を保持したまま関連ケースを追加）
- frontend/tests/schedule-detail-ui.test.tsx
- frontend/tests/schedule-api.test.ts
- docs/api/schedules.md
- 本報告

既存frontend/next-env.d.tsの未コミット内容はSHA-256不変。その他の未コミット資料・補助ツールも削除していない。

## テスト

- バックエンド163件成功、失敗0、エラー0。前回作成した専用PostgreSQL17.11、127.0.0.1:49895/training_portal/test1だけを利用。新しいクラスタ・Dockerは作成していない。
- ./mvnw clean testを隔離URL・spring.flyway.enabled=falseで実行。Flywayを直接実行するIssueStatusMasterMigrationTestの2件は除外・未実施。target設定は変更していない。
- 新人・講師の編集権限あり名称更新、40文字、空欄・空白・41文字・NULL・名称未指定の拒否、他の基本情報不変、関連明細不変を確認。
- 結合テストで編集権限なし403、名称更新成功、一覧・詳細・Home・Progressの新名称、古いversionによる409と名称不変を確認。その後も既存の進捗更新・コメントCRUD・課題解除・Home集計のフローが成功。
- フロントエンド14ファイル195件成功、失敗0。名称の未保存警告・元に戻した場合の警告解除・送信内容・成功後反映・入力境界・保存失敗時の保持を確認。
- 初回フロント試験は旧仕様（名称欄disabled／名称を送らない）を前提にした2件が失敗。確定した新仕様に期待値を更新し全件成功。
- TypeScript型チェック・lint・git diff --check成功。
- Webpack build成功。既存next-env.d.tsを保護する一時コピーで実施。今回next/fontエラーは再発しなかった。既存ロックファイル・依存バージョンは変更していない。

ログ：${NON_DISTRIBUTED_EVIDENCE_DIR}/backend-name.log、${NON_DISTRIBUTED_EVIDENCE_DIR}/name-frontend.log、${NON_DISTRIBUTED_EVIDENCE_DIR}/name-typecheck-final.log、${NON_DISTRIBUTED_EVIDENCE_DIR}/name-lint.log、${NON_DISTRIBUTED_EVIDENCE_DIR}/name-webpack.log。

## DB保護

開発用5432/public・既存test1へ接続・テストデータ投入を行っていない。試験データは専用クラスタのトランザクション内だけで作成しロールバック。隔離DBの試験前後plainダンプは、pg_dumpのrestrict/unrestrictランダムキー行だけを除いて完全一致。所有者・ACL・構造・全データ・シーケンス・Flyway履歴を含むSHA-256は前後とも127d258b388c8339db4739e3ce8765150d90790fc35de3cfa620c161d80febca。

試験後は専用PostgreSQLだけ正常停止。データ・証跡は保持。V1〜V4・通常/テストapplication.propertiesはSHA-256不変。DB構造変更・migration・repair/baseline/cleanは実行していない。

## 残る確認

実ブラウザで名称入力、クリア、画面移動時の警告、保存エラー後の入力保持、各画面へ移動した際の新名称を確認する。API・コンポーネント試験で確認済みだが、実ブラウザE2Eは未実施。外部クライアントがあれば必須フィールド追加への対応が必要。すでに表示中の別ブラウザ画面へ即時配信する機能は追加しておらず、再取得時に最新名称を反映する。

commit/push/mergeは行っていない。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
