# セキュリティ対策とExcel設計書の説明差分（2026-11-03）

対象実装は基準main `668ab0b2e00acf82947a2393b5ac1aa27a0e815e`。本書は実装済み内容を説明する文書補完であり、新しい認証仕様を実装するものではない。**正式なExcel改訂は未実施**。[基本詳細設計書](../design/基本詳細設計書.xlsx)39シートと[独自テーブル定義書](../design/テーブル定義書.xlsx)10シートのバイナリは維持する。変更案は次回Excel改訂時の対象であり、未確定の本番値やM1を承認済み仕様としない。

## 1. ログイン試行制限・429/Retry-After

- 現設計：基本`LGI_イベント内容!D10`は社員ID取得・パスワード照合・成功後Home遷移、`E10`は必須/社員存在/一致、`H10`は一般認証エラー。`共通仕様!D12/E25/E26`等のエラー説明に試行制限の詳細が不足。
- 実装：[LoginAttemptLimiter.java](../../src/main/java/com/training/portal/auth/LoginAttemptLimiter.java):19–85、[AuthController.java](../../src/main/java/com/training/portal/auth/AuthController.java)、[ApiExceptionHandler.java](../../src/main/java/com/training/portal/common/api/ApiExceptionHandler.java):218–221、[application.properties](../../src/main/resources/application.properties):38–45。BCryptの前に同期予約する。
- 現行既定：アカウント8失敗/300秒、送信元200試行/60秒、ブロック60秒、並列32、各map最大10000。送信元予算は成功やアカウント側拒否も含む。アカウント成功時は失敗数/ブロックをリセット。時間経過で解除し、拒否のたびに恒久延長しない。並列枠/キー上限等は1秒の再試行案内となる場合がある。
- 応答：HTTP429、code=`LOGIN_RATE_LIMITED`、`Retry-After`秒数。既知/未知IDで本文形式を分けない。CSRF不正は認証前403で、失敗回数として数えない。送信元はtransport peerで、生の転送ヘッダーを信用しない。
- 不一致/変更案：設計に設定名/窓/解除/429/Retry-After、正常利用者への影響と単一ノード前提を追記する。複数ノードには原子的共有ストアが必要。再起動で予算が失われる点、共有送信元/容量/並列の負荷検証は運用課題。理由は現行API契約を設計側へ反映するため。Excel自体は未改訂。

## 2. パスワード変更時の認証世代

- 現設計：基本`共通仕様!D21`は「区分変更時のみauth_versionを同一取引で+1」。`MMB_画面編集1!D12`、`MMB_イベント内容!D12`、`仕様監査結果!F52`も区分変更を中心に説明。テーブル`研修メンバー!D28`は「社員区分の実変更時に同一取引で+1」。
- 実装：[KenshuMember.java](../../src/main/java/com/training/portal/member/KenshuMember.java):110–118の`changePassword`がハッシュ更新とauthVersion増加を行う。[KenshuMemberService.java](../../src/main/java/com/training/portal/member/KenshuMemberService.java)で更新パスワードが空欄なら変更しない。非空のパスワード更新を行ったときに世代を増加させる。新旧平文が異なることを判定する仕様ではない。
- 不一致/変更案：「区分変更時のみ」を「区分の実変更および非空パスワード更新時」と説明し、本人と他端末の旧世代を拒否することを追記。型/DEFAULT0/CHECK>=0やDBスキーマは変更不要。区分とパスワードを同時更新した場合も各更新の増加を単一の+1へ単純化しない。理由は旧資格情報で作成されたセッションを失効させる実装と整合させるため。Excelは未改訂。

## 3. 社員区分変更と旧セッション拒否

- 現設計：基本`共通仕様!D21/E22`は区分変更時に世代増加、氏名等は増加なし、自己区分変更後は再ログイン。`E8`は無操作1時間と世代不一致401、`E34`は全端末を次回APIで拒否・共通ロックによる競合保護。この既存説明は維持する。
- 実装：[KenshuMember.java](../../src/main/java/com/training/portal/member/KenshuMember.java):100–102、[RoleSessionGuard.java](../../src/main/java/com/training/portal/auth/RoleSessionGuard.java)、[RoleSessionFilter.java](../../src/main/java/com/training/portal/auth/RoleSessionFilter.java):34–65。現在のDB区分/世代を照合し、旧認証は401、サーバーセッション破棄・JSESSIONID削除応答。書込は共通トランザクションロックで保護。氏名だけの更新は世代を増やさない。
- 変更案：既存の区分変更仕様を残し、パスワード更新にも同じ失効機構が及ぶことを追記。[members画面](../../frontend/app/(app)/members/page.tsx)の本人更新後の再ログイン導線を明記。他端末への即時push通知ではなく、次回APIで拒否される点を維持。[AuthController.java](../../src/main/java/com/training/portal/auth/AuthController.java)のログイン成功時のセッションID/CSRF更新も維持。理由は「即座に全ブラウザを強制遷移する」と誤解させないため。Excelは未改訂。

## 4. HTTPS・Secure Cookie・CORS/CSRF

- 現設計：基本`共通仕様!E8/E24/E34`等はセッション/エラー/旧世代拒否中心。本番HTTPS・転送ヘッダー信頼境界の詳細は不足。
- 実装：[application-prod.properties](../../src/main/resources/application-prod.properties):1–7、[ProductionSecurityPolicy.java](../../src/main/java/com/training/portal/config/ProductionSecurityPolicy.java)、[HttpsOnlyFilter.java](../../src/main/java/com/training/portal/config/HttpsOnlyFilter.java)、[SecurityConfig.java](../../src/main/java/com/training/portal/config/SecurityConfig.java)。prodはHTTPS必須、Secure/HttpOnly/SameSite=Lax、明示HTTPS frontend Origin、forward-headers-strategy=none。通常ローカルはHTTP可・Secure既定false。CSRF、限定Originの資格情報付きCORS、URLセッション追跡無効を維持する。
- 変更案：prodとローカル設定を区別して追記。実TLS終端/ドメイン/HTTP遮断/信頼プロキシは未確定と明示する。アプリ設定だけで実経路の安全性を保証しない。信頼境界を決めずに転送ヘッダーを信頼させない。理由は配備条件とローカル開発の両立。Excelは未改訂。

## 5. 画面CSP・ヘッダー・HSTS

- 現設計：基本`共通仕様`に対応する画面CSP・動的描画/キャッシュ・条件付きHSTSの規定が不足（追加項目案）。
- 実装：[frontend/proxy.ts](../../frontend/proxy.ts):4–17、[security-headers.ts](../../frontend/lib/security-headers.ts)、[next.config.mjs](../../frontend/next.config.mjs):3–13、[layout.tsx](../../frontend/app/layout.tsx)。要求固有nonce/strict-dynamic、script/style属性禁止、frame-ancestors/object/base制限、API Origin限定。unsafe-inline/unsafe-evalを追加しない。本番ビルドは強制、開発はReport-Only。HTMLは動的/private/no-store。
- HSTS：既定無効。HTTPS確認フラグと正の秒数を明示した場合に限る。Spring側も同じ運用条件。[SecurityConfig.java](../../src/main/java/com/training/portal/config/SecurityConfig.java):105付近。NextのHSTSヘッダーやNEXT_PUBLIC_API_BASE_URLはビルド値の影響を受けるため、起動時環境変数だけで既存ビルドが変わるとは扱わない。
- 変更案：`共通仕様`に新規項目として実装範囲と未確定の本番条件を追加。理由はCDNキャッシュ/nonce/TLS設定を矛盾させないため。実経路の検証は未完了、Excelは未改訂。

## 6. 監査イベントと運用未確定事項

- 現設計：`共通仕様`に安全な監査イベント/保管/欠落条件の規定が不足（新規項目案）。既存のDB監査列の説明を消さず、セキュリティイベント監査とは区別する。
- 実装：[SecurityAudit.java](../../src/main/java/com/training/portal/audit/SecurityAudit.java):18–67、[AuditOutcomeFilter.java](../../src/main/java/com/training/portal/audit/AuditOutcomeFilter.java)、[AuthController.java](../../src/main/java/com/training/portal/auth/AuthController.java)、[RoleSessionFilter.java](../../src/main/java/com/training/portal/auth/RoleSessionFilter.java)、[SecurityConfig.java](../../src/main/java/com/training/portal/config/SecurityConfig.java)、[logback-spring.xml](../../src/main/resources/logback-spring.xml)。ログイン成功/失敗/制限、ログアウト、旧世代失効、401/403、メンバー作成/更新/削除を固定イベントで記録。
- schema/time/event/status/actor/sourceの固定6フィールド。actor/sourceはHMAC仮名化し、生ID/IP・氏名・本文・URL・例外・パスワード・Cookie・CSRF等を記録しない。仮名は匿名情報とは限らず保護が必要。全操作/自然タイムアウトを網羅した監査ではない。
- 非同期有界キュー（既定1024）、例外/停止/満杯では認証を妨げず欠落し得る。HMAC鍵未設定/不正時は一時鍵で再起動後の相関が保てない。保存先既定stdout、外部設定例は方針確定ではない。Logback内部I/O失敗を破棄数だけでは網羅できず、別の配送監視が必要。
- 変更案：`共通仕様`に上記の実装と限界を追記。本番の安定鍵注入/更新、保持期間、アクセス権、容量/ローテーション、配送・欠落監視、障害復旧は未確定として別途決定する。理由は記録の機密性と可用性の両立。Excelは未改訂。

## 7. M1と検証の扱い

BCrypt/UTF-8 72バイト、更新空欄は変更なし、既存短いパスワードのログイン互換を維持。`LGI_画面編集1!E10`等の現行仕様を勝手に強化しない。最低長・漏えい拒否の推奨案は承認待ち・未実装。初期講師/Frontendの整合と既存利用者移行は承認後に検討する。

基準ソースの過去検証はFrontend230件/Backend251件（条件付き12件は別の独立DB実行で補完）。本書の文書補完で業務試験を新たに実行したものではない。実本番TLS/ログ運用・全画面状態の安全性を保証しない。[第2段階](stage2-authentication.md)、[第3段階](stage3-defense-and-audit.md)の実装記録と区別する。
