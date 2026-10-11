package com.saaketh.budget.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row of the built-in merchant dictionary (seeded by V11; read-only here). */
@Entity
@Table(name = "merchant_keywords")
public class MerchantKeywordEntry {

    @Id
    private String keyword;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "money_in_only", nullable = false)
    private boolean moneyInOnly;

    protected MerchantKeywordEntry() {
    }

    MerchantKeywords.Keyword toKeyword() {
        return new MerchantKeywords.Keyword(keyword, categoryName, moneyInOnly);
    }
}
