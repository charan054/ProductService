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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /product/search: filtering by name/category/price range, plus pagination and sorting. Real
 * SecurityFilterChain, real (in-memory) database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerSearchTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void seed() {
        productRepository.deleteAll();
        save("Red Mug", "kitchen", 9.99, 10);
        save("Blue Mug", "kitchen", 14.99, 5);
        save("Red Shirt", "clothing", 19.99, 20);
        save("Green Shirt", "clothing", 24.99, 0);
    }

    private void save(String name, String category, double price, int stock) {
        Product p = new Product();
        p.setProductName(name);
        p.setProductCategory(category);
        p.setProductPrice(price);
        p.setProductStock(stock);
        productRepository.save(p);
    }

    @Test
    void filtersByNameCaseInsensitiveSubstring() throws Exception {
        mockMvc.perform(get("/product/search").param("name", "mug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].productName").value("Red Mug"))
                .andExpect(jsonPath("$.content[1].productName").value("Blue Mug"));
    }

    @Test
    void filtersByCategory() throws Exception {
        mockMvc.perform(get("/product/search").param("category", "clothing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtersByPriceRange() throws Exception {
        mockMvc.perform(get("/product/search").param("minPrice", "10").param("maxPrice", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void rejectsMinPriceGreaterThanMaxPrice() throws Exception {
        mockMvc.perform(get("/product/search").param("minPrice", "20").param("maxPrice", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/product/search").param("sortBy", "notAField"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginatesResults() throws Exception {
        mockMvc.perform(get("/product/search").param("page", "0").param("size", "2").param("sortBy", "productName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void sortsDescendingByPrice() throws Exception {
        mockMvc.perform(get("/product/search").param("sortBy", "productPrice").param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("Green Shirt"))
                .andExpect(jsonPath("$.content[3].productName").value("Red Mug"));
    }
}
