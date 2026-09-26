package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.Review;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.ReviewException;
import com.example.productservice.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ProductService productService;
    @InjectMocks
    private ReviewService service;

    private Product product(long id) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName("Widget");
        p.setProductCategory("misc");
        p.setProductPrice(9.99);
        p.setProductStock(10);
        return p;
    }

    private Review review(long reviewId, long productId, long reviewerPhno, int rating) {
        Review r = new Review();
        r.setReviewId(reviewId);
        r.setProductId(productId);
        r.setReviewerName("Alice");
        r.setReviewerPhno(reviewerPhno);
        r.setRating(rating);
        r.setComment("Good");
        return r;
    }

    // ---------- addReview ----------

    @Test
    void addReviewThrowsWhenProductMissing() {
        when(productService.findById(1)).thenThrow(new ItemNotFoundException("Product not found"));
        assertThrows(ItemNotFoundException.class, () -> service.addReview(1L, "Alice", 9999999999L, 5, "Great"));
    }

    @Test
    void addReviewRejectsRatingOutOfRange() {
        when(productService.findById(1)).thenReturn(product(1));
        assertThrows(ReviewException.class, () -> service.addReview(1L, "Alice", 9999999999L, 6, "Great"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void addReviewRejectsASecondReviewFromTheSameReviewer() {
        when(productService.findById(1)).thenReturn(product(1));
        when(reviewRepository.findByProductIdAndReviewerPhno(1L, 9999999999L))
                .thenReturn(Optional.of(review(1, 1, 9999999999L, 4)));
        assertThrows(ReviewException.class, () -> service.addReview(1L, "Alice", 9999999999L, 5, "Great"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void addReviewStoresAValidReview() {
        when(productService.findById(1)).thenReturn(product(1));
        when(reviewRepository.findByProductIdAndReviewerPhno(1L, 9999999999L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Review result = service.addReview(1L, "Alice", 9999999999L, 5, "Great");
        assertEquals(5, result.getRating());
        assertEquals(1L, result.getProductId());
    }

    // ---------- updateReview ----------

    @Test
    void updateReviewRejectsAnotherReviewersAttempt() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review(1, 1, 9999999999L, 4)));
        assertThrows(ReviewException.class, () -> service.updateReview(1L, 1111111111L, 5, "Changed"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void updateReviewAppliesTheChange() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review(1, 1, 9999999999L, 4)));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Review result = service.updateReview(1L, 9999999999L, 2, "Changed my mind");
        assertEquals(2, result.getRating());
        assertEquals("Changed my mind", result.getComment());
    }

    // ---------- deleteReview ----------

    @Test
    void deleteReviewRejectsAnotherReviewersAttempt() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review(1, 1, 9999999999L, 4)));
        assertThrows(ReviewException.class, () -> service.deleteReview(1L, 1111111111L));
        verify(reviewRepository, never()).delete(any());
    }

    // ---------- ratingSummary ----------

    @Test
    void ratingSummaryReturnsZeroWhenNoReviewsExist() {
        when(productService.findById(1)).thenReturn(product(1));
        when(reviewRepository.averageRatingForProduct(1L)).thenReturn(null);
        when(reviewRepository.countByProductIdAndHiddenFalse(1L)).thenReturn(0L);
        var summary = service.ratingSummary(1L);
        assertEquals(0.0, summary.averageRating());
        assertEquals(0L, summary.reviewCount());
    }

    @Test
    void ratingSummaryRoundsAverageToOneDecimal() {
        when(productService.findById(1)).thenReturn(product(1));
        when(reviewRepository.averageRatingForProduct(1L)).thenReturn(4.666666);
        when(reviewRepository.countByProductIdAndHiddenFalse(1L)).thenReturn(3L);
        var summary = service.ratingSummary(1L);
        assertEquals(4.7, summary.averageRating());
        assertEquals(3L, summary.reviewCount());
    }

    // ---------- flagReview / hideReview / unhideReview / getFlaggedReviews ----------

    @Test
    void flagReviewThrowsWhenMissing() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ItemNotFoundException.class, () -> service.flagReview(1L, "Spam"));
    }

    @Test
    void flagReviewSetsFlaggedAndReason() {
        Review r = review(1, 1, 9999999999L, 4);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Review result = service.flagReview(1L, "Spam");

        assertTrue(result.isFlagged());
        assertEquals("Spam", result.getFlagReason());
    }

    @Test
    void flagReviewTreatsABlankReasonAsNone() {
        Review r = review(1, 1, 9999999999L, 4);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Review result = service.flagReview(1L, "  ");

        assertNull(result.getFlagReason());
    }

    @Test
    void hideReviewThrowsWhenMissing() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ItemNotFoundException.class, () -> service.hideReview(1L));
    }

    @Test
    void hideReviewSetsHidden() {
        Review r = review(1, 1, 9999999999L, 4);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Review result = service.hideReview(1L);

        assertTrue(result.isHidden());
    }

    @Test
    void unhideReviewClearsHidden() {
        Review r = review(1, 1, 9999999999L, 4);
        r.setHidden(true);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Review result = service.unhideReview(1L);

        assertFalse(result.isHidden());
    }

    @Test
    void getReviewCountForCustomerDelegatesToTheRepository() {
        when(reviewRepository.countByReviewerPhnoAndHiddenFalse(9999999999L)).thenReturn(3L);
        assertEquals(3L, service.getReviewCountForCustomer(9999999999L));
    }

    @Test
    void getFlaggedReviewsDelegatesToTheRepository() {
        Review r = review(1, 1, 9999999999L, 4);
        r.setFlagged(true);
        when(reviewRepository.findByFlaggedTrueAndHiddenFalseOrderByCreatedAtDescReviewIdDesc(any()))
                .thenReturn(new PageImpl<>(List.of(r)));

        var result = service.getFlaggedReviews(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
    }
}
