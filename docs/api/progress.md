# 進捗・課題管理 API

PRM01用APIはすべて認証必須で、対象社員は認証Principalから決定する。リクエストで指定された社員区分は使用せず、`kenshu_member.syain_kbn`を参照する。本人の`schedule_can_edit`が存在するスケジュールだけ閲覧でき、`can_edit=0`も参照可能である。

## GET /api/progress

閲覧可能なスケジュール・分類、スケジュール別のレビュー者候補、`issue_statuses`の正式な7ステータス、検索条件に一致する課題提出状況をまとめて返す。対象課題は`schedule_details.task_flg=1`だけである。

任意クエリは`scheduleId`、`categoryNo`、`submitter`、`from`、`to`、`reviewerEmployeeId`、`statusCode`、`scheduleDateNo`、`traineeEmployeeId`。`categoryNo`と`scheduleDateNo`は`scheduleId`との所属を検証する。URL値だけで認可せず、権限外スケジュール・新人は`403`とする。

新人は本人分だけを返す。講師は対象スケジュールの`schedule_can_edit`を持ち、実メンバーの区分が新人である利用者を対象とする。提出状況が未作成の場合はレスポンス上だけ区分1「未提出」とし、GETではINSERTしない。

```json
{
  "businessDate": "2026-04-03",
  "viewerRole": "TRAINEE",
  "schedules": [{
    "scheduleId": "S001",
    "scheduleName": "新人研修",
    "canEdit": false,
    "categories": [{ "categoryNo": 1, "categoryName": "Java研修" }],
    "reviewers": [
      { "employeeId": "admin001", "name": "初期講師" },
      { "employeeId": "trainee002", "name": "編集新人" }
    ]
  }],
  "statuses": [{ "statusCode": 1, "statusName": "未提出" }],
  "items": [{
    "scheduleId": "S001",
    "scheduleName": "新人研修",
    "scheduleDateNo": 3,
    "scheduleDate": "2026-04-03",
    "categoryNo": 1,
    "categoryName": "Java研修",
    "content": "チャプター2",
    "assignedInstructorEmployeeId": "admin001",
    "assignedInstructorName": "初期講師",
    "traineeEmployeeId": "trainee001",
    "traineeName": "初期新人",
    "reviewerEmployeeId": null,
    "reviewerName": null,
    "statusCode": 1,
    "statusName": "未提出",
    "completedDate": null,
    "canEdit": false,
    "canComment": true,
    "submissionUpdateCount": null,
    "comments": []
  }]
}
```

課題・分類・新人・提出状況・コメントが0件でも`200`と空配列を返す。スケジュール、明細、対象新人、提出状況、コメントは集合取得し、新人・課題・コメント単位のSQLを発行しない。

## PUT /api/progress/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}

レビュー者と正式ステータスを更新する。対象スケジュールの本人権限が`can_edit=1`の場合のみ利用できる。提出状況が未作成なら`updateCount=null`で新規作成し、既存なら取得時の`updateCount`を必須とする。競合は`409 PROGRESS_UPDATE_CONFLICT`。

```json
{ "reviewerEmployeeId": "admin001", "statusCode": 6, "updateCount": 2 }
```

ステータス6または7への変更時は完了日をAsia/Tokyoの業務日付へ自動設定し、それ以外へ戻すとNULLにする。レビュー者候補は対象スケジュールの`schedule_can_edit.can_edit=1`を持つ実メンバーであり、社員区分では制限しない。保存時も同じ条件を検証する。

## コメントAPI

- `POST /api/progress/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}/comments`
- `PUT /api/progress/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}/comments/{commentNo}`
- `DELETE /api/progress/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}/comments/{commentNo}?updateCount=...`

本文は空文字不可・200文字以内。新人は本人の課題、講師は`can_edit=1`の対象課題だけ追加・更新可能。コメント更新はこの条件に加え、投稿者本人に限定する。他人のコメントは編集権限があっても403 ACCESS_DENIED。これは従来のPUTから認可を厳格化した変更で、リクエスト形式は変更しない。追加時は課題単位で`comment_no`を採番する。更新・削除は`updateCount`で楽観ロックする。

削除は、認証Principalの社員IDとDBの`issue_submission_statuses_comment.comment_syain_id`が一致する投稿者本人だけが実行できる。講師やスケジュール編集権限者であっても、他人のコメントは削除できず`403 ACCESS_DENIED`とする。削除対象の投稿者はリクエスト値では判定せず、DBのコメント行から取得する。本人による削除は完全削除する。提出状況未作成の課題へ最初のコメントを追加する場合、この更新操作内で区分1の提出状況を作成する。

## エラー

- `400 INVALID_INPUT`: 日付範囲、分類所属、ステータス、レビュー者、コメント等の入力不正
- `401 AUTHENTICATION_REQUIRED`: 未認証
- `403 ACCESS_DENIED`: スケジュール／新人の閲覧権限なし、更新権限なし、または他人のコメント編集・削除
- `404 PROGRESS_NOT_FOUND`: 対象課題・コメントなし
- `409 PROGRESS_UPDATE_CONFLICT`: 提出状況・コメントの更新競合
- `500 INTERNAL_ERROR`: 予期しない失敗

参照・更新テーブルは`kenshu_member`、`schedule`、`schedule_can_edit`、`schedule_bunrui`、`schedule_details`、`issue_statuses`、`issue_submission_statuses`、`issue_submission_statuses_comment`。DB構造とFlywayは変更しない。
