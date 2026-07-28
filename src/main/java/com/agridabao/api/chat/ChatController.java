package com.agridabao.api.chat;

import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.ForbiddenException;
import com.agridabao.api.social.FriendshipAccessService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/{otherPlayerId}/messages")
    public List<ChatMessageResponse> messages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID otherPlayerId,
            @RequestParam(required = false) Instant after) {
        return service.messages(userId(jwt), otherPlayerId, after);
    }

    @PostMapping("/{otherPlayerId}/messages")
    @ResponseStatus(CREATED)
    public ChatMessageResponse send(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID otherPlayerId,
            @Valid @RequestBody SendMessageRequest request) {
        return service.send(userId(jwt), otherPlayerId, request.body());
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unread(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(service.unreadCount(userId(jwt)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

record SendMessageRequest(@NotBlank @Size(max = 1000) String body) {
}

record ChatMessageResponse(
        UUID id,
        UUID senderId,
        UUID receiverId,
        String body,
        Instant sentAt,
        Instant readAt) {
}

record UnreadCountResponse(long count) {
}

@Entity
@Table(name = "chat_message")
class ChatMessage {
    @Id
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected ChatMessage() {
    }

    ChatMessage(UUID id, UUID senderId, UUID receiverId, String body, Instant sentAt) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.body = body;
        this.sentAt = sentAt;
    }

    UUID getId() {
        return id;
    }

    UUID getSenderId() {
        return senderId;
    }

    UUID getReceiverId() {
        return receiverId;
    }

    String getBody() {
        return body;
    }

    Instant getSentAt() {
        return sentAt;
    }

    Instant getReadAt() {
        return readAt;
    }
}

interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        select m from ChatMessage m
        where
        (m.senderId = :first and m.receiverId = :second)
        or
        (m.senderId = :second and m.receiverId = :first)
        """)
    List<ChatMessage> conversation(
            @Param("first") UUID first,
            @Param("second") UUID second,
            Pageable pageable);

    @Query("""
        select m from ChatMessage m
        where
        (
            (m.senderId = :first and m.receiverId = :second)
            or
            (m.senderId = :second and m.receiverId = :first)
        )
        and m.sentAt > :after
        """)
    List<ChatMessage> conversationAfter(
            @Param("first") UUID first,
            @Param("second") UUID second,
            @Param("after") Instant after,
            Pageable pageable);

    @Modifying
    @Query("""
        update ChatMessage m
        set m.readAt = :now
        where m.senderId = :sender
        and m.receiverId = :receiver
        and m.readAt is null
        """)
    int markRead(
            @Param("sender") UUID sender,
            @Param("receiver") UUID receiver,
            @Param("now") Instant now);

    long countByReceiverIdAndReadAtIsNull(UUID receiverId);
}

@Service
class ChatService {
    private static final int MAX_MESSAGES_PER_REQUEST = 100;

    private final ChatMessageRepository repository;
    private final FriendshipAccessService friendships;

    ChatService(ChatMessageRepository repository, FriendshipAccessService friendships) {
        this.repository = repository;
        this.friendships = friendships;
    }

    @Transactional
public List<ChatMessageResponse> messages(
        UUID current,
        UUID other,
        Instant after) {

    requireFriend(current, other);

    repository.markRead(
            other,
            current,
            Instant.now());

    Pageable latestFirst = PageRequest.of(
            0,
            100,
            Sort.by(
                    Sort.Direction.DESC,
                    "sentAt"));

    List<ChatMessage> result;

    if (after == null) {
        result = repository.conversation(
                current,
                other,
                latestFirst);
    } else {
        result = repository.conversationAfter(
                current,
                other,
                after,
                latestFirst);
    }

    List<ChatMessageResponse> mapped =
            new ArrayList<>(
                    result.stream()
                            .map(ChatService::response)
                            .toList());

    Collections.reverse(mapped);
    return mapped;
}

    @Transactional
    public ChatMessageResponse send(UUID current, UUID other, String rawBody) {
        requireFriend(current, other);

        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) {
            throw new BadRequestException("Message cannot be empty.");
        }

        ChatMessage message = new ChatMessage(
                UUID.randomUUID(),
                current,
                other,
                body,
                Instant.now());

        return response(repository.save(message));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID current) {
        return repository.countByReceiverIdAndReadAtIsNull(current);
    }

    private void requireFriend(UUID current, UUID other) {
        if (!friendships.areFriends(current, other)) {
            throw new ForbiddenException(
                    "Chat is available only after both players become friends.");
        }
    }

    private static ChatMessageResponse response(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSenderId(),
                message.getReceiverId(),
                message.getBody(),
                message.getSentAt(),
                message.getReadAt());
    }
}

