package com.example.productservice.repository;

import com.example.productservice.entity.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    // Newest first; id breaks ties between two changes in the same instant.
    List<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Integer productId, Pageable pageable);

    List<StockMovement> findByProductIdAndTypeOrderByCreatedAtDescIdDesc(Integer productId, String type, Pageable pageable);
}
