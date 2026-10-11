package com.saaketh.budget.recurring;

import com.saaketh.budget.category.Category;
import com.saaketh.budget.category.CategoryService;
import com.saaketh.budget.common.BadRequestException;
import com.saaketh.budget.common.NotFoundException;
import com.saaketh.budget.recurring.RecurringDetector.Cadence;
import com.saaketh.budget.recurring.RecurringDetector.Charge;
import com.saaketh.budget.recurring.RecurringDetector.Detection;
import com.saaketh.budget.transaction.TransactionRepository;
import com.saaketh.budget.transaction.TransactionRepository.SpendingRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recurring charges for one user: detected fresh from their transactions on every request. */
@Service
public class RecurringService {

    /**
     * @param merchantKey identifies the merchant (used to hide it); see MerchantKey
     * @param name a friendlier version of the key, e.g. "Planet Fitness"
     * @param description the latest charge exactly as the bank wrote it
     * @param amount the latest charge (the current price), positive
     * @param active false = no charge when one was expected: probably cancelled
     */
    public record RecurringCharge(String merchantKey, String name, String description, Cadence cadence,
            BigDecimal amount, BigDecimal monthlyCost, int chargeCount, LocalDate firstDate, LocalDate lastDate,
            LocalDate nextExpected, boolean active, Long categoryId, String categoryName) {
    }

    /**
     * @param charges recurring charges the user hasn't hidden, most expensive per month first
     * @param hidden the ones they've marked "not a subscription", so they can undo it
     * @param activeMonthlyTotal what the active, visible charges cost per month
     * @param asOf the latest transaction date: "cancelled" is judged from here, not from today, since
     *     statements are uploaded after the fact
     */
    public record RecurringOverview(List<RecurringCharge> charges, List<RecurringCharge> hidden,
            BigDecimal activeMonthlyTotal, LocalDate asOf) {
    }

    /** Only what MerchantKey can produce, so the key is safe in a URL. */
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Z0-9]+( [A-Z0-9]+)*$");
    private static final Set<String> WEB_SUFFIXES = Set.of("COM", "NET", "ORG", "TV", "IO");

    private final TransactionRepository transactionRepository;
    private final HiddenMerchantRepository hiddenMerchantRepository;
    private final CategoryService categoryService;

    public RecurringService(TransactionRepository transactionRepository,
            HiddenMerchantRepository hiddenMerchantRepository, CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.hiddenMerchantRepository = hiddenMerchantRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public RecurringOverview overview(Long userId) {
        LocalDate asOf = transactionRepository.findLatestDate(userId).orElse(null);
        if (asOf == null) {
            return new RecurringOverview(List.of(), List.of(), BigDecimal.ZERO.setScale(2), null);
        }
        List<SpendingRow> rows = transactionRepository.findSpending(userId);
        Map<Long, Category> categories = categoryService.byId(userId);
        Set<String> hiddenKeys = hiddenMerchantRepository.findByUserId(userId).stream()
                .map(HiddenMerchant::getMerchantKey)
                .collect(Collectors.toSet());

        // Each merchant's category is the one on its most recent charge.
        Map<String, SpendingRow> latestByKey = new HashMap<>();
        for (SpendingRow row : rows) {
            latestByKey.merge(MerchantKey.of(row.description()), row,
                    (a, b) -> b.date().isAfter(a.date()) ? b : a);
        }

        List<Charge> charges = rows.stream().map(r -> new Charge(r.date(), r.description(), r.amount())).toList();
        List<RecurringCharge> visible = new ArrayList<>();
        List<RecurringCharge> hidden = new ArrayList<>();
        for (Detection d : RecurringDetector.detect(charges, asOf)) {
            Long categoryId = latestByKey.get(d.merchantKey()).categoryId();
            Category category = categoryId == null ? null : categories.get(categoryId);
            // Card payments and transfers repeat monthly too, but they aren't subscriptions.
            if (category != null && !category.isCountsAsSpending()) {
                continue;
            }
            RecurringCharge charge = new RecurringCharge(d.merchantKey(), displayName(d.merchantKey()),
                    d.latestDescription(), d.cadence(), d.amount(), d.monthlyCost(), d.chargeCount(), d.firstDate(),
                    d.lastDate(), d.nextExpected(), d.active(), category == null ? null : categoryId,
                    category == null ? null : category.getName());
            (hiddenKeys.contains(d.merchantKey()) ? hidden : visible).add(charge);
        }

        // Active first (the ones still costing money), then by cost.
        visible.sort(Comparator.comparing(RecurringCharge::active).reversed()
                .thenComparing(Comparator.comparing(RecurringCharge::monthlyCost).reversed()));
        BigDecimal total = visible.stream()
                .filter(RecurringCharge::active)
                .map(RecurringCharge::monthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        return new RecurringOverview(visible, hidden, total, asOf);
    }

    /** "Not a subscription": leave this merchant off the list. Doing it twice is harmless. */
    @Transactional
    public void hide(Long userId, String merchantKey) {
        validate(merchantKey);
        if (!hiddenMerchantRepository.existsByUserIdAndMerchantKey(userId, merchantKey)) {
            hiddenMerchantRepository.save(new HiddenMerchant(userId, merchantKey));
        }
    }

    @Transactional
    public void unhide(Long userId, String merchantKey) {
        validate(merchantKey);
        if (hiddenMerchantRepository.deleteByUserIdAndMerchantKey(userId, merchantKey) == 0) {
            throw new NotFoundException("That merchant isn't hidden");
        }
    }

    private static void validate(String merchantKey) {
        if (merchantKey == null || merchantKey.length() > 200 || !VALID_KEY.matcher(merchantKey).matches()) {
            throw new BadRequestException("Not a valid merchant");
        }
    }

    /** "NETFLIX COM" -> "Netflix", "PLANET FITNESS" -> "Planet Fitness". */
    static String displayName(String merchantKey) {
        List<String> words = new ArrayList<>(Arrays.asList(merchantKey.split(" ")));
        if (words.size() > 1 && WEB_SUFFIXES.contains(words.getLast())) {
            words.removeLast();
        }
        return words.stream()
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
