package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.CandidateMatch;
import com.abhishek.resumeanalyzer.entities.JobRequirement;
import com.abhishek.resumeanalyzer.repos.CandidateMatchRepository;
import com.abhishek.resumeanalyzer.repos.JobRequirementRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MatchReportService}.
 *
 * <p>Generates real PDFs and reads them back with PDFBox to verify the
 * content, ordering, pagination and handling of unsupported characters.</p>
 */
@ExtendWith(MockitoExtension.class)
class MatchReportServiceTest {

    @Mock CandidateMatchRepository candidateMatchRepository;
    @Mock JobRequirementRepository jobRequirementRepository;

    MatchReportService service;

    UUID jobId;
    JobRequirement job;

    @BeforeEach
    void setUp() {
        service = new MatchReportService(candidateMatchRepository, jobRequirementRepository);
        jobId = UUID.randomUUID();
        job = JobRequirement.builder()
                .id(jobId)
                .title("Senior Java Developer")
                .requiredSkills("Java, Spring Boot, PostgreSQL")
                .minExperienceYears(5)
                .maxExperienceYears(10)
                .location("Bengaluru")
                .build();
    }

    @Test
    @DisplayName("Report contains job details, ranked candidates and AI explanations")
    void containsJobDetailsAndCandidates() throws Exception {
        when(jobRequirementRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(candidateMatchRepository.findByJobRequirementIdOrderByMatchScoreDesc(jobId)).thenReturn(List.of(
                match("Jane Doe", 91.0, "Strong Spring Boot background.", true, false),
                match("John Smith", 64.0, "Limited PostgreSQL experience.", false, true)));

        String text = extractText(service.generateJobMatchReport(jobId));

        assertThat(text).contains("Candidate Match Report", "Senior Java Developer",
                "Java, Spring Boot, PostgreSQL", "5 - 10 years", "Bengaluru");
        assertThat(text).contains("Candidates matched: 2", "Average score: 78%", "Top score: 91%",
                "Shortlisted: 1", "Selected: 1");
        assertThat(text).contains("Strong Spring Boot background.", "Limited PostgreSQL experience.");
        assertThat(text.indexOf("Jane Doe")).isLessThan(text.indexOf("John Smith"));
    }

    @Test
    @DisplayName("Unsupported characters such as emoji do not break generation")
    void sanitizesUnsupportedCharacters() throws Exception {
        when(jobRequirementRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(candidateMatchRepository.findByJobRequirementIdOrderByMatchScoreDesc(jobId)).thenReturn(List.of(
                match("Ravi Kumar", 80.0, "Great fit 🚀\twith\tstrong 日本語 skills", false, false)));

        String text = extractText(service.generateJobMatchReport(jobId));

        assertThat(text).contains("Ravi Kumar", "Great fit ? with strong ??? skills");
    }

    @Test
    @DisplayName("Long reports flow onto multiple pages")
    void paginatesLongReports() throws Exception {
        List<CandidateMatch> matches = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            matches.add(match("Candidate " + i, 90.0 - i, "Explanation ".repeat(60), false, false));
        }
        when(jobRequirementRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(candidateMatchRepository.findByJobRequirementIdOrderByMatchScoreDesc(jobId)).thenReturn(matches);

        try (PDDocument pdf = Loader.loadPDF(service.generateJobMatchReport(jobId))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("Page 1 of " + pdf.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("A job without matches still produces a report")
    void handlesJobWithoutMatches() throws Exception {
        when(jobRequirementRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(candidateMatchRepository.findByJobRequirementIdOrderByMatchScoreDesc(jobId)).thenReturn(List.of());

        assertThat(extractText(service.generateJobMatchReport(jobId)))
                .contains("No candidates have been matched to this job yet.");
    }

    @Test
    @DisplayName("Unknown job requirement is rejected")
    void rejectsUnknownJob() {
        when(jobRequirementRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateJobMatchReport(jobId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(jobId.toString());
    }

    private CandidateMatch match(String name, double score, String explanation,
                                 boolean selected, boolean shortlisted) {
        Candidate candidate = Candidate.builder()
                .id(UUID.randomUUID())
                .name(name)
                .email(name.toLowerCase().replace(' ', '.') + "@example.com")
                .build();
        return CandidateMatch.builder()
                .id(UUID.randomUUID())
                .candidate(candidate)
                .jobRequirement(job)
                .matchScore(score)
                .skillsScore(score)
                .experienceScore(score - 5)
                .educationScore(70.0)
                .domainScore(60.0)
                .matchExplanation(explanation)
                .isSelected(selected)
                .isShortlisted(shortlisted)
                .build();
    }

    private static String extractText(byte[] pdfBytes) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(pdf);
        }
    }
}
