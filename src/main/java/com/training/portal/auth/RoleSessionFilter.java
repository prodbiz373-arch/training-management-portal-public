package com.training.portal.auth;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Registered only inside the security chain, after context loading and before authorization. */
public final class RoleSessionFilter extends OncePerRequestFilter {
    private final RoleSessionGuard guard;
    private final boolean secureCookie;
    private final String sameSite;
    private final com.training.portal.audit.SecurityAudit audit;
    public RoleSessionFilter(RoleSessionGuard guard) { this(guard,false,"lax"); }
    public RoleSessionFilter(RoleSessionGuard guard,boolean secureCookie,String sameSite) { this(guard,secureCookie,sameSite,null); }
    public RoleSessionFilter(RoleSessionGuard guard,boolean secureCookie,String sameSite, com.training.portal.audit.SecurityAudit audit) {
        this.audit=audit;
        this.guard=guard;this.secureCookie=secureCookie;
        if(!java.util.Set.of("lax","strict","none").contains(sameSite.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("SameSite設定が不正です");
        this.sameSite=sameSite;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/") || path.equals("/api/auth/login") || authentication == null
                || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
            chain.doFilter(request, response); return;
        }
        boolean write = !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        var buffered = new ContentCachingResponseWrapper(response);
        try {
            Runnable action = () -> {
                guard.verify(authentication);
                try { chain.doFilter(request, buffered); }
                catch (IOException | ServletException exception) { throw new IllegalStateException("API処理失敗"); }
            };
            if (write) guard.write(action); else action.run();
        } catch (RoleSessionGuard.StaleSession exception) {
            if(audit!=null)audit.record(com.training.portal.audit.SecurityAudit.Event.SESSION_EXPIRED,authentication.getName(),request.getRemoteAddr(),401);
            try { new SecurityContextLogoutHandler().logout(request, buffered, authentication); }
            catch (RuntimeException ignored) { SecurityContextHolder.clearContext(); }
            buffered.resetBuffer();
            buffered.addHeader("Set-Cookie", "JSESSIONID=; Path=" + (request.getContextPath().isEmpty() ? "/" : request.getContextPath()) + "; Max-Age=0; HttpOnly; SameSite=" + sameSite + (secureCookie ? "; Secure" : ""));
            error(buffered, 401, "AUTHENTICATION_REQUIRED", "ログインが必要です");
        } catch (RuntimeException exception) {
            // A business exception can mark the outer transaction rollback-only after advice mapped it.
            if (!(exception instanceof org.springframework.transaction.UnexpectedRollbackException)
                    || buffered.getStatus() < 400) {
                buffered.resetBuffer();
                error(buffered, 500, "INTERNAL_ERROR", "処理に失敗しました");
            }
        }
        // Copy only after the transaction has committed, so commit failure is never a false success.
        buffered.copyBodyToResponse();
    }
    private void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"errors\":[]}");
    }
}
