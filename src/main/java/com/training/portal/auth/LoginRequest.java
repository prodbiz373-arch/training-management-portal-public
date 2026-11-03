package com.training.portal.auth;

import com.training.portal.member.dto.BCryptInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 10) String syainId,
        @NotBlank @BCryptInput String password) {
    @Override
    public String toString() {
        return "LoginRequest[内容非表示]";
    }
}
