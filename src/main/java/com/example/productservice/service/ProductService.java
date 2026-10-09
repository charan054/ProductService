package com.example.productservice.service;

import com.example.productservice.dto.BulkImportResult;
import com.example.productservice.entity.Category;
import com.example.productservice.entity.PriceHistory;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductImage;
import com.example.productservice.entity.StockMovement;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.PriceException;
import com.example.productservice.exception.StockException;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.PriceHistoryRepository;
import com.example.productservice.repository.ProductImageRepository;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.repository.StockMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PriceHistoryRepository priceHistoryRepository;
    @Autowired
    private ProductImageRepository productImageRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Value("${app.upload-dir}")
    private String uploadDir;
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
        validateGstRate(product.getGstRate());
        product.setHsnCode(normalizeHsnCode(product.getHsnCode()));
        applyVariant(product, product.getVariantGroup(), product.getVariantLabel());
        product.setProductCategory(resolveCategory(product.getProductCategory()));
        Product existing = product.getProductId() == null ? null
                : productRepository.findById(product.getProductId().intValue()).orElse(null);
        Integer existingStock = existing == null ? null : existing.getProductStock();
        // createdAt is the server's to decide: stamped on creation, carried over on an edit, never client-supplied.
        product.setCreatedAt(existing == null ? java.time.Instant.now() : existing.getCreatedAt());
        Product saved = productRepository.save(product);
        if (saved.getProductId() != null) {
            if (existingStock == null) {
                recordMovement(saved.getProductId().intValue(), saved.getProductStock(), saved.getProductStock(),
                        StockMovement.INITIAL, "Product added", null, ADMIN_ACTOR);
            } else if (existingStock != saved.getProductStock()) {
                recordMovement(saved.getProductId().intValue(), saved.getProductStock() - existingStock, saved.getProductStock(),
                        StockMovement.CORRECTION, "Product edited", null, ADMIN_ACTOR);
            }
        }
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
                    if (columnIndex.containsKey("gstRate")) {
                        String rate = cell(values, columnIndex, "gstRate").trim();
                        product.setGstRate(rate.isBlank() ? null : Double.parseDouble(rate));
                    }
                    if (columnIndex.containsKey("hsnCode")) {
                        product.setHsnCode(cell(values, columnIndex, "hsnCode").trim());
                    }
                    if (columnIndex.containsKey("variantGroup")) {
                        product.setVariantGroup(cell(values, columnIndex, "variantGroup").trim());
                    }
                    if (columnIndex.containsKey("variantLabel")) {
                        product.setVariantLabel(cell(values, columnIndex, "variantLabel").trim());
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
    public Product updateStock(int id, int stock) {
        return updateStock(id, stock, null, null, null, null);
    }

    // Same change, with the ledger entry's context: type (SALE/CANCEL/RETURN/RESTOCK/CORRECTION; unknown cause =
    // CORRECTION), a reason, a reference such as "order #42", and who did it. Callers that say nothing still work.
    @Transactional
    public Product updateStock(int id, int stock, String type, String reason, String reference, String actor) {
        String movementType = type == null || type.isBlank() ? StockMovement.CORRECTION : parseMovementType(type);
        int updated = productRepository.adjustStock(id, stock);
        if (updated == 0) {
            // Zero rows updated means either the product doesn't exist, or it does but the delta would have
            // taken stock below zero - re-querying tells the two apart without reintroducing the race above.
            productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
            throw new StockException("Stock is low");
        }
        Product saved = productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
        recordMovement(id, stock, saved.getProductStock(), movementType, reason, reference,
                actorOrDefault(actor, movementType));
        warnIfLowStock(saved);
        return saved;
    }

    // New stock arriving from a supplier: always a positive RESTOCK entry.
    @Transactional
    public Product receiveStock(int id, int quantity, String reason, String reference, String actor) {
        if (quantity <= 0) {
            throw new StockException("Quantity received must be greater than 0");
        }
        return updateStock(id, quantity, StockMovement.RESTOCK, reason, reference, actor);
    }

    // Sets the stock to a counted number (a stocktake, a breakage). The old value is read under a row lock so the
    // delta in the ledger is exact, and a reason is required - a bare number change is how stock becomes a mystery.
    @Transactional
    public Product correctStock(int id, int newStock, String reason, String actor) {
        if (newStock < 0) {
            throw new StockException("Stock must not be negative");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for a stock correction");
        }
        Product product = productRepository.findByIdForUpdate(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
        int delta = newStock - product.getProductStock();
        product.setProductStock(newStock);
        Product saved = productRepository.save(product);
        if (delta != 0) {
            recordMovement(id, delta, newStock, StockMovement.CORRECTION, reason, null, actorOrDefault(actor, StockMovement.CORRECTION));
        }
        warnIfLowStock(saved);
        return saved;
    }

    // Newest first. type narrows it to one kind of movement; limit defaults to 100 and is capped at 500.
    public List<StockMovement> getStockHistory(int id, String type, Integer limit) {
        findById(id);
        int size = limit == null ? 100 : Math.max(1, Math.min(limit, 500));
        PageRequest page = PageRequest.of(0, size);
        if (type == null || type.isBlank()) {
            return stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(id, page);
        }
        return stockMovementRepository.findByProductIdAndTypeOrderByCreatedAtDescIdDesc(id, parseMovementType(type), page);
    }

    private static final String ADMIN_ACTOR = "admin";

    private void recordMovement(int productId, int delta, int stockAfter, String type, String reason, String reference, String actor) {
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setDelta(delta);
        movement.setStockAfter(stockAfter);
        movement.setType(type);
        movement.setReason(clip(reason, 200));
        movement.setReference(clip(reference, 100));
        movement.setActor(actor);
        movement.setCreatedAt(Instant.now());
        stockMovementRepository.save(movement);
    }

    private static String parseMovementType(String type) {
        String upper = type.trim().toUpperCase(Locale.ROOT);
        if (!StockMovement.TYPES.contains(upper)) {
            throw new IllegalArgumentException("Unknown stock movement type: " + type);
        }
        return upper;
    }

    // Sales/cancels/returns come from OrderService; everything else is a person at the admin dashboard.
    private static String actorOrDefault(String actor, String type) {
        if (actor != null && !actor.isBlank()) {
            return clip(actor.trim(), 64);
        }
        boolean fromOrders = StockMovement.SALE.equals(type) || StockMovement.CANCEL.equals(type) || StockMovement.RETURN.equals(type);
        return fromOrders ? "order-service" : ADMIN_ACTOR;
    }

    private static String clip(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
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

    // Sets/replaces productImageUrl on an already-existing product by plain URL - the original add-time-only
    // design (see Product.productImageUrl) had no way to attach or change an image after the fact other than
    // uploadProductImage's real file upload below.
    public Product updateImageUrl(int id, String imageUrl) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("imageUrl must not be blank");
        }
        product.setProductImageUrl(imageUrl.trim());
        return productRepository.save(product);
    }

    // The GST slabs in use in India (percent). Anything else is almost certainly a typo.
    static final Set<Double> GST_RATES = Set.of(0.0, 0.25, 3.0, 5.0, 12.0, 18.0, 28.0);

    // Sets (or clears, with gstRate null) a product's GST rate and HSN code. hsnCode null leaves it unchanged; blank
    // clears it.
    public Product updateTax(int id, Double gstRate, String hsnCode) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
        validateGstRate(gstRate);
        product.setGstRate(gstRate);
        if (hsnCode != null) {
            product.setHsnCode(normalizeHsnCode(hsnCode));
        }
        return productRepository.save(product);
    }

    static final int MAX_VARIANTS_PER_GROUP = 30;

    public List<Product> findVariantGroupMembers(String variantGroup) {
        return productRepository.findByVariantGroupOrderByProductIdAsc(variantGroup);
    }

    /**
     * "Add another option": copies a product into a new option of its group. A product that is not in a group yet is
     * put into one first (named after the product), which is why it then needs its own label (sourceLabel). The copy
     * keeps the name unless a new one is given, and the category, image, GST rate, HSN code, low-stock threshold and
     * price unless a new price is given; it starts with the given stock.
     */
    @Transactional
    public Product addVariantOption(int sourceId, String sourceLabel, String newLabel, String newName, Double newPrice, int newStock) {
        Product source = productRepository.findById(sourceId).orElseThrow(() -> new ItemNotFoundException("Product not found"));
        if (newLabel == null || newLabel.isBlank()) {
            throw new IllegalArgumentException("Give the new option a label, e.g. \"500 g\"");
        }
        String group = source.getVariantGroup();
        if (group == null) {
            if (sourceLabel == null || sourceLabel.isBlank()) {
                throw new IllegalArgumentException("This product is not part of a group yet - say what its own option is called (sourceLabel), e.g. \"340 ml\"");
            }
            group = groupSlug(source);
            updateVariant(sourceId, group, sourceLabel);
        }
        Product copy = new Product();
        copy.setProductName(newName == null || newName.isBlank() ? source.getProductName() : newName.trim());
        copy.setProductCategory(source.getProductCategory());
        copy.setProductImageUrl(source.getProductImageUrl());
        copy.setProductPrice(newPrice == null ? source.getProductPrice() : newPrice);
        copy.setProductStock(newStock);
        copy.setLowStockThreshold(source.getLowStockThreshold());
        copy.setGstRate(source.getGstRate());
        copy.setHsnCode(source.getHsnCode());
        copy.setVariantGroup(group);
        copy.setVariantLabel(newLabel);
        return save(copy);
    }

    // A readable unique slug for a new group: "Dove Shampoo" -> "dove-shampoo" (with the product id added if taken).
    private String groupSlug(Product source) {
        String slug = String.valueOf(source.getProductName()).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 55) {
            slug = slug.substring(0, 55).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            slug = "product";
        }
        return productRepository.findByVariantGroupOrderByProductIdAsc(slug).isEmpty() ? slug : slug + "-" + source.getProductId();
    }

    /**
     * Makes a product an option of a group (or, with both blank, an ordinary product again). A group and a label go
     * together, a label may be used once per group (any case), and a group holds at most 30 options.
     */
    public Product updateVariant(int id, String variantGroup, String variantLabel) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
        applyVariant(product, variantGroup, variantLabel);
        return productRepository.save(product);
    }

    private void applyVariant(Product product, String variantGroup, String variantLabel) {
        boolean noGroup = variantGroup == null || variantGroup.isBlank();
        boolean noLabel = variantLabel == null || variantLabel.isBlank();
        if (noGroup && noLabel) {
            product.setVariantGroup(null);
            product.setVariantLabel(null);
            return;
        }
        if (noGroup || noLabel) {
            throw new IllegalArgumentException("Give both a variant group and a variant label, or neither");
        }
        String group = variantGroup.trim().toLowerCase(java.util.Locale.ROOT);
        if (!group.matches("[a-z0-9][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException("variantGroup must be lowercase letters, digits and dashes, up to 63 characters (e.g. dove-shampoo)");
        }
        String label = variantLabel.trim();
        if (label.length() > 40 || label.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("variantLabel must be at most 40 characters, e.g. \"500 g\"");
        }
        List<Product> others = productRepository.findByVariantGroupOrderByProductIdAsc(group).stream()
                .filter(p -> product.getProductId() == null || !product.getProductId().equals(p.getProductId()))
                .toList();
        if (others.stream().anyMatch(p -> label.equalsIgnoreCase(p.getVariantLabel()))) {
            throw new IllegalArgumentException("The group \"" + group + "\" already has an option labelled \"" + label + "\"");
        }
        if (others.size() >= MAX_VARIANTS_PER_GROUP) {
            throw new IllegalArgumentException("A group can have at most " + MAX_VARIANTS_PER_GROUP + " options");
        }
        product.setVariantGroup(group);
        product.setVariantLabel(label);
    }

    private static void validateGstRate(Double gstRate) {
        if (gstRate != null && !GST_RATES.contains(gstRate)) {
            throw new IllegalArgumentException("gstRate must be one of 0, 0.25, 3, 5, 12, 18 or 28 (percent)");
        }
    }

    private static String normalizeHsnCode(String hsnCode) {
        if (hsnCode == null || hsnCode.isBlank()) {
            return null;
        }
        String code = hsnCode.trim();
        if (!code.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("hsnCode must be 4 to 8 digits");
        }
        return code;
    }

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    // Actual file upload, as opposed to productImageUrl's original plain-URL-only design (see Product.productImageUrl).
    // Saved to a filesystem directory (app.upload-dir, see WebConfig) named by a random UUID rather than the
    // original filename - avoids both a path-traversal filename and two different products' uploads colliding on
    // the same name. The old file (if any) is deliberately left on disk rather than deleted - a stale orphan file
    // is a much smaller problem than a delete racing a request still serving the old image.
    public Product uploadProductImage(int id, MultipartFile file) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Product not found"));
        String url = saveUploadedImageFile(file);
        product.setProductImageUrl(url);
        return productRepository.save(product);
    }

    // Shared by uploadProductImage above (the single cover image) and addGalleryImageUpload below (an
    // additional gallery photo) - both save the same way, just onto different fields/tables.
    private String saveUploadedImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, GIF or WEBP images are allowed");
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            default -> ".webp";
        };
        String filename = java.util.UUID.randomUUID() + extension;
        try {
            java.nio.file.Path targetDir = java.nio.file.Paths.get(uploadDir);
            java.nio.file.Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(filename));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not save the uploaded image: " + e.getMessage());
        }

        // Absolute URL, not a bare "/uploads/xxx" - shop.html (OrderService's origin, a different port) renders
        // this straight into an <img src>, which resolves a relative path against ITS OWN origin, not
        // ProductService's. Cross-origin <img> loading itself needs no CORS config (only fetch/XHR would).
        return currentBaseUrl() + "/uploads/" + filename;
    }

    // ---------- Product image gallery (additional photos beyond the single cover Product.productImageUrl) ----------

    public ProductImage addGalleryImageUrl(int id, String imageUrl) {
        if (!productRepository.existsById(id)) {
            throw new ItemNotFoundException("Product not found");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("imageUrl must not be blank");
        }
        ProductImage image = new ProductImage();
        image.setProductId(id);
        image.setImageUrl(imageUrl.trim());
        return productImageRepository.save(image);
    }

    public ProductImage addGalleryImageUpload(int id, MultipartFile file) {
        if (!productRepository.existsById(id)) {
            throw new ItemNotFoundException("Product not found");
        }
        String url = saveUploadedImageFile(file);
        ProductImage image = new ProductImage();
        image.setProductId(id);
        image.setImageUrl(url);
        return productImageRepository.save(image);
    }

    public List<ProductImage> getGalleryImages(int id) {
        return productImageRepository.findByProductIdOrderByIdAsc(id);
    }

    // Deliberately does NOT delete the underlying uploaded file from disk (if this was a file upload rather than
    // an external URL) - same "leave the old file, don't race a delete against an in-flight request" reasoning
    // as uploadProductImage's replace-the-cover-image path above.
    public void removeGalleryImage(long imageId) {
        if (!productImageRepository.existsById(imageId)) {
            throw new ItemNotFoundException("Image not found");
        }
        productImageRepository.deleteById(imageId);
    }

    // Empty outside a real HTTP request (e.g. a unit test calling this directly) - falls back to the old
    // relative-path behavior rather than throwing, since ServletUriComponentsBuilder needs RequestContextHolder
    // state that only exists during an actual request.
    private String currentBaseUrl() {
        try {
            return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
                    .build().toUriString();
        } catch (IllegalStateException e) {
            return "";
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
