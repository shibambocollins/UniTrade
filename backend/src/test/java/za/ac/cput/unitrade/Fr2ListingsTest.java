package za.ac.cput.unitrade;

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
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.repository.ListingRepository;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR2 Listings: create, read, edit, delete, owner-only rules and validation, through the real HTTP layer on H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fr2ListingsTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ListingRepository listings;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void registerTwoStudents() throws Exception {
        ownerToken = register("Owner One", "owner@mycput.ac.za");
        otherToken = register("Other Two", "other@mycput.ac.za");
    }

    private String register(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", name, "email", email, "password", "Password123!"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private static Map<String, Object> goodBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Calculus textbook");
        body.put("description", "Barely used");
        body.put("category", "TEXTBOOKS");
        body.put("type", "GOOD");
        body.put("price", 150);
        body.put("condition", "USED");
        body.put("imageUrl", "https://example.com/book.jpg");
        return body;
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String token, Object body) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private long createListing(String token) throws Exception {
        String json = send(post("/api/listings"), token, goodBody()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void fr2_01_createListingNeedsLogin() throws Exception {
        send(post("/api/listings"), null, goodBody()).andExpect(status().isUnauthorized());
        assertEquals(0, listings.count());
    }

    @Test
    void fr2_02_createStoresAllFieldsWithActiveStatusAndSeller() throws Exception {
        send(post("/api/listings"), ownerToken, goodBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Calculus textbook"))
                .andExpect(jsonPath("$.category").value("TEXTBOOKS"))
                .andExpect(jsonPath("$.type").value("GOOD"))
                .andExpect(jsonPath("$.price").value(150.0))
                .andExpect(jsonPath("$.condition").value("USED"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/book.jpg"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.seller.fullName").value("Owner One"))
                .andExpect(jsonPath("$.seller.email").doesNotExist());
    }

    @Test
    void fr2_03_validationRejectsBlankTitleNegativePriceAndMissingFields() throws Exception {
        Map<String, Object> body = goodBody();
        body.put("title", "   ");
        body.put("price", -1);
        body.remove("category");
        send(post("/api/listings"), ownerToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.price").value("Price cannot be negative"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Choose a category"));
        assertEquals(0, listings.count());
    }

    @Test
    void fr2_04_priceZeroIsAllowedButTooManyDecimalsIsNot() throws Exception {
        Map<String, Object> free = goodBody();
        free.put("price", 0);
        send(post("/api/listings"), ownerToken, free).andExpect(status().isCreated());

        Map<String, Object> odd = goodBody();
        odd.put("price", 10.999);
        send(post("/api/listings"), ownerToken, odd).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void fr2_05_goodsNeedConditionAndServicesStoreNone() throws Exception {
        Map<String, Object> noCondition = goodBody();
        noCondition.remove("condition");
        send(post("/api/listings"), ownerToken, noCondition).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.condition").exists());

        Map<String, Object> service = goodBody();
        service.put("type", "SERVICE");
        service.put("category", "TUTORING");
        service.put("condition", "NEW"); // must be ignored for services
        send(post("/api/listings"), ownerToken, service).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SERVICE"))
                .andExpect(jsonPath("$.condition").doesNotExist());
    }

    @Test
    void fr2_06_imageLinkMustBeHttpOrHttps() throws Exception {
        Map<String, Object> body = goodBody();
        body.put("imageUrl", "javascript:alert(1)");
        send(post("/api/listings"), ownerToken, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.imageUrl").exists());

        body.put("imageUrl", "");
        send(post("/api/listings"), ownerToken, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void fr2_07_anyoneCanViewOneListingAndUnknownIdIs404() throws Exception {
        long id = createListing(ownerToken);
        send(get("/api/listings/" + id), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Calculus textbook"));
        send(get("/api/listings/999999"), null, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void fr2_08_ownerCanEditTheirListing() throws Exception {
        long id = createListing(ownerToken);
        Map<String, Object> changed = goodBody();
        changed.put("title", "Calculus textbook (2nd edition)");
        changed.put("price", 120);
        send(put("/api/listings/" + id), ownerToken, changed).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Calculus textbook (2nd edition)"))
                .andExpect(jsonPath("$.price").value(120.0));
    }

    @Test
    void fr2_09_anotherStudentCannotEditOrDeleteGets403AndNothingChanges() throws Exception {
        long id = createListing(ownerToken);
        Map<String, Object> changed = goodBody();
        changed.put("title", "Hijacked");
        send(put("/api/listings/" + id), otherToken, changed).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        send(delete("/api/listings/" + id), otherToken, null).andExpect(status().isForbidden());

        Listing stored = listings.findById(id).orElseThrow();
        assertEquals("Calculus textbook", stored.getTitle());
        assertEquals(ListingStatus.ACTIVE, stored.getStatus());
    }

    @Test
    void fr2_10_editAndDeleteNeedLogin() throws Exception {
        long id = createListing(ownerToken);
        send(put("/api/listings/" + id), null, goodBody()).andExpect(status().isUnauthorized());
        send(delete("/api/listings/" + id), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void fr2_11_ownerDeleteRemovesTheListingFromViewAndMyListings() throws Exception {
        long id = createListing(ownerToken);
        send(delete("/api/listings/" + id), ownerToken, null).andExpect(status().isNoContent());

        send(get("/api/listings/" + id), null, null).andExpect(status().isNotFound());
        send(get("/api/listings/mine"), ownerToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        // soft delete: the row is still there for any past order that refers to it
        assertEquals(ListingStatus.REMOVED, listings.findById(id).orElseThrow().getStatus());
    }

    @Test
    void fr2_12_myListingsShowsOnlyMyOwn() throws Exception {
        createListing(ownerToken);
        createListing(ownerToken);
        createListing(otherToken);

        send(get("/api/listings/mine"), ownerToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        send(get("/api/listings/mine"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void fr2_13_soldListingCannotBeEditedOrDeleted() throws Exception {
        long id = createListing(ownerToken);
        Listing sold = listings.findById(id).orElseThrow();
        sold.setStatus(ListingStatus.SOLD);
        listings.saveAndFlush(sold);

        send(put("/api/listings/" + id), ownerToken, goodBody()).andExpect(status().isConflict());
        send(delete("/api/listings/" + id), ownerToken, null).andExpect(status().isConflict());
        // ...but it can still be viewed
        send(get("/api/listings/" + id), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));
    }

    @Test
    void fr2_14_invalidEnumValueGivesA400NotA500() throws Exception {
        Map<String, Object> body = goodBody();
        body.put("category", "NOT_A_CATEGORY");
        send(post("/api/listings"), ownerToken, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
