package com.saaketh.budget.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes userId so one user can never read or change another user's categories. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    /** For creating: is the name already used (ignoring case)? */
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    /** For renaming: is the name used by one of this user's *other* categories? */
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.categoryId = :categoryId")
    long countTransactions(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

    record TransactionCount(Long categoryId, long count) {
    }

    /** How many transactions each of this user's categories has (categories with none are left out). */
    @Query("""
            SELECT new com.saaketh.budget.category.CategoryRepository$TransactionCount(t.categoryId, COUNT(t))
            FROM Transaction t
            WHERE t.userId = :userId AND t.categoryId IS NOT NULL
            GROUP BY t.categoryId""")
    List<TransactionCount> countTransactionsByCategory(@Param("userId") Long userId);
}
