package com.example.productservice.controller;

import com.example.productservice.dto.RatingSummary;
import com.example.productservice.entity.Review;
import com.example.productservice.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product")
public class ReviewController {
    @Autowired
    private ReviewService reviewService;

    @PostMapping("/{productId}/reviews")
    public Review addReview(@PathVariable Long productId, @RequestBody Review review) {
        return reviewService.addReview(productId, review.getReviewerName(), review.getReviewerPhno(),
                review.getRating(), review.getComment());
    }

    @PutMapping("/{productId}/reviews/{reviewId}")
    public Review updateReview(@PathVariable Long reviewId, @RequestBody Review review) {
        return reviewService.updateReview(reviewId, review.getReviewerPhno(), review.getRating(), review.getComment());
    }

    @DeleteMapping("/{productId}/reviews/{reviewId}")
    public void deleteReview(@PathVariable Long reviewId, @RequestParam long reviewerPhno) {
        reviewService.deleteReview(reviewId, reviewerPhno);
    }

    @GetMapping("/{productId}/reviews")
    public Page<Review> getReviews(@PathVariable Long productId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return reviewService.listReviews(productId, PageRequest.of(page, size));
    }

    @GetMapping("/{productId}/rating-summary")
    public RatingSummary getRatingSummary(@PathVariable Long productId) {
        return reviewService.ratingSummary(productId);
    }
}
