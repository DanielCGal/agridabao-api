package com.agridabao.api.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
class GeminiFileSearchClient {
    private static final int MAX_PAGES = 50;

    private final String api;
    private final String uploadApi;
    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    GeminiFileSearchClient(@Value("${app.ai.gemini.api-key:}") String apiKey,
                           @Value("${app.ai.gemini.base-url:" + GeminiClient.DEFAULT_BASE_URL + "}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.api = GeminiClient.trimBase(baseUrl) + "/v1beta/";
        this.uploadApi = GeminiClient.trimBase(baseUrl) + "/upload/v1beta/";
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    boolean isConfigured() {
        return !apiKey.isBlank();
    }

    List<Store> listStores() {
        List<Store> stores = new ArrayList<>();
        String pageToken = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode body = getJson(api + "fileSearchStores?pageSize=20" + pageParam(pageToken));
            for (JsonNode node : body.path("fileSearchStores")) {
                stores.add(new Store(node.path("name").asText(""), node.path("displayName").asText("")));
            }
            pageToken = body.path("nextPageToken").asText("");
            if (pageToken.isBlank()) {
                break;
            }
        }

        return stores;
    }

    Store createStore(String displayName) {
        ObjectNode request = mapper.createObjectNode();
        request.put("displayName", displayName);

        JsonNode body = sendJson(HttpRequest.newBuilder()
                .uri(URI.create(api + "fileSearchStores"))
                .timeout(Duration.ofSeconds(30))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(request), StandardCharsets.UTF_8))
                .build());

        String name = body.path("name").asText("");
        if (name.isBlank()) {
            throw new IllegalStateException("Gemini created a store but did not return its name.");
        }
        return new Store(name, displayName);
    }

    void deleteStore(String storeName) {
        sendJson(HttpRequest.newBuilder()
                .uri(URI.create(api + storeName + "?force=true"))
                .timeout(Duration.ofSeconds(30))
                .header("x-goog-api-key", apiKey)
                .DELETE()
                .build());
    }

    List<StoredDocument> listDocuments(String storeName) {
        List<StoredDocument> documents = new ArrayList<>();
        String pageToken = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode body = getJson(api + storeName + "/documents?pageSize=20" + pageParam(pageToken));
            for (JsonNode node : body.path("documents")) {
                documents.add(new StoredDocument(
                        node.path("name").asText(""),
                        node.path("displayName").asText(""),
                        node.path("state").asText("")));
            }
            pageToken = body.path("nextPageToken").asText("");
            if (pageToken.isBlank()) {
                break;
            }
        }

        return documents;
    }

    void uploadDocument(String storeName, String displayName, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("displayName", displayName);

        HttpResponse<String> started = send(HttpRequest.newBuilder()
                .uri(URI.create(uploadApi + storeName + ":uploadToFileSearchStore"))
                .timeout(Duration.ofSeconds(30))
                .header("x-goog-api-key", apiKey)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", Integer.toString(bytes.length))
                .header("X-Goog-Upload-Header-Content-Type", "text/plain")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(metadata), StandardCharsets.UTF_8))
                .build());

        String uploadUrl = started.headers().firstValue("x-goog-upload-url").orElse("");
        if (uploadUrl.isBlank()) {
            throw new IllegalStateException("Gemini did not return an upload address.");
        }

        send(HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(Duration.ofSeconds(120))
                .header("x-goog-api-key", apiKey)
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build());
    }

    private static String pageParam(String pageToken) {
        return pageToken == null || pageToken.isBlank()
                ? ""
                : "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8);
    }

    private JsonNode getJson(String url) {
        return sendJson(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("x-goog-api-key", apiKey)
                .GET()
                .build());
    }

    private JsonNode sendJson(HttpRequest request) {
        String body = send(request).body();
        return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini File Search returned HTTP "
                        + response.statusCode() + errorDetail(response.body()));
            }

            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while contacting Gemini File Search.", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not reach Gemini File Search.", ex);
        }
    }

    private String errorDetail(String body) {
        return GeminiClient.errorDetail(mapper, body);
    }

    record Store(String name, String displayName) {
    }

    record StoredDocument(String name, String displayName, String state) {
        boolean failed() {
            return state.toUpperCase().contains("FAILED");
        }

        boolean active() {
            return state.toUpperCase().contains("ACTIVE");
        }
    }
}
