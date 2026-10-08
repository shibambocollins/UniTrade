package za.ac.cput.unitrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.repository.ListingRepository;
import za.ac.cput.unitrade.repository.OrderRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR4: checkout, mock payment (approve and decline), listings becoming SOLD, order lifecycle. Through HTTP on H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fr4OrdersTest {

    private static final String GOOD_CARD = "4242 4242 4242 4242";
    private static final String DECLINED_CARD = "4000 0000 0000 0002"; // documented test card: always declined

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ListingRepository listings;
    @Autowired
    private OrderRepository orders;

    private String seller;
    private String buyer;
    private String buyer2;

    @BeforeEach
    void registerStudents() throws Exception {
        seller = register("Sam Seller", "sam@mycput.ac.za");
        buyer = register("Bea Buyer", "bea@mycput.ac.za");
        buyer2 = register("Bo Buyer", "bo@mycput.ac.za");
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

    private long listing(String token, String title, double price) throws Exception {
        Map<String, Object> body = Map.of("title", title, "category", "OTHER", "type", "GOOD", "price", price, "condition", "USED");
        String json = send(post("/api/listings"), token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private ResultActions checkout(String token, Long... ids) throws Exception {
        return send(post("/api/orders"), token, Map.of("listingIds", List.of(ids)));
    }

    private long checkoutOk(String token, Long... ids) throws Exception {
        String json = checkout(token, ids).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private ResultActions pay(String token, long orderId, String card) throws Exception {
        return send(post("/api/orders/" + orderId + "/pay"), token, Map.of("cardNumber", card));
    }

    private String listingStatus(long id) throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(get("/api/listings/" + id)).andReturn().getResponse().getContentAsString());
        return node.get("status").asText();
    }

    @Test
    void fr4_01_checkoutNeedsLogin() throws Exception {
        long id = listing(seller, "Lamp", 80);
        checkout(null, id).andExpect(status().isUnauthorized());
        send(get("/api/orders"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void fr4_02_checkoutCreatesPendingOrderWithPricesAndTotalFromTheDatabase() throws Exception {
        long a = listing(seller, "Lamp", 150);
        long b = listing(seller, "Book", 50.50);

        checkout(buyer, a, b).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(200.5))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.seller.fullName").value("Sam Seller"))
                .andExpect(jsonPath("$.paymentReference").doesNotExist());
        // Nothing is reserved or sold until the order is paid
        assertEquals("ACTIVE", listingStatus(a));
    }

    @Test
    void fr4_03_cannotBuyYourOwnListing() throws Exception {
        long id = listing(seller, "Lamp", 80);
        checkout(seller, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot buy your own listing (\"Lamp\")."));
        assertEquals(0, orders.count());
    }

    @Test
    void fr4_04_rejectsEmptyCartUnknownListingAndDuplicates() throws Exception {
        long id = listing(seller, "Lamp", 80);
        send(post("/api/orders"), buyer, Map.of("listingIds", List.of())).andExpect(status().isBadRequest());
        checkout(buyer, 987654L).andExpect(status().isNotFound());
        checkout(buyer, id, id).andExpect(status().isBadRequest());
        assertEquals(0, orders.count());
    }

    @Test
    void fr4_05_anOrderCanOnlyContainItemsFromOneSeller() throws Exception {
        long fromSam = listing(seller, "Lamp", 80);
        long fromBo = listing(buyer2, "Chair", 120);
        checkout(buyer, fromSam, fromBo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An order can only contain items from one seller. Check out one seller at a time."));
    }

    @Test
    void fr4_06_removedListingsCannotBeOrdered() throws Exception {
        long id = listing(seller, "Lamp", 80);
        send(delete("/api/listings/" + id), seller, null).andExpect(status().isNoContent());
        checkout(buyer, id).andExpect(status().isConflict());
    }

    @Test
    void fr4_07_approvedPaymentMakesTheOrderPaidAndTheListingsSold() throws Exception {
        long a = listing(seller, "Lamp", 150);
        long orderId = checkoutOk(buyer, a);

        pay(buyer, orderId, GOOD_CARD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentReference").value(org.hamcrest.Matchers.startsWith("MOCK-")))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("4242"))));

        assertEquals("SOLD", listingStatus(a));
        // a sold listing no longer shows up in search
        mockMvc.perform(get("/api/listings")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void fr4_08_declinedCardGives402LeavesEverythingUnchangedAndAllowsARetry() throws Exception {
        long a = listing(seller, "Lamp", 150);
        long orderId = checkoutOk(buyer, a);

        pay(buyer, orderId, DECLINED_CARD).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.message").value("The payment was declined by the card issuer. Please try another card."));
        assertEquals("ACTIVE", listingStatus(a));
        send(get("/api/orders/" + orderId), buyer, null).andExpect(jsonPath("$.status").value("PENDING"));

        // retry the same order with a card that works
        pay(buyer, orderId, GOOD_CARD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        assertEquals("SOLD", listingStatus(a));
    }

    @Test
    void fr4_09_invalidCardNumberIsRejectedBeforeAnyPayment() throws Exception {
        long orderId = checkoutOk(buyer, listing(seller, "Lamp", 150));
        pay(buyer, orderId, "abcd").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cardNumber").exists());
        pay(buyer, orderId, "1234").andExpect(status().isBadRequest());
        send(post("/api/orders/" + orderId + "/pay"), buyer, Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void fr4_10_anOrderCannotBePaidTwice() throws Exception {
        long orderId = checkoutOk(buyer, listing(seller, "Lamp", 150));
        pay(buyer, orderId, GOOD_CARD).andExpect(status().isOk());
        pay(buyer, orderId, GOOD_CARD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This order has already been paid."));
    }

    @Test
    void fr4_11_onlyTheBuyerCanSeePayOrConfirmAnOrder() throws Exception {
        long orderId = checkoutOk(buyer, listing(seller, "Lamp", 150));
        send(get("/api/orders/" + orderId), buyer2, null).andExpect(status().isForbidden());
        pay(buyer2, orderId, GOOD_CARD).andExpect(status().isForbidden());
        send(post("/api/orders/" + orderId + "/confirm"), buyer2, null).andExpect(status().isForbidden());
        send(get("/api/orders/999999"), buyer, null).andExpect(status().isNotFound());
    }

    @Test
    void fr4_12_ifTwoBuyersOrderTheSameListingOnlyTheFirstToPaySucceeds() throws Exception {
        long id = listing(seller, "Last copy", 99);
        long orderBea = checkoutOk(buyer, id);
        long orderBo = checkoutOk(buyer2, id); // both can create an order: nothing is reserved yet

        pay(buyer, orderBea, GOOD_CARD).andExpect(status().isOk());
        pay(buyer2, orderBo, GOOD_CARD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("was just sold to someone else")));

        assertEquals("SOLD", listingStatus(id));
        send(get("/api/orders/" + orderBo), buyer2, null).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void fr4_13_buyerConfirmsReceiptPaidToCompleted() throws Exception {
        long orderId = checkoutOk(buyer, listing(seller, "Lamp", 150));

        send(post("/api/orders/" + orderId + "/confirm"), buyer, null).andExpect(status().isConflict()); // not paid yet
        pay(buyer, orderId, GOOD_CARD).andExpect(status().isOk());
        send(post("/api/orders/" + orderId + "/confirm"), buyer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());
        send(post("/api/orders/" + orderId + "/confirm"), buyer, null).andExpect(status().isConflict()); // only once
    }

    @Test
    void fr4_14_myOrdersListsOnlyMyOrdersNewestFirst() throws Exception {
        long first = checkoutOk(buyer, listing(seller, "Lamp", 10));
        long second = checkoutOk(buyer, listing(seller, "Book", 20));
        checkoutOk(buyer2, listing(seller, "Chair", 30));

        send(get("/api/orders"), buyer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));
    }

    @Test
    void fr4_15_aPaidListingCannotBeEditedOrDeletedByItsSeller() throws Exception {
        long id = listing(seller, "Lamp", 150);
        pay(buyer, checkoutOk(buyer, id), GOOD_CARD).andExpect(status().isOk());
        send(delete("/api/listings/" + id), seller, null).andExpect(status().isConflict());
        assertEquals(ListingStatus.SOLD, listings.findById(id).orElseThrow().getStatus());
    }
}
