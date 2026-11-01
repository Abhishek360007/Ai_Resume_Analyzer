package com.abhishek.resumeanalyzer.models;

/**
 * Verdict assigned by the LLM to a company researched from the internet.
 *
 * <p>Helps the recruiting team judge whether a company listed on a candidate's
 * resume appears to be a genuine organisation, a shell / suspicious entity, or
 * is unknown to public sources.</p>
 */
public enum CompanyVerdict {
    /** The company appears to be a genuine, verifiable organisation. */
    GENUINE,

    /** Public sources indicate the company may be fake, defunct, or suspicious. */
    SUSPICIOUS,

    /** No reliable public information was found about the company. */
    UNKNOWN
}
