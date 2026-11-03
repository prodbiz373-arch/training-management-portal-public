package com.training.portal.progress;

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

import com.training.portal.member.KenshuMemberService;
import com.training.portal.member.dto.MemberCreateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.training.portal.support.SecurityTestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ProgressApiTest.FixedBusinessClock.class)
class ProgressApiTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 3);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KenshuMemberService memberService;

    String trainee;
    String otherTrainee;
    String instructor;
    String viewerOnlyInstructor;
    String editableTrainee;
    String otherScheduleEditor;
    String schedule;
    String viewOnlySchedule;
    String inaccessibleSchedule;

    @BeforeEach
    void fixtures() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        trainee = "T" + suffix;
        otherTrainee = "U" + suffix;
        instructor = "I" + suffix;
        viewerOnlyInstructor = "V" + suffix;
        editableTrainee = "E" + suffix;
        otherScheduleEditor = "O" + suffix;
        schedule = "P1" + suffix;
        viewOnlySchedule = "P2" + suffix;
        inaccessibleSchedule = "PX" + suffix;

        createMember(instructor, (short) 2, "進捗講師");
        createMember(viewerOnlyInstructor, (short) 2, "参照講師");
        createMember(otherScheduleEditor, (short) 2, "別研修編集者");
        createMember(trainee, (short) 1, "本人新人");
        createMember(otherTrainee, (short) 1, "別新人");
        createMember(editableTrainee, (short) 1, "編集新人");
        insertSchedule(schedule, "実データ研修");
        insertSchedule(viewOnlySchedule, "参照研修");
        insertSchedule(inaccessibleSchedule, "権限外研修");
        insertPermission(schedule, instructor, 2, true);
        insertPermission(schedule, viewerOnlyInstructor, 2, false);
        insertPermission(schedule, trainee, 1, false);
        insertPermission(schedule, otherTrainee, 1, false);
        insertPermission(schedule, editableTrainee, 1, true);
        insertPermission(viewOnlySchedule, viewerOnlyInstructor, 2, false);
        insertPermission(viewOnlySchedule, otherScheduleEditor, 2, true);
        insertPermission(viewOnlySchedule, trainee, 1, false);
        insertPermission(inaccessibleSchedule, otherTrainee, 1, false);
        insertCategory(schedule, 1, "Java研修");
        insertCategory(schedule, 2, "Web研修");
        insertCategory(viewOnlySchedule, 1, "参照分類");
        insertCategory(inaccessibleSchedule, 1, "非公開分類");
        insertDetail(schedule, 1, 1, "実課題A", true, TODAY.minusDays(1));
        insertDetail(schedule, 2, 2, null, false, TODAY);
        insertDetail(schedule, 3, 2, "実課題B", true, TODAY.plusDays(1));
        insertDetail(viewOnlySchedule, 1, 1, "参照課題", true, TODAY);
        insertDetail(inaccessibleSchedule, 1, 1, "非公開課題", true, TODAY);
        insertSubmission(schedule, 1, trainee, instructor, 3);
        insertComment(schedule, 1, trainee, 1, trainee, "実DBコメント");
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get("/api/progress"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void traineeGetsOnlyOwnAccessibleTasksAndMissingSubmissionIsVirtualUnsubmitted() throws Exception {
        int before = jdbc.queryForObject("select count(*) from issue_submission_statuses", Integer.class);
        mvc.perform(get("/api/progress").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("TRAINEE"))
                .andExpect(jsonPath("$.statuses", hasSize(7)))
                .andExpect(jsonPath("$.statuses[0].statusCode").value(1))
                .andExpect(jsonPath("$.statuses[0].statusName").value("未提出"))
                .andExpect(jsonPath("$.statuses[6].statusCode").value(7))
                .andExpect(jsonPath("$.statuses[6].statusName").value("完了（コメント有）"))
                .andExpect(jsonPath("$.items[*].traineeEmployeeId", everyItem(org.hamcrest.Matchers.is(trainee))))
                .andExpect(jsonPath("$.items[*].content", not(hasItem("課題ではない内容"))))
                .andExpect(jsonPath("$.items[*].content", not(hasItem("非公開課題"))))
                .andExpect(jsonPath("$.items[?(@.content == '実課題B')].statusName", hasItem("未提出")))
                .andExpect(jsonPath("$.items[?(@.content == '実課題B')].submissionUpdateCount", hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.items[?(@.content == '実課題A')].comments[0].content", hasItem("実DBコメント")));
        assertThat(jdbc.queryForObject("select count(*) from issue_submission_statuses", Integer.class)).isEqualTo(before);
    }

    @Test
    void instructorGetsEveryAssignedTraineeAndCanEditFlag() throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", schedule)
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[*].traineeName", hasItem("本人新人")))
                .andExpect(jsonPath("$.items[*].traineeName", hasItem("別新人")))
                .andExpect(jsonPath("$.items[*].canEdit", everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void reviewerCandidatesAreAllAndOnlyScheduleEditorsRegardlessOfMemberType() throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", schedule)
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules[0].reviewers[*].employeeId", hasItem(instructor)))
                .andExpect(jsonPath("$.schedules[0].reviewers[*].employeeId", hasItem(editableTrainee)))
                .andExpect(jsonPath("$.schedules[0].reviewers[*].employeeId", not(hasItem(viewerOnlyInstructor))))
                .andExpect(jsonPath("$.schedules[0].reviewers[*].employeeId", not(hasItem(trainee))))
                .andExpect(jsonPath("$.schedules[0].reviewers[*].employeeId", not(hasItem(otherTrainee))))
                .andExpect(jsonPath("$.schedules[0].reviewers[*].employeeId", not(hasItem(otherScheduleEditor))));
    }

    @Test
    void editableTraineeCanBeSavedAsReviewerAndNonEditorIsRejected() throws Exception {
        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}", schedule, trainee)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":\"" + editableTrainee
                                + "\",\"statusCode\":3,\"updateCount\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewerEmployeeId").value(editableTrainee))
                .andExpect(jsonPath("$.reviewerName").value("編集新人"));

        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}", schedule, trainee)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":\"" + viewerOnlyInstructor
                                + "\",\"statusCode\":3,\"updateCount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}", schedule, trainee)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":\"" + otherScheduleEditor
                                + "\",\"statusCode\":3,\"updateCount\":1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}", schedule, trainee)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":\"UNKNOWN\",\"statusCode\":3,\"updateCount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void submissionUpdateStillRequiresCsrf() throws Exception {
        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}", schedule, trainee)
                        .with(user(instructor).roles("INSTRUCTOR"))
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":null,\"statusCode\":2,\"updateCount\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void canEditFalseInstructorCanReadButCannotUpdateOrComment() throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", schedule)
                        .with(user(viewerOnlyInstructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].canEdit").value(false))
                .andExpect(jsonPath("$.items[0].canComment").value(false));

        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}", schedule, trainee)
                        .with(user(viewerOnlyInstructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":null,\"statusCode\":2,\"updateCount\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inaccessibleScheduleAndOtherTraineeAreRejected() throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", inaccessibleSchedule)
                        .with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/progress").param("scheduleId", schedule)
                        .param("traineeEmployeeId", otherTrainee)
                        .with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoryMustBelongToSelectedScheduleAndZeroResultsAreNormal() throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", schedule).param("categoryNo", "99")
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/api/progress").param("scheduleId", schedule).param("submitter", "該当なし")
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void dateRangeSupportsHomeInstructorTodaySearchAndFutureSearch() throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", schedule)
                        .param("from", TODAY.toString()).param("to", TODAY.toString())
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/progress").param("scheduleId", schedule)
                        .param("from", TODAY.plusDays(1).toString())
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[*].content", everyItem(org.hamcrest.Matchers.is("実課題B"))));
    }

    @Test
    void editorCanCreateMissingSubmissionAndCompletionDateUsesBusinessDate() throws Exception {
        mvc.perform(put("/api/progress/{scheduleId}/3/trainees/{traineeId}", schedule, trainee)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":\"" + instructor
                                + "\",\"statusCode\":6,\"updateCount\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusName").value("完了"))
                .andExpect(jsonPath("$.completedDate").value("2026-04-03"))
                .andExpect(jsonPath("$.updateCount").value(0));
        assertThat(jdbc.queryForObject("""
                select task_status_kbn from issue_submission_statuses
                where schedule_id = ? and schedule_date_no = 3 and sinjin_syain_id = ?
                """, Short.class, schedule, trainee)).isEqualTo((short) 6);

        mvc.perform(get("/api/progress")
                        .param("scheduleId", schedule)
                        .param("scheduleDateNo", "3")
                        .param("traineeEmployeeId", trainee)
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].statusCode").value(6))
                .andExpect(jsonPath("$.items[0].statusName").value("完了"));
    }

    @Test
    void staleSubmissionUpdateReturns409() throws Exception {
        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}", schedule, trainee)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewerEmployeeId\":null,\"statusCode\":2,\"updateCount\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROGRESS_UPDATE_CONFLICT"));
    }

    @Test
    void traineeCanAddAndDeleteCommentOnlyOnOwnTask() throws Exception {
        String body = mvc.perform(post("/api/progress/{scheduleId}/1/trainees/{traineeId}/comments", schedule, trainee)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType("application/json").content("{\"content\":\"追加コメント\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("追加コメント"))
                .andReturn().getResponse().getContentAsString();
        long commentNo = Long.parseLong(body.replaceAll(".*\\\"commentNo\\\":(\\d+).*", "$1"));
        int updateCount = Integer.parseInt(body.replaceAll(".*\\\"updateCount\\\":(\\d+).*", "$1"));
        mvc.perform(delete("/api/progress/{scheduleId}/1/trainees/{traineeId}/comments/{commentNo}",
                        schedule, trainee, commentNo).param("updateCount", String.valueOf(updateCount))
                        .with(user(trainee).roles("TRAINEE")).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/progress/{scheduleId}/1/trainees/{traineeId}/comments", schedule, otherTrainee)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType("application/json").content("{\"content\":\"不正\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editableInstructorCannotDeleteAnotherAuthorsCommentAndCommentRemains() throws Exception {
        mvc.perform(delete("/api/progress/{scheduleId}/1/trainees/{traineeId}/comments/1", schedule, trainee)
                        .param("updateCount", "0")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbc.queryForObject("""
                select count(*) from issue_submission_statuses_comment
                where schedule_id = ? and schedule_date_no = 1 and sinjin_syain_id = ?
                  and comment_no = 1 and comment_syain_id = ? and comment_content = '実DBコメント'
                """, Integer.class, schedule, trainee, trainee)).isOne();
    }

    @Test
    void instructorCanDeleteOwnCommentEvenAfterEditPermissionIsRemoved() throws Exception {
        insertComment(schedule, 1, trainee, 2, viewerOnlyInstructor, "参照講師本人のコメント");

        mvc.perform(delete("/api/progress/{scheduleId}/1/trainees/{traineeId}/comments/2", schedule, trainee)
                        .param("updateCount", "0")
                        .with(user(viewerOnlyInstructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("""
                select count(*) from issue_submission_statuses_comment
                where schedule_id = ? and schedule_date_no = 1 and sinjin_syain_id = ? and comment_no = 2
                """, Integer.class, schedule, trainee)).isZero();
    }

    @Test
    void existingCommentReadAddAndUpdateBehaviorIsUnchanged() throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", schedule)
                        .with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.scheduleDateNo == 1)].comments[0].content")
                        .value("実DBコメント"));

        mvc.perform(put("/api/progress/{scheduleId}/1/trainees/{traineeId}/comments/1", schedule, trainee)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"更新済みコメント\",\"updateCount\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("更新済みコメント"));

        assertThat(jdbc.queryForObject("""
                select comment_content from issue_submission_statuses_comment
                where schedule_id = ? and schedule_date_no = 1 and sinjin_syain_id = ? and comment_no = 1
                """, String.class, schedule, trainee)).isEqualTo("更新済みコメント");
    }

    @Test
    void editorCannotUpdateAnotherAuthorsCommentAndNothingChanges() throws Exception {
        var before = jdbc.queryForMap("select * from issue_submission_statuses_comment where schedule_id=? and comment_no=1", schedule);
        mvc.perform(put("/api/progress/{id}/1/trainees/{traineeId}/comments/1", schedule, trainee)
                .with(user(instructor).roles("INSTRUCTOR")).with(csrf()).contentType("application/json")
                .content("{\"content\":\"他人による上書き\",\"updateCount\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(jdbc.queryForMap("select * from issue_submission_statuses_comment where schedule_id=? and comment_no=1", schedule)).isEqualTo(before);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   ", "long", "stale"})
    void invalidOrStaleCommentUpdateKeepsOriginal(String value) throws Exception {
        String content = value.equals("long") ? "x".repeat(201) : value;
        mvc.perform(put("/api/progress/{id}/1/trainees/{traineeId}/comments/1", schedule, trainee)
                .with(user(trainee).roles("TRAINEE")).with(csrf()).contentType("application/json")
                .content("{\"content\":\"" + content + "\",\"updateCount\":" + (value.equals("stale") ? 99 : 0) + "}"))
                .andExpect(status().is(value.equals("stale") ? 409 : 400));
        assertThat(jdbc.queryForObject("select comment_content from issue_submission_statuses_comment where schedule_id=? and comment_no=1", String.class, schedule)).isEqualTo("実DBコメント");
    }

    private void createMember(String id, short type, String name) {
        memberService.create(new MemberCreateRequest(id, type, name, null, "test-password"), instructor == null ? id : instructor);
    }

    private void insertSchedule(String id, String name) {
        jdbc.update("""
                insert into schedule (schedule_id, schedule_name, start_date, end_date, nohol_flg, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, 1, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, id, name, TODAY.minusDays(10), TODAY.plusDays(10));
    }

    private void insertPermission(String scheduleId, String employeeId, int type, boolean canEdit) {
        jdbc.update("""
                insert into schedule_can_edit (schedule_id, syain_id, syain_kbn, can_edit, pin_flg, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, 0, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, employeeId, type, canEdit ? 1 : 0);
    }

    private void insertCategory(String scheduleId, int no, String name) {
        jdbc.update("""
                insert into schedule_bunrui (schedule_id, bnrui_no, bnrui_name, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, no, name);
    }

    private void insertDetail(String scheduleId, int no, int category, String content, boolean task, LocalDate date) {
        jdbc.update("""
                insert into schedule_details (schedule_id, schedule_date_no, bnrui_no, koshi_syain_id,
                    schedule_content, task_flg, schedule_date, screen_id, update_count,
                    create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, ?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, no, category, instructor, content, task ? 1 : 0, date);
    }

    private void insertSubmission(String scheduleId, int no, String traineeId, String reviewerId, int status) {
        jdbc.update("""
                insert into issue_submission_statuses (schedule_id, schedule_date_no, sinjin_syain_id,
                    koshi_syain_id, task_status_kbn, screen_id, update_count,
                    create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, no, traineeId, reviewerId, status);
    }

    private void insertComment(String scheduleId, int no, String traineeId, long commentNo,
            String authorId, String content) {
        jdbc.update("""
                insert into issue_submission_statuses_comment (schedule_id, schedule_date_no, sinjin_syain_id,
                    comment_no, comment_syain_id, comment_content, screen_id, update_count,
                    create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, no, traineeId, commentNo, authorId, content);
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
