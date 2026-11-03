package com.training.portal.auth;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.training.portal.member.KenshuMemberService;
import com.training.portal.member.KenshuMemberRepository;
import com.training.portal.member.dto.MemberCreateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @Autowired KenshuMemberService members;
    @Autowired KenshuMemberRepository repository;
    @Autowired JdbcTemplate jdbc;
    JsonMapper json = new JsonMapper();
    String instructor;
    String trainee;
    static final String PASSWORD = "login-secret-123";

    @BeforeEach
    void fixtures() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");
        String prefix = UUID.randomUUID().toString().substring(0, 7);
        instructor = "I" + prefix;
        trainee = "T" + prefix;
        members.create(new MemberCreateRequest(instructor, (short) 2, "テスト講師", "こうし", PASSWORD), instructor);
        members.create(new MemberCreateRequest(trainee, (short) 1, "テスト新人", null, PASSWORD), instructor);
        repository.flush();
    }

    @Test
    void normalApplicationDoesNotExposeInitialSetupService() {
        assertThat(applicationContext.getBeansOfType(com.training.portal.setup.InitialInstructorService.class)).isEmpty();
    }

    @Test
    void roleChangeRejectsAllOldSessionsAndNewLoginUsesNewRole() throws Exception {
        var first = login(instructor);
        var second = login(instructor);
        changeRole(instructor, (short) 1);
        for (var client : java.util.List.of(first, second)) {
            mvc.perform(get("/api/auth/me").session(client.session()).header("Origin", "http://localhost:3000"))
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
        var fresh = login(instructor);
        mvc.perform(get("/api/auth/me").session(fresh.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.syainKbn").value(1))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_TRAINEE"));
        mvc.perform(get("/api/members").session(fresh.session())).andExpect(status().isForbidden());
    }

    @Test
    void roundTripRoleChangeDoesNotReviveOldSession() throws Exception {
        var old = login(instructor);
        changeRole(instructor, (short) 1);
        changeRole(instructor, (short) 2);
        assertThat(repository.findById(instructor).orElseThrow().getAuthVersion()).isEqualTo(2L);
        mvc.perform(get("/api/auth/me").session(old.session())).andExpect(status().isUnauthorized());
        var fresh = login(instructor);
        mvc.perform(get("/api/members").session(fresh.session())).andExpect(status().isOk());
    }

    @Test
    void ordinaryNameUpdateKeepsAuthenticationGenerationAndSession() throws Exception {
        var client = login(instructor);
        var member = repository.findById(instructor).orElseThrow();
        members.update(instructor, new com.training.portal.member.dto.MemberUpdateRequest(
                (short) 2, "名前のみ変更", null, "", member.getUpdateCount()), instructor);
        assertThat(member.getAuthVersion()).isZero();
        mvc.perform(get("/api/auth/me").session(client.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("名前のみ変更"));
    }

    @Test
    void selfRoleChangeReturnsSuccessBeforeReauthentication() throws Exception {
        var client = login(instructor);
        int version = repository.findById(instructor).orElseThrow().getUpdateCount();
        mvc.perform(put("/api/members/" + instructor).session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content("{\"syainKbn\":1,\"name\":\"本人変更\",\"updateCount\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.syainKbn").value(1));
        mvc.perform(get("/api/auth/me").session(client.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void conflictingRoleChangeKeepsGenerationAndSession() throws Exception {
        var client = login(instructor);
        mvc.perform(put("/api/members/" + instructor).session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content("{\"syainKbn\":1,\"name\":\"競合\",\"updateCount\":999}"))
                .andExpect(status().isConflict());
        assertThat(repository.findById(instructor).orElseThrow().getAuthVersion()).isZero();
        mvc.perform(get("/api/auth/me").session(client.session())).andExpect(status().isOk());
    }

    private void changeRole(String id, short type) {
        var member = repository.findById(id).orElseThrow();
        members.update(id, new com.training.portal.member.dto.MemberUpdateRequest(
                type, member.getName(), member.getNameKana(), "", member.getUpdateCount()), instructor);
        repository.flush();
    }

    record Client(MockHttpSession session, String token) {}

    Client csrf(MockHttpSession session) throws Exception {
        if (session == null) session = new MockHttpSession(null, UUID.randomUUID().toString());
        var request = get("/api/auth/csrf").session(session);
        var result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN")).andReturn();
        return new Client((MockHttpSession) result.getRequest().getSession(false),
                json.readTree(result.getResponse().getContentAsString()).get("token").asString());
    }

    String loginBody(String id, String password) {
        return json.writeValueAsString(new LoginRequest(id, password));
    }

    Client login(String id) throws Exception {
        var before = csrf(null);
        mvc.perform(post("/api/auth/login").session(before.session()).header("X-CSRF-TOKEN", before.token())
                .contentType("application/json").content(loginBody(id, PASSWORD))).andExpect(status().isOk());
        return csrf(before.session());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void loginCreatesSafeSessionAndMe(boolean isInstructor, CapturedOutput output) throws Exception {
        String id = isInstructor ? instructor : trainee;
        var client = csrf(null);
        String oldSessionId = client.session().getId();
        var result = mvc.perform(post("/api/auth/login").session(client.session())
                .header("X-CSRF-TOKEN", client.token()).contentType("application/json").content(loginBody(id, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.syainId").value(id))
                .andExpect(jsonPath("$.authorities[0]").value(isInstructor ? "ROLE_INSTRUCTOR" : "ROLE_TRAINEE"))
                .andExpect(jsonPath("$.length()").value(5)).andReturn();
        assertThat(client.session().getId()).isNotEqualTo(oldSessionId);
        var context = (SecurityContext) client.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(((UserDetails) context.getAuthentication().getPrincipal()).getPassword()).isNull();
        mvc.perform(get("/api/auth/me").session(client.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.syainId").value(id)).andExpect(jsonPath("$.nameKana").value(isInstructor ? "こうし" : null));
        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("password", "updateCount", "createUserCd", "sessionId");
        assertThat(output.getAll()).doesNotContain(PASSWORD, client.token(), oldSessionId);
        // MockHttpSessionの更新後IDは短い連番のため、値単独のログ検索には使用しない。
        assertThat(output.getAll()).doesNotContain("JSESSIONID=" + client.session().getId());
    }

    @Test
    void unknownIdAndWrongPasswordHaveIdentical401(CapturedOutput output) throws Exception {
        var client = csrf(null);
        var wrong = mvc.perform(post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content(loginBody(instructor, "wrong-secret")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED")).andReturn();
        var missing = mvc.perform(post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content(loginBody("missing", "wrong-secret")))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(missing.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString())
                .doesNotContain("wrong-secret", "missing", instructor, "Exception", "rejectedValue");
        assertThat(client.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        assertThat(output.getAll()).doesNotContain("wrong-secret", PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null", "", "{\"syainId\":\"12345678901\",\"password\":\"x\"}",
            "{\"syainId\":\"001\",\"password\":\" \"}"})
    void invalidLoginIs400(String body) throws Exception {
        var client = csrf(null);
        mvc.perform(post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void overlongLoginPasswordIs400AndNotLogged(CapturedOutput output) throws Exception {
        var client = csrf(null);
        var result = mvc.perform(post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content(loginBody(instructor, "secret".repeat(20))))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString() + output.getAll()).doesNotContain("secretsecret", "rejectedValue");
    }

    @Test
    void unauthenticatedProtectedApisAre401() throws Exception {
        for (var request : new MockHttpServletRequestBuilder[]{get("/api/auth/me"), get("/api/members"),
                get("/api/members/001"), post("/api/members"), put("/api/members/001"), delete("/api/members/001")}) {
            mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void traineeCannotAccessAnyMemberApi() throws Exception {
        var client = login(trainee);
        for (var request : new MockHttpServletRequestBuilder[]{get("/api/members"), get("/api/members/001"),
                post("/api/members"), put("/api/members/001"), delete("/api/members/001")}) {
            mvc.perform(request.session(client.session()).header("X-CSRF-TOKEN", client.token()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void instructorCrudUsesAuthenticatedEmployeeAndRejectsSelfDelete() throws Exception {
        var client = login(instructor);
        String target = "N" + instructor.substring(1);
        var created = mvc.perform(post("/api/members").session(client.session()).header("X-CSRF-TOKEN", client.token())
                .header("X-Operation-Syain-Id", trainee).contentType("application/json")
                .content(json.writeValueAsString(new MemberCreateRequest(target, (short) 1, "登録", null, PASSWORD))))
                .andExpect(status().isCreated()).andReturn();
        assertThat(repository.findById(target).orElseThrow().getCreateUserCd()).isEqualTo(instructor);
        int version = json.readTree(created.getResponse().getContentAsString()).get("updateCount").asInt();
        mvc.perform(put("/api/members/" + target).session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content("{\"syainKbn\":2,\"name\":\"更新\",\"updateCount\":" + version + "}"))
                .andExpect(status().isOk());
        assertThat(repository.findById(target).orElseThrow().getUpdateUserCd()).isEqualTo(instructor);
        mvc.perform(delete("/api/members/" + instructor).session(client.session()).header("X-CSRF-TOKEN", client.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER_SELF_DELETE_NOT_ALLOWED"));
        mvc.perform(delete("/api/members/" + target).session(client.session()).header("X-CSRF-TOKEN", client.token()))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(target)).isEmpty();
    }

    @Test
    void csrfIsRequiredForLoginAndEveryMutation() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody(instructor, PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        var client = login(instructor);
        for (var request : new MockHttpServletRequestBuilder[]{post("/api/auth/logout"), post("/api/members"),
                put("/api/members/001"), delete("/api/members/001")}) {
            mvc.perform(request.session(client.session())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        mvc.perform(delete("/api/members/001").session(client.session()).header("X-CSRF-TOKEN", "invalid-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(client.session())).andExpect(status().isOk());
    }

    @Test
    void loginRotatesCsrfAndLogoutInvalidatesSession() throws Exception {
        var before = csrf(null);
        mvc.perform(post("/api/auth/login").session(before.session()).header("X-CSRF-TOKEN", before.token())
                .contentType("application/json").content(loginBody(instructor, PASSWORD))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(before.session()).header("X-CSRF-TOKEN", before.token()))
                .andExpect(status().isForbidden());
        var after = csrf(before.session());
        mvc.perform(post("/api/auth/logout").session(after.session()).header("X-CSRF-TOKEN", after.token()))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(after.session().isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        var next = csrf(null);
        mvc.perform(post("/api/auth/login").session(next.session()).header("X-CSRF-TOKEN", after.token())
                .contentType("application/json").content(loginBody(instructor, PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void corsAllowsExactOriginAndCredentials() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(get("/api/auth/me").header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void corsRejectsOtherOriginsMethodsAndHeaders() throws Exception {
        mvc.perform(options("/api/members").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(options("/api/members").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PATCH")).andExpect(status().isForbidden());
        mvc.perform(options("/api/members").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-Operation-Syain-Id"))
                .andExpect(status().isForbidden());
    }

    @Test void passwordChangeRejectsBothOldBrowsersAndRequiresNewPassword() throws Exception {
        var first=login(instructor);var second=login(instructor);
        var m=members.findById(instructor);
        members.update(instructor,new com.training.portal.member.dto.MemberUpdateRequest((short)2,m.name(),null,"changed-secret-123",m.updateCount()),instructor);
        assertThat(repository.findById(instructor).orElseThrow().getAuthVersion()).isEqualTo(1L);
        for(var old:java.util.List.of(first,second))mvc.perform(get("/api/auth/me").session(old.session())).andExpect(status().isUnauthorized());
        var token=csrf(null);
        mvc.perform(post("/api/auth/login").session(token.session()).header("X-CSRF-TOKEN",token.token()).contentType("application/json").content(loginBody(instructor,PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").session(token.session()).header("X-CSRF-TOKEN",token.token()).contentType("application/json").content(loginBody(instructor,"changed-secret-123"))).andExpect(status().isOk());
    }
    @Test void conflictingPasswordUpdateDoesNotAdvanceGenerationOrReplacePassword() throws Exception {
        var m=members.findById(instructor);var stale=m.updateCount();
        members.update(instructor,new com.training.portal.member.dto.MemberUpdateRequest((short)2,m.name(),null,"first-secret-123",stale),instructor);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(()->members.update(instructor,new com.training.portal.member.dto.MemberUpdateRequest((short)2,m.name(),null,"second-secret-123",stale),instructor)))
            .isInstanceOf(com.training.portal.member.exception.MemberUpdateConflictException.class);
        assertThat(repository.findById(instructor).orElseThrow().getAuthVersion()).isEqualTo(1L);
    }

    @Test void shortExistingPasswordPolicyIsNotSilentlyChanged() throws Exception {
        var m=members.findById(instructor);
        members.update(instructor,new com.training.portal.member.dto.MemberUpdateRequest((short)2,m.name(),null,"a",m.updateCount()),instructor);
        var c=csrf(null);
        mvc.perform(post("/api/auth/login").session(c.session()).header("X-CSRF-TOKEN",c.token()).contentType("application/json").content(loginBody(instructor,"a"))).andExpect(status().isOk());
    }
}
