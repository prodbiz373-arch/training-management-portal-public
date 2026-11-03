# 課題解除制御の実装・検証（2026-10-12）

## 実装

対象スケジュール・日付番号に紐づく全受講者の提出状況とコメントを判定する。既存V2マスタの未提出は1。未提出以外が1件以上、またはコメントが1件以上ある日付を解除禁止とする。提出状況なしは未提出扱いで、提出状況・コメントを自動削除しない。

GET/PUTの詳細レスポンスへ日別の`taskRemovalBlocked`を追加した。画面はON→OFFと内容空欄化（空白のみを含む）を拒否し、変更直前の内容とチェックを保持する。既存MessageDialogを再利用し、指定タイトル・メッセージ・OKを表示する。レイアウト・スタイルは変更していない。解除可能なら従来の内容空欄化・自動解除を維持する。

PUTでは同じRepository判定を再実行する。false、null、行省略、内容空欄化による解除を検出し、禁止対象なら400 INVALID_INPUTと指定メッセージを返す。nullフラグと行省略は、解除禁止対象でなくても既存の必須・行集合制約として拒否する。保存前の判定は変更前に完了し、失敗時はトランザクションがロールバックする。認可・更新カウンタの検証は維持する。

課題解除と提出状況・コメント追加更新が競合しないよう、ScheduleServiceとProgressServiceは同じ課題行の排他ロックを取得する。Schedule側は日付番号順に取得する。楽観ロックも維持する。解除後の再ONは許可するが提出状況を自動生成しない。

## 変更ファイル

- `src/main/java/com/training/portal/schedule/ScheduleService.java`
- `src/main/java/com/training/portal/schedule/ScheduleDetailRepository.java`
- `src/main/java/com/training/portal/schedule/dto/ScheduleDetailResponse.java`
- `src/main/java/com/training/portal/schedule/dto/ScheduleUpdateRequest.java`
- `src/main/java/com/training/portal/schedule/exception/ScheduleTaskRemovalBlockedException.java`（新規）
- `src/main/java/com/training/portal/progress/ProgressService.java`
- `src/main/java/com/training/portal/common/api/ApiExceptionHandler.java`
- `frontend/components/schedule-detail.tsx`
- `frontend/lib/schedule-api.ts`
- `src/test/java/com/training/portal/schedule/ScheduleApiTest.java`
- `frontend/tests/schedule-detail-ui.test.tsx`
- `frontend/tests/schedule-api.test.ts`
- `docs/api/schedules.md`
- 本報告

既存の未コミット変更は保持した。Flyway SQL、application.properties、DB構造は今回編集していない。

## 検証

- バックエンド全157件成功：`./mvnw clean test -Dspring.flyway.target=3`。test1のみ使用し、V4の追加適用を防ぐためtarget=3に固定。
- フロントエンド全176件成功：`pnpm run test`。
- `pnpm run typecheck`、`pnpm run lint`成功。
- `pnpm exec next build --webpack`成功。
- 標準`pnpm run build`はTurbopackのポート作成でOperation not permitted。サンドボックス外でも同じエラー。標準ビルド成功とは扱わない。
- `git diff --check`成功。

API試験では未提出のみ・レコードなしの許可、他受講者の非未提出・コメント・両方による拒否、false/null/行省略/空欄化の直接更新拒否、拒否後の内容・課題フラグ・提出状況・コメントの保持、他スケジュール・他日付の除外を確認した。既存の新規登録・編集・権限・楽観ロック・Home/Progress試験も成功した。

画面試験ではチェック操作・空文字・空白による解除を拒否し、指定ポップアップを閉じても元の内容とON状態を保持することを確認。従来の解除可能なNULL内容編集・空欄化試験も成功した。解除可否欠落レスポンスはINVALID_RESPONSEとして拒否する。

## 残る確認事項

実ブラウザでの操作感・フォーカス、複数利用者による同時操作時のロック待ちを手動確認する。排他ロックを含む通常API試験は実施したが、同時トランザクション負荷試験は未実施。フロントの事前判定は取得時点の情報であり、その後の変更は保存時APIが再判定する。標準Turbopackビルドの実行環境問題は未解消。

前回の検証で起動したSpring Bootは再ビルド前に停止した。今回、開発用publicへのテストデータ書き込みやSpring Boot再起動は行っていない。commit/push/mergeは行っていない。

## 承認後の再実行

テスト設定と既存API記述・フロントテスト構造を再確認した。接続先は読み取り専用照会でtraining_portal/test1、training_portal_userと確認。`./mvnw clean test -Dspring.flyway.target=3`を再実行し、157件成功、失敗0、エラー0、スキップ0。実行ログでもtest1の現在バージョン3、追加migrationなしを確認した。

追加済みフロントテストを含む`pnpm exec vitest run tests/schedule-api.test.ts tests/schedule-detail-ui.test.tsx`は55件成功、失敗0。今回は実装・API仕様・テストコードを追加変更せず、本報告だけ追記した。

実行前後のpublic/test1のplainダンプを比較。pg_dump生成のrestrict/unrestrictキー行だけを除き、構造・所有者・ACL・全データ・シーケンス・Flyway履歴が一致した。SHA-256は前後とも`01c2d6abca8f23186e3502aa43cc648e303e17705ea07a7b8ae4f8af41bc31cf`。比較記録は`${NON_DISTRIBUTED_EVIDENCE_DIR}`、バックエンドログは`${NON_DISTRIBUTED_EVIDENCE_DIR}/task-removal-backend.log`。DBへの永続変更なし。commit/push/merge未実施。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
