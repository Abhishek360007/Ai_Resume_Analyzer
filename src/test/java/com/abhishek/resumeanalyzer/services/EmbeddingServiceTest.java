package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.ResumeEmbedding;
import com.abhishek.resumeanalyzer.repos.ResumeEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmbeddingService.
 * Tests vector embedding generation and chunking logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService Unit Tests")
class EmbeddingServiceTest {

    @Mock
    private ResumeEmbeddingRepository embeddingRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private DirectEmbeddingClient directEmbeddingClient;

    @InjectMocks
    private EmbeddingService embeddingService;

    private Candidate mockCandidate;
    private String sampleResumeContent;

    @BeforeEach
    void setUp() {
        mockCandidate = Candidate.builder()
                .id(UUID.randomUUID())
                .name("John Doe")
                .email("john@example.com")
                .build();

        sampleResumeContent = """
            JOHN DOE
            Senior Software Engineer
            john.doe@email.com | (555) 123-4567
            
            PROFESSIONAL EXPERIENCE
            
            Senior Software Engineer at Tech Corp (2019-2024)
            Led development of microservices architecture using Spring Boot and Kubernetes.
            Mentored team of 5 developers and implemented CI/CD pipelines.
            
            Software Engineer at StartupXYZ (2017-2019)
            Developed RESTful APIs and implemented database optimization strategies.
            
            SKILLS
            
            Programming: Java, Python, JavaScript
            Frameworks: Spring Boot, React, Node.js
            Cloud: AWS, Kubernetes, Docker
            Databases: PostgreSQL, MongoDB, Redis
            
            EDUCATION
            
            Master of Science in Computer Science
            Massachusetts Institute of Technology, 2017
            
            Bachelor of Science in Computer Science
            Stanford University, 2015
            """;

        // Set the batch size using reflection
        ReflectionTestUtils.setField(embeddingService, "batchSize", 10);
    }

    @Test
    @DisplayName("Should generate and store embeddings successfully")
    void shouldGenerateAndStoreEmbeddingsSuccessfully() {
        // Given - Return the right number of embeddings based on batch size
        when(embeddingModel.embedForResponse(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<Embedding> embeddingResults = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                embeddingResults.add(new Embedding(createMockEmbedding(), i));
            }
            return new EmbeddingResponse(embeddingResults);
        });

        // EmbeddingService uses insertEmbeddingNative (void) instead of save()
        doNothing().when(embeddingRepository).insertEmbeddingNative(
                any(), any(), any(), any(), any());

        // When
        List<ResumeEmbedding> results = embeddingService.generateAndStoreEmbeddings(
                mockCandidate, sampleResumeContent);

        // Then
        assertThat(results).isNotEmpty();
        verify(embeddingRepository).deleteByCandidate(mockCandidate);
        verify(embeddingModel, atLeastOnce()).embedForResponse(anyList());
        verify(embeddingRepository, atLeast(3)).insertEmbeddingNative(
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should delete existing embeddings before generating new ones")
    void shouldDeleteExistingEmbeddings() {
        // Given - Return the right number of embeddings based on batch size
        when(embeddingModel.embedForResponse(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<Embedding> embeddingResults = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                embeddingResults.add(new Embedding(createMockEmbedding(), i));
            }
            return new EmbeddingResponse(embeddingResults);
        });

        doNothing().when(embeddingRepository).insertEmbeddingNative(
                any(), any(), any(), any(), any());

        // When
        embeddingService.generateAndStoreEmbeddings(mockCandidate, sampleResumeContent);

        // Then
        verify(embeddingRepository).deleteByCandidate(mockCandidate);
    }

    @Test
    @DisplayName("Should identify section types correctly")
    void shouldIdentifySectionTypesCorrectly() {
        // Given
        String resumeWithSections = """
            EDUCATION
            Master of Science in Computer Science, MIT
            
            EXPERIENCE
            Senior Software Engineer at Tech Corp
            
            SKILLS
            Java, Spring Boot, Kubernetes
            
            PROJECTS
            Built a microservices platform
            
            CERTIFICATIONS
            AWS Certified Solutions Architect
            """;

        float[] mockEmbedding = createMockEmbedding();
        List<Embedding> embeddingResults = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            embeddingResults.add(new Embedding(mockEmbedding, i));
        }
        EmbeddingResponse mockResponse = new EmbeddingResponse(embeddingResults);

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);
        doNothing().when(embeddingRepository).insertEmbeddingNative(
                any(), any(), any(), any(), any());

        // When
        List<ResumeEmbedding> results = embeddingService.generateAndStoreEmbeddings(
                mockCandidate, resumeWithSections);

        // Then
        verify(embeddingRepository, atLeast(5)).insertEmbeddingNative(
                any(), any(), any(), any(), any());

        // Verify section types in the returned list
        assertThat(results).extracting(ResumeEmbedding::getSectionType)
                .contains("education", "experience", "skills");
    }

    @Test
    @DisplayName("Should chunk large sections into smaller pieces")
    void shouldChunkLargeSections() {
        // Given
        StringBuilder largeSection = new StringBuilder("EXPERIENCE\n\n");
        for (int i = 0; i < 50; i++) {
            largeSection.append("Worked on project ").append(i).append(". ");
        }
        String resumeWithLargeSection = largeSection.toString();

        // Return the right number of embeddings based on batch size
        when(embeddingModel.embedForResponse(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<Embedding> embeddingResults = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                embeddingResults.add(new Embedding(createMockEmbedding(), i));
            }
            return new EmbeddingResponse(embeddingResults);
        });

        doNothing().when(embeddingRepository).insertEmbeddingNative(
                any(), any(), any(), any(), any());

        // When
        List<ResumeEmbedding> results = embeddingService.generateAndStoreEmbeddings(
                mockCandidate, resumeWithLargeSection);

        // Then - Large section should be split into multiple chunks
        assertThat(results.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should process embeddings in batches")
    void shouldProcessEmbeddingsInBatches() {
        // Given
        ReflectionTestUtils.setField(embeddingService, "batchSize", 2);

        StringBuilder longResume = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            longResume.append("Section ").append(i).append("\n\n");
            longResume.append("Content for section ").append(i).append("\n\n");
        }

        float[] mockEmbedding = createMockEmbedding();
        List<Embedding> batchResults = List.of(
                new Embedding(mockEmbedding, 0),
                new Embedding(mockEmbedding, 1)
        );
        EmbeddingResponse mockResponse = new EmbeddingResponse(batchResults);

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);
        doNothing().when(embeddingRepository).insertEmbeddingNative(
                any(), any(), any(), any(), any());

        // When
        embeddingService.generateAndStoreEmbeddings(mockCandidate, longResume.toString());

        // Then - Should process in multiple batches
        verify(embeddingModel, atLeast(2)).embedForResponse(anyList());
    }

    @Test
    @DisplayName("Should generate query embedding successfully")
    void shouldGenerateQueryEmbeddingSuccessfully() {
        // Given
        String queryText = "Java Spring Boot developer with 5 years experience";
        float[] mockEmbedding = createMockEmbedding();
        EmbeddingResponse mockResponse = new EmbeddingResponse(
                List.of(new Embedding(mockEmbedding, 0))
        );

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);

        // When
        String result = embeddingService.generateQueryEmbedding(queryText);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).startsWith("[");
        assertThat(result).endsWith("]");
        verify(embeddingModel).embedForResponse(List.of(queryText));
    }

    @Test
    @DisplayName("Should handle empty resume content")
    void shouldHandleEmptyResumeContent() {
        // Given
        String emptyContent = "";

        // When
        List<ResumeEmbedding> results = embeddingService.generateAndStoreEmbeddings(
                mockCandidate, emptyContent);

        // Then
        assertThat(results).isEmpty();
        verify(embeddingRepository).deleteByCandidate(mockCandidate);
    }

    @Test
    @DisplayName("Should convert float array to vector string correctly")
    void shouldConvertFloatArrayToVectorString() {
        // Given
        String queryText = "test query";
        float[] embedding = new float[EmbeddingService.VECTOR_DIMENSIONS];
        embedding[0] = 0.1f;
        embedding[4] = 0.5f;
        EmbeddingResponse mockResponse = new EmbeddingResponse(
                List.of(new Embedding(embedding, 0))
        );

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);

        // When
        String result = embeddingService.generateQueryEmbedding(queryText);

        // Then
        assertThat(result).contains("0.1");
        assertThat(result).contains("0.5");
        assertThat(result).matches("\\[.*\\]"); // Should be array format
    }

    @Test
    @DisplayName("Should associate embeddings with correct candidate")
    void shouldAssociateEmbeddingsWithCandidate() {
        // Given
        float[] mockEmbedding = createMockEmbedding();
        EmbeddingResponse mockResponse = new EmbeddingResponse(
                List.of(new Embedding(mockEmbedding, 0))
        );

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);
        doNothing().when(embeddingRepository).insertEmbeddingNative(
                any(), any(), any(), any(), any());

        // When
        List<ResumeEmbedding> results = embeddingService.generateAndStoreEmbeddings(
                mockCandidate, "Test content");

        // Then - verify candidateId is passed to insertEmbeddingNative
        ArgumentCaptor<String> candidateIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingRepository, atLeastOnce()).insertEmbeddingNative(
                any(), candidateIdCaptor.capture(), any(), any(), any());

        assertThat(candidateIdCaptor.getValue())
                .isEqualTo(mockCandidate.getId().toString());
    }

    /**
     * Helper method to create a mock embedding vector
     */
    private float[] createMockEmbedding() {
        float[] embedding = new float[EmbeddingService.VECTOR_DIMENSIONS]; // Matches the vector(768) column
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.random() - 0.5);
        }
        return embedding;
    }

    @Test
    @DisplayName("Keeps vectors that already match the column size")
    void fitToColumnSize_keepsExactSize() {
        float[] vector = new float[EmbeddingService.VECTOR_DIMENSIONS];
        vector[0] = 1f;

        assertThat(EmbeddingService.fitToColumnSize(vector)).isSameAs(vector);
    }

    @Test
    @DisplayName("Truncates and re-normalises longer vectors such as Gemini's 3072 dimensions")
    void fitToColumnSize_truncatesAndNormalises() {
        float[] vector = new float[3072];
        java.util.Arrays.fill(vector, 0.5f);

        float[] fitted = EmbeddingService.fitToColumnSize(vector);

        assertThat(fitted).hasSize(EmbeddingService.VECTOR_DIMENSIONS);
        double norm = 0;
        for (float v : fitted) {
            norm += v * v;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    @DisplayName("Rejects vectors shorter than the column size with a clear message")
    void fitToColumnSize_rejectsShorterVectors() {
        assertThatThrownBy(() -> EmbeddingService.fitToColumnSize(new float[384]))
                .isInstanceOf(com.abhishek.resumeanalyzer.exceptions.LlmServiceException.class)
                .hasMessageContaining("384 dimensions");
    }

    @Test
    @DisplayName("Falls back to a direct request when Spring AI fails on a missing usage block")
    void usesDirectClientWhenUsageIsMissing() {
        float[] vector = new float[EmbeddingService.VECTOR_DIMENSIONS];
        vector[0] = 1f;
        when(embeddingModel.embedForResponse(anyList()))
                .thenThrow(new NullPointerException("Cannot invoke \"Usage.promptTokens()\" because \"usage\" is null"));
        when(directEmbeddingClient.embed(List.of("query text"))).thenReturn(List.of(vector));

        String result = embeddingService.generateQueryEmbedding("query text");

        assertThat(result).startsWith("[1.0, 0.0");
        verify(embeddingModel, never()).embed(anyString());
    }
}
