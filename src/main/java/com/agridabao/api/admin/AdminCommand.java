package com.agridabao.api.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What a queued command asks the receiving game to do. */
enum AdminCommandType {
    /** Add pesos to the player's wallet. Uses amount. */
    GRANT_MONEY,
    /** Start a weather event. Uses payload for the type and durationDays. */
    FORCE_WEATHER,
    /** Start a pest or disease outbreak. Uses payload for the enum name. */
    FORCE_PEST_DISEASE,
    /** Push the farm clock forward whole days. Uses amount. */
    PASS_DAYS,
    /** Push the farm clock forward whole hours. Uses amount. */
    PASS_HOURS
}

/**
 * One instruction waiting for one player.
 *
 * Rows are kept after delivery rather than deleted, so there is a record of
 * what was sent to whom during a test session.
 */
@Entity
@Table(name = "admin_command")
class AdminCommand {
    @Id
    private UUID id;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 32)
    private AdminCommandType commandType;

    @Column(length = 64)
    private String payload;

    private Integer amount;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected AdminCommand() {
    }

    AdminCommand(UUID id, UUID targetUserId, UUID issuedBy, AdminCommandType commandType,
                 String payload, Integer amount, Integer durationDays, Instant createdAt) {
        this.id = id;
        this.targetUserId = targetUserId;
        this.issuedBy = issuedBy;
        this.commandType = commandType;
        this.payload = payload;
        this.amount = amount;
        this.durationDays = durationDays;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getTargetUserId() { return targetUserId; }
    AdminCommandType getCommandType() { return commandType; }
    String getPayload() { return payload; }
    Integer getAmount() { return amount; }
    Integer getDurationDays() { return durationDays; }
    Instant getCreatedAt() { return createdAt; }
    Instant getDeliveredAt() { return deliveredAt; }

    void markDelivered(Instant when) {
        this.deliveredAt = when;
    }
}

interface AdminCommandRepository extends JpaRepository<AdminCommand, UUID> {
    List<AdminCommand> findByTargetUserIdAndDeliveredAtIsNullOrderByCreatedAtAsc(UUID targetUserId);
}
