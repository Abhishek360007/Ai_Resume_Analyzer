package com.abhishek.resumeanalyzer.entities;

/**
 * Enum representing the source of external candidate profile data.
 *
 * <p>To add a new source:
 * <ol>
 *   <li>Add a value below.</li>
 *   <li>Create a {@link com.abhishek.resumeanalyzer.services.enrichers.ProfileEnricher}
 *       {@code @Component} for it.</li>
 *   <li>No other changes needed — the enrichment service auto-discovers it.</li>
 * </ol>
 */
public enum ExternalProfileSource {
    GITHUB,
    LINKEDIN,
    TWITTER,
    INTERNET_SEARCH
}
