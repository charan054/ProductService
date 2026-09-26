package com.example.productservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog browsing (GET) is public; anything that changes the catalog needs the right X-Service-Key. Real
 * SecurityFilterChain, real (in-memory) database - only the header is varied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerSecurityTest {

    private static final String VALID_KEY = "test-service-key"; // matches application-test.properties
    private static final String WRONG_KEY = "not-the-right-key";

    @Autowired
    private MockMvc mockMvc;

    private static final String NEW_PRODUCT = """
            {"productName":"Widget","productCategory":"misc","productPrice":9.99,"productStock":10}
            """;

    @Test
    void readEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/product/all")).andExpect(status().isOk());
        mockMvc.perform(get("/product/byName").param("name", "Widget")).andExpect(status().isOk());
        mockMvc.perform(get("/product/byCategory").param("category", "misc")).andExpect(status().isOk());
        mockMvc.perform(get("/product/search")).andExpect(status().isOk());
        mockMvc.perform(get("/product/lowStock")).andExpect(status().isOk());
        mockMvc.perform(get("/category/all")).andExpect(status().isOk());
    }

    @Test
    void addCategoryWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/category/add").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Gadgets\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addCategoryWithValidKeySucceeds() throws Exception {
        mockMvc.perform(post("/category/add").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Gadgets\"}")
                        .header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk());
    }

    // Regression: the static dashboard was 401ing before Spring Security's static-resource handler ever got to
    // serve it, because nothing explicitly permitted it.
    @Test
    void staticDashboardIsPublic() throws Exception {
        mockMvc.perform(get("/product.html")).andExpect(status().isOk());
    }

    @Test
    void addProductWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/product/add").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addProductWithWrongKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/product/add").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT)
                        .header("X-Service-Key", WRONG_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addProductWithValidKeySucceeds() throws Exception {
        mockMvc.perform(post("/product/add").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT)
                        .header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void updateStockWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(put("/product/updateStock").param("id", "1").param("stock", "5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void relatedProductsIsPublicButReturns400ForAnUnknownId() throws Exception {
        mockMvc.perform(get("/product/related").param("id", "999999")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/product/delete").param("id", "1"))
                .andExpect(status().isUnauthorized());
    }
}
