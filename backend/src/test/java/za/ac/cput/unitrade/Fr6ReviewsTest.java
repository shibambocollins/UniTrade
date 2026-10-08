package za.ac.cput.unitrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.Order;
import za.ac.cput.unitrade.domain.Review;
import za.ac.cput.unitrade.repository.OrderRepository;
import za.ac.cput.unitrade.repository.ReviewRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR6 Reviews: rating the seller after a COMPLETED order, one per order, and the seller's average rating. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fr6ReviewsTest {

    private static final String CARD = "4242 4242 4242 4242";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ReviewRepository reviews;
    @Autowired
    private OrderRepository orders;

    private String seller;
    private String buyer;
    private String other;
    private long sellerId;

    @BeforeEach
    void registerStudents() throws Exception {
        seller = register("Sam Seller", "sam@mycput.ac.za");
        buyer = register("Bea Buyer", "bea@mycput.ac.za");
        other = register("Oli Other", "oli@mycput.ac.za");
        sellerId = objectMapper.readTree(send(get("/api/auth/me"), seller, null).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String register(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", name, "email", email, "password", "Password123!"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private long id(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** A listing from the seller, bought and paid by `buyerToken`; completed only if `confirm` is true. */
    private long order(String buyerToken, boolean pay, boolean confirm) throws Exception {
        Map<String, Object> listing = Map.of("title", "Item", "category", "OTHER", "type", "GOOD", "price", 50, "condition", "USED");
        long listingId = id(send(post("/api/listings"), seller, listing).andExpect(status().isCreated()));
        long orderId = id(send(post("/api/orders"), buyerToken, Map.of("listingIds", List.of(listingId))).andExpect(status().isCreated()));
        if (pay) {
            send(post("/api/orders/" + orderId + "/pay"), buyerToken, Map.of("cardNumber", CARD)).andExpect(status().isOk());
        }
        if (confirm) {
            send(post("/api/orders/" + orderId + "/confirm"), buyerToken, null).andExpect(status().isOk());
        }
        return orderId;
    }

    private ResultActions review(String token, long orderId, Object rating, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        body.put("comment", comment);
        return send(post("/api/orders/" + orderId + "/review"), token, body);
    }

    @Test
    void fr6_01_reviewNeedsLogin() throws Exception {
        long orderId = order(buyer, true, true);
        review(null, orderId, 5, "Great").andExpect(status().isUnauthorized());
    }

    @Test
    void fr6_02_cannotReviewBeforeTheOrderIsCompleted() throws Exception {
        long unpaid = order(buyer, false, false);
        long paidNotConfirmed = order(buyer, true, false);
        review(buyer, unpaid, 5, "x").andExpect(status().isConflict());
        review(buyer, paidNotConfirmed, 5, "x").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can review the seller after you confirm that you received the order."));
        assertEquals(0, reviews.count());
    }

    @Test
    void fr6_03_buyerReviewsACompletedOrder() throws Exception {
        long orderId = order(buyer, true, true);
        review(buyer, orderId, 4, "  Good seller, quick hand-over  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Good seller, quick hand-over"))
                .andExpect(jsonPath("$.reviewerName").value("Bea Buyer"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void fr6_04_onlyOneReviewPerOrder() throws Exception {
        long orderId = order(buyer, true, true);
        review(buyer, orderId, 5, "Great").andExpect(status().isCreated());
        review(buyer, orderId, 1, "Changed my mind").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already reviewed this order."));
        assertEquals(1, reviews.count());
    }

    @Test
    void fr6_05_theDatabaseItselfRefusesASecondReviewForTheSameOrder() throws Exception {
        long orderId = order(buyer, true, true);
        Order order = orders.findWithDetailsById(orderId).orElseThrow();
        reviews.saveAndFlush(new Review(order, 5, "first"));
        // bypassing the service check: the unique key on order_id still stops it (protects against simultaneous requests)
        assertThrows(DataIntegrityViolationException.class, () -> reviews.saveAndFlush(new Review(order, 1, "second")));
    }

    @Test
    void fr6_06_onlyTheBuyerCanReviewTheirOrder() throws Exception {
        long orderId = order(buyer, true, true);
        review(other, orderId, 5, "I never bought this").andExpect(status().isForbidden());
        review(seller, orderId, 5, "Rating myself").andExpect(status().isForbidden());
        review(buyer, 999999L, 5, "x").andExpect(status().isNotFound());
        assertEquals(0, reviews.count());
    }

    @Test
    void fr6_07_ratingMustBeAWholeNumberFromOneToFive() throws Exception {
        long orderId = order(buyer, true, true);
        review(buyer, orderId, 0, "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());
        review(buyer, orderId, 6, "x").andExpect(status().isBadRequest());
        review(buyer, orderId, null, "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());
        review(buyer, orderId, "five", "x").andExpect(status().isBadRequest());
        review(buyer, orderId, 3, "x".repeat(1001)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.comment").exists());
        assertEquals(0, reviews.count());
        review(buyer, orderId, 3, null).andExpect(status().isCreated()); // the text is optional
    }

    @Test
    void fr6_08_sellerAverageRatingAndCountAppearOnTheListingDetail() throws Exception {
        long listingBefore = id(send(post("/api/listings"), seller, Map.of("title", "Other item", "category", "OTHER", "type", "GOOD", "price", 10, "condition", "NEW")));
        send(get("/api/listings/" + listingBefore), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.seller.reviewCount").value(0))
                .andExpect(jsonPath("$.seller.averageRating").doesNotExist()); // no reviews yet

        review(buyer, order(buyer, true, true), 5, "Great").andExpect(status().isCreated());
        review(buyer, order(buyer, true, true), 4, "Good").andExpect(status().isCreated());
        review(buyer, order(buyer, true, true), 4, null).andExpect(status().isCreated());

        send(get("/api/listings/" + listingBefore), null, null)
                .andExpect(jsonPath("$.seller.reviewCount").value(3))
                .andExpect(jsonPath("$.seller.averageRating").value(4.3)); // (5+4+4)/3 = 4.33 -> 4.3
    }

    @Test
    void fr6_09_aSellersReviewsArePublicNewestFirstWithTheAverage() throws Exception {
        review(buyer, order(buyer, true, true), 5, "First").andExpect(status().isCreated());
        review(other, order(other, true, true), 3, "Second").andExpect(status().isCreated());

        send(get("/api/users/" + sellerId + "/reviews"), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.seller.fullName").value("Sam Seller"))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.reviews[0].comment").value("Second"))
                .andExpect(jsonPath("$.reviews[*].comment", not(hasItem("none"))))
                .andExpect(jsonPath("$.reviews[0].email").doesNotExist());
        send(get("/api/users/999999/reviews"), null, null).andExpect(status().isNotFound());
    }

    @Test
    void fr6_10_theOrderShowsItsReviewAfterwards() throws Exception {
        long orderId = order(buyer, true, true);
        send(get("/api/orders/" + orderId), buyer, null).andExpect(jsonPath("$.review").doesNotExist());
        review(buyer, orderId, 5, "Great").andExpect(status().isCreated());
        send(get("/api/orders/" + orderId), buyer, null).andExpect(jsonPath("$.review.rating").value(5))
                .andExpect(jsonPath("$.review.comment").value("Great"));
        send(get("/api/orders"), buyer, null).andExpect(jsonPath("$[0].review.rating").value(5));
    }
}
