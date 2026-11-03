package com.training.portal.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleSessionFilterTest {
    @Test
    void failedDatabaseCheckNeverReachesBusinessChain() throws Exception {
        var guard = mock(RoleSessionGuard.class);
        var principal = new MemberPrincipal("test", "unused", (short) 2, 0);
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private")).when(guard).verify(auth);
        var request = new MockHttpServletRequest("GET", "/api/members");
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            new RoleSessionFilter(guard).doFilter(request, response, (a, b) -> called.set(true));
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(called).isFalse();
            assertThat(response.getContentAsString()).contains("INTERNAL_ERROR").doesNotContain("private");
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test
    void legacyAuthenticationWithoutGenerationIsRejected() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var guard = new RoleSessionGuard(jdbc, mock(org.springframework.transaction.PlatformTransactionManager.class));
        var user = org.springframework.security.core.userdetails.User.withUsername("legacy").password("unused").roles("INSTRUCTOR").build();
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        assertThatThrownBy(() -> guard.verify(auth)).isInstanceOf(RoleSessionGuard.StaleSession.class);
        verifyNoInteractions(jdbc);
    }
}
