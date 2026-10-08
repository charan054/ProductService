package com.example.productservice.controller;

import com.example.productservice.dto.BulkImportResult;
import com.example.productservice.entity.PriceHistory;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductImage;
import com.example.productservice.entity.StockMovement;
import com.example.productservice.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/product")
public class ProductController {
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "productId", "productName", "productCategory", "productPrice", "productStock");

    @Autowired
    private ProductService productService;
    @PostMapping("/add")
    public Product addProduct(@RequestBody Product product){
        return productService.save(product);
    }
    // Same X-Service-Key boundary as add above - falls under the default authenticated rule, no SecurityConfig
    // changes needed.
    @PostMapping("/bulkImport")
    public BulkImportResult bulkImportProducts(@RequestParam("file") MultipartFile file){
        return productService.bulkImportProducts(file);
    }
    // Same X-Service-Key boundary as add/bulkImport above - falls under the default authenticated rule, no
    // SecurityConfig changes needed. The saved file itself is served back out publicly at /uploads/** (see
    // WebConfig/SecurityConfig) since a product image needs to be viewable by anyone browsing the catalog.
    @PostMapping("/{id}/image")
    public Product uploadProductImage(@PathVariable Integer id, @RequestParam("file") MultipartFile file){
        return productService.uploadProductImage(id, file);
    }
    // Same X-Service-Key boundary as the cover-image endpoints above - an admin action, not a customer one.
    @PostMapping("/{id}/images")
    public ProductImage addGalleryImageUrl(@PathVariable Integer id, @RequestParam String imageUrl){
        return productService.addGalleryImageUrl(id, imageUrl);
    }
    @PostMapping("/{id}/images/upload")
    public ProductImage addGalleryImageUpload(@PathVariable Integer id, @RequestParam("file") MultipartFile file){
        return productService.addGalleryImageUpload(id, file);
    }
    // Public, same catalog-browsing trust level as the other product GETs above - a product's gallery photos are
    // just as visible as its cover image and price.
    @GetMapping("/{id}/images")
    public List<ProductImage> getGalleryImages(@PathVariable Integer id){
        return productService.getGalleryImages(id);
    }
    @DeleteMapping("/images/{imageId}")
    public void removeGalleryImage(@PathVariable Long imageId){
        productService.removeGalleryImage(imageId);
    }
    @GetMapping("/all")
    public List<Product> getAllProducts(){
        return productService.findAll();
    }
    @GetMapping("/search")
    public Page<Product> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "productId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir){
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("sortBy must be one of " + SORTABLE_FIELDS);
        }
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return productService.search(name, category, minPrice, maxPrice, pageable);
    }
    @GetMapping("/byId")
    public Product getProductById(@RequestParam Integer id){
        return productService.findById(id);
    }
    // Same X-Service-Key boundary as updateTax. Send both, or neither to make it an ordinary product again.
    @PutMapping("/updateVariant")
    public Product updateProductVariant(@RequestParam Integer id, @RequestParam(required = false) String variantGroup,
                                        @RequestParam(required = false) String variantLabel){
        return productService.updateVariant(id, variantGroup, variantLabel);
    }
    // Same public trust level as the other catalog-browsing GETs above.
    @GetMapping("/related")
    public List<Product> getRelatedProducts(@RequestParam Integer id, @RequestParam(required = false) Integer limit){
        return productService.getRelatedProducts(id, limit);
    }
    @GetMapping("/byName")
    public List<Product> getProductByName(@RequestParam String name){
        return productService.findByName(name);
    }
    @GetMapping("/byCategory")
    public List<Product> getProductByCategory(@RequestParam String category){
        return productService.findByCategory(category);
    }
    @PutMapping("/updatePrice")
    public Product updateProductPrice(@RequestParam Integer id, @RequestParam Double price){
        return productService.updatePrice(id, price);
    }
    // Same X-Service-Key boundary as updatePrice/updateStock above - falls under the default authenticated rule.
    @PutMapping("/updateImageUrl")
    public Product updateProductImageUrl(@RequestParam Integer id, @RequestParam String imageUrl){
        return productService.updateImageUrl(id, imageUrl);
    }
    // Same X-Service-Key boundary as updateImageUrl above. gstRate omitted = clear it (use the store default).
    @PutMapping("/updateTax")
    public Product updateProductTax(@RequestParam Integer id, @RequestParam(required = false) Double gstRate,
                                    @RequestParam(required = false) String hsnCode){
        return productService.updateTax(id, gstRate, hsnCode);
    }
    // Same public trust level as the other catalog-browsing GETs above.
    @GetMapping("/priceHistory")
    public List<PriceHistory> getPriceHistory(@RequestParam Integer id){
        return productService.getPriceHistory(id);
    }
    // stock is a signed DELTA. type/reason/reference/actor are optional ledger context (see StockMovement); OrderService
    // sends SALE / CANCEL / RETURN with the order number, an admin leaving them out gets a CORRECTION entry.
    @PutMapping("/updateStock")
    public Product updateProductStock(@RequestParam Integer id, @RequestParam Integer stock,
                                      @RequestParam(required = false) String type, @RequestParam(required = false) String reason,
                                      @RequestParam(required = false) String reference, @RequestParam(required = false) String actor){
        return productService.updateStock(id, stock, type, reason, reference, actor);
    }
    // Stock arriving from a supplier (a positive RESTOCK entry in the ledger).
    @PostMapping("/{id}/receive")
    public Product receiveStock(@PathVariable Integer id, @RequestParam Integer quantity,
                                @RequestParam(required = false) String reason, @RequestParam(required = false) String reference,
                                @RequestParam(required = false) String actor){
        return productService.receiveStock(id, quantity, reason, reference, actor);
    }
    // Sets stock to a counted number; a reason is required.
    @PostMapping("/{id}/stock/correct")
    public Product correctStock(@PathVariable Integer id, @RequestParam Integer newStock, @RequestParam String reason,
                                @RequestParam(required = false) String actor){
        return productService.correctStock(id, newStock, reason, actor);
    }
    // Every change to this product's stock, newest first. Admin only (not in the public GET list).
    @GetMapping("/{id}/stock-history")
    public List<StockMovement> getStockHistory(@PathVariable Integer id, @RequestParam(required = false) String type,
                                               @RequestParam(required = false) Integer limit){
        return productService.getStockHistory(id, type, limit);
    }
    @PutMapping("/updateLowStockThreshold")
    public Product updateLowStockThreshold(@RequestParam Integer id, @RequestParam Integer threshold){
        return productService.updateLowStockThreshold(id, threshold);
    }
    @GetMapping("/lowStock")
    public List<Product> getLowStockProducts(){
        return productService.findLowStockProducts();
    }
    @DeleteMapping("/delete")
    public void deleteProduct(@RequestParam Integer id){
        productService.deleteById(id);
    }

}
