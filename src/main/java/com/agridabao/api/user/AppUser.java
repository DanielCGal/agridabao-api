package com.agridabao.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Bumped whenever every existing session for this account must stop working.
     * Each access token carries the value current when it was issued, and the
     * server refuses any token whose value no longer matches - see
     * SecurityConfig. Zero is the value for accounts that predate the column,
     * and matches a token carrying no version at all.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(UUID id, String email, String passwordHash, String displayName,
                   LocalDate dateOfBirth, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.dateOfBirth = dateOfBirth;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getTokenVersion() { return tokenVersion; }

    // Only the three fields a player can edit from the Account panel are
    // mutable. Each stamps updatedAt itself so no caller can change an account
    // without leaving a trace of when it happened.

    public void changeEmail(String newEmail) {
        this.email = newEmail;
        touch();
    }

    /**
     * Sets a new password and ends every session the account already had.
     *
     * The two happen together on purpose. Someone changing their password
     * because another person is using their account expects that person to be
     * shut out, and a password change that leaves the intruder signed in for
     * another month does not do what the player thinks it does. Whoever makes
     * the change is handed a fresh token in the same response, so they stay
     * signed in and only the other sessions fall away.
     *
     * Changing an email deliberately does not do this: it is not a response to
     * losing control of the account, and signing the player out of their other
     * phone would be a surprise rather than a protection.
     */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tokenVersion++;
        touch();
    }

    public void changeDisplayName(String newDisplayName) {
        this.displayName = newDisplayName;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
