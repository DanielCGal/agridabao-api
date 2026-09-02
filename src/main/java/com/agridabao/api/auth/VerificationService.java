package com.agridabao.api.auth;

import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerificationService {
    private final EmailVerificationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    private final int codeLength;
    private final long ttlMinutes;
    private final int maxAttempts;
    private final long resendCooldownSeconds;

    public VerificationService(EmailVerificationRepository repository,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.verification.code-length:9}") int codeLength,
                               @Value("${app.verification.ttl-minutes:10}") long ttlMinutes,
                               @Value("${app.verification.max-attempts:5}") int maxAttempts,
                               @Value("${app.verification.resend-cooldown-seconds:30}") long resendCooldownSeconds) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.codeLength = codeLength;
        this.ttlMinutes = ttlMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public long getTtlSeconds() {
        return ttlMinutes * 60L;
    }

    @Transactional
    public String createSignupCode(String email, String passwordHash, String displayName, LocalDate dateOfBirth) {
        return createCode(email, VerificationPurpose.SIGNUP, null, passwordHash, displayName, dateOfBirth);
    }

    @Transactional
    public String createLoginCode(String email) {
        return createCode(email, VerificationPurpose.LOGIN, null, null, null, null);
    }

    @Transactional
    public String createPasswordResetCode(String email, UUID userId) {
        return createCode(email, VerificationPurpose.PASSWORD_RESET, userId, null, null, null);
    }

    /** {@code newEmail} is where the code is sent - the address being moved to. */
    @Transactional
    public String createEmailChangeCode(String newEmail, UUID userId) {
        return createCode(newEmail, VerificationPurpose.EMAIL_CHANGE, userId, null, null, null);
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class})
    public PendingSignup consumeSignupCode(String email, String code) {
        EmailVerification record = validateAndConsume(email, null, code, VerificationPurpose.SIGNUP);
        return new PendingSignup(record.getPasswordHash(), record.getDisplayName(), record.getDateOfBirth());
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class})
    public void consumeLoginCode(String email, String code) {
        validateAndConsume(email, null, code, VerificationPurpose.LOGIN);
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class})
    public void consumePasswordResetCode(String email, String code) {
        validateAndConsume(email, null, code, VerificationPurpose.PASSWORD_RESET);
    }

    /** Returns the address the account is moving to, once its code checks out. */
    @Transactional(noRollbackFor = {UnauthorizedException.class})
    public String consumeEmailChangeCode(UUID userId, String code) {
        return validateAndConsume(null, userId, code, VerificationPurpose.EMAIL_CHANGE).getEmail();
    }

    private String createCode(String email, VerificationPurpose purpose, UUID userId,
                              String passwordHash, String displayName, LocalDate dateOfBirth) {
        Instant now = Instant.now();
        enforceResendCooldown(email, userId, purpose, now);

        // Only one live code per subject+purpose: replace any earlier ones.
        if (isUserScoped(purpose)) {
            repository.deleteByUserIdAndPurpose(userId, purpose);
        } else {
            repository.deleteByEmailAndPurpose(email, purpose);
        }

        String code = generateCode();
        EmailVerification record = new EmailVerification(
                UUID.randomUUID(),
                email,
                purpose,
                userId,
                passwordEncoder.encode(code),
                passwordHash,
                displayName,
                dateOfBirth,
                now.plus(Duration.ofMinutes(ttlMinutes)),
                now
        );
        repository.save(record);
        return code;
    }

    private EmailVerification validateAndConsume(String email, UUID userId,
                                                 String code, VerificationPurpose purpose) {
        Instant now = Instant.now();
        EmailVerification record = findLive(email, userId, purpose)
                .orElseThrow(() -> new UnauthorizedException(
                        "Your code has expired or was not found. Please request a new one."));

        if (record.isExpired(now)) {
            repository.delete(record);
            throw new UnauthorizedException("Your code has expired. Please request a new one.");
        }

        if (passwordEncoder.matches(code, record.getCodeHash())) {
            record.markConsumed(now);
            return record;
        }

        record.registerFailedAttempt();
        if (record.getAttempts() >= maxAttempts) {
            repository.delete(record);
            throw new UnauthorizedException("Too many incorrect attempts. Please request a new code.");
        }
        throw new UnauthorizedException("Incorrect code. Please try again.");
    }

    private void enforceResendCooldown(String email, UUID userId,
                                       VerificationPurpose purpose, Instant now) {
        findLive(email, userId, purpose).ifPresent(existing -> {
            Instant nextAllowed = existing.getCreatedAt().plus(Duration.ofSeconds(resendCooldownSeconds));
            if (now.isBefore(nextAllowed)) {
                long wait = Duration.between(now, nextAllowed).toSeconds() + 1;
                throw new BadRequestException(
                        "Please wait " + wait + " seconds before requesting another code.");
            }
        });
    }

    /**
     * An email change is looked up by account, every other purpose by address.
     *
     * The address is the wrong key for a change: the code is sent to one the
     * account does not own yet, and a player who mistypes it and tries again
     * would otherwise leave the first attempt live and be able to confirm the
     * typo'd address afterwards.
     */
    private Optional<EmailVerification> findLive(String email, UUID userId, VerificationPurpose purpose) {
        return isUserScoped(purpose)
                ? repository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(userId, purpose)
                : repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose);
    }

    private static boolean isUserScoped(VerificationPurpose purpose) {
        return purpose == VerificationPurpose.EMAIL_CHANGE;
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    public record PendingSignup(String passwordHash, String displayName, LocalDate dateOfBirth) {
    }
}
