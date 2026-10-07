package com.saaketh.budget.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A record of one uploaded statement file. The file itself is never stored. */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportBatch() {
    }

    public ImportBatch(Long userId, Long accountId, String fileName, int rowCount) {
        this.userId = userId;
        this.accountId = accountId;
        this.fileName = fileName;
        this.rowCount = rowCount;
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

    public String getFileName() {
        return fileName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
