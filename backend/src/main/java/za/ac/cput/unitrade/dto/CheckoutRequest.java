package za.ac.cput.unitrade.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of POST /api/orders. Only the listing ids are sent: titles and prices are read from the database,
 * so a browser cannot choose its own price.
 */
public record CheckoutRequest(
        @NotEmpty(message = "Your cart is empty")
        @Size(max = 20, message = "An order can contain at most 20 items")
        List<@NotNull(message = "Invalid listing") Long> listingIds) {
}
