package za.ac.cput.unitrade.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/orders/{id}/pay. The card number is passed to the payment gateway and never stored. */
public record PayRequest(@NotBlank(message = "Card number is required") String cardNumber) {
}
