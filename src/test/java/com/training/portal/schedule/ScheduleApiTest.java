package com.training.portal.schedule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import com.training.portal.member.KenshuMemberService;
import com.training.portal.member.dto.MemberCreateRequest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static com.training.portal.support.SecurityTestUsers.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ScheduleApiTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KenshuMemberService members;
    @Autowired ObjectMapper objectMapper;

    String trainee;
    String instructor;
    String secondInstructor;
    String secondTrainee;
    String pinned;
    String active;
    String inactive;
    String inaccessible;

    @BeforeEach
    void fixtures() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        trainee = "T" + suffix;
        instructor = "I" + suffix;
        secondInstructor = "J" + suffix;
        secondTrainee = "U" + suffix;
        members.create(new MemberCreateRequest(instructor, (short) 2, "テスト講師", null, "test-password"), instructor);
        members.create(new MemberCreateRequest(secondInstructor, (short) 2, "追加講師", null, "test-password"), instructor);
        members.create(new MemberCreateRequest(trainee, (short) 1, "テスト新人", null, "test-password"), instructor);
        members.create(new MemberCreateRequest(secondTrainee, (short) 1, "削除対象新人", null, "test-password"), instructor);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        pinned = "Z" + suffix;
        active = "Y" + suffix;
        inactive = "A" + suffix;
        inaccessible = "X" + suffix;
        insertSchedule(pinned, "ピン留め済み", today.minusDays(10), today.minusDays(1), 3);
        insertSchedule(active, "期間内", today.minusDays(1), today.plusDays(1), 2);
        insertSchedule(inactive, "期間外", today.plusDays(2), today.plusDays(10), 1);
        insertSchedule(inaccessible, "権限なし", today.minusDays(1), today.plusDays(1), 0);

        insertPermission(pinned, trainee, (short) 1, (short) 1, 1);
        insertPermission(active, trainee, (short) 0, (short) 0, 4);
        insertPermission(inactive, trainee, (short) 0, (short) 0, 0);
        insertPermission(active, instructor, (short) 1, (short) 0, 2);
        insertPermission(active, secondTrainee, (short) 0, (short) 0, 0);
        insertCategory(active, (short) 1, "Java研修");
        insertCategory(active, (short) 2, "削除対象分類");
        insertDetail(active, 1, (short) 1, instructor, "オリエンテーション", (short) 1, today);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   ", "long", "null", "missing"})
    void invalidScheduleNameDoesNotPartiallyUpdate(String mode) throws Exception {
        var before = jdbc.queryForMap("select * from schedule where schedule_id=?", active);
        var request = new HashMap<>(unchangedRequest(2));
        if (mode.equals("missing")) request.remove("scheduleName");
        else request.put("scheduleName", mode.equals("null") ? null : mode.equals("long") ? "名".repeat(41) : mode);
        mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(jdbc.queryForMap("select * from schedule where schedule_id=?", active)).isEqualTo(before);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void editableEmployeeCanRenameWithOtherBasicFieldsUnchanged(boolean asTrainee) throws Exception {
        if (asTrainee) jdbc.update("update schedule_can_edit set can_edit=1 where schedule_id=? and syain_id=?", active, trainee);
        var before = jdbc.queryForMap("select * from schedule where schedule_id=?", active);
        var detailsBefore = jdbc.queryForList("select * from schedule_details where schedule_id=?", active);
        var request = new HashMap<>(unchangedRequest(2));
        if (asTrainee) request.put("permissions", List.of(permission(instructor, true, 2), permission(trainee, true, 4), permission(secondTrainee, false, 0)));
        request.put("scheduleName", "名".repeat(40));
        request.put("scheduleId", "ILLEGAL"); request.put("startDate", "2000-01-01");
        request.put("endDate", "2099-01-01"); request.put("excludeHolidays", true);
        mvc.perform(put("/api/schedules/{id}", active).with(user(asTrainee ? trainee : instructor).roles(asTrainee ? "TRAINEE" : "INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduleName").value("名".repeat(40)));
        var after = jdbc.queryForMap("select * from schedule where schedule_id=?", active);
        for (String column : List.of("schedule_id", "start_date", "end_date", "nohol_flg")) assertThat(after.get(column)).isEqualTo(before.get(column));
        assertThat(jdbc.queryForList("select * from schedule_details where schedule_id=?", active)).isEqualTo(detailsBefore);
    }

    @Test
    void optionalContentAndTaskValidation() throws Exception {
        for (String content : new String[] { null, "", "   \t" }) {
            String id = "N" + UUID.randomUUID().toString().substring(0, 8);
            var request = new HashMap<>(validCreateRequest(id));
            var row = new HashMap<>(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "x", false));
            row.put("content", content);
            request.put("details", List.of(row));
            mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.details[0].content").isEmpty());
            assertThat(jdbc.queryForObject("select schedule_content from schedule_details where schedule_id = ?",
                    String.class, id)).isNull();
            mvc.perform(get("/api/schedules/{id}", id).with(user(instructor).roles("INSTRUCTOR")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.details[0].content").isEmpty());
            var badCreate = new HashMap<>(validCreateRequest("BADTASK"));
            row.put("taskFlg", true);
            badCreate.put("details", List.of(row));
            mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(badCreate)))
                    .andExpect(status().isBadRequest());
            var update = new HashMap<>(unchangedRequest(2));
            row.put("updateCount", 0);
            update.put("details", List.of(row));
            mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(update)))
                    .andExpect(status().isBadRequest());
        }
        var request = new HashMap<>(unchangedRequest(2));
        var row = new HashMap<>(detail(1, 1, instructor, "x", false, 0));
        row.put("content", null);
        request.put("details", List.of(row));
        mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.details[0].content").isEmpty());
        var next = new HashMap<>(unchangedRequest(3));
        next.put("details", List.of(detail(1, 1, instructor, "追加入力", true, 1)));
        mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(next)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.details[0].content").value("追加入力"));
    }

    @Test
    void traineeGetsOnlyAccessibleSchedulesInPinnedCurrentAndIdOrder() throws Exception {
        mvc.perform(get("/api/schedules").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].scheduleId").value(pinned))
                .andExpect(jsonPath("$[0].pinned").value(true))
                .andExpect(jsonPath("$[0].canEdit").value(true))
                .andExpect(jsonPath("$[0].scheduleUpdateCount").value(3))
                .andExpect(jsonPath("$[0].permissionUpdateCount").value(1))
                .andExpect(jsonPath("$[1].scheduleId").value(active))
                .andExpect(jsonPath("$[1].pinned").value(false))
                .andExpect(jsonPath("$[1].canEdit").value(false))
                .andExpect(jsonPath("$[2].scheduleId").value(inactive))
                .andExpect(jsonPath("$[*].scheduleId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(inaccessible))));
    }

    @Test
    void instructorGetsSchedulesGrantedToTheInstructor() throws Exception {
        mvc.perform(get("/api/schedules").with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleId").value(active))
                .andExpect(jsonPath("$[0].canEdit").value(true))
                .andExpect(jsonPath("$[0].pinned").value(false));
    }

    @Test
    void traineeCanPinOwnReadOnlyScheduleAndListIsResortedWithoutChangingOthersOrScheduleVersion() throws Exception {
        mvc.perform(put("/api/schedules/{scheduleId}/pin", active)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(pinRequest(true, 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(active))
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.permissionUpdateCount").value(5));

        assertThat(jdbc.queryForObject("select pin_flg from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, trainee)).isOne();
        assertThat(jdbc.queryForObject("select update_count from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, active, trainee)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select pin_flg from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, instructor)).isZero();
        assertThat(jdbc.queryForObject("select update_count from schedule where schedule_id = ?",
                Integer.class, active)).isEqualTo(2);
        var audit = jdbc.queryForMap("select screen_id, update_user_cd from schedule_can_edit where schedule_id = ? and syain_id = ?",
                active, trainee);
        assertThat(audit.get("screen_id")).isEqualTo("SCD01");
        assertThat(audit.get("update_user_cd")).isEqualTo(trainee);

        mvc.perform(get("/api/schedules").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value(active))
                .andExpect(jsonPath("$[1].scheduleId").value(pinned));
    }

    @Test
    void pinnedScheduleCanBeUnpinnedAndPermissionVersionIsIncremented() throws Exception {
        mvc.perform(put("/api/schedules/{scheduleId}/pin", pinned)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(pinRequest(false, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(false))
                .andExpect(jsonPath("$.permissionUpdateCount").value(2));
        assertThat(jdbc.queryForObject("select pin_flg from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, pinned, trainee)).isZero();
    }

    @Test
    void instructorWithReadOnlyPermissionCanChangeOwnPin() throws Exception {
        jdbc.update("update schedule_can_edit set can_edit = 0 where schedule_id = ? and syain_id = ?", active, instructor);
        mvc.perform(put("/api/schedules/{scheduleId}/pin", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(pinRequest(true, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void thirdPinIsRejectedButUnpinningMakesAnotherScheduleAvailable() throws Exception {
        jdbc.update("update schedule_can_edit set pin_flg = 1 where schedule_id = ? and syain_id = ?", active, trainee);

        mvc.perform(put("/api/schedules/{scheduleId}/pin", inactive)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(pinRequest(true, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("ピン留めできるスケジュールは2件までです。"));
        assertThat(jdbc.queryForObject("select pin_flg from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, inactive, trainee)).isZero();

        mvc.perform(put("/api/schedules/{scheduleId}/pin", active)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(pinRequest(false, 4))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/schedules/{scheduleId}/pin", inactive)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(pinRequest(true, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void pinUpdateReturnsAuthenticationAuthorizationNotFoundAndConflictErrors() throws Exception {
        var body = objectMapper.writeValueAsBytes(pinRequest(true, 0));
        mvc.perform(put("/api/schedules/{scheduleId}/pin", inactive).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/schedules/{scheduleId}/pin", inactive)
                        .with(user(trainee).roles("TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/schedules/{scheduleId}/pin", inaccessible)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(put("/api/schedules/{scheduleId}/pin", "NONE")
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
        mvc.perform(put("/api/schedules/{scheduleId}/pin", active)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(pinRequest(true, 999))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_UPDATE_CONFLICT"));
    }

    @Test
    void unauthenticatedRequestIs401() throws Exception {
        mvc.perform(get("/api/schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void instructorCanGetScheduleDetailWithEditPermission() throws Exception {
        mvc.perform(get("/api/schedules/{scheduleId}", active).with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(active))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.pinned").value(false))
                .andExpect(jsonPath("$.scheduleUpdateCount").value(2))
                .andExpect(jsonPath("$.permissionUpdateCount").value(2))
                .andExpect(jsonPath("$.permissions.length()").value(3))
                .andExpect(jsonPath("$.categories[0].categoryName").value("Java研修"))
                .andExpect(jsonPath("$.details[0].scheduleDateNo").value(1))
                .andExpect(jsonPath("$.details[0].instructorEmployeeId").value(instructor))
                .andExpect(jsonPath("$.details[0].taskFlg").value(true));
    }

    @Test
    void scheduleDetailIdentifiesReviewersAndCommentAuthorsForTheSelectedSchedule() throws Exception {
        insertPermission(active, secondInstructor, (short) 1, (short) 0, 0);
        insertIssueSubmission(active, 1, trainee, secondTrainee);
        insertIssueComment(active, 1, trainee, 1, secondInstructor);

        mvc.perform(get("/api/schedules/{scheduleId}", active).with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].usedAsReviewer"
                        .formatted(secondTrainee)).value(true))
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].hasComments"
                        .formatted(secondTrainee)).value(false))
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].usedAsReviewer"
                        .formatted(secondInstructor)).value(false))
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].hasComments"
                        .formatted(secondInstructor)).value(true));
    }

    @Test
    void traineeCanGetScheduleDetailInReadOnlyPermission() throws Exception {
        mvc.perform(get("/api/schedules/{scheduleId}", active).with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(active))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.permissionUpdateCount").value(4))
                .andExpect(jsonPath("$.details[0].content").value("オリエンテーション"));
    }

    @Test
    void traineeCanGetScheduleDetailWithEditPermission() throws Exception {
        mvc.perform(get("/api/schedules/{scheduleId}", pinned).with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(pinned))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.permissionUpdateCount").value(1));
    }

    @Test
    void userWithoutSchedulePermissionCannotGetDetail() throws Exception {
        mvc.perform(get("/api/schedules/{scheduleId}", inaccessible).with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedDetailRequestIs401() throws Exception {
        mvc.perform(get("/api/schedules/{scheduleId}", active))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedUserCanGetJapaneseNonWorkingDates() throws Exception {
        mvc.perform(get("/api/schedules/non-working-dates")
                        .param("startDate", "2026-10-05").param("endDate", "2026-10-16")
                        .with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates.length()").value(3))
                .andExpect(jsonPath("$.dates[0]").value("2026-10-10"))
                .andExpect(jsonPath("$.dates[1]").value("2026-10-11"))
                .andExpect(jsonPath("$.dates[2]").value("2026-10-12"));
    }

    @Test
    void authenticatedTraineeCanGetScheduleMemberOptionsWithoutUsingMemberManagementApi() throws Exception {
        mvc.perform(get("/api/schedules/member-options").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.syainId == '%s')].syainKbn".formatted(trainee)).value(1))
                .andExpect(jsonPath("$[?(@.syainId == '%s')].syainKbn".formatted(instructor)).value(2))
                // 対象スケジュールへ割当済みでも、画面側で現在値を基準に候補判定できるよう返す。
                .andExpect(jsonPath("$[?(@.syainId == '%s')].syainKbn".formatted(secondTrainee)).value(1));

        mvc.perform(get("/api/schedules/member-options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void instructorCanCreateScheduleAtomically() throws Exception {
        String id = "N" + UUID.randomUUID().toString().substring(0, 6);
        var request = createRequest(id,
                List.of(createPermission(instructor, true), createPermission(secondInstructor, true),
                        createPermission(trainee, false)),
                List.of(createCategory(1, "新規分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "新規内容", true)));

        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").value(id))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.permissions.length()").value(3))
                .andExpect(jsonPath("$.categories[0].categoryName").value("新規分類"))
                .andExpect(jsonPath("$.details[0].content").value("新規内容"));
        assertThat(jdbc.queryForObject("select count(*) from schedule where schedule_id = ?", Integer.class, id)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from issue_submission_statuses where schedule_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from schedule_can_edit where schedule_id = ? and pin_flg = 1",
                Integer.class, id)).isZero();
    }

    @Test
    void createValidatesWeekendsJapaneseHolidaysAndExclusionFlag() throws Exception {
        var rejectedDates = Map.of(
                "HOLSAT", LocalDate.of(2026, 10, 10),
                "HOLSUN", LocalDate.of(2026, 10, 11),
                "HOLSPORT", LocalDate.of(2026, 10, 12),
                "HOLSUB", LocalDate.of(2026, 5, 6));
        for (var entry : rejectedDates.entrySet()) {
            mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(
                                    createRequestForDate(entry.getKey(), entry.getValue(), true))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(schedulesCount(entry.getKey())).isZero();
        }

        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createRequestForDate(
                                "HOLWEEK", LocalDate.of(2026, 10, 13), true))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createRequestForDate(
                                "HOLALLOW", LocalDate.of(2026, 10, 12), false))))
                .andExpect(status().isCreated());
    }

    @Test
    void createRequiresAuthenticationAndCsrfButAllowsTraineeAndGrantsActorEditPermission() throws Exception {
        var body = objectMapper.writeValueAsBytes(validCreateRequest("AUTHCHECK"));
        mvc.perform(post("/api/schedules").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/schedules").with(user(trainee).roles("TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        var traineeRequest = createRequest("TRAINEW",
                List.of(createPermission(instructor, true)),
                List.of(createCategory(1, "新人登録分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "新人登録内容", false)));
        mvc.perform(post("/api/schedules").with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(traineeRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").value("TRAINEW"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].canEdit".formatted(trainee)).value(true));

        assertThat(jdbc.queryForObject("""
                select can_edit from schedule_can_edit where schedule_id = 'TRAINEW' and syain_id = ?
                """, Short.class, trainee)).isOne();

        var update = updateRequest(0,
                List.of(permission(instructor, true, 0), permission(trainee, true, 0)),
                List.of(), List.of(category(1, "新人登録分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "新人登録後の更新", false, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", "TRAINEW")
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.details[0].content").value("新人登録後の更新"));
    }

    @Test
    void duplicateScheduleIdReturns409() throws Exception {
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validCreateRequest(active))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_DUPLICATE"));
    }

    @Test
    void createRejectsNoTraineeDuplicateAndMissingMembers() throws Exception {
        var noTrainee = createRequest("NONEW",
                List.of(createPermission(instructor, true), createPermission(secondInstructor, true)),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(noTrainee)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("新人は1人以上登録する必要があります。"));

        var duplicate = createRequest("DUPMEM",
                List.of(createPermission(instructor, true), createPermission(trainee, false), createPermission(trainee, true)),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(duplicate)))
                .andExpect(status().isBadRequest());

        var missing = createRequest("MISSMEM",
                List.of(createPermission(instructor, true), createPermission(trainee, false), createPermission("UNKNOWN", false)),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(missing)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRequiresAtLeastOneEditableInstructorUsingStoredEmployeeType() throws Exception {
        var readOnlyInstructor = createRequest("NOEDITINS",
                List.of(createPermission(instructor, false), createPermission(trainee, true)),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(readOnlyInstructor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("編集権限を持つ講師を1人以上登録する必要があります。"));
        assertThat(schedulesCount("NOEDITINS")).isZero();

        var editableTraineeOnly = createRequest("EDITNEW",
                List.of(createPermission(trainee, true)),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(editableTraineeOnly)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("編集権限を持つ講師を1人以上登録する必要があります。"));
        assertThat(schedulesCount("EDITNEW")).isZero();

        var fakeInstructor = new HashMap<>(createPermission(trainee, true));
        fakeInstructor.put("syainKbn", 2);
        var spoofedType = createRequest("FAKEINS",
                List.of(fakeInstructor),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(spoofedType)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("編集権限を持つ講師を1人以上登録する必要があります。"));
        assertThat(schedulesCount("FAKEINS")).isZero();
    }

    @Test
    void createRequiresCreatorsEditPermission() throws Exception {
        var request = createRequest("NOACTOR",
                List.of(createPermission(instructor, false), createPermission(trainee, false)),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Java研修", "  Java研修  "})
    void updateRejectsDuplicateCategoryNamesWithoutPartialChanges(String duplicateName) throws Exception {
        insertIssueSubmission(active, 1, trainee, instructor);
        insertIssueComment(active, 1, trainee, 1L, trainee);
        var tables = List.of("schedule", "schedule_can_edit", "schedule_bunrui", "schedule_details",
                "issue_submission_statuses", "issue_submission_statuses_comment");
        var before = new HashMap<String, Object>();
        for (var table : tables) before.put(table, jdbc.queryForList(
                "select * from " + table + " where schedule_id=? order by 1,2", active));
        var request = new HashMap<>(unchangedRequest(2));
        request.put("scheduleName", "重複時はこの名称も保存しない");
        request.put("categories", List.of(category(1, "Java研修", 0), category(2, duplicateName, 0)));
        request.put("permissions", List.of(permission(instructor, true, 2), permission(trainee, true, 4), permission(secondTrainee, false, 0)));
        request.put("details", List.of(detail(1, 1, instructor, "この内容も保存しない", true, 0)));
        mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        for (var table : tables) assertThat(jdbc.queryForList(
                "select * from " + table + " where schedule_id=? order by 1,2", active))
                .as(table).isEqualTo(before.get(table));
    }

    @Test
    void updateAllowsDistinctCategoryNamesAndSameNameInAnotherSchedule() throws Exception {
        insertCategory(pinned, (short) 1, "別スケジュールと同名");
        var request = new HashMap<>(unchangedRequest(2));
        request.put("scheduleName", "分類更新成功");
        request.put("categories", List.of(category(1, "Java研修", 0), category(2, "変更した分類", 0),
                category(3, "別スケジュールと同名", 0), category(4, "java研修", 0),
                category(5, "é分類", 0), category(6, "e\u0301分類", 0)));
        request.put("details", List.of(detail(1, 3, instructor, "分類付け替え後の内容", true, 0)));
        mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduleName").value("分類更新成功"));
        assertThat(jdbc.queryForObject("select count(*) from schedule_bunrui where schedule_id=?", Integer.class, active)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select bnrui_name from schedule_bunrui where schedule_id=? and bnrui_no=2", String.class, active)).isEqualTo("変更した分類");
        assertThat(jdbc.queryForObject("select bnrui_name from schedule_bunrui where schedule_id=? and bnrui_no=1", String.class, pinned)).isEqualTo("別スケジュールと同名");
        assertThat(jdbc.queryForObject("select bnrui_no from schedule_details where schedule_id=? and schedule_date_no=1", Integer.class, active)).isEqualTo(3);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"分類A", "  分類A  "})
    void createStillRejectsDuplicateCategoryNamesAfterTrimming(String duplicateName) throws Exception {
        var request = createRequest("DUPNAME",
                List.of(createPermission(instructor, true), createPermission(trainee, false)),
                List.of(createCategory(1, "分類A"), createCategory(2, duplicateName)),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
        assertThat(schedulesCount("DUPNAME")).isZero();
    }

    @Test
    void createRejectsInvalidAndDuplicateCategories() throws Exception {
        var invalidReference = createRequest("BADCAT",
                List.of(createPermission(instructor, true), createPermission(trainee, false)),
                List.of(createCategory(1, "分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 2, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(invalidReference)))
                .andExpect(status().isBadRequest());

        var duplicate = createRequest("DUPCAT",
                List.of(createPermission(instructor, true), createPermission(trainee, false)),
                List.of(createCategory(1, "分類A"), createCategory(1, "分類B")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(duplicate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsInvalidInstructorOutOfRangeDateAndDuplicateDateNumberWithoutPartialInsert() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        var invalidInstructor = createRequest("BADINST",
                List.of(createPermission(instructor, true), createPermission(trainee, false)),
                List.of(createCategory(1, "分類")), List.of(createDetail(1, today, 1, trainee, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(invalidInstructor)))
                .andExpect(status().isBadRequest());
        assertThat(schedulesCount("BADINST")).isZero();

        var outside = createRequest("OUTSIDE",
                List.of(createPermission(instructor, true), createPermission(trainee, false)),
                List.of(createCategory(1, "分類")), List.of(createDetail(1, today.plusDays(2), 1, instructor, "内容", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(outside)))
                .andExpect(status().isBadRequest());
        assertThat(schedulesCount("OUTSIDE")).isZero();

        var duplicateNo = createRequest("DUPDATE",
                List.of(createPermission(instructor, true), createPermission(trainee, false)),
                List.of(createCategory(1, "分類")), List.of(createDetail(1, today, 1, instructor, "内容1", false),
                        createDetail(1, today.plusDays(1), 1, instructor, "内容2", false)));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(duplicateNo)))
                .andExpect(status().isBadRequest());
        assertThat(schedulesCount("DUPDATE")).isZero();
    }

    @Test
    void editableUserCanUpdatePermissionsCategoriesAndDetailsWithoutChangingBasicInformation() throws Exception {
        jdbc.update("update schedule_can_edit set pin_flg = 1 where schedule_id = ? and syain_id = ?", active, trainee);
        var before = jdbc.queryForMap("select * from schedule where schedule_id = ?", active);
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, true, 4), permission(secondInstructor, false, 0)),
                List.of(deletedPermission(secondTrainee, 0)),
                List.of(category(1, "Java研修改", 0), category(3, "追加分類", 0)),
                List.of(deletedCategory(2, 0)),
                List.of(detail(1, 3, secondInstructor, "更新後の内容", false, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleUpdateCount").value(3))
                .andExpect(jsonPath("$.permissions.length()").value(3))
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].canEdit".formatted(trainee)).value(true))
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')]".formatted(secondTrainee)).isEmpty())
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[?(@.categoryNo == 1)].categoryName").value("Java研修改"))
                .andExpect(jsonPath("$.categories[?(@.categoryNo == 3)].categoryName").value("追加分類"))
                .andExpect(jsonPath("$.details[0].categoryNo").value(3))
                .andExpect(jsonPath("$.details[0].instructorEmployeeId").value(secondInstructor))
                .andExpect(jsonPath("$.details[0].content").value("更新後の内容"))
                .andExpect(jsonPath("$.details[0].taskFlg").value(false));

        var after = jdbc.queryForMap("select * from schedule where schedule_id = ?", active);
        assertThat(after.get("schedule_name")).isEqualTo(before.get("schedule_name"));
        assertThat(after.get("start_date")).isEqualTo(before.get("start_date"));
        assertThat(after.get("end_date")).isEqualTo(before.get("end_date"));
        assertThat(after.get("nohol_flg")).isEqualTo(before.get("nohol_flg"));
        assertThat(jdbc.queryForObject("select pin_flg from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, trainee)).isOne();
        assertThat(jdbc.queryForObject("select can_edit from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, trainee)).isOne();
        assertThat(jdbc.queryForObject("select pin_flg from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, secondInstructor)).isZero();

        mvc.perform(get("/api/schedules").with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.scheduleId == '%s')].canEdit".formatted(active)).value(true));
        mvc.perform(get("/api/schedules/{scheduleId}", active).with(user(trainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void deletedPermissionCanBeLoadedAsMemberOptionAndReaddedWithEditPermission() throws Exception {
        insertPermission(pinned, secondTrainee, (short) 0, (short) 0, 0);
        var removeRequest = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4)),
                List.of(deletedPermission(secondTrainee, 0)),
                List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(removeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')]".formatted(secondTrainee)).isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, active, secondTrainee)).isZero();

        mvc.perform(get("/api/schedules/member-options").with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.syainId == '%s')].syainKbn".formatted(secondTrainee)).value(1));

        var readdRequest = updateRequest(3,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4),
                        permission(secondTrainee, true, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(readdRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].canEdit".formatted(secondTrainee)).value(true));

        assertThat(jdbc.queryForObject("select can_edit from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, secondTrainee)).isOne();
        assertThat(jdbc.queryForObject("select can_edit from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, pinned, secondTrainee)).isZero();
        mvc.perform(get("/api/schedules/{scheduleId}", active)
                        .with(user(secondTrainee).roles("TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')].canEdit".formatted(secondTrainee)).value(true));
    }

    @Test
    void editableTraineeCanUpdateSchedule() throws Exception {
        jdbc.update("update schedule_can_edit set can_edit = 1 where schedule_id = ? and syain_id = ?", active, trainee);
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, true, 4),
                        permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "新人による更新", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.details[0].content").value("新人による更新"));

        assertThat(jdbc.queryForObject("""
                select schedule_content from schedule_details where schedule_id = ? and schedule_date_no = 1
                """, String.class, active)).isEqualTo("新人による更新");
    }

    @Test
    void reviewerInstructorCannotBeRemovedAndNoOtherChangesAreSaved() throws Exception {
        insertPermission(active, secondInstructor, (short) 1, (short) 0, 0);
        insertIssueSubmission(active, 1, trainee, secondInstructor);
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4),
                        permission(secondTrainee, false, 0)),
                List.of(deletedPermission(secondInstructor, 0)),
                List.of(category(1, "保存されない分類", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "保存されない内容", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(
                        "進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません"));

        assertThat(jdbc.queryForObject(
                "select count(*) from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, active, secondInstructor)).isOne();
        assertThat(jdbc.queryForObject(
                "select bnrui_name from schedule_bunrui where schedule_id = ? and bnrui_no = 1",
                String.class, active)).isEqualTo("Java研修");
        assertThat(jdbc.queryForObject(
                "select schedule_content from schedule_details where schedule_id = ? and schedule_date_no = 1",
                String.class, active)).isEqualTo("オリエンテーション");
    }

    @Test
    void reviewerTraineeCannotBeRemoved() throws Exception {
        insertIssueSubmission(active, 1, trainee, secondTrainee);
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4)),
                List.of(deletedPermission(secondTrainee, 0)),
                List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません"));
        assertThat(jdbc.queryForObject(
                "select count(*) from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, active, secondTrainee)).isOne();
    }

    @Test
    void instructorAndTraineeCommentAuthorsCannotBeRemoved() throws Exception {
        insertPermission(active, secondInstructor, (short) 0, (short) 0, 0);
        insertIssueSubmission(active, 1, trainee, null);
        insertIssueComment(active, 1, trainee, 1, secondInstructor);

        var removeInstructor = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4),
                        permission(secondTrainee, false, 0)),
                List.of(deletedPermission(secondInstructor, 0)),
                List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(removeInstructor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません"));

        jdbc.update("""
                update issue_submission_statuses_comment set comment_syain_id = ?
                where schedule_id = ? and schedule_date_no = 1 and sinjin_syain_id = ? and comment_no = 1
                """, secondTrainee, active, trainee);
        var removeTrainee = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4),
                        permission(secondInstructor, false, 0)),
                List.of(deletedPermission(secondTrainee, 0)),
                List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(removeTrainee)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません"));

        assertThat(jdbc.queryForObject(
                "select count(*) from schedule_can_edit where schedule_id = ? and syain_id in (?, ?)",
                Integer.class, active, secondInstructor, secondTrainee)).isEqualTo(2);
    }

    @Test
    void reviewerCannotBeDowngradedButCommentAuthorCan() throws Exception {
        jdbc.update("update schedule_can_edit set can_edit = 1 where schedule_id = ? and syain_id = ?",
                active, secondTrainee);
        insertIssueSubmission(active, 1, trainee, secondTrainee);
        var downgradeReviewer = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4),
                        permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "保存されない内容", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(downgradeReviewer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(
                        "進捗・課題管理でレビュー者として登録されているため参照のみに変更できません"));
        assertThat(jdbc.queryForObject(
                "select can_edit from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, secondTrainee)).isOne();
        assertThat(jdbc.queryForObject(
                "select schedule_content from schedule_details where schedule_id = ? and schedule_date_no = 1",
                String.class, active)).isEqualTo("オリエンテーション");

        jdbc.update("update issue_submission_statuses set koshi_syain_id = null where schedule_id = ?", active);
        insertIssueComment(active, 1, trainee, 1, secondTrainee);
        var downgradeCommentAuthor = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4),
                        permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(downgradeCommentAuthor)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "select can_edit from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, secondTrainee)).isZero();
    }

    @Test
    void progressUsageOnAnotherScheduleDoesNotRestrictPermissionRemoval() throws Exception {
        insertPermission(pinned, secondTrainee, (short) 1, (short) 0, 0);
        insertCategory(pinned, (short) 1, "別スケジュール分類");
        insertDetail(pinned, 1, (short) 1, instructor, "別スケジュール内容", (short) 1,
                LocalDate.now(BUSINESS_ZONE).minusDays(2));
        insertIssueSubmission(pinned, 1, trainee, secondTrainee);
        insertIssueComment(pinned, 1, trainee, 1, secondTrainee);

        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4)),
                List.of(deletedPermission(secondTrainee, 0)),
                List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select count(*) from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, active, secondTrainee)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, pinned, secondTrainee)).isOne();
    }

    @Test
    void finalTraineeCannotBeRemovedAndAllChangesAreRolledBack() throws Exception {
        jdbc.update("delete from schedule_can_edit where schedule_id = ? and syain_id = ?", active, secondTrainee);
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2)),
                List.of(deletedPermission(trainee, 4)),
                List.of(category(1, "ロールバック対象", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "保存されない内容", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("新人は1人以上登録する必要があります。"));

        assertThat(jdbc.queryForObject("select count(*) from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, active, trainee)).isOne();
        assertThat(jdbc.queryForObject("select bnrui_name from schedule_bunrui where schedule_id = ? and bnrui_no = 1",
                String.class, active)).isEqualTo("Java研修");
        assertThat(jdbc.queryForObject("select schedule_content from schedule_details where schedule_id = ? and schedule_date_no = 1",
                String.class, active)).isEqualTo("オリエンテーション");
    }

    @Test
    void finalEditableInstructorCannotBeRemovedOrChangedToReadOnly() throws Exception {
        jdbc.update("update schedule_can_edit set can_edit = 1 where schedule_id = ? and syain_id = ?", active, trainee);
        var removeRequest = updateRequest(2,
                List.of(permission(trainee, true, 4), permission(secondTrainee, false, 0)),
                List.of(deletedPermission(instructor, 2)),
                List.of(category(1, "保存されない分類", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "保存されない内容", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(removeRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("編集権限を持つ講師を1人以上登録する必要があります。"));

        var readOnlyRequest = updateRequest(2,
                List.of(permission(instructor, false, 2), permission(trainee, true, 4),
                        permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "保存されない分類", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "保存されない内容", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(readOnlyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("編集権限を持つ講師を1人以上登録する必要があります。"));

        assertThat(jdbc.queryForObject("select can_edit from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, instructor)).isOne();
        assertThat(jdbc.queryForObject("select bnrui_name from schedule_bunrui where schedule_id = ? and bnrui_no = 1",
                String.class, active)).isEqualTo("Java研修");
        assertThat(jdbc.queryForObject("select schedule_content from schedule_details where schedule_id = ? and schedule_date_no = 1",
                String.class, active)).isEqualTo("オリエンテーション");
    }

    @Test
    void oneOfTwoEditableInstructorsCanBeRemoved() throws Exception {
        insertPermission(active, secondInstructor, (short) 1, (short) 0, 0);
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4),
                        permission(secondTrainee, false, 0)),
                List.of(deletedPermission(secondInstructor, 0)),
                List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@.employeeId == '%s')]".formatted(secondInstructor)).isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Integer.class, active, secondInstructor)).isZero();
    }

    @Test
    void clientSuppliedEmployeeTypeCannotSatisfyTraineeRequirement() throws Exception {
        var fakeTrainee = Map.<String, Object>of(
                "employeeId", secondInstructor,
                "canEdit", false,
                "updateCount", 0,
                "syainKbn", 1);
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), fakeTrainee),
                List.of(deletedPermission(trainee, 4), deletedPermission(secondTrainee, 0)),
                List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("新人は1人以上登録する必要があります。"));
    }

    @Test
    void categoryReferencedByFinalDetailsCannotBeDeleted() throws Exception {
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4), permission(secondTrainee, false, 0)),
                List.of(), List.of(category(2, "削除対象分類", 0)), List.of(deletedCategory(1, 0)),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("この分類はスケジュール内容で使用されているため削除できません。"));
        assertThat(jdbc.queryForObject("select count(*) from schedule_bunrui where schedule_id = ? and bnrui_no = 1",
                Integer.class, active)).isOne();
    }

    @Test
    void categoryCanBeDeletedAfterAllFinalDetailsAreReassignedInSameRequest() throws Exception {
        var request = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4), permission(secondTrainee, false, 0)),
                List.of(), List.of(category(2, "削除対象分類", 0)), List.of(deletedCategory(1, 0)),
                List.of(detail(1, 2, instructor, "分類変更後", true, 0)));

        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].categoryNo").value(2))
                .andExpect(jsonPath("$.details[0].categoryNo").value(2));
    }

    @Test
    void readOnlyAndMissingPermissionUsersCannotUpdate() throws Exception {
        var request = unchangedRequest(2);
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(trainee).roles("TRAINEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(put("/api/schedules/{scheduleId}", inaccessible)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void updateReturns401And404AsAppropriate() throws Exception {
        var body = objectMapper.writeValueAsBytes(unchangedRequest(2));
        mvc.perform(put("/api/schedules/{scheduleId}", active).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/schedules/{scheduleId}", "NONE")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    void updateCountConflictReturns409() throws Exception {
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(unchangedRequest(999))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_UPDATE_CONFLICT"));

        var childConflict = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4), permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 999), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(childConflict)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_UPDATE_CONFLICT"));
    }

    @Test
    void invalidCategoryAndInstructorAreRejectedAndPermissionChangeRollsBack() throws Exception {
        var invalidCategory = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, true, 4), permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 99, instructor, "不正分類", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(invalidCategory)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(jdbc.queryForObject("select can_edit from schedule_can_edit where schedule_id = ? and syain_id = ?",
                Short.class, active, trainee)).isZero();

        var invalidInstructor = updateRequest(2,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4), permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, trainee, "不正講師", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(invalidInstructor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operatorCannotRemoveOwnEditPermission() throws Exception {
        var request = updateRequest(2,
                List.of(permission(instructor, false, 2), permission(trainee, false, 4), permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
        mvc.perform(put("/api/schedules/{scheduleId}", active)
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,false,true", "2,false,false", "1,true,false", "3,true,false", "0,false,true"})
    void taskRemovalChecksAllTrainees(short statusCode, boolean comment, boolean allowed) throws Exception {
        if (statusCode != 0) {
            insertIssueSubmission(active, 1, secondTrainee, null);
            jdbc.update("update issue_submission_statuses set task_status_kbn = ? where schedule_id = ?", statusCode, active);
        }
        if (comment) insertIssueComment(active, 1, secondTrainee, 1, instructor);
        mvc.perform(get("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details[0].taskRemovalBlocked").value(!allowed));
        var beforeDetails = jdbc.queryForList("select * from schedule_details where schedule_id = ?", active);
        var beforeSubmissions = jdbc.queryForList("select * from issue_submission_statuses where schedule_id = ?", active);
        var beforeComments = jdbc.queryForList("select * from issue_submission_statuses_comment where schedule_id = ?", active);
        var request = new HashMap<>(unchangedRequest(2));
        request.put("details", List.of(detail(1, 1, instructor, "オリエンテーション", false, 0)));
        var result = mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)));
        if (allowed) result.andExpect(status().isOk());
        else {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("未提出以外の提出状況、またはコメントが登録されているため、課題を解除できません。"));
            assertThat(jdbc.queryForList("select * from schedule_details where schedule_id = ?", active)).isEqualTo(beforeDetails);
        }
        assertThat(jdbc.queryForList("select * from issue_submission_statuses where schedule_id = ?", active)).isEqualTo(beforeSubmissions);
        assertThat(jdbc.queryForList("select * from issue_submission_statuses_comment where schedule_id = ?", active)).isEqualTo(beforeComments);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"blank", "nullFlag", "omitted", "blankTrue"})
    void taskRemovalCannotBypassGuard(String mode) throws Exception {
        insertIssueSubmission(active, 1, trainee, null);
        insertIssueComment(active, 1, trainee, 1, instructor);
        var request = new HashMap<>(unchangedRequest(2));
        var row = new HashMap<>(detail(1, 1, instructor, "オリエンテーション", true, 0));
        if (mode.startsWith("blank")) { row.put("content", "   "); row.put("taskFlg", mode.equals("blankTrue")); }
        if (mode.equals("nullFlag")) row.put("taskFlg", null);
        request.put("details", mode.equals("omitted") ? List.of() : List.of(row));
        mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                        "未提出以外の提出状況、またはコメントが登録されているため、課題を解除できません。"));
        assertThat(jdbc.queryForObject("select schedule_content from schedule_details where schedule_id = ?", String.class, active))
                .isEqualTo("オリエンテーション");
        assertThat(jdbc.queryForObject("select task_flg from schedule_details where schedule_id = ?", Short.class, active)).isEqualTo((short) 1);
    }

    @Test
    void otherDatesAndSchedulesDoNotBlockTaskRemoval() throws Exception {
        insertDetail(active, 2, (short) 1, instructor, "別日", (short) 1, LocalDate.now(BUSINESS_ZONE).plusDays(1));
        insertCategory(inaccessible, (short) 1, "別研修");
        insertDetail(inaccessible, 1, (short) 1, instructor, "別研修", (short) 1, LocalDate.now(BUSINESS_ZONE));
        insertIssueSubmission(active, 2, trainee, null);
        insertIssueSubmission(inaccessible, 1, trainee, null);
        insertIssueComment(active, 2, trainee, 1, instructor);
        jdbc.update("update issue_submission_statuses set task_status_kbn = 3 where schedule_id in (?, ?)", active, inaccessible);
        var request = new HashMap<>(unchangedRequest(2));
        request.put("details", List.of(detail(1, 1, instructor, "", false, 0), detail(2, 1, instructor, "別日", true, 0)));
        mvc.perform(put("/api/schedules/{id}", active).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.details[0].content").isEmpty())
                .andExpect(jsonPath("$.details[1].taskRemovalBlocked").value(true));
    }

    @Test
    void editableInstructorCanDeleteScheduleAndAllOwnedChildren() throws Exception {
        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "2")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from schedule_details where schedule_id = ?", Integer.class, active)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from schedule_bunrui where schedule_id = ?", Integer.class, active)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from schedule_can_edit where schedule_id = ?", Integer.class, active)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from schedule where schedule_id = ?", Integer.class, active)).isZero();
    }

    @Test
    void editableTraineeCanDeleteSchedule() throws Exception {
        jdbc.update("update schedule_can_edit set can_edit = 1 where schedule_id = ? and syain_id = ?", active, trainee);

        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "2")
                        .with(user(trainee).roles("TRAINEE")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(schedulesCount(active)).isZero();
    }

    @Test
    void deleteRequiresAuthenticationCsrfEditPermissionAndExistingSchedule() throws Exception {
        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "2").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "2")
                        .with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "2")
                        .with(user(trainee).roles("TRAINEE")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        jdbc.update("update schedule_can_edit set can_edit = 0 where schedule_id = ? and syain_id = ?", active, instructor);
        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "2")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(delete("/api/schedules/{scheduleId}", inaccessible)
                        .param("scheduleUpdateCount", "0")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(delete("/api/schedules/{scheduleId}", "NONE")
                        .param("scheduleUpdateCount", "0")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    void staleDeleteReturns409AndKeepsSchedule() throws Exception {
        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "999")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_UPDATE_CONFLICT"));
        assertThat(schedulesCount(active)).isOne();
    }

    @Test
    void issueSubmissionsPreventDeleteAndKeepEveryRelatedRow() throws Exception {
        jdbc.update("""
                insert into issue_submission_statuses (schedule_id, schedule_date_no, sinjin_syain_id,
                    task_status_kbn, screen_id, update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, 1, ?, 1, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, active, trainee);

        mvc.perform(delete("/api/schedules/{scheduleId}", active)
                        .param("scheduleUpdateCount", "2")
                        .with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("課題提出状況が登録されているため、このスケジュールは削除できません。"));

        assertThat(schedulesCount(active)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from schedule_can_edit where schedule_id = ?", Integer.class, active)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from schedule_bunrui where schedule_id = ?", Integer.class, active)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from schedule_details where schedule_id = ?", Integer.class, active)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from issue_submission_statuses where schedule_id = ?", Integer.class, active)).isOne();
    }

    private void insertSchedule(String scheduleId, String name, LocalDate start, LocalDate end, int updateCount) {
        jdbc.update("""
                insert into schedule (schedule_id, schedule_name, start_date, end_date, nohol_flg, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, 1, 'TEST', ?, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, name, start, end, updateCount);
    }

    private void insertPermission(String scheduleId, String syainId, short canEdit, short pinned, int updateCount) {
        jdbc.update("""
                insert into schedule_can_edit (schedule_id, syain_id, syain_kbn, can_edit, pin_flg, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, ?, 'TEST', ?, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, syainId,
                syainId.equals(instructor) || syainId.equals(secondInstructor) ? 2 : 1,
                canEdit, pinned, updateCount);
    }

    private void insertCategory(String scheduleId, short categoryNo, String name) {
        jdbc.update("""
                insert into schedule_bunrui (schedule_id, bnrui_no, bnrui_name, screen_id,
                    update_count, create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, categoryNo, name);
    }

    private void insertDetail(
            String scheduleId,
            int scheduleDateNo,
            short categoryNo,
            String instructorId,
            String content,
            short taskFlg,
            LocalDate date) {
        jdbc.update("""
                insert into schedule_details (schedule_id, schedule_date_no, bnrui_no, koshi_syain_id,
                    schedule_content, task_flg, schedule_date, screen_id, update_count,
                    create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, ?, ?, ?, 'TEST', 0, 'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, scheduleDateNo, categoryNo, instructorId, content, taskFlg, date);
    }

    private void insertIssueSubmission(
            String scheduleId,
            int scheduleDateNo,
            String traineeId,
            String reviewerId) {
        jdbc.update("""
                insert into issue_submission_statuses (schedule_id, schedule_date_no, sinjin_syain_id,
                    koshi_syain_id, task_status_kbn, screen_id, update_count,
                    create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, 1, 'TEST', 0,
                    'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, scheduleDateNo, traineeId, reviewerId);
    }

    private void insertIssueComment(
            String scheduleId,
            int scheduleDateNo,
            String traineeId,
            long commentNo,
            String authorId) {
        jdbc.update("""
                insert into issue_submission_statuses_comment (schedule_id, schedule_date_no, sinjin_syain_id,
                    comment_no, comment_syain_id, comment_content, screen_id, update_count,
                    create_user_cd, create_date, update_user_cd, update_date)
                values (?, ?, ?, ?, ?, 'テストコメント', 'TEST', 0,
                    'operator', current_timestamp, 'operator', current_timestamp)
                """, scheduleId, scheduleDateNo, traineeId, commentNo, authorId);
    }

    private Map<String, Object> unchangedRequest(int scheduleVersion) {
        return updateRequest(scheduleVersion,
                List.of(permission(instructor, true, 2), permission(trainee, false, 4), permission(secondTrainee, false, 0)),
                List.of(), List.of(category(1, "Java研修", 0), category(2, "削除対象分類", 0)), List.of(),
                List.of(detail(1, 1, instructor, "オリエンテーション", true, 0)));
    }

    private Map<String, Object> pinRequest(boolean pinned, int permissionVersion) {
        return Map.of("pinned", pinned, "permissionUpdateCount", permissionVersion);
    }

    private Integer schedulesCount(String scheduleId) {
        return jdbc.queryForObject("select count(*) from schedule where schedule_id = ?", Integer.class, scheduleId);
    }

    private Map<String, Object> validCreateRequest(String scheduleId) {
        return createRequest(scheduleId,
                List.of(createPermission(instructor, true), createPermission(secondInstructor, true),
                        createPermission(trainee, false)),
                List.of(createCategory(1, "新規分類")),
                List.of(createDetail(1, LocalDate.now(BUSINESS_ZONE), 1, instructor, "新規内容", false)));
    }

    private Map<String, Object> createRequest(String scheduleId, List<Map<String, Object>> permissionRows,
            List<Map<String, Object>> categoryRows, List<Map<String, Object>> detailRows) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return Map.of("scheduleId", scheduleId, "scheduleName", "新規スケジュール",
                "startDate", today.minusDays(1), "endDate", today.plusDays(1),
                "excludeHolidays", false, "permissions", permissionRows,
                "categories", categoryRows, "details", detailRows);
    }

    private Map<String, Object> createRequestForDate(String scheduleId, LocalDate date, boolean excludeHolidays) {
        var request = new HashMap<>(createRequest(scheduleId,
                List.of(createPermission(instructor, true), createPermission(trainee, false)),
                List.of(createCategory(1, "新規分類")),
                List.of(createDetail(1, date, 1, instructor, "新規内容", false))));
        request.put("startDate", date);
        request.put("endDate", date);
        request.put("excludeHolidays", excludeHolidays);
        return request;
    }

    private Map<String, Object> createPermission(String id, boolean canEdit) {
        return Map.of("employeeId", id, "canEdit", canEdit);
    }

    private Map<String, Object> createCategory(int no, String name) {
        return Map.of("categoryNo", no, "categoryName", name);
    }

    private Map<String, Object> createDetail(int no, LocalDate date, int categoryNo,
            String instructorId, String content, boolean task) {
        return Map.of("scheduleDateNo", no, "scheduleDate", date, "categoryNo", categoryNo,
                "instructorEmployeeId", instructorId, "content", content, "taskFlg", task);
    }

    private Map<String, Object> updateRequest(int scheduleVersion, List<Map<String, Object>> permissionRows,
            List<Map<String, Object>> deletedPermissionRows, List<Map<String, Object>> categoryRows,
            List<Map<String, Object>> deletedCategoryRows, List<Map<String, Object>> detailRows) {
        return Map.of("scheduleName", "期間内", "scheduleUpdateCount", scheduleVersion, "permissions", permissionRows,
                "deletedPermissions", deletedPermissionRows, "categories", categoryRows,
                "deletedCategories", deletedCategoryRows, "details", detailRows);
    }

    private Map<String, Object> permission(String id, boolean canEdit, int version) {
        return Map.of("employeeId", id, "canEdit", canEdit, "updateCount", version);
    }

    private Map<String, Object> deletedPermission(String id, int version) {
        return Map.of("employeeId", id, "updateCount", version);
    }

    private Map<String, Object> category(int no, String name, int version) {
        return Map.of("categoryNo", no, "categoryName", name, "updateCount", version);
    }

    private Map<String, Object> deletedCategory(int no, int version) {
        return Map.of("categoryNo", no, "updateCount", version);
    }

    private Map<String, Object> detail(int no, int categoryNo, String instructorId,
            String content, boolean task, int version) {
        return Map.of("scheduleDateNo", no, "categoryNo", categoryNo,
                "instructorEmployeeId", instructorId, "content", content,
                "taskFlg", task, "updateCount", version);
    }
}
