package com.agridabao.api.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
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
    public void heartbeat(@AuthenticationPrincipal Jwt jwt) {
        service.heartbeat(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/offline")
    @ResponseStatus(NO_CONTENT)
    public void offline(@AuthenticationPrincipal Jwt jwt) {
        service.markOffline(UUID.fromString(jwt.getSubject()));
    }
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

    protected PlayerPresence() {
    }

    PlayerPresence(UUID userId, Instant now) {
        this.userId = userId;
        this.connectedAt = now;
        this.lastSeenAt = now;
    }

    UUID getUserId() { return userId; }
    Instant getLastSeenAt() { return lastSeenAt; }
    void touch(Instant now) { this.lastSeenAt = now; }
}

interface PlayerPresenceRepository extends JpaRepository<PlayerPresence, UUID> {
}

@Service
class PresenceService {
    static final Duration ONLINE_WINDOW = Duration.ofSeconds(45);
    private final PlayerPresenceRepository repository;

    PresenceService(PlayerPresenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void heartbeat(UUID userId) {
        Instant now = Instant.now();
        PlayerPresence presence = repository.findById(userId)
                .orElseGet(() -> new PlayerPresence(userId, now));
        presence.touch(now);
        repository.save(presence);
    }

    @Transactional
    public void markOffline(UUID userId) {
        repository.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public boolean isOnline(UUID userId) {
        Optional<PlayerPresence> value = repository.findById(userId);
        return value.isPresent() &&
                value.get().getLastSeenAt().isAfter(Instant.now().minus(ONLINE_WINDOW));
    }
}
