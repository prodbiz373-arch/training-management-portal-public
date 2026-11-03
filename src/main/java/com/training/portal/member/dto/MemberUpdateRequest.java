package com.training.portal.member.dto;

import jakarta.validation.constraints.*;

public record MemberUpdateRequest(
        @NotNull @Min(1) @Max(2) Short syainKbn,
        @NotBlank @Size(max = 50) String name,
        @Size(max = 100) String nameKana,
        @BCryptInput String password,
        @NotNull @PositiveOrZero Integer updateCount) {
    @Override
    public String toString() {
        return "MemberUpdateRequest[内容非表示]";
    }
}
