package com.example.productservice.service;

import com.example.productservice.dto.RatingSummary;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.Review;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.ReviewException;
import com.example.productservice.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ProductService productService;

    public Review addReview(Long productId, String reviewerName, long reviewerPhno, int rating, String comment) {
        return addReview(productId, reviewerName, reviewerPhno, rating, comment, null);
    }

    public Review addReview(Long productId, String reviewerName, long reviewerPhno, int rating, String comment, String photoUrl) {
        productService.findById(productId.intValue());
        validateRating(rating);
        String photo = normalizePhotoUrl(photoUrl);
        reviewRepository.findByProductIdAndReviewerPhno(productId, reviewerPhno).ifPresent(r -> {
            throw new ReviewException("You have already reviewed this product; update your existing review instead");
        });
        Review review = new Review();
        review.setProductId(productId);
        review.setReviewerName(reviewerName);
        review.setReviewerPhno(reviewerPhno);
        review.setRating(rating);
        review.setComment(comment);
        review.setPhotoUrl(photo);
        return reviewRepository.save(review);
    }

    public Review updateReview(Long reviewId, long reviewerPhno, int rating, String comment) {
        return updateReview(reviewId, reviewerPhno, rating, comment, null);
    }

    // photoUrl null leaves the current photo alone (an older client that does not know about photos must not wipe it);
    // an empty string removes it; anything else replaces it after the same validation as when adding.
    public Review updateReview(Long reviewId, long reviewerPhno, int rating, String comment, String photoUrl) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ItemNotFoundException("Review not found"));
        if (review.getReviewerPhno() != reviewerPhno) {
            throw new ReviewException("You can only update your own review");
        }
        validateRating(rating);
        String photo = photoUrl == null ? review.getPhotoUrl() : normalizePhotoUrl(photoUrl);
        review.setRating(rating);
        review.setComment(comment);
        review.setPhotoUrl(photo);
        return reviewRepository.save(review);
    }

    public void deleteReview(Long reviewId, long reviewerPhno) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ItemNotFoundException("Review not found"));
        if (review.getReviewerPhno() != reviewerPhno) {
            throw new ReviewException("You can only delete your own review");
        }
        reviewRepository.delete(review);
    }

    // A product that is one option of a variant group shows the reviews of the WHOLE group (every review row still
    // belongs to the option that was bought - productId on it says which). An ordinary product just shows its own.
    public Page<Review> listReviews(Long productId, Pageable pageable) {
        List<Long> ids = reviewedProductIds(productId);
        if (ids.size() > 1) {
            return reviewRepository.findByProductIdInAndHiddenFalseOrderByCreatedAtDescReviewIdDesc(ids, pageable);
        }
        return reviewRepository.findByProductIdAndHiddenFalseOrderByCreatedAtDescReviewIdDesc(productId, pageable);
    }

    // Same roll-up for the star rating: the average and count are over the whole group when there is one.
    public RatingSummary ratingSummary(Long productId) {
        List<Long> ids = reviewedProductIds(productId);
        Double average;
        long count;
        if (ids.size() > 1) {
            average = reviewRepository.averageRatingForProducts(ids);
            count = reviewRepository.countByProductIdInAndHiddenFalse(ids);
        } else {
            average = reviewRepository.averageRatingForProduct(productId);
            count = reviewRepository.countByProductIdAndHiddenFalse(productId);
        }
        double rounded = average == null ? 0.0 : Math.round(average * 10) / 10.0;
        return new RatingSummary(productId, rounded, count);
    }

    // The product itself plus its variant-group siblings (just itself when it is not in a group). 404s for an unknown id.
    private List<Long> reviewedProductIds(Long productId) {
        Product product = productService.findById(productId.intValue());
        if (product == null || product.getVariantGroup() == null) {
            return List.of(productId);
        }
        List<Long> ids = productService.findVariantGroupMembers(product.getVariantGroup()).stream()
                .map(Product::getProductId).toList();
        return ids.isEmpty() ? List.of(productId) : ids;
    }

    // Anyone can report a review as inappropriate - the same direct-from-customer trust level as posting one in
    // the first place, no ownership check the way update/delete have. Flagging never hides anything by itself;
    // it just surfaces the review in the moderation queue below for an admin to act on.
    public Review flagReview(Long reviewId, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ItemNotFoundException("Review not found"));
        review.setFlagged(true);
        review.setFlagReason(reason == null || reason.isBlank() ? null : reason);
        return reviewRepository.save(review);
    }

    // Admin-only (X-Service-Key, enforced by the default SecurityConfig rule) - not restricted to flagged
    // reviews only, so an admin can also pre-emptively hide one nobody has reported yet.
    public Review hideReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ItemNotFoundException("Review not found"));
        review.setHidden(true);
        return reviewRepository.save(review);
    }

    public Review unhideReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ItemNotFoundException("Review not found"));
        review.setHidden(false);
        return reviewRepository.save(review);
    }

    public Page<Review> getFlaggedReviews(Pageable pageable) {
        return reviewRepository.findByFlaggedTrueAndHiddenFalseOrderByCreatedAtDescReviewIdDesc(pageable);
    }

    // Used by OrderService's cross-service customer profile rollup (via Feign) - not this service's own
    // concept of a customer, just a count of what that phone number has posted here.
    public long getReviewCountForCustomer(long reviewerPhno) {
        return reviewRepository.countByReviewerPhnoAndHiddenFalse(reviewerPhno);
    }

    static final int MAX_PHOTO_URL_LENGTH = 500;

    // Blank means no photo. Otherwise it has to be one absolute http(s) URL with a host and no spaces, quotes or angle
    // brackets - it ends up in an img src, and javascript:, data: and relative URLs have no place there.
    static String normalizePhotoUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        String url = photoUrl.trim();
        if (url.length() > MAX_PHOTO_URL_LENGTH) {
            throw new ReviewException("The photo link can be at most " + MAX_PHOTO_URL_LENGTH + " characters");
        }
        if (!url.matches("(?i)https?://[^\\s<>\"'`]+")) {
            throw new ReviewException("The photo must be a web link starting with http:// or https://");
        }
        try {
            if (java.net.URI.create(url).getHost() == null) {
                throw new ReviewException("The photo link has no host name");
            }
        } catch (IllegalArgumentException e) {
            throw new ReviewException("The photo link is not a valid web address");
        }
        return url;
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ReviewException("Rating must be between 1 and 5");
        }
    }
}
