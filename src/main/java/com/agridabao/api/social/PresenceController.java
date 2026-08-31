package com.agridabao.api.social;

import com.agridabao.api.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {
    private final PresenceService service;

    public PresenceController(PresenceService service) {
        this.service = service;
    }

    /**
     * Claims or refreshes this account's play session for the calling device.
     *
     * Answers 409 when a different device is already playing on this account,
     * which is what stops one account being played on two phones at once. The
     * body is optional so a client built before device tracking keeps working.
     */
    @PostMapping("/heartbeat")
    @ResponseStatus(NO_CONTENT)
    public void heartbeat(@AuthenticationPrincipal Jwt jwt,
                          @Valid @RequestBody(required = false) PresenceHeartbeatRequest request) {
        service.heartbeat(
                UUID.fromString(jwt.getSubject()),
                request == null ? null : request.deviceId());
    }

    @PostMapping("/offline")
    @ResponseStatus(NO_CONTENT)
    public void offline(@AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody(required = false) PresenceHeartbeatRequest request) {
        service.markOffline(
                UUID.fromString(jwt.getSubject()),
                request == null ? null : request.deviceId());
    }
}

record PresenceHeartbeatRequest(@Size(max = 128) String deviceId) {
}

@Entity
@Table(name = "player_presence")
class PlayerPresence {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** The device holding this session; null on rows written before device tracking. */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    protected PlayerPresence() {
    }

    PlayerPresence(UUID userId, String deviceId, Instant now) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.connectedAt = now;
        this.lastSeenAt = now;
    }

    UUID getUserId() { return userId; }
    Instant getLastSeenAt() { return lastSeenAt; }
    String getDeviceId() { return deviceId; }

    void touch(Instant now) { this.lastSeenAt = now; }

    /** Hands the session to a new device and restarts its clock. */
    void claim(String newDeviceId, Instant now) {
        this.deviceId = newDeviceId;
        this.connectedAt = now;
        this.lastSeenAt = now;
    }
}

interface PlayerPresenceRepository extends JpaRepository<PlayerPresence, UUID> {
}

@Service
class PresenceService {
    static final Duration ONLINE_WINDOW = Duration.ofSeconds(45);

    /**
     * Shown to the second device. It names the support address deliberately: to
     * the player this either means they left the game running somewhere else, or
     * somebody else has their password, and the second case needs a way out.
     */
    private static final String IN_USE_MESSAGE =
            "The account you are logged in are currently using in a different device. " +
            "If you didn't share your login information, contact at " +
            "greenscapeacd@gmail.com for Account Help";

    private final PlayerPresenceRepository repository;

    PresenceService(PlayerPresenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Refreshes the session for {@code deviceId}, taking it over when nobody
     * else holds it.
     *
     * A device may claim when the row is missing, when the previous holder has
     * gone quiet for longer than {@link #ONLINE_WINDOW}, when it already holds
     * the row, or when the row predates device tracking and names no holder.
     * Anything else means a second device is genuinely playing right now.
     */
    @Transactional
    public void heartbeat(UUID userId, String deviceId) {
        Instant now = Instant.now();
        PlayerPresence presence = repository.findById(userId).orElse(null);

        if (presence == null) {
            repository.save(new PlayerPresence(userId, deviceId, now));
            return;
        }

        if (isHeldByAnotherDevice(presence, deviceId, now)) {
            throw new ConflictException(IN_USE_MESSAGE);
        }

        if (sameDevice(presence.getDeviceId(), deviceId)) {
            presence.touch(now);
        } else {
            presence.claim(deviceId, now);
        }

        repository.save(presence);
    }

    /**
     * Releases the session. A device that never held it cannot end someone
     * else's, so a blocked second phone quitting does not free the first.
     */
    @Transactional
    public void markOffline(UUID userId, String deviceId) {
        PlayerPresence presence = repository.findById(userId).orElse(null);
        if (presence == null) {
            return;
        }

        if (presence.getDeviceId() == null ||
                deviceId == null ||
                sameDevice(presence.getDeviceId(), deviceId)) {
            repository.delete(presence);
        }
    }

    @Transactional(readOnly = true)
    public boolean isOnline(UUID userId) {
        Optional<PlayerPresence> value = repository.findById(userId);
        return value.isPresent() &&
                value.get().getLastSeenAt().isAfter(Instant.now().minus(ONLINE_WINDOW));
    }

    private static boolean isHeldByAnotherDevice(PlayerPresence presence, String deviceId, Instant now) {
        String holder = presence.getDeviceId();

        // Nobody named, or the holder has gone quiet: free to take.
        if (holder == null || !presence.getLastSeenAt().isAfter(now.minus(ONLINE_WINDOW))) {
            return false;
        }

        // A caller that cannot identify itself is never treated as an intruder,
        // so an older build keeps behaving exactly as it did.
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }

        return !sameDevice(holder, deviceId);
    }

    private static boolean sameDevice(String first, String second) {
        return first != null && first.equals(second);
    }
}
