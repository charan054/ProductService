package com.example.productservice.repository;

import com.example.productservice.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    // The same reads over several products at once - the options of one variant group share their reviews.
    Page<Review> findByProductIdInAndHiddenFalseOrderByCreatedAtDescReviewIdDesc(Collection<Long> productIds, Pageable pageable);
    long countByProductIdInAndHiddenFalse(Collection<Long> productIds);

    @Query("select avg(r.rating) from Review r where r.productId in :productIds and r.hidden = false")
    Double averageRatingForProducts(@Param("productIds") Collection<Long> productIds);

    @Query("select avg(r.rating) from Review r where r.productId = :productId and r.hidden = false")
    Double averageRatingForProduct(@Param("productId") Long productId);

    // Every photo link any review still points at, hidden reviews included (hiding is reversible, so a hidden
    // review's photo is still wanted). OrderService's photo tidy-up deletes the files nothing in this list names.
    @Query("select r.photoUrl from Review r where r.photoUrl is not null and r.photoUrl <> ''")
    List<String> findAllPhotoUrls();

    // Account deletion: the reviews stay (their stars and words are the shop's) but stop pointing at a person. The name
    // becomes a placeholder, the phone number becomes -reviewId (unique per review, so the (product, reviewer) unique key
    // still holds, and never a valid mobile number), and the photo link is dropped (the file is then unreferenced and
    // OrderService's photo tidy-up removes it). Returns how many reviews changed.
    @Modifying
    @Query("update Review r set r.reviewerName = 'Deleted customer', r.reviewerPhno = 0 - r.reviewId, r.photoUrl = null "
            + "where r.reviewerPhno = :phno and r.reviewerPhno > 0")
    int anonymiseByReviewerPhno(@Param("phno") long phno);
}
