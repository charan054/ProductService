package com.example.productservice.repository;

import com.example.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {
    public List<Product> findByproductName(String name);
    public List<Product> findByproductCategory(String category);

    @Query("select p from Product p where p.productStock <= p.lowStockThreshold")
    List<Product> findLowStockProducts();

}
