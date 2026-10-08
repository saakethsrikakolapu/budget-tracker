package com.saaketh.budget.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A bank's category label and the default category it maps to (seeded by V6; read-only here). */
@Entity
@Table(name = "bank_category_mappings")
public class BankCategoryMapping {

    /** Lowercase, e.g. "dining". */
    @Id
    @Column(name = "bank_label")
    private String bankLabel;

    /** e.g. "Food & Dining"; matched case-insensitively against the user's category names. */
    @Column(name = "category_name", nullable = false)
    private String categoryName;

    protected BankCategoryMapping() {
    }

    public String getBankLabel() {
        return bankLabel;
    }

    public String getCategoryName() {
        return categoryName;
    }
}
