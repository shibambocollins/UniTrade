package za.ac.cput.unitrade.domain;

/** PENDING (created, not paid) → PAID (payment approved, listings SOLD) → COMPLETED (buyer confirmed receipt). */
public enum OrderStatus {
    PENDING,
    PAID,
    COMPLETED
}
