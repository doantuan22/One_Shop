package com.oneshop;

import com.oneshop.dto.request.PlaceOrderRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.oneshop.entity.FulfillmentType.DELIVERY;
import static com.oneshop.entity.PaymentMethod.COD;

/** The receiver phone needs real digits: 8 to 20 characters, at least 8 digits, an optional leading plus. */
class ReceiverPhoneValidationTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static boolean phoneRejected(String phone) {
        var request = new PlaceOrderRequest(List.of(1L), DELIVERY, COD, "Người Nhận", phone,
                "12 Nguyễn Huệ, Quận 1, TP HCM", null, false, null, null);
        return VALIDATOR.validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("receiverPhone"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "+84912345678", "+84 912 345 678", "091 234 5678", "12345678"})
    void realPhoneNumbersAreAccepted(String phone) {
        assertThat(phoneRejected(phone)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"++++++++", "+ + + + + +", "        ", "1234567", "+1234567", "0912-345-678", "09123abc78",
            "09123456789012345678901", "+84+912345678", "0912345678+"})
    void numbersWithoutEnoughDigitsOrWithStrayCharactersAreRejected(String phone) {
        assertThat(phoneRejected(phone)).isTrue();
    }

    @Test
    void aMissingPhoneIsRejected() {
        assertThat(phoneRejected(null)).isTrue();
    }
}
