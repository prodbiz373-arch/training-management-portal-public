package com.training.portal.auth;

import com.training.portal.member.KenshuMemberRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberUserDetailsService implements UserDetailsService {
    private final KenshuMemberRepository repository;

    public MemberUserDetailsService(KenshuMemberRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String syainId) {
        var member = repository.findById(syainId)
                .orElseThrow(() -> new UsernameNotFoundException("認証できません"));
        if (member.getSyainKbn() != 1 && member.getSyainKbn() != 2) {
            throw new UsernameNotFoundException("認証できません");
        }
        return new MemberPrincipal(member.getSyainId(), member.getPassword(), member.getSyainKbn(), member.getAuthVersion());
    }
}
