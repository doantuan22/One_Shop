package com.oneshop.service;

import com.oneshop.entity.Promotion;
import com.oneshop.entity.PromotionType;
import com.oneshop.service.promotion.PromotionCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The money arithmetic of a promotion: no database, no Spring. */
class PromotionCalculatorTest {

    private static Promotion percent(String value, String cap) {
        Promotion p = new Promotion();
        p.setDiscountType(PromotionType.PERCENT);
        p.setDiscountValue(new BigDecimal(value));
        p.setMaxDiscountAmount(cap == null ? null : new BigDecimal(cap));
        return p;
    }

    private static Promotion fixed(String value) {
        Promotion p = new Promotion();
        p.setDiscountType(PromotionType.FIXED);
        p.setDiscountValue(new BigDecimal(value));
        return p;
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("a percentage is taken off the goods and rounded down to whole VND")
    void percentRoundsDown() {
        assertThat(PromotionCalculator.discountFor(percent("10", null), d("333335"))).isEqualByComparingTo("33333");
        assertThat(PromotionCalculator.discountFor(percent("15", null), d("100001"))).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("the cap limits a percentage; it never raises it")
    void capLimitsAPercentage() {
        assertThat(PromotionCalculator.discountFor(percent("10", "50000"), d("1000000"))).isEqualByComparingTo("50000");
        assertThat(PromotionCalculator.discountFor(percent("10", "50000"), d("300000"))).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("a fixed discount is never more than the goods, and a zero or negative basis gives nothing")
    void fixedNeverExceedsTheGoods() {
        assertThat(PromotionCalculator.discountFor(fixed("30000"), d("500000"))).isEqualByComparingTo("30000");
        assertThat(PromotionCalculator.discountFor(fixed("30000"), d("20000"))).isEqualByComparingTo("20000");
        assertThat(PromotionCalculator.discountFor(fixed("30000"), BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(PromotionCalculator.discountFor(percent("100", null), d("80000"))).isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("allocation adds up to EXACTLY the total, in whole VND, never above a weight")
    void allocationIsExact() {
        List<BigDecimal> shares = PromotionCalculator.allocate(d("10"), List.of(d("10"), d("10"), d("10")));
        assertThat(shares).hasSize(3);
        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10");
        assertThat(shares).allSatisfy(s -> assertThat(s.scale()).isEqualTo(2));

        List<BigDecimal> weights = List.of(d("138000"), d("71000"), d("340000"), d("178000"));
        for (String total : List.of("1", "7", "99999", "50000", "727000")) {
            List<BigDecimal> parts = PromotionCalculator.allocate(d(total), weights);
            assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).as(total).isEqualByComparingTo(total);
            for (int i = 0; i < parts.size(); i++) {
                assertThat(parts.get(i)).as(total + " part " + i).isLessThanOrEqualTo(weights.get(i));
                assertThat(parts.get(i).signum()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("the shares follow the weights; a zero total or zero weights give zero shares")
    void allocationFollowsTheWeights() {
        List<BigDecimal> shares = PromotionCalculator.allocate(d("30000"), List.of(d("100000"), d("200000")));
        assertThat(shares.get(0)).isEqualByComparingTo("10000");
        assertThat(shares.get(1)).isEqualByComparingTo("20000");
        assertThat(PromotionCalculator.allocate(BigDecimal.ZERO, List.of(d("5"), d("5"))))
                .allSatisfy(s -> assertThat(s).isEqualByComparingTo("0"));
        assertThat(PromotionCalculator.allocate(d("10"), List.of(BigDecimal.ZERO, BigDecimal.ZERO)))
                .allSatisfy(s -> assertThat(s).isEqualByComparingTo("0"));
    }

    @Test
    @DisplayName("a full discount leaves every line worth 0, never negative")
    void fullDiscountLeavesZero() {
        List<BigDecimal> weights = List.of(d("138000"), d("71000"));
        List<BigDecimal> shares = PromotionCalculator.allocate(d("209000"), weights);
        assertThat(shares.get(0)).isEqualByComparingTo("138000");
        assertThat(shares.get(1)).isEqualByComparingTo("71000");
    }
}
