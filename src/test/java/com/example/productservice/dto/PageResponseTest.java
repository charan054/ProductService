package com.example.productservice.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {
    @Test
    void copiesTheFieldsCallersRead() {
        PageResponse<String> r = PageResponse.of(new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5));

        assertEquals(List.of("a", "b"), r.content());
        assertFalse(r.last());
        assertTrue(r.first());
        assertEquals(3, r.totalPages());
        assertEquals(5, r.totalElements());
        assertEquals(0, r.number());
        assertEquals(2, r.numberOfElements());
    }

    @Test
    void lastPageIsMarkedLast() {
        assertTrue(PageResponse.of(new PageImpl<>(List.of("a"), PageRequest.of(2, 2), 5)).last());
    }
}
