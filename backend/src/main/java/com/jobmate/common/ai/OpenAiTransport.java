package com.jobmate.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.exception.CustomException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiTransport {

    private final AiSettings settings;
    private final ObjectMapper mapper;
    private final RestClient client;

    public OpenAiTransport(AiSettings settings, ObjectMapper mapper,
            @Qualifier("openAiRestClient") RestClient client) {
        this.settings = settings;
        this.mapper = mapper;
        this.client = client;
    }

    public Result request(AiPurpose purpose, String instructions, String maskedInput, JsonNode schema) {
        Map<String, Object> body = Map.of(
                "model", settings.model(),
                "store", false,
                "max_output_tokens", settings.maxOutputTokens(),
                "input", List.of(
                        Map.of("role", "developer", "content", instructions),
                        Map.of("role", "user", "content", maskedInput)),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "jobmate_" + purpose.name().toLowerCase(Locale.ROOT),
                        "strict", true, "schema", schema)));
        try {
            JsonNode response = client.post().uri("https://api.openai.com/v1/responses")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).exchange((request, result) -> {
                        HttpStatusCode code = result.getStatusCode();
                        if (code.value() == 429 || code.is5xxServerError()) {
                            throw new TransientFailure();
                        }
                        if (!code.is2xxSuccessful()) {
                            throw new CustomException(AiErrorCode.AI_UNAVAILABLE);
                        }
                        byte[] bytes = result.getBody().readNBytes(1_048_577);
                        if (bytes.length > 1_048_576) {
                            throw new CustomException(AiErrorCode.AI_INVALID_RESPONSE);
                        }
                        return mapper.readTree(bytes);
                    });
            if (response == null || !"completed".equals(response.path("status").asText())) {
                throw new CustomException(AiErrorCode.AI_INVALID_RESPONSE);
            }
            String text = null;
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("refusal".equals(content.path("type").asText())) {
                        throw new CustomException(AiErrorCode.AI_INVALID_RESPONSE);
                    }
                    if ("output_text".equals(content.path("type").asText()) && content.path("text").isTextual()) {
                        if (text != null) {
                            throw new CustomException(AiErrorCode.AI_INVALID_RESPONSE);
                        }
                        text = content.path("text").asText();
                    }
                }
            }
            if (text == null) {
                throw new CustomException(AiErrorCode.AI_INVALID_RESPONSE);
            }
            return new Result(mapper.readTree(text), response.path("usage").path("input_tokens").asLong(),
                    response.path("usage").path("output_tokens").asLong());
        } catch (ResourceAccessException exception) {
            throw new TransientFailure();
        } catch (IOException exception) {
            throw new CustomException(AiErrorCode.AI_INVALID_RESPONSE);
        }
    }

    public record Result(JsonNode output, long inputTokens, long outputTokens) {
    }

    public static class TransientFailure extends RuntimeException {
    }
}
