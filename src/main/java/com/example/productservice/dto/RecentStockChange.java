package com.example.productservice.dto;

import java.time.Instant;

// One hand-made stock change (a CORRECTION or a RESTOCK) from the stock ledger, with the product name, for the admin
// daily digest (GET /product/stock-movements/recent).
public record RecentStockChange(Integer productId, String productName, String type, int delta, int stockAfter,
                                String reason, String reference, String actor, Instant createdAt) {
}
