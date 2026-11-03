package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.service.PurchaseReader.ReadOutcome;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// CA-24, CA-27: the real HTTP call against a local server, never against OpenAI
@ExtendWith(OutputCaptureExtension.class)
class OpenAiPurchaseReaderTest {

    private static final String KEY = "sk-test-secret-key";
    private static final String ANSWER = """
        {"status":"completed",
         "usage":{"input_tokens":1200,"output_tokens":150},
         "output":[{"type":"message","content":[{"type":"output_text",
           "text":"{\\"supplier\\":\\"Flora\\",\\"multiple_suppliers\\":false,\\"loose_note\\":true,\\"paid_note\\":true,\\"lines\\":[{\\"product\\":\\"Jamaica\\",\\"quantity\\":2,\\"quantity_text\\":\\"2\\",\\"quantity_doubtful\\":false,\\"unit_cost\\":12.5,\\"line_amount\\":null,\\"note\\":null}],\\"shipping\\":15,\\"total\\":40}"}]}]}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void answers(int status, String body) {
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private OpenAiPurchaseReader reader(String key, Duration timeout) {
        return new OpenAiPurchaseReader(key, "gpt-6.1-terra", "http://localhost:" + server.getAddress().getPort() + "/v1", timeout);
    }

    private OpenAiPurchaseReader reader() {
        return reader(KEY, Duration.ofSeconds(5));
    }

    @Test
    void readsATextWithStoreFalseAndTheKeyOnlyInTheHeader() {
        answers(200, ANSWER);

        ReadOutcome outcome = reader().readText("2 jamaica");

        assertThat(outcome.purchase().supplier()).isEqualTo("Flora");
        assertThat(outcome.purchase().looseNote()).isTrue();
        assertThat(outcome.purchase().paidNote()).isTrue();
        assertThat(outcome.purchase().lines()).singleElement().satisfies(line -> {
            assertThat(line.product()).isEqualTo("Jamaica");
            assertThat(line.quantity()).isEqualByComparingTo("2");
            assertThat(line.unitCost()).isEqualByComparingTo("12.5");
            assertThat(line.lineAmount()).isNull();
        });
        assertThat(outcome.purchase().shipping()).isEqualByComparingTo("15");
        assertThat(outcome.inputTokens()).isEqualTo(1200L);
        assertThat(outcome.outputTokens()).isEqualTo(150L);
        assertThat(outcome.rawResult()).contains("Jamaica");

        JsonNode sent = json.readTree(requestBody.get());
        assertThat(sent.path("store").asBoolean(true)).isFalse();
        assertThat(sent.path("model").asString()).isEqualTo("gpt-6.1-terra");
        assertThat(sent.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(authorization.get()).isEqualTo("Bearer " + KEY);
        assertThat(requestBody.get()).doesNotContain(KEY).contains("2 jamaica");
    }

    @Test
    void sendsThePhotoAsAJpegDataUrl() {
        answers(200, ANSWER);

        reader().readPhoto(new byte[] {1, 2, 3});

        JsonNode content = json.readTree(requestBody.get()).path("input").get(0).path("content");
        assertThat(content.get(1).path("type").asString()).isEqualTo("input_image");
        assertThat(content.get(1).path("image_url").asString())
            .isEqualTo("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
    }

    @Test
    void anHttpErrorIsAFailureWithoutRetriesOrTheKeyInTheLogs(CapturedOutput output) {
        answers(500, "{\"error\":\"boom " + KEY + "\"}");

        ReadOutcome outcome = reader().readText("2 jamaica privado");

        assertThat(outcome.purchase()).isNull();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(KEY).doesNotContain("2 jamaica privado");
    }

    @Test
    void anInvalidAnswerFailsButKeepsTheUsage() {
        answers(200, """
            {"status":"completed","usage":{"input_tokens":10,"output_tokens":5},
             "output":[{"type":"message","content":[{"type":"output_text","text":"no es json"}]}]}""");

        ReadOutcome outcome = reader().readText("x");

        assertThat(outcome.purchase()).isNull();
        assertThat(outcome.inputTokens()).isEqualTo(10L);
        assertThat(outcome.outputTokens()).isEqualTo(5L);
    }

    @Test
    void anIncompleteAnswerIsAFailure() {
        answers(200, """
            {"status":"incomplete","usage":{"input_tokens":10,"output_tokens":5},"output":[]}""");

        assertThat(reader().readText("x").purchase()).isNull();
    }

    @Test
    void aTimeoutIsAFailure() {
        server.createContext("/v1/responses", exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        assertThat(reader(KEY, Duration.ofMillis(100)).readText("x").purchase()).isNull();
    }

    @Test
    void isOffWithoutTheKey() {
        assertThat(reader("", Duration.ofSeconds(1)).isConfigured()).isFalse();
        assertThat(reader(KEY, Duration.ofSeconds(1)).isConfigured()).isTrue();
    }
}
