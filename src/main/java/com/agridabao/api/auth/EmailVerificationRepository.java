package com.agridabao.api.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {
    Optional<EmailVerification> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, VerificationPurpose purpose);

    /**
     * The live codes for an address, newest first, with their rows locked until
     * the transaction ends. Needs a transaction.
     *
     * Checking a code reads its attempt count, compares the code and writes the
     * count back. Unlocked, guesses that arrive together all read the same count,
     * so more than the allowed number of them get compared. Locked, each guess
     * waits for the one before it to finish and then sees the count it left - or
     * finds the code already used or deleted.
     *
     * A list rather than findFirst so the lock is taken by a plain ordered select
     * with no row limit. There is normally only one live code per address and
     * purpose.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailVerification> findLockedByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, VerificationPurpose purpose);

    void deleteByEmailAndPurpose(String email, VerificationPurpose purpose);

    /**
     * The live code for an account, used by the email change: the code is sent
     * to an address the account does not own yet, so it cannot be found by the
     * account's current address.
     */
    Optional<EmailVerification> findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID userId, VerificationPurpose purpose);

    /** The email change's live codes, locked - see findLockedByEmail above. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailVerification> findLockedByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID userId, VerificationPurpose purpose);

    void deleteByUserIdAndPurpose(UUID userId, VerificationPurpose purpose);

    /**
     * Every code for an address, whatever it was for.
     *
     * Used when an account is deleted. This table is keyed on the address and
     * has no foreign key to app_user, so unlike every other table holding a
     * player's data, nothing cascades into it: without these two the codes
     * outlive the account they belonged to.
     */
    void deleteByEmail(String email);

    void deleteByUserId(UUID userId);
}
