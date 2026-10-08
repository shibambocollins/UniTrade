package za.ac.cput.unitrade.dto;

import za.ac.cput.unitrade.domain.Category;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.domain.ListingType;

import java.math.BigDecimal;
import java.time.Instant;

/** What the API returns for a listing. */
public record ListingResponse(Long id, String title, String description, Category category, ListingType type,
                              BigDecimal price, ItemCondition condition, String imageUrl, ListingStatus status,
                              Instant createdAt, SellerSummary seller) {

    public static ListingResponse from(Listing listing) {
        return from(listing, SellerSummary.from(listing.getSeller()));
    }

    /** Same, with a seller summary that already carries the rating (used by the detail page). */
    public static ListingResponse from(Listing listing, SellerSummary seller) {
        return new ListingResponse(listing.getId(), listing.getTitle(), listing.getDescription(),
                listing.getCategory(), listing.getType(), listing.getPrice(), listing.getCondition(),
                listing.getImageUrl(), listing.getStatus(), listing.getCreatedAt(), seller);
    }
}
