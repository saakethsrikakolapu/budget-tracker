package com.saaketh.budget.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One line from a bank statement. */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "import_batch_id", nullable = false, updatable = false)
    private Long importBatchId;

    /** When the purchase happened; budgets use this date. */
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /** When the bank finalized it; may be missing for some banks. */
    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(nullable = false)
    private String description;

    /** Negative = money spent, positive = refund or payment. Always BigDecimal, never double. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "bank_category")
    private String bankCategory;

    /** The user's category; null means Uncategorized. */
    @Column(name = "category_id")
    private Long categoryId;

    /** How categoryId was decided; MANUAL ones are never overwritten by automation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category_source")
    private CategorySource categorySource;

    /** See TransactionFingerprint: identifies "the same purchase" for duplicate detection. */
    @Column(nullable = false, updatable = false)
    private String fingerprint;

    /** 1 for the first copy of a purchase in this account, 2 for an identical second purchase, ... */
    @Column(nullable = false, updatable = false)
    private int occurrence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(Long userId, Long accountId, Long importBatchId, LocalDate transactionDate,
            LocalDate postedDate, String description, BigDecimal amount, String bankCategory, int occurrence) {
        this.userId = userId;
        this.accountId = accountId;
        this.importBatchId = importBatchId;
        this.transactionDate = transactionDate;
        this.postedDate = postedDate;
        this.description = description;
        this.amount = amount;
        this.bankCategory = bankCategory;
        this.fingerprint = TransactionFingerprint.of(transactionDate, amount, description);
        this.occurrence = occurrence;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getBankCategory() {
        return bankCategory;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    /** @param categoryId null for Uncategorized */
    public void setCategory(Long categoryId, CategorySource source) {
        this.categoryId = categoryId;
        this.categorySource = source;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public int getOccurrence() {
        return occurrence;
    }
}
