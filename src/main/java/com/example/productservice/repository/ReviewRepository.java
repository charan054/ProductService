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
    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
    Optional<Review> findByProductIdAndReviewerPhno(Long productId, long reviewerPhno);
    long countByProductId(Long productId);

    @Query("select avg(r.rating) from Review r where r.productId = :productId")
    Double averageRatingForProduct(@Param("productId") Long productId);
}
