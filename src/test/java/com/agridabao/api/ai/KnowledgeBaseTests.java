package com.agridabao.api.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseTests {

    private static final String OLD_STORE = "fileSearchStores/old-guide";
    private static final String NEW_STORE = "fileSearchStores/new-guide";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> uploadedTitles = Collections.synchronizedList(new ArrayList<>());
    private final List<String> uploadedBodies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> deletedStores = Collections.synchronizedList(new ArrayList<>());
    private final List<String> createdStores = Collections.synchronizedList(new ArrayList<>());
    private final List<JsonNode> generateRequests = Collections.synchronizedList(new ArrayList<>());

    private HttpServer server;
    private String base;
    private volatile boolean rejectFileSearch;
    private volatile boolean sourceOffline;

    @BeforeEach
    void startFakeGemini() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeGemini() {
        server.stop(0);
    }

    @Test
    void buildsTheStoreFromTheDocumentAndRemovesTheOlderOne() throws Exception {
        KnowledgeBase knowledge = syncedKnowledgeBase();

        KnowledgeStatusResponse status = knowledge.status();
        assertThat(status.ready()).isTrue();
        assertThat(status.state()).isEqualTo("READY");
        assertThat(status.documents()).isEqualTo(8);
        assertThat(status.problem()).isNull();

        assertThat(createdStores).hasSize(1);
        assertThat(createdStores.get(0)).startsWith(KnowledgeBase.STORE_PREFIX);
        assertThat(uploadedTitles).contains("Banana production guide", "Tomato pests and diseases").hasSize(8);
        assertThat(uploadedBodies).anyMatch(body -> body.contains("### Tomato pest: Aphids"));
        assertThat(deletedStores).containsExactly(OLD_STORE);

        assertThat(knowledge.storeFor(null)).isEqualTo(NEW_STORE);
        assertThat(knowledge.storeFor(true)).isEqualTo(NEW_STORE);
        assertThat(knowledge.storeFor(false)).isNull();
    }

    @Test
    void doesNotUploadAgainWhenTheStoreAlreadyHoldsTheSameDocument() throws Exception {
        syncedKnowledgeBase();
        int uploadsAfterFirstStart = uploadedTitles.size();

        KnowledgeBase restarted = syncedKnowledgeBase();

        assertThat(restarted.status().ready()).isTrue();
        assertThat(uploadedTitles).hasSize(uploadsAfterFirstStart);
        assertThat(createdStores).hasSize(1);
    }

    @Test
    void keepsTheEarlierStoreWhenTheDocumentCannotBeRead() throws Exception {
        syncedKnowledgeBase();
        sourceOffline = true;

        KnowledgeBase restarted = syncedKnowledgeBase();

        assertThat(restarted.status().ready()).isTrue();
        assertThat(restarted.storeFor(null)).isEqualTo(NEW_STORE);
        assertThat(restarted.status().problem()).startsWith("Kept the earlier field guide");
    }

    @Test
    void asksGeminiWithTheStoreAndNamesTheSourcesItUsed() throws Exception {
        KnowledgeBase knowledge = syncedKnowledgeBase();
        GeminiClient gemini = new GeminiClient("test-key", "gemini-2.5-flash", base);

        GeminiClient.Result result = gemini.generate("Be Antonio.", List.of("How do I space bananas?"),
                new GeminiClient.GenerationConfig(0.4, 0.9, 3000, false), knowledge.storeFor(true));

        JsonNode request = generateRequests.get(0);
        assertThat(request.path("tools").path(0).path("file_search").path("file_search_store_names").path(0).asText())
                .isEqualTo(NEW_STORE);
        assertThat(result.text()).isEqualTo("Plant them about four metres apart.");
        assertThat(result.sources()).containsExactly("Banana production guide");
        assertThat(knowledge.describeSources(result.sources())).containsExactly(
                "Banana production guide (Agricultural Training Institute; "
                        + "Department of Agriculture, High Value Crops Development Program)");

        GeminiClient.Result plain = gemini.generate("Be Antonio.", List.of("Hello"),
                new GeminiClient.GenerationConfig(0.4, 0.9, 3000, false));
        assertThat(generateRequests.get(1).has("tools")).isFalse();
        assertThat(plain.sources()).isEmpty();
    }

    @Test
    void answersWithoutTheStoreAndPausesItWhenGeminiRejectsFileSearch() throws Exception {
        KnowledgeBase knowledge = syncedKnowledgeBase();
        GeminiClient gemini = new GeminiClient("test-key", "gemini-2.5-flash", base);
        AiService service = new AiService(gemini, knowledge, true);
        rejectFileSearch = true;

        AiGenerateResponse response = service.generate("player-1", new AiGenerateRequest(
                AiFeature.ADVISOR, "Keep it short.", List.of("Farm facts.", "How do I space bananas?"), true));

        assertThat(response.available()).isTrue();
        assertThat(response.text()).isEqualTo("Plant them about four metres apart.");
        assertThat(response.sources()).isEmpty();
        assertThat(generateRequests).hasSize(2);
        assertThat(generateRequests.get(0).has("tools")).isTrue();
        assertThat(generateRequests.get(1).has("tools")).isFalse();

        assertThat(knowledge.storeFor(true)).isNull();
        assertThat(knowledge.status().state()).isEqualTo("PAUSED");
        assertThat(knowledge.status().problem()).contains("file_search is not supported");
    }

    @Test
    void usesTheFieldGuideWithoutPrintingSourcesInTheAnswer() throws Exception {
        KnowledgeBase knowledge = syncedKnowledgeBase();
        AiService service = new AiService(new GeminiClient("test-key", "gemini-2.5-flash", base), knowledge, true);

        AiGenerateResponse grounded = service.generate("player-1", new AiGenerateRequest(
                AiFeature.ADVISOR, null, List.of("How do I space bananas?"), null));
        AiGenerateResponse switchedOff = service.generate("player-1", new AiGenerateRequest(
                AiFeature.ADVISOR, null, List.of("How do I space bananas?"), false));
        AiGenerateResponse task = service.generate("player-1", new AiGenerateRequest(
                AiFeature.TASK_GENERATE, "Return JSON.", List.of("Make a task."), true));

        assertThat(grounded.text()).isEqualTo("Plant them about four metres apart.");
        assertThat(grounded.sources()).containsExactly(
                "Banana production guide (Agricultural Training Institute; "
                        + "Department of Agriculture, High Value Crops Development Program)");
        assertThat(generateRequests.get(0).path("systemInstruction").path("parts").path(0).path("text").asText())
                .contains("7. FIELD GUIDE");

        assertThat(switchedOff.text()).isEqualTo("Plant them about four metres apart.");
        assertThat(generateRequests.get(1).has("tools")).isFalse();
        assertThat(generateRequests.get(1).path("systemInstruction").path("parts").path(0).path("text").asText())
                .doesNotContain("FIELD GUIDE");

        assertThat(task.sources()).isEmpty();
        assertThat(generateRequests.get(2).has("tools")).isFalse();
    }

    private KnowledgeBase syncedKnowledgeBase() throws InterruptedException {
        KnowledgeBase knowledge = new KnowledgeBase(
                new GeminiFileSearchClient("test-key", base), true, true, base + "/doc");
        knowledge.startSyncing();

        long deadline = System.currentTimeMillis() + 20_000;
        while (!"READY".equals(knowledge.status().state()) && !"FAILED".equals(knowledge.status().state())
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        return knowledge;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        try {
            if (path.equals("/doc")) {
                if (sourceOffline) {
                    respond(exchange, 404, "text/html", "<html>Sign in</html>");
                } else {
                    respond(exchange, 200, "text/plain; charset=utf-8", KnowledgeDocumentsTests.SAMPLE);
                }
            } else if (path.equals("/v1beta/fileSearchStores") && method.equals("GET")) {
                StringBuilder stores = new StringBuilder("{\"fileSearchStores\":[");
                if (!deletedStores.contains(OLD_STORE)) {
                    stores.append("{\"name\":\"").append(OLD_STORE)
                            .append("\",\"displayName\":\"").append(KnowledgeBase.STORE_PREFIX).append("older\"},");
                }
                stores.append("{\"name\":\"fileSearchStores/someone-elses\",\"displayName\":\"unrelated\"}");
                for (String created : createdStores) {
                    stores.append(",{\"name\":\"").append(NEW_STORE)
                            .append("\",\"displayName\":\"").append(created).append("\"}");
                }
                respond(exchange, 200, "application/json", stores.append("]}").toString());
            } else if (path.equals("/v1beta/fileSearchStores") && method.equals("POST")) {
                createdStores.add(mapper.readTree(body).path("displayName").asText());
                respond(exchange, 200, "application/json", "{\"name\":\"" + NEW_STORE + "\"}");
            } else if (path.equals("/v1beta/" + NEW_STORE + "/documents") && method.equals("GET")) {
                StringBuilder documents = new StringBuilder("{\"documents\":[");
                synchronized (uploadedBodies) {
                    for (int i = 0; i < uploadedBodies.size(); i++) {
                        documents.append(i == 0 ? "" : ",")
                                .append("{\"name\":\"").append(NEW_STORE).append("/documents/d").append(i)
                                .append("\",\"displayName\":").append(mapper.writeValueAsString(uploadedTitles.get(i)))
                                .append(",\"state\":\"STATE_ACTIVE\"}");
                    }
                }
                respond(exchange, 200, "application/json", documents.append("]}").toString());
            } else if (path.equals("/v1beta/" + OLD_STORE + "/documents") && method.equals("GET")) {
                respond(exchange, 200, "application/json", "{}");
            } else if (path.equals("/upload/v1beta/" + NEW_STORE + ":uploadToFileSearchStore")) {
                assertThat(exchange.getRequestHeaders().getFirst("X-Goog-Upload-Command")).isEqualTo("start");
                assertThat(exchange.getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("test-key");
                uploadedTitles.add(mapper.readTree(body).path("displayName").asText());
                exchange.getResponseHeaders().add("X-Goog-Upload-URL", base + "/upload-session");
                respond(exchange, 200, "application/json", "{}");
            } else if (path.equals("/upload-session")) {
                assertThat(exchange.getRequestHeaders().getFirst("X-Goog-Upload-Command"))
                        .isEqualTo("upload, finalize");
                uploadedBodies.add(body);
                respond(exchange, 200, "application/json",
                        "{\"name\":\"" + NEW_STORE + "/upload/operations/op\"}");
            } else if (method.equals("DELETE") && path.startsWith("/v1beta/fileSearchStores/")) {
                assertThat(exchange.getRequestURI().getQuery()).isEqualTo("force=true");
                deletedStores.add(path.substring("/v1beta/".length()));
                respond(exchange, 200, "application/json", "{}");
            } else if (path.equals("/v1beta/models/gemini-2.5-flash:generateContent")) {
                JsonNode request = mapper.readTree(body);
                generateRequests.add(request);
                if (request.has("tools") && rejectFileSearch) {
                    respond(exchange, 400, "application/json",
                            "{\"error\":{\"code\":400,\"message\":\"file_search is not supported for this model\"}}");
                } else if (request.has("tools")) {
                    respond(exchange, 200, "application/json", """
                            {"candidates":[{"content":{"parts":[{"text":"Plant them about four metres apart."}]},
                              "finishReason":"STOP",
                              "groundingMetadata":{
                                "groundingChunks":[
                                  {"retrievedContext":{"title":"Banana production guide","text":"..."}},
                                  {"retrievedContext":{"title":"Corn production guide","text":"..."}}],
                                "groundingSupports":[
                                  {"segment":{"startIndex":0,"endIndex":10},"groundingChunkIndices":[0]}]}}]}
                            """);
                } else {
                    respond(exchange, 200, "application/json", """
                            {"candidates":[{"content":{"parts":[{"text":"Plant them about four metres apart."}]},
                              "finishReason":"STOP"}]}
                            """);
                }
            } else {
                respond(exchange, 404, "application/json",
                        "{\"error\":{\"message\":\"unexpected " + method + " " + path + "\"}}");
            }
        } catch (AssertionError | RuntimeException ex) {
            respond(exchange, 500, "application/json",
                    "{\"error\":{\"message\":" + mapper.writeValueAsString(String.valueOf(ex.getMessage())) + "}}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
