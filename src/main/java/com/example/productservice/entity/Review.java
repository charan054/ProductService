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
        "createdAt",
        "flagged",
        "flagReason",
        "hidden"
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
    // Set by ReviewService.flagReview() - a customer reporting a review, not a moderation decision itself. A
    // flagged review still shows up publicly until an admin actually hides it.
    private boolean flagged;
    private String flagReason;
    // Set by ReviewService.hideReview()/unhideReview() - the actual moderation decision. Hidden reviews are
    // excluded from listReviews() and ratingSummary() (both the average and the count), same as if deleted, but
    // recoverable via unhideReview() unlike an actual delete.
    private boolean hidden;
}
