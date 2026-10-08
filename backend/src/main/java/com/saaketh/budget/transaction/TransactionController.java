package com.saaketh.budget.transaction;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.transaction.TransactionQueryService.Filter;
import com.saaketh.budget.transaction.TransactionQueryService.TransactionItem;
import com.saaketh.budget.transaction.TransactionQueryService.TransactionPage;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    /** categoryId null means Uncategorized. */
    public record SetCategoryRequest(Long categoryId) {
    }

    private final TransactionQueryService queryService;
    private final TransactionCategoryService categoryService;

    public TransactionController(TransactionQueryService queryService, TransactionCategoryService categoryService) {
        this.queryService = queryService;
        this.categoryService = categoryService;
    }

    /**
     * e.g. GET /api/transactions?accountId=3&from=2026-09-01&to=2026-09-30&q=coffee&category=7&page=0&size=50
     * (category is a category id or "uncategorized")
     * Every filter is optional. Results are newest first; totals cover all matches, not just this page.
     */
    @GetMapping
    public TransactionPage search(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TransactionQueryService.DEFAULT_PAGE_SIZE) int size) {
        return queryService.search(user.getId(), new Filter(accountId, from, to, q, category), page, size);
    }

    /** Change one transaction's category by hand. */
    @PutMapping("/{id}/category")
    public TransactionItem setCategory(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @RequestBody SetCategoryRequest request) {
        return categoryService.setCategory(user.getId(), id, request.categoryId());
    }

    /** Months that have transactions, newest first, for the month picker. */
    @GetMapping("/months")
    public List<String> months(@AuthenticationPrincipal AuthenticatedUser user) {
        return queryService.months(user.getId());
    }
}
