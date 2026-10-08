package com.example.productservice.dto;

// One line of the reorder list: what is running low, how fast it has been selling, and how much to order.
// soldInPeriod is net of cancels and returns; daysOfCover is null when nothing sold in the period (no rate to divide by).
public record ReorderSuggestion(Long productId, String productName, String variantLabel, String productCategory,
                                int stock, int lowStockThreshold, int soldInPeriod, double soldPerDay,
                                Double daysOfCover, int suggestedQuantity, String reason) {
}
