package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.exception.BadRequestException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Pure conversions between OneShop values and payOS values. */
public final class PayosMath {

    /** payOS orderCode must fit a JavaScript safe integer. */
    public static final long MAX_ORDER_CODE = 9_007_199_254_740_991L;
    private static final long EPOCH_OFFSET = 1_700_000_000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter BANK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PayosMath() {
    }

    /**
     * Whole VND exactly: a fractional amount is refused, never rounded (the link must equal the Order total).
     *
     * @throws BadRequestException when the amount is not a whole number inside the configured limits
     */
    public static long toVnd(BigDecimal amount, PayosProperties limits) {
        if (amount == null) {
            throw new BadRequestException("Đơn hàng không có số tiền thanh toán.");
        }
        long vnd;
        try {
            vnd = amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Số tiền đơn hàng phải là số nguyên đồng để thanh toán qua payOS.");
        }
        if (vnd < limits.getMinAmount()) {
            throw new BadRequestException("Số tiền đơn hàng thấp hơn mức tối thiểu "
                    + limits.getMinAmount() + " đ của thanh toán trực tuyến.");
        }
        if (vnd > limits.getMaxAmount()) {
            throw new BadRequestException("Số tiền đơn hàng vượt mức tối đa "
                    + limits.getMaxAmount() + " đ của thanh toán trực tuyến; vui lòng chọn phương thức khác.");
        }
        return vnd;
    }

    /** Same conversion without limits, for comparing stored money with what payOS reports. */
    public static Long exactVnd(BigDecimal amount) {
        try {
            return amount == null ? null : amount.longValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    /**
     * A unique, stable payOS orderCode: seconds since a fixed epoch times 100000 plus a random 0..99999 suffix
     * (below 2.5e14 until the year 2100, far under 2^53-1). It is stored on the Payment row before payOS is called and
     * then reused, so a retry never invents a second code for the same attempt. A timestamp alone would collide.
     */
    public static long newOrderCode(LocalDateTime now, ZoneId zone) {
        long seconds = now.atZone(zone).toEpochSecond() - EPOCH_OFFSET;
        if (seconds < 1) {
            throw new IllegalStateException("System clock is before 2023-11-14; refuse to build an orderCode.");
        }
        long code = seconds * 100_000L + RANDOM.nextInt(100_000);
        if (code < 1 || code > MAX_ORDER_CODE) {
            throw new IllegalStateException("orderCode out of range");
        }
        return code;
    }

    /** payOS {@code transactionDateTime} ("yyyy-MM-dd HH:mm:ss", Vietnam time) in the server zone, or {@code null}. */
    public static LocalDateTime bankTime(String text, ZoneId bankZone, ZoneId serverZone) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), BANK_TIME).atZone(bankZone).withZoneSameInstant(serverZone)
                    .toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** payOS description: at most 25 characters, plain ASCII. */
    public static String description(Long orderId) {
        String text = "OneShop " + orderId;
        return text.length() > 25 ? text.substring(0, 25) : text;
    }
}
