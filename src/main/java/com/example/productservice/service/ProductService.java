package com.example.productservice.service;

import com.example.productservice.dto.BulkImportResult;
import com.example.productservice.entity.Category;
import com.example.productservice.entity.PriceHistory;
import com.example.productservice.entity.Product;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.PriceException;
import com.example.productservice.exception.StockException;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.PriceHistoryRepository;
import com.example.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PriceHistoryRepository priceHistoryRepository;
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

    private static final List<String> REQUIRED_CSV_COLUMNS =
            List.of("productName", "productCategory", "productPrice", "productStock");

    // One bad row must never sink the whole file - each row is saved through the same save() above (so it gets
    // the same validation and category resolution as adding a product one at a time), and a row's failure is
    // recorded rather than thrown, so the rest of the file still gets a chance. A simple split-on-comma parser,
    // not a full CSV parser - no support for quoted fields containing commas, which is a real limitation for
    // free-text values like product names, but keeps this dependency-free and matches the file's existing style.
    public BulkImportResult bulkImportProducts(MultipartFile file) {
        List<BulkImportResult.RowError> errors = new ArrayList<>();
        int successCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnIndex.put(headers[i].trim(), i);
            }
            for (String required : REQUIRED_CSV_COLUMNS) {
                if (!columnIndex.containsKey(required)) {
                    throw new IllegalArgumentException("CSV header is missing required column: " + required);
                }
            }

            String line;
            int rowNumber = 1; // 1 = the first data row, right after the header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    rowNumber++;
                    continue;
                }
                try {
                    String[] values = line.split(",", -1);
                    Product product = new Product();
                    product.setProductName(cell(values, columnIndex, "productName").trim());
                    product.setProductCategory(cell(values, columnIndex, "productCategory").trim());
                    product.setProductPrice(Double.parseDouble(cell(values, columnIndex, "productPrice").trim()));
                    product.setProductStock(Integer.parseInt(cell(values, columnIndex, "productStock").trim()));
                    if (columnIndex.containsKey("productImageUrl")) {
                        String imageUrl = cell(values, columnIndex, "productImageUrl").trim();
                        product.setProductImageUrl(imageUrl.isBlank() ? null : imageUrl);
                    }
                    if (columnIndex.containsKey("lowStockThreshold")) {
                        String threshold = cell(values, columnIndex, "lowStockThreshold").trim();
                        if (!threshold.isBlank()) {
                            product.setLowStockThreshold(Integer.parseInt(threshold));
                        }
                    }
                    save(product);
                    successCount++;
                } catch (RuntimeException e) {
                    errors.add(new BulkImportResult.RowError(rowNumber, e.getMessage()));
                }
                rowNumber++;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read CSV file: " + e.getMessage());
        }
        return new BulkImportResult(successCount, errors.size(), errors);
    }

    private String cell(String[] values, Map<String, Integer> columnIndex, String column) {
        int index = columnIndex.get(column);
        if (index >= values.length) {
            throw new IllegalArgumentException("Row is missing a value for column: " + column);
        }
        return values[index];
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

    private static final int DEFAULT_RELATED_LIMIT = 5;
    private static final int MAX_RELATED_LIMIT = 20;

    // "You might also like": other products in the same category, excluding the product itself. Simple
    // same-category matching rather than anything behavioral (no purchase-history/co-occurrence data exists
    // anywhere in this system to base a smarter recommendation on).
    public List<Product> getRelatedProducts(int id, Integer limit) {
        Product product = findById(id);
        int effectiveLimit = limit == null ? DEFAULT_RELATED_LIMIT : limit;
        if (effectiveLimit <= 0 || effectiveLimit > MAX_RELATED_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_RELATED_LIMIT);
        }
        return productRepository.findByproductCategory(product.getProductCategory()).stream()
                .filter(p -> !p.getProductId().equals(product.getProductId()))
                .limit(effectiveLimit)
                .toList();
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
        double oldPrice = product.getProductPrice();
        product.setProductPrice(price);
        Product saved = productRepository.save(product);
        // Only an actual change is worth a history row - a no-op "update" to the same price would otherwise
        // create noise with nothing to show for it.
        if (oldPrice != price) {
            PriceHistory history = new PriceHistory();
            history.setProductId(id);
            history.setOldPrice(oldPrice);
            history.setNewPrice(price);
            history.setChangedAt(Instant.now());
            priceHistoryRepository.save(history);
        }
        return saved;
    }

    // Newest first, same convention as every other history/timeline endpoint in this codebase.
    public List<PriceHistory> getPriceHistory(int id) {
        findById(id);
        return priceHistoryRepository.findByProductIdOrderByChangedAtDesc(id);
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
