package com.agridabao.api.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final String apiKey;
    private final String model;
    private final String endpoint;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(@Value("${app.ai.gemini.api-key:}") String apiKey,
                        @Value("${app.ai.gemini.model:gemini-2.5-flash}") String model,
                        @Value("${app.ai.gemini.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        this.endpoint = trimBase(baseUrl) + "/v1beta/models/" + this.model + ":generateContent";
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public Result generate(String systemInstruction, List<String> userParts, GenerationConfig config) {
        return generate(systemInstruction, userParts, config, null);
    }

    public Result generate(String systemInstruction, List<String> userParts, GenerationConfig config,
                           String fileSearchStore) {
        ObjectNode body = mapper.createObjectNode();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            ObjectNode system = body.putObject("systemInstruction");
            system.putArray("parts").addObject().put("text", systemInstruction);
        }

        ArrayNode contents = body.putArray("contents");
        for (String part : userParts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            ObjectNode content = contents.addObject();
            content.put("role", "user");
            content.putArray("parts").addObject().put("text", part);
        }

        if (contents.isEmpty()) {
            throw new IllegalArgumentException("The request contained no prompt text.");
        }

        if (fileSearchStore != null && !fileSearchStore.isBlank()) {
            body.putArray("tools").addObject()
                    .putObject("file_search")
                    .putArray("file_search_store_names").add(fileSearchStore);
        }

        ObjectNode generationConfig = body.putObject("generationConfig");
        if (config.temperature() != null) {
            generationConfig.put("temperature", config.temperature());
        }
        if (config.topP() != null) {
            generationConfig.put("topP", config.topP());
        }
        if (config.maxOutputTokens() != null) {
            generationConfig.put("maxOutputTokens", config.maxOutputTokens());
        }
        if (config.jsonResponse()) {
            generationConfig.put("responseMimeType", "application/json");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = errorDetail(mapper, response.body());
                log.warn("Gemini rejected the request (HTTP {}{}).", response.statusCode(), detail);
                throw new IllegalStateException(
                        "The AI service returned HTTP " + response.statusCode() + detail + ".");
            }

            return parse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while contacting the AI service.", ex);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not reach the AI service.", ex);
        }
    }

    private Result parse(String rawJson) {
        JsonNode root = mapper.readTree(rawJson);
        JsonNode candidate = root.path("candidates").path(0);

        String finishReason = candidate.path("finishReason").asText("UNKNOWN");

        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            String value = part.path("text").asText("");
            if (value.isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(value.trim());
        }

        return new Result(text.toString().trim(), finishReason, sourceTitles(candidate));
    }

    private static List<String> sourceTitles(JsonNode candidate) {
        JsonNode grounding = candidate.path("groundingMetadata");
        JsonNode chunks = grounding.path("groundingChunks");
        if (!chunks.isArray() || chunks.isEmpty()) {
            return List.of();
        }

        Set<Integer> used = new LinkedHashSet<>();
        for (JsonNode support : grounding.path("groundingSupports")) {
            for (JsonNode index : support.path("groundingChunkIndices")) {
                used.add(index.asInt(-1));
            }
        }
        if (used.isEmpty()) {
            for (int i = 0; i < chunks.size(); i++) {
                used.add(i);
            }
        }

        Set<String> titles = new LinkedHashSet<>();
        for (int index : used) {
            if (index < 0 || index >= chunks.size()) {
                continue;
            }
            String title = chunks.get(index).path("retrievedContext").path("title").asText("").trim();
            if (!title.isEmpty()) {
                titles.add(title);
            }
        }
        return new ArrayList<>(titles);
    }

    static String trimBase(String baseUrl) {
        String base = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    static String errorDetail(ObjectMapper mapper, String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            String message = mapper.readTree(body).path("error").path("message").asText("").trim();
            if (message.isEmpty()) {
                return "";
            }
            return ": " + (message.length() <= 240 ? message : message.substring(0, 240) + "...");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    public record GenerationConfig(Double temperature,
                                   Double topP,
                                   Integer maxOutputTokens,
                                   boolean jsonResponse) {
    }

    public record Result(String text, String finishReason, List<String> sources) {
    }
}
