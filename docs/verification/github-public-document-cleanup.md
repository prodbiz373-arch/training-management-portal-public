# 公開資料の匿名化・画像差し替え準備（2026-10-18）

## 1. 今回の結果

「修正後公開」15件のうちMarkdown14件を匿名化し、パス36箇所を置換した。テストスクリプト1件は旧V4イメージ/構築依存の問題があり、資料匿名化だけで再現可能にはできないため変更せず「非公開候補」へ移す。

既存のアプリ・テストコード・DB・Flyway・Excel・画像・設定・README・.gitignoreは変更していない。追加は本報告とexcel-image-replacement-plan.md、変更は下記14資料だけ。GitHub設定は操作しておらずPrivate維持の方針。git add/reset/clean、履歴書換え、commit/push/mergeを行っていない。

## 2. 修正した14資料と箇所

表の行番号は修正前のもの。元の本文の変更は個人/一時パスの置換だけであり、現在の参照整理は末尾へ独立追記した。テスト件数・失敗/スキップ・未実施範囲・ハッシュ・当時の技術判断を変更していない。

| 資料 | パス修正行 | 修正内容 | 最新分類 |
| --- | --- | --- | --- |
| docs/verification/browser-e2e-test.md | 59,61,61,61 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/comment-edit-ui-test.md | 42 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/design-final-quality-check.md | 112 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/design-final-update-report.md | 15,22 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/diff-23-24-test.md | 65,82 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/flyway-v5-application-plan.md | 69 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/flyway-v5-application-result.md | 26,32,140 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/fresh-environment-setup-test.md | 128,129 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/initial-instructor-setup-test.md | 43,94 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/integration-test.md | 14,61,62,63,64,65,66 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/member-role-change-session-test.md | 65 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/progress-comment-filter-test.md | 45,61 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/schedule-category-duplicate-test.md | 27,87,115 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |
| docs/verification/schedule-name-edit-test.md | 40,40,40,40,40 | パスの匿名化、非配布証跡の説明、現行setup/APIリンク、過去記録との区別を追記 | 公開候補 |

`${PRIVATE_BACKUP_DIR}`は管理者の非公開バックアップ保管先、`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布証跡を示す匿名化表記。新しい必須環境変数や配布ファイルではなく、値を設定して当時の試験を再実行できるものでもない。資料ごとにその意味と長期保存の非保証を追記した。

現在のセットアップ手順とAPIへの相対リンクも14件に追加した。過去の報告の成功件数を今回の試験成功として数えない。失敗したブラウザ補助試行、Migrationテスト除外、スキップ、未実施の記録はそのまま保持する。

## 3. 15件目の扱い

対象：tools/flyway/docker-isolation/test_lifecycle_docker.py。

固定イメージID・固定証跡先に加え、旧Dockerfile/prepare/PublicV4Runner等への構築依存が残る。最新V5を含む状態ではprepareのSQL4件ガードも満たさない。今回は資料整理であり、コードや安全ガードを変更してまで公開用へ転用しない。分類を「修正後公開」から「非公開候補」に変更し、必要なら将来、独立したツール整備作業として検討する。元ファイルは保持した。

前回C48件の更新後内訳：公開31件（従来17＋今回14）、非公開17件（従来16＋今回の1）、修正後公開0件。これはC48件だけの集計で、別枠の基本詳細設計書は旧画像41点のため修正後公開のまま。元の選別報告は過去の判断として変更せず、本報告を最新差分とする。

## 4. Excel旧41画像

全件をシート・ZIP画像パート・Drawing/図形ID・旧イベント表示へ対応付けた。LGI3、HOM5、MMB6、SCD一覧5、SCD詳細12、PRM10の計41点。全件は過去の2コミットの同じ画像とバイト一致し、更新済み11点の合成撮影根拠を流用できないため、差し替え候補とした。

画像ごとの表示候補、再撮影条件、現行仕様との相違をexcel-image-replacement-plan.mdに記録した。未ログイン自己登録誘導は廃止済みで、同じ挙動を再撮影しない。専用戻るボタンは共通Homeナビで代替し、ネイティブ確認ダイアログをDOMポップアップとして偽造しない。実ブラウザ撮影・画像差し替えは今回未実施。

## 5. 元の非公開16件の保管方針と履歴

16件は現在すべて未追跡、通常のbranch/remotes履歴には存在しなかった。一方、**全16件がローカルの補助refから到達可能なコミット履歴に存在した**。未追跡だからGit履歴に無いとは判断しない。ローカル補助refを含むミラー/--all系の公開操作では混入し得る。GitHubのリモート専用refは今回取得していない。

READMEとfrontend/READMEには16件の直接参照なし。監査台帳での列挙、旧V4資料間の参照、補助ツール間の依存はあるため、単独で抜いて公開用パッケージを壊さない。

| ファイル | 履歴状況 | 現在の文書参照（監査台帳の列挙を含む） | 方針 |
| --- | --- | --- | --- |
| docs/verification/schedule-content-v4-docker-build.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/schedule-content-v4-docker-plan.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-docker-plan.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/schedule-content-v4-lifecycle-test.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-docker-restore.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-docker-test.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-docker-write-result.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/schedule-content-v4-owned-write-result.md、docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-docker-write-test.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-mount-check.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-public-ready.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| docs/verification/schedule-content-v4-restore-result.md | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| tools/flyway/apply-public-v4-verified.py | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/schedule-content-v4-public-apply-result.md、docs/verification/fresh-environment-setup-review.md、docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| tools/flyway/docker-isolation/prepare.py | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/fresh-environment-setup-review.md、docs/verification/github-public-file-selection.md、docs/verification/schedule-content-v4-network-guard.md、docs/verification/schedule-content-v4-docker-plan.md、docs/verification/github-public-preparation-result.md、docs/verification/schedule-content-v4-lifecycle-test.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| tools/flyway/docker-isolation/restore_isolated.py | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/schedule-content-v4-restore-result.md、docs/verification/schedule-content-v4-owned-write-result.md、docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/schedule-content-v4-docker-restore.md、docs/verification/schedule-content-v4-mount-check.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| tools/flyway/docker-isolation/test_restore_mounts.py | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/schedule-content-v4-mount-check.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| tools/flyway/docker-isolation/verify_owned_workflow.py | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/schedule-content-v4-owned-write-result.md、docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| tools/flyway/docker-isolation/verify_restored.py | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/schedule-content-v4-restore-result.md、docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |
| tools/flyway/docker-isolation/write_verify.py | 通常branch/remotes履歴なし。ローカル補助refの履歴あり（e76648cf） | docs/verification/schedule-content-v4-docker-write-result.md、docs/verification/github-public-file-selection.md、docs/verification/github-public-preparation-result.md、docs/verification/github-public-readiness-review.md | 非公開アーカイブへ保存する提案。今回は移動/削除しない |

安全な保管案：Gitルート外の管理者専用ディレクトリを700、資料を600とし、可能なら暗号化されたボリューム/バックアップへ保管する。パスは管理者が決め、個人ホームの実値を公開資料へ載せない。ファイル一覧とSHA-256を作り、関連ツール・manifest・検証ログを依存単位で保存する。資格情報・DBバックアップ・ブラウザプロファイルはさらに分離し、保管期限を決める。

今回コピー・移動・削除は行っていない。将来のコピー完了・ハッシュ照合だけではGit履歴から消えない。既存の未コミットファイルや履歴を処理する前に別途承認・バックアップが必要。

## 6. 参照整合性と再監査

匿名化した14資料には個人ホーム、一時領域の生の絶対パスを残さない。ループバック接続先、スキーマ名、公開手順に必要な一般ロール、SHA-256/SQL checksumは秘密値とは区別して保持した。新たな個人名・パスワード・キーは追加していない。今回の再監査は修正資料とリンクの範囲で、全履歴の秘密情報スキャン再実行ではない。

READMEは変更していない。README→setup、新規構築検証報告へのリンクを維持。14資料に追加したsetup/APIリンク、E2E/コメント報告から証跡PNGへのリンクも確認する。旧V4の非公開資料同士の参照は、資料が全て保持されているため現在は切れていない。公開集合から外すときは一式として扱う。

非配布の一時スクリプトでしか実施していないブラウザ試験は、その実行手順を今回新規実装していない。公開資料として当時の結果を説明できることと、全試験を第三者がそのまま再実行できることは別である。

## 7. 残る公開リスクと次の作業

1. 高：旧41画像の合成根拠/許諾確認、または安全な合成データで再撮影。Excelのキャプションと現行仕様も併せて確認する。
2. 高：新規撮影のmanifestと実API/stubの区別を記録し、メタデータ・人物欄を目視確認してからExcel差し替えを別途実施する。
3. 中：今回の分類は現在の公開資料候補。非公開17件は削除せず、保管/公開集合の確定をユーザーが判断する。
4. 中：旧画像・個人メタデータ・node_modules/.next/.pnpm-store・個人メール等の履歴整理方針を別コピーで検討する。通常branchと補助refを区別して対象を確定する。
5. 中：公開予定スナップショットの依存閉包/リンクを確認し、空依存キャッシュと独立DBでクリーン構築を別途実施する。

今回Public化・画像差し替え・履歴整理・構築再検証は行っていない。未実施を成功としていない。

最終検証：変更14資料の旧本文はパス置換を正規化すると元記録と全文一致。個人ホーム/生の一時絶対パスは修正資料内0件。ローカルMarkdownリンク59件は参照先存在。開始時276ファイルのうち変更を許可した14資料以外はSHA-256全件一致。Excel・画像・SQL・コード・設定・Gitインデックス不変、git diff --check成功。
