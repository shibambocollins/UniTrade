package za.ac.cput.unitrade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Something a student offers for sale. The indexes are on the columns the search (FR3) filters and sorts by;
 * without them every search would scan the whole table.
 */
@Entity
@Table(name = "listings", indexes = {
        @Index(name = "idx_listings_status_created", columnList = "status, created_at"),
        @Index(name = "idx_listings_category", columnList = "category"),
        @Index(name = "idx_listings_type", columnList = "type"),
        @Index(name = "idx_listings_condition", columnList = "item_condition"),
        @Index(name = "idx_listings_price", columnList = "price"),
        @Index(name = "idx_listings_seller", columnList = "seller_id")
})
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ListingType type;

    /** Price in South African rand, two decimals. */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Column is not called "condition" because that is a reserved word in MySQL.
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", length = 10)
    private ItemCondition condition;

    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ListingStatus status = ListingStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Optimistic locking: if two requests change the same listing at once, the second one fails (used in Slice 4). */
    @Version
    private long version;

    protected Listing() {
        // required by JPA
    }

    private Listing(Builder builder) {
        this.title = builder.title;
        this.description = builder.description;
        this.category = builder.category;
        this.type = builder.type;
        this.price = builder.price;
        this.condition = builder.condition;
        this.imageUrl = builder.imageUrl;
        this.seller = builder.seller;
        if (builder.status != null) {
            this.status = builder.status;
        }
    }

    /** Applies the editable fields (everything except seller, status and createdAt). */
    public void updateDetails(String title, String description, Category category, ListingType type,
                              BigDecimal price, ItemCondition condition, String imageUrl) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = type;
        this.price = price;
        this.condition = condition;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public ListingType getType() { return type; }
    public BigDecimal getPrice() { return price; }
    public ItemCondition getCondition() { return condition; }
    public String getImageUrl() { return imageUrl; }
    public ListingStatus getStatus() { return status; }
    public User getSeller() { return seller; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(ListingStatus status) { this.status = status; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title;
        private String description;
        private Category category;
        private ListingType type;
        private BigDecimal price;
        private ItemCondition condition;
        private String imageUrl;
        private ListingStatus status;
        private User seller;

        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder category(Category category) { this.category = category; return this; }
        public Builder type(ListingType type) { this.type = type; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder condition(ItemCondition condition) { this.condition = condition; return this; }
        public Builder imageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
        public Builder status(ListingStatus status) { this.status = status; return this; }
        public Builder seller(User seller) { this.seller = seller; return this; }

        public Listing build() {
            return new Listing(this);
        }
    }
}
