package za.ac.cput.unitrade.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.domain.ListingType;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.dto.ListingRequest;
import za.ac.cput.unitrade.dto.ListingResponse;
import za.ac.cput.unitrade.dto.ListingSearchCriteria;
import za.ac.cput.unitrade.dto.PageResponse;
import za.ac.cput.unitrade.repository.ListingSpecifications;
import za.ac.cput.unitrade.exception.ApiException;
import za.ac.cput.unitrade.repository.ListingRepository;
import za.ac.cput.unitrade.repository.UserRepository;

import java.math.RoundingMode;
import java.util.List;

/** FR2: create, read, edit and delete listings. Only the owner may edit or delete. */
@Service
public class ListingService {

    private final ListingRepository listings;
    private final UserRepository users;
    private final ReviewService reviewService;

    public ListingService(ListingRepository listings, UserRepository users, ReviewService reviewService) {
        this.listings = listings;
        this.users = users;
        this.reviewService = reviewService;
    }

    @Transactional
    public ListingResponse create(Long userId, ListingRequest request) {
        User seller = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Please log in to continue."));
        ItemCondition condition = checkedCondition(request);
        Listing listing = Listing.builder()
                .title(request.title().trim())
                .description(clean(request.description()))
                .category(request.category())
                .type(request.type())
                .price(request.price().setScale(2, RoundingMode.HALF_UP))
                .condition(condition)
                .imageUrl(clean(request.imageUrl()))
                .seller(seller)
                .build();
        return ListingResponse.from(listings.save(listing));
    }

    /** Anyone may view a listing, including sold ones (the link in an order must keep working). Removed ones are gone. */
    @Transactional(readOnly = true)
    public ListingResponse get(Long id) {
        Listing listing = find(id);
        // The detail page also shows the seller's average rating (FR6); lists skip it to stay cheap.
        return ListingResponse.from(listing, reviewService.summaryOf(listing.getSeller()));
    }

    @Transactional
    public ListingResponse update(Long userId, Long id, ListingRequest request) {
        Listing listing = find(id);
        requireOwner(listing, userId);
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw ApiException.conflict("A sold listing can no longer be edited.");
        }
        listing.updateDetails(request.title().trim(), clean(request.description()), request.category(),
                request.type(), request.price().setScale(2, RoundingMode.HALF_UP), checkedCondition(request),
                clean(request.imageUrl()));
        return ListingResponse.from(listing);
    }

    /** Soft delete: the row stays (orders may refer to it) but disappears from the app. */
    @Transactional
    public void delete(Long userId, Long id) {
        Listing listing = find(id);
        requireOwner(listing, userId);
        if (listing.getStatus() == ListingStatus.SOLD) {
            throw ApiException.conflict("A sold listing cannot be deleted.");
        }
        listing.setStatus(ListingStatus.REMOVED);
    }

    /** The logged-in student's own listings (active and sold), newest first. */
    @Transactional(readOnly = true)
    public List<ListingResponse> mine(Long userId) {
        return listings.findBySellerIdAndStatusNotOrderByCreatedAtDesc(userId, ListingStatus.REMOVED).stream()
                .map(ListingResponse::from)
                .toList();
    }

    /** FR3: ACTIVE listings matching the filters, newest first, one page at a time. */
    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> search(ListingSearchCriteria requested) {
        ListingSearchCriteria criteria = validated(requested).normalised();
        // Newest first; id breaks ties so paging is stable when several listings share a timestamp.
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(listings.findAll(ListingSpecifications.matching(criteria), pageRequest),
                ListingResponse::from);
    }

    private static ListingSearchCriteria validated(ListingSearchCriteria c) {
        if (c.page() < 0) {
            throw ApiException.badRequest("page", "Page cannot be negative");
        }
        if (c.size() < 1 || c.size() > ListingSearchCriteria.MAX_SIZE) {
            throw ApiException.badRequest("size", "Page size must be between 1 and " + ListingSearchCriteria.MAX_SIZE);
        }
        if (c.q() != null && c.q().trim().length() > ListingSearchCriteria.MAX_QUERY_LENGTH) {
            throw ApiException.badRequest("q", "Search text must be at most " + ListingSearchCriteria.MAX_QUERY_LENGTH + " characters");
        }
        if (c.minPrice() != null && c.minPrice().signum() < 0) {
            throw ApiException.badRequest("minPrice", "Minimum price cannot be negative");
        }
        if (c.maxPrice() != null && c.maxPrice().signum() < 0) {
            throw ApiException.badRequest("maxPrice", "Maximum price cannot be negative");
        }
        if (c.minPrice() != null && c.maxPrice() != null && c.minPrice().compareTo(c.maxPrice()) > 0) {
            throw ApiException.badRequest("minPrice", "Minimum price cannot be more than the maximum price");
        }
        return c;
    }

    private Listing find(Long id) {
        return listings.findWithSellerById(id)
                .filter(found -> found.getStatus() != ListingStatus.REMOVED)
                .orElseThrow(() -> ApiException.notFound("This listing does not exist or was removed."));
    }

    private static void requireOwner(Listing listing, Long userId) {
        if (!listing.getSeller().getId().equals(userId)) {
            throw ApiException.forbidden("You can only change your own listings.");
        }
    }

    /** Goods need a condition; services have none. */
    private static ItemCondition checkedCondition(ListingRequest request) {
        if (request.type() == ListingType.SERVICE) {
            return null;
        }
        if (request.condition() == null) {
            throw ApiException.badRequest("condition", "Choose NEW or USED for goods");
        }
        return request.condition();
    }

    /** Blank optional text is stored as null. */
    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
