package com.training.portal.member;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.training.portal.member.dto.MemberCreateRequest;
import com.training.portal.member.dto.MemberUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class KenshuMemberPersistenceTest {
    @Autowired KenshuMemberService service;
    @Autowired KenshuMemberRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Test
    void persistsUpdatesAndDeletesInTestSchema() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("test1");
        String id = "T" + UUID.randomUUID().toString().substring(0, 8);
        var created = service.create(new MemberCreateRequest(id, (short) 1, "テスト社員", null, "test-password"), "operator");
        assertThat(created.updateCount()).isZero();
        entityManager.clear();
        var saved = repository.findById(id).orElseThrow();
        assertThat(encoder.matches("test-password", saved.getPassword())).isTrue();
        assertThat(saved.getCreateDate()).isNotNull();
        assertThat(saved.getUpdateDate()).isNotNull();
        assertThat(saved.getScreenId()).isEqualTo("MMB01");
        assertThat(saved.getCreateUserCd()).isEqualTo("operator");
        assertThat(jdbc.queryForObject("select count(*) from test1.kenshu_member where syain_id = ?", Integer.class, id)).isEqualTo(1);
        String originalHash = saved.getPassword();
        var updated = service.update(id, new MemberUpdateRequest((short) 2, "更新社員", "かな", "", created.updateCount()), "editor");
        assertThat(updated.updateCount()).isEqualTo(1);
        entityManager.clear();
        saved = repository.findById(id).orElseThrow();
        assertThat(saved.getPassword()).isEqualTo(originalHash);
        assertThat(saved.getName()).isEqualTo("更新社員");
        assertThat(saved.getUpdateUserCd()).isEqualTo("editor");
        assertThat(saved.getCreateUserCd()).isEqualTo("operator");
        assertThat(saved.getUpdateDate()).isAfterOrEqualTo(saved.getCreateDate());
        service.delete(id);
        entityManager.clear();
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void repositoryOrdersByEmployeeId() {
        String prefix = "T" + UUID.randomUUID().toString().substring(0, 7);
        service.create(new MemberCreateRequest(prefix + "2", (short) 1, "テスト2", null, "x"), "operator");
        service.create(new MemberCreateRequest(prefix + "1", (short) 1, "テスト1", null, "x"), "operator");
        entityManager.clear();
        var ids = repository.findAllByOrderBySyainIdAsc().stream()
                .map(KenshuMember::getSyainId).filter(id -> id.startsWith(prefix)).toList();
        assertThat(ids).containsExactly(prefix + "1", prefix + "2");
    }
}
