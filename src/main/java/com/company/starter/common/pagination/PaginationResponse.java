package com.company.starter.common.pagination;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginationResponse<T>(
        List<T> items,
        int pageIndex,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext
) {

    /**
     * Maps a Spring Data {@link Page} into the API pagination envelope. The page
     * should already hold the response DTOs (use {@link Page#map}) so this layer
     * never leaks entities.
     */
    public static <T> PaginationResponse<T> from(Page<T> page) {
        return new PaginationResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
