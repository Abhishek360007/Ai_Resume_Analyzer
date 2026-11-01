package com.abhishek.resumeanalyzer.entities;

import com.abhishek.resumeanalyzer.models.CompanyVerdict;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An AI-generated impression of a company gathered from the internet.
 *
 * <p>Impressions are <em>global</em> — they are keyed by {@code companyName}
 * and reused across candidates.  If a candidate's resume lists a company that
 * has already been researched, the cached impression is reused instead of
 * hitting the internet again.</p>
 */
@Entity
@Table(name = "company_impressions",
        uniqueConstraints = @UniqueConstraint(name = "uk_company_impressions_name", columnNames = {"company_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyImpression {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /**
     * Normalised company name as listed on the candidate's resume.
     */
    @Column(name = "company_name", nullable = false)
    private String companyName;

    /**
     * Industry / sector of the company, when public sources identify it.
     */
    @Column(name = "industry")
    private String industry;

    /**
     * LLM verdict on whether the company is genuine, suspicious, or unknown.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false)
    private CompanyVerdict verdict;

    /**
     * Human-readable impression summary produced by the LLM.
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * LLM confidence in the verdict (0.0 – 1.0).
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * Raw evidence / snippets gathered from internet search results.
     */
    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
