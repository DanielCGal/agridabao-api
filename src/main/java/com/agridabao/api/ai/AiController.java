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

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService service;

    public AiController(AiService service) {
        this.service = service;
    }

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

enum AiFeature {
    ADVISOR,
    CLIMATE_EVALUATION,
    TASK_GENERATE,
    TASK_CHECK
}

record AiGenerateRequest(
        @NotNull AiFeature feature,
        @Size(max = 20000) String systemInstruction,
        @NotEmpty @Size(max = 8) List<@Size(max = 60000) String> userParts
) {
}

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
            log.warn("AI request for {} failed: {}", request.feature(), ex.getMessage());
            return unavailable();
        }
    }

    private static final int MAX_QUESTION_CHARS = 1000;

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

    private static GeminiClient.GenerationConfig configFor(AiFeature feature) {
        return switch (feature) {
            case ADVISOR -> new GeminiClient.GenerationConfig(0.4, 0.9, 3000, false);
            case CLIMATE_EVALUATION -> new GeminiClient.GenerationConfig(null, null, null, false);
            case TASK_GENERATE, TASK_CHECK ->
                    new GeminiClient.GenerationConfig(0.25, null, null, true);
        };
    }
}
