# GitHub公開準備：ExcelメタデータとGit管理対象整理（2026-10-18）

## 1. 実施範囲・結果

Excel2冊の個人識別メタデータと保存元パスを整理し、.gitignoreを最小限補強した。GitHubの公開設定は操作せず、Privateのままとする方針を維持した（GitHub設定の再取得・再確認は行っていない）。Git履歴やインデックスは変更していない。

今回の変更は次の4ファイルのみ。

- docs/design/基本詳細設計書.xlsx
- docs/design/テーブル定義書.xlsx（実ファイル名は濁点分離を含む。改名していない）
- .gitignore
- 本報告書

アプリコード・DB・Flyway・通常設定・既存の検証報告書は変更していない。DB接続、System Events、アクセシビリティ、画面収録を使用していない。git add/reset/clean、履歴書換え、commit/push/mergeは行っていない。

## 2. バックアップ

元Excelは編集前にGit管理外の専用ディレクトリへコピーした。

保存先：`${PRIVATE_BACKUP_DIR}/excel-before-cleanup`

ディレクトリは700、バックアップExcelは600。元ファイル名で保存している。バックアップには整理前の識別情報が残るため、Gitや共有ストレージへ無断で追加しない。一時領域なので長期保存は保証されない。必要な保存期間に応じて管理者が非公開の安全な保管先へ移すことを推奨する。既存のバックアップは削除していない。

| ファイル | 整理前SHA-256 | 整理後SHA-256 |
| --- | --- | --- |
| 基本詳細設計書.xlsx | `3652ceb82e498d12d5f47675c0a4ca94b66164d272c9b598d0d08e9c4d6d36d5` | `cfa47ecc67b775f2524bc403d0d6adddb5655752486d0ec4c2ea472dbb58bc3c` |
| テーブル定義書.xlsx | `23e3832744d1598c8683ec33398a273f45141b5f4c936b8f932603f6c8874052` | `c927916869a071f4e73b15c4d075ec27ca2ce637c3b2e3362565a139698956a0` |

## 3. Excelの修正内容

| 対象 | 変更内容 |
| --- | --- |
| 両ExcelのdocProps/core.xml | lastModifiedByを各1件除去。creatorは元から存在せず、新しい個人名は追加しない |
| 両Excelのxl/workbook.xml | absPath要素を各1件除去。個人環境の保存元絶対パスを含むメタデータのみ対象 |
| テーブル定義書のxl/comments1.xml〜comments8.xml | author要素8件を「匿名」に置換。authorIdとコメント本文・位置は維持 |

コメント本文は16件すべて保持した。8業務テーブルのO4は「小数点以下の桁数を指定」、AA4は「実際に入る値、コード値の意味を記入」で、いずれも設計書テンプレートの記入説明だった。個人情報ではなく、削除する理由がないためコメントとして残した。

セルへ移すことも今回は行っていない。将来コメントを廃止する場合は、O4/AA4の説明を共通の記入要領へ移す案があるが、現時点で移動は不要と判断する。

作成日時・更新日時等の日時情報、書式、図形名、業務セル等は個人情報と推測して削除していない。コメント本文から氏名等を削除する必要は見つからなかった。画像内部のEXIF/XMP相当情報や画像内容の変更は行っていない。

## 4. Excel内部構造の比較

画像・Drawingを読み書きするExcel編集ライブラリで全体保存せず、ZIP内の限定したXMLをバイト単位で置換した。ZIPメンバーの名前・順序・属性と圧縮方式を維持した。圧縮されたアーカイブ全体のバイト一致ではなく、展開後の各パートを比較した。

| 検証 | 基本詳細設計書 | テーブル定義書 |
| --- | ---: | ---: |
| シート数・名前・順序 | 39、維持 | 10、維持 |
| 画像ファイル | 52、全件バイト一致 | 0 |
| 数式 | 0 | 215、全件保持 |
| セル結合 | 702、保持 | 1,777、保持 |
| 名前定義 | 2、保持 | 1、保持 |
| ZIP内パート数 | 121、維持 | 52、維持 |
| 変更パート | core.xmlとworkbook.xmlの2件 | 同2件とコメントXML8件の計10件 |
| その他のパート | 119件、全件バイト一致 | 42件、全件バイト一致 |
| 内部Relationship検査 | 105件、欠落0 | 41件、欠落0 |
| XML整形式・ZIP CRC | 成功 | 成功 |

全worksheet、styles、sharedStrings、Drawing、VML、画像、Relationships、Content Types、印刷関連パートはバイト一致。workbook.xmlはabsPath要素を除いて構造一致。コメントXMLはauthor以外、commentList全体（本文・セル位置・authorId）が一致した。これにより図形・罫線・色・列幅・行高・印刷設定などに新たな変更を加えていないことを確認した。

整理後はcore.xmlのcreator/lastModifiedByに非空値0件、ExcelのXML内の対象ローカルパス形式0件。今回除去した最終更新者の文字列が他XMLに残存していないことも値を表示せず確認した。コメント作成者は全件「匿名」。セルや画像内のすべての個人情報不存在を保証する検証ではない。

**今回の更新後ファイルをMicrosoft Excelで実際に開く確認、全シートの目視、印刷プレビューは未実施。** ZIP/XMLの検査をExcelでの表示確認と同一視しない。前回の品質確認結果を今回の実機確認として流用しない。

## 5. .gitignoreの修正

既存の除外設定を保持し、次を追加した。

- 全階層のnode_modules/、.next/、.pnpm-store/
- .env、.env.*。レビュー済みテンプレート用に!.env.exampleを指定
- .pgpass、runtime-credentials.json
- *.dump、*.backup、*.sql.gz
- *.log、*.tmp、Excel一時ロックファイル~$*.xlsx

生の*.sql、*.json、*.png、*.xlsx、tools/、docs/等を一律除外しない。Flyway・テストfixture・設計書・安全な画像・ツールを誤って隠さないためである。.env.production等も除外されるため、意図的に公開する非秘密設定例は.exampleへ分離する。未知の秘密ファイルを拡張子だけで全て防ぐものではない。

`git check-ignore --no-index`で、依存/Next/pnpmキャッシュ、ルート・frontend・入れ子の.env、バックアップ、ログ、一時ファイル、資格情報ファイル等16パターンが除外されることを確認した。

開始時の追跡ファイル・未追跡ファイルについても検査し、意図した.pnpm-store以外に新たに除外される既存ファイルは0件。Flyway V1〜V5、setup実装・テスト・スクリプト、.env.example、README、Excel等が除外されない。新しい除外ルールは過去のGit履歴を消さない。

## 6. 未追跡ファイルのA/B/C分類

開始時の未追跡73ファイルを分類した。フォルダ単位のgit status表示ではなく、内部のファイル単位で集計した。

| 分類 | 開始時件数 | 扱い |
| --- | ---: | --- |
| A：公開・Git管理が必要 | 24 | V5、認証関連、setup、テスト、新規構築検証ツール。今回git addしない |
| B：公開不要で除外 | 1 | .pnpm-store/v11/index.db。今回除外済み、実ファイルは保持 |
| C：内容確認後に判断 | 48 | 検証文書・証跡画像・既存環境専用補助ツール。削除や一律ignoreはしない |

本報告書は新規Aに追加するため、成果物を含めた分類対象はA25/B1/C48の計74ファイル。既存の追跡済み変更ファイルはこの未追跡件数に含めない。

### A：公開・Git管理が必要

- docs/setup/fresh-environment.md
- docs/setup/initial-instructor-setup.md
- frontend/tests/register-disabled.test.tsx
- src/main/java/com/training/portal/auth/MemberPrincipal.java
- src/main/java/com/training/portal/auth/RoleSessionFilter.java
- src/main/java/com/training/portal/auth/RoleSessionGuard.java
- src/main/java/com/training/portal/member/MemberRoleChangeSupport.java
- src/main/java/com/training/portal/member/exception/MemberRoleChangeNotAllowedException.java
- src/main/java/com/training/portal/setup/FreshDatabaseCommand.java
- src/main/java/com/training/portal/setup/InitialInstructorCommand.java
- src/main/java/com/training/portal/setup/InitialInstructorService.java
- src/main/resources/db/migration/V5__add_member_auth_version.sql
- src/test/java/com/training/portal/auth/RoleSessionConcurrencyTest.java
- src/test/java/com/training/portal/auth/RoleSessionFilterTest.java
- src/test/java/com/training/portal/integration/ScheduleProgressHomeIntegrationTest.java
- src/test/java/com/training/portal/member/MemberRoleChangeApiTest.java
- src/test/java/com/training/portal/setup/FreshDatabaseCommandIntegrationTest.java
- src/test/java/com/training/portal/setup/FreshDatabaseCommandTest.java
- src/test/java/com/training/portal/setup/InitialInstructorApiTest.java
- src/test/java/com/training/portal/setup/InitialInstructorCommandTest.java
- src/test/java/com/training/portal/setup/InitialInstructorConcurrencyTest.java
- src/test/java/com/training/portal/support/SecurityTestUsers.java
- tools/flyway/fresh-browser-check.cjs
- tools/flyway/test-fresh-database.py

認証の変更済み追跡ファイルと上記新規クラスを一体で扱う。V5や新規クラスだけ取りこぼすとビルド・起動・DB整合性に問題が生じる。

### B：公開不要で除外

- .pnpm-store/v11/index.db

### C：確認後に判断

- docs/verification/browser-e2e-evidence/05-name-error.png
- docs/verification/browser-e2e-evidence/08-progress-unsubmitted.png
- docs/verification/browser-e2e-evidence/10-home-completed.png
- docs/verification/browser-e2e-evidence/14-trainee-comment.png
- docs/verification/browser-e2e-evidence/15-other-author-no-delete.png
- docs/verification/browser-e2e-evidence/18-blocked-content-clear.png
- docs/verification/browser-e2e-evidence/23-api-network-error.png
- docs/verification/browser-e2e-evidence/25-expired-auth-redirect.png
- docs/verification/browser-e2e-evidence/26-home-without-task.png
- docs/verification/browser-e2e-test.md
- docs/verification/comment-edit-ui-evidence/comment-edit-other-author.png
- docs/verification/comment-edit-ui-evidence/comment-edit-saved.png
- docs/verification/comment-edit-ui-test.md
- docs/verification/design-final-quality-check.md
- docs/verification/design-final-update-report.md
- docs/verification/design-implementation-diff-final-review.md
- docs/verification/design-implementation-diff.md
- docs/verification/diff-23-24-test.md
- docs/verification/flyway-v5-application-plan.md
- docs/verification/flyway-v5-application-result.md
- docs/verification/fresh-environment-setup-review.md
- docs/verification/fresh-environment-setup-test.md
- docs/verification/github-public-readiness-review.md
- docs/verification/initial-instructor-setup-test.md
- docs/verification/integration-test.md
- docs/verification/member-role-change-session-design.md
- docs/verification/member-role-change-session-test.md
- docs/verification/progress-comment-filter-evidence/progress-filter-after-delete.png
- docs/verification/progress-comment-filter-test.md
- docs/verification/schedule-category-duplicate-test.md
- docs/verification/schedule-content-v4-docker-build.md
- docs/verification/schedule-content-v4-docker-plan.md
- docs/verification/schedule-content-v4-docker-restore.md
- docs/verification/schedule-content-v4-docker-test.md
- docs/verification/schedule-content-v4-docker-write-result.md
- docs/verification/schedule-content-v4-docker-write-test.md
- docs/verification/schedule-content-v4-mount-check.md
- docs/verification/schedule-content-v4-public-ready.md
- docs/verification/schedule-content-v4-restore-result.md
- docs/verification/schedule-name-edit-test.md
- tools/flyway/apply-public-v4-verified.py
- tools/flyway/docker-isolation/prepare.py
- tools/flyway/docker-isolation/restore_isolated.py
- tools/flyway/docker-isolation/test_lifecycle_docker.py
- tools/flyway/docker-isolation/test_restore_mounts.py
- tools/flyway/docker-isolation/verify_owned_workflow.py
- tools/flyway/docker-isolation/verify_restored.py
- tools/flyway/docker-isolation/write_verify.py

Cの判断基準：

- 検証報告書は成果の説明として有用だが、個人環境の絶対パスや一時ログ参照を見直す。必要な修正は今回行っていない。
- READMEからリンクされるfresh-environment-setup-test.md等は、公開版でリンク切れにしないため優先してレビューしAへ移す。今は未確認事項が残るためCとした。
- 証跡画像は合成データであることの目視確認とメタデータ確認後に判断する。レポートを採用する場合は画像リンクも維持する。
- apply-public-v4-verified.pyとdocker-isolationの復元・書込み補助は過去の特定DB・バックアップ・一時manifestへの依存がある。新規構築ツールとは分離し、依存ファイルが公開集合に存在するか、継続提供が必要かを判断する。安全ガードを緩めて再利用しない。
- Cは公開禁止・削除決定という意味ではない。今回もファイルを保持した。

### 追跡済みファイルの扱い

README、frontend/README、API仕様、関連する変更済みアプリ/テスト、pom.xml、更新済みExcel2冊は必要な構成要素。Excelは今回メタデータのみ整理したが、実表示確認は残る。既存V1〜V4は追跡済みで維持、V5はA。既存の検証文書やV4ツールは、追跡済みでも公開前レビュー対象であることに変わりない。

## 7. 再監査・既存変更の保護

現在のGit管理候補274ファイル（キャッシュ除外・本報告書作成前）を再走査し、代表的な秘密鍵、GitHub/AWS等トークン、セッションCookie形式、DBパスワード固定設定の明らかな候補は検出しなかった。パターン検査であり、未知形式の秘密値や画像内の全情報を保証しない。今回の再走査は全履歴を再実行したものではなく、履歴の評価は前回監査を維持する。

frontend/.env.localは除外を維持。新しいローカル設定や認証情報はソースへ追加していない。バックアップはGit管理外に保持した。

開始時の既存ファイルのSHA-256と比較し、変更があったのは許可対象のExcel2冊と.gitignoreだけ。その他の既存アプリ・文書・SQL・テスト・キャッシュファイルは全件不変。Gitインデックスのパス/モード/blob/stage記録も前後一致し、ステージング変更はない。ファイル名比較はmacOSのUnicode正規化差を考慮し、ファイルを改名していない。

## 8. 残る問題と次の作業

1. 更新後ExcelをMicrosoft Excelで開き、全49シートの表示・画像・印刷を目視確認する。
2. 画像の個人情報とEXIF/XMP相当情報を確認する。今回画像を変えていないため、前回の12画像のメタデータと1画像のOCR未読は残る。
3. C分類の報告・画像・V4補助ツールをレビューし、公開集合を決める。個人環境パスの整理とREADMEリンクの維持を行う。
4. 履歴内のExcel旧メタデータ、node_modules/.next/.pnpm-store、コミットメールは残る。今回は履歴を変更しない方針のため未対応。公開前に別途方針と承認を決める。現在のExcelを整理しても過去版は消えない。
5. A分類と関連する既存変更を一体で確認した後、ユーザーがGit管理への追加・コミットを判断する。
6. 公開予定スナップショットで、依存キャッシュを使わないクリーン構築・ログインを別途検証する。

現時点でPublic化の完了・全面的な安全性保証とは判定しない。GitHubは今回操作していない。

最終検証：既存275ファイルを照合し、Excel2冊と.gitignore以外は全件SHA-256一致。新規ファイルは本報告書だけ。git diff --check成功、Gitインデックス記録不変。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
