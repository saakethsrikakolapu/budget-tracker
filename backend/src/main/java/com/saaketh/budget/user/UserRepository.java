package com.saaketh.budget.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for users. Spring Data generates the implementation from the method names,
 * e.g. findByEmail becomes "SELECT ... FROM users WHERE email = ?".
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
