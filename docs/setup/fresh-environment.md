# 空の専用PostgreSQLからの新規セットアップ

既存DBの移行・復旧手順ではありません。Java 21、PostgreSQL 17（検証17.11）、Node.js 24（検証24.20.0）、pnpm 12（検証12.4.1）を用意します。依存取得にはネットワークが必要です。以下はmacOS/LinuxのBash向けの例で、Windowsのコマンド構文は未検証です。

通常のSpring BootはFlyway target=2のままです。最新EntityはV5までの構造を必要とするため、**先に新規専用ランナーを実行**してください。通常起動を空DBの初期構築に使わないでください。

## 1. 専用PostgreSQLの準備

既存のlocalhost:5432/training_portalのpublic/test1は使用しません。ランナーは安全上5432番を拒否します。新しいデータ領域・ポート・ソケットを使い、他のアプリが接続しない自分専用クラスタを準備してください。本番・共有DB・既存データを持つDBは禁止です。ポートだけではクラスタの専有性を保証しないため、作成した管理者が接続先を確認します。

PostgreSQL 17のinitdb/pg_ctl/psqlをPATHへ設定します。例の55432は空きポートであることを確認し、使用中なら別の1024以上のポートを選びます。PGDATA変数を既存の領域へ上書きしないよう、専用の変数名を使用します。

```sh
mkdir -m 700 "$HOME/training-portal-fresh"
mkdir -m 700 "$HOME/training-portal-fresh/socket"
# このディレクトリが既に存在する場合は停止し、既存のものを削除・再利用しない。
initdb -D "$HOME/training-portal-fresh/data" -U postgres \
  --encoding=UTF8 --locale=C --auth-local=scram-sha-256 \
  --auth-host=scram-sha-256 --pwprompt
pg_ctl -D "$HOME/training-portal-fresh/data" \
  -l "$HOME/training-portal-fresh/postgres.log" \
  -o "-h 127.0.0.1 -p 55432 -k $HOME/training-portal-fresh/socket" -w start
psql -X -h 127.0.0.1 -p 55432 -U postgres -d postgres -W
```

上のmkdirは各行の成功を確認してから次へ進んでください。パスに空白がある環境ではpg_ctlの-o内のソケット指定の引用方法を環境に合わせて確認します。initdbの管理者パスワードは非表示入力し、ソースやシェル履歴へ書きません。

psql内で、接続した新クラスタのホスト・ポートを確認します。

```sql
SELECT current_database(), current_user, host(inet_server_addr()), inet_server_port();
CREATE ROLE training_portal_user LOGIN;
\password training_portal_user
CREATE DATABASE training_portal OWNER training_portal_user;
-- DBを作成したら \q で終了する。
```

DBパスワードもpsqlの非表示入力で設定します。アプリロールにSUPERUSER/CREATEDB/CREATEROLE/REPLICATIONを付与しません。DB所有者をtraining_portal_userにすることで、標準publicの所有者pg_database_ownerを維持したままMigrationできます。

public用とtest1用のランナーは**DB全体が空であること**を要求します。同じDBにpublicを構築してからtest1を追加する用途には使えません。テスト用は同じ専用クラスタ内でも別の空DBを用意します。

```sql
-- 新クラスタの管理者がpostgres DBへの接続で実施
CREATE DATABASE training_portal_tests OWNER training_portal_user;
```

別名DBへpsqlで接続し、対象スキーマだけを準備します。

```sh
psql -X -h 127.0.0.1 -p 55432 -U training_portal_user -d training_portal_tests -W
```

```sql
CREATE SCHEMA test1 AUTHORIZATION training_portal_user;
```

public/test1以外のユーザースキーマ、既存テーブル・ビュー・シーケンス・ユーザー型・関数、追加Extensionなどが存在すると拒否します。初期シードとして社員を直接INSERTしないでください。

## 2. リポジトリとJARの準備

リポジトリ直下で実行します。

```sh
./mvnw -DskipTests package
```

Maven Wrapperが依存を取得します。手元に特定の7個のJARが事前配置されている必要はありません。Java21と、Flyway12.4.0を解決する現在のpom.xmlを使用します。ランナーも実行時にFlyway12.4.0とMigrationのSHA-256を確認します。依存更新を行った場合はランナーの検証も見直します。

このpackageではDBテストを省略しています。テスト成功と混同しないでください。

## 3. publicの新規構築

```sh
java -Dloader.main=com.training.portal.setup.FreshDatabaseCommand \
  -cp target/training-portal-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --initialize-empty-database \
  --host=127.0.0.1 --port=55432 \
  --database=training_portal --schema=public \
  --user=training_portal_user \
  --migrations=src/main/resources/db/migration
```

表示された`training_portal/public@127.0.0.1:55432`を再入力し、DBパスワードを非表示入力します。パスワードのコマンド引数・設定ファイル・固定環境変数による指定は提供しません。実端末のないパイプ実行は拒否します。

接続先の省略、remote/localhostなど127.0.0.1以外、5432番、postgres/template DB、不正名、public/test1以外は拒否します。接続後も実サーバーのアドレス・ポート、DB・ロール・schema、PostgreSQL17、所有者・ロール属性を照合します。

| 段階 | target | skipExecutingMigrations | pending | 結果 |
| --- | --- | --- | --- | --- |
| 1 | 2 | false | [1,2] | V1/V2を通常実行 |
| 2 | 3 | true | [3] | FlywayでV3履歴を登録。V3 SQLは実行しない |
| 3 | 4 | false | [4] | V4通常適用、研修内容をNULL許可 |
| 4 | 5 | false | [5] | V5通常適用、auth_version追加 |

各段階で新しいFlyway設定を作り、skip=trueを次段へ引き継ぎません。V3省略直後の列がまだNOT NULLであることも検証します。履歴は既存実績と同じversion/script/type=SQL/checksum/successの形式で、履歴を手動INSERTしません。履歴だけでは省略実行を識別できないため、ランナーのphaseログも保存します。

## 4. test1の新規構築

**別の空DB**を指定します。

```sh
java -Dloader.main=com.training.portal.setup.FreshDatabaseCommand \
  -cp target/training-portal-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --initialize-empty-database \
  --host=127.0.0.1 --port=55432 \
  --database=training_portal_tests --schema=test1 \
  --user=training_portal_user \
  --migrations=src/main/resources/db/migration
```

target=5、skip=false、pending=[1,2,3,4,5]で適用します。V3はtest1に通常実行し、V4は既にNULL許可であることを確認してDDLなしで完了、V5を追加します。対象外publicには業務表を作りません。

## 5. 適用結果の確認

最後の`PASS`と終了コード0を確認します。さらに正しいDBへpsqlで接続し、読み取り専用で確認します。test1なら修飾名をtest1へ変更します。

```sql
BEGIN READ ONLY;
SELECT current_database(), current_user, host(inet_server_addr()), inet_server_port();
SELECT installed_rank,version,script,type,checksum,success
FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT column_name,data_type,is_nullable,column_default
FROM information_schema.columns
WHERE table_schema='public' AND table_name='kenshu_member' AND column_name='auth_version';
SELECT column_name,data_type,character_maximum_length,is_nullable
FROM information_schema.columns
WHERE table_schema='public' AND table_name='schedule_details' AND column_name='schedule_content';
SELECT count(*) FROM public.kenshu_member;
COMMIT;
```

期待値：V1〜V5 success=true、checksum順に1122476101/463837306/-676549352/1630550406/1806942905、auth_version BIGINT NOT NULL DEFAULT0・CHECK>=0、schedule_content VARCHAR(50) NULL許可、社員0件。ランナーはvalidate成功・pendingなし・9テーブルの所有者・7ステータス・空業務データも確認します。

ランナーは二回目を拒否します。**二回目の拒否はmigrationの冪等再実行テストではなく、空DB専用の安全仕様**です。通常アプリ起動時のFlyway validateでは履歴を維持します。

## 6. 初期講師

[初期講師手順](initial-instructor-setup.md)のとおり、同じJARのInitialInstructorCommandを実端末で実行します。

```sh
java -Dloader.main=com.training.portal.setup.InitialInstructorCommand \
  -cp target/training-portal-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --initialize-first-instructor \
  --host=127.0.0.1 --port=55432 \
  --database=training_portal --schema=public
```

初期講師コマンドはDB名training_portalに限定されています。テスト用別名DBに初期講師を作る機能ではありません。パスワードは非表示入力、社員区分2・BCrypt・auth_version=0です。社員が1件でも存在すると拒否します。未認証Web自己登録はありません。

## 7. バックエンド起動・環境変数

Bashでパスワードを非表示入力する例です。zsh等では各シェルのread構文に合わせてください。

```bash
read -r -s -p 'DB password: ' TRAINING_PORTAL_DB_PASSWORD
printf '\n'
export TRAINING_PORTAL_DB_PASSWORD
java -jar target/training-portal-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/training_portal
```

通常application.propertiesのtarget=2は変更しません。V5まで適用済みの履歴でvalidate成功・追加migrationなし・Hibernate validate成功を確認します。DBパスワードを`-D`や起動引数へ渡しません。

| 環境変数 | 用途 |
| --- | --- |
| TRAINING_PORTAL_DB_PASSWORD | 通常バックエンドのDBパスワード |
| TRAINING_PORTAL_FRONTEND_ORIGIN | 既定http://localhost:3000。CORSの単一Origin |
| TRAINING_PORTAL_COOKIE_SECURE | ローカルHTTP既定false。公開HTTPSではtrueが必要 |
| TRAINING_PORTAL_COOKIE_SAME_SITE | 既定lax。別サイト構成はHTTPS/ブラウザ制約を別途確認 |
| NEXT_PUBLIC_API_BASE_URL | フロントの公開API URL。秘密値を入れない |

## 8. Next.js起動

別ターミナルで実行します。

```sh
cd frontend
pnpm install --frozen-lockfile
cp .env.example .env.local
pnpm run dev
```

既存の.env.localがあれば上書きせず内容を確認してください。例は次の公開URLだけです。

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

http://localhost:3000を開きます。API URL・CORS Origin・ブラウザのホストを整合させてください。localhostと127.0.0.1のCookieを混在させません。production確認は次のとおりです。

```sh
pnpm run typecheck
pnpm run lint
pnpm run test
pnpm exec next build --webpack
pnpm run start
```

NEXT_PUBLIC_API_BASE_URLはbuild時に確定します。Google Fonts・SheetJSの外部配布を含む依存取得にはネットワークが必要です。

## 9. ログイン・基本確認

初期講師でログインし、Home/スケジュール一覧/進捗・課題管理/メンバー管理を開きます。空環境では課題・スケジュール0件が正常です。メンバー管理で新人・講師を登録し、最低人数制約を満たすスケジュールを作成して連携を確認します。ログアウト後に保護画面へ直接アクセスするとログインへ戻ることも確認してください。

## 10. よくある停止理由

| 状態 | 対処 |
| --- | --- |
| 5432/remote/接続先不足で拒否 | 専用クラスタの安全な接続先を明示。ガードを削除しない |
| 新規構築ランナーで既存テーブル・履歴を検出 | 既存環境用のツールではない。停止し、空の別DBを準備。既存表を削除して通さない |
| 同じDBのpublic構築後にtest1を作れない | DB全体の空検証のため正常。別の空テストDBを使用 |
| PostgreSQL版・所有者・role属性・schema不一致 | 準備を見直す。別DBへ自動フォールバックしない |
| 依存取得失敗/Flyway版不一致 | ネットワークとpomの解決版を確認。SQLの修正・バージョンガード解除をしない |
| SQLファイル数/SHA/checksum不一致 | 正しいコミットのV1〜V5を揃える。追加ファイルや改変は別途設計・検証 |
| 段階途中で失敗 | 各phaseは別コミットで、途中まで残る場合がある。全体自動ロールバックなし。履歴/ログを調査し、勝手に再実行・repair・cleanしない |
| 初期講師コマンドが拒否 | 実端末、DB名training_portal、V5、社員0件、入力/接続先を確認 |
| 通常起動でauth_version不存在 | Bootstrap未完了。target設定だけ変更して通常起動を繰り返さない |
| ログイン/CSRF/CORSエラー | バックエンドURL、Origin、Cookieホストを確認。CSRFを無効化しない |
| next/font取得失敗 | ネットワーク/証明書/実行環境を切り分け。業務API不具合と混同しない |

## 11. 検証ツール

Python3とインストール済みPostgreSQLのbinを指定します。既存のDB資格情報を継承せず、新しい一時クラスタ・DB・合成アカウントだけを作ります。

```sh
python3 tools/flyway/test-fresh-database.py \
  --execute-isolated-tests --pg-bin /path/to/postgresql17/bin --frontend
```

headless Chromeがあれば`--chrome-bin /path/to/chrome`を追加できます。Chrome DevTools Protocolを使用し、System Events・アクセシビリティ・OS画面収録権限は使いません。ブラウザ確認はオプションで、未指定ならHTML/API試験をブラウザE2E成功とは扱いません。`--smoke-only`はJUnitを省略して新規構築・起動確認だけを行う補助モードで、回帰テスト成功を意味しません。

検証用ソースはリポジトリ内で完結します。フロント検証は先にfrontendの依存をインストールしておく必要があり、既存node_modulesを一時コピーから参照します。クリーンな依存キャッシュまで毎回再作成するテストではありません。証跡・専用データ領域は一時ディレクトリへ残し、専用プロセスを停止します。秘密値・Chromeプロファイル・DBデータ・バックアップは公開しません。

標準の`./mvnw clean test`は既存test1へ接続する設定なので、無確認で実行しないでください。上の検証ツールは新規構築した別名DBのtest1を明示して全バックエンドテストを実行します。既存のポート固定条件付きテストはスキップになる場合があり、結果報告で件数を区別します。

## セキュリティ統合候補の追加前提

Temurin 21.0.13+9、Node.js 24.22.1を検証基準とします。JDKベンダ・版指定だけで追加CA対策済みとは扱わず、[専用JDKの対策と検証](../security/stage1-runtime-upgrade.md)を確認してください。OS全体の実行環境を無条件に変更しません。

既存の独立DB・専用初期構築手順・通常target=2は維持します。初期講師の登録も現行のパスワード検証を共用し、新しい最低文字数を今回強制していません。

[prodのHTTPS設定例と信頼境界](../security/stage2-authentication.md)、[CSP/HSTS・監査ログ設定](../security/stage3-defense-and-audit.md)を確認してください。実ドメイン・証明書・プロキシ・ログ保持は未確定です。NextのAPI接続先とheaders設定はビルド出力・実応答を確認し、起動時の環境変数だけで変更できると仮定しないでください。ローカルHTTP開発と本番のHTTPS必須設定を区別します。
