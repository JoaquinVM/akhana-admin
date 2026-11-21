package com.akhana.akhana_admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A page of results with a fixed JSON: Spring Data does not promise the shape of a serialized PageImpl. The fields are
 * the ones the screens read (PageResponse in user.models.ts).
 */
public record PageResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int size,
    int number
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getSize(),
            page.getNumber());
    }
}
