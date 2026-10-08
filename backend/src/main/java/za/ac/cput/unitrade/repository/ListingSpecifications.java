package za.ac.cput.unitrade.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.dto.ListingSearchCriteria;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the search query (FR3) from the filters the user chose. Every value is passed as a query parameter by
 * the JPA criteria API, never glued into SQL text, so there is no SQL injection.
 */
public final class ListingSpecifications {

    private ListingSpecifications() {
    }

    /** Only ACTIVE listings, plus whichever optional filters were given (all combined with AND). */
    public static Specification<Listing> matching(ListingSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("status"), ListingStatus.ACTIVE));

            if (c.q() != null) {
                // Case-insensitive "contains" on title or description. % and _ typed by the user are escaped so they
                // are matched literally instead of acting as wildcards.
                String pattern = "%" + escapeLike(c.q().toLowerCase()) + "%";
                all.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (c.category() != null) {
                all.add(cb.equal(root.get("category"), c.category()));
            }
            if (c.type() != null) {
                all.add(cb.equal(root.get("type"), c.type()));
            }
            if (c.condition() != null) {
                all.add(cb.equal(root.get("condition"), c.condition()));
            }
            if (c.minPrice() != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("price"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                all.add(cb.lessThanOrEqualTo(root.get("price"), c.maxPrice()));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
