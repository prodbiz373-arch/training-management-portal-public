package com.training.portal.config;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Never infer TLS from client-supplied Forwarded/X-Forwarded-* headers. */
public final class HttpsOnlyFilter extends OncePerRequestFilter {
    private final boolean required;
    public HttpsOnlyFilter(boolean required){this.required=required;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(required && !request.isSecure()) {
            response.setStatus(403);response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"HTTPS_REQUIRED\",\"message\":\"HTTPSで接続してください\",\"errors\":[]}");return;
        }
        chain.doFilter(request,response);
    }
}
