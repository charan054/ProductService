package com.example.productservice.service;

import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.PriceException;
import com.example.productservice.exception.StockException;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
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
        product.setProductCategory(resolveCategory(product.getProductCategory()));
        Product saved = productRepository.save(product);
        warnIfLowStock(saved);
        return saved;
    }

    // Finds-or-creates the Category by name (case-insensitive) and returns its canonical stored name, so
    // "Shampoo" and "shampoo" end up as the exact same category instead of two near-duplicates.
    private String resolveCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("Product category is required");
        }
        String trimmed = categoryName.trim();
        return categoryRepository.findByCategoryNameIgnoreCase(trimmed)
                .map(Category::getCategoryName)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setCategoryName(trimmed);
                    return categoryRepository.save(category).getCategoryName();
                });
    }

    public Category saveCategory(Category category) {
        if (category.getCategoryName() == null || category.getCategoryName().isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        String name = category.getCategoryName().trim();
        return categoryRepository.findByCategoryNameIgnoreCase(name)
                .orElseGet(() -> {
                    Category fresh = new Category();
                    fresh.setCategoryName(name);
                    return categoryRepository.save(fresh);
                });
    }

    public List<Category> getCategories() {
        return categoryRepository.findAll();
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
    // A single atomic conditional UPDATE (see ProductRepository.adjustStock) rather than the previous
    // read-modify-write, which let two concurrent requests for the last unit both read the same starting stock
    // and both pass their own "would this go negative" check - a lost-update race that oversold stock under
    // real concurrency despite this exact guard being present.
    @Transactional
    public Product updateStock(int id, int stock) {
        int updated = productRepository.adjustStock(id, stock);
        if (updated == 0) {
            // Zero rows updated means either the product doesn't exist, or it does but the delta would have
            // taken stock below zero - re-querying tells the two apart without reintroducing the race above.
            productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
            throw new StockException("Stock is low");
        }
        Product saved = productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
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
