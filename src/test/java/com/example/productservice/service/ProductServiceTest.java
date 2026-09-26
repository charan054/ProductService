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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private ProductService service;

    private void stubCategoryLookupCreatesNew() {
        when(categoryRepository.findByCategoryNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product stored(int id, double price, int stock) {
        Product p = new Product();
        p.setProductId((long) id);
        p.setProductName("Widget");
        p.setProductCategory("misc");
        p.setProductPrice(price);
        p.setProductStock(stock);
        return p;
    }

    // ---------- save ----------

    @Test
    void saveRejectsNonPositivePrice() {
        Product p = stored(0, 0, 10);
        assertThrows(PriceException.class, () -> service.save(p));
        verify(productRepository, never()).save(any());
    }

    @Test
    void saveRejectsNonPositiveStock() {
        Product p = stored(0, 9.99, 0);
        assertThrows(PriceException.class, () -> service.save(p));
        verify(productRepository, never()).save(any());
    }

    @Test
    void saveStoresAValidProduct() {
        stubCategoryLookupCreatesNew();
        Product p = stored(0, 9.99, 10);
        when(productRepository.save(p)).thenReturn(p);
        Product result = service.save(p);
        assertEquals(9.99, result.getProductPrice());
    }

    @Test
    void saveRejectsABlankCategory() {
        Product p = stored(0, 9.99, 10);
        p.setProductCategory("  ");
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
        verify(productRepository, never()).save(any());
    }

    @Test
    void saveReusesAnExistingCategoryCaseInsensitivelyAndNormalizesCasing() {
        Category existing = new Category();
        existing.setCategoryId(1L);
        existing.setCategoryName("Shampoo");
        when(categoryRepository.findByCategoryNameIgnoreCase("shampoo")).thenReturn(Optional.of(existing));
        Product p = stored(0, 9.99, 10);
        p.setProductCategory("shampoo");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.save(p);

        assertEquals("Shampoo", result.getProductCategory());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void saveCreatesANewCategoryWhenNoneMatches() {
        stubCategoryLookupCreatesNew();
        Product p = stored(0, 9.99, 10);
        p.setProductCategory("Gadgets");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.save(p);

        assertEquals("Gadgets", result.getProductCategory());
        verify(categoryRepository).save(any(Category.class));
    }

    // ---------- findById ----------

    @Test
    void findByIdThrowsWhenMissing() {
        when(productRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(ItemNotFoundException.class, () -> service.findById(99));
    }

    @Test
    void findByIdReturnsTheStoredProduct() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        assertEquals(p, service.findById(1));
    }

    // ---------- getRelatedProducts ----------

    @Test
    void getRelatedProductsThrowsWhenTheProductDoesNotExist() {
        when(productRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(ItemNotFoundException.class, () -> service.getRelatedProducts(99, null));
    }

    @Test
    void getRelatedProductsExcludesTheProductItself() {
        Product target = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(target));
        Product sameCategory = stored(2, 12.0, 5);
        // findByproductCategory("misc") would never return a product from a different category in the real
        // repository - only the target and other same-category products are stubbed here.
        when(productRepository.findByproductCategory("misc")).thenReturn(List.of(target, sameCategory));

        List<Product> result = service.getRelatedProducts(1, null);

        assertEquals(List.of(sameCategory), result);
    }

    @Test
    void getRelatedProductsDefaultsToFiveResults() {
        Product target = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(target));
        List<Product> others = IntStream.rangeClosed(2, 8)
                .mapToObj(i -> stored(i, 9.99, 10))
                .toList();
        List<Product> allInCategory = new ArrayList<>();
        allInCategory.add(target);
        allInCategory.addAll(others);
        when(productRepository.findByproductCategory("misc")).thenReturn(allInCategory);

        List<Product> result = service.getRelatedProducts(1, null);

        assertEquals(5, result.size());
    }

    @Test
    void getRelatedProductsRespectsAnExplicitLimit() {
        Product target = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(target));
        Product other = stored(2, 9.99, 10);
        when(productRepository.findByproductCategory("misc")).thenReturn(List.of(target, other));

        List<Product> result = service.getRelatedProducts(1, 1);

        assertEquals(1, result.size());
    }

    @Test
    void getRelatedProductsRejectsANonPositiveLimit() {
        Product target = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(target));
        assertThrows(IllegalArgumentException.class, () -> service.getRelatedProducts(1, 0));
    }

    @Test
    void getRelatedProductsRejectsALimitAboveTheMaximum() {
        Product target = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(target));
        assertThrows(IllegalArgumentException.class, () -> service.getRelatedProducts(1, 21));
    }

    // ---------- updateStock ----------
    // updateStock() now delegates the actual arithmetic to a single atomic conditional UPDATE
    // (ProductRepository.adjustStock) instead of reading the entity, checking the resulting value in Java, and
    // saving it back - that read-modify-write let two concurrent requests for the last unit both read the same
    // starting stock and both pass their own guard (a lost-update race). These tests cover the two outcomes
    // adjustStock can report (0 rows vs 1 row updated) and how updateStock() turns each into the right exception
    // or result, not the arithmetic itself - that lives in the query, not in Java, on purpose.

    @Test
    void updateStockRejectsADeltaThatWouldGoNegative() {
        when(productRepository.adjustStock(1, -20)).thenReturn(0);
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));

        assertThrows(StockException.class, () -> service.updateStock(1, -20));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateStockThrowsNotFoundWhenTheProductDoesNotExist() {
        when(productRepository.adjustStock(99, -1)).thenReturn(0);
        when(productRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> service.updateStock(99, -1));
    }

    @Test
    void updateStockAllowsADeltaThatLandsExactlyOnZero() {
        when(productRepository.adjustStock(1, -10)).thenReturn(1);
        Product p = stored(1, 9.99, 0);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));

        Product result = service.updateStock(1, -10);

        assertEquals(0, result.getProductStock());
    }

    @Test
    void updateStockAppliesAPositiveDelta() {
        when(productRepository.adjustStock(1, 5)).thenReturn(1);
        Product p = stored(1, 9.99, 15);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));

        Product result = service.updateStock(1, 5);

        assertEquals(15, result.getProductStock());
    }

    // ---------- updatePrice ----------

    @Test
    void updatePriceRejectsNonPositivePrice() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        assertThrows(PriceException.class, () -> service.updatePrice(1, 0));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updatePriceThrowsWhenProductMissing() {
        when(productRepository.findById(1)).thenReturn(Optional.empty());
        assertThrows(ItemNotFoundException.class, () -> service.updatePrice(1, 5.0));
    }

    @Test
    void updatePriceRecordsAHistoryEntryWhenThePriceActuallyChanges() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updatePrice(1, 12.99);

        ArgumentCaptor<PriceHistory> captor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getProductId());
        assertEquals(9.99, captor.getValue().getOldPrice());
        assertEquals(12.99, captor.getValue().getNewPrice());
    }

    // Setting a product's price to what it already is isn't a change - nothing worth recording happened.
    @Test
    void updatePriceRecordsNoHistoryWhenThePriceIsUnchanged() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updatePrice(1, 9.99);

        verify(priceHistoryRepository, never()).save(any());
    }

    // ---------- getPriceHistory ----------

    @Test
    void getPriceHistoryThrowsWhenTheProductDoesNotExist() {
        when(productRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(ItemNotFoundException.class, () -> service.getPriceHistory(99));
        verify(priceHistoryRepository, never()).findByProductIdOrderByChangedAtDesc(any());
    }

    @Test
    void getPriceHistoryReturnsTheProductsHistoryNewestFirst() {
        Product p = stored(1, 12.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        PriceHistory entry = new PriceHistory();
        entry.setProductId(1);
        entry.setOldPrice(9.99);
        entry.setNewPrice(12.99);
        when(priceHistoryRepository.findByProductIdOrderByChangedAtDesc(1)).thenReturn(List.of(entry));

        List<PriceHistory> result = service.getPriceHistory(1);

        assertEquals(List.of(entry), result);
    }

    // ---------- findByName / findByCategory / findAll ----------

    @Test
    void findAllDelegatesToTheRepository() {
        List<Product> all = List.of(stored(1, 1, 1), stored(2, 2, 2));
        when(productRepository.findAll()).thenReturn(all);
        assertEquals(all, service.findAll());
    }

    // ---------- low-stock threshold ----------

    @Test
    void savedProductGetsTheDefaultThresholdWhenNotSpecified() {
        stubCategoryLookupCreatesNew();
        Product p = new Product();
        p.setProductName("Widget");
        p.setProductCategory("misc");
        p.setProductPrice(9.99);
        p.setProductStock(10);
        when(productRepository.save(p)).thenReturn(p);
        Product result = service.save(p);
        assertEquals(5, result.getLowStockThreshold());
    }

    @Test
    void saveRejectsANegativeThreshold() {
        Product p = stored(0, 9.99, 10);
        p.setLowStockThreshold(-1);
        assertThrows(StockException.class, () -> service.save(p));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateLowStockThresholdRejectsNegative() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        assertThrows(StockException.class, () -> service.updateLowStockThreshold(1, -1));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateLowStockThresholdAppliesTheChange() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        when(productRepository.save(p)).thenReturn(p);
        Product result = service.updateLowStockThreshold(1, 8);
        assertEquals(8, result.getLowStockThreshold());
    }

    @Test
    void updateLowStockThresholdThrowsWhenProductMissing() {
        when(productRepository.findById(1)).thenReturn(Optional.empty());
        assertThrows(ItemNotFoundException.class, () -> service.updateLowStockThreshold(1, 3));
    }

    @Test
    void findLowStockProductsDelegatesToTheRepository() {
        List<Product> lowStock = List.of(stored(1, 9.99, 2));
        when(productRepository.findLowStockProducts()).thenReturn(lowStock);
        assertEquals(lowStock, service.findLowStockProducts());
    }

    // ---------- categories ----------

    @Test
    void saveCategoryRejectsABlankName() {
        Category c = new Category();
        c.setCategoryName(" ");
        assertThrows(IllegalArgumentException.class, () -> service.saveCategory(c));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void saveCategoryCreatesANewOneWhenNoneMatches() {
        when(categoryRepository.findByCategoryNameIgnoreCase("Gadgets")).thenReturn(Optional.empty());
        Category input = new Category();
        input.setCategoryName("Gadgets");
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = service.saveCategory(input);

        assertEquals("Gadgets", result.getCategoryName());
    }

    @Test
    void saveCategoryReturnsTheExistingOneInsteadOfDuplicating() {
        Category existing = new Category();
        existing.setCategoryId(1L);
        existing.setCategoryName("Shampoo");
        when(categoryRepository.findByCategoryNameIgnoreCase("shampoo")).thenReturn(Optional.of(existing));
        Category input = new Category();
        input.setCategoryName("shampoo");

        Category result = service.saveCategory(input);

        assertEquals(existing, result);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void getCategoriesDelegatesToTheRepository() {
        Category c = new Category();
        c.setCategoryName("Shampoo");
        when(categoryRepository.findAll()).thenReturn(List.of(c));
        assertEquals(1, service.getCategories().size());
    }

    // ---------- bulkImportProducts ----------

    private void stubSavesToPassThrough() {
        stubCategoryLookupCreatesNew();
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "products.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bulkImportProductsRejectsAnEmptyFile() {
        assertThrows(IllegalArgumentException.class, () -> service.bulkImportProducts(csv("")));
    }

    @Test
    void bulkImportProductsRejectsAHeaderMissingARequiredColumn() {
        MockMultipartFile file = csv("productName,productCategory,productPrice\nWidget,misc,9.99\n");
        assertThrows(IllegalArgumentException.class, () -> service.bulkImportProducts(file));
    }

    @Test
    void bulkImportProductsImportsEveryValidRow() {
        stubSavesToPassThrough();
        MockMultipartFile file = csv(
                "productName,productCategory,productPrice,productStock\n" +
                "Widget,misc,9.99,10\n" +
                "Gadget,misc,19.99,5\n");

        BulkImportResult result = service.bulkImportProducts(file);

        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        assertEquals(List.of(), result.errors());
        verify(productRepository, times(2)).save(any(Product.class));
    }

    @Test
    void bulkImportProductsAppliesOptionalColumns() {
        stubSavesToPassThrough();
        MockMultipartFile file = csv(
                "productName,productCategory,productPrice,productStock,productImageUrl,lowStockThreshold\n" +
                "Widget,misc,9.99,10,https://example.com/w.png,2\n");

        service.bulkImportProducts(file);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("https://example.com/w.png", captor.getValue().getProductImageUrl());
        assertEquals(2, captor.getValue().getLowStockThreshold());
    }

    @Test
    void bulkImportProductsRecordsAFailingRowWithoutStoppingTheRest() {
        stubSavesToPassThrough();
        MockMultipartFile file = csv(
                "productName,productCategory,productPrice,productStock\n" +
                "BadRow,misc,-5,10\n" +
                "GoodRow,misc,9.99,10\n");

        BulkImportResult result = service.bulkImportProducts(file);

        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(1, result.errors().get(0).rowNumber());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void bulkImportProductsSkipsBlankLines() {
        stubSavesToPassThrough();
        MockMultipartFile file = csv(
                "productName,productCategory,productPrice,productStock\n" +
                "\n" +
                "Widget,misc,9.99,10\n");

        BulkImportResult result = service.bulkImportProducts(file);

        assertEquals(1, result.successCount());
        assertEquals(0, result.failureCount());
    }
}
