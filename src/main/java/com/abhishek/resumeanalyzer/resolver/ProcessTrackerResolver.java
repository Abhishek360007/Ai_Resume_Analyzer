package com.abhishek.resumeanalyzer.resolver;

import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import com.abhishek.resumeanalyzer.repos.ProcessTrackerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GraphQL resolver for ProcessTracker queries.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ProcessTrackerResolver {

    private final ProcessTrackerRepository trackerRepository;

    @QueryMapping
    @PreAuthorize("hasAnyRole(\'ADMIN\', \'RECRUITER\')")
    public ProcessTracker processStatus(@Argument UUID trackerId) {
        log.info("Fetching process status: {}", trackerId);
        return trackerRepository.findById(trackerId)
                .orElseThrow(() -> new RuntimeException("Process tracker not found: " + trackerId));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole(\'ADMIN\', \'RECRUITER\')")
    public List<ProcessTracker> allProcessTrackers() {
        log.info("Fetching all process trackers");
        return trackerRepository.findAllOrderByCreatedAtDesc();
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole(\'ADMIN\', \'RECRUITER\')")
    public List<ProcessTracker> recentProcessTrackers(@Argument int hours) {
        log.info("Fetching process trackers from the last {} hours", hours);
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hours);
        return trackerRepository.findByCreatedAtAfter(cutoffTime);
    }
}
