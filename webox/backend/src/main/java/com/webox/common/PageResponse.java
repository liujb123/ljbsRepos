package com.webox.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Uniform pagination payload returned inside {@link ApiResponse#data()}.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / (double) size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    public static <T> PageResponse<T> single(List<T> items, int size) {
        return new PageResponse<>(items, 0, size, items.size(), items.isEmpty() ? 0 : 1);
    }
}
