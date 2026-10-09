package com.saaketh.budget.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A user's remembered column mapping for files with a particular shape (see FileSignature). */
@Entity
@Table(name = "import_formats")
public class ImportFormat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String signature;

    /** ColumnMapping serialized as JSON. */
    @Column(nullable = false)
    private String mapping;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImportFormat() {
    }

    public ImportFormat(Long userId, String signature, String mapping) {
        this.userId = userId;
        this.signature = signature;
        this.mapping = mapping;
        this.updatedAt = Instant.now();
    }

    public String getMapping() {
        return mapping;
    }

    void setMapping(String mapping) {
        this.mapping = mapping;
        this.updatedAt = Instant.now();
    }
}
