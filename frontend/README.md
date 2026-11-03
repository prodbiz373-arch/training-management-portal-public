# 研修ポータル フロントエンド

Next.js/React/TypeScriptによる画面です。ログイン・メンバー管理・スケジュール・Progress・HomeはSpring Boot REST APIと連携します。任意パスワードやlocalStorageのモック認証ではログインできません。

リポジトリ直下の[新規セットアップ手順](../docs/setup/fresh-environment.md)で、先にDBのV1〜V5適用・初期講師登録・バックエンド起動を行ってください。

```sh
pnpm install --frozen-lockfile
# 既存の.env.localは上書きせず、.env.exampleを参考に設定
pnpm run dev
```

.env.local例（公開設定のみ）：

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

ブラウザはhttp://localhost:3000。API URL・CORS Origin・Cookieホストを整合させ、URL変更時は再起動してください。productionではNEXT_PUBLIC_API_BASE_URLをbuild前に設定します。

```sh
pnpm run test
pnpm run typecheck
pnpm run lint
pnpm exec next build --webpack
pnpm run start
```

検証版はNode24.20.0/pnpm12.4.1。依存取得・Google Fonts取得にはネットワークが必要です。初期講師でログイン後、新人・講師はメンバー管理から登録します。未ログイン自己登録はありません。パスワード・CSRFトークン・CookieをWeb Storageやログへ保存しないでください。
