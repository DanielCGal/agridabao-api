package com.agridabao.api.ai;

import com.agridabao.api.ai.GeminiFileSearchClient.Store;
import com.agridabao.api.ai.GeminiFileSearchClient.StoredDocument;
import com.agridabao.api.ai.KnowledgeDocuments.KnowledgeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
class KnowledgeBase {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    static final String STORE_PREFIX = "agridabaw-field-guide-";

    private static final Duration[] RETRY_AFTER =
            {Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(5)};
    private static final Duration RETRY_LATER = Duration.ofMinutes(15);
    private static final Duration INDEXING_WAIT = Duration.ofMinutes(4);
    private static final Duration PAUSE_AFTER_FAILURE = Duration.ofMinutes(10);
    private static final int MAX_SOURCES = 2;
    private static final int MAX_REFERENCES_PER_SOURCE = 2;

    private final GeminiFileSearchClient fileSearch;
    private final boolean enabled;
    private final boolean onForOlderGames;
    private final String sourceUrl;

    private final Map<String, List<String>> referencesByTitle = new ConcurrentHashMap<>();

    private volatile String activeStore;
    private volatile int documentCount;
    private volatile Instant lastSyncAt;
    private volatile String lastProblem;
    private volatile String state = "OFF";
    private volatile Instant pausedUntil = Instant.EPOCH;

    KnowledgeBase(GeminiFileSearchClient fileSearch,
                  @Value("${app.ai.knowledge.enabled:true}") boolean enabled,
                  @Value("${app.ai.knowledge.on-for-older-games:true}") boolean onForOlderGames,
                  @Value("${app.ai.knowledge.source-url:}") String sourceUrl) {
        this.fileSearch = fileSearch;
        this.enabled = enabled;
        this.onForOlderGames = onForOlderGames;
        this.sourceUrl = sourceUrl == null ? "" : sourceUrl.trim();
    }

    @EventListener(ApplicationReadyEvent.class)
    void startSyncing() {
        if (!enabled) {
            log.info("AI field guide: DISABLED by APP_AI_KNOWLEDGE_ENABLED.");
            return;
        }
        if (!fileSearch.isConfigured()) {
            log.info("AI field guide: skipped, no Gemini key.");
            return;
        }

        Thread worker = new Thread(this::syncWithRetries, "field-guide-sync");
        worker.setDaemon(true);
        worker.start();
    }

    String storeFor(Boolean gameWantsFieldGuide) {
        boolean wanted = gameWantsFieldGuide == null ? onForOlderGames : gameWantsFieldGuide;
        if (!enabled || !wanted || Instant.now().isBefore(pausedUntil)) {
            return null;
        }
        return activeStore;
    }

    void reportFailure(String problem) {
        lastProblem = problem;
        pausedUntil = Instant.now().plus(PAUSE_AFTER_FAILURE);
        log.warn("AI field guide: paused for {} minutes after a failed request: {}",
                PAUSE_AFTER_FAILURE.toMinutes(), problem);
    }

    List<String> describeSources(List<String> titles) {
        List<String> described = new ArrayList<>();
        for (String title : titles) {
            if (described.size() >= MAX_SOURCES) {
                break;
            }
            List<String> references = referencesByTitle.getOrDefault(title, List.of());
            if (references.isEmpty()) {
                described.add(title);
                continue;
            }
            List<String> shown = references.subList(0, Math.min(MAX_REFERENCES_PER_SOURCE, references.size()));
            described.add(title + " (" + String.join("; ", shown) + ")");
        }
        return described;
    }

    KnowledgeStatusResponse status() {
        boolean ready = enabled && activeStore != null;
        boolean paused = ready && Instant.now().isBefore(pausedUntil);
        return new KnowledgeStatusResponse(
                enabled,
                ready && !paused,
                paused ? "PAUSED" : state,
                documentCount,
                lastSyncAt == null ? null : lastSyncAt.toString(),
                lastProblem);
    }

    private void syncWithRetries() {
        for (int attempt = 0; ; attempt++) {
            try {
                sync();
                return;
            } catch (RuntimeException ex) {
                lastProblem = ex.getMessage();
                state = activeStore == null ? "FAILED" : "READY";
                log.warn("AI field guide: sync attempt {} failed: {}", attempt + 1, ex.getMessage());
            }

            Duration wait = attempt < RETRY_AFTER.length ? RETRY_AFTER[attempt] : RETRY_LATER;
            try {
                Thread.sleep(wait.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sync() {
        state = activeStore == null ? "SYNCING" : state;

        List<Store> stores = fileSearch.listStores();
        List<KnowledgeDocument> documents = fetchDocuments(stores);
        if (documents == null) {
            return;
        }

        String displayName = STORE_PREFIX + KnowledgeDocuments.contentId(documents);
        Store store = stores.stream()
                .filter(candidate -> candidate.displayName().equals(displayName))
                .findFirst()
                .orElse(null);

        if (store == null) {
            store = fileSearch.createStore(displayName);
            log.info("AI field guide: created store {} for {} documents.", displayName, documents.size());
        }

        Set<String> stored = new HashSet<>();
        for (StoredDocument document : fileSearch.listDocuments(store.name())) {
            if (!document.failed()) {
                stored.add(document.displayName());
            }
        }

        int uploaded = 0;
        for (KnowledgeDocument document : documents) {
            if (stored.contains(document.title())) {
                continue;
            }
            fileSearch.uploadDocument(store.name(), document.title(), document.text());
            uploaded++;
        }

        int active = waitUntilIndexed(store.name(), documents.size());
        if (active == 0) {
            throw new IllegalStateException("No field guide document finished indexing yet.");
        }

        referencesByTitle.clear();
        for (KnowledgeDocument document : documents) {
            referencesByTitle.put(document.title(), document.references());
        }

        activeStore = store.name();
        documentCount = active;
        lastSyncAt = Instant.now();
        lastProblem = active < documents.size()
                ? "Only " + active + " of " + documents.size() + " documents are indexed so far."
                : null;
        state = "READY";
        log.info("AI field guide: ready with {} of {} documents ({} uploaded this time).",
                active, documents.size(), uploaded);

        removeOlderStores(stores, store.name());
    }

    private List<KnowledgeDocument> fetchDocuments(List<Store> stores) {
        try {
            if (sourceUrl.isBlank()) {
                throw new IllegalStateException("No field guide address is configured.");
            }
            List<KnowledgeDocument> documents = KnowledgeDocuments.split(download(sourceUrl));
            if (documents.isEmpty()) {
                throw new IllegalStateException("The field guide document came back empty.");
            }
            return documents;
        } catch (RuntimeException ex) {
            Store existing = stores.stream()
                    .filter(candidate -> candidate.displayName().startsWith(STORE_PREFIX))
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                throw ex;
            }

            int active = countActive(existing.name());
            if (active == 0) {
                throw ex;
            }

            activeStore = existing.name();
            documentCount = active;
            lastSyncAt = Instant.now();
            lastProblem = "Kept the earlier field guide: " + ex.getMessage();
            state = "READY";
            log.warn("AI field guide: could not read the source, kept the existing store: {}", ex.getMessage());
            return null;
        }
    }

    private int waitUntilIndexed(String storeName, int expected) {
        Instant deadline = Instant.now().plus(INDEXING_WAIT);
        int active = countActive(storeName);

        while (active < expected && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            active = countActive(storeName);
        }

        return active;
    }

    private int countActive(String storeName) {
        int active = 0;
        for (StoredDocument document : fileSearch.listDocuments(storeName)) {
            if (document.active()) {
                active++;
            }
        }
        return active;
    }

    private void removeOlderStores(List<Store> stores, String keep) {
        for (Store store : stores) {
            if (!store.displayName().startsWith(STORE_PREFIX) || store.name().equals(keep)) {
                continue;
            }
            try {
                fileSearch.deleteStore(store.name());
                log.info("AI field guide: removed the older store {}.", store.displayName());
            } catch (RuntimeException ex) {
                log.warn("AI field guide: could not remove the older store {}: {}",
                        store.displayName(), ex.getMessage());
            }
        }
    }

    private static String download(String url) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (response.statusCode() != 200 || !contentType.startsWith("text/plain")) {
                throw new IllegalStateException("The field guide document could not be read (HTTP "
                        + response.statusCode() + "). Check that it is still shared by link.");
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the field guide document.", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not reach the field guide document.", ex);
        }
    }
}
