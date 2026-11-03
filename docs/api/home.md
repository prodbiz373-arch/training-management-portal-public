# ホーム API

## GET /api/home

ログイン済みの新人・講師が利用する、HOM01専用の読み取りAPI。Controllerは認証Principalの社員IDだけをServiceへ渡し、社員IDや社員区分をリクエストから受け取らない。業務日付はサーバーOSやDBセッションに依存せず、`Asia/Tokyo`のClockから決定する。

本人の`schedule_can_edit`が存在するスケジュールを、既存の一覧APIと同じく「本人の`pin_flg=1`、当日が期間内、`schedule_id`昇順」で返す。`can_edit=0`も閲覧対象である。各スケジュールの明細は当日行を先頭、そのほかを日付・`schedule_date_no`昇順で返す。画面では全明細を保持し、10日分の高さで残りをスクロール表示する。

```json
{
  "businessDate": "2026-04-03",
  "viewerRole": "TRAINEE",
  "taskSummary": {
    "unsubmitted": 1,
    "correctionRequired": 1,
    "inReview": 2,
    "completed": 3
  },
  "schedules": [
    {
      "scheduleId": "S001",
      "scheduleName": "新人研修",
      "startDate": "2026-04-01",
      "endDate": "2026-04-30",
      "canEdit": false,
      "pinned": true,
      "details": [
        {
          "scheduleDateNo": 3,
          "scheduleDate": "2026-04-03",
          "categoryNo": 1,
          "categoryName": "Java研修",
          "content": "チャプター2",
          "instructorName": "初期講師",
          "taskFlg": true,
          "taskStatusCode": 1,
          "taskStatusName": "未提出",
          "completedCount": null,
          "targetTraineeCount": null
        }
      ]
    }
  ]
}
```

「今日までの課題状況」は、`task_flg=1`かつ`schedule_date <= businessDate`を対象とする。未来日の課題は含めない。新人は本人が閲覧可能なスケジュールにある本人分だけ、講師は本人が閲覧可能なスケジュールに権限登録された新人分を集計する。提出状況が未生成の対象課題は、テーブル定義の初期状態である区分1（未提出）として扱い、完了には含めない。

ステータス集約は、区分1を未提出、2を修正待ち、3～5をレビュー中、6～7を完了とする。新人の日別課題には本人のステータスを返す。講師の日別課題には、そのスケジュールに登録された新人のうち区分6～7の人数を`completedCount`、対象新人総数を`targetTraineeCount`として返し、画面で`[完了] n/m`と表示する。

ピン留め済みスケジュールは`schedules`内の`pinned`で判定し、画面の「今日の予定」に最大2件表示する。本APIはピン留めを変更しない。

対象課題・スケジュール・ピン留め・対象新人が0件でも`200`を返し、件数0または空配列で表す。未認証は`401 AUTHENTICATION_REQUIRED`、認証社員がDB上に存在しないなど認可不能な場合は`403 ACCESS_DENIED`、予期しない失敗は`500 INTERNAL_ERROR`である。本APIは読み取り専用でCSRFトークンは不要である。

参照テーブルは`kenshu_member`、`schedule`、`schedule_can_edit`、`schedule_bunrui`、`schedule_details`、`issue_submission_statuses`、`issue_statuses`。スケジュール・明細・対象新人・提出状況を集合取得し、利用者やスケジュールごとのAPI呼び出し・SQL発行は行わない。
