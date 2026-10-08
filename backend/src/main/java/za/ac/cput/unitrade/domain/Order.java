package za.ac.cput.unitrade.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One purchase: items from ONE seller, bought by one student. (One seller per order keeps "rate the seller
 * after the order" unambiguous in FR6.) The table is "orders" because "order" is a reserved SQL word.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_buyer", columnList = "buyer_id, created_at"),
        @Index(name = "idx_orders_seller", columnList = "seller_id")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    private Instant paidAt;

    private Instant completedAt;

    /** Reference returned by the payment gateway (never a card number). */
    @Column(length = 60)
    private String paymentReference;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Version
    private long version;

    protected Order() {
        // required by JPA
    }

    public Order(User buyer, User seller) {
        this.buyer = buyer;
        this.seller = seller;
        this.total = BigDecimal.ZERO.setScale(2);
    }

    /** Adds a line and keeps the total in step. The price is copied, so later price edits do not change the order. */
    public void addItem(Listing listing) {
        items.add(new OrderItem(this, listing));
        total = total.add(listing.getPrice());
    }

    public void markPaid(String reference) {
        this.status = OrderStatus.PAID;
        this.paymentReference = reference;
        this.paidAt = Instant.now();
    }

    public void markCompleted() {
        this.status = OrderStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getBuyer() { return buyer; }
    public User getSeller() { return seller; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getPaymentReference() { return paymentReference; }
    public List<OrderItem> getItems() { return items; }
}
