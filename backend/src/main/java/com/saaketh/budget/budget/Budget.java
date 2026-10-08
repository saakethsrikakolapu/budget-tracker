package com.saaketh.budget.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** A monthly spending limit for one category; applies to every month. */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Column(name = "monthly_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Budget() {
    }

    public Budget(Long userId, Long categoryId, BigDecimal monthlyLimit) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.monthlyLimit = monthlyLimit;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }
}
