package com.example.productservice.dto;

import java.util.List;

// Outcome of POST /product/bulkPriceUpdate. With dryRun=true nothing was changed: CHANGE rows are what WOULD be
// applied. Otherwise CHANGE rows were applied. UNCHANGED = same price already, ERROR = row rejected (see message).
// largeChange marks a move of more than 50% either way, a likely typo worth a second look in the preview.
public record PriceUpdateResult(boolean dryRun, int changed, int unchanged, int failed, List<Row> rows) {
    public record Row(int rowNumber, Integer productId, String productName, Double oldPrice, Double newPrice,
                      String status, String message, boolean largeChange) {
    }
}
