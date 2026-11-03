package com.training.portal.member;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;
import com.training.portal.member.dto.MemberCreateRequest;
import static com.training.portal.support.SecurityTestUsers.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MemberRoleChangeApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired KenshuMemberService members;
    JsonMapper json = new JsonMapper();
    String actor, teacher, first, second, schedule;

    @BeforeEach void fixture() throws Exception {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        actor="A"+suffix; teacher="B"+suffix; first="T"+suffix; second="U"+suffix; schedule="S"+suffix;
        for (String id : List.of(actor, teacher, first, second)) members.create(
                new MemberCreateRequest(id, (short)(id.equals(actor)||id.equals(teacher)?2:1), "合成社員", null, "test-password"), actor);
        var request = Map.of("scheduleId", schedule, "scheduleName", "区分変更検証", "startDate", "2026-10-18",
                "endDate", "2026-10-18", "excludeHolidays", false,
                "permissions", List.of(permission(actor,true),permission(teacher,true),permission(first,true),permission(second,false)),
                "categories", List.of(Map.of("categoryNo",1,"categoryName","分類")),
                "details", List.of(Map.of("scheduleDateNo",1,"scheduleDate","2026-10-18","categoryNo",1,
                        "instructorEmployeeId",teacher,"content","課題","taskFlg",true)));
        mvc.perform(post("/api/schedules").with(user(actor).roles("INSTRUCTOR")).with(csrf())
                .contentType("application/json").content(json.writeValueAsBytes(request))).andExpect(status().isCreated());
        em.clear();
    }
    Map<String,Object> permission(String id,boolean edit) { return Map.of("employeeId",id,"canEdit",edit); }
    org.springframework.test.web.servlet.ResultActions change(String id,int next) throws Exception {
        return mvc.perform(put("/api/members/"+id).with(user(actor).roles("INSTRUCTOR")).with(csrf())
                .contentType("application/json").content(json.writeValueAsBytes(Map.of("syainKbn",next,"name","合成社員","updateCount",0))));
    }
    @Test void synchronizeTypeKeepsEditableTraineePinAndTaskData() throws Exception {
        jdbc.update("update schedule_can_edit set pin_flg=1 where schedule_id=? and syain_id=?",schedule,actor);
        em.clear();
        var before = jdbc.queryForMap("select * from schedule_details where schedule_id=?",schedule);
        change(actor,1).andExpect(status().isOk());
        var row = jdbc.queryForMap("select * from schedule_can_edit where schedule_id=? and syain_id=?",schedule,actor);
        assertThat(((Number)row.get("syain_kbn")).intValue()).isEqualTo(1);
        assertThat(((Number)row.get("can_edit")).intValue()).isEqualTo(1);
        assertThat(((Number)row.get("pin_flg")).intValue()).isEqualTo(1);
        assertThat(((Number)row.get("update_count")).intValue()).isEqualTo(1);
        assertThat(row.get("update_user_cd")).isEqualTo(actor);
        assertThat(jdbc.queryForMap("select * from schedule_details where schedule_id=?",schedule)).isEqualTo(before);
    }
    @Test void lastEditableInstructorCannotChangeRole() throws Exception {
        jdbc.update("update schedule_can_edit set can_edit=0 where schedule_id=? and syain_id=?",schedule,teacher);
        em.clear(); change(actor,1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER_ROLE_CHANGE_NOT_ALLOWED"));
        assertUnchanged(actor,2);
    }
    @Test void lastTraineeCannotBecomeInstructor() throws Exception {
        jdbc.update("delete from schedule_can_edit where schedule_id=? and syain_id=?",schedule,second);
        em.clear(); change(first,2).andExpect(status().isConflict()); assertUnchanged(first,1);
    }
    @Test void assignedInstructorCannotBecomeTrainee() throws Exception {
        change(teacher,1).andExpect(status().isConflict()); assertUnchanged(teacher,2);
    }
    @ParameterizedTest @ValueSource(strings={"unsubmitted","submitted","comment"})
    void submissionHistoryPolicyAndDataPreservation(String mode) throws Exception {
        jdbc.update("""
                insert into issue_submission_statuses(schedule_id,schedule_date_no,sinjin_syain_id,task_status_kbn,
                screen_id,update_count,create_user_cd,create_date,update_user_cd,update_date)
                values (?,1,?,?,'TEST',0,'TEST',current_timestamp,'TEST',current_timestamp)
                """,schedule,first,mode.equals("submitted")?3:1);
        if(mode.equals("comment")) jdbc.update("""
                insert into issue_submission_statuses_comment(schedule_id,schedule_date_no,sinjin_syain_id,comment_no,
                comment_syain_id,comment_content,screen_id,update_count,create_user_cd,create_date,update_user_cd,update_date)
                values (?,1,?,1,?,'合成コメント','TEST',0,'TEST',current_timestamp,'TEST',current_timestamp)
                """,schedule,first,first);
        var before=jdbc.queryForList("select * from issue_submission_statuses where schedule_id=?",schedule);
        var comments=jdbc.queryForList("select * from issue_submission_statuses_comment where schedule_id=?",schedule);
        change(first,2).andExpect(mode.equals("unsubmitted")?status().isOk():status().isConflict());
        assertThat(jdbc.queryForList("select * from issue_submission_statuses where schedule_id=?",schedule)).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from issue_submission_statuses_comment where schedule_id=?",schedule)).isEqualTo(comments);
        if(!mode.equals("unsubmitted")) assertUnchanged(first,1);
    }
    private void assertUnchanged(String id,int type) {
        assertThat(jdbc.queryForObject("select auth_version from kenshu_member where syain_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select syain_kbn from kenshu_member where syain_id=?",Integer.class,id)).isEqualTo(type);
        assertThat(jdbc.queryForObject("select update_count from schedule_can_edit where schedule_id=? and syain_id=?",Integer.class,schedule,id)).isZero();
    }
}
