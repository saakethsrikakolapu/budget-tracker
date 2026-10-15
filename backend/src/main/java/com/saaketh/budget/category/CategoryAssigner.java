package com.saaketh.budget.category;

import com.saaketh.budget.transaction.CategorySource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides a transaction's category automatically. Order of precedence:
 * <ol>
 *   <li>the user's rules (most specific first)</li>
 *   <li>the bank's own category label (via bank_category_mappings)</li>
 *   <li>the built-in merchant dictionary (merchant_keywords), for banks without category labels</li>
 *   <li>the machine-learning model's suggestion, for spending only and only when it's confident</li>
 *   <li>otherwise Uncategorized</li>
 * </ol>
 * Manual choices sit above all of these; callers never pass manual transactions through here.
 */
@Service
public class CategoryAssigner {

    /** The outcome: categoryId and source are both null for Uncategorized. */
    public record Assignment(Long categoryId, CategorySource source) {

        static final Assignment NONE = new Assignment(null, null);
    }

    /**
     * Everything needed to categorize one user's transactions, loaded once (e.g. per import)
     * instead of once per row. Only entries whose target category the user still has are kept
     * (they may have renamed or deleted a default category).
     */
    public record Context(List<CategoryRule> rulesMostSpecificFirst, Map<String, Long> bankLabelToCategoryId,
            List<MerchantKeywords.Keyword> keywordsMostSpecificFirst, Map<String, Long> categoryIdByLowerName) {
    }

    private final CategoryRepository categoryRepository;
    private final BankCategoryMappingRepository mappingRepository;
    private final CategoryRuleRepository ruleRepository;
    private final MerchantKeywordRepository keywordRepository;
    private final CategoryModel model;

    public CategoryAssigner(CategoryRepository categoryRepository, BankCategoryMappingRepository mappingRepository,
            CategoryRuleRepository ruleRepository, MerchantKeywordRepository keywordRepository, CategoryModel model) {
        this.model = model;
        this.categoryRepository = categoryRepository;
        this.mappingRepository = mappingRepository;
        this.ruleRepository = ruleRepository;
        this.keywordRepository = keywordRepository;
    }

    @Transactional(readOnly = true)
    public Context contextFor(Long userId) {
        Map<String, Long> categoryIdByLowerName = categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), Category::getId));
        Map<String, Long> bankLabelToCategoryId = mappingRepository.findAll().stream()
                .filter(m -> categoryIdByLowerName.containsKey(m.getCategoryName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toMap(BankCategoryMapping::getBankLabel,
                        m -> categoryIdByLowerName.get(m.getCategoryName().toLowerCase(Locale.ROOT))));
        List<CategoryRule> rules = ruleRepository.findByUserId(userId).stream()
                .sorted(CategoryRules.MOST_SPECIFIC_FIRST)
                .toList();
        List<MerchantKeywords.Keyword> keywords = keywordRepository.findAll().stream()
                .map(MerchantKeywordEntry::toKeyword)
                .filter(k -> categoryIdByLowerName.containsKey(k.categoryName().toLowerCase(Locale.ROOT)))
                .sorted(MerchantKeywords.MOST_SPECIFIC_FIRST)
                .toList();
        return new Context(rules, bankLabelToCategoryId, keywords, categoryIdByLowerName);
    }

    /** @param amount negative = spent, positive = money in (some keywords only apply to money in) */
    public Assignment assign(Context context, String description, String bankCategory, BigDecimal amount) {
        Optional<CategoryRule> rule = CategoryRules.firstMatch(context.rulesMostSpecificFirst(), description);
        if (rule.isPresent()) {
            return new Assignment(rule.get().getCategoryId(), CategorySource.RULE);
        }
        Long fromBank = bankLabel(context, bankCategory);
        if (fromBank != null) {
            return new Assignment(fromBank, CategorySource.BANK);
        }
        Optional<MerchantKeywords.Keyword> keyword =
                MerchantKeywords.match(context.keywordsMostSpecificFirst(), description, amount);
        if (keyword.isPresent()) {
            Long categoryId = context.categoryIdByLowerName().get(keyword.get().categoryName().toLowerCase(Locale.ROOT));
            return new Assignment(categoryId, CategorySource.KEYWORD);
        }
        // Last resort: the model, trained on spending categories only (money in is left to the rules).
        if (amount != null && amount.signum() < 0) {
            Optional<Long> suggested = model.suggest(description)
                    .map(s -> context.categoryIdByLowerName().get(s.categoryName().toLowerCase(Locale.ROOT)));
            if (suggested.isPresent()) { // the user may have renamed or deleted that category
                return new Assignment(suggested.get(), CategorySource.SUGGESTED);
            }
        }
        return Assignment.NONE;
    }

    private static Long bankLabel(Context context, String bankCategory) {
        if (bankCategory == null) {
            return null;
        }
        String label = bankCategory.trim().toLowerCase(Locale.ROOT);
        Long categoryId = context.bankLabelToCategoryId().get(label);
        // Some banks use "Group-Subgroup" labels (e.g. "Restaurant-Restaurant"); try the group.
        if (categoryId == null && label.contains("-")) {
            categoryId = context.bankLabelToCategoryId().get(label.substring(0, label.indexOf('-')).trim());
        }
        return categoryId;
    }
}
