package com.example.productservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// Explicit JSON for a page of results. Returning Spring's PageImpl directly logs "Serializing PageImpl instances
// as-is is not supported" because its JSON shape is not guaranteed to stay stable; this pins the fields callers use
// (OrderService reads content and last). The nested pageable/sort objects of the old shape are not included.
public record PageResponse<T>(List<T> content, boolean last, boolean first, int totalPages, long totalElements,
                              int size, int number, int numberOfElements, boolean empty) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.isLast(), page.isFirst(), page.getTotalPages(),
                page.getTotalElements(), page.getSize(), page.getNumber(), page.getNumberOfElements(), page.isEmpty());
    }
}
