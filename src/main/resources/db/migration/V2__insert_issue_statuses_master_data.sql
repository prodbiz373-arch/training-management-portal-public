WITH formal_statuses (task_status_kbn, task_status_name) AS (
    VALUES
        (1::SMALLINT, '未提出'),
        (2::SMALLINT, '修正待'),
        (3::SMALLINT, '提出済'),
        (4::SMALLINT, 'GPレビュー中'),
        (5::SMALLINT, '講師レビュー中'),
        (6::SMALLINT, '完了'),
        (7::SMALLINT, '完了（コメント有）')
)
INSERT INTO issue_statuses (
    task_status_kbn,
    task_status_name,
    overdue_flags,
    rev_overdue_flg,
    screen_id,
    update_count,
    create_user_cd,
    create_date,
    update_user_cd,
    update_date
)
SELECT
    task_status_kbn,
    task_status_name,
    0,
    0,
    'FLYWAY',
    0,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
FROM formal_statuses
ON CONFLICT (task_status_kbn) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            VALUES
                (1::SMALLINT, '未提出'),
                (2::SMALLINT, '修正待'),
                (3::SMALLINT, '提出済'),
                (4::SMALLINT, 'GPレビュー中'),
                (5::SMALLINT, '講師レビュー中'),
                (6::SMALLINT, '完了'),
                (7::SMALLINT, '完了（コメント有）')
        ) AS expected(task_status_kbn, task_status_name)
        LEFT JOIN issue_statuses actual
            ON actual.task_status_kbn = expected.task_status_kbn
        WHERE actual.task_status_name IS DISTINCT FROM expected.task_status_name
    ) THEN
        RAISE EXCEPTION 'issue_statuses contains a code/name that differs from the formal master data';
    END IF;
END
$$;
