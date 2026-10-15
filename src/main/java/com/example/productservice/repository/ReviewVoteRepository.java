package com.example.productservice.repository;

import com.example.productservice.entity.ReviewVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewVoteRepository extends JpaRepository<ReviewVote, Long> {
    Optional<ReviewVote> findByReviewIdAndVoterPhno(Long reviewId, long voterPhno);
    long countByReviewId(Long reviewId);

    @Query("select v.reviewId from ReviewVote v where v.voterPhno = :phno")
    List<Long> findReviewIdsByVoter(@Param("phno") long phno);
}
