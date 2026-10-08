package za.ac.cput.unitrade.payment;

import java.math.BigDecimal;

/** What the gateway needs: which order, how much, and the card. The card number is never stored by UniTrade. */
public record PaymentRequest(String orderReference, BigDecimal amount, String cardNumber) {
}
