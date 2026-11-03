package com.training.portal.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;

@Configuration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class SecurityConfig {
    @Bean
    ProductionSecurityPolicy productionSecurityPolicy(org.springframework.core.env.Environment env) { return new ProductionSecurityPolicy(env); }
    @Bean
    com.training.portal.audit.SecurityAudit securityAudit(
            @Value("${training.portal.audit.hmac-key:}") String key,
            @Value("${training.portal.audit.queue-capacity:1024}") int capacity) {
        return new com.training.portal.audit.SecurityAudit(key,capacity);
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            CsrfTokenRepository csrf, com.training.portal.auth.RoleSessionGuard roleGuard, ProductionSecurityPolicy policy, com.training.portal.audit.SecurityAudit audit,
            @Value("${training.portal.hsts.max-age-seconds:0}") long hsts, @Value("${training.portal.https-confirmed:false}") boolean httpsConfirmed, @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite, @Value("${training.portal.frontend-origin:http://localhost:3000}") String origin)
            throws Exception {
        if(hsts<0 || hsts>31536000 || (hsts>0 && !httpsConfirmed)) throw new IllegalArgumentException("HSTS設定が不正です");
        var cors = new CorsConfiguration();
        if (origin.contains("*")) throw new IllegalArgumentException("許可Originにワイルドカードは使用できません");
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setExposedHeaders(List.of("Location", "Retry-After"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        var corsFilter = new CorsFilter(source);
        corsFilter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().set("Content-Type", "application/json;charset=UTF-8");
                response.getBody().write(errorJson("ACCESS_DENIED", "アクセス権限がありません").getBytes(StandardCharsets.UTF_8));
                response.flush();
            }
        });
        AccessDeniedHandler denied = (request, response, exception) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean anonymous = authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken;
            // CSRF検証は認可より先。未認証の保護APIは常に401、ログインのCSRF違反は403。
            boolean login = request.getRequestURI().equals(request.getContextPath() + "/api/auth/login");
            if (anonymous && !login) writeError(response, 401, "AUTHENTICATION_REQUIRED", "ログインが必要です");
            else writeError(response, 403, "ACCESS_DENIED", "アクセス権限がありません");
        };
        http.addFilterBefore(new HttpsOnlyFilter(policy.httpsRequired()), CorsFilter.class)
                .addFilterBefore(new com.training.portal.audit.AuditOutcomeFilter(audit), HttpsOnlyFilter.class)
                .addFilterAt(corsFilter, CorsFilter.class)
                .addFilterAfter(new com.training.portal.auth.RoleSessionFilter(roleGuard, secureCookie, sameSite, audit), CorsFilter.class)
                .headers(headers -> {
                    if(hsts==0) headers.httpStrictTransportSecurity(config -> config.disable());
                    else headers.httpStrictTransportSecurity(config -> config.maxAgeInSeconds(hsts).includeSubDomains(false).preload(false));
                })
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .csrf(config -> config.csrfTokenRepository(csrf))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, 401, "AUTHENTICATION_REQUIRED", "ログインが必要です"))
                        .accessDeniedHandler(denied))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/home").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/progress", "/api/progress/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/progress/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/progress/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/progress/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/schedules", "/api/schedules/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/schedules").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/schedules/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/schedules/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/members", "/api/members/**").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/members").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/members/**").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/members/**").hasRole("INSTRUCTOR")
                        .anyRequest().denyAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> { audit.record(com.training.portal.audit.SecurityAudit.Event.LOGOUT, authentication==null?null:authentication.getName(), request.getRemoteAddr(),204); response.setStatus(204); }));
        return http.build();
    }

    private static String errorJson(String code, String message) {
        // 呼び出し元は固定文字列のみ。例外やリクエスト情報を含めない。
        return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"errors\":[]}";
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(errorJson(code, message));
    }
}
