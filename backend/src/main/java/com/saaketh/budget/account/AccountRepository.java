package com.saaketh.budget.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes userId so one user can never read another user's accounts. */
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByNameAsc(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
