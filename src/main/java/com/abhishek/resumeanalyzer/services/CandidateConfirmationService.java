package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.models.CandidateStatus;
import com.abhishek.resumeanalyzer.models.ConfirmCandidateInput;
import com.abhishek.resumeanalyzer.repos.CandidateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Confirms AI-extracted candidates once an HR / TAG / recruiting manager has
 * reviewed the extracted details and (optionally) supplied social profile URLs.
 *
 * <p>Confirmation activates the candidate and triggers the downstream intake
 * workflow that was deferred while awaiting review:</p>
 * <ol>
 *   <li>Apply any corrected fields provided by the reviewer.</li>
 *   <li>Store social profile URLs and auto-enrich from them where an enricher
 *       exists (GitHub, LinkedIn, Twitter).</li>
 *   <li>Research the candidate's past employers and build / reuse company
 *       impressions in the database.</li>
 *   <li>Mark the candidate {@link CandidateStatus#ACTIVE} so they become
 *       eligible for job matching.</li>
 * </ol>
 */
@Service
@Slf4j
public class CandidateConfirmationService {

    private final CandidateRepository candidateRepository;
    private final CandidateProfileEnrichmentService enrichmentService;
    private final CompanyResearchService companyResearchService;

    public CandidateConfirmationService(CandidateRepository candidateRepository,
                                        CandidateProfileEnrichmentService enrichmentService,
                                        CompanyResearchService companyResearchService) {
        this.candidateRepository = candidateRepository;
        this.enrichmentService = enrichmentService;
        this.companyResearchService = companyResearchService;
    }

    /**
     * Confirm a pending candidate.
     *
     * @param candidateId candidate awaiting confirmation
     * @param input       optional corrections + social profile URLs
     * @return the activated candidate
     */
    @Transactional
    public Candidate confirmCandidate(UUID candidateId, ConfirmCandidateInput input) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        if (candidate.getStatus() != CandidateStatus.PENDING_CONFIRMATION) {
            throw new IllegalArgumentException("Candidate is not awaiting confirmation: " + candidateId);
        }

        applyCorrections(candidate, input);

        // Persist corrected details + ACTIVE status before side effects
        candidate.setStatus(CandidateStatus.ACTIVE);
        candidate = candidateRepository.save(candidate);
        log.info("Candidate confirmed: id={}, name={}", candidate.getId(), candidate.getName());

        enrichSocialProfiles(candidate, input);

        try {
            int count = companyResearchService.ensureImpressions(candidate).size();
            log.info("Company impressions ensured for {}: {}", candidate.getName(), count);
        } catch (Exception e) {
            log.warn("Company impression research failed for {} ({}): {}",
                    candidate.getName(), candidate.getId(), e.getMessage());
        }

        return candidate;
    }

    /**
     * Discard a candidate that is still awaiting confirmation.
     *
     * <p>Lets a reviewer reject an AI-extracted candidate that is wrong or
     * spam rather than being forced to confirm it. Only candidates still in
     * {@link CandidateStatus#PENDING_CONFIRMATION} can be discarded.</p>
     *
     * @param candidateId candidate awaiting confirmation
     */
    @Transactional
    public void discardCandidate(UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        if (candidate.getStatus() != CandidateStatus.PENDING_CONFIRMATION) {
            throw new IllegalArgumentException("Only pending candidates can be discarded: " + candidateId);
        }

        candidateRepository.delete(candidate);
        log.info("Candidate discarded: id={}, name={}", candidate.getId(), candidate.getName());
    }

    private void applyCorrections(Candidate candidate, ConfirmCandidateInput input) {
        if (input == null) {
            return;
        }
        if (input.getName() != null && !input.getName().isBlank()) candidate.setName(input.getName());
        if (input.getEmail() != null && !input.getEmail().isBlank()) candidate.setEmail(input.getEmail());
        if (input.getMobile() != null && !input.getMobile().isBlank()) candidate.setMobile(input.getMobile());
        if (input.getSkills() != null && !input.getSkills().isBlank()) candidate.setSkills(input.getSkills());
        if (input.getYearsOfExperience() != null) candidate.setYearsOfExperience(input.getYearsOfExperience());
        if (input.getExperienceSummary() != null && !input.getExperienceSummary().isBlank()) {
            candidate.setExperienceSummary(input.getExperienceSummary());
        }
        if (input.getLinkedInUrl() != null && !input.getLinkedInUrl().isBlank()) candidate.setLinkedInUrl(input.getLinkedInUrl());
        if (input.getGithubUrl() != null && !input.getGithubUrl().isBlank()) candidate.setGithubUrl(input.getGithubUrl());
        if (input.getTwitterUrl() != null && !input.getTwitterUrl().isBlank()) candidate.setTwitterUrl(input.getTwitterUrl());
    }

    private void enrichSocialProfiles(Candidate candidate, ConfirmCandidateInput input) {
        List<String> urls = java.util.stream.Stream.of(
                        input != null ? input.getLinkedInUrl() : null,
                        input != null ? input.getGithubUrl() : null,
                        input != null ? input.getTwitterUrl() : null,
                        candidate.getLinkedInUrl(),
                        candidate.getGithubUrl(),
                        candidate.getTwitterUrl())
                .filter(u -> u != null && !u.isBlank())
                .distinct()
                .toList();

        for (String url : urls) {
            try {
                enrichmentService.enrichFromUrl(candidate.getId(), url);
            } catch (Exception e) {
                log.warn("Social profile enrichment failed for {} from {}: {}",
                        candidate.getName(), url, e.getMessage());
            }
        }
    }
}
