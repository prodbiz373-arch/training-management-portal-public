package com.training.portal.schedule.dto;

import com.training.portal.member.KenshuMember;

public record ScheduleMemberOptionResponse(
        String syainId,
        short syainKbn,
        String name,
        String nameKana) {

    public static ScheduleMemberOptionResponse from(KenshuMember member) {
        return new ScheduleMemberOptionResponse(
                member.getSyainId(), member.getSyainKbn(), member.getName(), member.getNameKana());
    }
}
