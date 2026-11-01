package com.abhishek.resumeanalyzer.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for confirming a candidate whose AI-extracted resume details are
 * awaiting review by an HR / TAG / recruiting manager.
 *
 * <p>All fields are optional.  Only the fields supplied by the client are
 * applied to the candidate record.  Social profile URLs provided here are
 * used to enrich the candidate's external profiles automatically.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmCandidateInput {

    /** Corrected candidate name, if the AI extraction needs fixing. */
    private String name;

    /** Corrected email address, if the AI extraction needs fixing. */
    private String email;

    /** Corrected mobile number, if the AI extraction needs fixing. */
    private String mobile;

    /** Corrected skills (comma-separated), if the AI extraction needs fixing. */
    private String skills;

    /** Corrected years of experience, if the AI extraction needs fixing. */
    private Integer yearsOfExperience;

    /** Corrected experience summary, if the AI extraction needs fixing. */
    private String experienceSummary;

    /** LinkedIn profile URL (manually provided or auto-extracted). */
    private String linkedInUrl;

    /** GitHub profile URL (manually provided or auto-extracted). */
    private String githubUrl;

    /** Twitter / X profile URL (manually provided or auto-extracted). */
    private String twitterUrl;
}
