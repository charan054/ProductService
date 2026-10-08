package com.example.productservice.repository;

import com.example.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {
    public List<Product> findByproductName(String name);
    public List<Product> findByproductCategory(String category);

    List<Product> findByVariantGroupOrderByProductIdAsc(String variantGroup);

    @Query("select p from Product p where p.productStock <= p.lowStockThreshold")
    List<Product> findLowStockProducts();

    // A single atomic UPDATE, not a read-then-write: the WHERE clause re-checks the resulting stock in the same
    // statement the database executes, so two concurrent calls for the last unit can no longer both read the
    // same starting stock and both "succeed" (the classic lost-update race that let stock go oversold/negative).
    // Returns 0 rows updated when either the product doesn't exist or the delta would take stock below zero -
    // ProductService.updateStock() re-queries to tell those two cases apart.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.productStock = p.productStock + :delta where p.productId = :id and p.productStock + :delta >= 0")
    int adjustStock(@Param("id") int id, @Param("delta") int delta);

}
