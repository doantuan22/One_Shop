package com.oneshop;

import com.oneshop.exception.BadRequestException;
import com.oneshop.service.query.SqlDayRange;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class QaFixSqlDayRangeTest {
    @Test void ordinaryEndDateIsInclusiveByExclusiveNextDay() {
        LocalDate day=LocalDate.of(2026,10,24);var range=SqlDayRange.of(day,day);
        assertThat(range.fromInclusive()).isEqualTo(day.atStartOfDay());
        assertThat(range.toExclusive()).isEqualTo(day.plusDays(1).atStartOfDay());
    }
    @Test void maximumSqlDateNeverCreatesYear10000() {
        var range=SqlDayRange.of(LocalDate.of(1,1,1),SqlDayRange.MAX_DATE);
        assertThat(range.fromInclusive().getYear()).isEqualTo(1);
        assertThat(range.toExclusive()).isNull();
    }
    @Test void optionalBoundsAndLeapDays() {
        assertThat(SqlDayRange.of(null,null)).isEqualTo(new SqlDayRange(null,null));
        assertThat(SqlDayRange.of(null,LocalDate.of(2024,2,29)).toExclusive()).isEqualTo(LocalDate.of(2024,3,1).atStartOfDay());
    }
    @Test void invertedAndUnsupportedYearsAreRejectedNotSilentlyEmptied() {
        assertThatThrownBy(()->SqlDayRange.of(LocalDate.of(2026,10,25),LocalDate.of(2026,10,24))).isInstanceOf(BadRequestException.class);
        for(var date:java.util.List.of(LocalDate.MIN,LocalDate.MAX,LocalDate.of(0,1,1),LocalDate.of(10000,1,1)))
            assertThatThrownBy(()->SqlDayRange.of(date,date)).isInstanceOf(BadRequestException.class);
    }
}
