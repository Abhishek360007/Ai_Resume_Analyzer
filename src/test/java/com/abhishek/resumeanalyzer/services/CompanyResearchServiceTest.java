package com.abhishek.resumeanalyzer.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.abhishek.resumeanalyzer.config.EnrichmentProperties;
import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.CompanyImpression;
import com.abhishek.resumeanalyzer.models.CompanyImpressionResponse;
import com.abhishek.resumeanalyzer.models.CompanyVerdict;
import com.abhishek.resumeanalyzer.models.EmploymentEntry;
import com.abhishek.resumeanalyzer.repos.CompanyImpressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompanyResearchService}.
 *
 * <p>The internet (Tavily) call and the LLM are mocked — these tests verify
 * the orchestration: cache reuse, research + persistence, graceful failure,
 * and context building.</p>
 */
@ExtendWith(MockitoExtension.class)
class CompanyResearchServiceTest {

    @Mock CompanyImpressionRepository impressionRepository;
    @Mock AIService aiService;

    final ObjectMapper objectMapper = new ObjectMapper();

    CompanyResearchService service;

    Candidate candidate;

    @BeforeEach
    void setUp() {
        EnrichmentProperties props = new EnrichmentProperties();
        props.getTavily().setApiKey("");
        service = new CompanyResearchService(impressionRepository, aiService, props, objectMapper);
        candidate = Candidate.builder()
                .id(UUID.randomUUID())
                .name("Jane Doe")
                .build();
    }

    private void withWorkHistory(List<EmploymentEntry> entries) {
        try {
            candidate.setWorkHistory(objectMapper.writeValueAsString(entries));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialise work history", e);
        }
    }

    @Test
    @DisplayName("Returns empty list when candidate has no work history")
    void returnsEmptyWhenNoWorkHistory() {
        assertThat(service.ensureImpressions(candidate)).isEmpty();
        assertThat(service.buildImpressionsContext(candidate)).isEmpty();
        assertThat(service.getImpressionsForCandidate(candidate)).isEmpty();
        verify(impressionRepository, never()).findByCompanyNameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Reuses cached impressions without re-researching")
    void reusesCachedImpressions() {
        withWorkHistory(List.of(new EmploymentEntry("Acme Corp", "Engineer", 2020, 2022)));

        CompanyImpression cached = CompanyImpression.builder()
                .companyName("Acme Corp")
                .verdict(CompanyVerdict.GENUINE)
                .summary("Well-known company")
                .confidenceScore(0.9)
                .build();
        when(impressionRepository.findByCompanyNameIgnoreCase("Acme Corp")).thenReturn(Optional.of(cached));

        List<CompanyImpression> results = service.ensureImpressions(candidate);

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isSameAs(cached);
        verify(aiService, never()).analyzeCompany(anyString(), anyString());
        verify(impressionRepository, never()).save(any(CompanyImpression.class));
    }

    @Test
    @DisplayName("Researches and stores missing companies with a distinct dedupe")
    void researchesAndStoresMissingCompanies() {
        withWorkHistory(List.of(
                new EmploymentEntry("Acme Corp", "Engineer", 2020, 2022),
                new EmploymentEntry("acme corp", "Sr Engineer", 2022, null),
                new EmploymentEntry("Globex", "Manager", 2018, 2020)));

        when(impressionRepository.findByCompanyNameIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
        when(aiService.analyzeCompany(eq("Acme Corp"), anyString()))
                .thenReturn(CompanyImpressionResponse.builder()
                        .industry("Software")
                        .verdict(CompanyVerdict.GENUINE)
                        .summary("Legit software company")
                        .confidenceScore(0.88)
                        .build());
        when(aiService.analyzeCompany(eq("Globex"), anyString()))
                .thenReturn(CompanyImpressionResponse.builder()
                        .verdict(CompanyVerdict.SUSPICIOUS)
                        .summary("Little public footprint")
                        .confidenceScore(0.55)
                        .build());
        when(impressionRepository.save(any(CompanyImpression.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<CompanyImpression> results = service.ensureImpressions(candidate);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(CompanyImpression::getCompanyName)
                .containsExactly("Acme Corp", "Globex");
        assertThat(results).extracting(CompanyImpression::getVerdict)
                .containsExactly(CompanyVerdict.GENUINE, CompanyVerdict.SUSPICIOUS);
        verify(impressionRepository, times(2)).save(any(CompanyImpression.class));
        verify(aiService, times(2)).analyzeCompany(anyString(), anyString());
    }

    @Test
    @DisplayName("Gracefully degrades to UNKNOWN when research or analysis fails")
    void degradesToUnknownOnFailure() {
        withWorkHistory(List.of(new EmploymentEntry("Acme Corp", "Engineer", 2020, 2022)));

        when(impressionRepository.findByCompanyNameIgnoreCase("Acme Corp")).thenReturn(Optional.empty());
        when(aiService.analyzeCompany(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        List<CompanyImpression> results = service.ensureImpressions(candidate);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVerdict()).isEqualTo(CompanyVerdict.UNKNOWN);
        assertThat(results.get(0).getConfidenceScore()).isZero();
        verify(impressionRepository, never()).save(any(CompanyImpression.class));
    }

    @Test
    @DisplayName("Analyse falls back to UNKNOWN when LLM returns no verdict")
    void defaultsMissingVerdictToUnknown() {
        withWorkHistory(List.of(new EmploymentEntry("Acme Corp", "Engineer", 2020, 2022)));

        when(impressionRepository.findByCompanyNameIgnoreCase("Acme Corp")).thenReturn(Optional.empty());
        when(aiService.analyzeCompany(anyString(), anyString()))
                .thenReturn(CompanyImpressionResponse.builder().summary("No data").build());
        when(impressionRepository.save(any(CompanyImpression.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<CompanyImpression> results = service.ensureImpressions(candidate);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVerdict()).isEqualTo(CompanyVerdict.UNKNOWN);
        assertThat(results.get(0).getSummary()).isEqualTo("No data");
    }

    @Test
    @DisplayName("Builds a formatted impressions context block")
    void buildsImpressionsContext() {
        withWorkHistory(List.of(new EmploymentEntry("Acme Corp", "Engineer", 2020, 2022)));

        when(impressionRepository.findByCompanyNameInIgnoreCase(List.of("Acme Corp")))
                .thenReturn(List.of(CompanyImpression.builder()
                        .companyName("Acme Corp")
                        .verdict(CompanyVerdict.GENUINE)
                        .summary("Legit")
                        .confidenceScore(0.9)
                        .build()));

        String context = service.buildImpressionsContext(candidate);

        assertThat(context)
                .contains("Acme Corp")
                .contains("[GENUINE]")
                .contains("Legit")
                .contains("90%");
    }

    @Test
    @DisplayName("Returns empty context when no stored impressions match")
    void emptyContextWhenNothingStored() {
        withWorkHistory(List.of(new EmploymentEntry("Acme Corp", "Engineer", 2020, 2022)));

        when(impressionRepository.findByCompanyNameInIgnoreCase(List.of("Acme Corp")))
                .thenReturn(List.of());

        assertThat(service.buildImpressionsContext(candidate)).isEmpty();
    }
}
