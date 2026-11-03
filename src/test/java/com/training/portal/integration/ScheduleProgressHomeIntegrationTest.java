package com.training.portal.integration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.training.portal.member.KenshuMemberService;
import com.training.portal.member.dto.MemberCreateRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static com.training.portal.support.SecurityTestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cross-feature flow; requires the explicitly prepared, separate PostgreSQL instance. */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST_PORT", matches = "[0-9]+")
@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ScheduleProgressHomeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KenshuMemberService members;
    @Autowired ObjectMapper mapper;
    String instructor, trainee, other, schedule;

    @BeforeEach
    void requireIsolatedDatabaseBeforeAnyWrite() {
        String port = System.getenv("INTEGRATION_TEST_PORT");
        assertThat(port).isNotBlank().isNotEqualTo("5432");
        assertThat(jdbc.queryForObject("select current_setting('port')", String.class)).isEqualTo(port);
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("training_portal");
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        instructor = "I" + suffix; trainee = "T" + suffix; other = "U" + suffix; schedule = "IT" + suffix;
        for (String id : List.of(instructor, trainee, other)) {
            members.create(new MemberCreateRequest(id, (short) (id.equals(instructor) ? 2 : 1),
                    "結合テスト", null, "integration-test-password"), instructor);
        }
    }

    @Test
    void scheduleCreationSubmissionCommentsAndRemovalStayConsistentWithHome() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        var task = Map.of("scheduleDateNo", 1, "scheduleDate", today.toString(), "categoryNo", 1,
                "instructorEmployeeId", instructor, "content", "結合課題", "taskFlg", true);
        var normal = Map.of("scheduleDateNo", 2, "scheduleDate", today.toString(), "categoryNo", 1,
                "instructorEmployeeId", instructor, "content", "通常研修", "taskFlg", false);
        var create = Map.of("scheduleId", schedule, "scheduleName", "結合テスト研修",
                "startDate", today.toString(), "endDate", today.toString(), "excludeHolidays", false,
                "permissions", List.of(Map.of("employeeId", instructor, "canEdit", true),
                        Map.of("employeeId", trainee, "canEdit", false), Map.of("employeeId", other, "canEdit", false)),
                "categories", List.of(Map.of("categoryNo", 1, "categoryName", "結合分類")), "details", List.of(task, normal));
        mvc.perform(post("/api/schedules").with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsBytes(create)))
                .andExpect(status().isCreated());
        progress(trainee, "TRAINEE", 1, 1, 0);
        progress(instructor, "INSTRUCTOR", 2, 1, 0);
        assertThat(jdbc.queryForObject("select count(*) from issue_submission_statuses where schedule_id = ?",
                Integer.class, schedule)).isZero();
        home(trainee, "TRAINEE", 1, 0);
        home(instructor, "INSTRUCTOR", 2, 0);

        // A name-only PUT must be visible through every feature's existing read path.
        String renamed = "結合名称変更";
        var baseline = mapper.readTree(mvc.perform(get("/api/schedules/" + schedule).with(user(instructor).roles("INSTRUCTOR")))
                .andReturn().getResponse().getContentAsString());
        var rename = new HashMap<String, Object>();
        rename.put("scheduleName", renamed); rename.put("scheduleUpdateCount", baseline.get("scheduleUpdateCount").asInt());
        rename.put("permissions", project(baseline.get("permissions"), "employeeId", "canEdit", "updateCount"));
        rename.put("categories", project(baseline.get("categories"), "categoryNo", "categoryName", "updateCount"));
        rename.put("details", project(baseline.get("details"), "scheduleDateNo", "categoryNo", "instructorEmployeeId", "content", "taskFlg", "updateCount"));
        rename.put("deletedPermissions", List.of()); rename.put("deletedCategories", List.of());
        mvc.perform(put("/api/schedules/" + schedule).with(user(trainee).roles("TRAINEE")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsBytes(rename))).andExpect(status().isForbidden());
        mvc.perform(put("/api/schedules/" + schedule).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsBytes(rename)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduleName").value(renamed));
        for (String endpoint : List.of("/api/schedules", "/api/schedules/" + schedule, "/api/home", "/api/progress")) {
            String filter = endpoint.equals("/api/schedules") ? "$[?(@.scheduleId == '" + schedule + "')].scheduleName"
                    : endpoint.equals("/api/schedules/" + schedule) ? "$.scheduleName" : "$.schedules[?(@.scheduleId == '" + schedule + "')].scheduleName";
            mvc.perform(get(endpoint).with(user(instructor).roles("INSTRUCTOR")))
                    .andExpect(status().isOk()).andExpect(jsonPath(filter).value(renamed));
        }
        rename.put("scheduleName", "競合による上書き");
        mvc.perform(put("/api/schedules/" + schedule).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsBytes(rename))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select schedule_name from schedule where schedule_id=?", String.class, schedule)).isEqualTo(renamed);

        String path = "/api/progress/" + schedule + "/1/trainees/" + trainee;
        mvc.perform(put(path).with(user(instructor).roles("INSTRUCTOR")).with(csrf()).contentType("application/json")
                .content("{\"reviewerEmployeeId\":\"" + instructor + "\",\"statusCode\":6,\"updateCount\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedDate").value(today.toString()));
        progress(trainee, "TRAINEE", 1, 6, 0);
        home(trainee, "TRAINEE", 0, 1);
        home(other, "TRAINEE", 1, 0);
        home(instructor, "INSTRUCTOR", 1, 1);

        String commentsPath = path + "/comments";
        var comment = mapper.readTree(mvc.perform(post(commentsPath).with(user(trainee).roles("TRAINEE")).with(csrf())
                .contentType("application/json").content("{\"content\":\"登録コメント\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String commentPath = commentsPath + "/" + comment.get("commentNo").asLong();
        mvc.perform(put(commentPath).with(user(trainee).roles("TRAINEE")).with(csrf()).contentType("application/json")
                .content("{\"content\":\"編集コメント\",\"updateCount\":" + comment.get("updateCount").asInt() + "}"))
                .andExpect(status().isOk());
        progress(trainee, "TRAINEE", 1, 6, 1);
        mvc.perform(get("/api/progress").param("scheduleId", schedule).with(user(trainee).roles("TRAINEE")))
                .andExpect(jsonPath("$.items[0].comments[0].content").value("編集コメント"));
        mvc.perform(delete(commentPath).param("updateCount", "1").with(user(instructor).roles("INSTRUCTOR")).with(csrf()))
                .andExpect(status().isForbidden());
        var before = jdbc.queryForList("select * from issue_submission_statuses where schedule_id = ?", schedule);
        var beforeComments = jdbc.queryForList("select * from issue_submission_statuses_comment where schedule_id = ?", schedule);
        updateTask(false, "", 400);
        assertThat(jdbc.queryForList("select * from issue_submission_statuses where schedule_id = ?", schedule)).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from issue_submission_statuses_comment where schedule_id = ?", schedule)).isEqualTo(beforeComments);
        home(instructor, "INSTRUCTOR", 1, 1);

        mvc.perform(put(path).with(user(instructor).roles("INSTRUCTOR")).with(csrf()).contentType("application/json")
                .content("{\"reviewerEmployeeId\":null,\"statusCode\":1,\"updateCount\":0}"))
                .andExpect(status().isOk());
        updateTask(false, "", 400); // Unsubmitted still cannot be removed while the comment exists.
        mvc.perform(delete(commentPath).param("updateCount", "1").with(user(trainee).roles("TRAINEE")).with(csrf()))
                .andExpect(status().isNoContent());
        progress(trainee, "TRAINEE", 1, 1, 0);
        updateTask(false, "", 200);
        mvc.perform(get("/api/progress").param("scheduleId", schedule).with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        home(instructor, "INSTRUCTOR", 0, 0);
        home(trainee, "TRAINEE", 0, 0);
        assertThat(jdbc.queryForObject("select schedule_content from schedule_details where schedule_id = ? and schedule_date_no=1",
                String.class, schedule)).isNull();
    }

    void progress(String id, String role, int count, int status, int comments) throws Exception {
        mvc.perform(get("/api/progress").param("scheduleId", schedule).with(user(id).roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(count))
                .andExpect(jsonPath("$.items[0].statusCode").value(status))
                .andExpect(jsonPath("$.items[0].comments.length()").value(comments));
    }
    void home(String id, String role, int unsubmitted, int completed) throws Exception {
        mvc.perform(get("/api/home").with(user(id).roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskSummary.unsubmitted").value(unsubmitted))
                .andExpect(jsonPath("$.taskSummary.completed").value(completed));
    }
    void updateTask(boolean task, String content, int expectedStatus) throws Exception {
        JsonNode baseline = mapper.readTree(mvc.perform(get("/api/schedules/" + schedule).with(user(instructor).roles("INSTRUCTOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var request = new HashMap<String, Object>();
        request.put("scheduleName", baseline.get("scheduleName").asText());
        request.put("scheduleUpdateCount", baseline.get("scheduleUpdateCount").asInt());
        request.put("deletedPermissions", List.of()); request.put("deletedCategories", List.of());
        request.put("permissions", project(baseline.get("permissions"), "employeeId", "canEdit", "updateCount"));
        request.put("categories", project(baseline.get("categories"), "categoryNo", "categoryName", "updateCount"));
        var rows = project(baseline.get("details"), "scheduleDateNo", "categoryNo", "instructorEmployeeId", "content", "taskFlg", "updateCount");
        rows.get(0).put("taskFlg", task); rows.get(0).put("content", content); request.put("details", rows);
        var result = mvc.perform(put("/api/schedules/" + schedule).with(user(instructor).roles("INSTRUCTOR")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsBytes(request)))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 400) {
            result.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(jdbc.queryForObject("select task_flg from schedule_details where schedule_id=? and schedule_date_no=1", Short.class, schedule))
                    .isEqualTo((short) 1);
            assertThat(mapper.readTree(mvc.perform(get("/api/schedules/" + schedule).with(user(instructor).roles("INSTRUCTOR")))
                    .andReturn().getResponse().getContentAsString())).isEqualTo(baseline);
        }
    }
    List<Map<String, Object>> project(JsonNode rows, String... fields) {
        var result = new ArrayList<Map<String, Object>>();
        for (JsonNode row : rows) {
            var values = new HashMap<String, Object>();
            for (String field : fields) values.put(field, mapper.convertValue(row.get(field), Object.class));
            result.add(values);
        }
        return result;
    }
}
