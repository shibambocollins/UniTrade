package za.ac.cput.unitrade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/orders/{id}/review. */
public record ReviewRequest(
        @NotNull(message = "Choose a rating from 1 to 5")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @Size(max = 1000, message = "Review must be at most 1000 characters")
        String comment) {
}
