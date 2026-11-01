package com.abhishek.resumeanalyzer.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single employment entry extracted from a candidate's resume.
 *
 * <p>Years are stored as integers because the LLM extracts them far more
 * reliably from free-form resume text than exact dates, and job matching only
 * needs year-level granularity.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmploymentEntry {

    /** Company / organisation the candidate worked for. */
    private String company;

    /** Job title held at the company. */
    private String title;

    /** Start year of the employment (nullable if not stated). */
    private Integer startYear;

    /** End year of the employment; {@code null} indicates the current role. */
    private Integer endYear;

    /**
     * Approximate number of years spent at the company, derived from the
     * start/end years.  Uses the current year when {@code endYear} is null.
     */
    @JsonIgnore
    public Integer getDurationYears() {
        if (startYear == null) {
            return null;
        }
        int end = endYear != null ? endYear : java.time.Year.now().getValue();
        return Math.max(0, end - startYear + 1);
    }
}
