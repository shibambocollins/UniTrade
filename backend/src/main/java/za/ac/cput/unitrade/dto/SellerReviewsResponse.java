package za.ac.cput.unitrade.dto;

import java.util.List;

/** A student's seller profile: average rating (null when there are no reviews), count and the reviews. */
public record SellerReviewsResponse(SellerSummary seller, Double averageRating, long reviewCount,
                                    List<ReviewResponse> reviews) {
}
