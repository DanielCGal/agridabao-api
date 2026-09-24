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

    private static final String IN_USE_MESSAGE =
            "The account you are logged in are currently using in a different device. " +
            "If you didn't share your login information, contact at " +
            "greenscapeacd@gmail.com for Account Help";

    private final PlayerPresenceRepository repository;

    PresenceService(PlayerPresenceRepository repository) {
        this.repository = repository;
    }

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

        if (holder == null || !presence.getLastSeenAt().isAfter(now.minus(ONLINE_WINDOW))) {
            return false;
        }

        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }

        return !sameDevice(holder, deviceId);
    }

    private static boolean sameDevice(String first, String second) {
        return first != null && first.equals(second);
    }
}
