package com.agridabao.api.social;

import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.ForbiddenException;
import com.agridabao.api.error.NotFoundException;
import com.agridabao.api.farm.EconomyJsonService;
import com.agridabao.api.farm.FarmSave;
import com.agridabao.api.farm.FarmSaveRepository;
import com.agridabao.api.user.AppUser;
import com.agridabao.api.user.AppUserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api")
public class SocialController {
    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    @GetMapping("/players/search")
    public List<PlayerCardResponse> search(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam String displayName) {
        return service.search(userId(jwt), displayName);
    }

    @GetMapping("/players/{playerId}")
    public PlayerProfileResponse profile(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID playerId) {
        return service.profile(userId(jwt), playerId);
    }

    @GetMapping("/friends")
    public List<PlayerCardResponse> friends(@AuthenticationPrincipal Jwt jwt) {
        return service.friends(userId(jwt));
    }

    @GetMapping("/friends/requests")
    public List<FriendRequestResponse> incomingRequests(@AuthenticationPrincipal Jwt jwt) {
        return service.incomingRequests(userId(jwt));
    }

    @PostMapping("/friends/requests/{targetId}")
    @ResponseStatus(CREATED)
    public FriendRequestResponse sendRequest(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID targetId) {
        return service.sendRequest(userId(jwt), targetId);
    }

    @PostMapping("/friends/requests/{requestId}/accept")
    @ResponseStatus(NO_CONTENT)
    public void accept(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID requestId) {
        service.respond(userId(jwt), requestId, true);
    }

    @PostMapping("/friends/requests/{requestId}/decline")
    @ResponseStatus(NO_CONTENT)
    public void decline(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID requestId) {
        service.respond(userId(jwt), requestId, false);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

enum FriendRequestStatus { PENDING, ACCEPTED, DECLINED }
enum RelationshipStatus { SELF, NONE, OUTGOING_PENDING, INCOMING_PENDING, FRIENDS }

record PlayerCardResponse(
        UUID id,
        String displayName,
        String districtName,
        boolean online,
        RelationshipStatus relationship
) { }

record PlayerProfileResponse(
        UUID id,
        String displayName,
        String districtName,
        int money,
        boolean online,
        RelationshipStatus relationship
) { }

record FriendRequestResponse(
        UUID requestId,
        UUID playerId,
        String displayName,
        String districtName,
        boolean online,
        Instant createdAt
) { }

@Entity
@Table(name = "friend_request")
class FriendRequest {
    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected FriendRequest() { }

    FriendRequest(UUID id, UUID requesterId, UUID receiverId, Instant now) {
        this.id = id;
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.status = FriendRequestStatus.PENDING;
        this.createdAt = now;
    }

    UUID getId() { return id; }
    UUID getRequesterId() { return requesterId; }
    UUID getReceiverId() { return receiverId; }
    FriendRequestStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }

    void respond(boolean accepted, Instant now) {
        status = accepted ? FriendRequestStatus.ACCEPTED : FriendRequestStatus.DECLINED;
        respondedAt = now;
    }
}

@Entity
@Table(name = "friendship")
@IdClass(FriendshipId.class)
class Friendship {
    @Id
    @Column(name = "user_low_id")
    private UUID userLowId;

    @Id
    @Column(name = "user_high_id")
    private UUID userHighId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Friendship() { }

    Friendship(UUID first, UUID second, Instant now) {
        if (first.compareTo(second) < 0) {
            userLowId = first;
            userHighId = second;
        } else {
            userLowId = second;
            userHighId = first;
        }
        createdAt = now;
    }

    UUID other(UUID current) {
        return userLowId.equals(current) ? userHighId : userLowId;
    }
}

interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {
    List<FriendRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(
            UUID receiverId,
            FriendRequestStatus status
    );

    @Query("select r from FriendRequest r where r.status = :status and " +
            "((r.requesterId = :first and r.receiverId = :second) or " +
            "(r.requesterId = :second and r.receiverId = :first))")
    List<FriendRequest> findBetween(@Param("first") UUID first,
                                    @Param("second") UUID second,
                                    @Param("status") FriendRequestStatus status);
}

interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {
    @Query("select f from Friendship f where f.userLowId = :userId or f.userHighId = :userId")
    List<Friendship> findForUser(@Param("userId") UUID userId);
}

@Service
class SocialService {
    private final AppUserRepository userRepository;
    private final FarmSaveRepository farmRepository;
    private final EconomyJsonService economy;
    private final PresenceService presence;
    private final FriendRequestRepository requestRepository;
    private final FriendshipRepository friendshipRepository;

    SocialService(AppUserRepository userRepository,
                  FarmSaveRepository farmRepository,
                  EconomyJsonService economy,
                  PresenceService presence,
                  FriendRequestRepository requestRepository,
                  FriendshipRepository friendshipRepository) {
        this.userRepository = userRepository;
        this.farmRepository = farmRepository;
        this.economy = economy;
        this.presence = presence;
        this.requestRepository = requestRepository;
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional(readOnly = true)
    public List<PlayerCardResponse> search(UUID currentUserId, String displayName) {
        String query = displayName == null ? "" : displayName.trim();
        if (query.length() < 2) {
            throw new BadRequestException("Enter at least two characters of the display name.");
        }
        return userRepository.findByDisplayNameContainingIgnoreCaseAndIdNot(
                        query, currentUserId, PageRequest.of(0, 30))
                .stream()
                .sorted(Comparator.comparing(AppUser::getDisplayName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(user -> card(currentUserId, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse profile(UUID currentUserId, UUID playerId) {
        AppUser user = requireUser(playerId);
        Optional<FarmSave> farm = farmRepository.findById(playerId);
        return new PlayerProfileResponse(
                user.getId(),
                displayName(user),
                farm.map(value -> economy.district(value.getSnapshot())).orElse("No farm yet"),
                farm.map(value -> economy.getMoney(value.getSnapshot())).orElse(0),
                presence.isOnline(playerId),
                relationship(currentUserId, playerId)
        );
    }

    @Transactional(readOnly = true)
    public List<PlayerCardResponse> friends(UUID currentUserId) {
        List<PlayerCardResponse> result = new ArrayList<>();
        for (Friendship friendship : friendshipRepository.findForUser(currentUserId)) {
            AppUser other = requireUser(friendship.other(currentUserId));
            result.add(card(currentUserId, other));
        }
        result.sort(Comparator.comparing(PlayerCardResponse::displayName,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> incomingRequests(UUID currentUserId) {
        return requestRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(
                        currentUserId, FriendRequestStatus.PENDING)
                .stream()
                .map(request -> {
                    AppUser sender = requireUser(request.getRequesterId());
                    Optional<FarmSave> farm = farmRepository.findById(sender.getId());
                    return new FriendRequestResponse(
                            request.getId(), sender.getId(), displayName(sender),
                            farm.map(value -> economy.district(value.getSnapshot())).orElse("No farm yet"),
                            presence.isOnline(sender.getId()), request.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public FriendRequestResponse sendRequest(UUID currentUserId, UUID targetId) {
        if (currentUserId.equals(targetId)) {
            throw new BadRequestException("You cannot add yourself as a friend.");
        }
        AppUser target = requireUser(targetId);
        if (isFriend(currentUserId, targetId)) {
            throw new ConflictException("This player is already your friend.");
        }
        List<FriendRequest> existing = requestRepository.findBetween(
                currentUserId, targetId, FriendRequestStatus.PENDING);
        if (!existing.isEmpty()) {
            throw new ConflictException("A friend request between these players is already pending.");
        }
        FriendRequest request = new FriendRequest(
                UUID.randomUUID(), currentUserId, targetId, Instant.now());
        requestRepository.save(request);
        Optional<FarmSave> farm = farmRepository.findById(targetId);
        return new FriendRequestResponse(
                request.getId(), targetId, displayName(target),
                farm.map(value -> economy.district(value.getSnapshot())).orElse("No farm yet"),
                presence.isOnline(targetId), request.getCreatedAt());
    }

    @Transactional
    public void respond(UUID currentUserId, UUID requestId, boolean accept) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));
        if (!request.getReceiverId().equals(currentUserId)) {
            throw new ForbiddenException("Only the receiving player can answer this friend request.");
        }
        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new ConflictException("This friend request has already been answered.");
        }
        request.respond(accept, Instant.now());
        requestRepository.save(request);
        if (accept && !isFriend(request.getRequesterId(), request.getReceiverId())) {
            friendshipRepository.save(new Friendship(
                    request.getRequesterId(), request.getReceiverId(), Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public boolean isFriend(UUID first, UUID second) {
        if (first.equals(second)) return false;
        FriendshipId id = first.compareTo(second) < 0
                ? new FriendshipId(first, second)
                : new FriendshipId(second, first);
        return friendshipRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public RelationshipStatus relationship(UUID currentUserId, UUID targetId) {
        if (currentUserId.equals(targetId)) return RelationshipStatus.SELF;
        if (isFriend(currentUserId, targetId)) return RelationshipStatus.FRIENDS;
        List<FriendRequest> pending = requestRepository.findBetween(
                currentUserId, targetId, FriendRequestStatus.PENDING);
        if (pending.isEmpty()) return RelationshipStatus.NONE;
        return pending.get(0).getRequesterId().equals(currentUserId)
                ? RelationshipStatus.OUTGOING_PENDING
                : RelationshipStatus.INCOMING_PENDING;
    }

    private PlayerCardResponse card(UUID currentUserId, AppUser user) {
        Optional<FarmSave> farm = farmRepository.findById(user.getId());
        return new PlayerCardResponse(
                user.getId(), displayName(user),
                farm.map(value -> economy.district(value.getSnapshot())).orElse("No farm yet"),
                presence.isOnline(user.getId()), relationship(currentUserId, user.getId()));
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Player not found."));
    }

    private static String displayName(AppUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? "Unnamed Farmer"
                : user.getDisplayName();
    }
}
