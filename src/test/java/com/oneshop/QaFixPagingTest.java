package com.oneshop;

import com.oneshop.exception.BadRequestException;
import com.oneshop.service.query.Paging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class QaFixPagingTest {
    @ParameterizedTest @ValueSource(ints={1,12,20,200,500}) void exactTechnicalOffsetBoundary(int size) {
        int last=Integer.MAX_VALUE/size;
        assertThat(Paging.page(last,size).getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
        if(last<Integer.MAX_VALUE)assertThatThrownBy(()->Paging.page(last+1,size)).isInstanceOf(BadRequestException.class);
        if(last>0)assertThat(Paging.page(last-1,size).getOffset()).isLessThan(Integer.MAX_VALUE);
    }
    @ParameterizedTest @ValueSource(ints={Integer.MIN_VALUE,-1,0,501,Integer.MAX_VALUE}) void invalidSizeIsControlled(int size) {
        assertThatThrownBy(()->Paging.page(0,size)).isInstanceOf(BadRequestException.class);
    }
    @Test void negativePageRemainsFirstPageButLargePositiveIsNotClamped() {
        assertThat(Paging.page(-1,20).getPageNumber()).isZero();
        assertThat(Paging.page(1,20).getOffset()).isEqualTo(20);
        assertThatThrownBy(()->Paging.page(Integer.MAX_VALUE,20)).isInstanceOf(BadRequestException.class);
    }
}
