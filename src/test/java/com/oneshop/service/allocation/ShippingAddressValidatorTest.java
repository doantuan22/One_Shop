package com.oneshop.service.allocation;

import com.oneshop.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Structural validation only: the schema has no structured address and no way to verify the country. */
class ShippingAddressValidatorTest {

    private final ShippingAddressValidator validator = new ShippingAddressValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "12 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
            "Thôn 3, xã Ea Kao, TP. Buôn Ma Thuột, Đắk Lắk",
            "5 Hàng Bài, Hoàn Kiếm, Hà Nội",
            "101 Võ Văn Ngân   Thủ Đức"})
    void realLookingAddressesAreAccepted(String address) {
        assertThat(validator.requireValid(address)).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "1234567890", "x y z", "123 456 789 000", ".,.,.,.,.,.,.",
            "<script>alert(1)</script> abc def"})
    void unusableTextIsRefused(String address) {
        assertThatThrownBy(() -> validator.requireValid(address)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Too long a text is refused, whitespace is normalised")
    void lengthAndWhitespace() {
        assertThatThrownBy(() -> validator.requireValid("a b ".repeat(200))).isInstanceOf(BadRequestException.class);
        assertThat(validator.requireValid("  12   Lê Lợi,   Quận 1   ")).isEqualTo("12 Lê Lợi, Quận 1");
    }
}
