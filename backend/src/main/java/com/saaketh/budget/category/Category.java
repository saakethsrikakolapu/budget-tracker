package com.saaketh.budget.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One of a user's spending categories, e.g. "Food & Dining". */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    /** False for e.g. "Payments & Transfers", which budgets and spending totals should leave out. */
    @Column(name = "counts_as_spending", nullable = false)
    private boolean countsAsSpending;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
    }

    public Category(Long userId, String name, boolean countsAsSpending) {
        this.userId = userId;
        this.name = name;
        this.countsAsSpending = countsAsSpending;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public boolean isCountsAsSpending() {
        return countsAsSpending;
    }

    /** JPA notices field changes on loaded entities and saves them when the transaction commits. */
    void update(String name, boolean countsAsSpending) {
        this.name = name;
        this.countsAsSpending = countsAsSpending;
    }
}
