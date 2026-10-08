package za.ac.cput.unitrade.domain;

/**
 * ACTIVE: shown in search and can be bought. SOLD: paid for (Slice 4). REMOVED: deleted by the owner;
 * kept in the database (soft delete) so past orders still point at a real row.
 */
public enum ListingStatus {
    ACTIVE,
    SOLD,
    REMOVED
}
