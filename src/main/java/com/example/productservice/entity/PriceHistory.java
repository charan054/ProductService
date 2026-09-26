package com.example.productservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

// One row per actual price change - see ProductService.updatePrice(), which only writes one when the new price
// differs from the old one, so a no-op "update" to the same price leaves no history noise. Append-only, never
// updated or deleted, so this is an audit trail rather than a mutable "current price" field (that's still
// Product.productPrice).
@Data
@Table(name = "price_history")
@Entity
public class PriceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer productId;
    private double oldPrice;
    private double newPrice;
    private Instant changedAt;
}
