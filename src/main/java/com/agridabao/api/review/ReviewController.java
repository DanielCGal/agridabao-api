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
import org.springframework.data.jpa.repository.Query;
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
import java.util.LinkedHashMap;
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
        @Size(max = ReviewService.MAX_CHOICES, message = "Too many choices were sent.")
        List<@Size(max = ReviewService.MAX_CODE_LENGTH) String> features,
        @Size(max = ReviewService.MAX_OTHER_LENGTH,
                message = "Please keep your answer to 100 characters or fewer.")
        String featuresOther,
        @Min(value = 1, message = "Choose a rating from 1 to 5.")
        @Max(value = 5, message = "Choose a rating from 1 to 5.")
        Integer websiteRating,
        @Size(max = ReviewService.MAX_CHOICES, message = "Too many choices were sent.")
        List<@Size(max = ReviewService.MAX_CODE_LENGTH) String> hardestSections,
        @Size(max = ReviewService.MAX_OTHER_LENGTH,
                message = "Please keep your answer to 100 characters or fewer.")
        String hardestOther,
        @Size(max = ReviewService.MAX_COMMENT_LENGTH,
                message = "Please keep your answer to 1000 characters or fewer.")
        String websiteChange,
        @Size(max = 200) String website
) { }

record ReviewResponse(UUID id, String name, int rating, String comment, String websiteChange,
                      Instant createdAt) { }

record RatingSummaryResponse(long total, double average, List<Long> counts) { }

record ChoiceSummaryResponse(long responses, Map<String, Long> counts, List<String> others) { }

record SurveySummaryResponse(
        ChoiceSummaryResponse features,
        RatingSummaryResponse website,
        ChoiceSummaryResponse hardestSections
) { }

record ReviewPageResponse(
        RatingSummaryResponse summary,
        SurveySummaryResponse survey,
        List<ReviewResponse> reviews,
        int page,
        int size,
        boolean hasMore
) { }

record ReviewPostedResponse(ReviewResponse review, RatingSummaryResponse summary,
                            SurveySummaryResponse survey) { }

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

    @Column(name = "enjoyed_features", length = 400)
    private String enjoyedFeatures;

    @Column(name = "enjoyed_other", length = 100)
    private String enjoyedOther;

    @Column(name = "website_rating")
    private Integer websiteRating;

    @Column(name = "hardest_sections", length = 300)
    private String hardestSections;

    @Column(name = "hardest_other", length = 100)
    private String hardestOther;

    @Column(name = "website_change", length = 1000)
    private String websiteChange;

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

    void answerSurvey(String enjoyedFeatures, String enjoyedOther, Integer websiteRating,
                      String hardestSections, String hardestOther, String websiteChange) {
        this.enjoyedFeatures = enjoyedFeatures;
        this.enjoyedOther = enjoyedOther;
        this.websiteRating = websiteRating;
        this.hardestSections = hardestSections;
        this.hardestOther = hardestOther;
        this.websiteChange = websiteChange;
    }

    UUID getId() { return id; }
    String getReviewerName() { return reviewerName; }
    int getRating() { return rating; }
    String getBody() { return body; }
    boolean isHidden() { return hidden; }
    String getWebsiteChange() { return websiteChange; }
    Instant getCreatedAt() { return createdAt; }
}

interface GameReviewRepository extends JpaRepository<GameReview, UUID> {
    Slice<GameReview> findByHiddenFalseOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByHiddenFalseAndRating(int rating);

    @Query("select r.enjoyedFeatures, r.websiteRating, r.hardestSections from GameReview r "
            + "where r.hidden = false")
    List<Object[]> findSurveyAnswers();

    @Query("select r.enjoyedOther from GameReview r where r.hidden = false "
            + "and r.enjoyedOther is not null order by r.createdAt desc")
    List<String> findRecentFeatureOthers(Pageable pageable);

    @Query("select r.hardestOther from GameReview r where r.hidden = false "
            + "and r.hardestOther is not null order by r.createdAt desc")
    List<String> findRecentSectionOthers(Pageable pageable);
}

@Service
class ReviewService {
    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_COMMENT_LENGTH = 1000;
    static final int MAX_OTHER_LENGTH = 100;
    static final int MAX_CHOICES = 12;
    static final int MAX_CODE_LENGTH = 40;
    static final String OTHER = "OTHER";
    static final String NONE = "NONE";
    static final List<String> FEATURE_CODES = List.of(
            "FARM_LAND", "TUTORIAL", "DAILY_OBJECTIVES", "SHOP", "AI_ADVISER",
            "MARKETPLACE", "TRADING", "CHAT_FRIENDS", "NOTIFICATIONS", OTHER);
    static final List<String> SECTION_CODES = List.of(
            "ABOUT", "PROBLEM", "FEATURES", "DISTRICTS", "AUDIENCE",
            "GALLERY", "REVIEWS", "DOWNLOAD", NONE, OTHER);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_OTHERS_SHOWN = 20;

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
                survey(),
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

        List<String> features = choices(request.features(), FEATURE_CODES);
        String featuresOther = otherAnswer(features, request.featuresOther());

        List<String> sections = choices(request.hardestSections(), SECTION_CODES);
        if (sections.contains(NONE) && sections.size() > 1) {
            throw new BadRequestException(
                    "Choose None only when no section was hard to understand or navigate.");
        }
        String hardestOther = otherAnswer(sections, request.hardestOther());

        String websiteChange = clean(request.websiteChange());
        if (websiteChange.length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException("Please keep your answer to 1000 characters or fewer.");
        }

        if (!rateLimiter.tryAcquire(clientAddress)) {
            throw new TooManyRequestsException(
                    "Too many reviews have been sent in the last hour. Please try again later.");
        }

        GameReview review = new GameReview(
                UUID.randomUUID(), name, request.rating(), comment, Instant.now());
        review.answerSurvey(
                joined(features),
                featuresOther,
                request.websiteRating(),
                joined(sections),
                hardestOther,
                websiteChange.isEmpty() ? null : websiteChange);
        repository.save(review);

        return new ReviewPostedResponse(response(review), summary(), survey());
    }

    private SurveySummaryResponse survey() {
        Map<String, Long> featureCounts = emptyCounts(FEATURE_CODES);
        Map<String, Long> sectionCounts = emptyCounts(SECTION_CODES);
        long[] websiteCounts = new long[5];
        long featureResponses = 0;
        long sectionResponses = 0;

        for (Object[] row : repository.findSurveyAnswers()) {
            if (tally((String) row[0], featureCounts)) {
                featureResponses++;
            }
            if (row[1] instanceof Number stars && stars.intValue() >= 1 && stars.intValue() <= 5) {
                websiteCounts[stars.intValue() - 1]++;
            }
            if (tally((String) row[2], sectionCounts)) {
                sectionResponses++;
            }
        }

        List<Long> counts = new ArrayList<>(5);
        long total = 0;
        long points = 0;
        for (int stars = 1; stars <= 5; stars++) {
            long count = websiteCounts[stars - 1];
            counts.add(count);
            total += count;
            points += count * stars;
        }
        double average = total == 0 ? 0 : Math.round(points * 100.0 / total) / 100.0;

        PageRequest newest = PageRequest.of(0, MAX_OTHERS_SHOWN);
        return new SurveySummaryResponse(
                new ChoiceSummaryResponse(featureResponses, featureCounts,
                        repository.findRecentFeatureOthers(newest)),
                new RatingSummaryResponse(total, average, List.copyOf(counts)),
                new ChoiceSummaryResponse(sectionResponses, sectionCounts,
                        repository.findRecentSectionOthers(newest)));
    }

    private static Map<String, Long> emptyCounts(List<String> codes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        codes.forEach(code -> counts.put(code, 0L));
        return counts;
    }

    private static boolean tally(String stored, Map<String, Long> counts) {
        if (stored == null || stored.isBlank()) {
            return false;
        }

        boolean counted = false;
        for (String code : stored.split(",")) {
            if (counts.containsKey(code)) {
                counts.merge(code, 1L, Long::sum);
                counted = true;
            }
        }
        return counted;
    }

    private static List<String> choices(List<String> raw, List<String> allowed) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        for (String code : raw) {
            if (code == null || !allowed.contains(code)) {
                throw new BadRequestException(
                        "One of the choices was not recognised. Please reload the page and try again.");
            }
        }
        return allowed.stream().filter(raw::contains).toList();
    }

    private static String otherAnswer(List<String> chosen, String raw) {
        if (!chosen.contains(OTHER)) {
            return null;
        }

        String text = clean(raw).replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            throw new BadRequestException("Please write your answer beside Other.");
        }
        if (text.length() > MAX_OTHER_LENGTH) {
            throw new BadRequestException("Please keep your answer to 100 characters or fewer.");
        }
        return text;
    }

    private static String joined(List<String> codes) {
        return codes.isEmpty() ? null : String.join(",", codes);
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
                review.getWebsiteChange(),
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
