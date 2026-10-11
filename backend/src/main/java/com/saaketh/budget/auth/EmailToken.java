package com.saaketh.budget.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A one-time emailed link (see V13). Only the token's hash is stored. */
@Entity
@Table(name = "email_tokens")
public class EmailToken {

    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Purpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailToken() {
    }

    EmailToken(Long userId, Purpose purpose, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    Long getUserId() {
        return userId;
    }

    boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    void markUsed(Instant now) {
        usedAt = now;
    }
}
