package com.saaketh.budget.recurring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A merchant the user said isn't a subscription, so it's left off the recurring list. */
@Entity
@Table(name = "recurring_hidden")
public class HiddenMerchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "merchant_key", nullable = false, updatable = false)
    private String merchantKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HiddenMerchant() {
    }

    public HiddenMerchant(Long userId, String merchantKey) {
        this.userId = userId;
        this.merchantKey = merchantKey;
        this.createdAt = Instant.now();
    }

    public String getMerchantKey() {
        return merchantKey;
    }
}
