package com.training.portal.member.dto;

import java.nio.charset.StandardCharsets;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BCryptInputValidator implements ConstraintValidator<BCryptInput, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空欄は更新時の「変更なし」。新規の必須制約は@NotBlankで検証する。
        return value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
