package com.saaketh.budget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JpaSpecificationExecutor adds findAll(Specification, Pageable) for filtered, paged queries. */
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** For one fingerprint already in an account: how many copies exist, and the highest occurrence used. */
    record FingerprintStats(String fingerprint, long count, int maxOccurrence) {
    }

    /** One query for the whole file instead of one per row. */
    @Query("""
            SELECT new com.saaketh.budget.transaction.TransactionRepository$FingerprintStats(
                       t.fingerprint, COUNT(t), MAX(t.occurrence))
            FROM Transaction t
            WHERE t.accountId = :accountId AND t.fingerprint IN :fingerprints
            GROUP BY t.fingerprint""")
    List<FingerprintStats> findFingerprintStats(@Param("accountId") Long accountId,
            @Param("fingerprints") Collection<String> fingerprints);

    record YearMonthRow(int year, int month) {
    }

    @Query("""
            SELECT DISTINCT new com.saaketh.budget.transaction.TransactionRepository$YearMonthRow(
                       year(t.transactionDate), month(t.transactionDate))
            FROM Transaction t
            WHERE t.userId = :userId
            ORDER BY year(t.transactionDate) DESC, month(t.transactionDate) DESC""")
    List<YearMonthRow> findMonthsWithTransactions(@Param("userId") Long userId);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    /** Everything automation is allowed to (re)categorize: all except the user's manual choices. */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.userId = :userId
              AND (t.categorySource IS NULL OR t.categorySource <> com.saaketh.budget.transaction.CategorySource.MANUAL)""")
    List<Transaction> findAutoCategorized(@Param("userId") Long userId);

    @Query("SELECT t.description FROM Transaction t WHERE t.userId = :userId")
    List<String> findDescriptions(@Param("userId") Long userId);

    /** Sum of amounts for one category (categoryId null = Uncategorized). Negative = net spending. */
    record CategoryTotal(Long categoryId, BigDecimal total) {
    }

    /** One row per category with transactions between from and to (inclusive). Used by budgets and charts. */
    @Query("""
            SELECT new com.saaketh.budget.transaction.TransactionRepository$CategoryTotal(t.categoryId, SUM(t.amount))
            FROM Transaction t
            WHERE t.userId = :userId AND t.transactionDate BETWEEN :from AND :to
            GROUP BY t.categoryId""")
    List<CategoryTotal> sumByCategory(@Param("userId") Long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<Transaction> findByImportBatchIdOrderByIdAsc(Long importBatchId);

    long countByImportBatchId(Long importBatchId);

    long countByAccountId(Long accountId);
}
