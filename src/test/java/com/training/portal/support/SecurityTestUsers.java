package com.training.portal.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import com.training.portal.auth.MemberPrincipal;

/** Existing API fixtures are newly created members with authentication generation zero. */
public final class SecurityTestUsers {
    public static TestUser user(String id) { return new TestUser(id); }
    public record TestUser(String id) {
        public RequestPostProcessor roles(String... roles) {
            return SecurityMockMvcRequestPostProcessors.user(new MemberPrincipal(id, "unused", (short)
                    (java.util.List.of(roles).contains("INSTRUCTOR") ? 2 : 1), 0));
        }
    }
}
