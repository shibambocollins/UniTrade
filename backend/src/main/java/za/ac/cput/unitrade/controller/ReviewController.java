package za.ac.cput.unitrade.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.ac.cput.unitrade.dto.ReviewRequest;
import za.ac.cput.unitrade.dto.ReviewResponse;
import za.ac.cput.unitrade.dto.SellerReviewsResponse;
import za.ac.cput.unitrade.security.AuthenticatedUser;
import za.ac.cput.unitrade.service.ReviewService;

/** FR6 endpoints: write a review for a completed order (login needed); read a seller's reviews (public). */
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/orders/{orderId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long orderId,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(user.id(), orderId, request);
    }

    @GetMapping("/api/users/{sellerId}/reviews")
    public SellerReviewsResponse forSeller(@PathVariable Long sellerId) {
        return reviewService.forSeller(sellerId);
    }
}
