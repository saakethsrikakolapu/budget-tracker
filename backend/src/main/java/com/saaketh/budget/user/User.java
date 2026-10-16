package com.saaketh.budget.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A registered user. Maps to the "users" table created in V1__create_users.sql. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the user proved they own the email; null until then. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Opted in to the monthly "time to upload your statement" email. */
    @Column(name = "upload_reminders", nullable = false)
    private boolean uploadReminders;

    /** The month ("2026-10") the last reminder went out, so it's never sent twice in a month. */
    @Column(name = "last_reminder_month", length = 7)
    private String lastReminderMonth;

    /** Required by JPA, which creates objects by reflection when loading rows. */
    protected User() {
    }

    /** @param email must already be normalized (trimmed, lowercase) */
    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified() {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Instant.now();
        }
    }

    public boolean isUploadReminders() {
        return uploadReminders;
    }

    public void setUploadReminders(boolean uploadReminders) {
        this.uploadReminders = uploadReminders;
    }

    public String getLastReminderMonth() {
        return lastReminderMonth;
    }

    public void setLastReminderMonth(String lastReminderMonth) {
        this.lastReminderMonth = lastReminderMonth;
    }

    /** @param passwordHash already encoded (BCrypt), never the plain password */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
