package com.agridabao.api.ai;

import com.agridabao.api.error.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The game's single entry point to the AI adviser.
 *
 * Unity used to call Google directly, which meant every build shipped the API
 * key inside the APK. The phone now calls this instead: it sends the prompt and
 * its player JWT, and the key stays on the server.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService service;

    public AiController(AiService service) {
        this.service = service;
    }

    /** Lets the game show the adviser as unavailable without making a full request. */
    @GetMapping("/status")
    public AiStatusResponse status() {
        return service.status();
    }

    @PostMapping("/generate")
    public AiGenerateResponse generate(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody AiGenerateRequest request) {
        return service.generate(jwt.getSubject(), request);
    }
}

/** Which part of the game is asking; decides the generation settings applied. */
enum AiFeature {
    /** Free-form chat with the farm adviser. */
    ADVISOR,
    /** Post-event write-up of how the player handled a typhoon or drought. */
    CLIMATE_EVALUATION,
    /** Invents one multi-day task; must return strict JSON. */
    TASK_GENERATE,
    /** Grades an assigned task; must return strict JSON. */
    TASK_CHECK
}

record AiGenerateRequest(
        @NotNull AiFeature feature,
        @Size(max = 20000) String systemInstruction,
        @NotEmpty @Size(max = 8) List<@Size(max = 60000) String> userParts
) {
}

/**
 * @param available    false when the adviser is switched off; the game shows
 *                     {@code message} instead of an answer
 * @param finishReason "STOP" when the model completed its own sentence
 */
record AiGenerateResponse(boolean available,
                          String text,
                          String finishReason,
                          String message) {
}

record AiStatusResponse(boolean available, String message) {
}

@Service
class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    /**
     * Shown whenever the adviser cannot answer - switched off, unconfigured, or
     * the upstream call failed. Deliberately in the adviser's own voice so the
     * game never surfaces a technical error to a player.
     */
    static final String UNAVAILABLE_MESSAGE =
            "Sorry, Antonio is still busy with his farm, please try again later.";

    private final GeminiClient gemini;
    private final boolean enabled;

    AiService(GeminiClient gemini, @Value("${app.ai.enabled:true}") boolean enabled) {
        this.gemini = gemini;
        this.enabled = enabled;

        log.info("AI adviser: {} (key {})",
                enabled ? "enabled" : "DISABLED by APP_AI_ENABLED",
                gemini.isConfigured() ? "present" : "MISSING");
    }

    AiStatusResponse status() {
        return isAvailable()
                ? new AiStatusResponse(true, null)
                : new AiStatusResponse(false, UNAVAILABLE_MESSAGE);
    }

    AiGenerateResponse generate(String userId, AiGenerateRequest request) {
        if (!isAvailable()) {
            log.debug("AI request from {} refused: adviser unavailable.", userId);
            return unavailable();
        }

        GeminiClient.GenerationConfig config = configFor(request.feature());

        // Both of these are applied here rather than trusted from the phone. The
        // prompt the client sends lives inside the APK, so a modified build can
        // send anything at all; whatever it sends, it arrives underneath the
        // server's rules and its last part arrives quoted.
        String systemInstruction =
                AiGuardrails.systemInstructionFor(request.feature(), request.systemInstruction());
        List<String> userParts = prepareParts(request.feature(), request.userParts());

        try {
            GeminiClient.Result result = gemini.generate(systemInstruction, userParts, config);

            if (result.text() == null || result.text().isBlank()) {
                log.warn("AI returned no text for {} (finishReason={}).",
                        request.feature(), result.finishReason());
                return unavailable();
            }

            return new AiGenerateResponse(true, result.text(), result.finishReason(), null);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        } catch (RuntimeException ex) {
            // An upstream outage is not the player's problem: answer in-character
            // rather than surfacing a 500 in the middle of the game.
            log.warn("AI request for {} failed: {}", request.feature(), ex.getMessage());
            return unavailable();
        }
    }

    /**
     * A farming question does not need an essay. Anything longer is either a
     * mistake or an attempt to bury an instruction deep enough that the model
     * loses track of the rules above it.
     */
    private static final int MAX_QUESTION_CHARS = 1000;

    /**
     * Quotes the player's question so it cannot be mistaken for part of the
     * prompt around it.
     *
     * Only the last part is treated as the question: the adviser sends the game
     * context first and the typed question last. Everything before it is
     * generated by the game rather than typed by anyone, so it needs no fence -
     * and fencing it would only bury the real question further from the rules.
     *
     * Features other than the adviser take no player text at all, so their parts
     * are passed through exactly as they arrive.
     */
    private static List<String> prepareParts(AiFeature feature, List<String> parts) {
        if (feature != AiFeature.ADVISOR || parts.isEmpty()) {
            return parts;
        }

        int last = parts.size() - 1;
        String question = parts.get(last);

        if (question != null && question.length() > MAX_QUESTION_CHARS) {
            throw new BadRequestException(
                    "That question is a bit long for Antonio. Please ask something shorter.");
        }

        List<String> prepared = new java.util.ArrayList<>(parts);
        prepared.set(last, AiGuardrails.fencePlayerQuestion(question));
        return prepared;
    }

    private boolean isAvailable() {
        return enabled && gemini.isConfigured();
    }

    private static AiGenerateResponse unavailable() {
        return new AiGenerateResponse(false, null, null, UNAVAILABLE_MESSAGE);
    }

    /**
     * Mirrors the settings the Unity clients used before the move, so the
     * adviser's behaviour is unchanged by relocating the call.
     */
    private static GeminiClient.GenerationConfig configFor(AiFeature feature) {
        return switch (feature) {
            // Chat: slightly creative, and a ceiling high enough that a farm with
            // several crop groups does not get its answer cut off mid-sentence.
            case ADVISOR -> new GeminiClient.GenerationConfig(0.4, 0.9, 3000, false);
            // No token ceiling, which is how this ran before the call moved here.
            //
            // Gemini 2.5 Flash is a thinking model and its reasoning tokens count
            // against maxOutputTokens. The climate payload is by far the largest
            // prompt the game sends - the guidance plus every before/after crop
            // snapshot and every recorded action - so a 3000 cap could be spent
            // entirely on thinking, returning MAX_TOKENS with an empty parts array
            // and therefore no evaluation text at all.
            case CLIMATE_EVALUATION -> new GeminiClient.GenerationConfig(null, null, null, false);
            // Task generation and grading must return parseable JSON, so the
            // response MIME type is pinned and the temperature kept low.
            case TASK_GENERATE, TASK_CHECK ->
                    new GeminiClient.GenerationConfig(0.25, null, 3000, true);
        };
    }
}
