# 研修メンバー永続化・Service層

## 根拠と実装範囲

確定事項は `docs/design/基本詳細設計書.xlsx` のMMB01項目・入力チェック・CRUD・イベント定義と、`docs/design/テーブル定義書.xlsx` の研修メンバー定義に従う。
社員ID、社員区分、氏名、ふりがなの制約、ハッシュ保存、編集時の空欄パスワード維持、監査項目、更新カウンタを実装した。Controller・ControllerAdviceは実装済み。HTTP契約は[研修メンバーAPI仕様](../api/members.md)を参照。セッション認証・API認可は実装済み。画面接続は次回実装する。

## 呼び出し契約

- `findAll()`：社員ID昇順の `List<MemberResponse>`。
- `findById(syainId)`：1件の `MemberResponse`。
- `create(MemberCreateRequest, operationSyainId)`：登録後の `MemberResponse`。
- `update(syainId, MemberUpdateRequest, operationSyainId)`：flush後の更新カウンタを持つ `MemberResponse`。
- `delete(syainId)`：削除。返却値なし。

参照はreadOnly、登録・更新・削除は書き込みトランザクション。更新は管理状態のEntityを変更してflushする。作成・更新の操作社員IDは呼び出し元から渡し、画面IDはMMB01を設定する。Controllerが認証済みAuthenticationから操作社員IDを取得し、Serviceへ渡す。

RequestはrecordでJakarta Validationを使用する。Serviceでも検証するため、Controller以外からの呼び出しにも適用される。不正入力は入力値を含まないIllegalArgumentExceptionとする。RequestのtoStringは内容を表示しない。Responseは指定の5項目だけで、パスワードと監査項目を含まない。

BCryptの入力制約としてUTF-8で72バイト以内を採用した。日本語・絵文字は文字数とバイト数が異なるため、文字数の上限では検証しない。入力を切り詰めたり、事前ハッシュしたりしない。更新のnull・空文字・空白のみは変更なしとして扱う。最小文字数・複雑性は未確定のまま。
根拠: https://github.com/spring-projects/spring-security/blob/main/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCrypt.java

## HTTP例外変換

| 例外 | HTTP |
| --- | --- |
| IllegalArgumentException（入力チェック） | 400 |
| MemberNotFoundException | 404 |
| DuplicateMemberException | 409 |
| MemberUpdateConflictException | 409 |
| MemberReferencedException | 409 |

事前の重複・更新カウンタ確認に加え、flush時の一意違反（SQLSTATE 23505）とSpringの楽観ロック例外を業務例外へ変換する。削除時の外部キー違反（23503）はMemberReferencedExceptionへ変換し、その他のDataIntegrityViolationExceptionはそのまま送出する。SQLのエラーメッセージで判定しない。ControllerAdviceでは内部例外・SQL情報・ValidationのrejectedValueをレスポンスやログへ出さないこと。

## DBとテスト

Entityのスキーマ固定を外し、通常はHibernate/Flywayのpublic、テストはtest1を明示する。テスト接続URLもcurrentSchema=test1を指定する。DBはtraining_portal、ユーザーはtraining_portal_user、パスワードはTRAINING_PORTAL_DB_PASSWORD。
V1は変更していない。統合テストのデータ操作はテストトランザクションでロールバックする。Flywayのtest1への適用は残る。

`./mvnw clean test` でService単体テスト・PostgreSQL統合テスト・起動テストを実行する。

## 未確定・次回対応

未認証の自己登録は今回許可しない。将来の自己登録仕様、パスワードの業務上の文字数・複雑性、祝日判定は未確定。
操作社員IDヘッダーは廃止済み。自己削除禁止はControllerが認証社員IDと比較してService呼び出し前に実施する。セッション認証・CSRF・CORSの詳細は[認証API仕様](../api/auth.md)を参照。

権限はログイン時のスナップショット。社員区分変更・パスワード変更・他ユーザー削除時の既存セッション即時失効、同時ログイン数、ログイン試行回数制限は未確定であり、追加していない。既存セッションの権限はログアウトまたはタイムアウトまで残る。meの氏名等はDBから再取得する（削除済みなら404）。次回、運用要件に合わせてセッション失効方針を確定する。
本番Origin、HTTPS終端、SameSite設定、初期講師アカウントの払い出し方法は本番構成・運用で確定する。未認証の初期登録エンドポイントや固定パスワードのユーザーは作成していない。

## 認証実装の検証

AuthApiTestは実際のBCrypt、Spring Securityフィルタ、HttpSessionとCSRF取得APIを使ってログイン・me・ログアウト、権限、監査社員ID、自己削除、CORSを確認する。DB操作はtest1でテストトランザクションによりロールバックする。
SessionCookieTestは実HTTPでHttpOnly・SameSite・Secureのローカル設定と1時間タイムアウト、Cookieのみのセッション追跡を確認する。DB更新を行わない。
既存ControllerテストもSecurityFilterChainを有効にし、テスト用ログイン情報とCSRFを付けて既存HTTP契約を検証する。

参考: [Spring Securityのセッション保存](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)、[CSRF保護](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)。
