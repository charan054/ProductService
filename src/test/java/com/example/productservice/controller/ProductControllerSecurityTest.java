package com.example.productservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    void updateTaxWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(put("/product/updateTax").param("id", "1").param("gstRate", "18"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateVariantWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(put("/product/updateVariant").param("id", "1").param("variantGroup", "dove").param("variantLabel", "340 ml"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStockWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(put("/product/updateStock").param("id", "1").param("stock", "5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stockHistoryWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/product/1/stock-history")).andExpect(status().isUnauthorized());
    }

    @Test
    void receiveAndCorrectStockWithoutKeyAreUnauthorized() throws Exception {
        mockMvc.perform(post("/product/1/receive").param("quantity", "5")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/product/1/stock/correct").param("newStock", "5").param("reason", "recount")).andExpect(status().isUnauthorized());
    }

    @Test
    void addOptionWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/product/1/addOption").param("variantLabel", "650 ml").param("stock", "5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stockReportsWithoutKeyAreUnauthorized() throws Exception {
        mockMvc.perform(get("/product/reorder-list")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/product/stock-movements/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void stockReportsWithTheKeyWork() throws Exception {
        mockMvc.perform(get("/product/reorder-list").header("X-Service-Key", "test-service-key")).andExpect(status().isOk());
        mockMvc.perform(get("/product/stock-movements/export").header("X-Service-Key", "test-service-key"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.startsWith("id,createdAt,productId,productName,type,delta")));
        mockMvc.perform(get("/product/reorder-list").param("days", "0").header("X-Service-Key", "test-service-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void relatedProductsIsPublicButReturns400ForAnUnknownId() throws Exception {
        mockMvc.perform(get("/product/related").param("id", "999999")).andExpect(status().isBadRequest());
    }

    @Test
    void priceHistoryIsPublicButReturns400ForAnUnknownId() throws Exception {
        mockMvc.perform(get("/product/priceHistory").param("id", "999999")).andExpect(status().isBadRequest());
    }

    private static final MockMultipartFile BULK_IMPORT_CSV = new MockMultipartFile(
            "file", "products.csv", "text/csv",
            "productName,productCategory,productPrice,productStock\nWidget,misc,9.99,10\n".getBytes(StandardCharsets.UTF_8));

    @Test
    void bulkImportWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/product/bulkImport").file(BULK_IMPORT_CSV))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bulkImportWithValidKeySucceeds() throws Exception {
        mockMvc.perform(multipart("/product/bulkImport").file(BULK_IMPORT_CSV).header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk());
    }

    private static final MockMultipartFile PRICE_CSV = new MockMultipartFile(
            "file", "prices.csv", "text/csv", "productId,newPrice\n999999,10\n".getBytes(StandardCharsets.UTF_8));

    @Test
    void bulkPriceUpdateWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/product/bulkPriceUpdate").file(PRICE_CSV)).andExpect(status().isUnauthorized());
    }

    @Test
    void bulkPriceUpdateWithValidKeyDefaultsToADryRun() throws Exception {
        mockMvc.perform(multipart("/product/bulkPriceUpdate").file(PRICE_CSV).header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void recentStockChangesNeedTheKeyAndRejectABadWindow() throws Exception {
        mockMvc.perform(get("/product/stock-movements/recent")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/product/stock-movements/recent").header("X-Service-Key", VALID_KEY)).andExpect(status().isOk());
        mockMvc.perform(get("/product/stock-movements/recent").param("hours", "0").header("X-Service-Key", VALID_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/product/delete").param("id", "1"))
                .andExpect(status().isUnauthorized());
    }
}
