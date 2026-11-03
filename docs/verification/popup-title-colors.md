# ポップアップタイトル色の統一（2026-10-12）

共通MessageDialog/ConfirmDialogに任意のkind（error / warning / confirmation / information / success）を追加。タイトル文字列やdestructive指定から種別を推測しない。共通定義でエラー#DC2626、警告#D97706、確認・情報#054A99とする。成功・未指定は既存の青を維持。本文・ボタン・背景・枠線・余白・サイズ・配置・文言・イベントは変更していない。

分類は呼び出し処理の意味に基づく。課題解除拒否、分類削除不可、実績利用中メンバー削除不可、APIによる削除拒否はerror。ピン留め最大2件は既存仕様の警告としてwarning（事前判定・API応答とも）。削除確認、基本情報解除確認、未保存変更の確認はconfirmationの既定値を使用する。単に削除に関係するという理由でwarningにはしない。

ログイン・Home・Progress・メンバー管理の入力／権限／APIエラーは既存の画面内表示を維持。共通ポップアップを新設していない。登録成功のwindow.alert、ログアウト・メンバー削除・コメント削除のwindow.confirmはブラウザ標準UIでタイトル色を制御できないため、既存デザイン・動作を維持する今回の範囲では置き換えていない。

変更ファイル：
- frontend/components/message-dialog.tsx
- frontend/components/schedule-detail.tsx
- frontend/components/schedule-editor.tsx
- frontend/app/(app)/schedule/page.tsx
- frontend/tests/message-dialog.test.tsx（新規）
- frontend/tests/schedule-detail-ui.test.tsx
- frontend/tests/schedule-ui.test.tsx
- 本報告

検証：フロントエンド14ファイル190件成功、失敗0。型チェック・lint・git diff --check成功。共通コンポーネントで全種別・未指定の色、OK／キャンセル／Escape／pending動作を確認。課題解除エラーの赤とピン留め警告のオレンジを画面テストでも確認した。

標準pnpm run buildはサンドボックス内外でTurbopackのポート作成Operation not permittedにより失敗。pnpm exec next build --webpackは成功。バックエンド・API仕様・DB・業務ロジックは変更しておらず、今回バックエンドテストは実行していない。

手動確認：実ブラウザでの色とレイアウトの視認確認。標準Turbopackビルドの環境問題は継続課題。commit/push/merge未実施。
