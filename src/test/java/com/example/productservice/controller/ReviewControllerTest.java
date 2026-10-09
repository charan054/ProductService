package com.example.productservice.controller;

import com.example.productservice.entity.Product;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reviews are customer-generated content, not a catalog change - no X-Service-Key needed, same public trust
 * level as the GET catalog endpoints. Real SecurityFilterChain, real (in-memory) database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ReviewRepository reviewRepository;

    private static final String VALID_KEY = "test-service-key"; // matches application-test.properties

    private Long productId;

    @BeforeEach
    void seed() {
        reviewRepository.deleteAll();
        productRepository.deleteAll();
        Product p = new Product();
        p.setProductName("Widget");
        p.setProductCategory("misc");
        p.setProductPrice(9.99);
        p.setProductStock(10);
        productId = productRepository.save(p).getProductId();
    }

    private String reviewJson(String name, long phno, int rating, String comment) {
        return """
                {"reviewerName":"%s","reviewerPhno":%d,"rating":%d,"comment":"%s"}
                """.formatted(name, phno, rating, comment);
    }

    @Test
    void addReviewWithoutServiceKeySucceeds() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 5, "Great product")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.reviewerName").value("Alice"));
    }

    @Test
    void addReviewForMissingProductFails() throws Exception {
        mockMvc.perform(post("/product/999999/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 5, "Great product")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addReviewRejectsOutOfRangeRating() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 6, "Great product")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSecondReviewFromTheSameReviewerIsRejected() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 5, "Great product")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 3, "Changed my mind")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ratingSummaryReflectsAddedReviews() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson("Alice", 1111111111L, 4, "Good")));
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson("Bob", 2222222222L, 2, "Meh")));

        mockMvc.perform(get("/product/" + productId + "/rating-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(3.0))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }

    @Test
    void updateReviewByOwnerSucceeds() throws Exception {
        String body = mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 5, "Great")))
                .andReturn().getResponse().getContentAsString();
        long reviewId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.reviewId")).longValue();

        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 2, "Changed my mind")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2));
    }

    private String reviewJsonWithPhoto(String name, long phno, String photoUrl) {
        return """
                {"reviewerName":"%s","reviewerPhno":%d,"rating":5,"comment":"With a picture","photoUrl":%s}
                """.formatted(name, phno, photoUrl == null ? "null" : "\"" + photoUrl + "\"");
    }

    @Test
    void aReviewCanCarryAPhotoLinkAndThePublicListingShowsIt() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJsonWithPhoto("Alice", 9999999999L, "https://shop.example/review-photos/a.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://shop.example/review-photos/a.jpg"));

        mockMvc.perform(get("/product/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].photoUrl").value("https://shop.example/review-photos/a.jpg"))
                .andExpect(jsonPath("$.content[0].reviewerPhno").doesNotExist());
    }

    @Test
    void aBadPhotoLinkIsRejectedBeforeAnythingIsSaved() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJsonWithPhoto("Alice", 9999999999L, "javascript:alert(1)")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/product/" + productId + "/reviews"))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void hidingAReviewTakesItsPhotoOffTheListingAndEditingKeepsOrReplacesIt() throws Exception {
        String body = mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJsonWithPhoto("Alice", 9999999999L, "https://shop.example/review-photos/a.jpg")))
                .andReturn().getResponse().getContentAsString();
        long reviewId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.reviewId")).longValue();

        // An edit from a client that sends no photoUrl keeps the photo; one that sends a new link replaces it.
        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 4, "Edited")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://shop.example/review-photos/a.jpg"));
        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJsonWithPhoto("Alice", 9999999999L, "https://shop.example/review-photos/b.jpg")))
                .andExpect(jsonPath("$.photoUrl").value("https://shop.example/review-photos/b.jpg"));

        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId + "/hide").header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/product/" + productId + "/reviews"))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void updateReviewByADifferentReviewerIsRejected() throws Exception {
        String body = mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 5, "Great")))
                .andReturn().getResponse().getContentAsString();
        long reviewId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.reviewId")).longValue();

        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 1111111111L, 2, "Not mine")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReviewByOwnerRemovesIt() throws Exception {
        String body = mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Alice", 9999999999L, 5, "Great")))
                .andReturn().getResponse().getContentAsString();
        long reviewId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.reviewId")).longValue();

        mockMvc.perform(delete("/product/" + productId + "/reviews/" + reviewId).param("reviewerPhno", "9999999999"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/product/" + productId + "/rating-summary"))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void listReviewsReturnsNewestFirst() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson("Alice", 1111111111L, 4, "First")));
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson("Bob", 2222222222L, 5, "Second")));

        mockMvc.perform(get("/product/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].reviewerName").value("Bob"));
    }

    @Test
    void publicListDoesNotExposeReviewerPhone() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson("Alice", 1111111111L, 4, "Nice")));

        mockMvc.perform(get("/product/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewerName").value("Alice"))
                .andExpect(jsonPath("$.content[0].reviewerPhno").doesNotExist());
    }

    @Test
    void internalListWithoutServiceKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/product/internal/" + productId + "/reviews"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalListWithServiceKeyIncludesReviewerPhone() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson("Alice", 1111111111L, 4, "Nice")));

        mockMvc.perform(get("/product/internal/" + productId + "/reviews").header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewerPhno").value(1111111111L));
    }

    private long addReview(String name, long phno, int rating, String comment) throws Exception {
        String body = mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(name, phno, rating, comment)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.reviewId")).longValue();
    }

    @Test
    void flagReviewWithoutServiceKeySucceeds() throws Exception {
        long reviewId = addReview("Alice", 9999999999L, 5, "Great");

        mockMvc.perform(post("/product/" + productId + "/reviews/" + reviewId + "/flag").param("reason", "Spam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagged").value(true))
                .andExpect(jsonPath("$.flagReason").value("Spam"))
                .andExpect(jsonPath("$.reviewerPhno").doesNotExist());
    }

    @Test
    void hideReviewWithoutServiceKeyIsUnauthorized() throws Exception {
        long reviewId = addReview("Alice", 9999999999L, 5, "Great");
        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId + "/hide"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hideReviewWithServiceKeyRemovesItFromListingsAndTheRatingAverage() throws Exception {
        long reviewId = addReview("Alice", 9999999999L, 5, "Great");

        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId + "/hide")
                        .header("X-Service-Key", "test-service-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));

        mockMvc.perform(get("/product/" + productId + "/reviews"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/product/" + productId + "/rating-summary"))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void unhideReviewRestoresItToListings() throws Exception {
        long reviewId = addReview("Alice", 9999999999L, 5, "Great");
        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId + "/hide")
                .header("X-Service-Key", "test-service-key"));

        mockMvc.perform(put("/product/" + productId + "/reviews/" + reviewId + "/unhide")
                        .header("X-Service-Key", "test-service-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(false));

        mockMvc.perform(get("/product/" + productId + "/reviews"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void flaggedReviewsQueueWithoutServiceKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/product/reviews/flagged")).andExpect(status().isUnauthorized());
    }

    @Test
    void flaggedReviewsQueueListsFlaggedButNotYetHiddenReviews() throws Exception {
        long reviewId = addReview("Alice", 9999999999L, 5, "Great");
        mockMvc.perform(post("/product/" + productId + "/reviews/" + reviewId + "/flag"));

        mockMvc.perform(get("/product/reviews/flagged").header("X-Service-Key", "test-service-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void reviewCountIsPublic() throws Exception {
        mockMvc.perform(get("/product/reviews/count").param("phno", "9999999999")).andExpect(status().isOk());
    }

    @Test
    void reviewCountReflectsHowManyVisibleReviewsThatCustomerHasPosted() throws Exception {
        addReview("Alice", 9999999999L, 5, "Great");

        mockMvc.perform(get("/product/reviews/count").param("phno", "9999999999"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void photoListWithoutServiceKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/product/reviews/photos")).andExpect(status().isUnauthorized());
    }

    @Test
    void photoListNamesEveryPhotoAReviewUsesHiddenOnesIncludedAndSkipsReviewsWithoutOne() throws Exception {
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJsonWithPhoto("Alice", 9999999999L, "https://shop.example/review-photos/a.jpg")));
        mockMvc.perform(post("/product/" + productId + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(reviewJsonWithPhoto("Bob", 8888888888L, "https://shop.example/review-photos/b.jpg")));
        addReview("Cara", 7777777777L, 4, "No picture");
        long bobId = reviewRepository.findAll().stream()
                .filter(r -> "Bob".equals(r.getReviewerName())).findFirst().orElseThrow().getReviewId();
        mockMvc.perform(put("/product/" + productId + "/reviews/" + bobId + "/hide").header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/product/reviews/photos").header("X-Service-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@ == 'https://shop.example/review-photos/a.jpg')]").exists())
                .andExpect(jsonPath("$[?(@ == 'https://shop.example/review-photos/b.jpg')]").exists());
    }
}
