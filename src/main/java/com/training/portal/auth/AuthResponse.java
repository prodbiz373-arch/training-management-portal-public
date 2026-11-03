package com.training.portal.auth;

import java.util.List;
import com.training.portal.member.dto.MemberResponse;
import org.springframework.security.core.Authentication;

public record AuthResponse(String syainId, Short syainKbn, String name, String nameKana, List<String> authorities) {
    public static AuthResponse from(MemberResponse member, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof MemberPrincipal principal)
                || member.syainKbn() != principal.getMemberType()) {
            throw new org.springframework.security.authentication.BadCredentialsException("再ログインが必要です");
        }
        return new AuthResponse(member.syainId(), member.syainKbn(), member.name(), member.nameKana(),
                authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                        .filter(authority -> authority.equals("ROLE_TRAINEE") || authority.equals("ROLE_INSTRUCTOR")).sorted().toList());
    }
}
