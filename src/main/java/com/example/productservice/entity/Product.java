package com.example.productservice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.persistence.*;
import lombok.Data;
@JsonPropertyOrder({
    "productId",
    "productName",
    "productCategory",
    "productImageUrl",
    "productPrice",
    "productStock",
    "lowStockThreshold"
})
@Table(name="products")
@Entity
@Data
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;
    private String productName;
    // Backed by the Category table (see ProductService.save(), which resolves/normalizes this against it) so
    // catalog categories stay a managed, typo-free set instead of arbitrary free text, while every existing
    // caller that reads/writes this field as a plain string keeps working unchanged.
    private String productCategory;
    // Optional; a plain URL rather than stored binary data - no file upload/storage infra in scope here.
    private String productImageUrl;
    private double productPrice;
    private int productStock;
    // Alerts fire (see ProductService.updateStock/save) when stock drops to or below this. Defaults to 5 when a
    // caller doesn't set it explicitly.
    private int lowStockThreshold = 5;
    // GST rate in percent, one of the standard slabs (see ProductService.GST_RATES). Catalog prices INCLUDE GST; this
    // only says how much of the price is tax, for the invoice. Null = the store's default rate (OrderService decides).
    private Double gstRate;
    // Optional HSN code (4-8 digits) printed on the invoice next to the product.
    @Column(length = 8)
    private String hsnCode;

}
