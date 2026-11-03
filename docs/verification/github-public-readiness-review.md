# GitHub Public化前の監査結果（2026-10-18）

## 1. 現時点の評価

**Public化は保留を推奨する。秘密情報の流出が確定したという判断ではない。** 現行実装に必要なファイルの未追跡、Excelの編集者・保存元情報、過去の依存物・ビルドキャッシュ、画像の個人情報確認が残るためである。

develop、HEAD d63ebbaa。開始時は追跡202ファイル、変更済み38ファイル、未追跡72ファイル（.pnpm-storeのDB1件を含む）。本報告書はこの件数に含めない。未コミットの現行実装は、現在のHEADをcloneしただけでは取得できない。

今回はローカル静的監査のみ。DB接続、migration、アプリ起動、依存追加、外部サービスへの送信、GitHub設定変更、ファイル削除、履歴書換え、commit/push/mergeは行っていない。既存ファイルは変更しない。

## 2. 監査範囲と方法

- 作業ツリーの追跡ファイルとキャッシュ以外の未追跡ファイル273件を確認。除外されたローカルfrontend/.env.localも値を表示せず確認した。
- Gitの全ローカルrefから到達可能な22コミット、27,358種類のblobを検査。通常のbranches/remotesから到達可能なのは19コミットであり、残りはローカル監査支援用refに由来する。削除済みファイルもblob単位で含めた。パスは同一blobの代表パスの場合がある。
- パスワード設定、秘密鍵マーカー、代表的なGitHub/AWS/Google/Slackトークン形式、JWT形式、資格情報入りURL、セッションCookie形式、ローカル絶対パスを検査。第三者コードの候補はアプリ固有の資格情報と区別した。
- 現在のExcel2冊と履歴内の4種類のExcel blobをZIP/OOXMLとして検査した。
- 現在のdocs画像・Excel埋め込み画像63種類、履歴の追加17種類、合計80種類を抽出。ローカルVision OCRで79種類を処理できた。過去のfrontend/public/placeholder.jpgの1種類は画像として読み取れなかった。画像の同一性はバイトのSHA-256で判定。
- OCR、画像メタデータの存在確認、Excelコメント・非表示・外部リンク・埋め込みの構造確認を実施した。全画像を人間が目視した結果ではない。
- README、frontend/README、docs/setupおよびdocs全体のMarkdown形式の相対リンクを確認した。

制限：任意形式の秘密値を完全検知する専用商用監査ではない。依存コード内の任意の難読化文字列、バイナリキャッシュの復号・意味解析、全アーカイブの展開、個人名の本人照合は未実施。GitHubへfetchしていないためリモートにしか存在しないref/PR/削除ブランチ、reflog限定・到達不能オブジェクトは保証対象外。Public化前にリモート側の公開対象refも確認する。

## 3. 秘密情報・個人情報の検出結果

| ID | 種別・場所 | 結果・対応 |
| --- | --- | --- |
| SEC-01 | Spring BootのDBパスワード設定 | src/main/resources/application.propertiesは環境変数参照。対象の設定・SQLから実運用の固定DBパスワードは検出しなかった。環境変数の実値を出力していない |
| SEC-02 | frontend/.env.local | ローカルファイルが存在し、gitignore対象。到達可能な履歴の同名ファイルは検出しなかった。現在の除外状態を維持し、git add -fしない |
| SEC-03 | frontend/.env.example | 追跡済み。公開API URLの設定例のみ。DB秘密値をここへ追加しない。NEXT_PUBLIC変数はブラウザ公開情報として扱う |
| SEC-04 | 履歴内の秘密鍵形式 | 外部依存5ファイル、8箇所。dotenvx CLI例、dotenv README英語/スペイン語、jose鍵構文確認、Next環境変数ドキュメント。例・省略表現・構文マーカーとして分類。dotenvx例には完全なPEM形式もあるため、形式検出を単に無視しない。アプリ運用鍵として利用された根拠は見つからなかった |
| SEC-05 | 履歴内の資格情報URL/JWT | dotenvx sample.js:7、fast-uri test/fixtures/uri-js-parse.json:91/104、zod classic/tests/string.test.ts:307、zod mini/tests/string.test.ts:289/292。依存のサンプル/テストとして分類。値は転載しない |
| SEC-06 | DBバックアップ・秘密鍵ファイル | 監査対象の追跡/未追跡と到達可能な履歴で、独立したdump/backup/pem/key/p12/pfx形式の実ファイルは検出しなかった。SQLに社員の固定初期データはなく、V2は提出状況マスタ投入。DBバックアップへの文書内パスとバックアップ本体は区別する |
| SEC-07 | 両ExcelのdocProps/core.xml | lastModifiedByに編集者識別情報が存在。公開可否を本人確認し、不要なら別作業で除去。歴史版にも存在する |
| SEC-08 | 両Excelのxl/workbook.xml | absPathのurl属性に個人環境の保存元絶対パスが存在。公開前の除去を推奨。値は転載しない。通常のセル表示だけでは確認できない |
| SEC-09 | テーブル定義書のコメント | xl/comments1.xml〜comments8.xmlに計16コメント、作成者エントリ8件。研修メンバー、スケジュール、スケジュール_編集権限、スケジュール_分類、スケジュール詳細、課題状況、課題提出状況、課題提出状況_コメントのO4/AA4。コメント作成者の識別情報を公開前に整理。コメント本文も削除可否を別途レビューする |
| SEC-10 | 画像・画面キャプチャ | OCRからメール・個人環境パス・代表的なCookie/キー形式は検出なし。氏名/社員/講師等の欄は多数存在し、実在性はOCRでは判別できない。合成データとする検証報告はあるが、全画像の実在人物不在を保証しない |
| SEC-11 | 画像メタデータ | 80種類中12種類にメタデータ形式を検出。PNG11種類にeXIf/iTXt、JPEG1種類にEXIF。GPS/作成者等の全タグの意味解析は未完了。公開前にメタデータを確認し、不要なものを除去する |
| SEC-12 | Gitコミット識別情報 | author/committerのメール識別子は2種類。Gitの通常仕様として履歴に含まれる。値は転載しない。本人が公開を許容するアドレスか確認する。メール形式の存在そのものを秘密鍵流出とは扱わない |

現行のアプリ固有の実運用トークン・秘密鍵・セッションCookieの流出は今回の検査では確定していない。テストコードの合成パスワード/CSRF値は実資格情報と区別する。ただし実環境で同じ値を使っていないことは、コードだけでは証明できない。

Excelは39+10の49シート、非表示シート0、hidden属性の行列0、外部リンクパート/埋め込みOLEパート0。基本詳細設計書に画像52個、テーブル定義書に数式215件。名前定義は2件/1件。これは内部構造の確認であり、全セル・画像の個人情報不存在やMicrosoft Excel上の表示品質を保証するものではない。

## 4. Git履歴の監査結果

現在の.gitignoreにより除外されていても、過去の内容は公開される。履歴に次の生成物が残っている。

| 種類 | 一意blob数 | blobの非圧縮合計 |
| --- | --- | --- |
| frontend/node_modules/ | 26,520 | 450,570,871 bytes |
| frontend/.next/ | 376 | 63,114,933 bytes |

同一ファイルの異なる版を含む一意blobの合計であり、clone転送量ではない。Git packは136.98 MiB、loose objectsは11.98 MiB。最大blobはNext SWCのmacOS用ネイティブライブラリ88,930,328 bytes。次点はlibvips 17,765,992 bytes。Turbopackキャッシュにも約10 MBのblobが複数存在する。node_modulesの導入コミットはb31fe70eで確認できる。履歴には.pnpm-store/v11/index.dbも存在する。

これらは現在の業務ソースではない。容量だけでなく、ビルド時のローカルパスや環境情報を含むため、Public化前に履歴から除外する方針を決めることを推奨する。今回履歴変更はしていない。

安全な対応案：既存履歴を別の非公開バックアップへ保存し、別のローカルmirrorでgit-filter-repo等による対象パス限定除外を試す。元リポジトリは変更せず、監査再実行・ref差分・必須ファイル確認後に、ユーザー承認を得て公開先へ反映する。既に他者がclone済みなら履歴変更の共有と再cloneが必要。新しい公開用履歴を作る方式も比較できるが、過去の非公開リポジトリやコピーが消えるわけではない。実秘密が今後見つかった場合は、履歴除去より先に失効・ローテーションする。

## 5. Git管理対象の過不足

### 公開に必須・推奨の追加

- V1〜V4は追跡済み。**V5__add_member_auth_version.sqlは未追跡**。V5を欠いた公開は認証世代管理とDB構造の不整合になる。
- MemberPrincipal/RoleSessionFilter/RoleSessionGuard、MemberRoleChangeSupport/例外、対応テストは未追跡。追跡済みの変更ファイルと一体で必要。
- FreshDatabaseCommand、InitialInstructorCommand、InitialInstructorService、そのテスト群は未追跡。
- docs/setup/fresh-environment.md、initial-instructor-setup.mdは未追跡。READMEはこれらを参照している。
- tools/flyway/test-fresh-database.py、fresh-browser-check.cjs、関連テスト・SecurityTestUsers・結合テストも未追跡。公開する検証手順に必要。
- README/API/画面/Service/設定依存のpom.xmlなど38件の変更は未コミット。新規ファイルだけを追加しても現在の完成状態にならない。
- Maven Wrapper、wrapper properties、frontend/package.json、pnpm-lock.yaml、.env.exampleは存在し追跡済み。今回の確認で必要なファイルが誤ってignoreされている事例は検出しなかった。

### 任意の検証資料・公開前レビュー対象

docs/verification配下の報告書と画像は監査証跡として有用だが、起動に必須ではない。採用する報告と相対リンク先の証跡をセットにする。ローカルパス・識別情報を整理し、画像は合成データのみであることを確認してから追加する。

tools/flyway/apply-public-v4-verified.pyおよびdocker-isolationの復元・書込み補助群は、既存DBの過去の隔離検証用。一時バックアップの場所、特定のmanifest、特定のPostgreSQL配置等を前提にするため、新規構築の必須ツールとして扱わない。採用するなら過去用途を明記し、公開用の一般手順と分離する。V4既存ランナーの安全ガードを今回変更していない。

### 公開不要

.pnpm-store/v11/index.db、node_modules、.next、target、ビルド出力、ローカル.env、DBデータ領域/ダンプ、資格情報JSON、Chrome専用プロファイル、Cookie/トークン入りネットワーク記録。現在の未追跡.pnpm-storeは8,192 bytesのローカル索引DBであり、ソースとして追加しない。DB中身の全レコード意味解析は未実施。

## 6. .gitignoreの改善候補（今回は変更しない）

- .pnpm-store/を除外する。
- ルートや他階層の.env/.env.*を除外し、必要な安全な.exampleのみ例外にする。現在はfrontend/.env*だけが対象。
- *.dump、*.backup、*.sql.gz、DBデータ領域、runtime-credentials.json、.pgpass、認証用鍵/証明書の秘密部分、専用ブラウザプロファイルを用途に応じて除外する。
- 検証用ログ/生ネットワーク記録/一時ディレクトリの出力場所をGit外に固定する。*.sqlやすべての画像・JSONを一律除外しない。migration、設計画像、安全なfixtureを誤って隠さない。
- ignoreは過去の履歴や既に追跡中の情報を消さない。履歴対応と別に扱う。

## 7. README・セットアップの参照整合性

現在の作業ツリーではREADME・frontend README・docs/setupの相対Markdownリンク9件は全て存在する。docs全体でも同形式のリンク先欠落は検出しなかった。URL・アンカー・コードブロック内の任意文字列まで実行保証したものではない。

READMEのセットアップ先と検証結果は未追跡なので、現在のHEADだけではリンク先を取得できない。公開予定のファイル集合として再確認する必要がある。

文書には過去の一時ログ・バックアップ・専用データ領域の絶対パスが多数残る。現在の監査では文書と一部ツールにlocal-path形式を64箇所検出した（Excel内部を含む。全てを機密情報と断定しない）。個人アカウント名を含むものは匿名化、再現手順が一時スクリプトを必要とするものは継続利用するrepo内手順へ置換、単なる過去証跡は「一時記録で再現に不要」と明示する案を推奨する。

新規構築はrepo内FreshDatabaseCommandとsetup手順を使用する。古いV4ツールは新規構築に転用しない。初期講師コマンドは非表示入力・ローカル明示実行であり、未認証Web登録を追加するものではない。通常起動のtarget=2と、専用ランナーの段階的V1〜V5適用を混同しない。

## 8. クリーンcheckoutでの再現性

前回の構築結果は、現在の未コミットソース、ローカルのJava/PostgreSQL/Node/Chrome、既存Maven/pnpmキャッシュ、インストール済みnode_modulesを使用している。完全クリーンclone・依存キャッシュ空での検証は未実施。本監査でも実行していない。

推奨検証手順：

1. 公開予定ファイルをレビューした後、その完全なスナップショットを別環境へ用意する。現HEADのcloneだけでは未追跡機能を検証できない。
2. Java21/PostgreSQL17/Node/pnpmを手順に従って準備し、別のMavenローカルリポジトリとpnpm storeを使用する。node_modules/.next/targetを持ち込まない。
3. lockfileに従い依存取得し、外部取得先の到達性を記録する。next/fontのビルド時ネットワーク依存も確認する。
4. 開発DBとは別の専用PostgreSQL、非5432ポート、空DBを用意する。publicとtest1はランナー仕様に従い別々の空DBで検証する。
5. V1/V2→publicのV3省略登録→V4/V5、test1のV1〜V5通常適用、初期講師、起動、ブラウザログイン・基本画面を確認する。DBパスワード/初期パスワードは出力しない。
6. git statusで秘密設定や生成物が公開候補に混入しないことを再確認する。

この検証は既存training_portal/public・test1に接続しない条件で、別作業として実施する。

## 9. Public化前の必須対応・優先順位

1. **高：Excelの編集者/コメント作成者情報・保存元パス、Gitメールの公開可否を確定。** 必要なら許可を得て安全に除去し、過去版にも残ることを考慮する。
2. **高：未追跡のV5・認証・セットアップ実装とテスト・文書を公開集合へ含める。** 現在の変更38件と依存関係を含めてレビューし、一部だけ公開しない。
3. **高：全ての公開画像を目視し、実在社員情報・認証情報・画像メタデータを確認。** OCR未読1画像、EXIF/XMP相当の12画像も含む。不要な検証画像は公開集合から外す方針を検討する。
4. **中：履歴内node_modules/.next/.pnpm-storeの扱いを決定。** 別コピーで履歴整理を試し、再監査してから反映可否を判断する。
5. **中：.gitignoreを補強し、検証資料/古いツールのローカル絶対パスを整理。** 再現に必要なツールと過去証跡を区別する。
6. **中：公開予定スナップショットでクリーン構築を実施。** 空依存キャッシュからの取得、初期講師・実ブラウザまで確認する。
7. **公開直前：公開対象refの秘密情報再検査、git diff --check、最終git statusを確認。** GitHubの自動秘密検出だけに依存しない。

実秘密の漏えいが確定した場合は優先順位を繰り上げ、対象資格情報を失効してから履歴修正方針を決める。今回はそのような資格情報の値を報告へ記載していない。

## 10. 変更・検証記録

今回追加するのは本報告書だけ。アプリ・設定・SQL・Excel・既存資料は変更しない。DB操作・アプリテスト・migrationは監査に不要なため未実施。既存ファイルの前後SHA-256比較とgit diff --checkの結果を末尾に記録する。

## 付録：開始時の未追跡ファイル（キャッシュを除く71件）

以下は全て自動的にコミット推奨という意味ではない。実装/テスト/setupは必要、検証画像と旧環境専用ツールは上記分類に従い精査する。

- docs/setup/fresh-environment.md
- docs/setup/initial-instructor-setup.md
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
- tools/flyway/apply-public-v4-verified.py
- tools/flyway/docker-isolation/prepare.py
- tools/flyway/docker-isolation/restore_isolated.py
- tools/flyway/docker-isolation/test_lifecycle_docker.py
- tools/flyway/docker-isolation/test_restore_mounts.py
- tools/flyway/docker-isolation/verify_owned_workflow.py
- tools/flyway/docker-isolation/verify_restored.py
- tools/flyway/docker-isolation/write_verify.py
- tools/flyway/fresh-browser-check.cjs
- tools/flyway/test-fresh-database.py

最終確認：開始時に保存した既存273ファイルのSHA-256は全件不変。git diff --check成功（終了コード0）。追加は本報告書のみ。Git管理外キャッシュ・ローカル設定の全バイト不変検証はこの273件の比較に含めない。
