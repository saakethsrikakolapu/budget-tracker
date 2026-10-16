package com.saaketh.budget.transaction;

import com.saaketh.budget.account.Account;
import com.saaketh.budget.account.AccountService;
import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.category.Category;
import com.saaketh.budget.category.CategoryService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Download my data": every transaction as a CSV that opens in Excel or Google Sheets. Your data
 * is yours, so you can always take it with you.
 */
@RestController
public class TransactionExportController {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public TransactionExportController(TransactionRepository transactionRepository, AccountService accountService,
            CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
    }

    /** GET /api/transactions/export: newest first, one row per transaction. */
    @GetMapping("/api/transactions/export")
    @Transactional(readOnly = true)
    public ResponseEntity<String> export(@AuthenticationPrincipal AuthenticatedUser user) {
        Map<Long, String> accounts = accountService.list(user.getId()).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        Map<Long, String> categories = categoryService.byId(user.getId()).values().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        StringBuilder csv = new StringBuilder("Date,Description,Amount,Category,Account\r\n");
        for (Transaction t : transactionRepository.findAllForExport(user.getId())) {
            csv.append(t.getTransactionDate()).append(',')
                    .append(cell(t.getDescription())).append(',')
                    .append(t.getAmount().toPlainString()).append(',')
                    .append(cell(t.getCategoryId() == null ? "Uncategorized" : categories.get(t.getCategoryId())))
                    .append(',')
                    .append(cell(accounts.get(t.getAccountId())))
                    .append("\r\n");
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"spendr-transactions-" + LocalDate.now() + ".csv\"")
                .body(csv.toString());
    }

    /**
     * One text cell. Quotes and commas are escaped the CSV way, and text that a spreadsheet would
     * run as a formula (starting with = + - @) gets a leading apostrophe: a bank description like
     * "=HYPERLINK(...)" must stay text when the file is opened ("CSV injection").
     */
    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
