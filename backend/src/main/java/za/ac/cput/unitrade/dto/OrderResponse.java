package za.ac.cput.unitrade.dto;

import za.ac.cput.unitrade.domain.Order;
import za.ac.cput.unitrade.domain.OrderItem;
import za.ac.cput.unitrade.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** What the API returns for an order. {@code review} is the buyer's review of this order, or null if none yet. */
public record OrderResponse(Long id, OrderStatus status, BigDecimal total, Instant createdAt, Instant paidAt,
                            Instant completedAt, String paymentReference, SellerSummary seller,
                            List<Item> items, ReviewResponse review) {

    public record Item(Long listingId, String title, BigDecimal price) {
        static Item from(OrderItem item) {
            return new Item(item.getListing().getId(), item.getTitle(), item.getPrice());
        }
    }

    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    public static OrderResponse from(Order order, ReviewResponse review) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotal(), order.getCreatedAt(),
                order.getPaidAt(), order.getCompletedAt(), order.getPaymentReference(),
                SellerSummary.from(order.getSeller()), order.getItems().stream().map(Item::from).toList(), review);
    }
}
