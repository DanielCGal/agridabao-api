package com.agridabao.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {
    Optional<EmailVerification> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, VerificationPurpose purpose);

    void deleteByEmailAndPurpose(String email, VerificationPurpose purpose);

    /**
     * The live code for an account, used by the email change: the code is sent
     * to an address the account does not own yet, so it cannot be found by the
     * account's current address.
     */
    Optional<EmailVerification> findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID userId, VerificationPurpose purpose);

    void deleteByUserIdAndPurpose(UUID userId, VerificationPurpose purpose);
}
