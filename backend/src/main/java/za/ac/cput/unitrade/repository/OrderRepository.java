package za.ac.cput.unitrade.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.ac.cput.unitrade.domain.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Order with everything needed to build a response, in one query. */
    @EntityGraph(attributePaths = {"items", "items.listing", "seller", "buyer"})
    Optional<Order> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"items", "items.listing", "seller", "buyer"})
    List<Order> findByBuyerIdOrderByCreatedAtDescIdDesc(Long buyerId);

    /** Only the order row (no lines, no listings), used before the listings are locked so nothing stale is cached. */
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findShallowById(@Param("id") Long id);

    /** Ids of the listings on an order. */
    @Query("select i.listing.id from OrderItem i where i.order.id = :orderId order by i.listing.id")
    List<Long> findListingIds(@Param("orderId") Long orderId);
}
