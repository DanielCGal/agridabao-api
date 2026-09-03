package com.agridabao.api.admin;

import com.agridabao.api.auth.EmailVerificationRepository;
import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.ForbiddenException;
import com.agridabao.api.error.NotFoundException;
import com.agridabao.api.farm.FarmSaveRepository;
import com.agridabao.api.social.PresenceAccessService;
import com.agridabao.api.user.AccountFields;
import com.agridabao.api.user.AppUser;
import com.agridabao.api.user.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.List;
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

    /**
     * What the calling account is allowed to do with the developer tools.
     *
     * Deliberately not admin-gated: every signed-in player asks this, and a
     * non-admin gets an honest "no" rather than a refusal. It reveals only
     * whether the caller themselves is on the list, never who else is.
     */
    @PostMapping("/capability")
    public AdminCapabilityResponse capability(@AuthenticationPrincipal Jwt jwt) {
        return service.capability(userId(jwt));
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

    /** Queues an event or a payment for one player. Admin only. */
    @PostMapping("/commands")
    public AdminCommandQueuedResponse sendCommand(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody AdminCommandRequest request) {
        return service.queueCommand(userId(jwt), request);
    }

    /**
     * Anything queued for the caller, marked delivered as it is handed over.
     *
     * Not admin-gated, because every player's game polls it for itself - the
     * query is scoped to the caller's own id, so there is nothing here to reach
     * anyone else's commands with.
     */
    @PostMapping("/commands/pending")
    public List<AdminCommandView> pendingCommands(@AuthenticationPrincipal Jwt jwt) {
        return service.collectPending(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

record AdminEmailRequest(@NotBlank @Email @Size(max = 320) String email) {
}

/**
 * @param admin              whether this account is on the admin list
 * @param mobileToolsEnabled whether the server currently allows the tools on a phone
 */
record AdminCapabilityResponse(boolean admin, boolean mobileToolsEnabled) {
}

record AdminAccountLookupResponse(boolean exists,
                                  String email,
                                  String displayName,
                                  boolean hasFarm,
                                  Instant createdAt) {
}

record AdminDeleteResponse(String message, String email) {
}

/**
 * @param commandType  GRANT_MONEY, FORCE_WEATHER or FORCE_PEST_DISEASE
 * @param payload      the weather or pest name, ignored for a money grant
 * @param amount       pesos for a grant
 * @param durationDays how long a weather event should run
 */
record AdminCommandRequest(@NotBlank @Email @Size(max = 320) String email,
                           @NotNull AdminCommandType commandType,
                           @Size(max = 64) String payload,
                           Integer amount,
                           Integer durationDays) {
}

record AdminCommandQueuedResponse(String message, String email) {
}

record AdminCommandView(UUID id,
                        AdminCommandType commandType,
                        String payload,
                        Integer amount,
                        Integer durationDays) {
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

    /** Ceilings that turn a slipped digit into a refusal rather than a ruined save. */
    private static final int MAX_MONEY_GRANT = 1_000_000;
    private static final int MAX_WEATHER_DAYS = 14;

    /**
     * A game day is 900 real seconds, so thirty of them is well past any single
     * test session and far enough to see a crop through its stages. Beyond that
     * a slipped digit costs the tester their farm rather than their afternoon.
     */
    private static final int MAX_PASS_DAYS = 30;
    private static final int MAX_PASS_HOURS = 48;

    private final AppUserRepository userRepository;
    private final FarmSaveRepository farmSaveRepository;
    private final EmailVerificationRepository verificationRepository;
    private final AdminCommandRepository repository;
    private final PresenceAccessService presence;

    /** Whether an admin may open the tools on a phone. See application.yml. */
    private final boolean mobileToolsEnabled;

    AdminAccountService(AppUserRepository userRepository,
                        FarmSaveRepository farmSaveRepository,
                        EmailVerificationRepository verificationRepository,
                        AdminCommandRepository repository,
                        PresenceAccessService presence,
                        @Value("${app.admin.emails:}") String configured,
                        @Value("${app.admin.mobile-tools:false}") boolean mobileToolsEnabled) {
        this.userRepository = userRepository;
        this.farmSaveRepository = farmSaveRepository;
        this.verificationRepository = verificationRepository;
        this.repository = repository;
        this.presence = presence;
        this.mobileToolsEnabled = mobileToolsEnabled;
        this.adminEmails = parse(configured);

        log.info("Admin account tools: {}",
                adminEmails.isEmpty()
                        ? "DISABLED (APP_ADMIN_EMAILS is not set)"
                        : adminEmails.size() + " address(es) allowed");
        log.info("Admin tools on mobile: {}", mobileToolsEnabled ? "ALLOWED" : "blocked");
    }

    @Transactional(readOnly = true)
    AdminCapabilityResponse capability(UUID callerId) {
        boolean admin = !adminEmails.isEmpty()
                && userRepository.findById(callerId)
                .map(AppUser::getEmail)
                .filter(adminEmails::contains)
                .isPresent();

        // The phone flag is reported only to an admin. Telling a stranger whether
        // the mobile tools are switched on says something about the deployment
        // that they have no reason to know.
        return new AdminCapabilityResponse(admin, admin && mobileToolsEnabled);
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

    /**
     * Queues one command for a player who is currently in the game.
     *
     * The online check is the point of the whole feature, not a nicety. These
     * are meant to land while a tester is watching - a typhoon queued for
     * somebody who has closed the game would arrive whenever they next opened
     * it, possibly days later and with nobody expecting it.
     */
    @Transactional
    AdminCommandQueuedResponse queueCommand(UUID callerId, AdminCommandRequest request) {
        requireAdmin(callerId);

        String normalized = AccountFields.normalizeEmail(request.email());
        AppUser target = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new NotFoundException(
                        "No account exists with the email " + normalized + "."));

        if (!presence.isOnline(target.getId())) {
            throw new ConflictException(
                    normalized + " is not online right now. They must be in the game to receive this.");
        }

        Integer amount = request.amount();
        Integer duration = request.durationDays();
        String payload = request.payload() == null ? null : request.payload().trim();

        switch (request.commandType()) {
            case GRANT_MONEY -> {
                if (amount == null || amount <= 0) {
                    throw new BadRequestException("Enter an amount of money greater than zero.");
                }
                if (amount > MAX_MONEY_GRANT) {
                    throw new BadRequestException(
                            "The most that can be sent at once is P" + MAX_MONEY_GRANT + ".");
                }
                payload = null;
                duration = null;
            }
            case FORCE_WEATHER -> {
                requirePayload(payload, "Choose a weather event.");
                // Clamped rather than refused: a typo of 300 days would leave a
                // tester stuck in a typhoon for the rest of the session.
                duration = duration == null ? 1 : Math.clamp(duration, 1, MAX_WEATHER_DAYS);
                amount = null;
            }
            case FORCE_PEST_DISEASE -> {
                requirePayload(payload, "Choose a pest or disease.");
                amount = null;
                duration = null;
            }
            case PASS_DAYS -> {
                amount = requireCount(amount, MAX_PASS_DAYS, "days");
                payload = null;
                duration = null;
            }
            case PASS_HOURS -> {
                amount = requireCount(amount, MAX_PASS_HOURS, "hours");
                payload = null;
                duration = null;
            }
        }

        repository.save(new AdminCommand(
                UUID.randomUUID(), target.getId(), callerId,
                request.commandType(), payload, amount, duration, Instant.now()));

        log.info("Admin {} queued {} for {}.", callerId, request.commandType(), normalized);

        return new AdminCommandQueuedResponse(
                "Sent to " + normalized + ". It arrives within about 15 seconds.", normalized);
    }

    /**
     * Hands the caller their own queued commands and stamps them delivered.
     *
     * Marked as delivered when handed over rather than when applied. The
     * alternative would need the game to acknowledge each one, and a dropped
     * acknowledgement would replay a typhoon on the next poll - a worse failure
     * than one command occasionally being lost to a dropped response.
     */
    @Transactional
    List<AdminCommandView> collectPending(UUID callerId) {
        List<AdminCommand> pending =
                repository.findByTargetUserIdAndDeliveredAtIsNullOrderByCreatedAtAsc(callerId);

        Instant now = Instant.now();
        for (AdminCommand command : pending) {
            command.markDelivered(now);
        }
        repository.saveAll(pending);

        return pending.stream()
                .map(command -> new AdminCommandView(
                        command.getId(), command.getCommandType(), command.getPayload(),
                        command.getAmount(), command.getDurationDays()))
                .toList();
    }

    private static void requirePayload(String payload, String message) {
        if (payload == null || payload.isBlank()) {
            throw new BadRequestException(message);
        }
    }

    /**
     * A whole positive count within its ceiling.
     *
     * Refused rather than clamped, unlike a weather duration. Skipping time is
     * not undoable on the receiving farm: crops age, weather rolls and daily
     * objectives regenerate, so quietly turning a mistyped 200 into 30 would
     * still hand the player a farm they did not expect.
     */
    private static Integer requireCount(Integer amount, int ceiling, String unit) {
        if (amount == null || amount <= 0) {
            throw new BadRequestException("Enter how many " + unit + " to pass.");
        }

        if (amount > ceiling) {
            throw new BadRequestException(
                    "At most " + ceiling + " " + unit + " can be passed at once.");
        }

        return amount;
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
