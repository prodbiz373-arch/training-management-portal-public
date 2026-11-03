package com.training.portal.auth;

import com.training.portal.member.KenshuMemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final KenshuMemberService members;
    private final LoginAttemptLimiter limiter;
    private final com.training.portal.audit.SecurityAudit audit;

    public AuthController(AuthenticationManager manager, SessionAuthenticationStrategy sessionStrategy,
            SecurityContextRepository contextRepository, KenshuMemberService members, LoginAttemptLimiter limiter, com.training.portal.audit.SecurityAudit audit) {
        this.manager = manager;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.members = members;
        this.limiter = limiter;
        this.audit = audit;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        try (var attempt = limiter.acquire(input.syainId(), request.getRemoteAddr())) {
            var authentication = manager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(input.syainId(), input.password()));
            var user = AuthResponse.from(members.findById(authentication.getName()), authentication);
            // 手動ログインではセッション固定攻撃対策・CSRF更新・Context保存を明示的に実行する。
            sessionStrategy.onAuthentication(authentication, request, response);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            attempt.success();
            audit.record(com.training.portal.audit.SecurityAudit.Event.LOGIN_SUCCESS, authentication.getName(),request.getRemoteAddr(),200);
            return user;
        } catch(LoginAttemptLimiter.Limited exception) {
            audit.record(com.training.portal.audit.SecurityAudit.Event.LOGIN_LIMITED,input.syainId(),request.getRemoteAddr(),429);throw exception;
        } catch(org.springframework.security.core.AuthenticationException exception) {
            audit.record(com.training.portal.audit.SecurityAudit.Event.LOGIN_FAILURE,input.syainId(),request.getRemoteAddr(),401);throw exception;
        }
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        return AuthResponse.from(members.findById(authentication.getName()), authentication);
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    public record CsrfResponse(String headerName, String token) {
        @Override
        public String toString() { return "CsrfResponse[内容非表示]"; }
    }
}
