package com.agridabao.api.auth;

import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.UnauthorizedException;
import com.agridabao.api.farm.FarmSaveRepository;
import com.agridabao.api.mail.MailService;
import com.agridabao.api.security.JwtService;
import com.agridabao.api.user.AccountFields;
import com.agridabao.api.user.AppUser;
import com.agridabao.api.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final String BAD_CREDENTIALS = "Invalid email/display name or password.";

    private final AppUserRepository userRepository;
    private final FarmSaveRepository farmSaveRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final MailService mailService;
    private final boolean exposeCode;

    public AuthService(AppUserRepository userRepository,
                       FarmSaveRepository farmSaveRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       VerificationService verificationService,
                       MailService mailService,
                       @Value("${app.verification.expose-code:false}") boolean exposeCode) {
        this.userRepository = userRepository;
        this.farmSaveRepository = farmSaveRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationService = verificationService;
        this.mailService = mailService;
        this.exposeCode = exposeCode;
    }

    @Transactional
    public CodeRequestResponse requestRegister(RegisterRequest request) {
        String email = AccountFields.normalizeEmail(request.email());

        if (!request.password().equals(request.confirmPassword())) {
            throw new ConflictException("Password and confirmation password do not match.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        String displayName = AccountFields.normalizeDisplayName(request.displayName());
        if (displayName != null) {
            AccountFields.validateDisplayName(displayName);
            if (userRepository.existsByDisplayNameIgnoreCase(displayName)) {
                throw new ConflictException("That display name is already taken. Please choose another.");
            }
        }

        String passwordHash = passwordEncoder.encode(request.password());
        String code = verificationService.createSignupCode(
                email, passwordHash, displayName, request.dateOfBirth());
        mailService.sendSignupCode(email, code);

        return codeResponse("We emailed a sign-up code to " + email + ".", code, email);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse verifyRegister(VerifyCodeRequest request) {
        String email = AccountFields.normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        VerificationService.PendingSignup pending =
                verificationService.consumeSignupCode(email, request.code().trim());

        if (pending.displayName() != null
                && userRepository.existsByDisplayNameIgnoreCase(pending.displayName())) {
            throw new ConflictException(
                    "That display name was taken while you were confirming. Please sign up again with another.");
        }

        Instant now = Instant.now();
        AppUser user = new AppUser(
                UUID.randomUUID(),
                email,
                pending.passwordHash(),
                pending.displayName(),
                pending.dateOfBirth(),
                now,
                now
        );

        userRepository.save(user);
        return buildResponse(user);
    }

    @Transactional
    public CodeRequestResponse requestLogin(LoginRequest request) {
        AppUser user = findByIdentifier(request.loginIdentifier())
                .orElseThrow(() -> new UnauthorizedException(BAD_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }

        String email = user.getEmail();
        String code = verificationService.createLoginCode(email);
        mailService.sendLoginCode(email, code);

        return codeResponse("We emailed a login code to " + email + ".", code, email);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse verifyLogin(VerifyCodeRequest request) {
        String email = AccountFields.normalizeEmail(request.email());
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(BAD_CREDENTIALS));

        verificationService.consumeLoginCode(email, request.code().trim());
        return buildResponse(user);
    }

    @Transactional
    public CodeRequestResponse requestPasswordReset(ForgotPasswordRequest request) {
        AppUser user = findByIdentifier(request.identifier())
                .orElseThrow(() -> new UnauthorizedException(
                        "We could not find an account with that email or display name."));

        String email = user.getEmail();
        String code = verificationService.createPasswordResetCode(email, user.getId());
        mailService.sendPasswordResetCode(email, code);

        return codeResponse(
                "We emailed a code to " + AccountFields.mask(email) + ".", code, null);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public PasswordResetTicketResponse verifyPasswordReset(ForgotPasswordVerifyRequest request) {
        AppUser user = findByIdentifier(request.identifier())
                .orElseThrow(() -> new UnauthorizedException(
                        "We could not find an account with that email or display name."));

        verificationService.consumePasswordResetCode(user.getEmail(), request.code().trim());

        return new PasswordResetTicketResponse(
                jwtService.issueResetTicket(user),
                verificationService.getTtlSeconds());
    }

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        UUID userId = jwtService.readResetTicket(request.resetToken());

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ConflictException("New password and confirmation password do not match.");
        }

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        "That account no longer exists. Please sign up again."));

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return buildResponse(user);
    }

    private Optional<AppUser> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String trimmed = identifier.trim();

        Optional<AppUser> byEmail = userRepository.findByEmail(AccountFields.normalizeEmail(trimmed));
        if (byEmail.isPresent()) {
            return byEmail;
        }

        return userRepository.findFirstByDisplayNameIgnoreCase(trimmed);
    }

    private CodeRequestResponse codeResponse(String message, String code, String email) {
        return new CodeRequestResponse(
                message,
                verificationService.getTtlSeconds(),
                exposeCode ? code : null,
                email
        );
    }

    private AuthResponse buildResponse(AppUser user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(
                token.value(),
                token.expiresAt(),
                farmSaveRepository.existsById(user.getId()),
                UserResponse.from(user)
        );
    }
}
