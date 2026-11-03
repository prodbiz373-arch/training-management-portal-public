package com.training.portal.home;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.training.portal.member.KenshuMemberService;
import com.training.portal.member.dto.MemberCreateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static com.training.portal.support.SecurityTestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(HomeApiTest.FixedBusinessClock.class)
class HomeApiTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 3);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KenshuMemberService memberService;
    @Autowired EntityManager entityManager;

    String trainee;
    String secondTrainee;
    String instructor;
    String noScheduleTrainee;
    String firstSchedule;
    String secondSchedule;
    String inaccessibleSchedule;

    @BeforeEach
    void fixtures() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        trainee = "T" + suffix;
        secondTrainee = "U" + suffix;
        instructor = "I" + suffix;
        noScheduleTrainee = "N" + suffix;
        firstSchedule = "H1" + suffix;
        secondSchedule = "H2" + suffix;
        inaccessibleSchedule = "HX" + suffix;

        memberService.create(new MemberCreateRequest(instructor, (short) 2, "ホーム講師", null, "test-password"), instructor);
        memberService.create(new MemberCreateRequest(trainee, (short) 1, "本人新人", null, "test-password"), instructor);
        memberService.create(new MemberCreateRequest(secondTrainee, (short) 1, "別新人", null, "test-password"), instructor);
        memberService.create(new MemberCreateRequest(noScheduleTrainee, (short) 1, "空新人", null, "test-password"), instructor);
        insertSchedule(firstSchedule, "ホーム研修A", TODAY.minusDays(5), TODAY.plusDays(5));
        insertSchedule(secondSchedule, "ホーム研修B", TODAY.minusDays(10), TODAY.minusDays(2));
        insertSchedule(inaccessibleSchedule, "権限外研修", TODAY.minusDays(1), TODAY.plusDays(1));
        insertPermission(firstSchedule, instructor, 2, true, true);
        insertPermission(firstSchedule, trainee, 1, false, true);
        insertPermission(firstSchedule, secondTrainee, 1, false, false);
        insertPermission(secondSchedule, trainee, 1, false, false);
        insertPermission(inaccessibleSchedule, secondTrainee, 1, false, false);
        insertCategory(firstSchedule, "Java研修");
        insertCategory(secondSchedule, "Web研修");
        insertCategory(inaccessibleSchedule, "非公開分類");

        insertDetail(firstSchedule, 1, "過去の課題", true, TODAY.minusDays(1));
        insertDetail(firstSchedule, 2, "今日の課題", true, TODAY);
        insertDetail(firstSchedule, 3, "未来の課題", true, TODAY.plusDays(1));
        insertDetail(firstSchedule, 4, "課題なし", false, TODAY.minusDays(2));
        insertDetail(secondSchedule, 1, "完了した課題", true, TODAY.minusDays(3));
        insertDetail(inaccessibleSchedule, 1, "見えてはいけない課題", true, TODAY);

        insertSubmission(firstSchedule, 1, trainee, 2);
        insertSubmission(firstSchedule, 1, secondTrainee, 1);
        insertSubmission(firstSchedule, 2, secondTrainee, 6);
        insertSubmission(firstSchedule, 3, trainee, 6);
        insertSubmission(secondSchedule, 1, trainee, 7);
        insertSubmission(inaccessibleSchedule, 1, trainee, 6);
    }

    @Test
    void nonTaskWithNullContentIsReturnedWithoutChangingTaskSummary() throws Exception {
        jdbc.update("update schedule_details set schedule_content = null where schedule_id = ? and schedule_date_no = 4", firstSchedule);
        mvc.perform(get("/api/home").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules[0].details[?(@.scheduleDateNo == 4)].content", hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.taskSummary.unsubmitted").value(1))
                .andExpect(jsonPath("$.taskSummary.completed").value(1));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get("/api/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedPrincipalMissingFromMemberTableReturns401() throws Exception {
        mvc.perform(get("/api/home").with(user("unknown").roles("TRAINEE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void traineeGetsOnlyOwnAccessibleSchedulesAndOwnTaskStatuses() throws Exception {
        mvc.perform(get("/api/home").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value("2026-04-03"))
                .andExpect(jsonPath("$.viewerRole").value("TRAINEE"))
                .andExpect(jsonPath("$.taskSummary.unsubmitted").value(1))
                .andExpect(jsonPath("$.taskSummary.correctionRequired").value(1))
                .andExpect(jsonPath("$.taskSummary.inReview").value(0))
                .andExpect(jsonPath("$.taskSummary.completed").value(1))
                .andExpect(jsonPath("$.schedules", hasSize(2)))
                .andExpect(jsonPath("$.schedules[0].scheduleId").value(firstSchedule))
                .andExpect(jsonPath("$.schedules[0].canEdit").value(false))
                .andExpect(jsonPath("$.schedules[0].pinned").value(true))
                .andExpect(jsonPath("$.schedules[0].details[0].scheduleDate").value("2026-04-03"))
                .andExpect(jsonPath("$.schedules[0].details[0].taskStatusName").value("未提出"))
                .andExpect(jsonPath("$.schedules[0].details[2].taskStatusName").value("修正待"))
                .andExpect(jsonPath("$.schedules[*].scheduleId", not(hasItem(inaccessibleSchedule))));
    }

    @Test
    void futureTaskAndOtherTraineeCompletionAreNotIncludedInTraineeSummary() throws Exception {
        mvc.perform(get("/api/home").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskSummary.completed").value(1))
                .andExpect(jsonPath("$.schedules[0].details[0].taskStatusCode").value(1))
                .andExpect(jsonPath("$.schedules[0].details[3].taskStatusCode").value(6));
    }

    @Test
    void instructorGetsScheduleTraineeAggregationIncludingMissingSubmissionAsUnsubmitted() throws Exception {
        mvc.perform(get("/api/home").with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.schedules", hasSize(1)))
                .andExpect(jsonPath("$.taskSummary.unsubmitted").value(2))
                .andExpect(jsonPath("$.taskSummary.correctionRequired").value(1))
                .andExpect(jsonPath("$.taskSummary.completed").value(1))
                .andExpect(jsonPath("$.schedules[0].details[0].completedCount").value(1))
                .andExpect(jsonPath("$.schedules[0].details[0].targetTraineeCount").value(2))
                .andExpect(jsonPath("$.schedules[0].details[2].completedCount").value(0))
                .andExpect(jsonPath("$.schedules[0].details[2].targetTraineeCount").value(2));
    }

    @Test
    void noAccessibleScheduleReturnsEmptyDashboard() throws Exception {
        mvc.perform(get("/api/home").with(user(noScheduleTrainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskSummary.unsubmitted").value(0))
                .andExpect(jsonPath("$.taskSummary.completed").value(0))
                .andExpect(jsonPath("$.schedules", hasSize(0)));
    }

    @Test
    void pinZeroOneAndTwoAreReturnedInScheduleApiOrder() throws Exception {
        jdbc.update("update schedule_can_edit set pin_flg = 0 where syain_id = ?", trainee);
        mvc.perform(get("/api/home").with(user(trainee).roles("TRAINEE")))
                .andExpect(jsonPath("$.schedules[0].pinned").value(false))
                .andExpect(jsonPath("$.schedules[1].pinned").value(false));

        jdbc.update("update schedule_can_edit set pin_flg = 1 where schedule_id = ? and syain_id = ?", firstSchedule, trainee);
        entityManager.clear();
        mvc.perform(get("/api/home").with(user(trainee).roles("TRAINEE")))
                .andExpect(jsonPath("$.schedules[0].scheduleId").value(firstSchedule))
                .andExpect(jsonPath("$.schedules[0].pinned").value(true));

        jdbc.update("update schedule_can_edit set pin_flg = 1 where schedule_id = ? and syain_id = ?", secondSchedule, trainee);
        entityManager.clear();
        mvc.perform(get("/api/home").with(user(trainee).roles("TRAINEE")))
                .andExpect(jsonPath("$.schedules[0].scheduleId").value(firstSchedule))
                .andExpect(jsonPath("$.schedules[1].scheduleId").value(secondSchedule))
                .andExpect(jsonPath("$.schedules[0].pinned").value(true))
                .andExpect(jsonPath("$.schedules[1].pinned").value(true));
    }

    private void insertSchedule(String id, String name, LocalDate start, LocalDate end) {
        jdbc.update("""
                insert into schedule (schedule_id, schedule_name, start_date, end_date, nohol_flg, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, 1, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, id, name, start, end);
    }

    private void insertPermission(String scheduleId, String employeeId, int memberType, boolean canEdit, boolean pinned) {
        jdbc.update("""
                insert into schedule_can_edit (schedule_id, syain_id, syain_kbn, can_edit, pin_flg, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, employeeId, memberType, canEdit ? 1 : 0, pinned ? 1 : 0);
    }

    private void insertCategory(String scheduleId, String name) {
        jdbc.update("""
                insert into schedule_bunrui (schedule_id, bnrui_no, bnrui_name, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, 1, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, name);
    }

    private void insertDetail(String scheduleId, int no, String content, boolean task, LocalDate date) {
        jdbc.update("""
                insert into schedule_details (schedule_id, schedule_date_no, bnrui_no, koshi_syain_id,
                    schedule_content, task_flg, schedule_date, screen_id, update_count,
                    create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, 1, ?, ?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, no, instructor, content, task ? 1 : 0, date);
    }

    private void insertSubmission(String scheduleId, int no, String traineeId, int status) {
        jdbc.update("""
                insert into issue_submission_statuses (schedule_id, schedule_date_no, sinjin_syain_id,
                    task_status_kbn, screen_id, update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, no, traineeId, status);
    }

    @TestConfiguration
    static class FixedBusinessClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-04-02T15:00:00Z"), ZoneId.of("Asia/Tokyo"));
        }
    }
}
