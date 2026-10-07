package com.saaketh.budget.transaction;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

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

    List<Transaction> findByImportBatchIdOrderByIdAsc(Long importBatchId);

    long countByImportBatchId(Long importBatchId);

    long countByAccountId(Long accountId);
}
