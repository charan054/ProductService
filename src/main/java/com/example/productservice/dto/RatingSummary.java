package com.example.productservice.dto;

public record RatingSummary(Long productId, double averageRating, long reviewCount) {
}
