# 研修メンバーCRUD API

基本パス: `/api/members`。入出力はJSON（UTF-8）。根拠は基本詳細設計書のMMB01とテーブル定義書のkenshu_member。既存Serviceのトランザクション・業務ルールを利用する。

## 認証と操作社員ID

Spring Securityのセッション認証を使用する。研修メンバー管理は講師向け機能のため、GET一覧・1件取得を含むすべてのCRUD APIをROLE_INSTRUCTOR限定とする。未認証は401 AUTHENTICATION_REQUIRED、新人（ROLE_TRAINEE）はGETを含めて403 ACCESS_DENIED。

X-Operation-Syain-Idヘッダーは廃止した。登録・更新の操作社員IDはControllerのAuthentication.getName()からServiceへ渡す。クライアントのヘッダー値は無視し、監査項目には使用しない。登録時はcreate_user_cdとupdate_user_cd、更新時はupdate_user_cdにログイン社員IDを保存する。

ログイン中の本人をDELETEした場合は、Controllerが認証済み社員IDとパス社員IDを比較してService呼び出し前に拒否する（409 MEMBER_SELF_DELETE_NOT_ALLOWED）。他の社員の削除は従来のService・外部キー制約に従う。

未認証の自己登録は提供しない。POST・PUT・DELETEにはX-CSRF-TOKENが必要。Cookie・CSRF・CORS・認証手順は[認証API仕様](auth.md)を参照。

## エンドポイント

| メソッド・パス | 画面イベント | Request | 成功 | Response | kenshu_member処理 |
| --- | --- | --- | --- | --- | --- |
| GET /api/members | 初期表示・登録更新削除後の一覧再取得 | なし | 200 | MemberResponse配列（社員ID昇順、0件は[]） | SELECT全件 |
| GET /api/members/{syainId} | 一覧行選択 | パス社員ID | 200 | MemberResponse | SELECT1件 |
| POST /api/members | 新規登録 | MemberCreateRequest、CSRFヘッダー | 201 | MemberResponse、Locationヘッダー | INSERT |
| PUT /api/members/{syainId} | 更新 | MemberUpdateRequest、CSRFヘッダー | 200 | MemberResponse | SELECT/UPDATE |
| DELETE /api/members/{syainId} | 削除確認後 | パス社員ID、CSRFヘッダー | 204 | 本文なし | SELECT/DELETE |

パス社員IDは必須・空白不可・10文字以内。PUTの社員ID変更は不可。POSTのLocationは `/api/members/{登録社員ID}`（URIエンコード済みの相対パス）。

参照はServiceのreadOnlyトランザクション、登録・更新・削除は各Service呼び出しを1トランザクションとして扱う。失敗時はロールバックする。ControllerからRepositoryを直接呼ばない。参照テーブルがある削除は既存外部キーにより拒否する。

## DTO

| 項目 | 新規登録 | 更新 | 制約 |
| --- | --- | --- | --- |
| syainId | 必須 | 本文に含めない | 空白不可・10文字以内 |
| syainKbn | 必須 | 必須 | 数値1（新人）または2（講師） |
| name | 必須 | 必須 | 空白不可・50文字以内 |
| nameKana | 任意 | 任意 | 100文字以内。省略/nullでNULL |
| password | 必須 | 任意 | 新規は空白不可。更新は省略/null/空欄/空白のみで変更なし。入力時UTF-8で72バイト以内 |
| updateCount | 含めない | 必須 | 0以上の整数。DBと不一致なら409 |

PUTでは氏名・社員区分を必須とし、ふりがな省略はクリアする。パスワードを入力した場合のみBCryptで再ハッシュ化する。最小文字数・複雑性の追加規則は未確定。

MemberResponseは以下の5項目のみ。

```json
{"syainId":"001","syainKbn":1,"name":"テスト社員","nameKana":null,"updateCount":0}
```

パスワード（ハッシュを含む）、screenId、監査項目は返さない。更新レスポンスはflush後のupdateCount。

## エラーレスポンス

```json
{
  "code": "INVALID_INPUT",
  "message": "入力内容を確認してください",
  "errors": [{"field":"name","message":"必須項目です"}]
}
```

errorsは入力項目名と固定メッセージだけ。JSON不正、本文欠落、ヘッダー/パス不正、Service入力エラー、業務例外では空配列となる。入力値・ValidationのrejectedValue・内部例外名/メッセージ・SQL・スタックトレースは含めない。ログにもリクエスト本文や例外そのものを記録しない。

| HTTP | code | 条件 |
| --- | --- | --- |
| 400 | INVALID_INPUT | DTO制約違反、JSON不正・型不正、本文欠落、パス不正、Service入力検証 |
| 401 | AUTHENTICATION_REQUIRED | 未認証・セッション失効 |
| 403 | ACCESS_DENIED | 講師権限不足・CSRF違反 |
| 409 | MEMBER_SELF_DELETE_NOT_ALLOWED | ログイン中の本人の削除 |
| 404 | MEMBER_NOT_FOUND | MemberNotFoundException |
| 409 | MEMBER_DUPLICATE | DuplicateMemberException（同時登録を含む） |
| 409 | MEMBER_UPDATE_CONFLICT | MemberUpdateConflictException |
| 409 | MEMBER_REFERENCED | MemberReferencedException |
| 500 | INTERNAL_ERROR | 想定外エラー。内部情報は固定文言に置換 |
| 405 / 415等 | HTTP_ERROR | 未対応メソッド/Content-Type等。元のHTTPステータスを維持 |

未認証かつCSRF不正の保護APIは401、認証済みのCSRF不正は403となる。その他のDB整合性エラーは一律に業務409とせず、500として安全な文言を返す。

## 検証

ControllerテストはMockMvcとモックServiceでHTTP契約・入力検証・エラー変換・秘密値の非表示を検証する。DBへ接続しない。既存の永続化統合テストはtraining_portalのtest1スキーマで実行し、publicへ書き込まない。

実行: `./mvnw clean test`。

## TypeScript fetch例

ログイン後、GET /api/auth/csrfで取得したtokenを使用する。

```typescript
async function createMember(member: {
  syainId: string; syainKbn: 1 | 2; name: string;
  nameKana?: string; password: string;
}, csrf: { headerName: string; token: string }) {
  const res = await fetch("http://localhost:8080/api/members", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify(member),
  });
  if (!res.ok) throw new Error("登録できませんでした");
  return res.json();
}

async function deleteMember(syainId: string, csrf: { headerName: string; token: string }) {
  const res = await fetch(`http://localhost:8080/api/members/${encodeURIComponent(syainId)}`, {
    method: "DELETE",
    credentials: "include",
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!res.ok) throw new Error("削除できませんでした");
  // 204は本文なし。
}
```

PUTも同じcredentials/CSRF指定で、MemberUpdateRequestと最新のupdateCountを送る。GETはcredentialsのみでよい。

## 社員区分変更（DIFF-31）

PUTで社員区分が実際に変わる場合だけ、`kenshu_member.auth_version`を1増加する。氏名等のみの更新では増加しない。区分変更、世代増加、関連する全`schedule_can_edit.syain_kbn`の同期は同一トランザクションで行う。権限行は更新カウンタ・更新日時・更新者・画面IDも更新する。`can_edit`・`pin_flg`・提出状況・コメントは変更・削除しない。新人が保持する編集権限も維持する。

次の変更は409 `MEMBER_ROLE_CHANGE_NOT_ALLOWED`で全体を拒否する。

- 最後の編集可能講師を新人へ変更する。
- 最後の新人を講師へ変更する。
- 日別担当講師として参照中の社員を新人へ変更する。
- 対象新人の提出状況に未提出（区分1）以外がある、または対象新人の課題にコメントが存在する状態で講師へ変更する。未提出レコードだけなら、この履歴条件では禁止しない。

エラーメッセージは「スケジュールの人数条件、担当講師の参照、または提出履歴があるため、社員区分を変更できません」。既存の楽観ロック競合も維持する。自分自身の区分変更は保存成功200を返し、フロントは認証情報を再取得する。次の401でログイン画面へ移動し、保存失敗として表示しない。他社員の変更時は通常どおり一覧を更新する。リクエスト・正常レスポンス形式は変更しない。

## パスワード変更後の再認証（第2段階）

パスワードを変更した利用者の`auth_version`を更新し、他ブラウザを含む変更前のセッションは次回APIで401となる。氏名のみ・空欄によるパスワード変更なしでは失効しない。自分のパスワード変更は更新成功後に認証を再確認してログインへ戻る。更新競合は既存の409を維持する。[認証・HTTPS対策](../security/stage2-authentication.md)を参照。
