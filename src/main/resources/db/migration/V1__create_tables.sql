CREATE TABLE kenshu_member (
    syain_id       VARCHAR(10)  NOT NULL,
    syain_kbn      SMALLINT     NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    name_kana      VARCHAR(100),
    password       VARCHAR(255) NOT NULL,
    screen_id      VARCHAR(100) NOT NULL,
    update_count   INTEGER      NOT NULL DEFAULT 0,
    create_user_cd VARCHAR(100) NOT NULL,
    create_date    TIMESTAMP    NOT NULL,
    update_user_cd VARCHAR(100) NOT NULL,
    update_date    TIMESTAMP    NOT NULL,

    CONSTRAINT pk_kenshu_member PRIMARY KEY (syain_id),
    CONSTRAINT ck_kenshu_member_syain_kbn
        CHECK (syain_kbn IN (1, 2)),
    CONSTRAINT ck_kenshu_member_update_count
        CHECK (update_count >= 0)
);

CREATE TABLE schedule (
    schedule_id    VARCHAR(10)  NOT NULL,
    schedule_name  VARCHAR(40)  NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    nohol_flg      SMALLINT     NOT NULL DEFAULT 1,
    screen_id      VARCHAR(100) NOT NULL,
    update_count   INTEGER      NOT NULL DEFAULT 0,
    create_user_cd VARCHAR(100) NOT NULL,
    create_date    TIMESTAMP    NOT NULL,
    update_user_cd VARCHAR(100) NOT NULL,
    update_date    TIMESTAMP    NOT NULL,

    CONSTRAINT pk_schedule PRIMARY KEY (schedule_id),
    CONSTRAINT ck_schedule_date
        CHECK (start_date <= end_date),
    CONSTRAINT ck_schedule_nohol_flg
        CHECK (nohol_flg IN (0, 1)),
    CONSTRAINT ck_schedule_update_count
        CHECK (update_count >= 0)
);

CREATE TABLE schedule_can_edit (
    schedule_id    VARCHAR(10)  NOT NULL,
    syain_id       VARCHAR(10)  NOT NULL,
    syain_kbn      SMALLINT     NOT NULL,
    can_edit       SMALLINT     NOT NULL,
    pin_flg        SMALLINT     NOT NULL DEFAULT 0,
    screen_id      VARCHAR(100) NOT NULL,
    update_count   INTEGER      NOT NULL DEFAULT 0,
    create_user_cd VARCHAR(100) NOT NULL,
    create_date    TIMESTAMP    NOT NULL,
    update_user_cd VARCHAR(100) NOT NULL,
    update_date    TIMESTAMP    NOT NULL,

    CONSTRAINT pk_schedule_can_edit
        PRIMARY KEY (schedule_id, syain_id),
    CONSTRAINT fk_schedule_can_edit_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES schedule (schedule_id),
    CONSTRAINT fk_schedule_can_edit_member
        FOREIGN KEY (syain_id)
        REFERENCES kenshu_member (syain_id),
    CONSTRAINT ck_schedule_can_edit_syain_kbn
        CHECK (syain_kbn IN (1, 2)),
    CONSTRAINT ck_schedule_can_edit_can_edit
        CHECK (can_edit IN (0, 1)),
    CONSTRAINT ck_schedule_can_edit_pin_flg
        CHECK (pin_flg IN (0, 1)),
    CONSTRAINT ck_schedule_can_edit_update_count
        CHECK (update_count >= 0)
);

CREATE TABLE schedule_bunrui (
    schedule_id    VARCHAR(10)  NOT NULL,
    bnrui_no       SMALLINT     NOT NULL,
    bnrui_name     VARCHAR(30)  NOT NULL,
    screen_id      VARCHAR(100) NOT NULL,
    update_count   INTEGER      NOT NULL DEFAULT 0,
    create_user_cd VARCHAR(100) NOT NULL,
    create_date    TIMESTAMP    NOT NULL,
    update_user_cd VARCHAR(100) NOT NULL,
    update_date    TIMESTAMP    NOT NULL,

    CONSTRAINT pk_schedule_bunrui
        PRIMARY KEY (schedule_id, bnrui_no),
    CONSTRAINT fk_schedule_bunrui_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES schedule (schedule_id),
    CONSTRAINT ck_schedule_bunrui_no
        CHECK (bnrui_no BETWEEN 1 AND 99),
    CONSTRAINT ck_schedule_bunrui_update_count
        CHECK (update_count >= 0)
);

CREATE TABLE schedule_details (
    schedule_id       VARCHAR(10)  NOT NULL,
    schedule_date_no  INTEGER      NOT NULL,
    bnrui_no          SMALLINT     NOT NULL,
    koshi_syain_id    VARCHAR(10)  NOT NULL,
    schedule_content  VARCHAR(50)  NOT NULL,
    task_flg          SMALLINT     NOT NULL DEFAULT 0,
    schedule_date     DATE         NOT NULL,
    screen_id         VARCHAR(100) NOT NULL,
    update_count      INTEGER      NOT NULL DEFAULT 0,
    create_user_cd    VARCHAR(100) NOT NULL,
    create_date       TIMESTAMP    NOT NULL,
    update_user_cd    VARCHAR(100) NOT NULL,
    update_date       TIMESTAMP    NOT NULL,

    CONSTRAINT pk_schedule_details
        PRIMARY KEY (schedule_id, schedule_date_no),
    CONSTRAINT fk_schedule_details_bunrui
        FOREIGN KEY (schedule_id, bnrui_no)
        REFERENCES schedule_bunrui (schedule_id, bnrui_no),
    CONSTRAINT fk_schedule_details_koshi
        FOREIGN KEY (koshi_syain_id)
        REFERENCES kenshu_member (syain_id),
    CONSTRAINT ck_schedule_details_date_no
        CHECK (schedule_date_no >= 1),
    CONSTRAINT ck_schedule_details_task_flg
        CHECK (task_flg IN (0, 1)),
    CONSTRAINT ck_schedule_details_update_count
        CHECK (update_count >= 0)
);

CREATE TABLE issue_statuses (
    task_status_kbn  SMALLINT     NOT NULL,
    task_status_name VARCHAR(20)  NOT NULL,
    overdue_flags    SMALLINT     NOT NULL DEFAULT 0,
    rev_overdue_flg  SMALLINT     NOT NULL DEFAULT 0,
    screen_id        VARCHAR(100) NOT NULL,
    update_count     INTEGER      NOT NULL DEFAULT 0,
    create_user_cd   VARCHAR(100) NOT NULL,
    create_date      TIMESTAMP    NOT NULL,
    update_user_cd   VARCHAR(100) NOT NULL,
    update_date      TIMESTAMP    NOT NULL,

    CONSTRAINT pk_issue_statuses
        PRIMARY KEY (task_status_kbn),
    CONSTRAINT ck_issue_statuses_kbn
        CHECK (task_status_kbn BETWEEN 1 AND 7),
    CONSTRAINT ck_issue_statuses_overdue
        CHECK (overdue_flags IN (0, 1)),
    CONSTRAINT ck_issue_statuses_rev_overdue
        CHECK (rev_overdue_flg IN (0, 1)),
    CONSTRAINT ck_issue_statuses_update_count
        CHECK (update_count >= 0)
);

CREATE TABLE issue_submission_statuses (
    schedule_id       VARCHAR(10)  NOT NULL,
    schedule_date_no  INTEGER      NOT NULL,
    sinjin_syain_id   VARCHAR(10)  NOT NULL,
    koshi_syain_id    VARCHAR(10),
    task_status_kbn   SMALLINT     NOT NULL DEFAULT 1,
    completed_date    DATE,
    screen_id         VARCHAR(100) NOT NULL,
    update_count      INTEGER      NOT NULL DEFAULT 0,
    create_user_cd    VARCHAR(100) NOT NULL,
    create_date       TIMESTAMP    NOT NULL,
    update_user_cd    VARCHAR(100) NOT NULL,
    update_date       TIMESTAMP    NOT NULL,

    CONSTRAINT pk_issue_submission_statuses
        PRIMARY KEY (
            schedule_id,
            schedule_date_no,
            sinjin_syain_id
        ),
    CONSTRAINT fk_issue_submission_details
        FOREIGN KEY (schedule_id, schedule_date_no)
        REFERENCES schedule_details (schedule_id, schedule_date_no),
    CONSTRAINT fk_issue_submission_sinjin
        FOREIGN KEY (sinjin_syain_id)
        REFERENCES kenshu_member (syain_id),
    CONSTRAINT fk_issue_submission_koshi
        FOREIGN KEY (koshi_syain_id)
        REFERENCES kenshu_member (syain_id),
    CONSTRAINT fk_issue_submission_status
        FOREIGN KEY (task_status_kbn)
        REFERENCES issue_statuses (task_status_kbn),
    CONSTRAINT ck_issue_submission_update_count
        CHECK (update_count >= 0)
);

CREATE TABLE issue_submission_statuses_comment (
    schedule_id       VARCHAR(10)  NOT NULL,
    schedule_date_no  INTEGER      NOT NULL,
    sinjin_syain_id   VARCHAR(10)  NOT NULL,
    comment_no        BIGINT       NOT NULL,
    comment_syain_id  VARCHAR(10),
    comment_content   VARCHAR(200) NOT NULL,
    screen_id         VARCHAR(100) NOT NULL,
    update_count      INTEGER      NOT NULL DEFAULT 0,
    create_user_cd    VARCHAR(100) NOT NULL,
    create_date       TIMESTAMP    NOT NULL,
    update_user_cd    VARCHAR(100) NOT NULL,
    update_date       TIMESTAMP    NOT NULL,

    CONSTRAINT pk_issue_submission_comment
        PRIMARY KEY (
            schedule_id,
            schedule_date_no,
            sinjin_syain_id,
            comment_no
        ),
    CONSTRAINT fk_issue_submission_comment_submission
        FOREIGN KEY (
            schedule_id,
            schedule_date_no,
            sinjin_syain_id
        )
        REFERENCES issue_submission_statuses (
            schedule_id,
            schedule_date_no,
            sinjin_syain_id
        ),
    CONSTRAINT fk_issue_submission_comment_member
        FOREIGN KEY (comment_syain_id)
        REFERENCES kenshu_member (syain_id),
    CONSTRAINT ck_issue_submission_comment_no
        CHECK (comment_no >= 1),
    CONSTRAINT ck_issue_submission_comment_update_count
        CHECK (update_count >= 0)
);

-- =========================================================
-- 論理名（テーブル・カラムコメント）
-- =========================================================

COMMENT ON TABLE kenshu_member IS '研修メンバー';
COMMENT ON COLUMN kenshu_member.syain_id IS '社員ID';
COMMENT ON COLUMN kenshu_member.syain_kbn IS '社員区分';
COMMENT ON COLUMN kenshu_member.name IS '社員名';
COMMENT ON COLUMN kenshu_member.name_kana IS '社員名ふりがな';
COMMENT ON COLUMN kenshu_member.password IS 'パスワード';
COMMENT ON COLUMN kenshu_member.screen_id IS '画面ID';
COMMENT ON COLUMN kenshu_member.update_count IS '更新カウンタ';
COMMENT ON COLUMN kenshu_member.create_user_cd IS '作成者';
COMMENT ON COLUMN kenshu_member.create_date IS '作成日';
COMMENT ON COLUMN kenshu_member.update_user_cd IS '最終更新者';
COMMENT ON COLUMN kenshu_member.update_date IS '最終更新日';

COMMENT ON TABLE schedule IS 'スケジュール';
COMMENT ON COLUMN schedule.schedule_id IS 'スケジュールID';
COMMENT ON COLUMN schedule.schedule_name IS 'スケジュール名';
COMMENT ON COLUMN schedule.start_date IS '期間開始日';
COMMENT ON COLUMN schedule.end_date IS '期間終了日';
COMMENT ON COLUMN schedule.nohol_flg IS '休日無しフラグ';
COMMENT ON COLUMN schedule.screen_id IS '画面ID';
COMMENT ON COLUMN schedule.update_count IS '更新カウンタ';
COMMENT ON COLUMN schedule.create_user_cd IS '作成者';
COMMENT ON COLUMN schedule.create_date IS '作成日';
COMMENT ON COLUMN schedule.update_user_cd IS '最終更新者';
COMMENT ON COLUMN schedule.update_date IS '最終更新日';

COMMENT ON TABLE schedule_can_edit IS 'スケジュール_編集権限';
COMMENT ON COLUMN schedule_can_edit.schedule_id IS 'スケジュールID';
COMMENT ON COLUMN schedule_can_edit.syain_id IS '社員ID';
COMMENT ON COLUMN schedule_can_edit.syain_kbn IS '社員区分';
COMMENT ON COLUMN schedule_can_edit.can_edit IS '編集権限';
COMMENT ON COLUMN schedule_can_edit.pin_flg IS 'ピン留め';
COMMENT ON COLUMN schedule_can_edit.screen_id IS '画面ID';
COMMENT ON COLUMN schedule_can_edit.update_count IS '更新カウンタ';
COMMENT ON COLUMN schedule_can_edit.create_user_cd IS '作成者';
COMMENT ON COLUMN schedule_can_edit.create_date IS '作成日';
COMMENT ON COLUMN schedule_can_edit.update_user_cd IS '最終更新者';
COMMENT ON COLUMN schedule_can_edit.update_date IS '最終更新日';

COMMENT ON TABLE schedule_bunrui IS 'スケジュール_分類';
COMMENT ON COLUMN schedule_bunrui.schedule_id IS 'スケジュールID';
COMMENT ON COLUMN schedule_bunrui.bnrui_no IS '分類NO';
COMMENT ON COLUMN schedule_bunrui.bnrui_name IS '分類名称';
COMMENT ON COLUMN schedule_bunrui.screen_id IS '画面ID';
COMMENT ON COLUMN schedule_bunrui.update_count IS '更新カウンタ';
COMMENT ON COLUMN schedule_bunrui.create_user_cd IS '作成者';
COMMENT ON COLUMN schedule_bunrui.create_date IS '作成日';
COMMENT ON COLUMN schedule_bunrui.update_user_cd IS '最終更新者';
COMMENT ON COLUMN schedule_bunrui.update_date IS '最終更新日';

COMMENT ON TABLE schedule_details IS 'スケジュール詳細';
COMMENT ON COLUMN schedule_details.schedule_id IS 'スケジュールID';
COMMENT ON COLUMN schedule_details.schedule_date_no IS 'スケジュール日付NO';
COMMENT ON COLUMN schedule_details.bnrui_no IS '分類NO';
COMMENT ON COLUMN schedule_details.koshi_syain_id IS '講師社員ID';
COMMENT ON COLUMN schedule_details.schedule_content IS 'スケジュール内容';
COMMENT ON COLUMN schedule_details.task_flg IS '課題フラグ';
COMMENT ON COLUMN schedule_details.schedule_date IS 'スケジュール日付';
COMMENT ON COLUMN schedule_details.screen_id IS '画面ID';
COMMENT ON COLUMN schedule_details.update_count IS '更新カウンタ';
COMMENT ON COLUMN schedule_details.create_user_cd IS '作成者';
COMMENT ON COLUMN schedule_details.create_date IS '作成日';
COMMENT ON COLUMN schedule_details.update_user_cd IS '最終更新者';
COMMENT ON COLUMN schedule_details.update_date IS '最終更新日';

COMMENT ON TABLE issue_statuses IS '課題状況';
COMMENT ON COLUMN issue_statuses.task_status_kbn IS '課題ステータス区分';
COMMENT ON COLUMN issue_statuses.task_status_name IS '課題ステータス名称';
COMMENT ON COLUMN issue_statuses.overdue_flags IS '提出期限エラーフラグ';
COMMENT ON COLUMN issue_statuses.rev_overdue_flg IS 'レビュー期限エラーフラグ';
COMMENT ON COLUMN issue_statuses.screen_id IS '画面ID';
COMMENT ON COLUMN issue_statuses.update_count IS '更新カウンタ';
COMMENT ON COLUMN issue_statuses.create_user_cd IS '作成者';
COMMENT ON COLUMN issue_statuses.create_date IS '作成日';
COMMENT ON COLUMN issue_statuses.update_user_cd IS '最終更新者';
COMMENT ON COLUMN issue_statuses.update_date IS '最終更新日';

COMMENT ON TABLE issue_submission_statuses IS '課題提出状況';
COMMENT ON COLUMN issue_submission_statuses.schedule_id IS 'スケジュールID';
COMMENT ON COLUMN issue_submission_statuses.schedule_date_no IS 'スケジュール日付NO';
COMMENT ON COLUMN issue_submission_statuses.sinjin_syain_id IS '新人社員ID';
COMMENT ON COLUMN issue_submission_statuses.koshi_syain_id IS '講師社員ID';
COMMENT ON COLUMN issue_submission_statuses.task_status_kbn IS '課題ステータス区分';
COMMENT ON COLUMN issue_submission_statuses.completed_date IS '完了日';
COMMENT ON COLUMN issue_submission_statuses.screen_id IS '画面ID';
COMMENT ON COLUMN issue_submission_statuses.update_count IS '更新カウンタ';
COMMENT ON COLUMN issue_submission_statuses.create_user_cd IS '作成者';
COMMENT ON COLUMN issue_submission_statuses.create_date IS '作成日';
COMMENT ON COLUMN issue_submission_statuses.update_user_cd IS '最終更新者';
COMMENT ON COLUMN issue_submission_statuses.update_date IS '最終更新日';

COMMENT ON TABLE issue_submission_statuses_comment IS '課題提出状況_コメント';
COMMENT ON COLUMN issue_submission_statuses_comment.schedule_id IS 'スケジュールID';
COMMENT ON COLUMN issue_submission_statuses_comment.schedule_date_no IS 'スケジュール日付NO';
COMMENT ON COLUMN issue_submission_statuses_comment.sinjin_syain_id IS '新人社員ID';
COMMENT ON COLUMN issue_submission_statuses_comment.comment_no IS 'コメントNO';
COMMENT ON COLUMN issue_submission_statuses_comment.comment_syain_id IS 'コメント社員ID';
COMMENT ON COLUMN issue_submission_statuses_comment.comment_content IS 'コメント内容';
COMMENT ON COLUMN issue_submission_statuses_comment.screen_id IS '画面ID';
COMMENT ON COLUMN issue_submission_statuses_comment.update_count IS '更新カウンタ';
COMMENT ON COLUMN issue_submission_statuses_comment.create_user_cd IS '作成者';
COMMENT ON COLUMN issue_submission_statuses_comment.create_date IS '作成日';
COMMENT ON COLUMN issue_submission_statuses_comment.update_user_cd IS '最終更新者';
COMMENT ON COLUMN issue_submission_statuses_comment.update_date IS '最終更新日';