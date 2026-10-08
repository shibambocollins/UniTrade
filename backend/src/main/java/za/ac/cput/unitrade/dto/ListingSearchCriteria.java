package za.ac.cput.unitrade.dto;

import za.ac.cput.unitrade.domain.Category;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.ListingType;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Everything a search can ask for (FR3). {@link #normalised()} gives one canonical form, so the same question
 * always looks the same ("Lamp " and "lamp" are one search). Slice 7 uses it as the cache key.
 */
public record ListingSearchCriteria(String q, Category category, ListingType type, ItemCondition condition,
                                    BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_QUERY_LENGTH = 100;

    public ListingSearchCriteria normalised() {
        String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return new ListingSearchCriteria(text.isEmpty() ? null : text, category, type, condition,
                minPrice == null ? null : minPrice.stripTrailingZeros(),
                maxPrice == null ? null : maxPrice.stripTrailingZeros(), page, size);
    }
}
