package com.training.portal.member;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KenshuMemberRepository
        extends JpaRepository<KenshuMember, String> {

    List<KenshuMember> findAllByOrderBySyainIdAsc();

}
