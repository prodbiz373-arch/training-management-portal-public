# スケジュール一覧 API

基本パスは`/api/schedules`。ログイン利用者が`schedule_can_edit`を持つスケジュールの一覧・詳細を取得し、本人用のピン留めを変更できる。新人・講師のどちらも新規登録でき、社員区分にかかわらず本人の`can_edit=1`を条件として登録済みスケジュールを更新・削除できる。

## GET /api/schedules

ログイン済みの新人・講師が利用できる。Controllerは認証Principalの社員IDを利用し、`schedule_can_edit.syain_id`が一致するスケジュールだけを返す。クライアントが社員IDを指定することはできない。

並び順は、本人の`pin_flg=1`、当日が`start_date`から`end_date`の期間内、`schedule_id`昇順である。

成功時は`200`と配列を返す。`schedule_id`はテーブル定義どおり文字列である。

```json
[
  {
    "scheduleId": "S001",
    "scheduleName": "新人研修",
    "startDate": "2026-04-01",
    "endDate": "2026-04-30",
    "canEdit": true,
    "pinned": true,
    "scheduleUpdateCount": 3,
    "permissionUpdateCount": 1
  }
]
```

権限行がない利用者には空配列を返す。未認証は`401 AUTHENTICATION_REQUIRED`。本APIは読み取り専用のためCSRFトークンは不要である。

## GET /api/schedules/member-options

ログイン済みの新人・講師が、スケジュール新規登録・編集の権限設定に使用する社員候補を取得する。メンバー管理APIの利用権限は拡張せず、パスワードや更新カウンタを含まない次の項目だけを返す。

```json
[
  {
    "syainId": "trainee001",
    "syainKbn": 1,
    "name": "初期新人",
    "nameKana": null
  }
]
```

成功時は`200`、未認証は`401 AUTHENTICATION_REQUIRED`を返す。読み取り専用のためCSRFトークンは不要である。

## GET /api/schedules/non-working-dates

認証済み利用者が、`startDate`から`endDate`までの新規登録時の除外対象日を取得する。除外対象は土曜日、日曜日、日本の「国民の祝日に関する法律」に基づく休日（振替休日・国民の休日を含む）である。

`GET /api/schedules/non-working-dates?startDate=2026-10-05&endDate=2026-10-16`

```json
{
  "dates": ["2026-10-10", "2026-10-11", "2026-10-12"]
}
```

祝日判定はバックエンドを正とし、新規登録画面も本APIの結果を使用する。現在の対応年は2000年から2150年で、範囲外または開始日が終了日より後の場合は`400 INVALID_INPUT`を返す。

## GET /api/schedules/{scheduleId}

ログイン済みの新人・講師が利用できる。認可は認証Principalの社員IDと`schedule_can_edit.syain_id`の一致で判定する。`canEdit=false`の利用者も詳細を取得でき、画面側では参照モードとして表示する。

認可済みの利用者に対して、次を返す。

- `schedule`: 基本情報（ID、名称、期間、休日除外フラグ、更新カウンタ）
- 本人の`canEdit`、`pinned`、権限行の更新カウンタ
- 当該スケジュールの編集権限一覧
- `schedule_bunrui`の分類一覧
- `schedule_details`の日別内容（分類名、担当講師名、課題フラグを含む）

```json
{
  "scheduleId": "S001",
  "scheduleName": "新人研修",
  "startDate": "2026-04-01",
  "endDate": "2026-04-30",
  "excludeHolidays": true,
  "canEdit": false,
  "pinned": true,
  "scheduleUpdateCount": 3,
  "permissionUpdateCount": 1,
  "permissions": [
    {
      "employeeId": "trainee001",
      "name": "初期新人",
      "nameKana": null,
      "syainKbn": 1,
      "canEdit": false,
      "pinned": true,
      "updateCount": 1,
      "usedAsReviewer": false,
      "hasComments": true
    }
  ],
  "categories": [
    { "categoryNo": 1, "categoryName": "Java研修", "updateCount": 0 }
  ],
  "details": [
    {
      "scheduleDateNo": 1,
      "scheduleDate": "2026-04-01",
      "categoryNo": 1,
      "categoryName": "Java研修",
      "instructorEmployeeId": "admin001",
      "instructorName": "初期講師",
      "content": "オリエンテーション",
      "taskFlg": true,
      "updateCount": 0
    }
  ]
}
```

`permissions[].usedAsReviewer`は、対象スケジュールの`issue_submission_statuses.koshi_syain_id`に社員IDが存在する場合に`true`となる。`permissions[].hasComments`は、対象スケジュールの`issue_submission_statuses_comment.comment_syain_id`に社員IDが存在する場合に`true`となる。どちらも社員区分を問わず判定し、編集画面で権限メンバーの削除・権限変更を事前制御するために使用する。

存在しない`scheduleId`は`404 SCHEDULE_NOT_FOUND`、スケジュールは存在するが本人の権限行がない場合は`403 ACCESS_DENIED`を返す。未認証は`401 AUTHENTICATION_REQUIRED`である。いずれも詳細データは返さない。

## POST /api/schedules

ログイン済みの新人・講師が利用できる。セッション認証とCSRFトークンが必要で、`schedule`、`schedule_can_edit`、`schedule_bunrui`、`schedule_details`を1トランザクションで登録する。正常時は`201 Created`、`Location: /api/schedules/{scheduleId}`と登録後の詳細レスポンスを返す。

```json
{
  "scheduleId": "TR2026A",
  "scheduleName": "2026年度新人研修",
  "startDate": "2026-04-01",
  "endDate": "2026-04-30",
  "excludeHolidays": true,
  "permissions": [
    { "employeeId": "admin001", "canEdit": true },
    { "employeeId": "trainee001", "canEdit": false }
  ],
  "categories": [
    { "categoryNo": 1, "categoryName": "Java研修" }
  ],
  "details": [
    {
      "scheduleDateNo": 1,
      "scheduleDate": "2026-04-01",
      "categoryNo": 1,
      "instructorEmployeeId": "admin001",
      "content": "オリエンテーション",
      "taskFlg": false
    }
  ]
}
```

社員区分はクライアントから受け取らず、`kenshu_member`を正として検証する。登録者本人が権限一覧に未指定の場合は、社員区分にかかわらず`can_edit=1`で自動追加する。登録者本人を明示的に`canEdit=false`にする指定は拒否する。画面の初期状態では従来どおり全講師を`canEdit=true`で設定し、新人が登録者の場合は本人も`canEdit=true`で設定する。登録後の権限一覧には新人を1人以上、かつ`can_edit=1`の講師を1人以上含める。分類番号・社員ID・日付番号は重複不可であり、分類・担当講師・期間の参照整合も検証する。`taskFlg=true`でも本APIは`issue_submission_statuses`を生成しない。

`excludeHolidays=true`の場合、`schedule_details.schedule_date`に土曜日、日曜日、または日本の祝日法に基づく休日（振替休日・国民の休日を含む）を指定すると`400 INVALID_INPUT`で拒否する。`false`の場合はこれらの日付も登録できる。

スケジュールID重複は`409 SCHEDULE_DUPLICATE`、入力・参照整合違反は`400 INVALID_INPUT`、未認証は`401 AUTHENTICATION_REQUIRED`、CSRFトークン不備は`403`とする。いずれかの登録に失敗した場合は全テーブルをロールバックする。

## PUT /api/schedules/{scheduleId}

認証Principal本人の`schedule_can_edit.can_edit=1`が必要である。社員区分は認可条件に使用しないため、新人も`can_edit=1`なら更新でき、講師も`can_edit=0`なら更新できない。スケジュール名と次の3領域を1トランザクションで更新する。

`scheduleName`は必須・空白のみ不可・40文字以内。名称変更にも既存の`scheduleUpdateCount`による楽観ロックを使用する。名称未指定・NULL・空文字・空白のみ・41文字以上は`400 INVALID_INPUT`。旧リクエスト形式とは互換性がないため、名称を変更しないクライアントも取得済みの名称を送る必要がある。リポジトリ内の画面とテストは対応済み。外部クライアントがあれば同時更新が必要。

- `schedule_can_edit`: 権限追加、編集／参照のみ変更、削除
- `schedule_bunrui`: 分類追加、名称変更、削除
- `schedule_details`: 既存日付行の分類、担当講師、内容、課題フラグ変更

リクエスト例:

```json
{
  "scheduleName": "更新後の新人研修",
  "scheduleUpdateCount": 3,
  "permissions": [
    { "employeeId": "admin001", "canEdit": true, "updateCount": 1 },
    { "employeeId": "trainee001", "canEdit": false, "updateCount": 0 }
  ],
  "deletedPermissions": [
    { "employeeId": "trainee002", "updateCount": 2 }
  ],
  "categories": [
    { "categoryNo": 1, "categoryName": "Java基礎", "updateCount": 1 },
    { "categoryNo": 3, "categoryName": "演習", "updateCount": 0 }
  ],
  "deletedCategories": [
    { "categoryNo": 2, "updateCount": 1 }
  ],
  "details": [
    {
      "scheduleDateNo": 1,
      "categoryNo": 3,
      "instructorEmployeeId": "admin001",
      "content": "演習1",
      "taskFlg": true,
      "updateCount": 2
    }
  ]
}
```

正常時は`200`で最新の詳細レスポンスを返す。`schedule_id`、期間、休日設定、日付行そのものは更新対象外である。`schedule.update_count`は名称変更を含む集約全体の更新として増分する。名称は既存のschedule行で更新し、関連データの削除・再作成や画面別の名称複製は行わない。一覧・詳細・Home・Progressは次の取得で更新後の名称を返す。

既存行と削除行は、詳細取得時の`updateCount`を必須とする。`schedule`、権限、分類、日別内容のいずれかが先に更新されていた場合は`409 SCHEDULE_UPDATE_CONFLICT`を返す。

分類名は、新規登録・更新とも同一スケジュール内で前後空白を除去した後に重複できない。大文字小文字は区別し、Unicode正規化は行わない。別スケジュールの同名分類は許可する。更新は追加・名称変更・削除を反映した最終的な分類集合で検証し、重複時は`400 INVALID_INPUT`で更新全体を拒否する。

更新者本人の権限行は削除できず、`canEdit=false`にも変更できない。社員区分はクライアント入力ではなく`kenshu_member`から取得し、更新後の権限一覧には新人（`syain_kbn=1`）を1人以上、かつ`can_edit=1`の講師（`syain_kbn=2`）を1人以上必要とする。対象スケジュールでレビュー者またはコメント投稿者として使用されている社員の権限行は削除できず、レビュー者の`can_edit=1`から`0`への変更もできない。コメント投稿者であるだけの場合は`can_edit`の変更を妨げない。担当講師は講師区分かつ更新後の権限一覧に含まれる社員に限る。分類削除は、同一PUT内の変更を反映した最終的な日別内容から参照されない場合だけ許可する。

課題をONからOFFにする場合、同じスケジュール・日付番号の全受講者について、未提出（既存マスタの`task_status_kbn=1`）以外の提出状況、またはコメントが1件でもあれば`400 INVALID_INPUT`で拒否する。メッセージは「未提出以外の提出状況、またはコメントが登録されているため、課題を解除できません。」。全員未提出（レコードなしを含む）でコメントもなければ解除可能。提出状況・コメントは削除しない。NULLフラグ・行省略・内容空欄化も禁止条件を検証する。NULLフラグと行省略は通常の入力制約でも拒否する。`taskFlg=false`から`true`への変更時も提出状況を自動生成しない。

日別内容レスポンスの`taskRemovalBlocked`は解除禁止条件の判定結果。画面はチェック解除・内容空欄化を拒否し、値を保持して既存MessageDialogで「課題を解除できません」、上記メッセージ、OKボタンを表示する。判定情報欠落時はINVALID_RESPONSE。保存時も同じRepositoryの判定を再取得する。課題行の排他ロックをProgressの提出状況・コメント更新と共有する。既存の楽観ロック・認可は維持する。

エラーは`400 INVALID_INPUT`、`401 AUTHENTICATION_REQUIRED`、`403 ACCESS_DENIED`、`404 SCHEDULE_NOT_FOUND`、`409 SCHEDULE_UPDATE_CONFLICT`を使用する。更新後の新人が0人の場合は`400 INVALID_INPUT`（`新人は1人以上登録する必要があります。`）、編集権限を持つ講師が0人の場合は`400 INVALID_INPUT`（`編集権限を持つ講師を1人以上登録する必要があります。`）、レビュー者またはコメント投稿者を権限一覧から削除する場合は`400 INVALID_INPUT`（`進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません`）、レビュー者を参照のみに変更する場合は`400 INVALID_INPUT`（`進捗・課題管理でレビュー者として登録されているため参照のみに変更できません`）、最終的な日別内容から参照中の分類を削除する場合は`400 INVALID_INPUT`（`この分類はスケジュール内容で使用されているため削除できません。`）を返し、同一PUT内の変更はすべてロールバックする。判定は対象の`schedule_id`に限定し、保存時にDBの最新利用状況を再取得する。PUTはCSRFトークンが必要である。

## PUT /api/schedules/{scheduleId}/pin

認証Principal本人の`schedule_can_edit.pin_flg`だけを変更する個人設定APIである。`can_edit`とは独立しており、新人、`can_edit=0`の講師、`can_edit=1`の講師が利用できる。社員IDはリクエストで受け取らない。

```json
{
  "pinned": true,
  "permissionUpdateCount": 2
}
```

正常時は`200`を返す。

```json
{
  "scheduleId": "S001",
  "pinned": true,
  "permissionUpdateCount": 3
}
```

ピン留めは1利用者につき最大2件である。新たに3件目を設定する場合は更新せず、`400 INVALID_INPUT`（`ピン留めできるスケジュールは2件までです。`）を返す。解除時には上限チェックを行わない。利用者の権限行群を排他制御してDB現在値から件数を判定する。

楽観ロックには本人行の`schedule_can_edit.update_count`を使用し、不一致は`409 SCHEDULE_UPDATE_CONFLICT`とする。成功時は本人行の`pin_flg`、`update_count`、`screen_id`、`update_user_cd`、`update_date`を更新し、`schedule.update_count`は変更しない。

セッション認証とCSRFトークンが必要である。未認証は`401 AUTHENTICATION_REQUIRED`、スケジュールは存在するが本人の権限行がない場合は`403 ACCESS_DENIED`、スケジュール自体がない場合は`404 SCHEDULE_NOT_FOUND`を返す。成功後は一覧APIを再取得し、サーバー側の「ピン留め、期間内、schedule_id」の順序を使用する。

## DELETE /api/schedules/{scheduleId}

登録済みスケジュールを物理削除する。セッション認証、CSRFトークン、認証Principal本人の`schedule_can_edit.can_edit=1`が必要である。社員区分は認可条件に使用しないため、新人も`can_edit=1`なら削除でき、講師も`can_edit=0`なら削除できない。

詳細取得時の`scheduleUpdateCount`をクエリパラメータで必須指定する。

`DELETE /api/schedules/S001?scheduleUpdateCount=3`

正常時は`204 No Content`を返す。削除順序は`schedule_details`、`schedule_bunrui`、`schedule_can_edit`、`schedule`で、処理全体を1トランザクションとし、途中失敗時はすべてロールバックする。DBのON DELETE CASCADEには依存しない。

`issue_submission_statuses`が1件でも存在する場合、コメントを含む課題実績は削除せず、`400 INVALID_INPUT`（`課題提出状況が登録されているため、このスケジュールは削除できません。`）を返す。`issue_statuses`は共有マスタであり削除対象外である。

存在しないスケジュールは`404 SCHEDULE_NOT_FOUND`、本人の権限行がない、または`can_edit=0`の場合は`403 ACCESS_DENIED`、未認証は`401 AUTHENTICATION_REQUIRED`、更新カウンタ不一致または削除直前の競合は`409 SCHEDULE_UPDATE_CONFLICT`を返す。

### 内容の任意入力（V3 / test1限定）
分類・講師は必須。内容はnull・空文字・空白のみを許可し、未入力はDBでNULLに正規化する。
登録・更新ともtaskFlg=trueで内容が未入力の場合は400。内容は最大50文字。
画面では未入力時に課題を無効化し、内容を消すとチェックを外す。
NULLの内容も取得・編集できる。課題解除禁止条件に該当する場合は内容空欄化を拒否する。

通常起動のFlywayはtarget=2に固定し、V3はcurrent_schema()がtest1以外ならDDL前に中断する。
V3のDDLはtest1.schedule_detailsを明示する。テスト設定のみtarget=latest。
publicへの適用は未承認であり、通常起動の上限は解除しない。
