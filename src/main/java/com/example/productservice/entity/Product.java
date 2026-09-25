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
    private String productCategory;
    private double productPrice;
    private int productStock;
    // Alerts fire (see ProductService.updateStock/save) when stock drops to or below this. Defaults to 5 when a
    // caller doesn't set it explicitly.
    private int lowStockThreshold = 5;

}
