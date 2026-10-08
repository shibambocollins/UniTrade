package za.ac.cput.unitrade.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated payment provider (the charter allowed "simulated or functional"; no real money, no real PayFast account).
 * It behaves like a hosted checkout in test mode:
 * <ul>
 *   <li>any normal card number is APPROVED, e.g. 4242 4242 4242 4242;</li>
 *   <li>the documented test card {@value #DECLINE_TEST_CARD} is DECLINED, so the failure path can be demonstrated.</li>
 * </ul>
 * Used when payment.mode is "local" (the default).
 */
@Component
@ConditionalOnProperty(name = "payment.mode", havingValue = "local", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    /** Documented in the README: paying with this card always fails. */
    public static final String DECLINE_TEST_CARD = "4000000000000002";

    @Override
    public PaymentResult charge(PaymentRequest request) {
        String digits = request.cardNumber().replaceAll("[\\s-]", "");
        if (DECLINE_TEST_CARD.equals(digits)) {
            return PaymentResult.declined("The payment was declined by the card issuer. Please try another card.");
        }
        return PaymentResult.approved("MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}
