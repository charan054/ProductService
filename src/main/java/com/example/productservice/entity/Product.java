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
    // The regular price. Code that edits prices (updatePrice, bulk update, history) works on this field; what the
    // catalog JSON calls "productPrice" is livePrice(), which is the sale price while a sale is running.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private double productPrice;
    private int productStock;
    // Alerts fire (see ProductService.updateStock/save) when stock drops to or below this. Defaults to 5 when a
    // caller doesn't set it explicitly.
    private int lowStockThreshold = 5;
    // GST rate in percent, one of the standard slabs (see ProductService.GST_RATES). Catalog prices INCLUDE GST; this
    // only says how much of the price is tax, for the invoice. Null = the store's default rate (OrderService decides).
    private Double gstRate;
    // What one unit costs the shop. Staff-only: @JsonIgnore keeps it out of every catalog response (those are public),
    // and it is set through PUT /product/updateCost and read back through GET /product/costs (both X-Service-Key).
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Double costPrice;
    // Optional HSN code (4-8 digits) printed on the invoice next to the product.
    @Column(length = 8)
    private String hsnCode;
    // Options of one product (sizes, packs, colours): each option is a product row of its own - its own price, stock,
    // GST rate, image and id, so cart, stock and invoices need no special handling - and the rows that are options of
    // the same thing share a variantGroup key (a lowercase slug). variantLabel is what tells them apart ("500 g").
    // Both null = an ordinary product. The product NAME should still say which option it is ("Dove Shampoo - 340 ml"),
    // since that is what a cart line or invoice shows.
    @Column(length = 64)
    private String variantGroup;
    @Column(length = 40)
    private String variantLabel;
    // When the product was first added (UTC). Set by ProductService.save() and never taken from a request body or
    // changed by an edit. Null for products that existed before this field was introduced - they are simply not
    // "new", which is also true.
    private java.time.Instant createdAt;

    // Optional timed sale (set through PUT /product/sale, never from a request body). While now is in
    // [saleStartsAt, saleEndsAt) the catalog reports salePrice as productPrice, so cart, checkout and every other
    // reader of the catalog charge it without knowing about sales.
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Double salePrice;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private java.time.Instant saleStartsAt;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private java.time.Instant saleEndsAt;

    public boolean saleLiveAt(java.time.Instant now) {
        return salePrice != null && saleStartsAt != null && saleEndsAt != null
                && !now.isBefore(saleStartsAt) && now.isBefore(saleEndsAt);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("productPrice")
    public double livePrice() {
        return saleLiveAt(java.time.Instant.now()) ? salePrice : productPrice;
    }

    // Always the regular price, so the storefront can show it struck through during a sale.
    @com.fasterxml.jackson.annotation.JsonProperty("regularPrice")
    public double regularPrice() {
        return productPrice;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("onSale")
    public boolean onSale() {
        return saleLiveAt(java.time.Instant.now());
    }

    @com.fasterxml.jackson.annotation.JsonSetter("productPrice")
    public void setProductPriceFromJson(double price) {
        this.productPrice = price;
    }
}
