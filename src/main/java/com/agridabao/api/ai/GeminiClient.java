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
import java.util.List;

/**
 * Talks to Google's Generative Language API on the game's behalf.
 *
 * The key lives here rather than in the Unity client. A Unity build ships its
 * serialized Inspector values inside the APK, so a key held on a MonoBehaviour
 * can be recovered by unpacking the package - anyone who did so could spend
 * against the project's quota. Keeping it server-side means the phone never
 * holds the secret; it only holds a player JWT, which is revocable and scoped.
 */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(@Value("${app.ai.gemini.api-key:}") String apiKey,
                        @Value("${app.ai.gemini.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * One generation call.
     *
     * @param systemInstruction optional system prompt, or null to send none
     * @param userParts         the user text blocks, in order
     * @param config            per-feature generation settings
     * @return the model's text plus the reason it stopped
     */
    public Result generate(String systemInstruction, List<String> userParts, GenerationConfig config) {
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
                .uri(URI.create(String.format(ENDPOINT_TEMPLATE, model)))
                .timeout(Duration.ofSeconds(60))
                // Header rather than ?key= so the secret never lands in a URL,
                // where proxies and access logs would record it.
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gemini rejected the request (HTTP {}).", response.statusCode());
                throw new IllegalStateException(
                        "The AI service returned HTTP " + response.statusCode() + ".");
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

    /**
     * Joins every text part of the first candidate, mirroring what the Unity
     * clients did locally so multi-part answers are not silently truncated.
     */
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

        return new Result(text.toString().trim(), finishReason);
    }

    /** Per-feature generation settings; nulls fall back to Gemini's defaults. */
    public record GenerationConfig(Double temperature,
                                   Double topP,
                                   Integer maxOutputTokens,
                                   boolean jsonResponse) {
    }

    /**
     * @param finishReason "STOP" means the model finished its own sentence.
     *                     Anything else - usually MAX_TOKENS - means the answer
     *                     was cut off, which the game shows as a retry prompt.
     */
    public record Result(String text, String finishReason) {
    }
}
