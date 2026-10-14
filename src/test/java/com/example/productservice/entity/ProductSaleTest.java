package com.example.productservice.entity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProductSaleTest {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    private Product product() {
        Product p = new Product();
        p.setProductName("mug");
        p.setProductPrice(100);
        return p;
    }

    @Test
    void jsonShowsTheRegularPriceWhenNoSaleIsSet() {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(product()));

        assertEquals(100.0, json.get("productPrice").asDouble());
        assertEquals(100.0, json.get("regularPrice").asDouble());
        assertFalse(json.get("onSale").asBoolean());
    }

    @Test
    void jsonShowsTheSalePriceWhileTheSaleRuns() {
        Product p = product();
        p.setSalePrice(80.0);
        p.setSaleStartsAt(Instant.now().minusSeconds(60));
        p.setSaleEndsAt(Instant.now().plusSeconds(3600));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(p));

        assertEquals(80.0, json.get("productPrice").asDouble());
        assertEquals(100.0, json.get("regularPrice").asDouble());
        assertTrue(json.get("onSale").asBoolean());
        assertEquals(100.0, p.getProductPrice(), "code that edits prices still sees the regular price");
    }

    @Test
    void aSaleThatHasNotStartedOrHasEndedIsIgnored() {
        Product p = product();
        p.setSalePrice(80.0);
        p.setSaleStartsAt(Instant.now().plusSeconds(3600));
        p.setSaleEndsAt(Instant.now().plusSeconds(7200));
        assertEquals(100.0, mapper.readTree(mapper.writeValueAsString(p)).get("productPrice").asDouble());

        p.setSaleStartsAt(Instant.now().minusSeconds(7200));
        p.setSaleEndsAt(Instant.now().minusSeconds(3600));
        assertEquals(100.0, mapper.readTree(mapper.writeValueAsString(p)).get("productPrice").asDouble());
    }

    @Test
    void productPriceStillReadsFromARequestBodyButSaleFieldsDoNot() {
        Product p = mapper.readValue(
                "{\"productName\":\"mug\",\"productPrice\":55,\"salePrice\":1,\"saleEndsAt\":\"2030-01-01T00:00:00Z\"}", Product.class);

        assertEquals(55.0, p.getProductPrice());
        assertNull(p.getSalePrice());
        assertNull(p.getSaleEndsAt());
    }
}
