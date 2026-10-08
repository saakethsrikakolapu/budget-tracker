package com.saaketh.budget.category;

import com.saaketh.budget.common.BadRequestException;
import com.saaketh.budget.common.NotFoundException;
import com.saaketh.budget.transaction.Transaction;
import com.saaketh.budget.transaction.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, changing, and deleting rules. Every change re-runs automatic categorization over the
 * user's non-manual transactions, so the rules always describe what you see.
 */
@Service
public class CategoryRuleService {

    static final int MIN_PATTERN_LENGTH = 2;

    public record RuleView(Long id, String pattern, Long categoryId, String categoryName, long matchCount) {
    }

    /** @param recategorizedCount how many transactions changed category because of this change */
    public record RuleChange(RuleView rule, int recategorizedCount) {
    }

    private final CategoryRuleRepository ruleRepository;
    private final CategoryService categoryService;
    private final CategoryAssigner assigner;
    private final TransactionRepository transactionRepository;

    public CategoryRuleService(CategoryRuleRepository ruleRepository, CategoryService categoryService,
            CategoryAssigner assigner, TransactionRepository transactionRepository) {
        this.ruleRepository = ruleRepository;
        this.categoryService = categoryService;
        this.assigner = assigner;
        this.transactionRepository = transactionRepository;
    }

    /** Most specific first (the order they're applied in). */
    @Transactional(readOnly = true)
    public List<RuleView> list(Long userId) {
        Map<Long, Category> categories = categoryService.byId(userId);
        List<String> descriptions = transactionRepository.findDescriptions(userId).stream()
                .map(CategoryRules::normalize)
                .toList();
        return ruleRepository.findByUserId(userId).stream()
                .sorted(CategoryRules.MOST_SPECIFIC_FIRST)
                .map(rule -> view(rule, categories, descriptions))
                .toList();
    }

    @Transactional
    public RuleChange create(Long userId, String pattern, Long categoryId) {
        String normalized = validPattern(pattern);
        categoryService.getOwned(userId, categoryId);
        if (ruleRepository.existsByUserIdAndPattern(userId, normalized)) {
            throw new RulePatternTakenException();
        }
        CategoryRule rule = saveOrConflict(new CategoryRule(userId, normalized, categoryId));
        return new RuleChange(viewOf(userId, rule), recategorize(userId));
    }

    @Transactional
    public RuleChange update(Long userId, Long ruleId, String pattern, Long categoryId) {
        CategoryRule rule = getOwned(userId, ruleId);
        String normalized = validPattern(pattern);
        categoryService.getOwned(userId, categoryId);
        if (ruleRepository.existsByUserIdAndPatternAndIdNot(userId, normalized, ruleId)) {
            throw new RulePatternTakenException();
        }
        rule.update(normalized, categoryId);
        CategoryRule saved = saveOrConflict(rule);
        return new RuleChange(viewOf(userId, saved), recategorize(userId));
    }

    /** Transactions the rule had categorized fall back to the next rule or the bank's label. */
    @Transactional
    public int delete(Long userId, Long ruleId) {
        ruleRepository.delete(getOwned(userId, ruleId));
        ruleRepository.flush();
        return recategorize(userId);
    }

    /**
     * Re-runs automatic categorization on every non-manual transaction of this user.
     * JPA saves the changed entities when the transaction commits (dirty checking).
     *
     * @return how many transactions ended up in a different category
     */
    @Transactional
    public int recategorize(Long userId) {
        CategoryAssigner.Context context = assigner.contextFor(userId);
        int changed = 0;
        for (Transaction t : transactionRepository.findAutoCategorized(userId)) {
            CategoryAssigner.Assignment a = assigner.assign(context, t.getDescription(), t.getBankCategory());
            if (!Objects.equals(a.categoryId(), t.getCategoryId())) {
                changed++;
            }
            if (!Objects.equals(a.categoryId(), t.getCategoryId()) || a.source() != t.getCategorySource()) {
                t.setCategory(a.categoryId(), a.source());
            }
        }
        return changed;
    }

    private CategoryRule getOwned(Long userId, Long ruleId) {
        return ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new NotFoundException("Rule not found"));
    }

    private static String validPattern(String pattern) {
        String normalized = CategoryRules.normalize(pattern);
        if (normalized.length() < MIN_PATTERN_LENGTH) {
            // One letter would match nearly everything.
            throw new BadRequestException("The text to match must be at least %d characters".formatted(MIN_PATTERN_LENGTH));
        }
        return normalized;
    }

    private CategoryRule saveOrConflict(CategoryRule rule) {
        try {
            return ruleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException e) {
            throw new RulePatternTakenException();
        }
    }

    private RuleView viewOf(Long userId, CategoryRule rule) {
        List<String> descriptions = transactionRepository.findDescriptions(userId).stream()
                .map(CategoryRules::normalize)
                .toList();
        return view(rule, categoryService.byId(userId), descriptions);
    }

    private static RuleView view(CategoryRule rule, Map<Long, Category> categories, List<String> descriptions) {
        long matches = descriptions.stream().filter(d -> d.contains(rule.getPattern())).count();
        Category category = categories.get(rule.getCategoryId());
        return new RuleView(rule.getId(), rule.getPattern(), rule.getCategoryId(),
                category == null ? null : category.getName(), matches);
    }
}
