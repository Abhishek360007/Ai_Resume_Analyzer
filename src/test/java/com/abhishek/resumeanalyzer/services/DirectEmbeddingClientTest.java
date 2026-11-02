package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.exceptions.LlmServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link DirectEmbeddingClient} against a mocked Gemini-style endpoint,
 * whose responses have no "usage" block.
 */
class DirectEmbeddingClientTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String PATH = "/v1beta/openai/embeddings";

    private MockRestServiceServer server;
    private DirectEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DirectEmbeddingClient(builder, BASE_URL + "/", "test-key", PATH, "gemini-embedding-001", 768);
    }

    @Test
    @DisplayName("Sends model, input and dimensions with the API key and returns vectors in input order")
    void embedsTextsInOrder() {
        server.expect(requestTo(BASE_URL + PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {"model": "gemini-embedding-001", "input": ["first", "second"], "dimensions": 768}
                        """))
                .andRespond(withSuccess("""
                        {"object": "list", "model": "gemini-embedding-001", "data": [
                          {"object": "embedding", "index": 1, "embedding": [0.3, 0.4]},
                          {"object": "embedding", "index": 0, "embedding": [0.1, 0.2]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(List.of("first", "second"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
        assertThat(vectors.get(1)).containsExactly(0.3f, 0.4f);
        server.verify();
    }

    @Test
    @DisplayName("Reports the provider's error message when the request fails")
    void reportsProviderErrors() {
        server.expect(requestTo(BASE_URL + PATH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": 404, \"message\": \"models/old-embedding is not found\"}}"));

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(LlmServiceException.class)
                .hasMessageContaining("models/old-embedding is not found");
    }
}
