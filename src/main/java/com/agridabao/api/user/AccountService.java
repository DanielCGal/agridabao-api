package com.agridabao.api.user;

import com.agridabao.api.auth.AuthResponse;
import com.agridabao.api.auth.CodeRequestResponse;
import com.agridabao.api.auth.MessageResponse;
import com.agridabao.api.auth.UserResponse;
import com.agridabao.api.auth.VerificationService;
import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.NotFoundException;
import com.agridabao.api.error.UnauthorizedException;
import com.agridabao.api.farm.FarmSaveRepository;
import com.agridabao.api.mail.MailService;
import com.agridabao.api.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The three things a signed-in player can change about their account.
 *
 * Kept apart from AuthService because everything here already knows who is
 * asking: the caller arrives with a valid token, so none of it has to guard
 * against account enumeration the way sign-in and recovery do.
 */
@Service
public class AccountService {
    private final AppUserRepository userRepository;
    private final FarmSaveRepository farmSaveRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final MailService mailService;
    private final boolean exposeCode;

    public AccountService(AppUserRepository userRepository,
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

    public UserResponse me(UUID userId) {
        return UserResponse.from(require(userId));
    }

    /**
     * Step 1 of changing an address: email a code to the NEW one.
     *
     * Sending to the new address rather than the current one is the whole point
     * - it proves the player can actually receive mail there, so a typo cannot
     * strand an account at an address nobody reads.
     */
    @Transactional
    public CodeRequestResponse requestEmailChange(UUID userId, ChangeEmailRequest request) {
        AppUser user = require(userId);
        String newEmail = AccountFields.normalizeEmail(request.newEmail());

        if (newEmail.equals(user.getEmail())) {
            throw new ConflictException("That is already the email on your account.");
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new ConflictException("An account with this email already exists.");
        }

        String code = verificationService.createEmailChangeCode(newEmail, userId);
        mailService.sendEmailChangeCode(newEmail, code);

        return new CodeRequestResponse(
                "We emailed a code to " + newEmail + ".",
                verificationService.getTtlSeconds(),
                exposeCode ? code : null,
                newEmail);
    }

    /**
     * Step 2: move the account across.
     *
     * A fresh token comes back with it. The old one keeps working - it names the
     * account by id, not by address - but it still carries the previous address
     * in its claims, and handing back a matching pair keeps the copy saved on the
     * phone from disagreeing with the account.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse confirmEmailChange(UUID userId, ConfirmEmailChangeRequest request) {
        AppUser user = require(userId);
        String newEmail = verificationService.consumeEmailChangeCode(userId, request.code().trim());

        // Rechecked after the code is accepted: the code is good for ten minutes
        // and somebody else may have registered that address in the meantime.
        if (userRepository.existsByEmail(newEmail)) {
            throw new ConflictException(
                    "Someone claimed that email while you were confirming. Please try another.");
        }

        user.changeEmail(newEmail);
        userRepository.save(user);

        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(
                token.value(),
                token.expiresAt(),
                farmSaveRepository.existsById(user.getId()),
                UserResponse.from(user));
    }

    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        AppUser user = require(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Your current password is incorrect.");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ConflictException("New password and confirmation password do not match.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ConflictException("Your new password is the same as your current one.");
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new MessageResponse("Your password has been changed.");
    }

    @Transactional
    public UserResponse changeDisplayName(UUID userId, ChangeDisplayNameRequest request) {
        AppUser user = require(userId);

        String displayName = AccountFields.normalizeDisplayName(request.displayName());
        if (displayName == null) {
            throw new ConflictException("Enter a display name.");
        }

        AccountFields.validateDisplayName(displayName);

        if (displayName.equals(user.getDisplayName())) {
            throw new ConflictException("That is already your display name.");
        }

        // Excluding this account so a player can restyle their own name -
        // "juan" to "Juan" - without colliding with themselves.
        if (userRepository.existsByDisplayNameIgnoreCaseAndIdNot(displayName, userId)) {
            throw new ConflictException("That display name is already taken. Please choose another.");
        }

        user.changeDisplayName(displayName);
        userRepository.save(user);

        return UserResponse.from(user);
    }

    private AppUser require(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }
}
