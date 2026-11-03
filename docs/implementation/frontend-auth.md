# フロントエンドのセッション認証接続

## 対象と構成

既存Next.jsは `frontend/`。現在は認証に加え、メンバー・スケジュール・Progress・HomeもREST APIに接続済み。新規環境は[セットアップ手順](../setup/fresh-environment.md)を先に実施する。
設計書LGI01と[認証API仕様](../api/auth.md)、[メンバーAPI仕様](../api/members.md)に準拠する。

- `lib/auth-api.ts`: 型、APIベースURL、credentials付きfetch、CSRF取得・送信、401通知、403/500/通信エラーの共通処理。
- `components/auth/auth-provider.tsx`: user・loading・CSRFをメモリだけで管理。ページ遷移時とタブ復帰時にmeを確認。未ログインと通信障害を区別する。
- `components/auth/require-auth.tsx`: app/(app)共通ガード。確認中は内容を描画せず、401はログイン画面へ、通信障害は再試行表示。
- `app/page.tsx`: 既存デザインを維持し、二重送信防止・安全なエラー表示・パスワード消去を追加。
- `components/app-shell.tsx`: API由来の利用者名・区分・社員IDとログアウトを表示。講師のみメンバー管理リンクを表示。
- 残存するモックファイルは新規構築の認証・業務データの初期化には使用しない。画面の実データは各REST APIから取得する。
- `app/register/layout.tsx`: 未認証自己登録を許可しないためログインへリダイレクト。既存フォームファイルは削除していない。

## 画面遷移

1. `/`初期表示でGET /api/auth/me。認証済みなら/home、401ならログインフォーム。
2. ログイン時にGET csrf → POST login（credentialsとX-CSRF-TOKEN）→ GET csrf（更新後）→ /home。
3. /home等のapp/(app)ではContextで認証確認。401ならuser/CSRFを破棄して/へ戻る。500/通信障害は内容を隠して安全な再試行表示。
4. ログアウト確認後にPOST logout。現在のCSRFを送り、再読み込み等で未取得なら先にGET csrf。204のJSONは読まない。
5. ログアウト成功または401でuser/CSRFを破棄し、保護画面の内容を消して/へ戻る。403/500/通信エラーで成功扱いにしない。

CSRF不正等でログアウトが失敗した場合はトークンを破棄し、次の明示操作で再取得する。POSTの自動再送はしない。
クライアントの保護は表示上の補助。バックエンドのROLE_INSTRUCTOR認可と401/403が正しい判定。メンバーCRUDはAPI接続済み。フロントの講師ガードに加えてバックエンドでも認可し、共通401通知・403例外処理を利用する。

## ローカル起動

前提: Java 21、Node.js 24、pnpm 12、PostgreSQL 17。リポジトリで採用済みのバージョンを使用する。

### PostgreSQL・Spring Boot

1. インストール済みのPostgreSQL 17を起動する。起動方法はローカルで使用しているサービス管理方式に従う。
2. `pg_isready -h localhost -p 5432` で接続受付を確認する。
3. この節はV5まで構築済みの環境用。新規環境は[空DBセットアップ](../setup/fresh-environment.md)で別の専用クラスタに構築する。通常はpublic。テストは既存test1を流用せず、独立したDBのtest1を明示する。
4. リポジトリ直下でDBパスワードを表示せず入力し、バックエンドを起動する。

```sh
# Bashの例（zsh等では各シェルの構文を確認）
read -r -s -p 'DB password: ' TRAINING_PORTAL_DB_PASSWORD
export TRAINING_PORTAL_DB_PASSWORD
./mvnw spring-boot:run
```

`read`の実行後にローカルのDBパスワードを入力してEnter。秘密情報をコマンド履歴・ソース・スクリーンショットへ残さない。
デフォルトのポートは8080。CORSの開発Originはhttp://localhost:3000。
ローカルHTTPではCookie Secure=false、SameSite=Lax。localhostと127.0.0.1を混在させない。

### Next.js

別ターミナルで以下を実行する。

```sh
cd frontend
pnpm install --frozen-lockfile
cp .env.example .env.local
```

`.env.local`の空欄をローカル開発では次のように設定する（秘密情報は不要）。

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

```sh
pnpm run dev
```

ブラウザでhttp://localhost:3000を開く。環境変数を変更したらNext.jsを再起動する。
`NEXT_PUBLIC_`変数は公開設定であり、パスワード等を含めない。本番ではbuild時に設定する。
`.env.local`等は.gitignore対象。実値を空にした`.env.example`だけを追跡する。

production build確認:

```sh
pnpm run typecheck
pnpm run lint
pnpm run test
pnpm exec next build --webpack
pnpm run start
```

Next.jsの既存Google Fonts取得にはビルド時のネットワーク接続が必要。TypeScriptエラーを無視する旧設定は撤去している。

## 講師・新人の手動確認

既存DBに登録された講師・新人アカウントを使用する。モックの社員ID・任意パスワードではログインできない。未認証登録APIや固定初期パスワードは提供しない。新規環境の最初の講師は[初期講師コマンド](../setup/initial-instructor-setup.md)で準備する。

- 講師: ログイン→/home。DBの氏名・社員IDを表示し、メンバー管理リンクを表示。
- 新人: ログイン→/home。メンバー管理リンクなし。/membersの直接入力で内容を表示しない。
- 未認証で/homeへ直接移動→ログイン画面。認証済みで/を開く→/home。
- 誤ったパスワードは固定文言。送信中の連打でも二重ログインしない。
- バックエンド停止時は安全な通信エラーを表示。復旧後に再試行できる。
- ログアウト確認→ログイン画面。戻る操作・再読み込みで保護内容を再表示しない。
- 別タブでログアウト後、元タブへ戻るとmeを再確認してログイン画面へ移動。
- 1時間のサーバーセッション失効後、APIアクセスまたはタブ復帰で401を扱えること。
- ブラウザのCookie制約・CORSによる実セッション保持、既存デザインのPC/モバイル表示は実ブラウザで確認する。

パスワード、CSRFトークン、Cookie、セッションIDをconsoleやWeb Storageへ保存しない。検証時も秘密値のスクリーンショット・ログを共有しない。

## テストと現在の接続範囲

Vitest/jsdomのAPIスタブ試験と、実ブラウザ・実API・実DBの検証は区別する。業務画面はAPI接続済みであり、残存モックファイルを初期講師・社員・課題データの正として使用しない。

社員区分変更ではauth_versionにより各端末の旧認証を次回APIアクセス時に401で拒否し、再ログインを要求する。他端末への即時通知・全セッションの一括物理削除は行わない。未ログイン自己登録は禁止。業務上未定義のパスワード最小長・複雑性は追加していない。
