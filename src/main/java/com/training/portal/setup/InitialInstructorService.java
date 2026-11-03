package com.training.portal.setup;

import com.training.portal.member.KenshuMemberRepository;
import com.training.portal.member.KenshuMemberService;
import com.training.portal.member.dto.MemberCreateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Explicitly imported by the local command only; never component-scanned. */
public class InitialInstructorService {
    private final JdbcTemplate jdbc;
    private final KenshuMemberRepository repository;
    private final KenshuMemberService members;
    public InitialInstructorService(JdbcTemplate jdbc, KenshuMemberRepository repository, KenshuMemberService members) {
        this.jdbc=jdbc; this.repository=repository; this.members=members;
    }
    @Transactional
    public void create(MemberCreateRequest input) {
        if(input==null || input.syainKbn()==null || input.syainKbn()!=2) {
            throw new IllegalArgumentException("初期講師の入力が不正です");
        }
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        // Conflicts with ordinary INSERT as well as concurrent bootstrap commands.
        jdbc.execute("LOCK TABLE kenshu_member IN EXCLUSIVE MODE");
        if(repository.count()!=0) throw new IllegalStateException("社員登録済みのため初期登録できません");
        members.create(input,input.syainId());
    }
}
