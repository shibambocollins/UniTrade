package za.ac.cput.unitrade.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import za.ac.cput.unitrade.domain.User;

/**
 * The public part of a seller shown on a listing (never the email). The rating fields are only filled where they are
 * cheap to compute (one listing's detail page), so they are left out of the JSON elsewhere.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SellerSummary(Long id, String fullName, Double averageRating, Long reviewCount) {

    public static SellerSummary from(User seller) {
        return new SellerSummary(seller.getId(), seller.getFullName(), null, null);
    }

    /** With rating: averageRating stays null (and is omitted) when the seller has no reviews yet. */
    public static SellerSummary from(User seller, Double averageRating, long reviewCount) {
        return new SellerSummary(seller.getId(), seller.getFullName(), averageRating, reviewCount);
    }
}
