package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.exceptions.LlmServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls an OpenAI-compatible {@code /embeddings} endpoint directly.
 *
 * <p>Spring AI 1.0.0-M6's {@code OpenAiEmbeddingModel} throws a NullPointerException when
 * the response has no {@code usage} block, which Google Gemini's OpenAI-compatible API
 * omits. This client reads only the vectors, using the same {@code spring.ai.openai.*}
 * settings, and is used as a fallback when that bug is hit.</p>
 */
@Slf4j
@Component
public class DirectEmbeddingClient {

    private final RestClient restClient;
    private final String embeddingsPath;
    private final String model;
    private final Integer dimensions;

    public DirectEmbeddingClient(
            RestClient.Builder restClientBuilder,
            @Value("${spring.ai.openai.embedding.base-url:${spring.ai.openai.base-url:https://api.openai.com}}") String baseUrl,
            @Value("${spring.ai.openai.embedding.api-key:${spring.ai.openai.api-key:}}") String apiKey,
            @Value("${spring.ai.openai.embedding.embeddings-path:/v1/embeddings}") String embeddingsPath,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-ada-002}") String model,
            @Value("${spring.ai.openai.embedding.options.dimensions:#{null}}") Integer dimensions) {
        RestClient.Builder builder = restClientBuilder.clone().baseUrl(baseUrl.replaceAll("/+$", ""));
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.restClient = builder.build();
        this.embeddingsPath = embeddingsPath.startsWith("/") ? embeddingsPath : "/" + embeddingsPath;
        this.model = model;
        this.dimensions = dimensions;
    }

    /**
     * Embed the given texts, returning one vector per text in input order.
     *
     * @throws LlmServiceException if the request fails or the response has no vectors
     */
    @SuppressWarnings("unchecked")
    public List<float[]> embed(List<String> texts) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("input", texts);
        if (dimensions != null) {
            request.put("dimensions", dimensions);
        }

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(embeddingsPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new LlmServiceException("Embedding request failed: " + AIService.summarizeError(e), e);
        }

        Object data = response != null ? response.get("data") : null;
        if (!(data instanceof List<?> items)) {
            throw new LlmServiceException("Embedding response contained no data");
        }

        List<Map<String, Object>> sorted = new ArrayList<>();
        for (Object item : items) {
            sorted.add((Map<String, Object>) item);
        }
        sorted.sort(Comparator.comparingInt(item -> ((Number) item.getOrDefault("index", 0)).intValue()));

        List<float[]> vectors = new ArrayList<>(sorted.size());
        for (Map<String, Object> item : sorted) {
            List<Number> values = (List<Number>) item.get("embedding");
            float[] vector = new float[values == null ? 0 : values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = values.get(i).floatValue();
            }
            vectors.add(vector);
        }
        log.debug("Direct embedding call returned {} vector(s) via {}", vectors.size(), embeddingsPath);
        return vectors;
    }
}
