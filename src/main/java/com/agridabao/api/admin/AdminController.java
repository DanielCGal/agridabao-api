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

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAccountService service;

    public AdminController(AdminAccountService service) {
        this.service = service;
    }

    @PostMapping("/capability")
    public AdminCapabilityResponse capability(@AuthenticationPrincipal Jwt jwt) {
        return service.capability(userId(jwt));
    }

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

    @PostMapping("/commands")
    public AdminCommandQueuedResponse sendCommand(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody AdminCommandRequest request) {
        return service.queueCommand(userId(jwt), request);
    }

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

    private final Set<String> adminEmails;

    private static final int MAX_MONEY_GRANT = 1_000_000;
    private static final int MAX_WEATHER_DAYS = 14;

    private static final int MAX_PASS_DAYS = 30;
    private static final int MAX_PASS_HOURS = 48;

    private final AppUserRepository userRepository;
    private final FarmSaveRepository farmSaveRepository;
    private final EmailVerificationRepository verificationRepository;
    private final AdminCommandRepository repository;
    private final PresenceAccessService presence;

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

    @Transactional
    AdminDeleteResponse delete(UUID callerId, String email) {
        requireAdmin(callerId);

        String normalized = AccountFields.normalizeEmail(email);
        AppUser target = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new NotFoundException(
                        "No account exists with the email " + normalized + "."));

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
