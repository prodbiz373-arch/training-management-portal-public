package com.training.portal.member.dto;

import jakarta.validation.constraints.*;

public record MemberCreateRequest(
        @NotBlank @Size(max = 10) String syainId,
        @NotNull @Min(1) @Max(2) Short syainKbn,
        @NotBlank @Size(max = 50) String name,
        @Size(max = 100) String nameKana,
        @NotBlank @BCryptInput String password) {
    @Override
    public String toString() {
        return "MemberCreateRequest[内容非表示]";
    }
}
