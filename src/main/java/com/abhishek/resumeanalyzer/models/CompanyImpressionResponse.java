package com.abhishek.resumeanalyzer.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured LLM response describing an impression of a company based on
 * internet search snippets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyImpressionResponse {

    /** Industry / sector of the company, or null when unknown. */
    private String industry;

    /** Verdict on the company — GENUINE, SUSPICIOUS, or UNKNOWN. */
    private CompanyVerdict verdict;

    /** Concise human-readable impression of the company. */
    private String summary;

    /** LLM confidence in the verdict (0.0 – 1.0). */
    private Double confidenceScore;
}
