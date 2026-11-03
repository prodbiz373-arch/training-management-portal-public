package com.training.portal.audit;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Outside authorization/transaction filters so management success is recorded after commit. */
public final class AuditOutcomeFilter extends OncePerRequestFilter {
    private final SecurityAudit audit;
    public AuditOutcomeFilter(SecurityAudit audit){this.audit=audit;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        String actor=auth!=null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)?auth.getName():null;
        try {chain.doFilter(request,response);}
        finally {
            int status=response.getStatus();
            if(status==403)audit.record(SecurityAudit.Event.ACCESS_DENIED,actor,request.getRemoteAddr(),status);
            else if(status==401 && !request.getRequestURI().equals(request.getContextPath()+"/api/auth/login"))audit.record(SecurityAudit.Event.AUTHENTICATION_REQUIRED,actor,request.getRemoteAddr(),status);
            else if(status>=200 && status<300){
                String path=request.getRequestURI().substring(request.getContextPath().length());
                if(path.equals("/api/members") || path.startsWith("/api/members/")){
                    var event=switch(request.getMethod()){case "POST"->SecurityAudit.Event.MEMBER_CREATED;case "PUT"->SecurityAudit.Event.MEMBER_UPDATED;case "DELETE"->SecurityAudit.Event.MEMBER_DELETED;default->null;};
                    if(event!=null)audit.record(event,actor,request.getRemoteAddr(),status);
                }
            }
        }
    }
}
