package com.saaketh.budget.transaction;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.transaction.TransactionQueryService.Filter;
import com.saaketh.budget.transaction.TransactionQueryService.TransactionPage;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionQueryService queryService;

    public TransactionController(TransactionQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * e.g. GET /api/transactions?accountId=3&from=2026-09-01&to=2026-09-30&q=coffee&page=0&size=50
     * Every filter is optional. Results are newest first; totals cover all matches, not just this page.
     */
    @GetMapping
    public TransactionPage search(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TransactionQueryService.DEFAULT_PAGE_SIZE) int size) {
        return queryService.search(user.getId(), new Filter(accountId, from, to, q), page, size);
    }

    /** Months that have transactions, newest first, for the month picker. */
    @GetMapping("/months")
    public List<String> months(@AuthenticationPrincipal AuthenticatedUser user) {
        return queryService.months(user.getId());
    }
}
