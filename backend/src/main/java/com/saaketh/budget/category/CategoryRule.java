package com.saaketh.budget.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** "If the description contains {@code pattern}, use {@code categoryId}." */
@Entity
@Table(name = "category_rules")
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Normalized: uppercase with single spaces (see CategoryRules.normalize). */
    @Column(nullable = false)
    private String pattern;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CategoryRule() {
    }

    public CategoryRule(Long userId, String pattern, Long categoryId) {
        this.userId = userId;
        this.pattern = pattern;
        this.categoryId = categoryId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPattern() {
        return pattern;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void update(String pattern, Long categoryId) {
        this.pattern = pattern;
        this.categoryId = categoryId;
    }
}
