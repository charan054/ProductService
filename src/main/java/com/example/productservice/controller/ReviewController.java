package com.example.productservice.controller;

import com.example.productservice.dto.PublicReview;
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
    public Page<PublicReview> getReviews(@PathVariable Long productId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return reviewService.listReviews(productId, PageRequest.of(page, size)).map(PublicReview::from);
    }

    // Full reviews including reviewerPhno, X-Service-Key only (the path has 4 segments so none of the public
    // "/product/*/reviews" matchers apply). OrderService uses it to compute the Verified-purchase badge.
    @GetMapping("/internal/{productId}/reviews")
    public Page<Review> getReviewsInternal(@PathVariable Long productId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return reviewService.listReviews(productId, PageRequest.of(page, size));
    }

    @GetMapping("/{productId}/rating-summary")
    public RatingSummary getRatingSummary(@PathVariable Long productId) {
        return reviewService.ratingSummary(productId);
    }

    // Public, same trust level as posting a review - reporting one is a customer action, not an admin decision.
    @PostMapping("/{productId}/reviews/{reviewId}/flag")
    public Review flagReview(@PathVariable Long reviewId, @RequestParam(required = false) String reason) {
        return reviewService.flagReview(reviewId, reason);
    }

    // Admin-only (falls under the default X-Service-Key-authenticated rule - no path here matches any of the
    // public review patterns in SecurityConfig).
    @PutMapping("/{productId}/reviews/{reviewId}/hide")
    public Review hideReview(@PathVariable Long reviewId) {
        return reviewService.hideReview(reviewId);
    }

    @PutMapping("/{productId}/reviews/{reviewId}/unhide")
    public Review unhideReview(@PathVariable Long reviewId) {
        return reviewService.unhideReview(reviewId);
    }

    @GetMapping("/reviews/flagged")
    public Page<Review> getFlaggedReviews(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return reviewService.getFlaggedReviews(PageRequest.of(page, size));
    }

    // Public, same customer-generated-content trust level as browsing reviews - a count, not any one review's
    // content. Called by OrderService (via Feign) to build its cross-service customer profile.
    @GetMapping("/reviews/count")
    public long getReviewCount(@RequestParam long phno) {
        return reviewService.getReviewCountForCustomer(phno);
    }
}
