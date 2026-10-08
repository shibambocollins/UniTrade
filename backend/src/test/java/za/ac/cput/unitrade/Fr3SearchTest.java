package za.ac.cput.unitrade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.Category;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.domain.ListingType;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.repository.ListingRepository;
import za.ac.cput.unitrade.repository.UserRepository;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR3 Search and filter: GET /api/listings with its optional parameters, through the HTTP layer on H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fr3SearchTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ListingRepository listings;
    @Autowired
    private UserRepository users;

    private User seller;

    @BeforeEach
    void createSeller() {
        seller = users.save(User.builder().fullName("Seller").email("seller@mycput.ac.za").passwordHash("x").build());
    }

    private Listing add(String title, String description, Category category, ListingType type, String price,
                        ItemCondition condition) {
        return listings.save(Listing.builder().seller(seller).title(title).description(description)
                .category(category).type(type).price(new BigDecimal(price)).condition(condition).build());
    }

    private Listing book(String title, String price) {
        return add(title, null, Category.TEXTBOOKS, ListingType.GOOD, price, ItemCondition.USED);
    }

    /**
     * Takes a query string like "?q=lamp&maxPrice=100" (percent-encoded as a browser would send it) and sends the
     * DECODED values as request parameters. (Passing the string to MockMvc as a URL would encode "%" a second time.)
     */
    private ResultActions search(String query) throws Exception {
        var request = get("/api/listings");
        if (query.startsWith("?")) {
            for (String pair : query.substring(1).split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                request.param(name, value);
            }
        }
        return mockMvc.perform(request);
    }

    @Test
    void fr3_01_onlyActiveListingsAreReturnedAndNoLoginIsNeeded() throws Exception {
        book("Active book", "100");
        Listing sold = book("Sold book", "100");
        sold.setStatus(ListingStatus.SOLD);
        Listing removed = book("Removed book", "100");
        removed.setStatus(ListingStatus.REMOVED);
        listings.flush();

        search("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Active book"))
                .andExpect(jsonPath("$.items[0].seller.fullName").value("Seller"))
                .andExpect(jsonPath("$.items[0].seller.email").doesNotExist());
    }

    @Test
    void fr3_02_keywordMatchesTitleOrDescriptionIgnoringCase() throws Exception {
        add("Desk LAMP", null, Category.ELECTRONICS, ListingType.GOOD, "80", ItemCondition.NEW);
        add("Study light", "A bright reading lamp for your desk", Category.ELECTRONICS, ListingType.GOOD, "90", ItemCondition.NEW);
        add("Calculator", "Casio", Category.ELECTRONICS, ListingType.GOOD, "200", ItemCondition.USED);

        search("?q=lAmP").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].title", containsInAnyOrder("Desk LAMP", "Study light")));
        search("?q=%20%20casio%20").andExpect(jsonPath("$.totalItems").value(1)); // surrounding spaces are ignored
        search("?q=").andExpect(jsonPath("$.totalItems").value(3));               // blank = no keyword filter
    }

    @Test
    void fr3_03_wildcardCharactersInTheKeywordAreMatchedLiterally() throws Exception {
        book("50% off notes", "10");
        book("Plain notes", "10");
        book("under_score notes", "10");

        search("?q=%25").andExpect(jsonPath("$.totalItems").value(1))        // "%" is not "match everything"
                .andExpect(jsonPath("$.items[0].title").value("50% off notes"));
        search("?q=_").andExpect(jsonPath("$.totalItems").value(1))          // "_" is not "any one character"
                .andExpect(jsonPath("$.items[0].title").value("under_score notes"));
    }

    @Test
    void fr3_04_filtersByCategoryTypeAndCondition() throws Exception {
        add("Maths book", null, Category.TEXTBOOKS, ListingType.GOOD, "100", ItemCondition.USED);
        add("New desk", null, Category.FURNITURE, ListingType.GOOD, "500", ItemCondition.NEW);
        add("Maths tutoring", null, Category.TUTORING, ListingType.SERVICE, "90", null);

        search("?category=TEXTBOOKS").andExpect(jsonPath("$.totalItems").value(1));
        search("?type=SERVICE").andExpect(jsonPath("$.items[0].title").value("Maths tutoring"))
                .andExpect(jsonPath("$.totalItems").value(1));
        search("?condition=NEW").andExpect(jsonPath("$.items[0].title").value("New desk"))
                .andExpect(jsonPath("$.totalItems").value(1));
        search("?type=GOOD&condition=USED").andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void fr3_05_priceRangeIsInclusiveAndEitherEndMayBeOmitted() throws Exception {
        book("R50", "50");
        book("R100", "100");
        book("R150", "150");

        search("?minPrice=50&maxPrice=100").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("R50", "R100")));
        search("?minPrice=101").andExpect(jsonPath("$.items[*].title", contains("R150")));
        search("?maxPrice=49.99").andExpect(jsonPath("$.totalItems").value(0));
        search("?minPrice=0").andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void fr3_06_filtersCombine() throws Exception {
        add("Cheap used lamp", "desk", Category.ELECTRONICS, ListingType.GOOD, "40", ItemCondition.USED);
        add("Pricey used lamp", "desk", Category.ELECTRONICS, ListingType.GOOD, "400", ItemCondition.USED);
        add("Cheap new lamp", "desk", Category.ELECTRONICS, ListingType.GOOD, "45", ItemCondition.NEW);
        add("Cheap used chair", "desk", Category.FURNITURE, ListingType.GOOD, "40", ItemCondition.USED);

        search("?q=lamp&category=ELECTRONICS&condition=USED&maxPrice=100")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Cheap used lamp"));
    }

    @Test
    void fr3_07_resultsArePagedNewestFirstWithOurOwnPageShape() throws Exception {
        for (int i = 1; i <= 25; i++) {
            book("Book " + i, "10");
        }

        search("?size=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].title").value("Book 25"))   // newest first
                .andExpect(jsonPath("$.pageable").doesNotExist())            // not Spring's PageImpl shape
                .andExpect(jsonPath("$.content").doesNotExist());
        search("?size=10&page=2").andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[4].title").value("Book 1"));
        search("?size=10&page=9").andExpect(status().isOk())                 // beyond the last page: empty, not an error
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void fr3_08_noMatchesIsAnEmptyListNotAnError() throws Exception {
        book("Book", "10");
        search("?q=zzzz-nothing").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void fr3_09_badParametersGet400WithAMessage() throws Exception {
        search("?size=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.size").exists());
        search("?size=1000").andExpect(status().isBadRequest());
        search("?page=-1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.page").exists());
        search("?minPrice=200&maxPrice=100").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minPrice").exists());
        search("?minPrice=-5").andExpect(status().isBadRequest());
        search("?category=NOT_A_CATEGORY").andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        search("?minPrice=abc").andExpect(status().isBadRequest());
        search("?q=" + "x".repeat(101)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.q").exists());
    }

    @Test
    void fr3_10_sqlInjectionTextIsJustText() throws Exception {
        book("Safe book", "10");
        // If this text were glued into SQL it would break the query or return everything; as a parameter it matches nothing.
        search("?q=%27%20OR%20%271%27%3D%271").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        search("?q=%27%3B%20DROP%20TABLE%20listings%3B--").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        search("").andExpect(jsonPath("$.totalItems").value(1)); // the table is still there
    }
}
