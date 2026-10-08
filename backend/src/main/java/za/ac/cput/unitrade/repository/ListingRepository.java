package za.ac.cput.unitrade.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    // The seller is loaded in the same query (join), so building a response does not trigger one extra query per listing.
    @EntityGraph(attributePaths = "seller")
    Optional<Listing> findWithSellerById(Long id);

    @EntityGraph(attributePaths = "seller")
    List<Listing> findWithSellerByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "seller")
    List<Listing> findBySellerIdAndStatusNotOrderByCreatedAtDesc(Long sellerId, ListingStatus excluded);

    /**
     * Reads the listings AND locks their rows until the transaction ends (SQL: SELECT ... FOR UPDATE).
     * Two buyers paying for the same listing at the same moment queue up here: the second one only gets the row after
     * the first has committed, and then sees status SOLD. Ordered by id so two orders locking the same listings
     * always lock them in the same order (no deadlock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l join fetch l.seller where l.id in :ids order by l.id")
    List<Listing> lockAllByIdIn(@Param("ids") Collection<Long> ids);

    /** Search (FR3): the filters arrive as a Specification; the seller is joined in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = "seller")
    Page<Listing> findAll(Specification<Listing> spec, Pageable pageable);
}
