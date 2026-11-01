package com.abhishek.resumeanalyzer.resolver;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.CompanyImpression;
import com.abhishek.resumeanalyzer.models.CandidateStatus;
import com.abhishek.resumeanalyzer.models.ConfirmCandidateInput;
import com.abhishek.resumeanalyzer.repos.CandidateRepository;
import com.abhishek.resumeanalyzer.services.CandidateConfirmationService;
import com.abhishek.resumeanalyzer.services.CompanyResearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL resolver for Candidate queries and mutations.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CandidateResolver {

    private final CandidateRepository candidateRepository;
    private final CandidateConfirmationService confirmationService;
    private final CompanyResearchService companyResearchService;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'HR')")
    public Candidate candidate(@Argument UUID id) {
        log.info("Fetching candidate: {}", id);
        return candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + id));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'HR')")
    public List<Candidate> allCandidates() {
        log.info("Fetching all candidates");
        return candidateRepository.findAll();
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'HR')")
    public List<Candidate> searchCandidatesByName(@Argument String name) {
        log.info("Searching candidates by name: {}", name);
        return candidateRepository.searchByName(name);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'HR')")
    public List<Candidate> searchCandidatesBySkill(@Argument String skill) {
        log.info("Searching candidates by skill: {}", skill);
        return candidateRepository.findBySkillsContaining(skill);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'HR')")
    public List<Candidate> pendingCandidates() {
        log.info("Fetching candidates awaiting confirmation");
        return candidateRepository.findByStatus(CandidateStatus.PENDING_CONFIRMATION);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'HR')")
    public List<CompanyImpression> companyImpressionsForCandidate(@Argument UUID candidateId) {
        log.info("Fetching company impressions for candidate: {}", candidateId);
        return candidateRepository.findById(candidateId)
                .map(companyResearchService::getImpressionsForCandidate)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + candidateId));
    }

    @SchemaMapping(typeName = "Candidate", field = "companyImpressions")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'HR')")
    public List<CompanyImpression> companyImpressions(Candidate candidate) {
        return companyResearchService.getImpressionsForCandidate(candidate);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public Candidate confirmCandidate(@Argument UUID candidateId, @Argument ConfirmCandidateInput input) {
        log.info("Confirming candidate: {}", candidateId);
        return confirmationService.confirmCandidate(candidateId, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public Boolean discardCandidate(@Argument UUID candidateId) {
        log.info("Discarding candidate: {}", candidateId);
        confirmationService.discardCandidate(candidateId);
        return true;
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Boolean deleteCandidate(@Argument UUID id) {
        log.info("Deleting candidate: {}", id);
        candidateRepository.deleteById(id);
        return true;
    }
}
