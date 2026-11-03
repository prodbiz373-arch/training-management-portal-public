# 新規環境セットアップ再現性調査（2026-10-18）

## 1. 結論・調査範囲

**リポジトリと既存手順だけによる新規public環境の構築は、現時点では再現性確認済みとは判定できない。** 新しいPostgreSQLクラスタ/DBの作成、Migration、初期講師登録、アプリ起動の一連操作は今回実施していない。

現行作業ツリーにはV1〜V5、初期講師コマンド、API/起動設定がある。しかし、通常target=2とV5を要求するEntityの組合せ、public専用V3、4ファイル固定のV4ランナーにより、READMEの通常起動だけでは空DBの初期構築が完了しない。V5までの新規構築を担う、リポジトリ内で完結した実行手段と統合手順が不足している。

開始時developを確認。既存未コミット変更を保持した。今回は本報告書だけを追加し、README・セットアップ文書・実装・設定・Flyway SQLは変更していない。DBへ接続していない。既存public/test1の更新、初期化、migration再実行、新規Docker構築は行っていない。macOSのアクセシビリティ/System Events/画面収録操作も行っていない。commit/push/mergeなし。

比較対象：README.md、frontend/README.md、docs/implementation/frontend-auth.md、docs/setup/initial-instructor-setup.md、tools/flyway配下、通常/テストapplication.properties、pom.xml、frontend/package.json/pnpm-lock.yaml/.env.example、初期講師Command/Service/関連テスト、既存のV5・初期講師検証報告。

## 2. 現在の構成と再現可能な範囲

| 対象 | 確認した状態 | 判定 |
| --- | --- | --- |
| README.md | プロジェクト説明とfrontend-auth.mdへのリンクが中心 | 新環境用の入口として不足 |
| docs/setup | initial-instructor-setup.mdが存在 | 初期講師の前提・手順はあるがDB初期化とpublic移行は別の承認済み手順を要求 |
| Maven | Wrapper・Java21指定・Boot4.1.1・通常main固定 | ビルド定義あり。今回クリーンな依存キャッシュからのビルドは未検証 |
| Flyway | V1〜V5が作業ツリーに存在 | SQLは揃うがpublicへの一括適用不可 |
| 通常Spring Boot | training_portal/public、target=2、ddl-auto=validate | V5まで構築済みのDB用。空DBのブートストラップには使えない |
| 初期講師 | 独立main・対話式非表示入力・空の社員テーブル限定 | DB前提を満たせば利用する構成。今回実登録未実施 |
| Next.js | package.json、pnpm-lock.yaml、.env.exampleあり | 起動材料あり。最新の統合手順とバージョン固定の追記が必要 |
| 自動テスト | 標準接続先はlocalhost:5432/training_portalのtest1、target=latest | 既存test1へ無確認で実行してはいけない |

「既存ローカル環境で動いた」という過去の報告と、「第三者がクローンして新規構築できた」という検証は異なる。

## 3. 新環境構築の推奨手順（設計案・今回未実行）

以下は不足ツールを整備した後の推奨フローであり、そのまま実行できる完成済み自動化手順ではない。既存開発DBへ流用しない。

### 3.1 前提と隔離

1. Java21、PostgreSQL17、Node.js、pnpm、Python3（移行ツールに採用する場合）を用意する。既存文書はNode24/pnpm12を案内するが、frontend/package.jsonにはengines/packageManagerの固定がないため、公開手順では検証済み版を明示する。
2. 空の専用PostgreSQLクラスタを用意する。検証では既存5432とは別の専用ポートを選ぶ。例は55432だが空き・接続先を必ず確認し、固定の空きポートとみなさない。
3. 独立クラスタ内のDB名はtraining_portal、ロールはtraining_portal_userとする。初期講師コマンドがこの名前に固定されているため、同じ既存クラスタに別名DBを作るだけではコマンドをそのまま使えない。
4. PostgreSQLの管理者として新クラスタだけにロール・DBを作る。一般アプリロールをDB所有者にし、スーパーユーザーをアプリに使用しない。publicの所有者pg_database_ownerは通常のまま維持する。

新クラスタへの接続を確認した管理者がpsql内で実施する初期化案：

```sql
CREATE ROLE training_portal_user LOGIN;
-- psqlの非表示入力でDB用パスワードを設定する。固定値をSQLへ書かない。
\password training_portal_user
CREATE DATABASE training_portal OWNER training_portal_user;
```

新DBのアプリロールで、必要な場合だけ次を準備する。

```sql
CREATE SCHEMA test1 AUTHORIZATION training_portal_user;
```

クラスタ作成・OSサービス設定・認証設定は現行READMEに不足しており、Windows/macOS/Linuxの対象範囲を決めた手順整備が必要。上記SQLは今回実行していない。publicの無差別GRANT、既存DBのDROP、バックアップ復元による初期データ流用は新規構築の前提にしない。

### 3.2 Migrationをアプリ起動と分離する

Spring Bootの通常起動を初期Migration手段にしない。通常target=2ではauth_version未追加のままHibernateの最新Entity検証へ進むため、空DBでは起動が成立しない。単にtarget=5へ上書きしてpublicを起動するとV3のスキーマガードで失敗する。

先に専用の移行プロセスで次節の順番を実行し、validate・pending・所有者・カラムを確認する。通常設定ファイルのtarget=2は変更しない。V1〜V5の編集、SQL直実行と履歴手動INSERTによる代替、repair/baseline/cleanは採用しない。

### 3.3 ビルド・初期講師登録

Migration前のビルドはDBテストを動かさない方法に分ける。

```sh
./mvnw -DskipTests package
```

これはテスト成功を意味しない。V5までの適用後、実端末で以下を実行する。例のポートは新クラスタのものに合わせる。

```sh
java -Dloader.main=com.training.portal.setup.InitialInstructorCommand \
  -cp target/training-portal-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --initialize-first-instructor \
  --host=127.0.0.1 \
  --port=55432 \
  --database=training_portal \
  --schema=public
```

接続先再入力、DBパスワードの非表示入力、社員情報、初期講師パスワード2回の非表示入力を行う。コマンド自身はMigrationを実行しない。社員が1件でもいれば追加を拒否する。初期講師の固定パスワードを手順・環境変数例へ載せない。

### 3.4 バックエンド起動

V5適用済みの専用DBを使う。DBパスワードは安全に入力してTRAINING_PORTAL_DB_PASSWORDへ渡す。Bashとzshではreadのオプション構文が異なるため、公開手順はシェルを明示する必要がある。既存のread -s例を全OS共通とみなさない。

通常の新環境がlocalhost:5432なら設定の既定値を使用できる。隔離ポートの場合は設定ファイルを編集せず、実行時引数で接続先を明示する例：

```sh
java -jar target/training-portal-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:55432/training_portal
```

スキーマは通常public。Spring Bootの通常ポートは8080。起動ログでHibernate validate、Flyway validate、想定外のpending/migrateなしを確認する。通常target=2でV5まで適用済みのDBが起動することは過去の開発DB検証報告にあるが、新環境で今回は再検証していない。

### 3.5 フロントエンド起動

```sh
cd frontend
pnpm install --frozen-lockfile
cp .env.example .env.local
pnpm run dev
```

公開設定例：

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

localhost:3000でアクセスする。ブラウザからのAPI URL・CORS許可Origin・Cookieのホストを整合させ、localhostと127.0.0.1を混在させない。NEXT_PUBLIC_へ秘密値を入れない。productionではAPIベースURLをビルド時に設定する。

ビルド案：`pnpm exec next build --webpack` → `pnpm run start`。既定のpnpm run buildはnext buildでありWebpack指定ではない。依存取得・SheetJSの外部配布URL・next/fontのGoogle Fonts取得にネットワークが必要で、完全オフライン構築を保証しない。

初期講師でログインし、メンバー管理から新人・講師を登録する。スケジュールは新人1人以上・編集可能講師1人以上の制約を満たして確認する。/registerからの自己登録を解放してセットアップを代替しない。

## 4. Flyway V1〜V5の適用方法

### publicの新規構築

| 段階 | target | skipExecutingMigrations | 直前pendingの想定 | 処理 |
| --- | --- | --- | --- | --- |
| 1 | 2 | false | [1,2] | V1・V2通常実行。8業務表と7ステータス |
| 2 | 3 | true | [3] | V3のSQLを実行せずFlywayで履歴登録 |
| 3 | 4 | false | [4] | V4通常実行。public研修内容のNOT NULL解除 |
| 4 | 5 | false | [5] | V5通常実行。auth_version追加 |

各段階は接続先・schema・SQLハッシュ・履歴・チェックサム・pendingを検証してから実行し、不一致なら停止する。V3のskip=trueは別プロセスに閉じ込め、V4/V5へ引き継がない。V3を変更・削除せず、publicでそのSQLを通常実行しない。

### test1の新規構築

currentSchema・Flyway schemas/defaultSchemaをすべてtest1へ固定する。空のtest1ではtarget=5、skip=false、pending=[1,2,3,4,5]で通常適用する設計が可能。V3はtest1のNULL許可を実行し、V4はV3適用後のNULL許可を確認してDDL変更なしで完了し、V5が列を追加する。任意の別スキーマ名はV3/V4で許可されていない。

両スキーマともV1〜V5成功・validate成功・target5のpendingなし、auth_versionのBIGINT/NOT NULL/DEFAULT0/CHECK>=0、ステータス7件、所有者・ACLを確認する。初期登録前はkenshu_member=0件。既存環境ではこの新規用の手順を再実行しない。

### V4ツールのV5対応可否

**現行V4専用ツールはV5非対応。新規構築用にそのまま使用できない。**

- PublicV4Runner.javaのfiles()はファイル数を4件に固定。現在は5件である。ハッシュ/履歴の許可リストもV1〜V4のみ。今回、この条件をDB接続なしで照合して不整合を確認した。
- modesはdry-run/skip-v3/apply-v4のみで、V1/V2の新規適用やV5の適用を提供しない。
- 接続はlocalhost:5432、DB/ロール/public固定で、既存のV1・V2（またはV1〜V3）履歴があることを要求する。
- run-public-v4.pyは同ランナーとローカルMavenキャッシュ内の7依存JARの固定ハッシュに依存する。依存取得機能はなく、クリーン環境では別途準備が必要。
- apply-public-v4-verified.pyは既存開発DB、test1のV1〜V3、特定のローカル検証manifest、特定のPostgreSQLインストール先を要求する。新規環境の汎用セットアップではない。
- docker-isolation/prepare.pyは特定の一時バックアップ・ハッシュと4件のmigration集合を要求する。新規の空DB用には使えない。

ファイル数ガードを弱めたりV5を隠して既存ツールを通す対応は提案しない。V1〜V5に対応した新しい移行手段を別途実装・隔離検証する。

## 5. 不足ツール・手順と追加案

| 優先度 | 不足・差分 | 最小限の対応案 |
| --- | --- | --- |
| 高 | publicの新規V1〜V5 bootstrap手段なし | tools/flywayに新規構築専用ランナーを追加。schema/host/port/DB/user明示、空状態と各phase履歴ガード、V3だけskip、V4/V5通常実行、validate/replayを実装 |
| 高 | 取得済みJAR・一時manifestへの依存 | Mavenで定義から依存を取得し、検証済みFlyway/PG driverを再現できる手順を追加。個人キャッシュの存在を前提にしない |
| 高 | DB/role/schema初期化の統合手順なし | docs/setup/fresh-environment.md等を追加。新クラスタの確認と安全なパスワード入力、所有者/public/test1、移行順を記載 |
| 高 | V5・初期講師コード等がGit未追跡 | 公開前レビュー後に必要ファイルを公開対象へ含める。今回はadd/commitしない |
| 中 | READMEが既存DBの起動説明へのリンクだけ | 新規構築と既存環境起動を分け、新セットアップ→初期講師→起動を入口から辿れるようにする |
| 中 | frontend/README.mdにプロトタイプ/任意パスワード等の旧説明 | 最新API接続・登録方針へ整理。古いモック説明を現在の認証として案内しない |
| 中 | frontend-auth.mdに認証のみ接続/メンバー等は次回との旧説明 | 現在の接続済み範囲、DIFF-31、初期講師手順へのリンクへ更新 |
| 中 | Node/pnpm版・シェルの明示不足 | 検証版を文書化。必要ならpackageManager/engines追加を別作業で判断 |
| 中 | テストの既定接続先が既存test1 | 専用クラスタを指定する安全なテスト実行例と確認ガードを追加 |
| 中 | ローカル絶対パス・一時証跡が公開資料に多数 | 再利用ツールは引数/PATH化。歴史的報告は実行手順と分離し個人パスを公開前レビュー |

上記は追加案であり、今回ランナー・README・設定等を新規実装/更新したものではない。

## 6. 一時領域・ローカル環境依存

- V5適用報告には別Javaプロセスによる適用が記録されているが、その汎用V5ランナーはtools/flywayに存在しない。この実行環境には一時領域のrole-migration.pyが残ることを確認した。クローンした環境には存在しないため、公開手順の依存先にできない。今回実行・流用していない。
- 初期講師検証のDB準備・端末自動入力・ブラウザ補助も一時スクリプトで、再利用可能な公開テスト基盤ではない。初期講師コマンドそのものはリポジトリ作業ツリー内にある。
- V4適用wrapperのユーザーLibrary配下の特定manifest、prepare.pyの一時バックアップ、PostgreSQLのmacOS固定インストール先は新環境に存在しない。
- .m2/repositoryは通常のキャッシュ場所だが、特定の7ファイル・ハッシュが既に存在することを前提とする現在のlauncherには依存準備手順が不足する。
- 新規構築に既存業務データのバックアップを含める必要はない。検証用バックアップやruntime-credentials.json等をGitへ追加して依存を解消してはいけない。

## 7. 初期講師登録の確認結果

コード調査で確認：社員区分2固定、BCrypt既存cost10、認証世代0、入力チェック/重複チェック再利用、対話式非表示入力、接続先の固定・照合、空テーブル判定、EXCLUSIVEロックと同一トランザクション、通常Web起動からの非公開、Flywayを実行しない構成。

今回実行したDB非接続テスト：

```sh
./mvnw -Dtest=InitialInstructorCommandTest test
```

**3件成功、失敗0、エラー0、スキップ0。** 正しい対象指定、対象/引数不正の拒否、Consoleなしで接続前に拒否を検証した。SpringのDBコンテキストを起動しないテストである。実登録・BCrypt照合・同時登録・ログイン成功を今回この3件で確認したことにはしない。

既存initial-instructor-setup-test.mdには、空の隔離test1へV1〜V5適用、実コマンドの初期講師作成、再実行拒否、画面ログイン、新人/講師追加の成功報告がある。この過去の証跡は参考になるが、新規publicをクローンから一括構築した今回の結果ではない。

DB名・ユーザー名・ホスト127.0.0.1・public/test1という制限により、リモートDBや別名DBにはそのまま使用できない。既存の制限を今回緩和していない。

## 8. 環境変数・公開時の注意

| 設定 | 用途・注意 |
| --- | --- |
| TRAINING_PORTAL_DB_PASSWORD | 通常Spring BootのDBパスワード。実値をGit/ログ/コマンド引数へ含めない |
| TRAINING_PORTAL_FRONTEND_ORIGIN | 既定http://localhost:3000。CORSの単一Origin |
| TRAINING_PORTAL_COOKIE_SECURE | ローカルHTTP既定false。公開HTTPSではtrueを検討・設定 |
| TRAINING_PORTAL_COOKIE_SAME_SITE | 既定lax。別サイト構成のNoneはSecure/HTTPSとブラウザ制約を含め別途確認 |
| NEXT_PUBLIC_API_BASE_URL | ブラウザへ公開されるAPI URL。秘密値を置かない。productionはbuild時設定 |

初期講師コマンドはDBパスワードも対話式に入力し、通常起動用環境変数に固定した初期認証情報を流用する方式ではない。フロントの.env.exampleには公開API URLだけがあり、.env.localはgitignore対象であることを確認した。ローカル.env.localの実値は読み取り・出力していない。

公開準備で特に重要なのは、作業ツリーとGit登録内容の差である。今回、V5 SQL・InitialInstructorCommand.java・docs/setup/initial-instructor-setup.mdがGit未追跡であることを確認した。関連Service・認証世代クラス等も含め、必要ファイル一式の公開対象漏れを確認する必要がある。現在のHEADだけをクローンして最新仕様になるとは扱わない。

### 秘密情報・パスの限定スキャン

作業ツリーのGit追跡/未追跡候補のテキストを走査した（生成物・依存キャッシュ・大きなバイナリは対象外）。秘密値そのものを報告しない。結果は次のとおり。

- テキスト走査：245ファイル。
- 秘密鍵ヘッダ検出：0件。
- 対象とした既知トークン形式の検出：0件。
- DBバックアップ/鍵等の対象拡張子ファイル：0件（今回の公開候補集合内）。
- ローカル絶対パスを含むファイル：15件。
- 一時パスを含むファイル：28件。上記件数とは重複し得る。

トークン検出は一部の既知形式だけであり、全種類の認証情報が存在しないことを保証しない。テスト専用の合成認証値と運用資格情報の全面的な手動分類、Git履歴、画像/Excel内部の個人情報・作成者メタデータ、ignoredファイルは未監査。公開前に別途確認する。

絶対パス・一時パスの多くは検証報告書だが、ツールの実行依存にも一部残る。ローカル利用者名を含むパスは公開向けには一般化が望ましい。パスの存在を「パスワード漏えい」と同一視せず、実行依存と情報公開の両面で整理する。.pnpm-storeは未追跡であり、依存キャッシュを公開対象へ混入させない。

## 9. 今回の実施/未実施

| 内容 | 今回の結果 |
| --- | --- |
| README/セットアップ/コード/SQL/設定照合 | 実施 |
| V4ランナーの4件制限と5件の現状の照合 | 実施。非対応をDB接続なしで確認 |
| 初期講師の非DB単体テスト | 3件成功 |
| 秘密情報形式・ローカルパスの限定走査 | 実施。完全監査ではない |
| git diff --check | 成功 |
| 新規PostgreSQLの作成・V1〜V5適用 | 未実施 |
| 新規publicでの初期講師作成・ログイン | 未実施 |
| クリーンキャッシュからのMaven/pnpm依存取得 | 未実施 |
| 新環境でのバックエンド/Next起動・build・E2E | 未実施 |
| 開発DB/public/test1への接続・変更 | 実施していない |
| macOS画面操作権限を要求する操作 | 実施していない |

調査を優先し、public用の再利用可能な移行手段が不足する状態で一時ツールへ依存した新規構築を追加実行しなかった。未実施を成功としていない。

## 10. 次に必要な作業・実施順

1. 新規public/test1用のガード付きV1〜V5移行手段をリポジトリへ用意し、ローカル一時ファイルへの依存をなくす。
2. DB初期化→publicのV3省略→V4/V5→初期講師→両アプリ起動を統合した手順を追加し、READMEから案内する。古いモック・既存DB前提の説明を整理する。
3. 既存DBと完全に独立した新クラスタで、クリーンcheckout/依存キャッシュから一連手順を実行する。publicだけでなく必要なtest1の構築・安全なテスト手順も確認する。
4. 初期講師のログイン・追加メンバー作成、CSRF、auth_version、スケジュール/Progress/Homeを確認する。
5. 必要な未追跡ファイル、秘密情報、個人パス、依存キャッシュ、バックアップ混入を公開前レビューする。

上記の新規構築実証が完了してから「リポジトリの手順だけで新環境構築の再現性を確認済み」と判定する。
