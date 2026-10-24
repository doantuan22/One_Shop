package com.oneshop.dto.response;

import com.oneshop.entity.PromotionStatus;
import com.oneshop.entity.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A promotion as the Administrator sees it.
 *
 * @param state what the code can do right now: ACTIVE (can be used), SCHEDULED (not started), EXPIRED, EXHAUSTED (no
 *              uses left) or INACTIVE (switched off by an Administrator)
 */
public record PromotionResponse(Long id, String code, String name, String description, PromotionType discountType,
                                BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
                                int usageLimit, int usedCount, int perUserLimit, LocalDateTime startAt,
                                LocalDateTime endAt, PromotionStatus status, String state) {

    public int remaining() {
        return Math.max(0, usageLimit - usedCount);
    }
}
