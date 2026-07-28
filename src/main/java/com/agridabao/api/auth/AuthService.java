package com.agridabao.api.auth;

import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.UnauthorizedException;
import com.agridabao.api.farm.FarmSaveRepository;
import com.agridabao.api.mail.MailService;
import com.agridabao.api.security.JwtService;
import com.agridabao.api.user.AppUser;
import com.agridabao.api.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
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

    /** Step 1 of sign-up: validate the form and email a code. No account is created yet. */
    @Transactional
    public CodeRequestResponse requestRegister(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (!request.password().equals(request.confirmPassword())) {
            throw new ConflictException("Password and confirmation password do not match.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        String code = verificationService.createSignupCode(
                email, passwordHash, normalizeDisplayName(request.displayName()), request.dateOfBirth());
        mailService.sendSignupCode(email, code);

        return codeResponse("We emailed a sign-up code to " + email + ".", code);
    }

    /** Step 2 of sign-up: confirm the code, then create the account and issue a token. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse verifyRegister(VerifyCodeRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        VerificationService.PendingSignup pending =
                verificationService.consumeSignupCode(email, request.code().trim());

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

    /** Step 1 of login: validate credentials and email a 2FA code. No token is issued yet. */
    @Transactional
    public CodeRequestResponse requestLogin(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }

        String code = verificationService.createLoginCode(email);
        mailService.sendLoginCode(email, code);

        return codeResponse("We emailed a login code to " + email + ".", code);
    }

    /** Step 2 of login: confirm the 2FA code, then issue a token. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse verifyLogin(VerifyCodeRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        verificationService.consumeLoginCode(email, request.code().trim());
        return buildResponse(user);
    }

    private CodeRequestResponse codeResponse(String message, String code) {
        return new CodeRequestResponse(
                message,
                verificationService.getTtlSeconds(),
                exposeCode ? code : null
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
