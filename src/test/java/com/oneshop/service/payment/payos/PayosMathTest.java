package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayosMathTest {

    private final PayosProperties limits = new PayosProperties();

    @Test
    void wholeVndIsConvertedExactlyWithoutRounding() {
        assertThat(PayosMath.toVnd(new BigDecimal("200000.00"), limits)).isEqualTo(200_000L);
        assertThat(PayosMath.toVnd(new BigDecimal("1234567"), limits)).isEqualTo(1_234_567L);
    }

    @Test
    void fractionalAmountIsRefusedNeverRounded() {
        assertThatThrownBy(() -> PayosMath.toVnd(new BigDecimal("200000.50"), limits))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PayosMath.toVnd(new BigDecimal("199999.99"), limits))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void amountOutsideTheLimitsIsRefusedClearly() {
        assertThatThrownBy(() -> PayosMath.toVnd(new BigDecimal("1000"), limits))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("tối thiểu");
        assertThatThrownBy(() -> PayosMath.toVnd(new BigDecimal("500000001"), limits))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("tối đa");
        assertThatThrownBy(() -> PayosMath.toVnd(null, limits)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PayosMath.toVnd(new BigDecimal("0"), limits)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PayosMath.toVnd(new BigDecimal("-5000"), limits)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void orderCodeIsPositiveWithinPayosLimitAndDoesNotCollideWithinASecond() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 16, 0, 0);
        Set<Long> codes = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            long code = PayosMath.newOrderCode(now, zone);
            assertThat(code).isBetween(1L, PayosMath.MAX_ORDER_CODE);
            codes.add(code);
        }
        // 100000 values per second: 2000 draws collide with probability ~ 2%; the caller re-checks the database anyway
        assertThat(codes.size()).isGreaterThan(1900);
    }

    @Test
    void orderCodeStaysInRangeFarIntoTheFuture() {
        long code = PayosMath.newOrderCode(LocalDateTime.of(2099, 12, 31, 23, 59, 59), ZoneId.of("UTC"));
        assertThat(code).isLessThanOrEqualTo(PayosMath.MAX_ORDER_CODE);
    }

    @Test
    void bankTimeIsConvertedFromVietnamTimeToTheServerZone() {
        LocalDateTime utc = PayosMath.bankTime("2026-10-27 16:30:00", ZoneId.of("Asia/Ho_Chi_Minh"), ZoneId.of("UTC"));
        assertThat(utc).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 30, 0));
        assertThat(PayosMath.bankTime("garbage", ZoneId.of("UTC"), ZoneId.of("UTC"))).isNull();
        assertThat(PayosMath.bankTime(null, ZoneId.of("UTC"), ZoneId.of("UTC"))).isNull();
    }

    @Test
    void descriptionFitsThePayosLimit() {
        assertThat(PayosMath.description(123L)).isEqualTo("OneShop 123");
        assertThat(PayosMath.description(Long.MAX_VALUE).length()).isLessThanOrEqualTo(25);
    }
}
