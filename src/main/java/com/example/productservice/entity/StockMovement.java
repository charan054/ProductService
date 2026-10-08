package com.example.productservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

// One row per change to a product's stock - the "why is this at 3?" ledger. Append-only like PriceHistory: a row is
// never edited or deleted, and for a product with history the deltas add up to its current stock (existing products
// got an INITIAL "opening balance" row when the ledger was introduced, see V2__stock_movement.sql).
// actor is who made the change: "order-service" for sales/cancels/returns, otherwise whatever name the admin gave
// (self-declared - the only credential ProductService sees is the shared X-Service-Key).
@Data
@Table(name = "stock_movement", indexes = @Index(name = "idx_stock_movement_product", columnList = "productId, createdAt"))
@Entity
public class StockMovement {
    public static final String SALE = "SALE";
    public static final String CANCEL = "CANCEL";
    public static final String RETURN = "RETURN";
    public static final String RESTOCK = "RESTOCK";
    public static final String CORRECTION = "CORRECTION";
    public static final String INITIAL = "INITIAL";
    public static final Set<String> TYPES = Set.of(SALE, CANCEL, RETURN, RESTOCK, CORRECTION, INITIAL);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer productId;
    // Signed: negative takes stock out (a sale), positive puts it in.
    private int delta;
    private int stockAfter;
    @Column(length = 20, nullable = false)
    private String type;
    @Column(length = 200)
    private String reason;
    // What caused it, e.g. "order #42" or a supplier delivery note number.
    @Column(length = 100)
    private String reference;
    @Column(length = 64, nullable = false)
    private String actor;
    private Instant createdAt;
}
