package com.oneshop.service.query;

import com.oneshop.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** JPA pagination has an int offset even though Spring Data's Pageable exposes a long offset. */
public final class Paging {
    public static final int MAX_PAGE_SIZE = 500;
    private Paging() { }

    public static PageRequest page(int page, int size) {
        // Preserve the existing negative-page -> first-page contract; never clamp a large positive page to zero.
        int normalized = Math.max(0, page);
        requireSize(size);
        requireOffset((long) normalized * size);
        return PageRequest.of(normalized, size);
    }

    public static void validate(Pageable pageable) {
        if (pageable.isUnpaged()) return; // internal, already-filtered list readers explicitly use unpaged
        requireSize(pageable.getPageSize());
        requireOffset(pageable.getOffset());
    }

    private static void requireSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw new BadRequestException("size", "Số bản ghi mỗi trang phải từ 1 đến " + MAX_PAGE_SIZE + ".");
    }

    private static void requireOffset(long offset) {
        if (offset < 0 || offset > Integer.MAX_VALUE)
            throw new BadRequestException("page", "Trang yêu cầu vượt giới hạn phân trang được hỗ trợ.");
    }
}
