package com.oneshop.service.query;

import com.oneshop.exception.BadRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Inclusive calendar dates mapped to SQL Server datetime2's [0001,9999] range without losing end-day precision. */
public record SqlDayRange(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
    public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    public static SqlDayRange of(LocalDate from, LocalDate to) {
        requireSupported(from, "from");
        requireSupported(to, "to");
        if (from != null && to != null && from.isAfter(to))
            throw new BadRequestException("to", "Ngày kết thúc không được trước ngày bắt đầu.");
        // No SQL datetime2 value can be beyond MAX_DATE: no upper predicate includes its entire last day,
        // including datetime2(7) fractions, without binding year 10000 or rounding a fabricated last instant.
        return new SqlDayRange(from == null ? null : from.atStartOfDay(),
                to == null || to.equals(MAX_DATE) ? null : to.plusDays(1).atStartOfDay());
    }

    private static void requireSupported(LocalDate value, String field) {
        if (value != null && (value.getYear() < 1 || value.getYear() > 9999))
            throw new BadRequestException(field, "Ngày phải nằm trong khoảng 0001-01-01 đến 9999-12-31.");
    }
}
