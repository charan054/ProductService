package com.example.productservice.repository;

import com.example.productservice.entity.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    // Newest first; id breaks ties between two changes in the same instant.
    List<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Integer productId, Pageable pageable);

    List<StockMovement> findByProductIdAndTypeOrderByCreatedAtDescIdDesc(Integer productId, String type, Pageable pageable);

    // Movements in [from, to), oldest first, optionally narrowed to one product and/or one type.
    @Query("select m from StockMovement m where m.createdAt >= :from and m.createdAt < :to "
            + "and (:productId is null or m.productId = :productId) and (:type is null or m.type = :type) order by m.id asc")
    List<StockMovement> search(@Param("from") Instant from, @Param("to") Instant to, @Param("productId") Integer productId,
                               @Param("type") String type, Pageable pageable);

    // Net change per product caused by orders since a moment: sales (negative) plus cancels and returns (positive).
    @Query("select m.productId, sum(m.delta) from StockMovement m where m.createdAt >= :since "
            + "and m.type in ('SALE', 'CANCEL', 'RETURN') group by m.productId")
    List<Object[]> netOrderChangeSince(@Param("since") Instant since);
}
