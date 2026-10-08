package za.ac.cput.unitrade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.Order;
import za.ac.cput.unitrade.domain.OrderStatus;
import za.ac.cput.unitrade.domain.Review;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.dto.ReviewRequest;
import za.ac.cput.unitrade.dto.ReviewResponse;
import za.ac.cput.unitrade.dto.SellerReviewsResponse;
import za.ac.cput.unitrade.dto.SellerSummary;
import za.ac.cput.unitrade.exception.ApiException;
import za.ac.cput.unitrade.repository.OrderRepository;
import za.ac.cput.unitrade.repository.ReviewRepository;
import za.ac.cput.unitrade.repository.UserRepository;

/** FR6: after an order is COMPLETED the buyer rates the seller (1 to 5, optional text), once per order. */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final OrderRepository orders;
    private final UserRepository users;

    public ReviewService(ReviewRepository reviews, OrderRepository orders, UserRepository users) {
        this.reviews = reviews;
        this.orders = orders;
        this.users = users;
    }

    @Transactional
    public ReviewResponse create(Long buyerId, Long orderId, ReviewRequest request) {
        Order order = orders.findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("We could not find that order."));
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw ApiException.forbidden("You can only review orders you bought.");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw ApiException.conflict("You can review the seller after you confirm that you received the order.");
        }
        if (reviews.existsByOrderId(orderId)) {
            throw ApiException.conflict("You already reviewed this order.");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        // If two requests slip past the check above at the same instant, the database's unique key on order_id
        // refuses the second one and the global handler turns that into a 409.
        return ReviewResponse.from(reviews.save(new Review(order, request.rating(), comment)));
    }

    /** A seller's profile for reviews: average (one decimal), count and the reviews, newest first. Public. */
    @Transactional(readOnly = true)
    public SellerReviewsResponse forSeller(Long sellerId) {
        User seller = users.findById(sellerId).orElseThrow(() -> ApiException.notFound("We could not find that student."));
        SellerSummary summary = summaryOf(seller);
        return new SellerReviewsResponse(summary, summary.averageRating(), summary.reviewCount(),
                reviews.findBySellerIdOrderByCreatedAtDescIdDesc(sellerId).stream().map(ReviewResponse::from).toList());
    }

    /** The seller with their average rating (null if never reviewed) and review count. */
    @Transactional(readOnly = true)
    public SellerSummary summaryOf(User seller) {
        Double average = reviews.averageRatingBySellerId(seller.getId());
        Double rounded = average == null ? null : Math.round(average * 10.0) / 10.0;
        return SellerSummary.from(seller, rounded, reviews.countBySellerId(seller.getId()));
    }
}
