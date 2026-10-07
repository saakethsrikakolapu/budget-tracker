package com.saaketh.budget.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByImportBatchIdOrderByIdAsc(Long importBatchId);

    long countByUserId(Long userId);
}
