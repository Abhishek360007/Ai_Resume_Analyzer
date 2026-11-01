package com.abhishek.resumeanalyzer.repos;

import com.abhishek.resumeanalyzer.entities.CompanyImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for global company impressions, keyed by normalised company name.
 */
@Repository
public interface CompanyImpressionRepository extends JpaRepository<CompanyImpression, UUID> {

    /**
     * Find an existing impression for a company name (case-insensitive).
     */
    Optional<CompanyImpression> findByCompanyNameIgnoreCase(String companyName);

    /**
     * Bulk lookup used to assemble impression context for a candidate's employers.
     */
    List<CompanyImpression> findByCompanyNameInIgnoreCase(List<String> companyNames);
}
