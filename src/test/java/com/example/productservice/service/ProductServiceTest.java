package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.PriceException;
import com.example.productservice.exception.StockException;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService service;

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
        Product p = stored(0, 9.99, 10);
        when(productRepository.save(p)).thenReturn(p);
        Product result = service.save(p);
        assertEquals(9.99, result.getProductPrice());
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

    // ---------- updateStock ----------
    // Regression coverage for a bug where the guard checked the stock BEFORE applying the delta instead of the
    // resulting value, so it never actually stopped stock from going negative.

    @Test
    void updateStockRejectsADeltaThatWouldGoNegative() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        assertThrows(StockException.class, () -> service.updateStock(1, -20));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateStockAllowsADeltaThatLandsExactlyOnZero() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        when(productRepository.save(p)).thenReturn(p);
        Product result = service.updateStock(1, -10);
        assertEquals(0, result.getProductStock());
    }

    @Test
    void updateStockAppliesAPositiveDelta() {
        Product p = stored(1, 9.99, 10);
        when(productRepository.findById(1)).thenReturn(Optional.of(p));
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        when(productRepository.save(any())).thenReturn(p);
        service.updateStock(1, 5);
        verify(productRepository).save(captor.capture());
        assertEquals(15, captor.getValue().getProductStock());
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

    // ---------- findByName / findByCategory / findAll ----------

    @Test
    void findAllDelegatesToTheRepository() {
        List<Product> all = List.of(stored(1, 1, 1), stored(2, 2, 2));
        when(productRepository.findAll()).thenReturn(all);
        assertEquals(all, service.findAll());
    }
}
