package com.abhishek.resumeanalyzer.models;

/**
 * Lifecycle status of a candidate record.
 *
 * <p>After a resume is parsed and analysed the candidate is created in
 * {@link #PENDING_CONFIRMATION} so the HR / TAG / recruiting manager can review
 * the extracted details and add social profile URLs before the candidate is
 * activated for job matching.</p>
 */
public enum CandidateStatus {
    /** Candidate is awaiting manual confirmation of the AI-extracted details. */
    PENDING_CONFIRMATION,

    /** Candidate has been confirmed and is eligible for job matching. */
    ACTIVE
}
