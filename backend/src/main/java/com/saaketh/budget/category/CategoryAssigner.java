package com.saaketh.budget.category;

import com.saaketh.budget.transaction.CategorySource;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides a transaction's category automatically. Order of precedence:
 * the bank's label (via bank_category_mappings), then Uncategorized.
 * (Piece 3 adds the user's rules in front of the bank mapping.)
 */
@Service
public class CategoryAssigner {

    /** The outcome: categoryId and source are both null for Uncategorized. */
    public record Assignment(Long categoryId, CategorySource source) {

        static final Assignment NONE = new Assignment(null, null);
    }

    /**
     * Everything needed to categorize one user's transactions, loaded once (e.g. per import)
     * instead of once per row.
     */
    public record Context(Map<String, Long> bankLabelToCategoryId) {
    }

    private final CategoryRepository categoryRepository;
    private final BankCategoryMappingRepository mappingRepository;

    public CategoryAssigner(CategoryRepository categoryRepository, BankCategoryMappingRepository mappingRepository) {
        this.categoryRepository = categoryRepository;
        this.mappingRepository = mappingRepository;
    }

    @Transactional(readOnly = true)
    public Context contextFor(Long userId) {
        Map<String, Long> categoryIdByLowerName = categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), Category::getId));
        // Keep only mappings whose target category this user still has (it may be renamed or deleted).
        Map<String, Long> bankLabelToCategoryId = mappingRepository.findAll().stream()
                .filter(m -> categoryIdByLowerName.containsKey(m.getCategoryName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toMap(BankCategoryMapping::getBankLabel,
                        m -> categoryIdByLowerName.get(m.getCategoryName().toLowerCase(Locale.ROOT))));
        return new Context(bankLabelToCategoryId);
    }

    public Assignment assign(Context context, String description, String bankCategory) {
        if (bankCategory != null) {
            Long categoryId = context.bankLabelToCategoryId().get(bankCategory.trim().toLowerCase(Locale.ROOT));
            if (categoryId != null) {
                return new Assignment(categoryId, CategorySource.BANK);
            }
        }
        return Assignment.NONE;
    }
}
