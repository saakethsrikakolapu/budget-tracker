package com.saaketh.budget.transaction;

import com.saaketh.budget.account.Account;
import com.saaketh.budget.account.AccountService;
import com.saaketh.budget.category.CategoryService;
import com.saaketh.budget.common.NotFoundException;
import com.saaketh.budget.transaction.TransactionQueryService.TransactionItem;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user changing a transaction's category by hand. */
@Service
public class TransactionCategoryService {

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final AccountService accountService;

    public TransactionCategoryService(TransactionRepository transactionRepository, CategoryService categoryService,
            AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.accountService = accountService;
    }

    /**
     * @param categoryId null to make it Uncategorized
     * Marked MANUAL, so rules and future imports never change it back.
     */
    @Transactional
    public TransactionItem setCategory(Long userId, Long transactionId, Long categoryId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        if (categoryId != null) {
            categoryService.getOwned(userId, categoryId); // 404 if it isn't this user's category
        }
        transaction.setCategory(categoryId, CategorySource.MANUAL);

        Map<Long, String> accountNames = accountService.list(userId).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        return TransactionQueryService.toItem(transaction, accountNames, categoryService.byId(userId));
    }
}
