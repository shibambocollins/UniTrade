package za.ac.cput.unitrade.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.ac.cput.unitrade.domain.User;

import java.util.Optional;

/** Spring Data generates the (parameterised) SQL for these methods from their names. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
