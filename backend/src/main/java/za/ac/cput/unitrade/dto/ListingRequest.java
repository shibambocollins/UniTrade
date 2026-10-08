package za.ac.cput.unitrade.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import za.ac.cput.unitrade.domain.Category;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.ListingType;

import java.math.BigDecimal;

/** Body of POST /api/listings and PUT /api/listings/{id}. */
public record ListingRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotNull(message = "Choose a category")
        Category category,

        @NotNull(message = "Choose goods or service")
        ListingType type,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Price must be at most 8 digits with 2 decimals")
        BigDecimal price,

        // Required for goods, ignored for services: checked in ListingService.
        ItemCondition condition,

        // URL string only (no file upload). Only http(s) links, so "javascript:" style values are refused.
        @Size(max = 500, message = "Image link must be at most 500 characters")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Image link must start with http:// or https://")
        String imageUrl) {
}
