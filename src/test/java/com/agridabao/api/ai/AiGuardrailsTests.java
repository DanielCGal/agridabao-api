package com.agridabao.api.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AiGuardrailsTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Antonio, your crops are not doing well, friend.|Your crops are not doing well, friend.",
            "Antonio, yes. Antonio, water them today.|Yes. Water them today.",
            "Hello, Antonio! Your soil is dry.|Hello! Your soil is dry.",
            "Hi Antonio, your durian needs water.|Hi, your durian needs water.",
            "Good morning, Antonio. It will rain today.|Good morning. It will rain today.",
            "Maayong buntag Antonio! Check the squash.|Maayong buntag! Check the squash.",
            "That is a good question, Antonio.|That is a good question.",
            "Well done, Antonio! Keep mulching.|Well done! Keep mulching.",
            "Remember, Antonio, to drain the field.|Remember, to drain the field.",
            "Antonio my friend, the cacao needs shade.|My friend, the cacao needs shade.",
            "Here is the plan: Antonio, start with mulch.|Here is the plan: Start with mulch."
    })
    void stopsTheAdviserCallingThePlayerAntonio(String reply, String expected) {
        assertThat(AiGuardrails.withoutPlayerCalledAntonio(reply)).isEqualTo(expected);
    }

    @Test
    void keepsTaskJsonValidAndTheSpeakerName() {
        String reply = "{\"dialogue\":\"Antonio, your banana is thirsty.\",\"speaker\":\"Antonio\"}";

        assertThat(AiGuardrails.withoutPlayerCalledAntonio(reply))
                .isEqualTo("{\"dialogue\":\"Your banana is thirsty.\",\"speaker\":\"Antonio\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "I am Antonio, and your crops look healthy.",
            "Hello! Antonio here. Your crops look healthy.",
            "Hello, Antonio here with today's advice.",
            "Antonio, your farm adviser, has checked the field.",
            "Antonio thinks the soil is too wet.",
            "My name is Antonio. Ask me about your crops.",
            "Antonio only knows farming, friend. Ask me about your crops, your soil, or the weather.",
            "Your crops are fine."
    })
    void leavesAntonioTalkingAboutHimselfAlone(String reply) {
        assertThat(AiGuardrails.withoutPlayerCalledAntonio(reply)).isEqualTo(reply);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Antonio, your crops are multi-line.\n\nAntonio, water them."})
    void handlesEveryLine(String reply) {
        assertThat(AiGuardrails.withoutPlayerCalledAntonio(reply))
                .isEqualTo("Your crops are multi-line.\n\nWater them.");
    }
}
