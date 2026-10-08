package za.ac.cput.unitrade;

import org.junit.jupiter.api.Test;
import za.ac.cput.unitrade.payment.MockPaymentGateway;
import za.ac.cput.unitrade.payment.PaymentRequest;
import za.ac.cput.unitrade.payment.PaymentResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR4: the simulated payment provider in isolation. */
class Fr4PaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    private PaymentResult charge(String card) {
        return gateway.charge(new PaymentRequest("ORDER-1", new BigDecimal("100.00"), card));
    }

    @Test
    void fr4_16_normalCardsAreApprovedWithAReference() {
        PaymentResult result = charge("4242424242424242");
        assertTrue(result.approved());
        assertTrue(result.reference().startsWith("MOCK-"));
    }

    @Test
    void fr4_17_theDocumentedTestCardIsDeclined() {
        for (String card : new String[]{"4000000000000002", "4000 0000 0000 0002", "4000-0000-0000-0002"}) {
            PaymentResult result = charge(card);
            assertFalse(result.approved(), card);
            assertNull(result.reference());
            assertNotNull(result.message());
        }
    }
}
