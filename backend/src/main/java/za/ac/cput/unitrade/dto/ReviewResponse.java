package za.ac.cput.unitrade.dto;

import za.ac.cput.unitrade.domain.Review;

import java.time.Instant;

/** A review as shown to others: the rating, the text and who wrote it (name only). */
public record ReviewResponse(Long id, int rating, String comment, String reviewerName, Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getRating(), review.getComment(),
                review.getReviewer().getFullName(), review.getCreatedAt());
    }
}
