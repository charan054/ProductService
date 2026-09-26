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
    Page<Review> findByProductIdAndHiddenFalseOrderByCreatedAtDesc(Long productId, Pageable pageable);
    Optional<Review> findByProductIdAndReviewerPhno(Long productId, long reviewerPhno);
    long countByProductIdAndHiddenFalse(Long productId);
    // The moderation queue: reviews a customer has flagged that an admin hasn't already acted on. Once hidden,
    // a flagged review drops out of this queue - there's nothing left to decide.
    Page<Review> findByFlaggedTrueAndHiddenFalseOrderByCreatedAtDesc(Pageable pageable);

    @Query("select avg(r.rating) from Review r where r.productId = :productId and r.hidden = false")
    Double averageRatingForProduct(@Param("productId") Long productId);
}
