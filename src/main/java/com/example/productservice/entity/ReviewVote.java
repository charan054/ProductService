package com.example.productservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

// One customer's "this review was helpful". Unique per (review, voter); Review.helpfulCount mirrors how many exist.
@Data
@Entity
@Table(name = "review_vote", uniqueConstraints = @UniqueConstraint(columnNames = {"reviewId", "voterPhno"}))
public class ReviewVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long reviewId;
    private long voterPhno;
    private LocalDateTime createdAt = LocalDateTime.now();
}
