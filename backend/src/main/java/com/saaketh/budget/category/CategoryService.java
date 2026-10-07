package com.saaketh.budget.category;

import com.saaketh.budget.category.CategoryRepository.TransactionCount;
import com.saaketh.budget.common.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    public record CategoryWithCount(Category category, long transactionCount) {
    }

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** Called at signup so every new user starts with a useful set. */
    @Transactional
    public void createDefaults(Long userId) {
        categoryRepository.saveAll(DefaultCategories.ALL.stream()
                .map(d -> new Category(userId, d.name(), d.countsAsSpending()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryWithCount> list(Long userId) {
        Map<Long, Long> counts = categoryRepository.countTransactionsByCategory(userId).stream()
                .collect(Collectors.toMap(TransactionCount::categoryId, TransactionCount::count));
        return categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(c -> new CategoryWithCount(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public Category create(Long userId, String name, boolean countsAsSpending) {
        String trimmed = name.trim();
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, trimmed)) {
            throw new CategoryNameTakenException();
        }
        return saveOrConflict(new Category(userId, trimmed, countsAsSpending));
    }

    @Transactional
    public CategoryWithCount update(Long userId, Long categoryId, String name, boolean countsAsSpending) {
        Category category = getOwned(userId, categoryId);
        String trimmed = name.trim();
        // Renaming "food" to "Food" is fine: only *other* categories count as a clash.
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, trimmed, categoryId)) {
            throw new CategoryNameTakenException();
        }
        category.update(trimmed, countsAsSpending);
        Category saved = saveOrConflict(category);
        return new CategoryWithCount(saved, categoryRepository.countTransactions(userId, categoryId));
    }

    /** Its transactions become Uncategorized (the database's ON DELETE SET NULL), not deleted. */
    @Transactional
    public void delete(Long userId, Long categoryId) {
        categoryRepository.delete(getOwned(userId, categoryId));
    }

    /** Someone else's category looks exactly like a missing one (404). */
    @Transactional(readOnly = true)
    public Category getOwned(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private Category saveOrConflict(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            // Two requests creating the same name at once: the unique index lets only one through.
            throw new CategoryNameTakenException();
        }
    }
}
