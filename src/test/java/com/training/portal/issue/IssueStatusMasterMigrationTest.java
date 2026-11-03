package com.training.portal.issue;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IssueStatusMasterMigrationTest {
    private static final List<String> FORMAL_STATUSES = List.of(
            "1:未提出",
            "2:修正待",
            "3:提出済",
            "4:GPレビュー中",
            "5:講師レビュー中",
            "6:完了",
            "7:完了（コメント有）");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void flywayRegistersAllFormalStatusesExactlyOnce() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");

        List<String> actual = jdbc.query("""
                select task_status_kbn, task_status_name
                from issue_statuses
                order by task_status_kbn
                """, (resultSet, rowNum) -> resultSet.getShort("task_status_kbn")
                        + ":" + resultSet.getString("task_status_name"));

        assertThat(actual).containsExactlyElementsOf(FORMAL_STATUSES);
        assertThat(jdbc.queryForObject("select count(distinct task_status_kbn) from issue_statuses", Integer.class))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                select count(*) from issue_statuses
                where overdue_flags = 0
                  and rev_overdue_flg = 0
                  and update_count = 0
                  and screen_id = 'FLYWAY'
                  and create_user_cd = 'SYSTEM'
                  and update_user_cd = 'SYSTEM'
                  and create_date is not null
                  and update_date is not null
                """, Integer.class)).isEqualTo(7);
    }

    @Test
    void rerunningFlywayDoesNotDuplicateMasterRows() {
        int before = jdbc.queryForObject("select count(*) from issue_statuses", Integer.class);

        assertThat(flyway.migrate().migrationsExecuted).isZero();

        assertThat(jdbc.queryForObject("select count(*) from issue_statuses", Integer.class)).isEqualTo(before);
        assertThat(before).isEqualTo(7);
    }
}
