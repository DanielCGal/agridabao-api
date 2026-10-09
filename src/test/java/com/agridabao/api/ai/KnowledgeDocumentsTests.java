package com.agridabao.api.ai;

import com.agridabao.api.ai.KnowledgeDocuments.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KnowledgeDocumentsTests {

    private static final String FILLER =
            "Plenty of ordinary guidance text follows so that the section is long enough to keep. ".repeat(4);

    static final String SAMPLE = String.join("\n",
            "﻿“DAVAO CLIMATE / WEATHER CALENDAR”",
            "JANUARY",
            "* Rainfall: A moderately wet month (13 days, 117mm). " + FILLER,
            "References:",
            "Weather Atlas. (2024). Davao climate. https://www.weather-atlas.com/en/philippines/davao",
            "“Crops Pest & Disease Davao City”",
            "\tTOMATO",
            "\tWhen They Commonly Appear",
            "\tPossible Trigger / Cause of Appearance",
            "\tMitigation / Control Methods",
            "\tPEST:",
            "Aphids",
            "\tAphid attacks typically peak from March to May. " + FILLER,
            "\t• Presence of young, soft leaves and shoots.",
            "• Excessive nitrogen fertilizer.",
            "\t• Remove weeds around farms.",
            "\tDISEASES:",
            "Bacterial Wilt",
            "\tCommon in the wet months. " + FILLER,
            "\tCROPS:",
            "\tSQUASH",
            "\tPEST:",
            "Fruit Fly",
            "\tMost active while the fruit sets. " + FILLER,
            "Agricultural Crop Production Notes: Davao City",
            "Source: City Government of Davao Comprehensive Land Use Plan (CLUP), Vol. 3",
            "1. General Classification of Crops Planted. " + FILLER,
            "Climate Events and Recommended Mitigation Measures (AgriDabaw-3D)",
            "Banana",
            "Heavy rain, Panama disease. " + FILLER,
            "CACAO",
            "\tMaterial Required or Needed:",
            "1. Cacao Seed - Starting material for growing the cacao plant. " + FILLER,
            "BANANA PRODUCTION NOTES",
            "1. BANANA PRODUCTION OVERVIEW",
            "Saba and Cardava are the common cooking bananas. " + FILLER,
            "4. SOIL REQUIREMENTS",
            "Deep, well-drained loam. " + FILLER,
            "REFERENCES",
            "Agricultural Training Institute. (n.d.). Banana production manual. Department of Agriculture.",
            "Department of Agriculture, High Value Crops Development Program. (2022). Pagtanum og saging.",
            "Paderes, N. M., & Batoon, G. P. (2026). Optimizing Cardava banana propagation.",
            "MAIS (CORN) PRODUCTION",
            "1. CORN OVERVIEW",
            "Corn is grown in upland areas. " + FILLER,
            "");

    @Test
    void splitsTheFieldGuideByTopicAndCrop() {
        List<KnowledgeDocument> documents = KnowledgeDocuments.split(SAMPLE);

        assertThat(documents).extracting(KnowledgeDocument::title).containsExactly(
                "Davao climate and weather calendar",
                "Tomato pests and diseases",
                "Squash pests and diseases",
                "Davao City crop production notes (CLUP Volume 3)",
                "Climate events and mitigation measures by month and crop",
                "Cacao growth cycle and planting steps",
                "Banana production guide",
                "Corn production guide");
    }

    @Test
    void labelsEachPestRowWithItsCropAndKind() {
        String tomato = find("Tomato pests and diseases").text();

        assertThat(tomato)
                .contains("### Tomato pest: Aphids")
                .contains("### Tomato disease: Bacterial Wilt")
                .contains("Remove weeds around farms.")
                .doesNotContain("Fruit Fly")
                .doesNotContain("Mitigation / Control Methods");
    }

    @Test
    void namesTheCropInEveryGuideHeadingAndKeepsItsReferences() {
        KnowledgeDocument banana = find("Banana production guide");

        assertThat(banana.text())
                .contains("## Banana: 4. SOIL REQUIREMENTS")
                .doesNotContain("CORN OVERVIEW");
        assertThat(banana.references()).containsExactly(
                "Agricultural Training Institute",
                "Department of Agriculture, High Value Crops Development Program",
                "Paderes et al.");
        assertThat(find("Davao City crop production notes (CLUP Volume 3)").references())
                .containsExactly("City Government of Davao Comprehensive Land Use Plan (CLUP), Vol. 3");
    }

    @Test
    void theSameTextAlwaysGetsTheSameContentId() {
        String first = KnowledgeDocuments.contentId(KnowledgeDocuments.split(SAMPLE));
        String second = KnowledgeDocuments.contentId(KnowledgeDocuments.split(SAMPLE));
        String changed = KnowledgeDocuments.contentId(
                KnowledgeDocuments.split(SAMPLE.replace("Deep, well-drained loam", "Shallow sandy soil")));

        assertThat(first).hasSize(12).isEqualTo(second).isNotEqualTo(changed);
    }

    @Test
    void anUnrecognisedDocumentIsStillSplitIntoParts() {
        List<KnowledgeDocument> documents = KnowledgeDocuments.split(("Some notes. " + FILLER + "\n").repeat(400));

        assertThat(documents).isNotEmpty();
        assertThat(documents.get(0).title()).isEqualTo("AgriDabaw field guide, part 1");
    }

    @Test
    void printsTheOutlineOfARealExportWhenOneIsGiven() throws Exception {
        String path = System.getenv("KNOWLEDGE_SAMPLE");
        assumeTrue(path != null && !path.isBlank());

        List<KnowledgeDocument> documents =
                KnowledgeDocuments.split(Files.readString(Path.of(path), StandardCharsets.UTF_8));

        StringBuilder outline = new StringBuilder();
        int total = 0;
        for (KnowledgeDocument document : documents) {
            total += document.text().length();
            outline.append(String.format("%7d chars  %-58s %s%n",
                    document.text().length(), document.title(), document.references()));
        }
        outline.append(String.format("%d documents, %d chars, content id %s%n",
                documents.size(), total, KnowledgeDocuments.contentId(documents)));

        Files.writeString(Path.of(path + ".outline.txt"), outline.toString(), StandardCharsets.UTF_8);
        assertThat(documents.size()).isGreaterThan(20);
    }

    private static KnowledgeDocument find(String title) {
        return KnowledgeDocuments.split(SAMPLE).stream()
                .filter(document -> document.title().equals(title))
                .findFirst()
                .orElseThrow();
    }
}
