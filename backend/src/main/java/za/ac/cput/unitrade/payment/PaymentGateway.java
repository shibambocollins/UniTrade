package za.ac.cput.unitrade.payment;

/**
 * The only thing the order code knows about taking payment. Today the implementation is MockPaymentGateway;
 * a real PayFast integration, or a call to a separate payment service (Slice 8), would implement the same interface
 * and nothing in OrderService would change.
 */
public interface PaymentGateway {

    PaymentResult charge(PaymentRequest request);
}
