package com.agridabao.api.trade;

import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.ForbiddenException;
import com.agridabao.api.error.NotFoundException;
import com.agridabao.api.farm.EconomyJsonService;
import com.agridabao.api.farm.FarmSave;
import com.agridabao.api.social.PresenceAccessService;
import com.agridabao.api.user.AppUser;
import com.agridabao.api.user.AppUserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/trades")
public class TradeController {
    private final TradeService service;

    public TradeController(TradeService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public ActiveTradeResponse active(@AuthenticationPrincipal Jwt jwt) {
        return new ActiveTradeResponse(service.active(userId(jwt)));
    }

    @GetMapping("/incoming")
    public List<TradeView> incoming(@AuthenticationPrincipal Jwt jwt) {
        return service.incoming(userId(jwt));
    }

    @PostMapping("/invite")
    public TradeView invite(@AuthenticationPrincipal Jwt jwt,
                            @Valid @RequestBody TradeInviteRequest request) {
        return service.invite(userId(jwt), request.targetPlayerId());
    }

    @PostMapping("/{tradeId}/accept")
    public TradeView accept(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID tradeId) {
        return service.accept(userId(jwt), tradeId);
    }

    @PostMapping("/{tradeId}/decline")
    public TradeView decline(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID tradeId) {
        return service.decline(userId(jwt), tradeId);
    }

    @PutMapping("/{tradeId}/offer")
    public TradeView offer(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID tradeId,
                           @Valid @RequestBody UpdateTradeOfferRequest request) {
        return service.updateOffer(userId(jwt), tradeId, request);
    }

    @PostMapping("/{tradeId}/agree")
    public TradeView agree(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID tradeId) {
        return service.agree(userId(jwt), tradeId);
    }

    @PostMapping("/{tradeId}/confirm")
    public TradeView confirm(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID tradeId) {
        return service.confirm(userId(jwt), tradeId);
    }

    @PostMapping("/{tradeId}/cancel")
    public TradeView cancel(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID tradeId) {
        return service.cancel(userId(jwt), tradeId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

enum TradeStatus { PENDING, ACTIVE, COMPLETED, DECLINED, CANCELLED, EXPIRED }

record ActiveTradeResponse(TradeView trade) { }
record TradeInviteRequest(@NotNull UUID targetPlayerId) { }
record TradeItemRequest(@NotNull String itemType, @Min(1) int quantity) { }
record UpdateTradeOfferRequest(
        @Min(0) int money,
        @NotNull @Size(max = 30) List<@Valid TradeItemRequest> items
) { }
record TradeOfferView(int money, List<TradeItemRequest> items) { }
record TradeView(
        UUID id,
        UUID requesterId,
        String requesterDisplayName,
        UUID targetId,
        String targetDisplayName,
        TradeStatus status,
        TradeOfferView requesterOffer,
        TradeOfferView targetOffer,
        boolean requesterAgreed,
        boolean targetAgreed,
        boolean requesterConfirmed,
        boolean targetConfirmed,
        boolean requesterOnline,
        boolean targetOnline,
        Instant createdAt,
        Instant expiresAt,
        Long currentPlayerFarmRevision
) { }

@Entity
@Table(name = "trade_session")
class TradeSession {
    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TradeStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requester_offer", nullable = false, columnDefinition = "jsonb")
    private JsonNode requesterOffer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_offer", nullable = false, columnDefinition = "jsonb")
    private JsonNode targetOffer;

    @Column(name = "requester_agreed", nullable = false)
    private boolean requesterAgreed;

    @Column(name = "target_agreed", nullable = false)
    private boolean targetAgreed;

    @Column(name = "requester_confirmed", nullable = false)
    private boolean requesterConfirmed;

    @Column(name = "target_confirmed", nullable = false)
    private boolean targetConfirmed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TradeSession() { }

    TradeSession(UUID id, UUID requesterId, UUID targetId, JsonNode emptyOffer, Instant now) {
        this.id = id;
        this.requesterId = requesterId;
        this.targetId = targetId;
        this.status = TradeStatus.PENDING;
        this.requesterOffer = emptyOffer.deepCopy();
        this.targetOffer = emptyOffer.deepCopy();
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = now.plus(15, ChronoUnit.MINUTES);
    }

    UUID getId() { return id; }
    UUID getRequesterId() { return requesterId; }
    UUID getTargetId() { return targetId; }
    TradeStatus getStatus() { return status; }
    JsonNode getRequesterOffer() { return requesterOffer; }
    JsonNode getTargetOffer() { return targetOffer; }
    boolean isRequesterAgreed() { return requesterAgreed; }
    boolean isTargetAgreed() { return targetAgreed; }
    boolean isRequesterConfirmed() { return requesterConfirmed; }
    boolean isTargetConfirmed() { return targetConfirmed; }
    Instant getCreatedAt() { return createdAt; }
    Instant getExpiresAt() { return expiresAt; }

    boolean includes(UUID userId) {
        return requesterId.equals(userId) || targetId.equals(userId);
    }

    void accept() {
        status = TradeStatus.ACTIVE;
        touch();
    }

    void decline() {
        status = TradeStatus.DECLINED;
        touch();
    }

    void cancel() {
        status = TradeStatus.CANCELLED;
        touch();
    }

    void complete() {
        status = TradeStatus.COMPLETED;
        touch();
    }

    void expire() {
        status = TradeStatus.EXPIRED;
        touch();
    }

    void setOffer(UUID actor, JsonNode offer) {
        if (requesterId.equals(actor)) requesterOffer = offer;
        else targetOffer = offer;
        requesterAgreed = false;
        targetAgreed = false;
        requesterConfirmed = false;
        targetConfirmed = false;
        touch();
    }

    void agree(UUID actor) {
        if (requesterId.equals(actor)) requesterAgreed = true;
        else targetAgreed = true;
        requesterConfirmed = false;
        targetConfirmed = false;
        touch();
    }

    void confirm(UUID actor) {
        if (requesterId.equals(actor)) requesterConfirmed = true;
        else targetConfirmed = true;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}

interface TradeSessionRepository extends JpaRepository<TradeSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TradeSession t where t.id = :id")
    Optional<TradeSession> findForUpdate(@Param("id") UUID id);

    @Query("select t from TradeSession t where " +
            "(t.requesterId = :userId or t.targetId = :userId) and t.status in :statuses " +
            "order by t.updatedAt desc")
    List<TradeSession> findForUser(@Param("userId") UUID userId,
                                   @Param("statuses") Set<TradeStatus> statuses);

    @Query("select t from TradeSession t where t.targetId = :userId and t.status = :status " +
            "order by t.createdAt desc")
    List<TradeSession> findIncoming(@Param("userId") UUID userId,
                                    @Param("status") TradeStatus status);
}

@Service
class TradeService {
    private static final Set<TradeStatus> OPEN_STATUSES = Set.of(TradeStatus.PENDING, TradeStatus.ACTIVE);

    private final TradeSessionRepository repository;
    private final AppUserRepository userRepository;
    private final PresenceAccessService presence;
    private final EconomyJsonService economy;
    private final ObjectMapper objectMapper;

    TradeService(TradeSessionRepository repository,
                 AppUserRepository userRepository,
                 PresenceAccessService presence,
                 EconomyJsonService economy,
                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.presence = presence;
        this.economy = economy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TradeView active(UUID currentUserId) {
        List<TradeSession> values = openTrades(currentUserId);
        if (values.isEmpty()) return null;
        return view(values.get(0), currentUserId, null);
    }

    @Transactional(readOnly = true)
    public List<TradeView> incoming(UUID currentUserId) {
        return repository.findIncoming(currentUserId, TradeStatus.PENDING).stream()
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .map(value -> view(value, currentUserId, null))
                .toList();
    }

    @Transactional
    public TradeView invite(UUID requesterId, UUID targetId) {
        if (requesterId.equals(targetId)) throw new BadRequestException("You cannot trade with yourself.");
        requireUser(targetId);
        requireOnline(requesterId, targetId);
        if (!openTrades(requesterId).isEmpty()) {
            throw new ConflictException("Finish or cancel the current trade first.");
        }
        if (!openTrades(targetId).isEmpty()) {
            throw new ConflictException("The other player is already in another trade.");
        }
        economy.requireFarm(requesterId);
        economy.requireFarm(targetId);
        TradeSession trade = new TradeSession(
                UUID.randomUUID(), requesterId, targetId, emptyOffer(), Instant.now());
        repository.save(trade);
        return view(trade, requesterId, null);
    }

    @Transactional
    public TradeView accept(UUID actor, UUID tradeId) {
        TradeSession trade = requireTrade(tradeId);
        if (!trade.getTargetId().equals(actor)) {
            throw new ForbiddenException("Only the invited player can accept this trade.");
        }
        requireState(trade, TradeStatus.PENDING);
        requireNotExpired(trade);
        requireOnline(trade.getRequesterId(), trade.getTargetId());
        trade.accept();
        repository.save(trade);
        return view(trade, actor, null);
    }

    @Transactional
    public TradeView decline(UUID actor, UUID tradeId) {
        TradeSession trade = requireTrade(tradeId);
        if (!trade.getTargetId().equals(actor)) {
            throw new ForbiddenException("Only the invited player can decline this trade.");
        }
        requireState(trade, TradeStatus.PENDING);
        trade.decline();
        repository.save(trade);
        return view(trade, actor, null);
    }

    @Transactional
    public TradeView updateOffer(UUID actor, UUID tradeId, UpdateTradeOfferRequest request) {
        TradeSession trade = requireParticipantTrade(actor, tradeId);
        requireState(trade, TradeStatus.ACTIVE);
        requireNotExpired(trade);
        requireOnline(trade.getRequesterId(), trade.getTargetId());
        ObjectNode normalized = normalizeOffer(request);
        validateOfferAgainstSavedFarm(actor, normalized);
        trade.setOffer(actor, normalized);
        repository.save(trade);
        return view(trade, actor, null);
    }

    @Transactional
    public TradeView agree(UUID actor, UUID tradeId) {
        TradeSession trade = requireParticipantTrade(actor, tradeId);
        requireState(trade, TradeStatus.ACTIVE);
        requireNotExpired(trade);
        requireOnline(trade.getRequesterId(), trade.getTargetId());
        validateOfferAgainstSavedFarm(trade.getRequesterId(), trade.getRequesterOffer());
        validateOfferAgainstSavedFarm(trade.getTargetId(), trade.getTargetOffer());
        trade.agree(actor);
        repository.save(trade);
        return view(trade, actor, null);
    }

    @Transactional
    public TradeView confirm(UUID actor, UUID tradeId) {
        TradeSession trade = requireParticipantTrade(actor, tradeId);
        requireState(trade, TradeStatus.ACTIVE);
        requireNotExpired(trade);
        requireOnline(trade.getRequesterId(), trade.getTargetId());
        if (!trade.isRequesterAgreed() || !trade.isTargetAgreed()) {
            throw new ConflictException("Both players must press Agree Trade before final confirmation.");
        }
        trade.confirm(actor);
        Long revision = null;
        if (trade.isRequesterConfirmed() && trade.isTargetConfirmed()) {
            revision = executeExchange(trade, actor);
            trade.complete();
        }
        repository.save(trade);
        return view(trade, actor, revision);
    }

    @Transactional
    public TradeView cancel(UUID actor, UUID tradeId) {
        TradeSession trade = requireParticipantTrade(actor, tradeId);
        if (!OPEN_STATUSES.contains(trade.getStatus())) {
            throw new ConflictException("This trade is already closed.");
        }
        trade.cancel();
        repository.save(trade);
        return view(trade, actor, null);
    }

    private Long executeExchange(TradeSession trade, UUID currentUserId) {
        List<UUID> lockOrder = new ArrayList<>(List.of(trade.getRequesterId(), trade.getTargetId()));
        lockOrder.sort(Comparator.naturalOrder());
        FarmSave first = economy.requireFarmForUpdate(lockOrder.get(0));
        FarmSave second = economy.requireFarmForUpdate(lockOrder.get(1));
        FarmSave requesterFarm = first.getUserId().equals(trade.getRequesterId()) ? first : second;
        FarmSave targetFarm = first.getUserId().equals(trade.getTargetId()) ? first : second;

        ObjectNode requesterSnapshot = economy.editableSnapshot(requesterFarm);
        ObjectNode targetSnapshot = economy.editableSnapshot(targetFarm);
        Offer requesterOffer = readOffer(trade.getRequesterOffer());
        Offer targetOffer = readOffer(trade.getTargetOffer());

        validateOffer(requesterSnapshot, requesterOffer, "requester");
        validateOffer(targetSnapshot, targetOffer, "target");

        removeOffer(requesterSnapshot, requesterOffer);
        removeOffer(targetSnapshot, targetOffer);
        addOffer(requesterSnapshot, targetOffer);
        addOffer(targetSnapshot, requesterOffer);

        economy.saveChangedSnapshot(requesterFarm, requesterSnapshot);
        economy.saveChangedSnapshot(targetFarm, targetSnapshot);
        return currentUserId.equals(requesterFarm.getUserId())
                ? requesterFarm.getRevision() : targetFarm.getRevision();
    }

    private void validateOfferAgainstSavedFarm(UUID userId, JsonNode offerNode) {
        FarmSave farm = economy.requireFarm(userId);
        validateOffer(farm.getSnapshot(), readOffer(offerNode), "player");
    }

    private void validateOffer(JsonNode snapshot, Offer offer, String label) {
        if (economy.getMoney(snapshot) < offer.money()) {
            throw new ConflictException("The " + label + " no longer has enough money for this offer.");
        }
        for (TradeItemRequest item : offer.items()) {
            economy.validateTradableItem(item.itemType());
            if (economy.countItem(snapshot, item.itemType()) < item.quantity()) {
                throw new ConflictException("The " + label + " no longer has enough " + item.itemType() + ".");
            }
        }
    }

    private void removeOffer(ObjectNode snapshot, Offer offer) {
        economy.setMoney(snapshot, economy.getMoney(snapshot) - offer.money());
        for (TradeItemRequest item : offer.items()) {
            economy.removeItem(snapshot, item.itemType(), item.quantity());
        }
    }

    private void addOffer(ObjectNode snapshot, Offer offer) {
        for (TradeItemRequest item : offer.items()) {
            if (!economy.canAddItem(snapshot, item.itemType(), item.quantity())) {
                throw new ConflictException("A player does not have enough inventory space to finish the trade.");
            }
        }
        for (TradeItemRequest item : offer.items()) {
            economy.addItem(snapshot, item.itemType(), item.quantity());
        }
        economy.setMoney(snapshot, economy.getMoney(snapshot) + offer.money());
    }

    private ObjectNode normalizeOffer(UpdateTradeOfferRequest request) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (TradeItemRequest item : request.items()) {
            economy.validateTradableItem(item.itemType());
            if (item.quantity() <= 0) throw new BadRequestException("Trade quantities must be positive.");
            merged.merge(item.itemType(), item.quantity(), Math::addExact);
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("money", request.money());
        ArrayNode items = objectMapper.createArrayNode();
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("itemType", entry.getKey());
            node.put("quantity", entry.getValue());
            items.add(node);
        }
        root.set("items", items);
        return root;
    }

    private ObjectNode emptyOffer() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("money", 0);
        root.set("items", objectMapper.createArrayNode());
        return root;
    }

    private Offer readOffer(JsonNode node) {
        if (node == null || !node.isObject()) return new Offer(0, List.of());
        int money = Math.max(0, node.path("money").asInt(0));
        List<TradeItemRequest> items = new ArrayList<>();
        JsonNode rawItems = node.path("items");
        if (rawItems.isArray()) {
            for (JsonNode item : rawItems) {
                String itemType = item.path("itemType").asText("");
                int quantity = item.path("quantity").asInt(0);
                if (!itemType.isBlank() && quantity > 0) {
                    items.add(new TradeItemRequest(itemType, quantity));
                }
            }
        }
        return new Offer(money, List.copyOf(items));
    }

    private TradeView view(TradeSession trade, UUID currentUserId, Long currentRevision) {
        AppUser requester = requireUser(trade.getRequesterId());
        AppUser target = requireUser(trade.getTargetId());
        Offer requesterOffer = readOffer(trade.getRequesterOffer());
        Offer targetOffer = readOffer(trade.getTargetOffer());
        return new TradeView(
                trade.getId(), trade.getRequesterId(), displayName(requester),
                trade.getTargetId(), displayName(target), trade.getStatus(),
                new TradeOfferView(requesterOffer.money(), requesterOffer.items()),
                new TradeOfferView(targetOffer.money(), targetOffer.items()),
                trade.isRequesterAgreed(), trade.isTargetAgreed(),
                trade.isRequesterConfirmed(), trade.isTargetConfirmed(),
                presence.isOnline(trade.getRequesterId()), presence.isOnline(trade.getTargetId()),
                trade.getCreatedAt(), trade.getExpiresAt(), currentRevision);
    }

    private List<TradeSession> openTrades(UUID userId) {
        List<TradeSession> active = new ArrayList<>();
        for (TradeSession trade : repository.findForUser(userId, OPEN_STATUSES)) {
            if (trade.getExpiresAt().isAfter(Instant.now())) {
                active.add(trade);
            } else {
                trade.expire();
                repository.save(trade);
            }
        }
        return active;
    }

    private TradeSession requireTrade(UUID tradeId) {
        TradeSession trade = repository.findForUpdate(tradeId)
                .orElseThrow(() -> new NotFoundException("Trade session not found."));
        if (OPEN_STATUSES.contains(trade.getStatus()) && !trade.getExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("This trade session has expired.");
        }
        return trade;
    }

    private TradeSession requireParticipantTrade(UUID actor, UUID tradeId) {
        TradeSession trade = requireTrade(tradeId);
        if (!trade.includes(actor)) {
            throw new ForbiddenException("You are not a participant in this trade.");
        }
        return trade;
    }

    private static void requireState(TradeSession trade, TradeStatus expected) {
        if (trade.getStatus() != expected) {
            throw new ConflictException("The trade is in state " + trade.getStatus() + ", not " + expected + ".");
        }
    }

    private static void requireNotExpired(TradeSession trade) {
        if (!trade.getExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("This trade session has expired.");
        }
    }

    private void requireOnline(UUID first, UUID second) {
        if (!presence.isOnline(first) || !presence.isOnline(second)) {
            throw new ConflictException("Both players must be online to trade.");
        }
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Player not found."));
    }

    private static String displayName(AppUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? "Unnamed Farmer" : user.getDisplayName();
    }

    private record Offer(int money, List<TradeItemRequest> items) { }
}
