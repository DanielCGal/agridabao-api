package com.agridabao.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "email_verification")
public class EmailVerification {
    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationPurpose purpose;

    /**
     * The account this code belongs to, for the purposes that already know it
     * (password reset, email change). Null for SIGNUP - there is no account
     * yet - and for LOGIN, where the address alone identifies the account.
     */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerification() {
    }

    public EmailVerification(UUID id, String email, VerificationPurpose purpose, UUID userId, String codeHash,
                             String passwordHash, String displayName, LocalDate dateOfBirth,
                             Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.purpose = purpose;
        this.userId = userId;
        this.codeHash = codeHash;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.dateOfBirth = dateOfBirth;
        this.attempts = 0;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public VerificationPurpose getPurpose() { return purpose; }
    public UUID getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public int getAttempts() { return attempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void registerFailedAttempt() {
        this.attempts++;
    }

    public void markConsumed(Instant when) {
        this.consumedAt = when;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
