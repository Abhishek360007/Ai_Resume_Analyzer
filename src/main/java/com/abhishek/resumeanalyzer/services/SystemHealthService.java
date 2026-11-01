package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.SystemHealth;
import com.abhishek.resumeanalyzer.models.ServiceStatus;
import com.abhishek.resumeanalyzer.repositories.SystemHealthRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

/**
 * Service for system health monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemHealthService {

    private final SystemHealthRepository systemHealthRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.openai.base-url:http://localhost:1234}")
    private String llmStudioBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String llmStudioApiKey;

    @Value("${app.llm.provider-name:LM Studio}")
    private String llmProviderName;

    private static final int TIMEOUT_MS = 5000;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Initialize health records for all services
     */
    @PostConstruct
    public void initializeHealthRecords() {
        createOrUpdateHealthRecord("database", "PostgreSQL Database");
        createOrUpdateHealthRecord("llm-studio", llmProviderName + " API");
        createOrUpdateHealthRecord("application", "Spring Boot Application");
    }

    /**
     * Check all services health (scheduled every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkAllServices() {
        log.debug("Running scheduled health checks");
        checkDatabaseHealth();
        checkLLMStudioHealth();
        checkApplicationHealth();
    }

    /**
     * Get system health report (admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<SystemHealth> getSystemHealthReport() {
        return systemHealthRepository.findAll();
    }

    /**
     * Get health status for a specific service
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Optional<SystemHealth> getServiceHealth(String serviceName) {
        return systemHealthRepository.findByServiceName(serviceName);
    }

    /**
     * Check database health
     */
    @Transactional
    public SystemHealth checkDatabaseHealth() {
        long startTime = System.currentTimeMillis();
        SystemHealth health = getOrCreateHealthRecord("database", "PostgreSQL Database");
        
        try {
            // Simple query to check database connectivity
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            health.recordSuccess(responseTime, "Database connection successful");
            
            log.debug("Database health check: SUCCESS ({}ms)", responseTime);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            health.recordFailure(responseTime, "Database connection failed: " + e.getMessage());
            
            log.error("Database health check: FAILED", e);
        }
        
        return systemHealthRepository.save(health);
    }

    /**
     * Check LLM Studio health.
     *
     * <p>Unlike a bare TCP connect, this performs a real authenticated HTTP probe
     * of {@code GET /models} using the configured API key, so the failure message
     * distinguishes an unreachable server from an authentication problem or a
     * missing model.</p>
     */
    @Transactional
    public SystemHealth checkLLMStudioHealth() {
        long startTime = System.currentTimeMillis();
        SystemHealth health = getOrCreateHealthRecord("llm-studio", llmProviderName + " API");

        String baseUrl = StringUtils.hasText(llmStudioBaseUrl) ? llmStudioBaseUrl : "http://localhost:1234";
        String modelsUrl = baseUrl.endsWith("/") ? baseUrl + "v1/models" : baseUrl + "/v1/models";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            if (StringUtils.hasText(llmStudioApiKey)) {
                headers.setBearerAuth(llmStudioApiKey);
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    modelsUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            long responseTime = System.currentTimeMillis() - startTime;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                int modelCount = 0;
                Object data = response.getBody().get("data");
                if (data instanceof List<?> list) {
                    modelCount = list.size();
                }
                String message = modelCount > 0
                        ? llmProviderName + " is running (" + modelCount + " model(s) loaded)"
                        : llmProviderName + " is running but no models are loaded";
                health.recordSuccess(responseTime, message);
                log.debug("LLM Studio health check: SUCCESS ({}ms, {} model(s))", responseTime, modelCount);
                return systemHealthRepository.save(health);
            }

            long errorTime = System.currentTimeMillis() - startTime;
            String message = llmProviderName + " returned HTTP " + response.getStatusCode().value() + " at " + baseUrl;
            health.recordFailure(errorTime, message);
            log.warn("LLM Studio health check: HTTP {} at {}", response.getStatusCode().value(), baseUrl);
        } catch (HttpClientErrorException e) {
            long errorTime = System.currentTimeMillis() - startTime;
            String message = (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                    ? llmProviderName + " authentication failed at " + baseUrl + " (check the API key)"
                    : llmProviderName + " HTTP error " + e.getStatusCode().value() + " at " + baseUrl;
            health.recordFailure(errorTime, message);
            log.warn("LLM Studio health check: {}", message);
        } catch (ResourceAccessException e) {
            long errorTime = System.currentTimeMillis() - startTime;
            String message = llmProviderName + " is not running or not accessible at " + baseUrl;
            health.recordFailure(errorTime, message);
            log.warn("LLM Studio health check: NOT RUNNING at {} (expected if LM Studio is not started)", baseUrl);
        } catch (Exception e) {
            long errorTime = System.currentTimeMillis() - startTime;
            health.recordFailure(errorTime, llmProviderName + " health check failed: " + e.getMessage());
            log.error("LLM Studio health check: FAILED", e);
        }

        return systemHealthRepository.save(health);
    }

    /**
     * Check application health
     */
    @Transactional
    public SystemHealth checkApplicationHealth() {
        long startTime = System.currentTimeMillis();
        SystemHealth health = getOrCreateHealthRecord("application", "Spring Boot Application");
        
        try {
            // Check application is running (if we got here, it is)
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Set status based on memory usage
            if (memoryUsagePercent > 90) {
                health.recordDegraded(
                    String.format("High memory usage: %.2f%% (Used: %dMB, Max: %dMB)", 
                        memoryUsagePercent, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024), 
                    (int) responseTime);
            } else {
                health.recordSuccess(responseTime,
                    String.format("Application healthy. Memory usage: %.2f%% (Used: %dMB, Max: %dMB)",
                        memoryUsagePercent, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024));
            }
            
            log.debug("Application health check: SUCCESS ({}ms, memory: {:.2f}%)", 
                    responseTime, memoryUsagePercent);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            health.recordFailure(responseTime, "Application health check failed: " + e.getMessage());
            
            log.error("Application health check: FAILED", e);
        }
        
        return systemHealthRepository.save(health);
    }

    /**
     * Get overall system status
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getOverallSystemStatus() {
        List<SystemHealth> allHealth = systemHealthRepository.findAll();
        
        long upCount = systemHealthRepository.countByStatus(ServiceStatus.UP);
        long downCount = systemHealthRepository.countByStatus(ServiceStatus.DOWN);
        long degradedCount = systemHealthRepository.countByStatus(ServiceStatus.DEGRADED);
        
        // Overall status: DOWN if any service is down, DEGRADED if any degraded, otherwise UP
        ServiceStatus overallStatus;
        if (downCount > 0) {
            overallStatus = ServiceStatus.DOWN;
        } else if (degradedCount > 0) {
            overallStatus = ServiceStatus.DEGRADED;
        } else {
            overallStatus = ServiceStatus.UP;
        }
        
        return Map.of(
            "overallStatus", overallStatus,
            "services", allHealth,
            "summary", Map.of(
                "up", upCount,
                "down", downCount,
                "degraded", degradedCount,
                "total", allHealth.size()
            )
        );
    }

    /**
     * Manually trigger health check for a specific service
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SystemHealth checkServiceHealth(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "database" -> checkDatabaseHealth();
            case "llm-studio" -> checkLLMStudioHealth();
            case "application" -> checkApplicationHealth();
            default -> throw new RuntimeException("Unknown service: " + serviceName);
        };
    }

    /**
     * Get or create health record for a service
     */
    private SystemHealth getOrCreateHealthRecord(String serviceName, String description) {
        return systemHealthRepository.findByServiceName(serviceName)
                .orElseGet(() -> createOrUpdateHealthRecord(serviceName, description));
    }

    /**
     * Create or update health record
     */
    private SystemHealth createOrUpdateHealthRecord(String serviceName, String description) {
        Optional<SystemHealth> existing = systemHealthRepository.findByServiceName(serviceName);
        
        if (existing.isPresent()) {
            return existing.get();
        } else {
            SystemHealth health = new SystemHealth();
            health.setServiceName(serviceName);
            health.setStatus(ServiceStatus.UNKNOWN);
            health.setMessage(description + " - Not yet checked");
            health.setLastCheckedAt(LocalDateTime.now());
            return systemHealthRepository.save(health);
        }
    }

    /**
     * Reset failure count for a service (admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resetFailureCount(String serviceName) {
        SystemHealth health = systemHealthRepository.findByServiceName(serviceName)
                .orElseThrow(() -> new RuntimeException("Service not found: " + serviceName));
        
        health.setFailureCount(0);
        systemHealthRepository.save(health);
        
        log.info("Reset failure count for service: {}", serviceName);
    }
}
