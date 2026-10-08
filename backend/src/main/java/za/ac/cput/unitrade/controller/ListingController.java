package za.ac.cput.unitrade.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import za.ac.cput.unitrade.domain.Category;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.ListingType;
import za.ac.cput.unitrade.dto.ListingRequest;
import za.ac.cput.unitrade.dto.ListingResponse;
import za.ac.cput.unitrade.dto.ListingSearchCriteria;
import za.ac.cput.unitrade.dto.PageResponse;
import za.ac.cput.unitrade.security.AuthenticatedUser;
import za.ac.cput.unitrade.service.ListingService;

import java.math.BigDecimal;
import java.util.List;

/** FR2 endpoints (create, edit, delete) and FR3 search. Browsing is public; changing listings needs a login. */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody ListingRequest request) {
        return listingService.create(user.id(), request);
    }

    /** FR3: public search. Every parameter is optional; only ACTIVE listings are returned. */
    @GetMapping
    public PageResponse<ListingResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) ListingType type,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ListingSearchCriteria.DEFAULT_SIZE) int size) {
        return listingService.search(new ListingSearchCriteria(q, category, type, condition, minPrice, maxPrice, page, size));
    }

    @GetMapping("/mine")
    public List<ListingResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return listingService.mine(user.id());
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable Long id) {
        return listingService.get(id);
    }

    @PutMapping("/{id}")
    public ListingResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @Valid @RequestBody ListingRequest request) {
        return listingService.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        listingService.delete(user.id(), id);
    }
}
