package com.saaketh.budget.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    List<CategoryRule> findByUserId(Long userId);

    Optional<CategoryRule> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndPattern(Long userId, String pattern);

    boolean existsByUserIdAndPatternAndIdNot(Long userId, String pattern, Long id);
}
