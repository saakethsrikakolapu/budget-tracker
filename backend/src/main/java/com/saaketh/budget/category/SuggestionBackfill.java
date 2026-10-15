package com.saaketh.budget.category;

import com.saaketh.budget.transaction.Transaction;
import com.saaketh.budget.transaction.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * At startup, gives model suggestions to spending that's still Uncategorized: transactions
 * imported before the model existed (or before it was retrained). New imports get suggestions as
 * they arrive, so after the first run this finds little or nothing. Manual choices are never
 * touched: they're never Uncategorized-with-no-source.
 */
@Component
public class SuggestionBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuggestionBackfill.class);

    private final TransactionRepository transactionRepository;
    private final CategoryAssigner assigner;

    public SuggestionBackfill(TransactionRepository transactionRepository, CategoryAssigner assigner) {
        this.transactionRepository = transactionRepository;
        this.assigner = assigner;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<Long, List<Transaction>> byUser = transactionRepository.findUncategorizedSpending().stream()
                .collect(Collectors.groupingBy(Transaction::getUserId));
        int suggested = 0;
        for (Map.Entry<Long, List<Transaction>> entry : byUser.entrySet()) {
            CategoryAssigner.Context context = assigner.contextFor(entry.getKey());
            for (Transaction t : entry.getValue()) {
                CategoryAssigner.Assignment a = assigner.assign(context, t.getDescription(), t.getBankCategory(), t.getAmount());
                if (a.categoryId() != null) {
                    t.setCategory(a.categoryId(), a.source());
                    suggested++;
                }
            }
        }
        if (suggested > 0) {
            log.info("Categorized {} older uncategorized transactions with the model", suggested);
        }
    }
}
