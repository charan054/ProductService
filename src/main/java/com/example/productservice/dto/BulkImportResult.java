package com.example.productservice.dto;

import java.util.List;

// One row's failure never aborts the whole file - see ProductService.bulkImportProducts() - so this reports
// what happened per row rather than throwing on the first bad one.
public record BulkImportResult(int successCount, int failureCount, List<RowError> errors) {
    public record RowError(int rowNumber, String message) {
    }
}
