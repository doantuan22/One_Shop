package com.oneshop.service.promotion;

import com.oneshop.entity.Promotion;
import com.oneshop.entity.PromotionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The money arithmetic of a promotion, pure and without persistence. Amounts are whole VND (rounded DOWN, in the
 * customer's favour of the shop) and are returned with scale 2 to match the DECIMAL(18,2) columns.
 */
public final class PromotionCalculator {

    private PromotionCalculator() {
    }

    /**
     * Discount of {@code promotion} on {@code goods} (the value of the goods only; the shipping fee is never part of
     * it). PERCENT: the percentage, at most {@code max_discount_amount}; FIXED: the fixed amount. Never more than the
     * goods, never negative.
     */
    public static BigDecimal discountFor(Promotion promotion, BigDecimal goods) {
        if (goods == null || goods.signum() <= 0) {
            return zero();
        }
        BigDecimal discount;
        if (promotion.getDiscountType() == PromotionType.PERCENT) {
            discount = goods.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            if (promotion.getMaxDiscountAmount() != null) {
                discount = discount.min(promotion.getMaxDiscountAmount().setScale(0, RoundingMode.DOWN));
            }
        } else {
            discount = promotion.getDiscountValue().setScale(0, RoundingMode.DOWN);
        }
        return discount.min(goods.setScale(0, RoundingMode.DOWN)).max(BigDecimal.ZERO).setScale(2);
    }

    /**
     * Splits {@code total} between parts in proportion to their {@code weights} so that the shares add up to EXACTLY
     * {@code total} (whole VND, largest remainder first, ties to the earlier part) and no share is larger than its
     * weight. Used twice: the discount of a checkout between its Orders, then the discount of an Order between its
     * lines.
     */
    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        int n = weights.size();
        List<BigDecimal> shares = new ArrayList<>(n);
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total == null || total.signum() <= 0 || weightSum.signum() <= 0) {
            weights.forEach(w -> shares.add(zero()));
            return shares;
        }
        BigDecimal whole = total.setScale(0, RoundingMode.DOWN);
        BigDecimal[] floor = new BigDecimal[n];
        BigDecimal[] fraction = new BigDecimal[n];
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = whole.multiply(weights.get(i)).divide(weightSum, 10, RoundingMode.DOWN);
            floor[i] = exact.setScale(0, RoundingMode.DOWN);
            fraction[i] = exact.subtract(floor[i]);
            assigned = assigned.add(floor[i]);
        }
        int left = whole.subtract(assigned).intValueExact();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        // largest fractional remainder first; the sort is stable, so ties keep the earlier part first
        order.sort((x, y) -> fraction[y].compareTo(fraction[x]));
        for (int i : order) {
            if (left == 0) {
                break;
            }
            if (weights.get(i).setScale(0, RoundingMode.DOWN).subtract(floor[i]).signum() > 0) {
                floor[i] = floor[i].add(BigDecimal.ONE);
                left--;
            }
        }
        for (int i = 0; i < n; i++) {
            shares.add(floor[i].setScale(2));
        }
        return shares;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
