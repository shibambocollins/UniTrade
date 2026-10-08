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

import java.math.BigDecimal;

/** One line of an order. Title and price are copied from the listing at checkout time. */
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id"),
        @Index(name = "idx_order_items_listing", columnList = "listing_id")
})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected OrderItem() {
        // required by JPA
    }

    OrderItem(Order order, Listing listing) {
        this.order = order;
        this.listing = listing;
        this.title = listing.getTitle();
        this.price = listing.getPrice();
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public Listing getListing() { return listing; }
    public String getTitle() { return title; }
    public BigDecimal getPrice() { return price; }
}
