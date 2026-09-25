package com.example.productservice.controller;

import com.example.productservice.entity.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /product/lowStock (public, like other catalog browsing) and PUT /product/updateLowStockThreshold
 * (X-Service-Key required, like the other catalog-changing endpoints). Real SecurityFilterChain, real
 * (in-memory) database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerLowStockTest {

    private static final String VALID_KEY = "test-service-key"; // matches application-test.properties

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void seed() {
        productRepository.deleteAll();
        save("Plenty", "misc", 9.99, 20, 5);
        save("Low", "misc", 9.99, 3, 5);
        save("ExactlyAtThreshold", "misc", 9.99, 5, 5);
    }

    private void save(String name, String category, double price, int stock, int threshold) {
        Product p = new Product();
        p.setProductName(name);
        p.setProductCategory(category);
        p.setProductPrice(price);
        p.setProductStock(stock);
        p.setLowStockThreshold(threshold);
        productRepository.save(p);
    }

    @Test
    void lowStockIsPublicAndReturnsOnlyProductsAtOrBelowTheirThreshold() throws Exception {
        mockMvc.perform(get("/product/lowStock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].productName", org.hamcrest.Matchers.containsInAnyOrder("Low", "ExactlyAtThreshold")));
    }

    @Test
    void updateLowStockThresholdWithoutKeyIsUnauthorized() throws Exception {
        Long id = productRepository.findAll().get(0).getProductId();
        mockMvc.perform(put("/product/updateLowStockThreshold").param("id", id.toString()).param("threshold", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateLowStockThresholdWithValidKeyChangesWhatCountsAsLowStock() throws Exception {
        Long id = productRepository.findAll().stream()
                .filter(p -> p.getProductName().equals("Plenty")).findFirst().orElseThrow().getProductId();

        mockMvc.perform(put("/product/updateLowStockThreshold").param("id", id.toString()).param("threshold", "25")
                        .header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockThreshold").value(25));

        mockMvc.perform(get("/product/lowStock"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void updateLowStockThresholdRejectsNegativeValue() throws Exception {
        Long id = productRepository.findAll().get(0).getProductId();
        mockMvc.perform(put("/product/updateLowStockThreshold").param("id", id.toString()).param("threshold", "-1")
                        .header("X-Service-Key", VALID_KEY))
                .andExpect(status().isBadRequest());
    }
}
