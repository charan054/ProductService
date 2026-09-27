package com.example.productservice.entity;

import jakarta.persistence.*;
import lombok.Data;

// One additional gallery photo for a product, on top of Product.productImageUrl (the cover/primary image shown
// on the storefront card and in search results) - a product can have any number of these, shown in the
// product-details modal. addedAt ordering (ascending id, since these are never reordered) keeps them stable.
@Data
@Table(name = "product_image")
@Entity
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer productId;
    private String imageUrl;
}
