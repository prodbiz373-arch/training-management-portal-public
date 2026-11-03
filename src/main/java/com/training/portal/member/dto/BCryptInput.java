package com.training.portal.member.dto;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BCryptInputValidator.class)
public @interface BCryptInput {
    String message() default "パスワードはUTF-8で72バイト以内で入力してください";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
