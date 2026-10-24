package com.oneshop;

import com.oneshop.dto.request.ProductRequest;
import com.oneshop.entity.ProductStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** A product that is on sale needs a real online price; a hidden one may still be saved without. */
class ProductRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static ProductRequest request(String price, ProductStatus status) {
        ProductRequest request = new ProductRequest();
        request.setCategoryId(1L);
        request.setBrandId(1L);
        request.setSku("QA-1");
        request.setName("QA");
        request.setPrice(new BigDecimal(price));
        request.setStatus(status);
        return request;
    }

    @Test
    void anActiveProductWithPriceZeroIsRejected() {
        assertThat(validator.validate(request("0", ProductStatus.ACTIVE)))
                .extracting(v -> v.getPropertyPath().toString()).contains("activePriceValid");
    }

    @Test
    void anActiveProductWithARealPriceIsAccepted() {
        assertThat(validator.validate(request("129000", ProductStatus.ACTIVE))).isEmpty();
    }

    @Test
    void aNonActiveProductMayStillHavePriceZero() {
        assertThat(validator.validate(request("0", ProductStatus.INACTIVE))).isEmpty();
    }
}
