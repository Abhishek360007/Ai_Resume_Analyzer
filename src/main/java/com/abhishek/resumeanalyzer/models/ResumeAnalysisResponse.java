package com.abhishek.resumeanalyzer.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model object for Resume Analysis response from LLM.
 * Contains extracted candidate information and AI-generated summaries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysisResponse {

    /**
     * Extracted candidate name
     */
    private String name;

    /**
     * Extracted email address
     */
    private String email;

    /**
     * Extracted mobile number
     */
    private String mobile;

    /**
     * AI-generated experience summary
     */
    private String experienceSummary;

    /**
     * Identified skills (comma-separated)
     */
    @JsonDeserialize(using = ArrayOrStringDeserializer.class)
    private String skills;

    /**
     * Domain knowledge summary
     */
    @JsonDeserialize(using = ArrayOrStringDeserializer.class)
    private String domainKnowledge;

    /**
     * Academic background summary
     */
    @JsonDeserialize(using = ArrayOrStringDeserializer.class)
    private String academicBackground;

    /**
     * Calculated years of experience
     */
    private Integer yearsOfExperience;

    /**
     * Confidence score of the analysis (0-1)
     */
    private Double confidenceScore;

    /**
     * Employment history extracted from the resume (company, title, years).
     */
    @JsonDeserialize(using = WorkHistoryDeserializer.class)
    private java.util.List<EmploymentEntry> workHistory;

    /**
     * LinkedIn profile URL found in the resume, if any.
     */
    private String linkedInUrl;

    /**
     * GitHub profile URL found in the resume, if any.
     */
    private String githubUrl;

    /**
     * Twitter / X profile URL found in the resume, if any.
     */
    private String twitterUrl;

    /**
     * True when this is a placeholder produced because the LLM call failed,
     * so callers must not persist it as a real result.
     */
    @JsonIgnore
    @Builder.Default
    private boolean fallback = false;

    /** Why the LLM call failed (provider error message), when {@link #fallback} is true. */
    @JsonIgnore
    private String failureReason;
}
