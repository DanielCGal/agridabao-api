package com.agridabao.api.review;

import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.TooManyRequestsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping
    public ReviewPageResponse list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        return service.list(page, size);
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public ReviewPostedResponse create(@Valid @RequestBody CreateReviewRequest request,
                                       HttpServletRequest http) {
        return service.create(request, http.getRemoteAddr());
    }
}

record CreateReviewRequest(
        @Size(max = ReviewService.MAX_NAME_LENGTH,
                message = "Please keep your name to 60 characters or fewer.")
        String name,
        @NotNull(message = "This is a required question.")
        @Min(value = 1, message = "Choose a rating from 1 to 5 stars.")
        @Max(value = 5, message = "Choose a rating from 1 to 5 stars.")
        Integer rating,
        @NotBlank(message = "This is a required question.")
        @Size(max = ReviewService.MAX_COMMENT_LENGTH,
                message = "Please keep your answer to 1000 characters or fewer.")
        String comment,
        @Size(max = 200) String website
) { }

record ReviewResponse(UUID id, String name, int rating, String comment, Instant createdAt) { }

record RatingSummaryResponse(long total, double average, List<Long> counts) { }

record ReviewPageResponse(
        RatingSummaryResponse summary,
        List<ReviewResponse> reviews,
        int page,
        int size,
        boolean hasMore
) { }

record ReviewPostedResponse(ReviewResponse review, RatingSummaryResponse summary) { }

@Entity
@Table(name = "game_review")
class GameReview {
    @Id
    private UUID id;

    @Column(name = "reviewer_name", length = 60)
    private String reviewerName;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GameReview() { }

    GameReview(UUID id, String reviewerName, int rating, String body, Instant createdAt) {
        this.id = id;
        this.reviewerName = reviewerName;
        this.rating = rating;
        this.body = body;
        this.hidden = false;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    String getReviewerName() { return reviewerName; }
    int getRating() { return rating; }
    String getBody() { return body; }
    boolean isHidden() { return hidden; }
    Instant getCreatedAt() { return createdAt; }
}

interface GameReviewRepository extends JpaRepository<GameReview, UUID> {
    Slice<GameReview> findByHiddenFalseOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByHiddenFalseAndRating(int rating);
}

@Service
class ReviewService {
    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_COMMENT_LENGTH = 1000;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;

    private final GameReviewRepository repository;
    private final ReviewRateLimiter rateLimiter;

    ReviewService(GameReviewRepository repository, ReviewRateLimiter rateLimiter) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public ReviewPageResponse list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        Slice<GameReview> slice = repository.findByHiddenFalseOrderByCreatedAtDescIdDesc(
                PageRequest.of(safePage, safeSize));

        return new ReviewPageResponse(
                summary(),
                slice.getContent().stream().map(ReviewService::response).toList(),
                safePage,
                safeSize,
                slice.hasNext());
    }

    @Transactional
    public ReviewPostedResponse create(CreateReviewRequest request, String clientAddress) {
        if (request.website() != null && !request.website().isBlank()) {
            throw new BadRequestException("Your review could not be sent. Please try again.");
        }

        String comment = clean(request.comment());
        if (comment.isEmpty()) {
            throw new BadRequestException("This is a required question.");
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException("Please keep your answer to 1000 characters or fewer.");
        }

        String name = cleanName(request.name());
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("Please keep your name to 60 characters or fewer.");
        }

        if (!rateLimiter.tryAcquire(clientAddress)) {
            throw new TooManyRequestsException(
                    "Too many reviews have been sent in the last hour. Please try again later.");
        }

        GameReview review = new GameReview(
                UUID.randomUUID(), name, request.rating(), comment, Instant.now());
        repository.save(review);

        return new ReviewPostedResponse(response(review), summary());
    }

    private RatingSummaryResponse summary() {
        List<Long> counts = new ArrayList<>(5);
        long total = 0;
        long points = 0;

        for (int stars = 1; stars <= 5; stars++) {
            long count = repository.countByHiddenFalseAndRating(stars);
            counts.add(count);
            total += count;
            points += count * stars;
        }

        double average = total == 0 ? 0 : Math.round(points * 100.0 / total) / 100.0;
        return new RatingSummaryResponse(total, average, List.copyOf(counts));
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }

        String text = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        StringBuilder kept = new StringBuilder(text.length());
        text.codePoints()
                .filter(point -> point == '\n' || !isHiddenCharacter(point))
                .forEach(kept::appendCodePoint);

        return kept.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    private static String cleanName(String raw) {
        String name = clean(raw).replaceAll("\\s+", " ");
        return name.isEmpty() ? null : name;
    }

    private static boolean isHiddenCharacter(int point) {
        return Character.isISOControl(point)
                || (point >= 0x202A && point <= 0x202E)
                || (point >= 0x2066 && point <= 0x2069);
    }

    private static ReviewResponse response(GameReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getReviewerName(),
                review.getRating(),
                review.getBody(),
                review.getCreatedAt());
    }
}

@Component
class ReviewRateLimiter {
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final int maxPerAddress;
    private final int maxOverall;
    private final Deque<Instant> recent = new ArrayDeque<>();
    private final Map<String, Deque<Instant>> byAddress = new HashMap<>();

    ReviewRateLimiter(@Value("${app.reviews.max-per-address-per-hour:20}") int maxPerAddress,
                      @Value("${app.reviews.max-per-hour:200}") int maxOverall) {
        this.maxPerAddress = maxPerAddress;
        this.maxOverall = maxOverall;
    }

    synchronized boolean tryAcquire(String address) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);

        dropOlderThan(recent, cutoff);
        if (recent.size() >= maxOverall) {
            return false;
        }

        if (byAddress.size() >= MAX_TRACKED_ADDRESSES) {
            byAddress.values().forEach(times -> dropOlderThan(times, cutoff));
            byAddress.values().removeIf(Deque::isEmpty);
        }

        String key = address == null || address.isBlank() ? "unknown" : address;
        Deque<Instant> times = byAddress.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        dropOlderThan(times, cutoff);
        if (times.size() >= maxPerAddress) {
            return false;
        }

        recent.addLast(now);
        times.addLast(now);
        return true;
    }

    private static void dropOlderThan(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.pollFirst();
        }
    }
}
