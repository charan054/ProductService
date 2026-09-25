package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.PriceException;
import com.example.productservice.exception.StockException;
import com.example.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;
    public Product save(Product product) {
        if(product.getProductPrice()<=0)
        {
            throw new PriceException("Price must be greater than 0");
        }
        if(product.getProductStock()<=0)
        {
            throw new PriceException("Stock must be greater than 0");
        }
        if(product.getLowStockThreshold()<0)
        {
            throw new StockException("lowStockThreshold must not be negative");
        }
        Product saved = productRepository.save(product);
        warnIfLowStock(saved);
        return saved;
    }
    public List<Product> findAll() {
        return productRepository.findAll();
    }
    public List<Product> findByCategory(String category) {
        return productRepository.findByproductCategory(category);
    }
    public List<Product> findByName(String name) {
        return productRepository.findByproductName(name);
    }
    public Product findById(int id) {
        Product product = productRepository.findById(id).orElseThrow( ()-> new ItemNotFoundException("Product not found"));
        return product;
    }

    public void deleteById(int id) {
        productRepository.deleteById(id);
    }
    public Product updateStock(int id, int stock) {
        Product product = productRepository.findById(id).orElseThrow( ()-> new ItemNotFoundException("Product not found"));
        int newStock = product.getProductStock() + stock;
        if(newStock<0)
        {
            throw new StockException("Stock is low");
        }
        product.setProductStock(newStock);
        Product saved = productRepository.save(product);
        warnIfLowStock(saved);
        return saved;
    }
    public Product updateLowStockThreshold(int id, int threshold) {
        Product product = productRepository.findById(id).orElseThrow( ()-> new ItemNotFoundException("Product not found"));
        if(threshold<0)
        {
            throw new StockException("lowStockThreshold must not be negative");
        }
        product.setLowStockThreshold(threshold);
        Product saved = productRepository.save(product);
        warnIfLowStock(saved);
        return saved;
    }
    public List<Product> findLowStockProducts() {
        return productRepository.findLowStockProducts();
    }
    private void warnIfLowStock(Product product) {
        if (product.getProductStock() <= product.getLowStockThreshold()) {
            log.warn("Low stock alert: product {} ({}) has {} unit(s) left, at or below its threshold of {}",
                    product.getProductId(), product.getProductName(), product.getProductStock(), product.getLowStockThreshold());
        }
    }
    public Product updatePrice(int id, double price) {
        Product product = productRepository.findById(id).orElseThrow( ()-> new ItemNotFoundException("Product not found"));
        if(price<=0)
        {
            throw new PriceException("Price must be greater than 0");
        }
        product.setProductPrice(price);
        return productRepository.save(product);
    }
    public void delete(int id) {
        productRepository.deleteById(id);
    }

    public Page<Product> search(String name, String category, Double minPrice, Double maxPrice, Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new PriceException("minPrice must not be greater than maxPrice");
        }
        Specification<Product> spec = null;
        if (name != null && !name.isBlank()) {
            String pattern = "%" + name.toLowerCase() + "%";
            spec = and(spec, (root, query, cb) -> cb.like(cb.lower(root.get("productName")), pattern));
        }
        if (category != null && !category.isBlank()) {
            spec = and(spec, (root, query, cb) -> cb.equal(cb.lower(root.get("productCategory")), category.toLowerCase()));
        }
        if (minPrice != null) {
            spec = and(spec, (root, query, cb) -> cb.ge(root.get("productPrice"), minPrice));
        }
        if (maxPrice != null) {
            spec = and(spec, (root, query, cb) -> cb.le(root.get("productPrice"), maxPrice));
        }
        return spec == null ? productRepository.findAll(pageable) : productRepository.findAll(spec, pageable);
    }

    private Specification<Product> and(Specification<Product> spec, Specification<Product> next) {
        return spec == null ? next : spec.and(next);
    }
}
