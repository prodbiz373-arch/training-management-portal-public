package com.training.portal.member.dto;

import com.training.portal.member.KenshuMember;

public record MemberResponse(String syainId, Short syainKbn, String name,
        String nameKana, Integer updateCount) {
    public static MemberResponse from(KenshuMember member) {
        return new MemberResponse(member.getSyainId(), member.getSyainKbn(),
                member.getName(), member.getNameKana(), member.getUpdateCount());
    }
}
