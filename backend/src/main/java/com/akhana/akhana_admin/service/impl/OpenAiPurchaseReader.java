package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.service.PurchaseReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// COM-20: the Responses API, with no retries so no call is charged without being counted. Logs carry the failure type
// only: never the key, the text or the answer. store: false asks OpenAI not to keep the answer (CA-27)
@Component
@Slf4j
public class OpenAiPurchaseReader implements PurchaseReader {

    private static final Map<String, Object> TEXT_OR_NULL = Map.of("type", List.of("string", "null"));
    private static final Map<String, Object> NUMBER_OR_NULL = Map.of("type", List.of("number", "null"));
    private static final Map<String, Object> BOOLEAN = Map.of("type", "boolean");

    private static final Map<String, Object> LINE_SCHEMA = Map.of(
        "type", "object",
        "additionalProperties", false,
        "required", List.of("product", "quantity", "quantity_text", "quantity_doubtful", "unit_cost", "line_amount", "note"),
        "properties", Map.of(
            "product", TEXT_OR_NULL,
            "quantity", NUMBER_OR_NULL,
            "quantity_text", TEXT_OR_NULL,
            "quantity_doubtful", BOOLEAN,
            "unit_cost", NUMBER_OR_NULL,
            "line_amount", NUMBER_OR_NULL,
            "note", TEXT_OR_NULL));

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
        "type", "object",
        "additionalProperties", false,
        "required", List.of("supplier", "multiple_suppliers", "loose_note", "paid_note", "lines", "shipping", "total"),
        "properties", Map.of(
            "supplier", TEXT_OR_NULL,
            "multiple_suppliers", BOOLEAN,
            "loose_note", BOOLEAN,
            "paid_note", BOOLEAN,
            "lines", Map.of("type", "array", "items", LINE_SCHEMA),
            "shipping", NUMBER_OR_NULL,
            "total", NUMBER_OR_NULL));

    private final JsonMapper json = JsonMapper.builder().build();
    private final String apiKey;
    private final String model;
    private final RestClient client;
    private final String textPrompt = prompt("purchase-reading/text-prompt.txt");
    private final String photoPrompt = prompt("purchase-reading/photo-prompt.txt");

    public OpenAiPurchaseReader(@Value("${app.openai.api-key:}") String apiKey,
                                @Value("${app.openai.model:gpt-6.1-terra}") String model,
                                @Value("${app.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                @Value("${app.openai.timeout:50s}") Duration timeout) {
        this.apiKey = apiKey;
        this.model = model;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public ReadOutcome readText(String text) {
        return read(List.of(
            Map.of("type", "input_text", "text", textPrompt),
            Map.of("type", "input_text", "text", text)));
    }

    @Override
    public ReadOutcome readPhoto(byte[] jpeg) {
        return read(List.of(
            Map.of("type", "input_text", "text", photoPrompt),
            Map.of("type", "input_image", "detail", "high",
                "image_url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg))));
    }

    private ReadOutcome read(List<Map<String, Object>> content) {
        Map<String, Object> body = Map.of(
            "model", model,
            "store", false,
            "input", List.of(Map.of("role", "user", "content", content)),
            "text", Map.of("format", Map.of("type", "json_schema", "name", "purchase", "schema", RESPONSE_SCHEMA, "strict", true)));
        String answer;
        try {
            answer = client.post().uri("/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(body))
                .retrieve()
                .body(String.class);
        } catch (RestClientException ex) {
            log.warn("The purchase reading call failed: {}", ex.getClass().getSimpleName());
            return ReadOutcome.failed(null, null);
        }
        return parseAnswer(answer);
    }

    private ReadOutcome parseAnswer(String answer) {
        Long inputTokens = null;
        Long outputTokens = null;
        try {
            JsonNode response = json.readTree(answer);
            JsonNode usage = response.path("usage");
            inputTokens = usage.path("input_tokens").isNumber() ? usage.path("input_tokens").longValue() : null;
            outputTokens = usage.path("output_tokens").isNumber() ? usage.path("output_tokens").longValue() : null;
            String result = outputText(response);
            if (!"completed".equals(response.path("status").asString("")) || result.isEmpty()) {
                log.warn("The purchase reading answer was not complete");
                return ReadOutcome.failed(inputTokens, outputTokens);
            }
            return new ReadOutcome(parsePurchase(json.readTree(result)), result, inputTokens, outputTokens);
        } catch (JacksonException | IllegalArgumentException ex) {
            log.warn("The purchase reading answer was invalid: {}", ex.getClass().getSimpleName());
            return ReadOutcome.failed(inputTokens, outputTokens);
        }
    }

    private static String outputText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode output : response.path("output")) {
            if ("message".equals(output.path("type").asString(""))) {
                for (JsonNode item : output.path("content")) {
                    if ("output_text".equals(item.path("type").asString(""))) {
                        text.append(item.path("text").asString(""));
                    }
                }
            }
        }
        return text.toString();
    }

    private static ReadPurchase parsePurchase(JsonNode node) {
        if (!node.path("lines").isArray()) {
            throw new IllegalArgumentException("Answer without lines");
        }
        List<ReadLine> lines = new ArrayList<>();
        for (JsonNode line : node.path("lines")) {
            lines.add(new ReadLine(
                text(line.path("product")),
                number(line.path("quantity")),
                text(line.path("quantity_text")),
                line.path("quantity_doubtful").asBoolean(false),
                number(line.path("unit_cost")),
                number(line.path("line_amount")),
                text(line.path("note"))));
        }
        return new ReadPurchase(
            text(node.path("supplier")),
            node.path("multiple_suppliers").asBoolean(false),
            node.path("loose_note").asBoolean(false),
            node.path("paid_note").asBoolean(false),
            lines,
            number(node.path("shipping")),
            number(node.path("total")));
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString().trim() : null;
    }

    private static BigDecimal number(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : null;
    }

    private static String prompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
