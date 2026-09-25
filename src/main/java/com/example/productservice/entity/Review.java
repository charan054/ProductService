package com.example.productservice.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@JsonPropertyOrder({
        "reviewId",
        "productId",
        "reviewerName",
        "reviewerPhno",
        "rating",
        "comment",
        "createdAt"
})
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"productId", "reviewerPhno"}))
@Entity
@Data
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;
    private Long productId;
    private String reviewerName;
    private long reviewerPhno;
    private int rating;
    private String comment;
    private LocalDateTime createdAt = LocalDateTime.now();
}
