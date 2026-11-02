package com.abhishek.resumeanalyzer.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.abhishek.resumeanalyzer.config.AiPromptsProperties;
import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.ExternalProfileSource;
import com.abhishek.resumeanalyzer.entities.JobRequirement;
import com.abhishek.resumeanalyzer.exceptions.LlmServiceException;
import com.abhishek.resumeanalyzer.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for AI/LLM interactions using the Spring AI {@link ChatClient}.
 *
 * <p>Prompt templates are externalised in {@code ai-prompts.yml} and injected via
 * {@link AiPromptsProperties}, so prompts can be updated without touching Java source code.</p>
 *
 * <p>All LLM calls request a structured JSON response. Jackson ({@link ObjectMapper})
 * deserialises the JSON into strongly-typed Java objects, keeping the rest of the codebase
 * free of raw JSON string manipulation.</p>
 *
 * <p>Error-handling strategy:
 * <ul>
 *   <li>Network / API failures are caught and wrapped in {@link LlmServiceException}.</li>
 *   <li>JSON parse failures fall back to a safe default response so the caller can
 *       continue without crashing the upload / match pipeline.</li>
 *   <li>All errors are logged with enough context for operational debugging.</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class AIService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiPromptsProperties prompts;

    public AIService(ChatClient.Builder chatClientBuilder,
                     ObjectMapper objectMapper,
                     AiPromptsProperties prompts) {
        // Build a default ChatClient; per-call options (temperature, max-tokens) are
        // applied inline so that each LLM task uses its own tuning parameters.
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.prompts = prompts;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Analyse resume content using the LLM to extract candidate information.
     *
     * @param request Resume analysis request containing file name and raw text.
     * @return Populated {@link ResumeAnalysisResponse}; a safe fallback is returned
     *         when the LLM is unavailable or returns unparseable content.
     */
    public ResumeAnalysisResponse analyzeResume(ResumeAnalysisRequest request) {
        log.info("Analysing resume: {}", request.getFilename());
        try {
            String userPrompt = prompts.getResumeAnalysis().render(
                    "resumeContent", request.getResumeContent()
            );

            String rawResponse = callLlm(
                    prompts.getResumeAnalysis().getSystem(),
                    userPrompt,
                    /* temperature */ 0.3f,
                    /* maxTokens  */ 3000
            );

            log.info("LLM resume-analysis response length: {}", rawResponse != null ? rawResponse.length() : "null");
            return parseResumeAnalysisResponse(rawResponse);
        } catch (LlmServiceException e) {
            log.warn("LLM unavailable during resume analysis — returning fallback. Reason: {}", e.getMessage());
            return ResumeAnalysisResponse.builder()
                    .failureReason(summarizeError(e))
                    .name("Unknown")
                    .email("")
                    .mobile("")
                    .experienceSummary("Resume processed (AI analysis unavailable)")
                    .skills("")
                    .domainKnowledge("")
                    .academicBackground("")
                    .yearsOfExperience(0)
                    .confidenceScore(0.0)
                    .fallback(true)
                    .build();
        }
    }

    /**
     * Generate a match score between a candidate and a job requirement using the LLM.
     *
     * @param request Candidate vs. job match request.
     * @return Populated {@link CandidateMatchResponse}; a safe fallback is returned
     *         when the LLM is unavailable or returns unparseable content.
     */
    public CandidateMatchResponse matchCandidate(CandidateMatchRequest request) {
        log.info("Matching candidate for job: {}", request.getJobTitle());
        try {
            String enrichedSection = buildEnrichedSection(request.getEnrichedProfileContext());
            String resumeContextSection = buildResumeContextSection(request.getRelevantResumeContext());
            String companyImpressionsSection = buildCompanyImpressionsSection(request.getCompanyImpressionsContext());

            String userPrompt = prompts.getCandidateMatching().render(
                    "experienceSummary",  nullSafe(request.getExperienceSummary()),
                    "skills",             nullSafe(request.getSkills()),
                    "domainKnowledge",    nullSafe(request.getDomainKnowledge()),
                    "academicBackground", nullSafe(request.getAcademicBackground()),
                    "yearsOfExperience",  String.valueOf(request.getYearsOfExperience() != null ? request.getYearsOfExperience() : 0),
                    "enrichedSection",    enrichedSection,
                    "resumeContextSection",    resumeContextSection,
                    "companyImpressionsSection", companyImpressionsSection,
                    "jobTitle",           nullSafe(request.getJobTitle()),
                    "jobDescription",     nullSafe(request.getJobDescription()),
                    "requiredSkills",     nullSafe(request.getRequiredSkills()),
                    "requiredEducation",  nullSafe(request.getRequiredEducation()),
                    "domainRequirements", nullSafe(request.getDomainRequirements()),
                    "minExperience",      String.valueOf(request.getMinExperienceYears() != null ? request.getMinExperienceYears() : 0),
                    "maxExperience",      String.valueOf(request.getMaxExperienceYears() != null ? request.getMaxExperienceYears() : 100)
            );

            String rawResponse = callLlm(
                    prompts.getCandidateMatching().getSystem(),
                    userPrompt,
                    /* temperature */ 0.2f,
                    /* maxTokens  */ 2000
            );

            log.debug("LLM candidate-matching raw response: {}", rawResponse);
            return parseMatchingResponse(rawResponse);
        } catch (LlmServiceException e) {
            log.warn("LLM unavailable during candidate matching — returning fallback. Reason: {}", e.getMessage());
            return CandidateMatchResponse.builder()
                    .failureReason(summarizeError(e))
                    .matchScore(0.0)
                    .skillsScore(0.0)
                    .experienceScore(0.0)
                    .educationScore(0.0)
                    .domainScore(0.0)
                    .explanation("AI matching temporarily unavailable — please retry shortly.")
                    .strengths("")
                    .gaps("")
                    .recommendation("Error in Analysis")
                    .fallback(true)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Company due-diligence impressions
    // -------------------------------------------------------------------------

    /**
     * Generates an AI impression of a company based on internet search snippets.
     *
     * <p>Used by the company-research pipeline step.  Falls back to an
     * {@link CompanyVerdict#UNKNOWN} impression when the LLM is unavailable or
     * returns unparseable content, so the intake pipeline is never blocked.</p>
     *
     * @param companyName   the company to assess
     * @param searchResults raw internet search snippets for the company
     * @return populated {@link CompanyImpressionResponse}
     */
    public CompanyImpressionResponse analyzeCompany(String companyName, String searchResults) {
        log.info("Generating company impression for: {}", companyName);
        try {
            String userPrompt = prompts.getCompanyImpression().render(
                    "companyName",   nullSafe(companyName),
                    "searchResults", nullSafe(searchResults)
            );

            String rawResponse = callLlm(
                    prompts.getCompanyImpression().getSystem(),
                    userPrompt,
                    /* temperature */ 0.1f,
                    /* maxTokens  */ 500
            );

            String json = extractJson(rawResponse);
            CompanyImpressionResponse response = objectMapper.readValue(json, CompanyImpressionResponse.class);
            if (response.getVerdict() == null) {
                response.setVerdict(CompanyVerdict.UNKNOWN);
            }
            return response;
        } catch (Exception e) {
            log.warn("Company impression generation failed for {} ({}); defaulting to UNKNOWN",
                    companyName, e.getMessage());
            return CompanyImpressionResponse.builder()
                    .verdict(CompanyVerdict.UNKNOWN)
                    .summary("No impression available — company research failed.")
                    .confidenceScore(0.0)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Agentic source selection
    // -------------------------------------------------------------------------

    /**
     * Uses the LLM to decide which external data sources are most relevant for
     * the given candidate+job combination before a match request is processed.
     *
     * <p>This is the agentic decision-making step. The LLM reasons about the job
     * requirements and candidate profile to recommend which sources (GitHub, LinkedIn,
     * Twitter, INTERNET_SEARCH) to fetch before scoring begins.</p>
     *
     * <p>Falls back to {@code [INTERNET_SEARCH]} on any failure so the pipeline
     * is never blocked by an LLM error.</p>
     *
     * @param candidate the candidate being evaluated
     * @param job       the target job requirement
     * @return ordered list of recommended {@link ExternalProfileSource} values
     */
    @SuppressWarnings("unchecked")
    public List<ExternalProfileSource> selectEnrichmentSources(Candidate candidate, JobRequirement job) {
        log.info("[SOURCE-SELECT] Selecting enrichment sources for {} vs job '{}'",
                candidate.getName(), job.getTitle());
        try {
            String userPrompt = prompts.getSourceSelection().render(
                    "candidateSkills",   nullSafe(candidate.getSkills()),
                    "experienceSummary", nullSafe(candidate.getExperienceSummary()),
                    "yearsOfExperience", String.valueOf(candidate.getYearsOfExperience() != null ? candidate.getYearsOfExperience() : 0),
                    "jobTitle",          nullSafe(job.getTitle()),
                    "requiredSkills",    nullSafe(job.getRequiredSkills()),
                    "jobDescription",    nullSafe(job.getDescription())
            );

            String rawResponse = callLlm(
                    prompts.getSourceSelection().getSystem(),
                    userPrompt,
                    /* temperature */ 0.1f,
                    /* maxTokens  */ 300
            );

            String json = extractJson(rawResponse);
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> sourceNames = (List<String>) parsed.getOrDefault("sources", List.of("INTERNET_SEARCH"));
            String reasoning = (String) parsed.getOrDefault("reasoning", "");
            log.info("[SOURCE-SELECT] Recommended {} for {}: {}", sourceNames, candidate.getName(), reasoning);

            return sourceNames.stream()
                    .map(name -> {
                        try { return ExternalProfileSource.valueOf(name); }
                        catch (IllegalArgumentException e) { return null; }
                    })
                    .filter(s -> s != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("[SOURCE-SELECT] Failed ({}); defaulting to INTERNET_SEARCH", e.getMessage());
            return List.of(ExternalProfileSource.INTERNET_SEARCH);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — LLM communication
    // -------------------------------------------------------------------------

    /**
     * Calls the configured LLM with the given system and user messages.
     *
     * <p>Per-call {@link OpenAiChatOptions} allow each task to use its own
     * temperature / token budget without altering global application configuration.</p>
     *
     * @param systemMessage Persona / contract instruction for the LLM.
     * @param userMessage   The rendered prompt to send.
     * @param temperature   Sampling temperature (lower = more deterministic).
     * @param maxTokens     Maximum tokens in the response.
     * @return Raw LLM response text, or {@code null} on unrecoverable failure.
     * @throws LlmServiceException if the LLM returns an empty or null response.
     */
    private String callLlm(String systemMessage, String userMessage, float temperature, int maxTokens) {
        try {
            log.debug("Calling LLM — temperature={}, maxTokens={}", temperature, maxTokens);

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature((double) temperature)
                    .maxTokens(maxTokens)
                    .build();

            String content = chatClient.prompt()
                    .system(systemMessage)
                    .user(userMessage)
                    .options(options)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                throw new LlmServiceException("LLM returned an empty response");
            }

            return content;

        } catch (LlmServiceException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = "LLM API call failed: " + e.getMessage();
            log.error(errorMsg, e);
            throw new LlmServiceException(errorMsg, e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — response parsing
    // -------------------------------------------------------------------------

    /**
     * Parse the LLM response for resume analysis into a typed Java object.
     * Returns a safe fallback when the LLM is unreachable or the response is malformed.
     */
    private ResumeAnalysisResponse parseResumeAnalysisResponse(String rawResponse) {
        String reason;
        try {
            String json = extractJson(rawResponse);
            return objectMapper.readValue(json, ResumeAnalysisResponse.class);
        } catch (LlmServiceException e) {
            log.warn("LLM unavailable during resume analysis — returning fallback. Reason: {}", e.getMessage());
            reason = summarizeError(e);
        } catch (Exception e) {
            log.error("Failed to parse LLM resume-analysis response. Raw response: {}", rawResponse, e);
            reason = "the AI reply was not valid JSON";
        }
        return ResumeAnalysisResponse.builder()
                .failureReason(reason)
                .name("Unknown")
                .email("")
                .mobile("")
                .experienceSummary("Resume processed (AI analysis unavailable)")
                .skills("")
                .domainKnowledge("")
                .academicBackground("")
                .yearsOfExperience(0)
                .confidenceScore(0.0)
                .fallback(true)
                .build();
    }

    /**
     * Parse the LLM response for candidate matching into a typed Java object.
     * Returns a safe fallback when the LLM is unreachable or the response is malformed.
     */
    private CandidateMatchResponse parseMatchingResponse(String rawResponse) {
        String reason;
        try {
            String json = extractJson(rawResponse);
            return objectMapper.readValue(json, CandidateMatchResponse.class);
        } catch (LlmServiceException e) {
            log.warn("LLM unavailable during candidate matching — returning fallback. Reason: {}", e.getMessage());
            reason = summarizeError(e);
        } catch (Exception e) {
            log.error("Failed to parse LLM candidate-matching response. Raw response: {}", rawResponse, e);
            reason = "the AI reply was not valid JSON";
        }
        return CandidateMatchResponse.builder()
                .failureReason(reason)
                .matchScore(0.0)
                .skillsScore(0.0)
                .experienceScore(0.0)
                .educationScore(0.0)
                .domainScore(0.0)
                .explanation("AI matching temporarily unavailable — please retry shortly.")
                .strengths("")
                .gaps("")
                .recommendation("Error in Analysis")
                .fallback(true)
                .build();
    }

    /**
     * Condense an LLM failure into one line a user can act on. Provider errors usually
     * embed a JSON body such as {"error": {"message": "API key not valid"}}; its message
     * is far more useful than the full exception text.
     */
    private static final java.util.regex.Pattern PROVIDER_MESSAGE =
            java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final java.util.regex.Pattern HTTP_STATUS_PREFIX = java.util.regex.Pattern.compile("^(\\d{3})\\b");
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");

    static String summarizeError(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String text = String.valueOf(root.getMessage() != null ? root.getMessage() : error.getMessage());
        java.util.regex.Matcher providerMessage = PROVIDER_MESSAGE.matcher(text);
        String summary = providerMessage.find() ? providerMessage.group(1) : text;
        java.util.regex.Matcher status = HTTP_STATUS_PREFIX.matcher(text.trim());
        if (status.find() && !summary.startsWith(status.group(1))) {
            summary = "HTTP " + status.group(1) + ": " + summary;
        }
        summary = WHITESPACE.matcher(summary).replaceAll(" ").trim();
        return summary.length() > 240 ? summary.substring(0, 237) + "..." : summary;
    }

    /**
     * Extract the first complete JSON object from an LLM response string.
     *
     * <p>Some LLMs wrap their JSON in markdown code fences or add introductory text.
     * This method strips that noise by locating the outermost {@code { … }} block.</p>
     *
     * @param response Raw LLM response.
     * @return Clean JSON string.
     * @throws LlmServiceException if no JSON object can be detected.
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new LlmServiceException("LLM returned null or empty response — cannot extract JSON");
        }

        // Strip markdown code fences that some models emit: ```json ... ```
        String cleaned = response.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replaceAll("```", "").strip();

        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        throw new LlmServiceException(
                "No JSON object found in LLM response. Response starts with: "
                + response.substring(0, Math.min(200, response.length())));
    }

    // -------------------------------------------------------------------------
    // Private helpers — misc
    // -------------------------------------------------------------------------

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    private static String buildEnrichedSection(String enrichedProfileContext) {
        if (enrichedProfileContext == null || enrichedProfileContext.isBlank()) {
            return "";
        }
        return "\nEXTERNAL PROFILE DATA (from GitHub / LinkedIn / Internet):\n"
                + enrichedProfileContext + "\n";
    }

    private static String buildResumeContextSection(String relevantResumeContext) {
        if (relevantResumeContext == null || relevantResumeContext.isBlank()) {
            return "";
        }
        return "\nRELEVANT RESUME EVIDENCE (retrieved from the candidate's resume embeddings):\n"
                + relevantResumeContext + "\n";
    }

    private static String buildCompanyImpressionsSection(String companyImpressionsContext) {
        if (companyImpressionsContext == null || companyImpressionsContext.isBlank()) {
            return "";
        }
        return "\nCOMPANY IMPRESSIONS (due-diligence on the candidate's past employers):\n"
                + companyImpressionsContext + "\n";
    }
}
