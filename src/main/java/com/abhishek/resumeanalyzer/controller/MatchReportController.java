package com.abhishek.resumeanalyzer.controller;

import com.abhishek.resumeanalyzer.services.MatchReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for downloadable reports.
 * Binary downloads are served over REST because GraphQL only returns JSON.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class MatchReportController {

    private final MatchReportService matchReportService;

    /**
     * Download the candidate match report for a job requirement as a PDF.
     *
     * @param jobRequirementId Job requirement UUID
     * @return PDF file attachment
     */
    @GetMapping("/jobs/{jobRequirementId}/matches.pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'HR', 'HIRING_MANAGER')")
    public ResponseEntity<byte[]> downloadMatchReport(@PathVariable UUID jobRequirementId) {
        byte[] pdf = matchReportService.generateJobMatchReport(jobRequirementId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("match-report-" + jobRequirementId + ".pdf")
                        .build()
                        .toString())
                .body(pdf);
    }
}
