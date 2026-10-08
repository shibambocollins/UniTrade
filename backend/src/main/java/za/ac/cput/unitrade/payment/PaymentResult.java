package za.ac.cput.unitrade.payment;

/** Outcome of a payment attempt. {@code reference} identifies the payment when approved. */
public record PaymentResult(boolean approved, String reference, String message) {

    public static PaymentResult approved(String reference) {
        return new PaymentResult(true, reference, "Payment approved");
    }

    public static PaymentResult declined(String message) {
        return new PaymentResult(false, null, message);
    }
}
