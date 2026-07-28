package com.agridabao.api.marketplace;

import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.ForbiddenException;
import com.agridabao.api.error.NotFoundException;
import com.agridabao.api.farm.EconomyJsonService;
import com.agridabao.api.farm.FarmSave;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {
    private final MarketplaceService service;

    public MarketplaceController(MarketplaceService service) {
        this.service = service;
    }

    @GetMapping("/listings")
    public List<MarketplaceListingResponse> listings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String itemType) {
        return service.listings(userId(jwt), itemType);
    }

    @GetMapping("/fee")
    public ListingFeeResponse fee(@RequestParam String itemType,
                                  @RequestParam int quantity,
                                  @RequestParam int askingPrice) {
        return service.fee(itemType, quantity, askingPrice);
    }

    @PostMapping("/listings")
    @ResponseStatus(CREATED)
    public MarketplaceListingResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateListingRequest request) {
        return service.create(userId(jwt), request);
    }

    @PostMapping("/listings/{listingId}/buy")
    public MarketplacePurchaseResponse buy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID listingId) {
        return service.buy(userId(jwt), listingId);
    }

    @PostMapping("/listings/{listingId}/cancel")
    public MarketplaceListingResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID listingId) {
        return service.cancelOrClaim(userId(jwt), listingId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

enum ListingStatus { OPEN, SOLD, CANCELLED, EXPIRED, RETURN_PENDING }

record CreateListingRequest(
        @NotBlank @Size(max = 80) String itemType,
        @Min(1) int quantity,
        @Min(0) int askingPrice
) { }

record ListingFeeResponse(int itemBaseValue, int itemValueTotal,
                          int askingPrice, int listingFee) { }

record MarketplaceListingResponse(
        UUID id,
        UUID sellerId,
        String sellerDisplayName,
        String itemType,
        int quantity,
        int askingPrice,
        int listingFee,
        ListingStatus status,
        Instant createdAt,
        Instant expiresAt,
        boolean mine
) { }

record MarketplacePurchaseResponse(
        MarketplaceListingResponse listing,
        long buyerFarmRevision,
        int buyerMoney
) { }

@Entity
@Table(name = "marketplace_listing")
class MarketplaceListing {
    @Id
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "item_type", nullable = false, length = 80)
    private String itemType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "asking_price", nullable = false)
    private int askingPrice;

    @Column(name = "listing_fee", nullable = false)
    private int listingFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sold_at")
    private Instant soldAt;

    @Column(name = "buyer_id")
    private UUID buyerId;

    protected MarketplaceListing() { }

    MarketplaceListing(UUID id, UUID sellerId, String itemType, int quantity,
                       int askingPrice, int listingFee, Instant now) {
        this.id = id;
        this.sellerId = sellerId;
        this.itemType = itemType;
        this.quantity = quantity;
        this.askingPrice = askingPrice;
        this.listingFee = listingFee;
        this.status = ListingStatus.OPEN;
        this.createdAt = now;
        this.expiresAt = now.plus(3, ChronoUnit.DAYS);
    }

    UUID getId() { return id; }
    UUID getSellerId() { return sellerId; }
    String getItemType() { return itemType; }
    int getQuantity() { return quantity; }
    int getAskingPrice() { return askingPrice; }
    int getListingFee() { return listingFee; }
    ListingStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getExpiresAt() { return expiresAt; }

    void sold(UUID buyerId, Instant now) {
        this.status = ListingStatus.SOLD;
        this.buyerId = buyerId;
        this.soldAt = now;
    }

    void cancelled() { this.status = ListingStatus.CANCELLED; }
    void expired() { this.status = ListingStatus.EXPIRED; }
    void returnPending() { this.status = ListingStatus.RETURN_PENDING; }
}

interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, UUID> {
    List<MarketplaceListing> findByStatusOrderByCreatedAtDesc(ListingStatus status);
    List<MarketplaceListing> findByStatusAndItemTypeOrderByCreatedAtDesc(
            ListingStatus status, String itemType);
    List<MarketplaceListing> findBySellerIdAndStatusOrderByCreatedAtDesc(
            UUID sellerId, ListingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MarketplaceListing l where l.id = :id")
    Optional<MarketplaceListing> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MarketplaceListing l where l.status = :status and l.expiresAt <= :now")
    List<MarketplaceListing> findExpiredForUpdate(@Param("status") ListingStatus status,
                                                   @Param("now") Instant now);
}

@Service
class MarketplaceService {
    private final MarketplaceListingRepository repository;
    private final AppUserRepository userRepository;
    private final EconomyJsonService economy;

    MarketplaceService(MarketplaceListingRepository repository,
                       AppUserRepository userRepository,
                       EconomyJsonService economy) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.economy = economy;
    }

    @Transactional(readOnly = true)
    public ListingFeeResponse fee(String itemType, int quantity, int askingPrice) {
        economy.validateTradableItem(itemType);
        if (quantity <= 0 || askingPrice < 0) {
            throw new BadRequestException("Quantity must be positive and price cannot be negative.");
        }
        int base = economy.baseValue(itemType);
        long itemTotal = (long) base * quantity;
        long fee = itemTotal + askingPrice;
        if (fee > Integer.MAX_VALUE) throw new BadRequestException("The calculated listing fee is too large.");
        return new ListingFeeResponse(base, (int) itemTotal, askingPrice, (int) fee);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceListingResponse> listings(UUID currentUserId, String itemType) {
        List<MarketplaceListing> listings;
        if (itemType == null || itemType.isBlank() || "ALL".equalsIgnoreCase(itemType)) {
            listings = repository.findByStatusOrderByCreatedAtDesc(ListingStatus.OPEN);
        } else {
            economy.validateTradableItem(itemType);
            listings = repository.findByStatusAndItemTypeOrderByCreatedAtDesc(
                    ListingStatus.OPEN, itemType);
        }
        List<MarketplaceListing> combined = new ArrayList<>(listings.stream()
                .filter(listing -> listing.getExpiresAt().isAfter(Instant.now()))
                .toList());
        for (MarketplaceListing pending : repository.findBySellerIdAndStatusOrderByCreatedAtDesc(
                currentUserId, ListingStatus.RETURN_PENDING)) {
            if (itemType == null || itemType.isBlank() || "ALL".equalsIgnoreCase(itemType) ||
                    pending.getItemType().equals(itemType)) {
                combined.add(pending);
            }
        }
        return combined.stream()
                .map(listing -> response(listing, currentUserId))
                .toList();
    }

    @Transactional
    public MarketplaceListingResponse create(UUID sellerId, CreateListingRequest request) {
        economy.validateTradableItem(request.itemType());
        ListingFeeResponse fee = fee(request.itemType(), request.quantity(), request.askingPrice());
        FarmSave farm = economy.requireFarmForUpdate(sellerId);
        ObjectNode snapshot = economy.editableSnapshot(farm);
        if (economy.getMoney(snapshot) < fee.listingFee()) {
            throw new ConflictException("Not enough money to pay the listing fee of P" + fee.listingFee() + ".");
        }
        economy.removeItem(snapshot, request.itemType(), request.quantity());
        economy.setMoney(snapshot, economy.getMoney(snapshot) - fee.listingFee());
        economy.saveChangedSnapshot(farm, snapshot);

        MarketplaceListing listing = new MarketplaceListing(
                UUID.randomUUID(), sellerId, request.itemType(), request.quantity(),
                request.askingPrice(), fee.listingFee(), Instant.now());
        repository.save(listing);
        return response(listing, sellerId);
    }

    @Transactional
    public MarketplacePurchaseResponse buy(UUID buyerId, UUID listingId) {
        MarketplaceListing listing = repository.findForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Marketplace listing not found."));
        if (listing.getStatus() != ListingStatus.OPEN) {
            throw new ConflictException("This listing is no longer available.");
        }
        if (!listing.getExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("This listing has expired and will be returned by the expiration worker.");
        }
        if (listing.getSellerId().equals(buyerId)) {
            throw new BadRequestException("You cannot buy your own listing.");
        }

        List<UUID> order = new ArrayList<>(List.of(buyerId, listing.getSellerId()));
        order.sort(Comparator.naturalOrder());
        FarmSave first = economy.requireFarmForUpdate(order.get(0));
        FarmSave second = economy.requireFarmForUpdate(order.get(1));
        FarmSave buyerFarm = first.getUserId().equals(buyerId) ? first : second;
        FarmSave sellerFarm = first.getUserId().equals(listing.getSellerId()) ? first : second;

        ObjectNode buyerSnapshot = economy.editableSnapshot(buyerFarm);
        ObjectNode sellerSnapshot = economy.editableSnapshot(sellerFarm);
        if (economy.getMoney(buyerSnapshot) < listing.getAskingPrice()) {
            throw new ConflictException("The buyer does not have enough money.");
        }
        if (!economy.canAddItem(buyerSnapshot, listing.getItemType(), listing.getQuantity())) {
            throw new ConflictException("The buyer inventory does not have enough space.");
        }

        economy.setMoney(buyerSnapshot, economy.getMoney(buyerSnapshot) - listing.getAskingPrice());
        economy.addItem(buyerSnapshot, listing.getItemType(), listing.getQuantity());
        economy.setMoney(sellerSnapshot, economy.getMoney(sellerSnapshot) + listing.getAskingPrice());
        economy.saveChangedSnapshot(buyerFarm, buyerSnapshot);
        economy.saveChangedSnapshot(sellerFarm, sellerSnapshot);

        listing.sold(buyerId, Instant.now());
        repository.save(listing);
        return new MarketplacePurchaseResponse(
                response(listing, buyerId),
                buyerFarm.getRevision(),
                economy.getMoney(buyerSnapshot));
    }

    @Transactional
    public MarketplaceListingResponse cancelOrClaim(UUID sellerId, UUID listingId) {
        MarketplaceListing listing = repository.findForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Marketplace listing not found."));
        if (!listing.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("Only the seller can cancel or claim this listing.");
        }
        if (listing.getStatus() != ListingStatus.OPEN &&
                listing.getStatus() != ListingStatus.RETURN_PENDING) {
            throw new ConflictException("This listing cannot be cancelled or claimed.");
        }
        FarmSave farm = economy.requireFarmForUpdate(sellerId);
        ObjectNode snapshot = economy.editableSnapshot(farm);
        if (!economy.canAddItem(snapshot, listing.getItemType(), listing.getQuantity())) {
            throw new ConflictException("Free inventory space first, then press Cancel or Claim Return again.");
        }
        economy.addItem(snapshot, listing.getItemType(), listing.getQuantity());
        economy.saveChangedSnapshot(farm, snapshot);
        if (listing.getStatus() == ListingStatus.OPEN) listing.cancelled();
        else listing.expired();
        repository.save(listing);
        return response(listing, sellerId);
    }

    @Transactional
    public void expireDueListings() {
        List<MarketplaceListing> expired = repository.findExpiredForUpdate(
                ListingStatus.OPEN, Instant.now());
        for (MarketplaceListing listing : expired) {
            returnExpiredItem(listing);
        }
    }

    private void returnExpiredItem(MarketplaceListing listing) {
        try {
            FarmSave farm = economy.requireFarmForUpdate(listing.getSellerId());
            ObjectNode snapshot = economy.editableSnapshot(farm);
            if (!economy.canAddItem(snapshot, listing.getItemType(), listing.getQuantity())) {
                listing.returnPending();
            } else {
                economy.addItem(snapshot, listing.getItemType(), listing.getQuantity());
                economy.saveChangedSnapshot(farm, snapshot);
                listing.expired();
            }
        } catch (RuntimeException ex) {
            listing.returnPending();
        }
        repository.save(listing);
    }

    private MarketplaceListingResponse response(MarketplaceListing listing, UUID currentUserId) {
        AppUser seller = userRepository.findById(listing.getSellerId()).orElse(null);
        String displayName = seller == null || seller.getDisplayName() == null || seller.getDisplayName().isBlank()
                ? "Unnamed Farmer" : seller.getDisplayName();
        return new MarketplaceListingResponse(
                listing.getId(), listing.getSellerId(), displayName,
                listing.getItemType(), listing.getQuantity(), listing.getAskingPrice(),
                listing.getListingFee(), listing.getStatus(), listing.getCreatedAt(),
                listing.getExpiresAt(), listing.getSellerId().equals(currentUserId));
    }
}

@Component
class MarketplaceExpiryJob {
    private final MarketplaceService service;

    MarketplaceExpiryJob(MarketplaceService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000)
    public void returnExpiredListings() {
        service.expireDueListings();
    }
}
