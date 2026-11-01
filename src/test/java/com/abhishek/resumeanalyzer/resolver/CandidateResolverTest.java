package com.abhishek.resumeanalyzer.resolver;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.repos.CandidateRepository;
import com.abhishek.resumeanalyzer.services.CandidateConfirmationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CandidateResolver.
 * Tests GraphQL queries and mutations for candidates.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateResolver Unit Tests")
class CandidateResolverTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private CandidateConfirmationService confirmationService;

    @InjectMocks
    private CandidateResolver candidateResolver;

    private UUID candidateId;
    private Candidate mockCandidate;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();
        mockCandidate = Candidate.builder()
                .id(candidateId)
                .name("John Doe")
                .email("john.doe@email.com")
                .mobile("555-1234")
                .skills("Java, Spring Boot, Kubernetes")
                .experience(5)
                .education("Master of Science in Computer Science")
                .currentCompany("Tech Corp")
                .build();
    }

    @Test
    @DisplayName("Should fetch candidate by ID successfully")
    void shouldFetchCandidateById() {
        // Given
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(mockCandidate));

        // When
        Candidate result = candidateResolver.candidate(candidateId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(candidateId);
        assertThat(result.getName()).isEqualTo("John Doe");
        verify(candidateRepository).findById(candidateId);
    }

    @Test
    @DisplayName("Should throw exception when candidate not found")
    void shouldThrowExceptionWhenCandidateNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(candidateRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> candidateResolver.candidate(nonExistentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Candidate not found");
        verify(candidateRepository).findById(nonExistentId);
    }

    @Test
    @DisplayName("Should fetch all candidates successfully")
    void shouldFetchAllCandidates() {
        // Given
        Candidate candidate2 = Candidate.builder()
                .id(UUID.randomUUID())
                .name("Jane Smith")
                .email("jane@email.com")
                .build();

        List<Candidate> allCandidates = List.of(mockCandidate, candidate2);
        when(candidateRepository.findAll()).thenReturn(allCandidates);

        // When
        List<Candidate> results = candidateResolver.allCandidates();

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).containsExactly(mockCandidate, candidate2);
        verify(candidateRepository).findAll();
    }

    @Test
    @DisplayName("Should return empty list when no candidates exist")
    void shouldReturnEmptyListWhenNoCandidates() {
        // Given
        when(candidateRepository.findAll()).thenReturn(List.of());

        // When
        List<Candidate> results = candidateResolver.allCandidates();

        // Then
        assertThat(results).isEmpty();
        verify(candidateRepository).findAll();
    }

    @Test
    @DisplayName("Should search candidates by name successfully")
    void shouldSearchCandidatesByName() {
        // Given
        String searchName = "John";
        when(candidateRepository.searchByName(searchName)).thenReturn(List.of(mockCandidate));

        // When
        List<Candidate> results = candidateResolver.searchCandidatesByName(searchName);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).contains("John");
        verify(candidateRepository).searchByName(searchName);
    }

    @Test
    @DisplayName("Should search candidates by skill successfully")
    void shouldSearchCandidatesBySkill() {
        // Given
        String skill = "Java";
        when(candidateRepository.findBySkillsContaining(skill)).thenReturn(List.of(mockCandidate));

        // When
        List<Candidate> results = candidateResolver.searchCandidatesBySkill(skill);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSkills()).contains("Java");
        verify(candidateRepository).findBySkillsContaining(skill);
    }

    @Test
    @DisplayName("Should return empty list when no candidates match skill search")
    void shouldReturnEmptyListWhenNoSkillMatch() {
        // Given
        String skill = "Rust";
        when(candidateRepository.findBySkillsContaining(skill)).thenReturn(List.of());

        // When
        List<Candidate> results = candidateResolver.searchCandidatesBySkill(skill);

        // Then
        assertThat(results).isEmpty();
        verify(candidateRepository).findBySkillsContaining(skill);
    }

    @Test
    @DisplayName("Should delete candidate successfully")
    void shouldDeleteCandidateSuccessfully() {
        // Given
        doNothing().when(candidateRepository).deleteById(candidateId);

        // When
        Boolean result = candidateResolver.deleteCandidate(candidateId);

        // Then
        assertThat(result).isTrue();
        verify(candidateRepository).deleteById(candidateId);
    }

    @Test
    @DisplayName("Should handle delete of non-existent candidate")
    void shouldHandleDeleteOfNonExistentCandidate() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        doNothing().when(candidateRepository).deleteById(nonExistentId);

        // When
        Boolean result = candidateResolver.deleteCandidate(nonExistentId);

        // Then
        assertThat(result).isTrue();
        verify(candidateRepository).deleteById(nonExistentId);
    }

    @Test
    @DisplayName("Should discard candidate by delegating to confirmation service")
    void shouldDiscardCandidateSuccessfully() {
        // When
        Boolean result = candidateResolver.discardCandidate(candidateId);

        // Then
        assertThat(result).isTrue();
        verify(confirmationService).discardCandidate(candidateId);
    }

    @Test
    @DisplayName("Should propagate error when discarding non-pending candidate")
    void shouldPropagateDiscardError() {
        // Given
        doThrow(new IllegalArgumentException("Only pending candidates can be discarded"))
                .when(confirmationService).discardCandidate(candidateId);

        // When/Then
        assertThatThrownBy(() -> candidateResolver.discardCandidate(candidateId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only pending candidates");
        verify(confirmationService).discardCandidate(candidateId);
    }
}
