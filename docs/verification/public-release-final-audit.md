# 公開用リリース候補の最終ファイル監査（2026-10-19）

## 1. リリース候補と判断

リリース候補ID：`public-20261019-rc1`。対象はGit履歴を含まない公開用コピー。開始時266ファイルの全パス・SHA-256が前回保管台帳と一致した。非公開17件、Git、秘密設定、バックアップ、依存/ビルド生成物がないこと、必須実装・SQL・テスト・API・最新Excelの存在を確認した。

README1件を変更し、監査済みE2E報告書1件と本監査報告書1件を追加した。削除0件、最終268ファイル。業務コード・DB・Flyway・認証仕様・Excel・画像は変更していない。開発リポジトリへの変更も0件。

**候補一覧と内容はハッシュで固定するが、Public化の承認は保留。** Excelの表示/印刷・画面遷移図の確認と必要な修正が未完了である。内容を変えた場合は新しい候補IDとmanifestで再監査する。今後のファイル変更をOS権限で禁止したものではない。

## 2. 保管先と固定台帳

公開用コピー：`${WORKSPACE_ROOT}/training-portal-public-release-20261018`。監査台帳：隣接するGit管理外の`training-portal-public-release-20261018-rc1-audit`。WORKSPACE_ROOTは利用者作業領域を示す匿名表記で、実行に必要な環境変数ではない。

監査台帳には以下を保管する。公開用コピーへ混ぜず、個人環境パス・秘密情報を収録しない。

- `release-manifest.json`：全268ファイルの相対パス・サイズ・SHA-256、候補ID。
- `release-manifest.sha256`：manifest自体のSHA-256。
- `previous-266-manifest.json`：変更前266件の照合基準。
- `changes.json`：READMEとE2E報告書の変更理由・ハッシュ。
- `README-before.md`：READMEの修正前バックアップ。
- `final-check.json`：最終リンク・対象外混入・ハッシュ検査結果。

本報告書の内部に自身のSHA-256を埋め込むと循環するため、自身を含む全268件のハッシュは外部manifestへ記録する。後段の一覧は本報告書を除く267件であり、自己ハッシュだけは外部manifestを使用する。ファイルのパス集合も照合し、ハッシュ一致ファイル以外の追加物がないことを確認する。

## 3. ファイル・必須構成の監査

| 項目 | 結果 |
| --- | --- |
| 初期266件のパス集合・SHA-256 | 全件一致 |
| 匿名化済み資料2件 | 匿名化後ハッシュと一致、旧個人ホームパスの再混入なし |
| 非公開17件 | 全件不在。削除ではなく収録対象外を確認 |
| .git・旧履歴・補助ref | なし。Git初期化もしない |
| 秘密設定・DBバックアップ・ログ・生成物 | なし。.env.exampleは公開API URLのテンプレートとして保持 |
| Maven Wrapper・pom・frontend lock | 存在 |
| Flyway V1〜V5 | 5ファイル存在、開発版の現行SQLと全件一致 |
| 認証世代・社員区分変更関連実装/テスト | 存在 |
| 初期講師・新規空DBコマンド/テスト | 存在 |
| README・setup・API仕様書 | 存在 |
| 基本詳細設計書 | 最新版と一致。39シート・52画像・数式0 |
| テーブル定義書 | 実ファイル名を使用。最新版と一致。10シート・215数式 |

必須ファイルの監査は現在の公開候補全体を対象とし、Git追跡状態に関係なく未追跡だった実装も含む。旧V4ランナー等の過去補助資料は現在の新規構築経路では使用しない。公開候補には過去の経緯を示す文書も含まれ、記載時点の結果と現在の仕様を区別する。

## 4. READMEの確認と修正

READMEのプロジェクト概要・主要機能を実装と照合した。Next/React/TypeScript、Spring Boot4.1.1/Java21、PostgreSQL17、セッション認証、メンバー・スケジュール・ピン・Progress・Homeが現在の構成と一致する。未ログイン自己登録や固定初期パスワードを提供しない説明も一致する。

既存の構築手順を置き換えず、末尾へ次を追記した。修正前版を公開用コピー外へ退避済み。

| 変更 | 理由 |
| --- | --- |
| 空の専用Maven/pnpmキャッシュでの構築結果とリンク | 既存キャッシュだけで成功した過去検証と区別 |
| フロント218件・型チェック・lint | 実際に成功した検証範囲を提示 |
| バックエンド217件中207成功・10スキップと後続結果 | スキップを成功へ書き換えず、別枠成功・重複集計を明示 |
| 業務E2E19ケース・初回4失敗と再試験 | 最終成功と失敗履歴を併記 |
| 明示的なWebpack buildの確認経路 | 既定buildの別エンジンまで成功したと誤認させない |
| Excel遷移図・全49シート表示/印刷待ち | Public化の保留条件を入口で明示 |
| 現行setupと旧固定ポート/一時証跡の区別 | 非配布の過去手順を必須手順にしない |

docs/setup/fresh-environment.mdのJava21・Node24・pnpm12・PostgreSQL17、空DB専用ランナー、publicのV3省略登録・V4/V5、別空DBtest1、対話式初期講師、API URL設定、Webpack buildを確認した。今回の監査でビルド・DB・E2Eを再実行したわけではなく、直前の記録と今回の業務コード不変を根拠とする。

## 5. 匿名化・秘密情報・Excel

ソース/設定/Markdown/XML等を対象に、個人ホーム絶対パス、秘密鍵、アクセストークン形式を再検査し、該当0件。現在のDBパスワードは環境変数参照、設定テンプレートは秘密値を持たない。前回検出されたテストの固定入力値は合成フィクスチャであり、運用認証情報と区別した。網羅的な秘密情報不存在を保証する検査ではない。

匿名化2件はschedule-content-v4-owned-write-result.md、schedule-content-v4-post-apply-test.md。非配布バックアップの個人ホームを匿名表記にした版が維持されている。原本の古い内容を再コピーしていない。新しいE2E報告書には実値のパスワード・Cookie・トークン・個人ホームはない。

E2E報告書のループバックアドレス・専用ポート・合成ID・テスト名は再現条件を説明する非秘密情報として保持した。59455はテストの固定条件なので、むやみに匿名化して実行条件を変えない。個人作業領域はWORKSPACE_ROOT表記、非配布の一時資料は履歴資料である旨を記載済み。

ExcelのZIP CRC・全XMLパース成功。creator/lastModifiedByに個人識別値なし。両ブックとも非表示シート0、個人ホーム絶対パス0。基本設計書のコメントパート0、テーブル定義書のコメントパート8は一般的な作成者表記であり、設計上の内容を削除せず保持した。個人値そのものを報告しない。旧41画像のハッシュ一致0、合成撮影台帳との一致は前回の監査を引き継ぎ、今回もブック全体のハッシュ一致を確認した。画像73点に変更はないため、新たな加工・目視検証を行ったと報告しない。

## 6. E2E報告書の公開判断

`public-snapshot-e2e-validation.md`を収録する。理由は、公開候補だけで主要CRUD・認証認可が動く根拠と、初回失敗/再試験/未実施の境界を第三者が判断できるため。秘密値・個人ホームの記載がないことを確認し、元報告書と同一内容でコピーした。追加の匿名化は不要と判断した。

同文書の「266ファイル」「本報告書を保管版へ追加していない」は当時の検証時点の記録である。現在はE2E報告書と本監査報告書を収録し268件に更新した。本監査報告書とmanifestが最新のファイル集合を示す。過去の検証本文を現在の件数へ都合よく書き換えていない。

## 7. 公開前の保留条件・対象外

- 基本詳細設計書の遷移図15コネクタの接続元/先/ラベルを目視確認し、確定した古いラベルだけを修正する。
- 基本39・テーブル10シートの文字切れ、画像・図形位置、長文、印刷範囲/改ページ/倍率をMicrosoft Excelで確認する。今回はExcelの画面/OS自動操作を実施しない。
- 目視または印刷修正でExcelが変わった場合、旧ハッシュのRC1をそのまま公開せず新候補を作成する。
- ブラウザの複数課題「すべて」検索、改ざんAPI・409/500・他ブラウザ等は直前E2Eの19ケース範囲外。必要な追加検証を判断する。
- 作者/権利・公開範囲を利用者が最終承認する。本監査はPublic化の操作・許可を代替しない。

## 8. 新規公開リポジトリ作成手順（今回は未実行）

1. 上記のExcel保留を解消し、最新manifestと全ファイルを再照合する。ソースの追加・変更・削除は必ず新しい候補へ反映する。
2. 開発リポジトリをclone/mirrorしない。旧.git・refs・bundleを持ち込まず、監査済み相対パスのファイルだけで空の作業ディレクトリを用意する。
3. そのディレクトリに対し、新しいGit履歴を初期化する。例は`git init --initial-branch=main`。元Private側では実行しない。
4. 公開用のauthor/committer表示名とメールの方針を確認し、ユーザーが承認した値だけを設定する。既存個人設定を無確認で流用しない。
5. manifest対象だけを登録候補にし、git statusとステージ対象の差分、秘密情報、余計な生成物がないことをレビューしてから初回コミットを作る。今回はgit add/commitしない。
6. GitHubへ履歴を引き継がない新規リポジトリを別途作る。最初はPrivateで内容・作者・画像・セットアップを確認する。旧remoteや補助refの一括push、mirror pushは使用しない。
7. 新規リポジトリのcloneで構築を必要範囲再確認し、明示的な公開承認後にだけPublic化する。今回はGitHub作成・push・Public化を行わない。

## 9. 公開後の保守

開発Privateと公開用を区別し、承認した変更だけをファイル単位またはレビュー可能な差分で公開用へ反映する。元履歴・補助refを後から取り込まない。依存・Flyway・仕様変更時は必要なテスト/セットアップとAPI資料を更新し、新しいmanifest・リリース候補/タグを作る。秘密情報を誤公開した場合は値の失効/ローテーションを優先し、履歴処理は別途承認して行う。

## 10. 固定対象一覧（本報告書以外の267件）

全268件の最終ハッシュは外部release-manifest.jsonに記録する。以下にない追加ファイル、記載済みファイルの変更、削除を検出した場合、この候補との一致とは扱わない。

| 相対パス | SHA-256 |
| --- | --- |
| .gitattributes | `5775a77ad2f6d12dde053a3fb0cca4309189163f0888f4e7e984cd0ab327028b` |
| .gitignore | `29cdf9f08c3cf124217cd76b4a6a99cb7e5584cec5b37b547948bb3212c3a93d` |
| .mvn/wrapper/maven-wrapper.properties | `488e1b3f2e641779d4636abf9390845f901e64607261bc3c0b0bfe4fe96e6706` |
| AGENTS.md | `bb335cfdf2aede5f92212d81a0f3e577d2590c3c3f0b299df703dda7b3b334fa` |
| README.md | `8d545d9be0b4dbb3fa175450fd5b5b36bb9f05c2e8f19a4825ca31d0c58d5fdb` |
| docs/api/auth.md | `6de3fcfa72a8bb3b9fbae41bb2a4e49ab66c8eee60a7068084656ecc1a5a1b22` |
| docs/api/home.md | `01dfe039d2784c5bd7f5c7274b5067e025a88af13de8a9e29b8a3e0a8a859d8b` |
| docs/api/members.md | `b4d3bbbcf9f6938595ae0e54e0e62e397b68406bd99726ff9776440cc93b13cc` |
| docs/api/progress.md | `91caa825bd970e171244710465fe0acd98c79b071ce52e82f728d3ed2e52b77c` |
| docs/api/schedules.md | `46c020b6d6d588086e9fecf6db2cbd2d978fb8082ac652e6d58b2801b14bd3af` |
| docs/design/テーブル定義書.xlsx | `c927916869a071f4e73b15c4d075ec27ca2ce637c3b2e3362565a139698956a0` |
| docs/design/基本詳細設計書.xlsx | `2ff0029b73d7c8bcf29947c96d75c44f18a911b347f45d1198ec6072abaeb1ac` |
| docs/implementation/frontend-auth.md | `18a18891ea896490ea5b8dea08042ddc7e521505be86f600a8b0d42407bef462` |
| docs/implementation/frontend-members.md | `847d1d716adac7386e0f8205455fe73e2399c5f9060e71d9cda856fed647d23b` |
| docs/implementation/member-service.md | `7b42a3577297bad55ce061808a9088754f67c9e18bb1e18fd1e3061242e19e33` |
| docs/setup/fresh-environment.md | `5637a1bc89e0ae4935d5405f66b7ce3f7adf196a3a1d3cc6d053128326b0ebac` |
| docs/setup/initial-instructor-setup.md | `9bd0d991e620c7aafbd68504fc8a5539b3e8ac2990befd27892056597feae940` |
| docs/verification/browser-e2e-evidence/05-name-error.png | `ce52e140d3cc2e48d1feef68d20c44e8b04cf56a506f775264e9c94e81f37c95` |
| docs/verification/browser-e2e-evidence/08-progress-unsubmitted.png | `c3afed25da29dfa462655b4b8c43b5e5c91bdc7d6c3f521d83c5f3fcab6b2952` |
| docs/verification/browser-e2e-evidence/10-home-completed.png | `e5515ce91c99b189af6587362378e48561fd27d5549cde13a265dd8e05b3ff5d` |
| docs/verification/browser-e2e-evidence/14-trainee-comment.png | `0723d7f2b89acbc000df97156e9fd399460dfc0809ddf9a3494dfb5b4f4b80d7` |
| docs/verification/browser-e2e-evidence/15-other-author-no-delete.png | `f3e9b3059ed9983ee86a2058235f51d422595ad66f0dad727ba3b55d7b6970aa` |
| docs/verification/browser-e2e-evidence/18-blocked-content-clear.png | `0b796b4621a3183456811a1a65b9190855158b98f7e106ecf05d8d5c887dd740` |
| docs/verification/browser-e2e-evidence/23-api-network-error.png | `f275e493a18c8125df0bff0319c347741afc30f205cc4dd36e9ccdeeef981817` |
| docs/verification/browser-e2e-evidence/25-expired-auth-redirect.png | `959e17c6b7df1395ca0a864acf024eb3a08aa5d16f9366652e00ee74a0d207f8` |
| docs/verification/browser-e2e-evidence/26-home-without-task.png | `57fae9f12f20a6507aaa54526e0ebe2cfec11fcd3aa8ce83cf218bd2373322e8` |
| docs/verification/browser-e2e-test.md | `8ba5849fa52d6e7afc60521707f3007ce8cce8dbb062831922a85e52327f26cb` |
| docs/verification/comment-edit-ui-evidence/comment-edit-other-author.png | `bfec8c75fd9c61091ad5e736e9524762b23c2014f95ab8b6e52a6cbda33114fc` |
| docs/verification/comment-edit-ui-evidence/comment-edit-saved.png | `d0ba94101b58f828476e9409c70311a537138c5534d3667671305eedbba14a87` |
| docs/verification/comment-edit-ui-test.md | `87a64ac6046b5482677c4d5413031d78010d530642683410bb235843863bec98` |
| docs/verification/design-final-quality-check.md | `4ad555f723fd9207e8fdcd93c0c0e74c792eb94a6c0e54b06ef0f7d204446e40` |
| docs/verification/design-final-update-report.md | `990805292da0b167369244f699e3929f88a0d8a194a65c086b68313a00a6626c` |
| docs/verification/design-implementation-diff-final-review.md | `9367eb29add5ea94b72e91eb2876e0e5fc49f0ae06c070b985bdc6f134b40e60` |
| docs/verification/design-implementation-diff.md | `8a8094eb61b72a1b022206f20fee3e6c449f885c779a9e8d113e90ed98b02831` |
| docs/verification/diff-23-24-test.md | `ef5084bab7231e2d414f0ab0153f3fadcf2d6f139910a410121bcf66341882d3` |
| docs/verification/excel-final-consistency-review.md | `a56626ab374df3f114c2d1532bcf797d339deee802787165c2e5b2ae1fc987f9` |
| docs/verification/excel-image-replacement-plan.md | `8a003ef86522c4f42f9c3166f0603ae5c1a5406823345698568636aa019d1ce5` |
| docs/verification/excel-image-replacement-result.md | `ae49d3924d355750a12e3012a8222cd0260e4d513f2a5709fe493b6fe031ce35` |
| docs/verification/excel-visual-print-final-review.md | `14b449f674f9803a2ce68e59c30a42ad997f3080ae1bd9ff5f1db32fdbb3cf4b` |
| docs/verification/flyway-v5-application-plan.md | `0e27380a0f39095e6df7b9304fe64d83bdf33b59f916193534e246449d78751f` |
| docs/verification/flyway-v5-application-result.md | `3f22c7162e665e393673e5adbc4b6ade58fd8514ed6f4f803726cf84141fc9f0` |
| docs/verification/fresh-environment-setup-review.md | `58432883e2ba3a1ee7efaa4c93729709fcd06da92175c6bac33de1fef451f12a` |
| docs/verification/fresh-environment-setup-test.md | `6cb4106dc172c7b544e08ea35b058d470b19383d864ba8a8d8e3adb0ea33af2a` |
| docs/verification/github-history-cleanup-plan.md | `6fa91982b4276a60b4aa53e246733faacdaa593eda02d7e5eb72432e032c8ca2` |
| docs/verification/github-public-document-cleanup.md | `7f7d631c69975e5d0644d67f0f116e605bfb3b5c7fc9a4b45ccf8020a5e129ba` |
| docs/verification/github-public-file-selection.md | `f1e70270061a98d2cfdbd39bfd6b7877a1e8a8dc6e22f75700fd6b0a76dfc495` |
| docs/verification/github-public-preparation-result.md | `53529eb28a07af8e2818d95bdb89353b0b7714bc4f72fc6bcbe4d057a8c71ffe` |
| docs/verification/github-public-readiness-review.md | `bc9f08632d9451b8a3a1347b07b7068c803615abe0bebdf639198696e5210ac2` |
| docs/verification/initial-instructor-setup-test.md | `359376eeca9c7026ed0a782fe18d8130d154d62497ecb3c30cc4f23d8649b1de` |
| docs/verification/integration-test.md | `3a420ed2912c46544836192aee5cd30043d914d0fa1b5c9cd1e2e259b82e1178` |
| docs/verification/member-role-change-session-design.md | `76bac2534b5a0a489f1320bf820993e125e6d2e4e5dcaf114f1d2b0b04ffd115` |
| docs/verification/member-role-change-session-test.md | `5a606d1ca169cc3c3ac1da2e0a6954e14e899b54536a3eab5b4d85170bf8eab0` |
| docs/verification/popup-title-colors.md | `ca146810be257ba04c32adaa67ffd9fed170537dab094ed23b17491af0a2d31c` |
| docs/verification/progress-comment-filter-evidence/progress-filter-after-delete.png | `b8c2a056d42fc185cc0dcacdb36e2869cf3d67ab78e8f028b8216d6aca5ee461` |
| docs/verification/progress-comment-filter-test.md | `1aaae9ea740a70496e1be100b03e898db0dce646feb38960c6dbbeb2f5929735` |
| docs/verification/public-snapshot-e2e-validation.md | `620753a18c9823d194652a8dea38df7fc35c32426d37b9da249154b11ea574f8` |
| docs/verification/public-snapshot-validation.md | `e3835c1253008915370e478f114b5631381feb218cd0da21536638d0e66e1bb9` |
| docs/verification/schedule-category-duplicate-test.md | `710b038bbe3b05814a38944b7f73b41fe394ac1ff0cf16577f132f3dd9851241` |
| docs/verification/schedule-content-v3.md | `9f9f058b074726852b89bff8cbb3c593a07843033a0b2300977a4c64b6c17968` |
| docs/verification/schedule-content-v4-lifecycle-test.md | `2b8beb97ed4116830d34ca0eecadb22a2faf6e2d49ab2b0f88e330914c0e556b` |
| docs/verification/schedule-content-v4-network-guard.md | `f655eff3f0a5c9a9438833262862a8026f6daad4c4b30827b20567d742572113` |
| docs/verification/schedule-content-v4-owned-write-result.md | `b7f0c553ab4ef08bc5069a5e999159762e74deeed56618f1878b04aa34cfb7a7` |
| docs/verification/schedule-content-v4-plan.md | `afa742bf1776a3b3440593c47365997790b44c4850dcf37396bfe20228bcdcdb` |
| docs/verification/schedule-content-v4-post-apply-test.md | `1407f0affbf35f76051b932181eb4d24b260861c4d0609bd514dd11c217f5dc2` |
| docs/verification/schedule-content-v4-public-apply-result.md | `913d67d80a15824f23933a6da282c6777c2799b1140f92e86101aff7e46cd8ff` |
| docs/verification/schedule-content-v4-test.md | `14dbc0c8b3eefb4b25b27c3be4fb3b713dfae44e895ac249144f0886417936db` |
| docs/verification/schedule-name-edit-test.md | `dd2b19f25d63dc683bd082ba60a21d4787fc5f2c0d38844ce638290f1fa0044f` |
| docs/verification/schedule-task-removal-test.md | `fe7eb62081562c779b983e8fcebe136bfa27d48377b5a71eb48c0f8c10e82406` |
| frontend/.env.example | `763009b139e148984c605b9625258032697f741a020bdbfed5cf30c1ce857bcd` |
| frontend/README.md | `a9b7ddf3601923ce552240ac59061f60407d2f5485500066424f39d5b81949d7` |
| frontend/app/(app)/home/page.tsx | `2f2f252122e98d1bd51dee338f2f58286f9b3fba1e9adb02dc4557ea62808bb9` |
| frontend/app/(app)/layout.tsx | `1fa1f0afed56d73880df9aa184391788071fcc976bb240ed0dffda1aaa8f3825` |
| frontend/app/(app)/members/page.tsx | `a7030153c04b87a3c5180166b568ea681afa3ed0722e0abb4aef9715fc60a943` |
| frontend/app/(app)/progress/page.tsx | `03a1564bc794e6a78309c4f06dad16099d62eeb615a89eb6929798c66764b460` |
| frontend/app/(app)/schedule/[id]/page.tsx | `0e272ef82199e916e4c73c7c2f20ff0ed3a1eaae2b539416e82a8e64b2eb5c71` |
| frontend/app/(app)/schedule/page.tsx | `eb118619696b477ff6d1bdecdc9bb9e69d7b4c68ad351aa72eef16a3f869685b` |
| frontend/app/error.tsx | `167ad60389376e26e177e8b17721856bc279f5cadbc76ffc3a02f2b34aac980f` |
| frontend/app/globals.css | `677a795565565637984a9f5e193458889fbe678ea3e7afb1ca7cca28e4f782f5` |
| frontend/app/layout.tsx | `73348985a5f9ae6a4f4fed9b47d45cefbcb410c69fa2a9fca96f2c08c2e6742a` |
| frontend/app/not-found.tsx | `bc05f7569a1412ff81bf3c3f7d0488f5e8333b5ebede28e9dba665afc3d60fd1` |
| frontend/app/page.tsx | `6be7f9dfb6dc417a76439f7c354689c2744bac1e5ebe8b98212cfefe0a0ba2ae` |
| frontend/app/register/layout.tsx | `b34c11c2e813b0b18bffd20a0f6e12ec265e7ddce02f04ed8d0a700c8fc90654` |
| frontend/app/register/page.tsx | `1ee19de1a34916d7b9cbf0d46bb8d68dac4f7eeaff8587413bd2960b1adbc75a` |
| frontend/components/app-shell.tsx | `3e03a1b674b8676640abc7ae98f20b55f933118dd568602cf630c94e6941a767` |
| frontend/components/auth/auth-provider.tsx | `1fe67215a29451baf0db7692605f252cfca38ca1cfe40b1bc38af4827852f8c0` |
| frontend/components/auth/instructor-only.tsx | `1ffd869c377a57bbfaca48504c768e4ea0fe9d52b8693129a47f19ded96ec607` |
| frontend/components/auth/require-auth.tsx | `cc4502fbd2d3b73c2cce43cb5c1489ab7d068fa4199b4ffd6405938fea607c81` |
| frontend/components/message-dialog.tsx | `e0cbe855ecfa180f49cdcaa5dbc90a627d91cd29b4bed900c3cb74f44cf66e02` |
| frontend/components/schedule-detail.tsx | `af4f0750ef0bf174084ffd322194d9191f8576e3b750ae19c1049f5c1e3bef7d` |
| frontend/components/schedule-editor.tsx | `d7f69ad08ca41057d5a766f327c82c861d589461072361a4456af0860d8aee39` |
| frontend/components/status-badge.tsx | `9c392ad16505826177c80379d02beb5dc3cc0b2e0f064451c4bfb6c807d49a1e` |
| frontend/components/ui/button.tsx | `4e679215c9c8480ea8b876cf74d77c1225da544adaf6acba43634a434275a69d` |
| frontend/components/unsaved-changes-guard.tsx | `23f6a3a70839d3ac383291538bf89d4e339a50ece622ae5c61ffb34bc969c0b0` |
| frontend/components.json | `1301860ec7b710eaa7455957545ee4e756e361215cb29cc4cce2192eb47e45fb` |
| frontend/eslint.config.mjs | `242572bc3164157c2cd125678768451f2d0f4345db8f08ea8cdcffad08933fa2` |
| frontend/lib/auth-api.ts | `c7f4dcf5242cdf4ddac11910aaa69c53e4615abc072d404c4a52d69e96312b68` |
| frontend/lib/home-api.ts | `0f9a0de2ecbcec4d2185106ec0df910fb0f97b8eb99523cba462d5c6491f8d4b` |
| frontend/lib/member-api.ts | `d074bc9726119ecabd8f14e3649721435c05f5b61e230fdc70859844b6202e13` |
| frontend/lib/mock-data.ts | `9102181de7c1ec4eb2eeda55e9077bd080fb5c636e4368039c2ec68f6401f9fa` |
| frontend/lib/portal-store.ts | `4e9eb875fff9328f80f5670319b9cce805e2f6035a1f1cc2929256f805a8f844` |
| frontend/lib/progress-api.ts | `b596c1b578417fa2d2952ab41dd70f3bb5227ebdad5cbc0167c02c5aa63e6acd` |
| frontend/lib/schedule-api.ts | `34f994e78f39c78ab1f8166e97e511d9807562a3d7eeaea08999b3aeb0f9ae9e` |
| frontend/lib/task-status.ts | `59e2f890502ecbd6bccce6966852231621c51e70c2129b2a05263c0b3c47d03c` |
| frontend/lib/utils.ts | `e46429536f43f5910f5b2b0c50bb2769b510a4558bb4b5d20a3b0a9091dbf7c3` |
| frontend/next-env.d.ts | `0f70629890b72a0a82e91972cc032c04b658b26c265373cb711cf576bfbf8fcc` |
| frontend/next.config.mjs | `5f18dccf0fac783321b3423d2fbc0b79dd545f034122a74c9cd8ed431834abf4` |
| frontend/package.json | `b4e5f7104934568d10372554ae6145299041061fef7d2ed8ff910aef7b08a7ce` |
| frontend/pnpm-lock.yaml | `77d31bd19642053063641b03ae336e30d22a55eab17b951e4826e68ae86f0ae0` |
| frontend/pnpm-workspace.yaml | `df61fcbf584b40eb1563905213184e57e7038aa73560d5287a93969b819930a7` |
| frontend/postcss.config.mjs | `54b5708e687b8c9ecc8dbeebf29801dbec1213f890c5e018d7ca9c1541a6f7e3` |
| frontend/public/apple-icon.png | `da678942d4656a903f61000551cc51c09464d7a223faaae304e8210e1ca19257` |
| frontend/public/icon-dark-32x32.png | `8a1570b2955c3748b6844356a7487d88c60f9f1e699e870a3452a3decd1ba03c` |
| frontend/public/icon-light-32x32.png | `83145e5bb033ace23cd8e7fbb63e7c39733aaf58f8a7280d7800995cced6f7c2` |
| frontend/public/icon.svg | `263f117c0c69a44d3206a5702105c178ebaa164bca325f3c3973a13bd948f1bf` |
| frontend/public/placeholder-logo.png | `d7c39d978af643ba9525de6fb8171e0f834882db4110c82ecad0df3f4f69551a` |
| frontend/public/placeholder-logo.svg | `19a9e968f4656d52a4c249b4056117ad109370ba9da51cdc630ddded74cec73b` |
| frontend/public/placeholder-user.jpg | `22be067d748175598a5e00123902f5c8332dd7f101de03dfb92ec08d85bb5e53` |
| frontend/public/placeholder.jpg | `14be0c7aa11cca1e78f0966176b286a6d36d00ec64be027775dfeb33d8e2dfdf` |
| frontend/public/placeholder.svg | `64badf7aabda0b9630b87020ffb6095cb858ccbcf66b355c2aa08b1063954d3b` |
| frontend/tests/auth-api.test.ts | `9ab7f92dace7a7d116eac4ab5508cce8518a0217c72b2430ad62e33018f8e13c` |
| frontend/tests/auth-ui.test.tsx | `dc639937a816a6754f448da027c8d64cb9ab217c9960e6a0eb17714256a9b496` |
| frontend/tests/home-api.test.ts | `b2b6f0b26f8da006019b118dc578ae6d819874a83e5e70fd7accfeec383fcf85` |
| frontend/tests/home-ui.test.tsx | `001ab3110c1b5a50ffeecd7ea41edd05e321a512098fe6f6158833d695562ce9` |
| frontend/tests/member-api.test.ts | `e7e54073e50754c676dc85856bb7ff4435b8105aecf4b76dfa3c6118431ce88b` |
| frontend/tests/member-ui.test.tsx | `c465acee849a0f0a5c9e2074599ef0e7d8c2803ed037f44b8c28afc893afcd21` |
| frontend/tests/message-dialog.test.tsx | `a09f0c3229d263873b6a78672277c195ade49c78d8ee8c61321cd06341f5379f` |
| frontend/tests/progress-api.test.ts | `826fd4f4c4b6ccbb9cd5ebf26f42178884362f5594af980ad6fe1a016e145092` |
| frontend/tests/progress-ui.test.tsx | `364ce3d86fc249578d23386defbda027f1708ef072040018c212c677a8c905a1` |
| frontend/tests/register-disabled.test.tsx | `a131ef9d1a37ce0a94b874e6e2690002669cd9b87eff8a39b153c3fa8b426682` |
| frontend/tests/schedule-api.test.ts | `f0a82d8d48b81b7d6e4c28c147f234551b4a3f4ffb142c1e29b5eaef5dcb6f26` |
| frontend/tests/schedule-create-ui.test.tsx | `cb142901931c9a9cfc849ddcf413b0e5f406669c2a39fb9ec30732109ce65d4d` |
| frontend/tests/schedule-detail-ui.test.tsx | `94045e94cdedbb80610077c67638954ace851196bfa37eb26e1c53021afd7f82` |
| frontend/tests/schedule-ui.test.tsx | `7b1b40e8bc0cca787ddedfd4da31324fac5f7d63d305f04f86e57b327ac9d766` |
| frontend/tests/setup.ts | `336db78be01dd4a66a03d753e5ac15489b856c2965cbb6fd28988ea8253b4e6f` |
| frontend/tests/unsaved-changes-guard.test.tsx | `382994ecc6f83cd412f1673ed3edc701c3c5fda38714d3782c38856b68bcf05e` |
| frontend/tsconfig.json | `f2fc3309a966ecb35a51a17b2b7ab03c92d88b7c663ddbed5cb626f597c83277` |
| frontend/vitest.config.mts | `d4ffed353aa7a6038dc634b9fa4e373c2c10bf451238c6c34f1d088bc0c7b977` |
| mvnw | `cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02` |
| mvnw.cmd | `4a361e1374a3e5ad6d03e18e9adc0cf181ac5058ac6203b76f0ba3b456b56481` |
| pom.xml | `82a4c793860b40e10237bef549175b8949819657b35b130415d52e781604b700` |
| src/main/java/com/training/portal/TrainingPortalApplication.java | `b85fffbdaf13e89702d7319192ebaa6ddcb1a74fcaafdab9591aa4281de3275b` |
| src/main/java/com/training/portal/auth/AuthController.java | `47bcf1b587af67f2788308354a67df0a9c7b9b3bb752c9a9a7bf957adacb2449` |
| src/main/java/com/training/portal/auth/AuthResponse.java | `45b11d0a57c7798639d6be0406211f6cebc427afe5ec3c9478eac61a20875dc2` |
| src/main/java/com/training/portal/auth/LoginRequest.java | `2b02b4ccc2151c8586e1f3087c92b2584568938c57e41929d29a2986718920f2` |
| src/main/java/com/training/portal/auth/MemberPrincipal.java | `36b58932192b9f50ac6881a626400f82b1c28599b04bc4a2b277e4914b0dba05` |
| src/main/java/com/training/portal/auth/MemberUserDetailsService.java | `9b01e79e9565b7bb81723512a3c093ab7bc43fd83022339eb0555893bfc8f6a0` |
| src/main/java/com/training/portal/auth/RoleSessionFilter.java | `cf3357826687fef579f2c5a7126bf31313402a42898a42f58f256e14b34e8132` |
| src/main/java/com/training/portal/auth/RoleSessionGuard.java | `05dffa4bbae55364c39e0772912558a63851129df82df622e09c5a7b4792e370` |
| src/main/java/com/training/portal/common/api/ApiError.java | `bc767db10eca090fd00866a29aa8f807a69f73ce4029541003371d041af811ba` |
| src/main/java/com/training/portal/common/api/ApiExceptionHandler.java | `2812a3f3e2414af2aae50299d36f60d8708711472448936b272eb5f86192c7e5` |
| src/main/java/com/training/portal/config/BusinessTimeConfig.java | `e9d6035bb2b624c68ece67de38ca309ee0a7273ee6b4231fe52120f15dd8e4bb` |
| src/main/java/com/training/portal/config/PasswordEncoderConfig.java | `1421b0b9629ea9080f4059b87aeaa77cb4e80322836ff5dae6fbf16a2ecc1988` |
| src/main/java/com/training/portal/config/SecurityConfig.java | `045587280c8104389ccf35d8d9b527c8f0f31681812acc496cb58b0cc7d85909` |
| src/main/java/com/training/portal/home/HomeController.java | `9b5e00766d11941b8bc6a401911e8fd502797194b940ae6755aa120db13c3518` |
| src/main/java/com/training/portal/home/HomeService.java | `e34066d03214fb5c283ffddbf5c95f3ab09902b699df3a07e45adce7de811e29` |
| src/main/java/com/training/portal/home/dto/HomeDashboardResponse.java | `1ccd70c430477d696937a7f0d18e55aeae7a3ca75fb7432d47ac54b1631e3448` |
| src/main/java/com/training/portal/issue/IssueStatus.java | `82a4cb2cc5b0dd802725f8a86e083816d9a05f85640e4ae2418ebc69f537592e` |
| src/main/java/com/training/portal/issue/IssueStatusRepository.java | `5e7327f531b405e3bd9a80ea022c02eae0b80e4b114cd148ce56b55b2a07a2d1` |
| src/main/java/com/training/portal/issue/IssueSubmissionComment.java | `594aa0183a9f88338c397b81d513e9641fabf3d57e9d771cd39b2c85c438e0d5` |
| src/main/java/com/training/portal/issue/IssueSubmissionCommentId.java | `956e6f3363aa088e3188ded35672881d8a44b92edc6c3b580501787c42aa2f54` |
| src/main/java/com/training/portal/issue/IssueSubmissionCommentRepository.java | `3e2902ff49603de545e2c4739dfd5471c338faf59fc5a1e1aa89e4509cafda9e` |
| src/main/java/com/training/portal/issue/IssueSubmissionStatus.java | `7cb7697a29b3fefd5839e40df34c5d5e2d275bab7f3d370ce65ff6346b4bba1a` |
| src/main/java/com/training/portal/issue/IssueSubmissionStatusId.java | `798ef607a55eb53443635051943da713264944473314f04afd08a14117c883c3` |
| src/main/java/com/training/portal/issue/IssueSubmissionStatusRepository.java | `8f57bdd14c17114bdac7545de37df565165c5bcaa98361499668a1a426290def` |
| src/main/java/com/training/portal/member/KenshuMember.java | `a822f2c0fff32ab10787fd00f3afd5c735e52c3d5b030c66ca290c2505ab2bb7` |
| src/main/java/com/training/portal/member/KenshuMemberController.java | `b45e71170af619da4b971db1975f78a9e32e68c9ed106598903b44bdc1dd1067` |
| src/main/java/com/training/portal/member/KenshuMemberRepository.java | `9a0f9616824ccfb4e4acd75781602f118aa98556cebfb1bdb68587f14a209d5d` |
| src/main/java/com/training/portal/member/KenshuMemberService.java | `128b1077f66166de3ae99edc92a90f0c8c1092912e11c2c0db8d99853f1a4a44` |
| src/main/java/com/training/portal/member/MemberRoleChangeSupport.java | `f9a4fcf02fb3da36cabf8486b71723e219ea9edec8223d07ba2e5758eee950e8` |
| src/main/java/com/training/portal/member/dto/BCryptInput.java | `a65013146c242cb0bd5063812fb517233829dad2387d8074a4351f2223243828` |
| src/main/java/com/training/portal/member/dto/BCryptInputValidator.java | `eb02574b33d9fd62742dd40323e3fd36fbf0e1ec722bc294e6743cf7d093ac09` |
| src/main/java/com/training/portal/member/dto/MemberCreateRequest.java | `b278a26d6b6614f866729ec70a2d09b7d96c0c79d585b9989156376186bd0612` |
| src/main/java/com/training/portal/member/dto/MemberResponse.java | `ffb47ea380b0b2708aefe16e9dd35b5e00271cf1b7a01cc6e3e68dbd3f138256` |
| src/main/java/com/training/portal/member/dto/MemberUpdateRequest.java | `3a5e2f6a34402488564d7f71889e9003cf5926930fa09c6ce21bab64fc13a67c` |
| src/main/java/com/training/portal/member/exception/DuplicateMemberException.java | `a0dbfd35f95c41b35b6659dd3a2dc6dd852c7b01a00577a1ad9132c7b741952e` |
| src/main/java/com/training/portal/member/exception/MemberNotFoundException.java | `3c1d15cf07523dcd85329fc7e5559a1fbd9a9a66428b3986dbbd2b8fe1e999f1` |
| src/main/java/com/training/portal/member/exception/MemberReferencedException.java | `db904be0c5e2084532b4af08f150169b850bc26f5b4b85dfa1ec2211fc37d426` |
| src/main/java/com/training/portal/member/exception/MemberRoleChangeNotAllowedException.java | `a1d1c0effcb29200408529fec2b9a6c03afcccc31b3a1084634d346bbc109726` |
| src/main/java/com/training/portal/member/exception/MemberSelfDeleteNotAllowedException.java | `c83e8cec6c764829184d30d229e91ceba067b2bce3eec3ec51c3e12166d77c73` |
| src/main/java/com/training/portal/member/exception/MemberUpdateConflictException.java | `ac3d6cfd14b7654c48381b4347a7feb49a5797a6479ea64ae6ce8b7f2f1ae23f` |
| src/main/java/com/training/portal/progress/ProgressController.java | `0f31b92fba3d22519a547c472f235902498c43380d3cc2b3213e81b5d834282d` |
| src/main/java/com/training/portal/progress/ProgressService.java | `0a5420bbfa346f1c23fcc66c247faec7d0182c4ca0112a51272b416bef79cb0b` |
| src/main/java/com/training/portal/progress/dto/ProgressCommentRequest.java | `3936db3915012cbd7b1ba8db582c1a440725ad454a9e615ff2d8978cb0e203c1` |
| src/main/java/com/training/portal/progress/dto/ProgressCommentResponse.java | `efb9ac502c73f9f471b8be99c013e246d702f2e6c23535922e655f90246bec24` |
| src/main/java/com/training/portal/progress/dto/ProgressQuery.java | `885c1691717b9da571c44b51b1496259123a3c8a9bbe86f35c15fb5fbf3a68c2` |
| src/main/java/com/training/portal/progress/dto/ProgressResponse.java | `c3aed6cb7ad3be6cbbc16cb0c5caf4269e9f968d3a6fb7d95099419d849e4f13` |
| src/main/java/com/training/portal/progress/dto/ProgressSubmissionUpdateRequest.java | `76b6405090100a882d299687010a86d41b95e02c1b1a0286d4c1698f4129d59d` |
| src/main/java/com/training/portal/progress/dto/ProgressSubmissionUpdateResponse.java | `d69a5108a9aaa6140f437bdca1cca1e69e37e12357b7d712c9302d7fb2048595` |
| src/main/java/com/training/portal/progress/exception/ProgressAccessDeniedException.java | `af38a8983e87509a39063d4fb4de1b7ab90cdffe7c8a24f9db3c26d8b9e141f7` |
| src/main/java/com/training/portal/progress/exception/ProgressInvalidInputException.java | `1ea07a2888b2653778114f5876975c4e70d31231414df0ebc33920275388d8c0` |
| src/main/java/com/training/portal/progress/exception/ProgressNotFoundException.java | `57f7e401380c1ebf0349ba755355b436d4d039c8fc6ee6d7c4d9c9a84edf94ae` |
| src/main/java/com/training/portal/progress/exception/ProgressUpdateConflictException.java | `094cfc40f1aacf60140399d732ce018886720532176ce732d0c643d606d4146f` |
| src/main/java/com/training/portal/schedule/JapaneseHolidayService.java | `8da2369a7f7acee69583b5536340b61f0ffdc65e95d1dbd29ed859867aa4bdb3` |
| src/main/java/com/training/portal/schedule/Schedule.java | `ba49ddaf816affeb1bc13b858384b6cd8151cece352b465daf0127eb9aaf4097` |
| src/main/java/com/training/portal/schedule/ScheduleBunrui.java | `06b0f6cfbe7c01dae152ecf7f024cd27ab29261412a6a7f05eba74189782384c` |
| src/main/java/com/training/portal/schedule/ScheduleBunruiId.java | `bb1ac808e1c57b68f749222b840f9c7f6d4461ad706811f1de5d29656069dd96` |
| src/main/java/com/training/portal/schedule/ScheduleBunruiRepository.java | `3875d74cce3984e014fa53aa62749adfe9f75db92b84b6cb582bdc6ebca8a32f` |
| src/main/java/com/training/portal/schedule/ScheduleCanEdit.java | `710542f6bf861ef1b367228c030def5587d35e5b18dff3206f06ff6832ccd724` |
| src/main/java/com/training/portal/schedule/ScheduleCanEditId.java | `32d915bf037811b13f1682af7dd1ff421967bc9c881dddb30f4234dc0357b76b` |
| src/main/java/com/training/portal/schedule/ScheduleCanEditRepository.java | `461e798075c89c0e666cfcc8c3fdd51b7148514e01043df640930a4870324cf1` |
| src/main/java/com/training/portal/schedule/ScheduleController.java | `e8a031f42e4a007448fc86cca8e33b6495c6b7e076f16a79b8c2a40d142da0b6` |
| src/main/java/com/training/portal/schedule/ScheduleDetail.java | `21964021b355e3bfbfe0b7030788702f8b45c61864b2b87aaadabb66285ff8a3` |
| src/main/java/com/training/portal/schedule/ScheduleDetailId.java | `d437aa91a158952261105c933d07390a19dfaea8c7e08b0a58cde656bec06e03` |
| src/main/java/com/training/portal/schedule/ScheduleDetailRepository.java | `68e817df041e59774121266ca83144ef4d419616f856bbdd1d12668b3c886b82` |
| src/main/java/com/training/portal/schedule/ScheduleRepository.java | `001037c6053f7ce92131d70f06c642a0c66fcc3e33c0614fca992021ac0fc376` |
| src/main/java/com/training/portal/schedule/ScheduleService.java | `4a9b3ae9855e2a51368132794733db465cd4f9bf55f7f1d53315a5cb1ed33f40` |
| src/main/java/com/training/portal/schedule/dto/ScheduleCreateRequest.java | `cc074ae75a6abf54dec056eccdaa255080d7fb7db0f2df81733afc971a57eaab` |
| src/main/java/com/training/portal/schedule/dto/ScheduleDetailResponse.java | `77833592f18d6163b27a164e3fb83f3b9b6a1d8acc0758269bdcc237f6f729de` |
| src/main/java/com/training/portal/schedule/dto/ScheduleListResponse.java | `8cde7089545e14e6f01b4370ba5d941dae17c54c8d31841d9171efd69d708af4` |
| src/main/java/com/training/portal/schedule/dto/ScheduleMemberOptionResponse.java | `fce58aff5efcfde7df0caf1748d4a11457f15dc388144c0cafe4f452d2ff854b` |
| src/main/java/com/training/portal/schedule/dto/ScheduleNonWorkingDatesResponse.java | `c4b52829d93c3b82f8932bc92f305df1a312af9d605b3ad080b29479727ddc18` |
| src/main/java/com/training/portal/schedule/dto/SchedulePinUpdateRequest.java | `b21ef20f5670a386b50f9b47568ba6d87db51317730e82de6ad64268335e9506` |
| src/main/java/com/training/portal/schedule/dto/SchedulePinUpdateResponse.java | `daa63bb098f6f32d581bf0b711cfa74945fd13778921188f01cff05930fcc017` |
| src/main/java/com/training/portal/schedule/dto/ScheduleUpdateRequest.java | `d4ae5b88670bd9ff0d788865789ea09e4b427926a8bfb6b10fa3edf553c210d1` |
| src/main/java/com/training/portal/schedule/exception/DuplicateScheduleException.java | `4d58011d57ef2e2ffdb9b4c029de705589a4f48275f0c1484df5ce570181a2e6` |
| src/main/java/com/training/portal/schedule/exception/ScheduleAccessDeniedException.java | `575797841a4d202f6d91e7fac1595e984d9ccbc65692f519efd92d78bbf0eb9e` |
| src/main/java/com/training/portal/schedule/exception/ScheduleCategoryInUseException.java | `d80205d03a70e7c2bacb68ad84285573768bb66efc2813e8353a74d15ccdb9a3` |
| src/main/java/com/training/portal/schedule/exception/ScheduleEditableInstructorRequiredException.java | `d8cee8209a8b4faabdeb5d9b8891e6dc7bcaea296dc8ed9f7fba647a39bec492` |
| src/main/java/com/training/portal/schedule/exception/ScheduleHasIssueSubmissionsException.java | `ffd2c754447bd9c9950d2a736c50265fd41441cb1edd2e9422fa8eb886f6a25c` |
| src/main/java/com/training/portal/schedule/exception/ScheduleInvalidInputException.java | `e5060b83941456825eafb52bf673d55875d007d9565fd73d5fa79d0c5bcebfbf` |
| src/main/java/com/training/portal/schedule/exception/ScheduleNotFoundException.java | `dfb63befaf67113e8762fa4a2e636e8dc8de1cb8c327b134e02f66d447799e82` |
| src/main/java/com/training/portal/schedule/exception/SchedulePermissionMemberInUseException.java | `e9add4d932dcbfb4c7187610820b8975b8fb2d94a235a40fd8f1a9006ad0e514` |
| src/main/java/com/training/portal/schedule/exception/SchedulePinLimitExceededException.java | `9ead54bf960db21bb7545bda164d6839cf295f084b87c38ec9e4d5fad8b7ca1c` |
| src/main/java/com/training/portal/schedule/exception/ScheduleReviewerPermissionRequiredException.java | `52b1e80b2a0b3c7e187b998d402cf02c6998792cf629cde629b77e1ec35f4e40` |
| src/main/java/com/training/portal/schedule/exception/ScheduleTaskRemovalBlockedException.java | `a4d5b6f1336217cc7c9cb8c2395b61015a7b3c466f3a9ffc03562f8ba469a7cd` |
| src/main/java/com/training/portal/schedule/exception/ScheduleTraineeRequiredException.java | `910b5cd42c81cf488aee80196bc7c2e445f57806334a4513adddb71ac356205f` |
| src/main/java/com/training/portal/schedule/exception/ScheduleUpdateConflictException.java | `3d52204a8a57494b1f34f7888a5865842f97dcaac47973704bde4dbfcae9bdc3` |
| src/main/java/com/training/portal/setup/FreshDatabaseCommand.java | `70736d3cf8f4c0dc26ed907d6c52bab29108db3ecf80a6cf387b2c0702a12eac` |
| src/main/java/com/training/portal/setup/InitialInstructorCommand.java | `bd576d2614561ef880a0d3c0b30b43cb2df75c8f84ca37f6f423c93b8b93be20` |
| src/main/java/com/training/portal/setup/InitialInstructorService.java | `7e7fea32ba779b9bd934de7e0610819250bd4de4be55a6b64df36b7942df0ad1` |
| src/main/resources/application.properties | `de6c5ac917e3d7d61ea9d65b7b026fbd52b1f08954f06f8b139411a60ce11ee8` |
| src/main/resources/db/migration/V1__create_tables.sql | `df787f5f24e05ff10a5414169aaa2ebbf5948161935e32b2946c72d71773454c` |
| src/main/resources/db/migration/V2__insert_issue_statuses_master_data.sql | `17c9d848675ea26ca9abd5e8acf9507b316534e20d4ae33b6fb8ded73ecb2630` |
| src/main/resources/db/migration/V3__allow_null_schedule_content.sql | `305212aec43bfed52a0f56ea5cbf526ba63de401fb0aa10867a3cf87a138cfa5` |
| src/main/resources/db/migration/V4__allow_null_schedule_content_in_public.sql | `c8c8fce7632110f4ee6a09290d058a213d3afbe5c0d06bd8c2c5c2223821dfa7` |
| src/main/resources/db/migration/V5__add_member_auth_version.sql | `a95cc12eaeb1714b6cc509cba81caff521a94a41b43a3d9ba895f78f7dd79307` |
| src/test/java/com/training/portal/TrainingPortalApplicationTests.java | `2304dcf00edcfd34f1aa53af30e2352fd9a15c44d3ca35d387bc252fcbd8c7f7` |
| src/test/java/com/training/portal/auth/AuthApiTest.java | `1b8e66ac559c1826caf840aa12fd4a7d27f90fe187e7f95ebea9c6b8b4e255e4` |
| src/test/java/com/training/portal/auth/RoleSessionConcurrencyTest.java | `31023f4dd925daa95e730beeae55a713eb6737a2629375477d9898eb0923c802` |
| src/test/java/com/training/portal/auth/RoleSessionFilterTest.java | `58bea4dc9b0521973fc1423c6529645d5725220df6e6bd11d962dd4fd39d69a7` |
| src/test/java/com/training/portal/auth/SessionCookieTest.java | `8540a263056675ac6ca495be856a8be66e9db1eb869c34049d035398fbce6d5a` |
| src/test/java/com/training/portal/home/HomeApiTest.java | `d7ce716f6e9ad4c2a58d898fa807657b3c611250f6eec2c87e197b058527737f` |
| src/test/java/com/training/portal/integration/ScheduleProgressHomeIntegrationTest.java | `c03ae0712c974c7596cde4f7e290a433b975fdb8497158772aca621b043fdedc` |
| src/test/java/com/training/portal/issue/IssueStatusMasterMigrationTest.java | `ece76e8ca6acce965c3eff15e1b9966b58162de942b76302c0a98cd19e8a08ba` |
| src/test/java/com/training/portal/member/KenshuMemberControllerTest.java | `5f199cee685cd0e209e553b58d0ccb9a5177f1df623c792fa6a065786c0bea95` |
| src/test/java/com/training/portal/member/KenshuMemberPersistenceTest.java | `6ebad852ca4089c6bc0db114aa1ad00243975028fe593d97fe2ec98b28ce3471` |
| src/test/java/com/training/portal/member/KenshuMemberServiceTest.java | `00b8d99c19b4753fbbca3d3a21c655b5a4f42079d7786d894724fe3e073adc04` |
| src/test/java/com/training/portal/member/MemberRoleChangeApiTest.java | `e2909a2a4853d3e0479b698ff301b60e58d88c1d10e367d2a1660c9c1be4ec16` |
| src/test/java/com/training/portal/progress/ProgressApiTest.java | `bc5f07e3a303e48c95ea67df9929444cd8490b7d27a4b1790cb56c24715606b5` |
| src/test/java/com/training/portal/schedule/JapaneseHolidayServiceTest.java | `7fa411f1daf85d288475a9f6ac810ce16ed9ee84f682d67a00f7512bb6b4de70` |
| src/test/java/com/training/portal/schedule/ScheduleApiTest.java | `53fdcccc77df3f37fabe0d879c641e47aaf9fd784c5bedba102c3e8aa3bbdc8a` |
| src/test/java/com/training/portal/setup/FreshDatabaseCommandIntegrationTest.java | `5e3a45006a53bfad32873720bed3f47113ba3fc23add1766bd556eebd03d50d9` |
| src/test/java/com/training/portal/setup/FreshDatabaseCommandTest.java | `4aa1dadc860a9413aae49e700bfaa94f74a2f4999b48459b370f5043ad84a47b` |
| src/test/java/com/training/portal/setup/InitialInstructorApiTest.java | `efab3fd7ebd35d42a22180d426551fe250238445518bf4329c9ee89bfadee076` |
| src/test/java/com/training/portal/setup/InitialInstructorCommandTest.java | `5bd04aef6172889a81b7e9e7f15751e2219ddb9a4b791c4948ddef6d03d6ce2c` |
| src/test/java/com/training/portal/setup/InitialInstructorConcurrencyTest.java | `96c1f7abe7af5ed44fefee0a28b6e2e2daec7dc8cde5f5a83bad4756b21c553b` |
| src/test/java/com/training/portal/support/SecurityTestUsers.java | `2a4f5cba5239ab210a78c2825fddd8d5374937efb9d56686f554f01206ba13bc` |
| src/test/resources/application.properties | `b31cf8a12f23710357af09c3d05ed0ae5fc0d804d83b0b3b4253a25a143c46a5` |
| tools/flyway/PublicV4Runner.java | `d18d9c3af26723c19298b645e26be21fe3b5c99b7e14a83d06be96553f132c28` |
| tools/flyway/docker-isolation/Dockerfile | `4fc9532010104d589e8b70d09a150a2bdc0c65df04a8a8e8386fa742b63b8c46` |
| tools/flyway/docker-isolation/entrypoint.sh | `14d5f8f3bfc9fafb33e28bea674044c514295b1c5c2fe344455d3b42324b1e34` |
| tools/flyway/docker-isolation/lifecycle.py | `d04966502eaf5c9bfd9967a907eecc100f12d20df071b77cb78f2fb5568cee1b` |
| tools/flyway/docker-isolation/network_guard.py | `e34b5d78679576a3e75dbec1ed885b7d7bd8a5f644b115027ee9a79e31ec94ca` |
| tools/flyway/docker-isolation/test_network_guard.py | `2d019228b7886f09875b9cfc61c28af3e542483288fc2d3583631502fe9a1137` |
| tools/flyway/fresh-browser-check.cjs | `d3aeb4b5a8b9c9c816d9e4696f5f47def15e20f6a58a3c8e7446078eaf7a2b71` |
| tools/flyway/run-public-v4.py | `abef451db4b368e0ed4a17948e1365556394aaaf876555e1f9691045db464b94` |
| tools/flyway/test-fresh-database.py | `c67a622c00c33de60b2241b9628d31f6cea481328bd1c605fbd9ab7122668033` |

最終チェック：元開発リポジトリの既存ファイル・インデックス・HEAD・全ref・ローカルGit設定は前後不変。公開用コピーの既存削除0、業務コード・SQL・Excel変更0。変更はREADME追記と報告書2件追加のみ。DB接続・Git履歴操作・GitHub操作は行っていない。
