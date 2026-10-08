package za.ac.cput.unitrade.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import za.ac.cput.unitrade.domain.BulletinPost;
import za.ac.cput.unitrade.domain.PostCategory;

import java.util.Optional;

public interface BulletinPostRepository extends JpaRepository<BulletinPost, Long> {

    // The author is loaded in the same query, so listing posts does not run one extra query per post.
    @EntityGraph(attributePaths = "author")
    Page<BulletinPost> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<BulletinPost> findByCategory(PostCategory category, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<BulletinPost> findWithAuthorById(Long id);
}
