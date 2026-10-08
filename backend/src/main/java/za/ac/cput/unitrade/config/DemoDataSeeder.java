package za.ac.cput.unitrade.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.BulletinPost;
import za.ac.cput.unitrade.domain.Category;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.domain.ListingType;
import za.ac.cput.unitrade.domain.Order;
import za.ac.cput.unitrade.domain.PostCategory;
import za.ac.cput.unitrade.domain.Review;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.repository.BulletinPostRepository;
import za.ac.cput.unitrade.repository.ListingRepository;
import za.ac.cput.unitrade.repository.OrderRepository;
import za.ac.cput.unitrade.repository.ReviewRepository;
import za.ac.cput.unitrade.repository.UserRepository;

import java.math.BigDecimal;

/**
 * Inserts the documented demo data (README section 7) the first time the app starts on an empty database:
 * 3 students and 15 listings. Later slices add bulletin posts and a completed order here.
 * Existing data is never touched. Switched off in tests with app.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Documented demo password (README section 7). Demo accounts only. */
    static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository users;
    private final ListingRepository listings;
    private final OrderRepository orders;
    private final ReviewRepository reviews;
    private final BulletinPostRepository posts;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository users, ListingRepository listings, OrderRepository orders,
                          ReviewRepository reviews, BulletinPostRepository posts, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.listings = listings;
        this.orders = orders;
        this.reviews = reviews;
        this.posts = posts;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Each kind of data is seeded only while its table is empty, so a database that already has
        // students (e.g. from Slice 1) still receives the listings, and real data is never touched.
        if (users.count() == 0) {
            String hash = passwordEncoder.encode(DEMO_PASSWORD);
            users.save(demoUser("Thabo Nkosi", "thabo@mycput.ac.za", hash));
            users.save(demoUser("Ayesha Daniels", "ayesha@mycput.ac.za", hash));
            users.save(demoUser("Lerato Mokoena", "lerato@mycput.ac.za", hash));
            log.info("Seeded 3 demo students (password: {})", DEMO_PASSWORD);
        }
        if (listings.count() == 0) {
            seedListings();
        }
        if (orders.count() == 0) {
            seedCompletedOrderWithReview();
        }
        if (posts.count() == 0) {
            seedBulletinPosts();
        }
    }

    private void seedBulletinPosts() {
        var thabo = users.findByEmail("thabo@mycput.ac.za");
        var ayesha = users.findByEmail("ayesha@mycput.ac.za");
        var lerato = users.findByEmail("lerato@mycput.ac.za");
        if (thabo.isEmpty() || ayesha.isEmpty() || lerato.isEmpty()) {
            return;
        }
        posts.save(new BulletinPost(ayesha.get(), "Welcome to the UniTrade bulletin board",
                "Share notices, events and free help with other students here. Buying and selling belongs in Listings.",
                PostCategory.ANNOUNCEMENT));
        posts.save(new BulletinPost(lerato.get(), "Campus clean-up day this Saturday",
                "Meet at the main gate at 09:00. Gloves and bags are provided. Bring friends and water!",
                PostCategory.EVENT));
        posts.save(new BulletinPost(thabo.get(), "Free study group: Maths 1",
                "Every Tuesday 17:00 in the library discussion room. All first-years are welcome, no booking needed.",
                PostCategory.SERVICE));
        posts.save(new BulletinPost(thabo.get(), "Lost: blue calculator pouch",
                "Left in lecture hall B2 on Monday. If you found it please leave a message here.",
                PostCategory.ANNOUNCEMENT));
        log.info("Seeded 4 bulletin posts");
    }

    /** One finished sale (Thabo bought Ayesha's chair) with a 5-star review, so ratings and order history have content. */
    private void seedCompletedOrderWithReview() {
        var buyer = users.findByEmail("thabo@mycput.ac.za");
        var seller = users.findByEmail("ayesha@mycput.ac.za");
        if (buyer.isEmpty() || seller.isEmpty()) {
            return;
        }
        listings.findAll().stream()
                .filter(l -> l.getTitle().equals("Wooden study chair") && l.getSeller().getId().equals(seller.get().getId()))
                .filter(l -> l.getStatus() == ListingStatus.ACTIVE)
                .findFirst()
                .ifPresent(chair -> {
                    Order order = new Order(buyer.get(), seller.get());
                    order.addItem(chair);
                    order.markPaid("MOCK-DEMO0001");
                    order.markCompleted();
                    chair.setStatus(ListingStatus.SOLD);
                    orders.save(order);
                    reviews.save(new Review(order, 5, "Smooth hand-over and the chair was exactly as described."));
                    log.info("Seeded 1 completed order with a review");
                });
    }

    private void seedListings() {
        var thabo = users.findByEmail("thabo@mycput.ac.za");
        var ayesha = users.findByEmail("ayesha@mycput.ac.za");
        var lerato = users.findByEmail("lerato@mycput.ac.za");
        if (thabo.isEmpty() || ayesha.isEmpty() || lerato.isEmpty()) {
            return; // the demo students were removed: nobody to own the demo listings
        }
        seedListingsFor(thabo.get(), ayesha.get(), lerato.get());
    }

    private void seedListingsFor(User thabo, User ayesha, User lerato) {

        // seller, title, description, category, type, price, condition (null for services), image keyword
        good(thabo, "Engineering Mathematics (Stroud) 7th ed", "Lightly highlighted, no torn pages. Great for first-year maths.", Category.TEXTBOOKS, "180.00", ItemCondition.USED, "Maths-book");
        good(thabo, "Casio fx-991ZA scientific calculator", "Approved for exams. Comes with the cover.", Category.ELECTRONICS, "220.00", ItemCondition.USED, "Calculator");
        good(thabo, "Study desk lamp (LED)", "Three brightness levels, USB powered. Still in the box.", Category.ELECTRONICS, "120.00", ItemCondition.NEW, "Desk-lamp");
        good(thabo, "A4 lecture pads (pack of 5)", "Unused, ruled, 80 pages each.", Category.STATIONERY, "55.00", ItemCondition.NEW, "Lecture-pads");
        service(thabo, "Maths 1 tutoring (per hour)", "Calculus and algebra, one-on-one on campus or online. I got 78% for Maths 1.", Category.TUTORING, "90.00", "Maths-tutoring");

        good(ayesha, "Second-hand bar fridge", "Works perfectly, fits a res room. Collect from Bellville campus.", Category.FURNITURE, "950.00", ItemCondition.USED, "Bar-fridge");
        good(ayesha, "Wooden study chair", "Sturdy, comfortable cushion.", Category.FURNITURE, "200.00", ItemCondition.USED, "Study-chair");
        good(ayesha, "CPUT hoodie (size M)", "Worn twice. Navy blue with the crest.", Category.CLOTHING, "150.00", ItemCondition.USED, "CPUT-hoodie");
        good(ayesha, "Financial Accounting textbook", "Latest edition, includes the study guide.", Category.TEXTBOOKS, "300.00", ItemCondition.USED, "Accounting-book");
        service(ayesha, "CV and cover letter review", "I will fix your layout and wording within 24 hours.", Category.SERVICES, "70.00", "CV-review");

        good(lerato, "Wireless mouse", "Logitech, brand new, batteries included.", Category.ELECTRONICS, "140.00", ItemCondition.NEW, "Wireless-mouse");
        good(lerato, "Scientific poster tube and drawing set", "Used for one semester of design drawing.", Category.STATIONERY, "85.00", ItemCondition.USED, "Drawing-set");
        good(lerato, "Laptop backpack (waterproof)", "Fits a 15 inch laptop, padded back.", Category.OTHER, "260.00", ItemCondition.NEW, "Backpack");
        service(lerato, "Laundry and ironing service (per bag)", "Collected and returned within two days, Cape Town campus.", Category.SERVICES, "60.00", "Laundry");
        service(lerato, "Programming help: Java and Python", "Debugging and assignment help, explained step by step.", Category.TUTORING, "100.00", "Coding-help");
        log.info("Seeded 15 demo listings");
    }

    private void good(User seller, String title, String description, Category category, String price,
                      ItemCondition condition, String imageKeyword) {
        save(seller, title, description, category, ListingType.GOOD, price, condition, imageKeyword);
    }

    private void service(User seller, String title, String description, Category category, String price,
                         String imageKeyword) {
        save(seller, title, description, category, ListingType.SERVICE, price, null, imageKeyword);
    }

    private void save(User seller, String title, String description, Category category, ListingType type,
                      String price, ItemCondition condition, String imageKeyword) {
        listings.save(Listing.builder()
                .seller(seller).title(title).description(description).category(category).type(type)
                .price(new BigDecimal(price)).condition(condition)
                // A free placeholder-image service that draws the item's name on a navy tile (so the demo pictures
                // match the items). If it cannot load (offline) the UI shows a neutral "No photo" box instead.
                .imageUrl("https://placehold.co/600x400/1d3b6f/ffffff.png?text=" + imageKeyword.replace('-', '+'))
                .build());
    }

    private static User demoUser(String name, String email, String passwordHash) {
        return User.builder().fullName(name).email(email).passwordHash(passwordHash).build();
    }
}
