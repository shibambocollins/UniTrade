package za.ac.cput.unitrade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * The buyer's rating (1 to 5) and optional text for the seller of a COMPLETED order.
 * The unique key on order_id enforces "one review per order" in the database itself, so even two simultaneous
 * requests cannot create two reviews.
 */
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_reviews_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_reviews_seller", columnList = "seller_id, created_at"))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Review() {
        // required by JPA
    }

    public Review(Order order, int rating, String comment) {
        this.order = order;
        this.reviewer = order.getBuyer();
        this.seller = order.getSeller();
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public User getReviewer() { return reviewer; }
    public User getSeller() { return seller; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
