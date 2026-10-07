package com.example.productservice.dto;

import com.example.productservice.entity.Review;

import java.time.LocalDateTime;

// What the public review listing returns - a Review minus reviewerPhno. That number doubles as the ownership
// check for update/delete, so exposing it publicly would let anyone edit or remove someone else's review.
public record PublicReview(Long reviewId, Long productId, String reviewerName, int rating, String comment,
                           LocalDateTime createdAt, boolean flagged, String flagReason, boolean hidden) {
    public static PublicReview from(Review r) {
        return new PublicReview(r.getReviewId(), r.getProductId(), r.getReviewerName(), r.getRating(),
                r.getComment(), r.getCreatedAt(), r.isFlagged(), r.getFlagReason(), r.isHidden());
    }
}
