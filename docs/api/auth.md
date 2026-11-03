# セッション認証API

React/Next.js向けのJSON API。JWT・フォームログイン・HTTP Basicは使用しない。
根拠は `docs/design/基本詳細設計書.xlsx` のLGI01・共通セッション仕様、テーブル定義書のkenshu_memberと今回の実装指示。

## API一覧

| メソッド・パス | 認証 | CSRF | 成功レスポンス | 画面イベント・DB |
| --- | --- | --- | --- | --- |
| GET /api/auth/csrf | 不要 | 不要 | 200、CsrfResponse | ログイン前・ログイン後のトークン取得。DB操作なし |
| POST /api/auth/login | 不要 | 必須 | 200、AuthResponse | LGI01ログイン。kenshu_memberを社員IDでSELECTしBCrypt照合 |
| GET /api/auth/me | 必須 | 不要 | 200、AuthResponse | ログイン状態・利用者表示。kenshu_member SELECT |
| POST /api/auth/logout | セッション破棄は認証状態に関係なく実行可能 | 必須 | 204、本文なし | ログアウト。DB操作なし |

認証情報取得・利用者情報取得はServiceのreadOnlyトランザクション。認証のためのDB更新や新規テーブル作成は行わない。ログアウトはSpring SecurityのLogoutFilterで処理する。

## 入出力

LoginRequest:

```json
{"syainId":"001","password":"password"}
```

syainIdは必須・空白不可・10文字以内。passwordは必須・空白不可、既存登録処理と同じBCryptの技術的制約（UTF-8で72バイト以内）を適用する。入力を切り詰めたりトリムしたりしない。業務上の最小文字数・複雑性は追加していない。

AuthResponse（ログイン成功・me共通）:

```json
{"syainId":"001","syainKbn":2,"name":"テスト講師","nameKana":"こうし","authorities":["ROLE_INSTRUCTOR"]}
```

syain_kbn=1はROLE_TRAINEE、2はROLE_INSTRUCTOR。権限配列はこの業務ロールのみ。パスワード・ハッシュ・監査項目・セッションIDは含めない。

CsrfResponse:

```json
{"headerName":"X-CSRF-TOKEN","token":"取得したトークン"}
```

このtokenはCSRF対策専用。認証トークンではなく、ログイン状態はセッションCookieで維持する。Cookie・CSRFトークンをログ出力しない。

## Cookie・セッション

- Cookie名はJSESSIONID。HttpOnlyを有効にし、JavaScriptからは読まない。
- サーバーセッションにSecurityContextを保存する。認証後は認証用パスワード/ハッシュを消去したprincipalを保存する。
- ログイン成功時はセッションIDを変更し、セッション固定攻撃を防ぐ。
- 無操作1時間でセッション失効。サーバー再起動でも失効する（インメモリセッション）。
- URLへのセッションID埋め込みを無効にし、Cookieのみで追跡する。
- ログアウトはセッション・SecurityContext・CSRF情報を破棄し、JSESSIONIDの削除Cookieを返す。
- ローカルHTTPではSecure=false、SameSite=Lax。本番HTTPSではTRAINING_PORTAL_COOKIE_SECURE=trueを設定する。
- TRAINING_PORTAL_COOKIE_SAME_SITEでSameSiteを切り替え可能。別サイト構成でNoneを選ぶ場合はSecure=trueとHTTPSが必要。第三者Cookieのブラウザ制約も考慮し、本番ドメイン構成に合わせて確定する。
- 認証・CSRF応答はSpring SecurityのCache-Control: no-store等でキャッシュを抑止する。

ログイン時の社員区分・権限と`auth_version`をセッション認証情報へ保持する。社員区分変更が確定すると認証世代が増加し、各端末の旧認証は次回の認証済みAPIアクセス時にDB照合で拒否される。社員が削除された場合も、次回の照合で社員不存在として拒否される。応答は401 `AUTHENTICATION_REQUIRED`となり、そのリクエストの端末のセッションを破棄して再ログインを要求する。

複数ブラウザ・複数端末の旧認証にも同じ照合を適用する。他端末への即時通知や、変更時点で全端末のセッションを一括して物理削除する機構はない。アクセスしていない端末の画面を即座にログイン画面へ切り替えるものではない。詳細は「社員区分変更と認証世代（DIFF-31）」を参照。

## CSRF手順

1. ログイン前にGET /api/auth/csrfをcredentials付きで呼ぶ。未認証でもCSRF保存用セッションが作成される。
2. POST /api/auth/loginに返却されたheaderName/tokenをヘッダーとして付ける。
3. ログイン成功後に再度GET /api/auth/csrfを呼ぶ。ログイン前のトークンは無効になる。
4. POST・PUT・DELETE・ログアウトで同じセッションのトークンを送る。
5. ログアウト後の再ログインでもトークンを取り直す。

トークンはサーバーセッションに保存し、応答ではSpring SecurityのXORマスク方式を使用する。CSRFは全面無効化せず、ログインとログアウトにも適用する。GET/HEAD/OPTIONS等の安全なメソッドは対象外。

CSRF不正時は403 ACCESS_DENIED。ただし未認証の保護APIへのアクセスは401 AUTHENTICATION_REQUIREDを優先する。ログインのCSRF不正は未認証でも403。認証失敗の401テスト・呼び出しにも正しいCSRFトークンが必要。

## CORS

開発用Originはhttp://localhost:3000のみ。credentials=true、ワイルドカードなし。
TRAINING_PORTAL_FRONTEND_ORIGINで本番の単一Originへ変更できる。

許可メソッド: GET, POST, PUT, DELETE, OPTIONS。
許可リクエストヘッダー: Content-Type, X-CSRF-TOKEN。
公開レスポンスヘッダー: Location。
Cookieはブラウザがcredentials指定に従って付ける。JavaScriptでCookieやAuthorizationを設定しない。
廃止済みX-Operation-Syain-IdはCORS許可対象に含めない。
許可されないOrigin・プリフライトは403の共通JSON形式。ブラウザにはCORSエラーとして見える場合がある。

## エラー

既存形式 `{"code":"...","message":"固定文言","errors":[]}` を使用する。入力制約違反のみerrorsに項目名と安全な固定文言を含める。

| HTTP | code | 条件 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 必須・長さ・JSON・型不正 |
| 401 | LOGIN_FAILED | 社員ID不存在・パスワード不一致などの認証失敗。応答を区別しない |
| 401 | AUTHENTICATION_REQUIRED | 未認証・セッション失効で保護APIへアクセス |
| 403 | ACCESS_DENIED | 権限不足・CSRF違反・CORS拒否 |
| 500 | INTERNAL_ERROR | 想定外エラー |

LOGIN_FAILEDのメッセージは「社員IDまたはパスワードが正しくありません」。Spring SecurityのDaoAuthenticationProviderで不存在を隠蔽し、BCryptのダミー照合も利用する。認証が失敗したリクエストは新しい認証状態を保存しない。
レスポンス本文・アプリログにパスワード、ハッシュ、Cookie値、セッションID、Authorization、SQL、内部例外、スタックトレース、rejectedValueを出さない。Cookie発行/削除に必要なSet-Cookieヘッダーは除く。

## TypeScript fetch例

```typescript
const base = "http://localhost:8080";
type Csrf = { headerName: string; token: string };

async function getCsrf(): Promise<Csrf> {
  const res = await fetch(`${base}/api/auth/csrf`, {
    credentials: "include",
  });
  if (!res.ok) throw new Error("CSRFトークンを取得できませんでした");
  return res.json();
}

async function login(syainId: string, password: string) {
  const csrf = await getCsrf();
  const res = await fetch(`${base}/api/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify({ syainId, password }),
  });
  if (!res.ok) throw new Error("ログインできませんでした");
  return { user: await res.json(), csrf: await getCsrf() };
}

async function me() {
  const res = await fetch(`${base}/api/auth/me`, { credentials: "include" });
  if (res.status === 401) return null;
  if (!res.ok) throw new Error("利用者情報を取得できませんでした");
  return res.json();
}

async function logout(csrf: Csrf) {
  const res = await fetch(`${base}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!res.ok) throw new Error("ログアウトできませんでした");
  // 204にはJSON本文がない。保持していた利用者情報・CSRFトークンを破棄する。
}
```

トークン・パスワードをconsole等に出さない。401ではログイン画面へ戻し、403では権限・CSRFを確認する。CSRF再取得後の更新リクエストの自動再送は行わず、画面の状態を確認する。

## 社員区分変更と認証世代（DIFF-31）

ログイン時にDBの`auth_version`と社員区分をセッション認証情報へ保持する。認証済みAPIは毎回DBの現在値と照合し、不一致・社員不存在・旧形式の認証情報を401 `AUTHENTICATION_REQUIRED`で拒否し、その端末のセッションを破棄する。区分を元へ戻しても世代は戻さない。全端末の旧認証は次回リクエストで拒否され、再ログインが必要になる。DB照合失敗は500 `INTERNAL_ERROR`で業務処理を実行しない。

更新APIはPostgreSQLのトランザクションアドバイザリロック取得後に世代を再確認し、認可・Service更新・コミットまで同じトランザクションで処理する。同じDBを利用する全アプリのAPI更新を直列化する。ロック待機上限5秒、トランザクション上限30秒。ロック取得・コミット失敗は成功レスポンスにしない。既存のCSRF・セッション固定対策は維持する。

他端末の画面を即座に遷移させる通知機構は設けない。既に認可済みの読み取りレスポンスは取り消さない。複数サーバーでもDB世代照合・更新ロックは有効だが、HTTPセッションの共有は追加していないため従来どおり同一サーバーへのルーティング等が必要。新コードへの切替時は旧形式セッションの再ログインが必要。

## セキュリティ第2段階

ログイン制限中は429 `LOGIN_RATE_LIMITED`、固定メッセージと`errors: []`、`Retry-After`（秒）を返す。既存の401/403、CSRF・セッションID再発行は維持する。送信元総試行とアカウント失敗の両方を制限する。設定と複数サーバー構成の制限は[認証・HTTPS対策](../security/stage2-authentication.md)を参照。

prodではHTTPを403 `HTTPS_REQUIRED`で拒否し、未確定のプロキシヘッダーを信頼しない。証明書や外部HTTP遮断が設定済みという意味ではない。

## 第3段階の監査と画面ヘッダー

認証のHTTPステータス、CSRF・セッション・試行制限の契約は維持する。ログイン成功/失敗/429、ログアウト、失効・401・403を秘密情報のない固定形式で監査する。監査出力の失敗によって認証結果を変更しない。Next画面のnonce CSPと運用条件、未承認のパスワード方針案は[第3段階の説明](../security/stage3-defense-and-audit.md)を参照。新しい最低文字数は今回導入していない。
