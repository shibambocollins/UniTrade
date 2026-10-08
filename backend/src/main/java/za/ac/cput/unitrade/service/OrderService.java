package za.ac.cput.unitrade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.domain.Order;
import za.ac.cput.unitrade.domain.OrderStatus;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.dto.CheckoutRequest;
import za.ac.cput.unitrade.dto.OrderResponse;
import za.ac.cput.unitrade.dto.PayRequest;
import za.ac.cput.unitrade.dto.ReviewResponse;
import za.ac.cput.unitrade.repository.ReviewRepository;
import za.ac.cput.unitrade.exception.ApiException;
import za.ac.cput.unitrade.payment.PaymentGateway;
import za.ac.cput.unitrade.payment.PaymentRequest;
import za.ac.cput.unitrade.payment.PaymentResult;
import za.ac.cput.unitrade.repository.ListingRepository;
import za.ac.cput.unitrade.repository.OrderRepository;
import za.ac.cput.unitrade.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR4: checkout creates an order (PENDING); paying it through the PaymentGateway makes it PAID and marks the listings
 * SOLD; the buyer then confirms receipt (COMPLETED).
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final ListingRepository listings;
    private final UserRepository users;
    private final PaymentGateway paymentGateway;
    private final ReviewRepository reviews;

    public OrderService(OrderRepository orders, ListingRepository listings, UserRepository users,
                        PaymentGateway paymentGateway, ReviewRepository reviews) {
        this.orders = orders;
        this.listings = listings;
        this.users = users;
        this.paymentGateway = paymentGateway;
        this.reviews = reviews;
    }

    /** Checkout: turns the cart into a PENDING order. Nothing is reserved or sold yet. */
    @Transactional
    public OrderResponse checkout(Long buyerId, CheckoutRequest request) {
        Set<Long> ids = new HashSet<>(request.listingIds());
        if (ids.size() != request.listingIds().size()) {
            throw ApiException.badRequest("The same listing is in your cart twice.");
        }
        List<Listing> found = listings.findWithSellerByIdIn(ids);
        if (found.size() != ids.size()) {
            throw ApiException.notFound("One of the listings in your cart no longer exists.");
        }
        for (Listing listing : found) {
            if (listing.getSeller().getId().equals(buyerId)) {
                throw ApiException.forbidden("You cannot buy your own listing (\"" + listing.getTitle() + "\").");
            }
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                throw ApiException.conflict("\"" + listing.getTitle() + "\" is no longer available. Remove it from your cart.");
            }
        }
        Set<Long> sellerIds = new HashSet<>();
        found.forEach(listing -> sellerIds.add(listing.getSeller().getId()));
        if (sellerIds.size() > 1) {
            throw ApiException.badRequest("An order can only contain items from one seller. Check out one seller at a time.");
        }

        User buyer = users.findById(buyerId).orElseThrow(() -> ApiException.unauthorized("Please log in to continue."));
        Order order = new Order(buyer, found.get(0).getSeller());
        found.forEach(order::addItem); // title and price are copied from the database, never taken from the browser
        return OrderResponse.from(orders.save(order));
    }

    /**
     * Pays a PENDING order. The listings are locked first, so when two buyers pay for the same listing at the same
     * moment one waits for the other: the first marks it SOLD and the second then sees it is gone (409).
     * If the gateway declines (402) nothing changes and the buyer may try another card.
     */
    @Transactional
    public OrderResponse pay(Long buyerId, Long orderId, PayRequest request) {
        // Load only the order row first. Loading the listings here would put possibly stale copies in memory.
        Order order = orders.findShallowById(orderId)
                .orElseThrow(() -> ApiException.notFound("We could not find that order."));
        requireBuyer(order, buyerId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("This order has already been paid.");
        }
        String cardNumber = checkedCardNumber(request.cardNumber());

        // Lock and re-read the listings: the status we see now is the real, current one.
        List<Listing> locked = listings.lockAllByIdIn(orders.findListingIds(orderId));
        for (Listing listing : locked) {
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                throw ApiException.conflict("\"" + listing.getTitle() + "\" was just sold to someone else. "
                        + "You have not been charged.");
            }
        }

        PaymentResult result = paymentGateway.charge(new PaymentRequest("ORDER-" + order.getId(), order.getTotal(), cardNumber));
        if (!result.approved()) {
            throw ApiException.paymentDeclined(result.message());
        }

        locked.forEach(listing -> listing.setStatus(ListingStatus.SOLD));
        order.markPaid(result.reference());
        return OrderResponse.from(order);
    }

    /** The buyer says the goods or service arrived: PAID → COMPLETED. This unlocks the review (FR6). */
    @Transactional
    public OrderResponse confirmReceipt(Long buyerId, Long orderId) {
        Order order = orders.findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("We could not find that order."));
        requireBuyer(order, buyerId);
        if (order.getStatus() == OrderStatus.PENDING) {
            throw ApiException.conflict("Pay for this order before confirming that you received it.");
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.conflict("You already confirmed this order.");
        }
        order.markCompleted();
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long buyerId, Long orderId) {
        Order order = orders.findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("We could not find that order."));
        requireBuyer(order, buyerId);
        return OrderResponse.from(order, reviews.findByOrderId(orderId).map(ReviewResponse::from).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> mine(Long buyerId) {
        List<Order> mine = orders.findByBuyerIdOrderByCreatedAtDescIdDesc(buyerId);
        // One query for all reviews of these orders (instead of one per order)
        Map<Long, ReviewResponse> reviewByOrder = reviews.findByOrderIdIn(mine.stream().map(Order::getId).toList())
                .stream().collect(Collectors.toMap(review -> review.getOrder().getId(), ReviewResponse::from));
        return mine.stream().map(order -> OrderResponse.from(order, reviewByOrder.get(order.getId()))).toList();
    }

    private static void requireBuyer(Order order, Long userId) {
        if (!order.getBuyer().getId().equals(userId)) {
            throw ApiException.forbidden("This is not your order.");
        }
    }

    /** Accepts spaces and dashes as typed ("4242 4242 4242 4242"); the rest must be 12 to 19 digits. */
    private static String checkedCardNumber(String raw) {
        String digits = raw.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{12,19}")) {
            throw ApiException.badRequest("cardNumber", "Enter a valid card number (12 to 19 digits)");
        }
        return digits;
    }
}
