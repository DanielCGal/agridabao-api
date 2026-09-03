package com.agridabao.api.admin;

import com.agridabao.api.auth.EmailVerificationRepository;
import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.ForbiddenException;
import com.agridabao.api.error.NotFoundException;
import com.agridabao.api.farm.FarmSaveRepository;
import com.agridabao.api.user.AccountFields;
import com.agridabao.api.user.AppUser;
import com.agridabao.api.user.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Operations that act on somebody else's account, for the developer tools.
 *
 * There is no way for a player to delete their own account in this build, so
 * an address used once was used forever - which made testing sign-up painful
 * and left no way to clear a tester's data afterwards. This is that missing
 * lever, and it is deliberately kept off the player-facing surface.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAccountService service;

    public AdminController(AdminAccountService service) {
        this.service = service;
    }

    /** Answers whether an address is in use, so the tool can confirm before deleting. */
    @PostMapping("/accounts/lookup")
    public AdminAccountLookupResponse lookup(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody AdminEmailRequest request) {
        return service.lookup(userId(jwt), request.email());
    }

    @PostMapping("/accounts/delete")
    public AdminDeleteResponse delete(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody AdminEmailRequest request) {
        return service.delete(userId(jwt), request.email());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

record AdminEmailRequest(@NotBlank @Email @Size(max = 320) String email) {
}

record AdminAccountLookupResponse(boolean exists,
                                  String email,
                                  String displayName,
                                  boolean hasFarm,
                                  Instant createdAt) {
}

record AdminDeleteResponse(String message, String email) {
}

@Service
class AdminAccountService {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

    /**
     * The addresses allowed to use this controller, from APP_ADMIN_EMAILS.
     *
     * Empty is not "everyone" but "nobody": with no list configured every call
     * here is refused. That is the safe direction to fail in - forgetting to set
     * the variable costs a developer one confusing error, while the opposite
     * default would hand every player in the game a button that deletes anyone.
     */
    private final Set<String> adminEmails;

    private final AppUserRepository userRepository;
    private final FarmSaveRepository farmSaveRepository;
    private final EmailVerificationRepository verificationRepository;

    AdminAccountService(AppUserRepository userRepository,
                        FarmSaveRepository farmSaveRepository,
                        EmailVerificationRepository verificationRepository,
                        @Value("${app.admin.emails:}") String configured) {
        this.userRepository = userRepository;
        this.farmSaveRepository = farmSaveRepository;
        this.verificationRepository = verificationRepository;
        this.adminEmails = parse(configured);

        log.info("Admin account tools: {}",
                adminEmails.isEmpty()
                        ? "DISABLED (APP_ADMIN_EMAILS is not set)"
                        : adminEmails.size() + " address(es) allowed");
    }

    @Transactional(readOnly = true)
    AdminAccountLookupResponse lookup(UUID callerId, String email) {
        requireAdmin(callerId);

        String normalized = AccountFields.normalizeEmail(email);
        return userRepository.findByEmail(normalized)
                .map(user -> new AdminAccountLookupResponse(
                        true,
                        user.getEmail(),
                        user.getDisplayName(),
                        farmSaveRepository.existsById(user.getId()),
                        user.getCreatedAt()))
                .orElseGet(() -> new AdminAccountLookupResponse(
                        false, normalized, null, false, null));
    }

    /**
     * Removes an account and everything belonging to it, freeing the address
     * for a fresh sign-up.
     *
     * Almost all of it happens in the database rather than here: farm saves,
     * settings, presence, friendships, friend requests, chat, trades and
     * listings are all declared ON DELETE CASCADE against app_user, so removing
     * the one row takes the rest with it. A sold listing keeps its record with
     * buyer_id set to null, which is deliberate - the other player's sale
     * history should not disappear because their buyer left.
     *
     * The verification codes are the exception. That table is keyed on the
     * address rather than the account and carries no foreign key, so nothing
     * cascades into it and its rows have to be cleared by hand or they outlive
     * the account they belonged to.
     */
    @Transactional
    AdminDeleteResponse delete(UUID callerId, String email) {
        requireAdmin(callerId);

        String normalized = AccountFields.normalizeEmail(email);
        AppUser target = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new NotFoundException(
                        "No account exists with the email " + normalized + "."));

        // Deleting the account you are signed in with would revoke the token
        // mid-request and leave the tool unable to explain what happened.
        if (target.getId().equals(callerId)) {
            throw new ConflictException(
                    "You cannot delete the account you are currently signed in with.");
        }

        verificationRepository.deleteByEmail(normalized);
        verificationRepository.deleteByUserId(target.getId());
        userRepository.delete(target);

        log.warn("Admin {} deleted the account {} and all of its data.", callerId, normalized);

        return new AdminDeleteResponse(
                "Deleted " + normalized + " and all of its data. The email is free to use again.",
                normalized);
    }

    private void requireAdmin(UUID callerId) {
        if (adminEmails.isEmpty()) {
            throw new ForbiddenException(
                    "Admin tools are switched off on this server.");
        }

        String callerEmail = userRepository.findById(callerId)
                .map(AppUser::getEmail)
                .orElse(null);

        // Checked against the account rather than against the token's email
        // claim: an address that has since been changed should not keep the
        // powers it had, and the claim is only as fresh as the token holding it.
        if (callerEmail == null || !adminEmails.contains(callerEmail)) {
            throw new ForbiddenException(
                    "This account is not allowed to use the admin tools.");
        }
    }

    private static Set<String> parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }

        Set<String> parsed = new LinkedHashSet<>();
        for (String entry : configured.split(",")) {
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return Set.copyOf(parsed);
    }
}
