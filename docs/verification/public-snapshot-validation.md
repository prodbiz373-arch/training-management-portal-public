# 公開用スナップショット構築・検証（2026-10-18）

## 1. 結果と公開判断

現在の作業ツリーを元に、履歴のない独立スナップショットを準備し、空の専用Maven/pnpmキャッシュからの依存取得、ビルド、専用PostgreSQL、初期講師、実Chromeログインと4画面の表示を確認した。技術的な構築・基本動作の検証は成功した。一方、スキップしたテスト、ブラウザ業務CRUD未実施、Excel遷移図・表示・印刷未確認が残るため、無条件のPublic化承認とはしない。GitHub操作は行っていない。

元リポジトリの変更は、ユーザー指定の本検証報告書追加だけ。既存ファイル・未コミット変更・Git履歴・設定・インデックスは保持する。Excelや業務コードの修正は行っていない。公開用コピー内の過去検証資料2件だけは、発見した個人ホームのバックアップパスを匿名化した。

## 2. 保存先・ファイル集合

最終配布用ディレクトリ：`${NON_DISTRIBUTED_EVIDENCE_DIR}/public-snapshot`。検証用ワークスペース：`${NON_DISTRIBUTED_EVIDENCE_DIR}/validation-workspace`。いずれもGit管理外・今回専用であり、一時領域の長期保存は保証しない。管理者は必要ならGit外の安全な永続領域へ保管する。

開始時の非除外ファイル282件から、既に非公開分類となった17件を除き、265件をコピーした。git未追跡の必須実装も含む。不明な新規ファイルは候補へ追加していない。さらに本報告書1件を配布用コピーへ追加し、最終266件とする。
.git、過去コミット、補助ref、node_modules、.next、.pnpm-store、target、DBバックアップ、.env.local、ログ、一時証跡、ブラウザプロファイルは最終配布物へ含めない。検証用ワークスペースに生成した依存やDBデータを、配布用へコピーしない。

全265件は最初にコピー元・先のSHA-256一致を確認した。匿名化2件は意図的な相違として元/配布版ハッシュを台帳に記録し、それ以外263件は元ファイルと一致させる。匿名化2件と追加報告書を含む最終ファイル台帳は検証用領域のfinal-manifest.jsonへ保持する。

## 3. 必須ファイル・参照整合性

次を現在の作業ツリーから収録した。HEADからのgit archiveではなく、未追跡の最新実装も明示的にコピーしている。

- Maven Wrapper・pom.xml・Javaのsrc/main/src/test。
- Flyway V1〜V5。SQL・checksum・通常target=2は変更していない。
- MemberPrincipal、RoleSessionFilter/Guard、社員区分変更・認証世代関連実装/テスト。
- FreshDatabaseCommand、InitialInstructorCommand/Service、関連テスト。
- tools/flyway/test-fresh-database.py、fresh-browser-check.cjs。
- frontendのソース・テスト・package.json・pnpm-lock.yaml・.env.example。
- README、docs/setup、docs/api、最新のExcel2件、公開候補の検証資料・合成画像。

候補資料のローカルMarkdownリンクは参照先不存在0件。非公開17件は配布物に存在しない。過去の監査台帳内で非公開ファイル名を説明することは、当該ファイルの配布を意味しない。旧V4保守ランナーは新規構築手順で使用しない。

## 4. セキュリティ再監査

ソース・設定・Markdownの秘密鍵、トークン形式、秘密値らしい代入、個人ホームパス、メール候補を静的検査し、Excel ZIP/XML・識別メタデータ・画像ハッシュ・画像メタデータも確認した。外部サービスへソースや認証情報を送信していない。依存取得ではMaven Central・npm等の配布元へアクセスした。

初回27箇所の候補を検出。うち個人ホームパス2箇所を公開用コピーだけで匿名化した。残りはWebルート等18箇所と、テスト認証用の固定入力7箇所として区別した。これらのテスト値は本番資格情報を意味しない。秘密値・個人ホームの実値は報告しない。高確度の秘密鍵・アクセストークンはこの検査では検出しなかったが、網羅的な秘密情報不存在の保証ではない。

| 公開用コピー内のみの修正 | 位置 | 内容 |
| --- | --- | --- |
| docs/verification/schedule-content-v4-owned-write-result.md | 元21行 | 個人ホームを含むバックアップパスを非配布の匿名表記へ変更 |
| docs/verification/schedule-content-v4-post-apply-test.md | 元12行 | 同上 |

検証件数・失敗・未実施の履歴は変更していない。両資料に匿名表記の意味を追記した。`${PRIVATE_BACKUP_DIR}`は新規構築に必要な環境変数ではなく、非配布バックアップの説明用表記である。

画像はExcel52個、独立画像/資産21個の計73点を列挙した。Excel旧41画像とのハッシュ一致は0件。差し替え済み41画像は前回の合成データ撮影台帳のハッシュと全件一致。Excelのcreator/lastModifiedByは空。sipsで読めるラスタ画像のメタデータで個人識別・GPS等の要確認キーは検出なし。SVGはラスタメタデータAPIの対象外で、テキスト検査対象とした。

画像の全小文字を今回新たに原寸目視したわけではない。以前の画像監査・合成データ撮影記録と現在のハッシュ一致を根拠に引き継ぐ。実在性や許諾が記録で確定しない画像・文面を新たに安全と推測していない。Excelの全シート表示・印刷と遷移図は手動確認待ち。Git履歴は配布物へ含めないが、履歴を持つ元リポジトリをPublic化してよいという意味ではない。

## 5. 空キャッシュでの依存取得・ビルド

Java21.0.12.1、Node24.20.0、pnpm12.4.1、PostgreSQL17を使用。Maven User Home、Mavenローカルrepository、pnpm storeをそれぞれ新規空ディレクトリへ分離した。元の.m2・pnpm store・node_modulesをコピーしていない。OSに既存インストールされたJava/Node/pnpm/PostgreSQL/Chromeは利用するため、OS自体が完全初期状態という意味ではない。

最初のsandbox内依存取得はDNS/ネットワーク制限で失敗。許可された実行環境で再実行し成功した。アプリの不具合とは分類せず、初回失敗を隠していない。

| 項目 | 結果 |
| --- | --- |
| Maven Wrapper配布物・依存取得 | 新規専用キャッシュへ取得成功 |
| Java package | 成功。最初のpackageは-DskipTests、試験結果とは別 |
| pnpm install --frozen-lockfile | 新規専用ストアで成功。lockファイル変更なし |
| フロントエンドテスト | 15ファイル218件成功、失敗0 |
| TypeScript型チェック | 成功 |
| lint | 成功 |
| Next Webpack build | 成功。next/fontエラー再発なし |
| 起動用フロントコピー | 同じ公開候補ソース＋今回取得したnode_modulesを参照。元開発環境の依存へリンクしない |

READMEの手順のうち実行はpnpmの通常buildではなく明示的なWebpack buildを利用した。通常buildの別エンジンまで検証済みとはしない。外部フォント・パッケージ配布元へのネットワークアクセスが必要な環境条件は残る。

## 6. 隔離DB・Flyway・初期講師・起動・ブラウザ

公開候補に収録したtest-fresh-database.pyを、公開用コピー内で実行した。個別テストと回帰テスト、パッケージ内の対話式コマンド、起動、実ブラウザまで同一の専用環境で確認した。生成したパスワードは対話PTYで非表示入力し、報告書・Git・起動引数へ出力していない。

| 接続先 | 値 |
| --- | --- |
| 専用PostgreSQL | 127.0.0.1:53076 |
| 専用Spring Boot | 127.0.0.1:53077 |
| 専用Next | localhost:53078 |

既存127.0.0.1:5432のtraining_portal.public/test1には接続していない。今回のDB名training_portalは完全に別クラスタ内で、同名でも既存DBではない。

| 検証 | 結果 |
| --- | --- |
| public新規構築 | V1/V2通常→V3 SQL未実行の省略登録→V4/V5。全履歴success=true |
| 別DB fresh_test1のtest1 | V1〜V5通常適用、全履歴success=true |
| auth_version | 両スキーマでbigint・NOT NULL・DEFAULT 0 |
| 構築ガード等の集中テスト | 14件成功、失敗/エラー/スキップ0 |
| 初期講師 | パッケージ内コマンドで登録、社員区分2・auth_version=0・BCrypt格納を監査 |
| Spring Boot通常起動 | target=2のまま成功 |
| 実ログイン/API | Home・Progress・Schedules・Membersすべて200 |
| Next起動 | 成功 |
| 実headless Chrome | フォームからログインし/home、/progress、/schedule、/members表示成功 |
| 終了 | 専用Chrome・Next・Spring Bootを終了、専用PostgreSQL停止成功 |

スキーマの履歴と列定義を最終読み取り確認するため、今回専用クラスタだけを一時再起動して再停止した。DBを配布物へ含めない。公開用スナップショットの新規構築は既存DBバックアップへ依存しない。

## 7. 回帰テスト・業務機能の検証範囲

バックエンド回帰は総数217、成功207、失敗0、エラー0、スキップ10。集中14件とは重複するクラスがあるため、合計をユニークなテスト件数として加算しない。

| スキップしたクラス | 件数 | 扱い |
| --- | --- | --- |
| com.training.portal.auth.RoleSessionConcurrencyTest | 3 | この回帰実行の条件では未実施。成功件数に含めない |
| com.training.portal.integration.ScheduleProgressHomeIntegrationTest | 1 | この回帰実行の条件では未実施。成功件数に含めない |
| com.training.portal.setup.FreshDatabaseCommandIntegrationTest | 5 | この回帰実行の条件では未実施。成功件数に含めない |
| com.training.portal.setup.InitialInstructorConcurrencyTest | 1 | この回帰実行の条件では未実施。成功件数に含めない |

FreshDatabaseCommandIntegrationTestの5件は集中試験では実行成功し、回帰実行では専用環境変数がないためスキップした。同時実行・連携の残る5件については今回の回帰では未実施。RoleSessionConcurrencyTestとInitialInstructorConcurrencyTestにはINTEGRATION_TEST_PORT=59455の固定条件があり、任意ポートの完全クリーン検証へそのまま適用できない。ポート固定を避けるテスト整備は別途検討が必要。過去の成功記録で今回のスキップを埋めない。

既存APIテストで認証・メンバーCRUD・スケジュール・Progress・Homeの回帰を確認した。ブラウザは初期講師のログインと4画面表示のスモークまで。新規メンバー・スケジュールの登録/更新/削除、提出状況更新、コメントCRUD等の一連のブラウザ業務フローは今回未実施で、APIテストをその成功として流用していない。

## 8. 生成物・証跡の分離と最終確認

最終配布物は原本台帳のパスだけから改めて作り直し、検証で生じたtarget/node_modules/.next・秘密設定・一時証跡をコピーしない。元ファイルと配布物のハッシュを照合し、匿名化2件以外の差異0を確認する。追加報告書のハッシュも台帳へ収録する。Markdown相対リンクを最終再検査する。

元のファイル・Gitインデックス・HEAD・全ref・Gitローカル設定の前後照合、およびgit diff --checkを実施する。元の既存ファイル変更は0、追加は本報告書のみとする。

## 9. 公開までの残作業

1. Excel遷移図15本、基本39シートとテーブル10シートの表示・印刷を手動確認する。未確認のまま最終品質合格としない。
2. 今回スキップした同時実行・連携5件を専用条件で追加実行し、必要なら主要CRUDの実ブラウザ確認を追加する。
3. 最終ファイル集合と匿名化2件をレビューし、画像の小文字・公開許諾等の手動判断を完了する。
4. 配布用スナップショットとmanifestを安全な永続領域へ保管する。検証ログ・DB・ブラウザプロファイルは別管理する。
5. 公開用コピーから新しい履歴を開始するかを別途承認して決定する。元Privateリポジトリの履歴は引き継がない。今回はgit init/add/commit/push/mergeやGitHubリポジトリ作成を行わない。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
