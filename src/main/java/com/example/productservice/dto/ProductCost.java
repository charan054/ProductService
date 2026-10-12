package com.example.productservice.dto;

// One product with its cost price (GET /product/costs, service key only) - the only place a cost ever leaves this service.
public record ProductCost(long productId, String productName, double productPrice, double costPrice) {
}
