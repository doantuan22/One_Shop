package com.oneshop;

import com.oneshop.dto.request.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FSA-02: the password limit of BCrypt is in bytes, so the DTO must count UTF-8 bytes and not characters. */
class MaxUtf8BytesValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static boolean passwordHasError(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Tên");
        request.setUserName("tester01");
        request.setEmail("tester@oneshop.test");
        request.setPassword(password);
        return VALIDATOR.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void asciiPasswordIsLimitedTo72() {
        assertThat(passwordHasError("a".repeat(72))).isFalse();
        assertThat(passwordHasError("a".repeat(73))).isTrue();
        assertThat(passwordHasError("a".repeat(7))).isTrue();
        assertThat(passwordHasError("a".repeat(8))).isFalse();
    }

    @Test
    void multiByteCharactersCountAsTheirUtf8Bytes() {
        assertThat(passwordHasError("ấ".repeat(24))).as("24 x 3 bytes = 72").isFalse();
        assertThat(passwordHasError("ấ".repeat(25))).as("25 x 3 bytes = 75").isTrue();
        assertThat(passwordHasError("ấ".repeat(30))).as("30 characters but 90 bytes").isTrue();
        // 2-byte letters: 36 x 2 = 72
        assertThat(passwordHasError("é".repeat(36))).isFalse();
        assertThat(passwordHasError("é".repeat(37))).isTrue();
        // mixed: 8 ASCII + 21 x 3 = 71, +1 more 3-byte = 74
        assertThat(passwordHasError("abcdefgh" + "ế".repeat(21))).isFalse();
        assertThat(passwordHasError("abcdefgh" + "ế".repeat(22))).isTrue();
    }
}
