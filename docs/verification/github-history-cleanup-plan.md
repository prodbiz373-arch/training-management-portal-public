# Git管理対象とGit履歴整理の事前検証（2026-10-18）

## 1. 評価・集計

公開候補の選別と独立コピーでの静的検証まで実施した。履歴の書き換え・コミット作成・リモート操作・クリーン構築は実施していない。GitHubの公開設定は操作していない。Public化可能との最終判定は保留する。

開始時の追跡済み202件、未追跡79件、合計281件を個別に分類した。非公開17件を除く264件を公開候補の独立スナップショットへコピーした。今回新規作成する本報告書も候補に加える場合は265件となる。これはGit登録済みという意味ではない。Excel2件は候補に含むが、表示・印刷品質の手動承認待ち。

## 2. Git管理対象の最終候補一覧

全ファイルの追跡・変更状態と採否を下表へ記録する。変更ありには未追跡も含む。公開候補は内容監査・最終承認待ちの選別案であり、すべてを無条件で公開可能としたものではない。

| ファイル | Git状態 | 変更あり | 分類 | 理由 |
| --- | --- | --- | --- | --- |
| .gitattributes | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| .gitignore | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| .mvn/wrapper/maven-wrapper.properties | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| AGENTS.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| README.md | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/api/auth.md | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/api/home.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/api/members.md | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/api/progress.md | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/api/schedules.md | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/design/テーブル定義書.xlsx | 追跡済み | あり | 公開候補（表示・印刷承認待ち） | 最新匿名化・画像更新版。Excel表示・遷移図の手動確認未完了 |
| docs/design/基本詳細設計書.xlsx | 追跡済み | あり | 公開候補（表示・印刷承認待ち） | 最新匿名化・画像更新版。Excel表示・遷移図の手動確認未完了 |
| docs/implementation/frontend-auth.md | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/implementation/frontend-members.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/implementation/member-service.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/setup/fresh-environment.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/setup/initial-instructor-setup.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/05-name-error.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/08-progress-unsubmitted.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/10-home-completed.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/14-trainee-comment.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/15-other-author-no-delete.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/18-blocked-content-clear.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/23-api-network-error.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/25-expired-auth-redirect.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-evidence/26-home-without-task.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/browser-e2e-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/comment-edit-ui-evidence/comment-edit-other-author.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/comment-edit-ui-evidence/comment-edit-saved.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/comment-edit-ui-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/design-final-quality-check.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/design-final-update-report.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/design-implementation-diff-final-review.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/design-implementation-diff.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/diff-23-24-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/excel-final-consistency-review.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/excel-image-replacement-plan.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/excel-image-replacement-result.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/excel-visual-print-final-review.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/flyway-v5-application-plan.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/flyway-v5-application-result.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/fresh-environment-setup-review.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/fresh-environment-setup-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/github-public-document-cleanup.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/github-public-file-selection.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/github-public-preparation-result.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/github-public-readiness-review.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/initial-instructor-setup-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/integration-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/member-role-change-session-design.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/member-role-change-session-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/popup-title-colors.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/progress-comment-filter-evidence/progress-filter-after-delete.png | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/progress-comment-filter-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-category-duplicate-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v3.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v4-docker-build.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-docker-plan.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-docker-restore.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-docker-test.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-docker-write-result.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-docker-write-test.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-lifecycle-test.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v4-mount-check.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-network-guard.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v4-owned-write-result.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v4-plan.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v4-post-apply-test.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v4-public-apply-result.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-content-v4-public-ready.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-restore-result.md | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| docs/verification/schedule-content-v4-test.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-name-edit-test.md | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| docs/verification/schedule-task-removal-test.md | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/.env.example | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/README.md | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/(app)/home/page.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/(app)/layout.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/(app)/members/page.tsx | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/(app)/progress/page.tsx | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/(app)/schedule/[id]/page.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/(app)/schedule/page.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/error.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/globals.css | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/layout.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/not-found.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/page.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/register/layout.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/app/register/page.tsx | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components.json | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/app-shell.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/auth/auth-provider.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/auth/instructor-only.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/auth/require-auth.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/message-dialog.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/schedule-detail.tsx | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/schedule-editor.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/status-badge.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/ui/button.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/components/unsaved-changes-guard.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/eslint.config.mjs | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/auth-api.ts | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/home-api.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/member-api.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/mock-data.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/portal-store.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/progress-api.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/schedule-api.ts | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/task-status.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/lib/utils.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/next-env.d.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/next.config.mjs | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/package.json | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/pnpm-lock.yaml | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/pnpm-workspace.yaml | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/postcss.config.mjs | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/apple-icon.png | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/icon-dark-32x32.png | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/icon-light-32x32.png | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/icon.svg | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/placeholder-logo.png | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/placeholder-logo.svg | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/placeholder-user.jpg | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/placeholder.jpg | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/public/placeholder.svg | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/auth-api.test.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/auth-ui.test.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/home-api.test.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/home-ui.test.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/member-api.test.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/member-ui.test.tsx | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/message-dialog.test.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/progress-api.test.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/progress-ui.test.tsx | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/register-disabled.test.tsx | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/schedule-api.test.ts | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/schedule-create-ui.test.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/schedule-detail-ui.test.tsx | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/schedule-ui.test.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/setup.ts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tests/unsaved-changes-guard.test.tsx | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/tsconfig.json | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| frontend/vitest.config.mts | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| mvnw | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| mvnw.cmd | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| pom.xml | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/TrainingPortalApplication.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/auth/AuthController.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/auth/AuthResponse.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/auth/LoginRequest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/auth/MemberPrincipal.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/auth/MemberUserDetailsService.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/auth/RoleSessionFilter.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/auth/RoleSessionGuard.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/common/api/ApiError.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/common/api/ApiExceptionHandler.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/config/BusinessTimeConfig.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/config/PasswordEncoderConfig.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/config/SecurityConfig.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/home/HomeController.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/home/HomeService.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/home/dto/HomeDashboardResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueStatus.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueStatusRepository.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueSubmissionComment.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueSubmissionCommentId.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueSubmissionCommentRepository.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueSubmissionStatus.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueSubmissionStatusId.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/issue/IssueSubmissionStatusRepository.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/KenshuMember.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/KenshuMemberController.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/KenshuMemberRepository.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/KenshuMemberService.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/MemberRoleChangeSupport.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/dto/BCryptInput.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/dto/BCryptInputValidator.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/dto/MemberCreateRequest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/dto/MemberResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/dto/MemberUpdateRequest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/exception/DuplicateMemberException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/exception/MemberNotFoundException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/exception/MemberReferencedException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/exception/MemberRoleChangeNotAllowedException.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/exception/MemberSelfDeleteNotAllowedException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/member/exception/MemberUpdateConflictException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/ProgressController.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/ProgressService.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/dto/ProgressCommentRequest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/dto/ProgressCommentResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/dto/ProgressQuery.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/dto/ProgressResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/dto/ProgressSubmissionUpdateRequest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/dto/ProgressSubmissionUpdateResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/exception/ProgressAccessDeniedException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/exception/ProgressInvalidInputException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/exception/ProgressNotFoundException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/progress/exception/ProgressUpdateConflictException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/JapaneseHolidayService.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/Schedule.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleBunrui.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleBunruiId.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleBunruiRepository.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleCanEdit.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleCanEditId.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleCanEditRepository.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleController.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleDetail.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleDetailId.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleDetailRepository.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleRepository.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/ScheduleService.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/ScheduleCreateRequest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/ScheduleDetailResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/ScheduleListResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/ScheduleMemberOptionResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/ScheduleNonWorkingDatesResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/SchedulePinUpdateRequest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/SchedulePinUpdateResponse.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/dto/ScheduleUpdateRequest.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/DuplicateScheduleException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleAccessDeniedException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleCategoryInUseException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleEditableInstructorRequiredException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleHasIssueSubmissionsException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleInvalidInputException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleNotFoundException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/SchedulePermissionMemberInUseException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/SchedulePinLimitExceededException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleReviewerPermissionRequiredException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleTaskRemovalBlockedException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleTraineeRequiredException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/schedule/exception/ScheduleUpdateConflictException.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/setup/FreshDatabaseCommand.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/setup/InitialInstructorCommand.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/java/com/training/portal/setup/InitialInstructorService.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/resources/application.properties | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/resources/db/migration/V1__create_tables.sql | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/resources/db/migration/V2__insert_issue_statuses_master_data.sql | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/resources/db/migration/V3__allow_null_schedule_content.sql | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/resources/db/migration/V4__allow_null_schedule_content_in_public.sql | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/main/resources/db/migration/V5__add_member_auth_version.sql | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/TrainingPortalApplicationTests.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/auth/AuthApiTest.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/auth/RoleSessionConcurrencyTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/auth/RoleSessionFilterTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/auth/SessionCookieTest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/home/HomeApiTest.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/integration/ScheduleProgressHomeIntegrationTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/issue/IssueStatusMasterMigrationTest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/member/KenshuMemberControllerTest.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/member/KenshuMemberPersistenceTest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/member/KenshuMemberServiceTest.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/member/MemberRoleChangeApiTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/progress/ProgressApiTest.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/schedule/JapaneseHolidayServiceTest.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/schedule/ScheduleApiTest.java | 追跡済み | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/setup/FreshDatabaseCommandIntegrationTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/setup/FreshDatabaseCommandTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/setup/InitialInstructorApiTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/setup/InitialInstructorCommandTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/setup/InitialInstructorConcurrencyTest.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/java/com/training/portal/support/SecurityTestUsers.java | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| src/test/resources/application.properties | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/PublicV4Runner.java | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/apply-public-v4-verified.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/docker-isolation/Dockerfile | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/docker-isolation/entrypoint.sh | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/docker-isolation/lifecycle.py | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/docker-isolation/network_guard.py | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/docker-isolation/prepare.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/docker-isolation/restore_isolated.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/docker-isolation/test_lifecycle_docker.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/docker-isolation/test_network_guard.py | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/docker-isolation/test_restore_mounts.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/docker-isolation/verify_owned_workflow.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/docker-isolation/verify_restored.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/docker-isolation/write_verify.py | 未追跡 | あり | 対象外 | 旧環境専用資料・補助ツール。最新分類の非公開17件 |
| tools/flyway/fresh-browser-check.cjs | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/run-public-v4.py | 追跡済み | なし | 公開候補 | 現行ソース・構築・仕様・検証資料 |
| tools/flyway/test-fresh-database.py | 未追跡 | あり | 公開候補 | 現行ソース・構築・仕様・検証資料 |

## 3. 公開必須ファイルと未追跡依存

V1〜V5、認証世代管理、初期講師・新規構築コマンド、関連テスト、Maven Wrapper、pom.xml、フロントのpackage.json/lock、README、setup、API仕様は現在のファイル集合に存在する。現行の構築手順は旧V4補助ツールを使用しない。
.gitignoreの試験ではnode_modules・.next・.pnpm-store・.env.local・dump・logが除外され、frontend/.env.example、src、必要tools、docs/setupは除外されなかった。ignoreは追跡済み履歴の削除にはならない。

特に次の必須実装・テストは未追跡であり、現在のHEADだけをcloneすると欠落する。今回はgit addしない。

| 未追跡の必須ファイル |
| --- |
| src/main/java/com/training/portal/auth/MemberPrincipal.java |
| src/main/java/com/training/portal/auth/RoleSessionFilter.java |
| src/main/java/com/training/portal/auth/RoleSessionGuard.java |
| src/main/java/com/training/portal/member/MemberRoleChangeSupport.java |
| src/main/java/com/training/portal/member/exception/MemberRoleChangeNotAllowedException.java |
| src/main/java/com/training/portal/setup/FreshDatabaseCommand.java |
| src/main/java/com/training/portal/setup/InitialInstructorCommand.java |
| src/main/java/com/training/portal/setup/InitialInstructorService.java |
| src/main/resources/db/migration/V5__add_member_auth_version.sql |
| src/test/java/com/training/portal/auth/RoleSessionConcurrencyTest.java |
| src/test/java/com/training/portal/auth/RoleSessionFilterTest.java |
| src/test/java/com/training/portal/integration/ScheduleProgressHomeIntegrationTest.java |
| src/test/java/com/training/portal/member/MemberRoleChangeApiTest.java |
| src/test/java/com/training/portal/setup/FreshDatabaseCommandIntegrationTest.java |
| src/test/java/com/training/portal/setup/FreshDatabaseCommandTest.java |
| src/test/java/com/training/portal/setup/InitialInstructorApiTest.java |
| src/test/java/com/training/portal/setup/InitialInstructorCommandTest.java |
| src/test/java/com/training/portal/setup/InitialInstructorConcurrencyTest.java |
| src/test/java/com/training/portal/support/SecurityTestUsers.java |
| tools/flyway/test-fresh-database.py |

最新Excel2件は現在の作業ツリー版をコピーし、ハッシュ一致を確認した。履歴中の古いExcelを最新版として公開しない。基本39シート・52画像、テーブル10シート・215数式の保持と、表示/印刷の未確認範囲は直前の検証報告に記録済み。

## 4. 公開対象外17件

最新のdocument-cleanup報告に従い、旧分類16件とtest_lifecycle_docker.pyの計17件を候補コピーから除外した。元ファイルは削除・移動していない。Git管理外のアクセス制限された非公開アーカイブへ、依存関係・ハッシュ台帳と一緒に保管する方針。バックアップ・ログ・資格情報は別管理し、個人パスは公開資料へ転載しない。

| 対象外ファイル | 理由 |
| --- | --- |
| docs/verification/schedule-content-v4-docker-build.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-docker-plan.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-docker-restore.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-docker-test.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-docker-write-result.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-docker-write-test.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-mount-check.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-public-ready.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| docs/verification/schedule-content-v4-restore-result.md | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/apply-public-v4-verified.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/docker-isolation/prepare.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/docker-isolation/restore_isolated.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/docker-isolation/test_lifecycle_docker.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/docker-isolation/test_restore_mounts.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/docker-isolation/verify_owned_workflow.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/docker-isolation/verify_restored.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |
| tools/flyway/docker-isolation/write_verify.py | 旧環境・旧V4専用の実行条件や証跡依存。新規構築には不要 |

候補Markdownの相対リンクを検査し、存在しない参照先および対象外17件を指すMarkdownリンクは0件だった。監査台帳のファイル名列挙・旧資料中のプレーンテキスト参照はリンク検査とは別であり、旧ツールが配布されるという保証ではない。旧V4ランナーPublicV4Runner.java/run-public-v4.py等は候補に残るが、新規構築の必須集合には含めない。固定ローカルMavenキャッシュ依存があるため、補助保守資料としての説明を付け、クリーン構築に使用しない。

## 5. 履歴調査・整理対象

全9参照から到達可能なコミット22件、HEADの祖先19件を調査した。通常ブランチだけでなくローカル補助refを含む。GitHubサーバー上の非取得ref・PRキャッシュ・フォークは今回の調査対象外。reflogのみ/到達不能オブジェクトの全面的な秘密情報監査も実施していない。

git count-objectsではpack約140270 KiB、loose約26436 KiB。次の値は到達可能なユニークblobの非圧縮論理サイズであり、削除後のpack削減量の実測ではない。

| 対象 | blob数 | 論理バイト数 | 対応案 |
| --- | --- | --- | --- |
| node_modules | 26520 | 450570871 | 履歴から除去候補 |
| .next | 376 | 63114933 | 履歴から除去候補 |
| .pnpm-store | 0 | 0 | 履歴から除去候補 |
| other | 470 | 17676092 | 保持対象・別途選別 |

.pnpm-storeの到達可能blobは今回0件。node_modulesと.nextの2分類だけで513685804バイト（約490 MiB）の論理blobがある。最大blobはnode_modules配下のプラットフォーム依存バイナリで約88.9 MB。これらを公開履歴に保持する必要はない。

履歴内Excel4blobをZIP/XML解析した。個人識別フィールドの値自体は記載しない。

| Excel履歴blob | パス | 識別メタデータのフィールド | 旧画像一致数 | 追加/削除が記録されたコミット |
| --- | --- | --- | --- | --- |
| 8f6bba4e4934 | docs/design/テーブル定義書.xlsx | なし | 0 |  |
| 04c8a8a28c7d | docs/design/基本詳細設計書.xlsx | なし | 0 |  |
| e903f625aa08 | docs/design/テーブル定義書.xlsx | lastModifiedBy | 0 | e76648cf、8c431972 |
| 04de14969aae | docs/design/基本詳細設計書.xlsx | lastModifiedBy | 41 | e76648cf、8c431972 |

旧画像41個を含む基本設計書blobが1個、lastModifiedByを含むExcel blobが2個ある。現在のExcelの匿名化・画像差し替えでは履歴中のblobは消えない。旧Excelは分解して画像だけ消すのではなく、過去の対象Excelパス全体を除去し、最新版を公開スナップショットに保持する案が単純で安全。表定義書はUnicode正規化前後の実パスを両方確認する。

非公開17件は補助refを含む履歴からも除外候補。コミットauthor/committerの氏名・メールはblob掃除だけでは匿名化されないため、公開する識別情報の承認またはmailmap/書換え方針の別確認が必要。今回の監査は秘密情報の全面再スキャンではなく、認証情報が全履歴に存在しないと保証しない。

## 6. 方法の比較と独立コピーの検証

| 方法 | メリット | リスク・条件 | 推奨 |
| --- | --- | --- | --- |
| 最新の承認済みファイル集合から別の公開用リポジトリを作成 | 旧blob・補助refを持ち込まない。未コミットの最新機能を明示的に集約できる | 開発履歴を公開しない。新規コミットは今回禁止、将来の承認工程 | ポートフォリオ公開を優先する場合の第一候補 |
| 独立ミラーでgit filter-repoによるパス除去 | 必要な開発履歴を残せる | 全コミットID変更、補助ref・署名・タグ・作者情報の扱い、旧cloneへの影響。新しい依存ツールの導入が必要 | 履歴維持が必要な場合 |
| 既存Gitのfilter-branch等 | 追加ツールなし | 複雑で誤操作・残存refのリスク。今回実行しない | 非推奨 |
| 最新コミットで削除、.gitignoreだけ追加 | 作業は容易 | 過去blob・旧画像・メタデータは残る | 履歴整理の代替にならない |

`git clone --mirror --no-hardlinks`で完全に別ディレクトリにローカルミラーを作成した。ハードリンクや共有alternatesに依存せず、22コミット・全参照のコピーを確認した。fsckは終了コード0で、dangling tree/blobの通知があった。これは今回の到達可能履歴検査とは別であり、完全な秘密情報除去済みを意味しない。独立コピーも機微な履歴を含むため非公開で保持する。

git filter-repoが利用できなかったため、履歴書き換えは試していない。書換え前後のコミット数・pack容量・機能保持の比較は未実施。コピー前後は22→22コミットであり、整理成功と読み替えない。force push・remote更新・元リポジトリのref/config変更は行っていない。

別途、公開候補264ファイルを作業ツリーからGit外へコピーし、非公開17件の不在を検証した。候補コピーはGit履歴を持たないファイル集合であり、commit済みのクリーンcheckoutではない。業務コード・最新版Excel・未追跡実装も含むが、ビルド・ログイン・DB接続は未実施。

## 7. クリーン構築の検証手順

1. 公開候補一覧とExcelの手動確認を承認し、必要な未追跡ファイルを含む配布版を確定する。ステージング・コミットは今回行わず別工程とする。
2. 履歴維持の要否を決め、新規公開用リポジトリまたはfilter-repo後の独立コピーを準備する。元リポジトリを直接編集しない。
3. 実際に配布するコミットから新規ディレクトリへcloneする。元作業ツリー・未追跡ファイル・target・node_modules・.next・.env.localを持ち込まない。
4. Java21・PostgreSQL17・Node24・pnpm12を準備し、隔離Maven/pnpmキャッシュで依存取得する。ネットワーク要否と使用バージョンを記録する。
5. 既存5432とは別の専用PostgreSQLクラスタと空DBを準備し、README→docs/setup/fresh-environment.mdに沿って接続先を明示する。
6. Maven package、新規FreshDatabaseCommandでpublicのV1/V2→V3省略登録→V4/V5、別空DBでtest1のV1〜V5通常適用を検証する。既存DBには接続しない。
7. 初期講師コマンドを非表示対話入力で実行し、BCrypt・二重登録拒否・再ログイン・CSRFを確認する。秘密値は記録しない。
8. 設定テンプレートから隔離API向け環境を作り、Spring Boot/Nextを起動。ログイン、Home、メンバー、スケジュール、Progressを実ブラウザで確認する。
9. 型チェック・lint・フロントテスト・build、専用隔離DBでバックエンドテストを実行する。既定の開発DB向けテストを誤実行しない。
10. 非公開17件・ローカルキャッシュ・非配布の一時ブラウザスクリプトを追加しなくても構築できたことを記録する。再実行基盤のない過去E2Eは自動再現成功として数えない。

## 8. 公開前の残作業・優先順位

1. 高：未追跡の必須実装・V5・テストを含む候補集合を承認し、現在のHEADだけの公開による欠落を防ぐ。
2. 高：旧Excel画像・識別メタデータ・生成物・補助refの履歴を持ち込まない公開方式を決定する。最新スナップショットからの別リポジトリ作成を推奨するが、今回実行しない。
3. 高：実際に公開する全ファイルと全履歴に対して秘密情報・作者情報の再監査を行う。旧アクセス情報が見つかる場合は除去だけでなく失効/ローテーションを検討する。
4. 中：Excelの遷移図15本、全49シートの表示・印刷品質を手動確認する。
5. 中：候補コミットの完全クリーンcheckoutで構築を別途実行し、未追跡・キャッシュ依存がないことを確認する。
6. 中：最終的な公開対象と履歴の承認後にだけGitHub公開設定を検討する。

元の既存ファイルのSHA-256、Gitインデックス、HEAD、全ref、ローカルGit設定を終了時に比較する。変更を許可された今回の成果物は本報告書のみ。DB・Excel・Flyway・業務コードには手を加えていない。
