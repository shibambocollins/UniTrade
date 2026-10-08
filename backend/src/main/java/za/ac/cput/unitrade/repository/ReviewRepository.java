package za.ac.cput.unitrade.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.ac.cput.unitrade.domain.Review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = "reviewer")
    Optional<Review> findByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"reviewer", "order"})
    List<Review> findByOrderIdIn(Collection<Long> orderIds);

    /** Reviews a seller received, newest first; the reviewer's name is joined in the same query. */
    @EntityGraph(attributePaths = "reviewer")
    List<Review> findBySellerIdOrderByCreatedAtDescIdDesc(Long sellerId);

    long countBySellerId(Long sellerId);

    /** Average rating, or null when the seller has no reviews yet. */
    @Query("select avg(r.rating) from Review r where r.seller.id = :sellerId")
    Double averageRatingBySellerId(@Param("sellerId") Long sellerId);
}
