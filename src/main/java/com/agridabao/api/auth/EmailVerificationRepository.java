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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailVerification> findLockedByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, VerificationPurpose purpose);

    void deleteByEmailAndPurpose(String email, VerificationPurpose purpose);

    Optional<EmailVerification> findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID userId, VerificationPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailVerification> findLockedByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID userId, VerificationPurpose purpose);

    void deleteByUserIdAndPurpose(UUID userId, VerificationPurpose purpose);

    void deleteByEmail(String email);

    void deleteByUserId(UUID userId);
}
