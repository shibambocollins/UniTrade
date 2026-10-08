package za.ac.cput.unitrade.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Our own paged answer: {items, page, size, totalItems, totalPages}.
 * We do not return Spring's Page/PageImpl directly: its JSON shape is large, changes between versions,
 * and it does not serialise reliably in a cache (Slice 7).
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
