package com.training.portal.member;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.training.portal.common.api.ApiExceptionHandler;
import com.training.portal.member.dto.*;
import com.training.portal.member.exception.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KenshuMemberController.class)
@Import({ApiExceptionHandler.class, com.training.portal.config.SecurityConfig.class, com.training.portal.config.PasswordEncoderConfig.class})
@org.springframework.security.test.context.support.WithMockUser(username = "operator", roles = "INSTRUCTOR")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class KenshuMemberControllerTest {
    @MockitoBean org.springframework.security.core.userdetails.UserDetailsService users;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.web.context.WebApplicationContext context;

    @org.junit.jupiter.api.BeforeEach
    void configureSecurityTestSupport() {
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(roleGuard).write(any(Runnable.class));
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @MockitoBean KenshuMemberService service;
    @MockitoBean com.training.portal.auth.RoleSessionGuard roleGuard;
    private static final String CREATE = """
            {"syainId":"001","syainKbn":1,"name":"テスト","nameKana":null,"password":"private-password"}
            """;
    private static final MemberResponse MEMBER = new MemberResponse("001", (short) 1, "テスト", null, 0);

    @Test
    void listAndGetReturnOnlyResponseFields() throws Exception {
        when(service.findAll()).thenReturn(List.of(MEMBER));
        when(service.findById("001")).thenReturn(MEMBER);
        mvc.perform(get("/api/members")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].syainId").value("001"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mvc.perform(get("/api/members/001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.updateCount").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/members", "/api/members/001"})
    @org.springframework.security.test.context.support.WithMockUser(username = "trainee", roles = "TRAINEE")
    void traineeGetIsForbidden(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(service);
    }

    @Test
    void emptyListIsAnArray() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/members")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void createReturns201AndLocation(CapturedOutput output) throws Exception {
        when(service.create(any(), eq("operator"))).thenReturn(MEMBER);
        var result = mvc.perform(post("/api/members").with(csrf())
                .contentType("application/json").content(CREATE))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/members/001"))
                .andExpect(jsonPath("$.length()").value(5)).andReturn();
        verify(service).create(new MemberCreateRequest("001", (short) 1, "テスト", null, "private-password"), "operator");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-password", "password", "createUserCd");
        assertThat(output.getAll()).doesNotContain("private-password");
    }

    @Test
    void updatePassesVersionAndBlankPassword() throws Exception {
        when(service.update(eq("001"), any(), eq("operator"))).thenReturn(MEMBER);
        mvc.perform(put("/api/members/001").with(csrf())
                .contentType("application/json").content("""
                {"syainKbn":1,"name":"テスト","password":"","updateCount":0}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.syainId").value("001"));
        verify(service).update("001", new MemberUpdateRequest((short) 1, "テスト", null, "", 0), "operator");
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/members/001").with(csrf())).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete("001");
    }

    @Test
    void validationHasSafeFieldErrors(CapturedOutput output) throws Exception {
        var result = mvc.perform(post("/api/members").with(csrf())
                .contentType("application/json").content(CREATE.replace("private-password", "private-password".repeat(8))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("UTF-8で72バイト以内で入力してください")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-password", "rejectedValue", "Exception");
        assertThat(output.getAll()).doesNotContain("private-password", "rejectedValue");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{\"syainKbn\":\"private-password\"}", "{}"})
    void invalidBodiesReturn400WithoutDetails(String body, CapturedOutput output) throws Exception {
        var result = mvc.perform(post("/api/members").with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-password", "Exception", "rejectedValue");
        assertThat(output.getAll()).doesNotContain("private-password", "rejectedValue");
        verifyNoInteractions(service);
    }

    @Test
    void spoofedOperatorHeaderIsIgnored() throws Exception {
        when(service.create(any(), eq("operator"))).thenReturn(MEMBER);
        mvc.perform(post("/api/members").with(csrf()).header("X-Operation-Syain-Id", "attacker")
                .contentType("application/json").content(CREATE)).andExpect(status().isCreated());
        verify(service).create(any(), eq("operator"));
    }

    @Test
    void selfDeleteIs409WithoutCallingService() throws Exception {
        mvc.perform(delete("/api/members/operator").with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_SELF_DELETE_NOT_ALLOWED"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidPathIs400() throws Exception {
        mvc.perform(get("/api/members/12345678901")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void updateRequiresVersion() throws Exception {
        mvc.perform(put("/api/members/001").with(csrf())
                .contentType("application/json").content("{\"name\":\"テスト\",\"syainKbn\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<RuntimeException> conflicts() {
        return Stream.of(new DuplicateMemberException(), new MemberUpdateConflictException(), new MemberReferencedException());
    }

    @ParameterizedTest
    @MethodSource("conflicts")
    void businessConflictsReturn409(RuntimeException failure) throws Exception {
        doThrow(failure).when(service).delete("001");
        mvc.perform(delete("/api/members/001").with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void missingMemberIs404() throws Exception {
        when(service.findById("001")).thenThrow(new MemberNotFoundException());
        mvc.perform(get("/api/members/001")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void serviceValidationIs400WithoutExceptionText(CapturedOutput output) throws Exception {
        when(service.findAll()).thenThrow(new IllegalArgumentException("private-password"));
        var result = mvc.perform(get("/api/members")).andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString() + output.getAll()).doesNotContain("private-password");
    }

    @Test
    void unexpectedFailureIsSafe500(CapturedOutput output) throws Exception {
        when(service.findAll()).thenThrow(new DataIntegrityViolationException("SELECT password FROM kenshu_member private-password"));
        var result = mvc.perform(get("/api/members")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(result.getResponse().getContentAsString() + output.getAll())
                .doesNotContain("SELECT password", "private-password", "DataIntegrityViolationException");
    }

    @Test
    void unsupportedMethodAndMediaTypeKeepHttpStatus() throws Exception {
        mvc.perform(patch("/api/members/001").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/members").with(csrf())
                .contentType("text/plain").content(CREATE)).andExpect(status().isUnsupportedMediaType());
    }
}
