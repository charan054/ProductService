package com.example.productservice.repository;

import com.example.productservice.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    // Hidden reviews are excluded from every customer-facing read (listing, average, count) - the same
    // "gone as far as anyone browsing can tell" treatment a delete would give, but reversible via unhideReview().
    // Ordered by createdAt DESC with reviewId DESC as a tiebreaker: createdAt is a LocalDateTime set at entity
    // construction time, and two reviews created back-to-back (as tests routinely do) can land on the same
    // millisecond, which otherwise made "newest first" nondeterministic between them. reviewId is a strictly
    // increasing IDENTITY column, so it's a reliable secondary key for "which one was actually created later".
    Page<Review> findByProductIdAndHiddenFalseOrderByCreatedAtDescReviewIdDesc(Long productId, Pageable pageable);
    Optional<Review> findByProductIdAndReviewerPhno(Long productId, long reviewerPhno);
    long countByProductIdAndHiddenFalse(Long productId);
    // How many (visible) reviews a given customer has left across every product - used by OrderService's
    // cross-service customer profile rollup. Hidden reviews are excluded, same treatment as everywhere else.
    long countByReviewerPhnoAndHiddenFalse(long reviewerPhno);
    // The moderation queue: reviews a customer has flagged that an admin hasn't already acted on. Once hidden,
    // a flagged review drops out of this queue - there's nothing left to decide. Same tiebreaker reasoning as
    // above.
    Page<Review> findByFlaggedTrueAndHiddenFalseOrderByCreatedAtDescReviewIdDesc(Pageable pageable);

    @Query("select avg(r.rating) from Review r where r.productId = :productId and r.hidden = false")
    Double averageRatingForProduct(@Param("productId") Long productId);
}
