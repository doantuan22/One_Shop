package com.oneshop.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The text must encode to at most {@link #value()} bytes in UTF-8. {@code @Size} counts characters, but BCrypt
 * limits the password to 72 <em>bytes</em>, and a Vietnamese letter takes 2-3 bytes. A {@code null} value is valid
 * (combine with {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Giá trị vượt quá {value} byte";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
