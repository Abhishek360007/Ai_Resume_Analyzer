package com.abhishek.resumeanalyzer.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.abhishek.resumeanalyzer.config.EnrichmentProperties;
import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.CompanyImpression;
import com.abhishek.resumeanalyzer.models.CompanyImpressionResponse;
import com.abhishek.resumeanalyzer.models.CompanyVerdict;
import com.abhishek.resumeanalyzer.models.EmploymentEntry;
import com.abhishek.resumeanalyzer.repos.CompanyImpressionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Researches the companies a candidate has worked for and builds AI-generated
 * impressions (genuine / suspicious / unknown) that are cached in the database.
 *
 * <p>Impressions are global and keyed by company name.  If a company has
 * already been researched for any candidate, the stored impression is reused
 * instead of calling the internet again.</p>
 */
@Service
@Slf4j
public class CompanyResearchService {

    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";

    private final CompanyImpressionRepository impressionRepository;
    private final AIService aiService;
    private final EnrichmentProperties enrichmentProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public CompanyResearchService(CompanyImpressionRepository impressionRepository,
                                  AIService aiService,
                                  EnrichmentProperties enrichmentProperties,
                                  ObjectMapper objectMapper) {
        this.impressionRepository = impressionRepository;
        this.aiService = aiService;
        this.enrichmentProperties = enrichmentProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Ensure every company in the candidate's employment history has an
     * impression.  Existing impressions are reused; missing ones are researched
     * via the internet and stored.  Never throws — failures are logged and the
     * candidate record is left usable.
     *
     * @return the candidate's full list of company impressions
     */
    @Transactional
    public List<CompanyImpression> ensureImpressions(Candidate candidate) {
        List<String> companies = distinctCompanies(candidate);
        if (companies.isEmpty()) {
            return List.of();
        }

        List<CompanyImpression> results = new ArrayList<>();
        for (String company : companies) {
            Optional<CompanyImpression> existing = impressionRepository.findByCompanyNameIgnoreCase(company);
            if (existing.isPresent()) {
                results.add(existing.get());
                continue;
            }

            try {
                CompanyImpression impression = researchAndStore(company);
                results.add(impression);
            } catch (Exception e) {
                log.warn("Company research failed for '{}' ({}): {}",
                        company, candidate.getName(), e.getMessage());
                results.add(CompanyImpression.builder()
                        .companyName(company)
                        .verdict(CompanyVerdict.UNKNOWN)
                        .summary("Company research failed — no impression available.")
                        .confidenceScore(0.0)
                        .build());
            }
        }
        return results;
    }

    /**
     * Internet-research a single company and persist its impression.
     * Uses Tavily web search when an API key is configured, otherwise asks the
     * LLM to reason with a locally-synthesised context.
     */
    private CompanyImpression researchAndStore(String companyName) {
        String searchResults = searchCompany(companyName);
        CompanyImpressionResponse analysis = aiService.analyzeCompany(companyName, searchResults);

        CompanyImpression impression = CompanyImpression.builder()
                .companyName(companyName)
                .industry(analysis.getIndustry())
                .verdict(analysis.getVerdict() != null ? analysis.getVerdict() : CompanyVerdict.UNKNOWN)
                .summary(analysis.getSummary())
                .confidenceScore(analysis.getConfidenceScore())
                .evidence(searchResults)
                .build();

        impressionRepository.save(impression);
        log.info("Stored company impression: {} → {}", companyName, impression.getVerdict());
        return impression;
    }

    /**
     * Returns the distinct, non-blank company names from the candidate's
     * work-history JSON.  Deduplication is case-insensitive (first casing
     * wins), matching the case-insensitive uniqueness of the cache.  Order is
     * preserved (first appearance wins).
     */
    private List<String> distinctCompanies(Candidate candidate) {
        if (candidate.getWorkHistory() == null || candidate.getWorkHistory().isBlank()) {
            return List.of();
        }
        try {
            List<EmploymentEntry> entries =
                    objectMapper.readValue(candidate.getWorkHistory(), new TypeReference<>() {});
            Map<String, String> seen = new LinkedHashMap<>();
            for (EmploymentEntry entry : entries) {
                if (entry != null && entry.getCompany() != null && !entry.getCompany().isBlank()) {
                    String company = entry.getCompany().trim();
                    seen.putIfAbsent(company.toLowerCase(), company);
                }
            }
            return new ArrayList<>(seen.values());
        } catch (Exception e) {
            log.warn("Failed to parse work history for candidate {}: {}", candidate.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Run a Tavily web search for the company, falling back to a synthesised
     * context when no API key is configured or the search fails.
     */
    @SuppressWarnings("unchecked")
    private String searchCompany(String companyName) {
        String apiKey = enrichmentProperties.getTavily().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[COMPANY-RESEARCH] No Tavily API key — synthesised context for '{}'", companyName);
            return "No web search API key configured. "
                    + "Company '" + companyName + "' could not be independently verified.";
        }

        try {
            String query = "Company \"" + companyName + "\" company profile employees website legitimacy";
            Map<String, Object> requestBody = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "max_results", 5,
                    "include_answer", true,
                    "search_depth", "basic"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            Map<String, Object> response =
                    restTemplate.postForObject(TAVILY_SEARCH_URL, request, Map.class);
            if (response == null) {
                return "No web search results returned for '" + companyName + "'.";
            }

            StringBuilder sb = new StringBuilder();
            Object answer = response.get("answer");
            if (answer instanceof String s && !s.isBlank()) {
                sb.append("Summary: ").append(s).append("\n\n");
            }
            Object results = response.get("results");
            if (results instanceof List<?> list) {
                int count = 0;
                for (Object item : list) {
                    if (count >= 5) break;
                    if (item instanceof Map<?, ?> r) {
                        Object title = r.get("title");
                        Object content = r.get("content");
                        Object url = r.get("url");
                        sb.append("Source: ").append(title).append(" (").append(url).append(")\n");
                        if (content instanceof String c && !c.isBlank()) {
                            String snippet = c.length() > 400 ? c.substring(0, 400) + "..." : c;
                            sb.append(snippet).append("\n\n");
                        }
                        count++;
                    }
                }
            }
            if (sb.length() < 30) {
                return "No useful web results for '" + companyName + "'.";
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[COMPANY-RESEARCH] Tavily search failed for '{}': {}", companyName, e.getMessage());
            return "Web search failed for '" + companyName + "'. No independent verification available.";
        }
    }

    /**
     * Convenience: build a formatted context block of all impressions for a
     * candidate's employers, used as extra LLM matching context.
     */
    public String buildImpressionsContext(Candidate candidate) {
        List<String> companies = distinctCompanies(candidate);
        if (companies.isEmpty()) {
            return "";
        }
        List<CompanyImpression> impressions =
                impressionRepository.findByCompanyNameInIgnoreCase(companies);
        if (impressions.isEmpty()) {
            return "";
        }

        return impressions.stream()
                .map(i -> String.format("- %s: [%s] %s (confidence %.0f%%)",
                        i.getCompanyName(), i.getVerdict(),
                        i.getSummary() != null ? i.getSummary() : "No summary",
                        i.getConfidenceScore() != null ? i.getConfidenceScore() * 100 : 0))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Returns all stored impressions for the companies in the candidate's
     * work history, without triggering any new research.
     */
    public List<CompanyImpression> getImpressionsForCandidate(Candidate candidate) {
        List<String> companies = distinctCompanies(candidate);
        if (companies.isEmpty()) {
            return List.of();
        }
        return impressionRepository.findByCompanyNameInIgnoreCase(companies);
    }
}
