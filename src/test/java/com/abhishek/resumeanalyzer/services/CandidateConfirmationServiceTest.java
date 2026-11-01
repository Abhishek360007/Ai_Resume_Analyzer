package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.models.CandidateStatus;
import com.abhishek.resumeanalyzer.models.ConfirmCandidateInput;
import com.abhishek.resumeanalyzer.repos.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CandidateConfirmationService}.
 *
 * <p>Verifies the confirmation lifecycle: corrections are applied, the
 * candidate is activated, social profiles are enriched, and company impressions
 * are ensured — all failing softly so a confirmed candidate is never lost.</p>
 */
@ExtendWith(MockitoExtension.class)
class CandidateConfirmationServiceTest {

    @Mock CandidateRepository candidateRepository;
    @Mock CandidateProfileEnrichmentService enrichmentService;
    @Mock CompanyResearchService companyResearchService;

    CandidateConfirmationService service;

    Candidate candidate;
    UUID candidateId;

    @BeforeEach
    void setUp() {
        service = new CandidateConfirmationService(candidateRepository, enrichmentService, companyResearchService);
        candidateId = UUID.randomUUID();
        candidate = Candidate.builder()
                .id(candidateId)
                .name("Jane Doe")
                .email("jane@example.com")
                .status(CandidateStatus.PENDING_CONFIRMATION)
                .build();
    }

    @Test
    @DisplayName("Activates a pending candidate and runs the intake workflow")
    void activatesAndRunsIntakeWorkflow() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(Candidate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Candidate result = service.confirmCandidate(candidateId, ConfirmCandidateInput.builder()
                .linkedInUrl("https://linkedin.com/in/jane")
                .build());

        assertThat(result.getStatus()).isEqualTo(CandidateStatus.ACTIVE);
        assertThat(result.getLinkedInUrl()).isEqualTo("https://linkedin.com/in/jane");
        verify(candidateRepository).save(candidate);
        verify(enrichmentService).enrichFromUrl(eq(candidateId), eq("https://linkedin.com/in/jane"));
        verify(companyResearchService).ensureImpressions(candidate);
    }

    @Test
    @DisplayName("Throws when candidate does not exist")
    void throwsWhenCandidateNotFound() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmCandidate(candidateId, ConfirmCandidateInput.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    @Test
    @DisplayName("Throws when candidate is not awaiting confirmation")
    void throwsWhenNotPending() {
        candidate.setStatus(CandidateStatus.ACTIVE);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.confirmCandidate(candidateId, ConfirmCandidateInput.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not awaiting confirmation");
        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    @Test
    @DisplayName("Applies only the corrections provided in the input")
    void appliesProvidedCorrections() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(Candidate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.confirmCandidate(candidateId, ConfirmCandidateInput.builder()
                .name("Jane A. Doe")
                .yearsOfExperience(7)
                .build());

        assertThat(candidate.getName()).isEqualTo("Jane A. Doe");
        assertThat(candidate.getYearsOfExperience()).isEqualTo(7);
        assertThat(candidate.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Ignores blank corrections and keeps existing values")
    void ignoresBlankCorrections() {
        candidate.setLinkedInUrl("https://linkedin.com/in/existing");
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(Candidate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.confirmCandidate(candidateId, ConfirmCandidateInput.builder()
                .name("   ")
                .linkedInUrl("   ")
                .build());

        assertThat(candidate.getName()).isEqualTo("Jane Doe");
        assertThat(candidate.getLinkedInUrl()).isEqualTo("https://linkedin.com/in/existing");
    }

    @Test
    @DisplayName("Still confirms the candidate when social enrichment fails")
    void survivesEnrichmentFailure() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(Candidate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(enrichmentService.enrichFromUrl(eq(candidateId), eq("https://github.com/jane")))
                .thenThrow(new RuntimeException("GitHub unreachable"));

        Candidate result = service.confirmCandidate(candidateId, ConfirmCandidateInput.builder()
                .githubUrl("https://github.com/jane")
                .build());

        assertThat(result.getStatus()).isEqualTo(CandidateStatus.ACTIVE);
    }

    @Test
    @DisplayName("Still confirms the candidate when company research fails")
    void survivesCompanyResearchFailure() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(Candidate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(companyResearchService.ensureImpressions(candidate))
                .thenThrow(new RuntimeException("Research error"));

        Candidate result = service.confirmCandidate(candidateId, ConfirmCandidateInput.builder().build());

        assertThat(result.getStatus()).isEqualTo(CandidateStatus.ACTIVE);
        verify(enrichmentService, never()).enrichFromUrl(eq(candidateId), any());
    }
}
