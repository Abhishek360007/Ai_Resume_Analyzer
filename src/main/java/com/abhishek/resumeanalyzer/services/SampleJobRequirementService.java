package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.JobRequirement;
import com.abhishek.resumeanalyzer.entities.Skill;
import com.abhishek.resumeanalyzer.repos.JobRequirementRepository;
import com.abhishek.resumeanalyzer.repos.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads and removes a curated set of sample job requirements on demand.
 * Intended for the ADMIN role so the matching engine can be demoed without
 * manually creating jobs. Load is idempotent (skips titles that already exist);
 * removal only deletes jobs whose titles match the samples.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SampleJobRequirementService {

    private final JobRequirementRepository jobRepository;
    private final SkillRepository skillRepository;

    @Transactional
    public List<JobRequirement> loadSampleJobRequirements() {
        List<JobRequirement> created = new ArrayList<>();
        for (SampleJob sample : SAMPLE_JOBS) {
            if (existsWithTitle(sample.title())) {
                continue;
            }
            Set<Skill> skills = resolveSkills(sample.skills());
            JobRequirement job = JobRequirement.builder()
                    .title(sample.title())
                    .description(sample.description())
                    .requiredSkills(String.join(", ", sample.skills()))
                    .minExperience(sample.minExperience())
                    .maxExperience(sample.maxExperience())
                    .minExperienceYears(sample.minExperience())
                    .maxExperienceYears(sample.maxExperience())
                    .requiredEducation(sample.requiredEducation())
                    .domain(sample.domain())
                    .domainRequirements(sample.domain())
                    .location(sample.location())
                    .skills(skills)
                    .isActive(true)
                    .build();
            created.add(jobRepository.save(job));
        }
        log.info("Loaded {} sample job requirements", created.size());
        return created;
    }

    @Transactional
    public int removeSampleJobRequirements() {
        int removed = 0;
        for (SampleJob sample : SAMPLE_JOBS) {
            for (JobRequirement job : jobRepository.searchByTitle(sample.title())) {
                if (job.getTitle().equalsIgnoreCase(sample.title())) {
                    jobRepository.delete(job);
                    removed++;
                }
            }
        }
        log.info("Removed {} sample job requirements", removed);
        return removed;
    }

    private boolean existsWithTitle(String title) {
        return jobRepository.searchByTitle(title).stream()
                .anyMatch(job -> job.getTitle().equalsIgnoreCase(title));
    }

    private Set<Skill> resolveSkills(String[] skillNames) {
        Set<Skill> skills = new LinkedHashSet<>();
        for (String name : skillNames) {
            skillRepository.findByNameIgnoreCase(name).ifPresent(skills::add);
        }
        return skills;
    }

    private record SampleJob(
            String title,
            String description,
            String[] skills,
            int minExperience,
            int maxExperience,
            String requiredEducation,
            String domain,
            String location) {}

    private static final List<SampleJob> SAMPLE_JOBS = List.of(
            new SampleJob(
                    "Senior Java Backend Engineer",
                    "Design and build scalable, resilient backend services for our fintech platform. " +
                    "Own services end to end: REST APIs, event-driven processing, and performance tuning.",
                    new String[]{"Java", "Spring Boot", "Spring Framework", "PostgreSQL", "REST API", "Microservices", "Docker", "AWS"},
                    4, 8,
                    "Bachelor's in Computer Science or related field",
                    "Fintech",
                    "Bangalore (Hybrid)"),
            new SampleJob(
                    "React Frontend Developer",
                    "Build delightful, accessible user interfaces for our e-commerce storefront using React. " +
                    "Collaborate with designers and own the component library.",
                    new String[]{"React", "TypeScript", "JavaScript", "HTML5", "CSS3", "GraphQL", "Redux"},
                    2, 5,
                    "Bachelor's in Computer Science or Design",
                    "E-commerce",
                    "Remote"),
            new SampleJob(
                    "Data Engineer",
                    "Build and maintain data pipelines, warehouses, and analytics infrastructure. " +
                    "Ensure data quality, reliability, and observability across the platform.",
                    new String[]{"Python", "PostgreSQL", "MongoDB", "AWS", "Docker", "Kubernetes", "Elasticsearch"},
                    3, 7,
                    "Bachelor's in Computer Science or Statistics",
                    "Analytics",
                    "Pune"),
            new SampleJob(
                    "DevOps Cloud Engineer",
                    "Own CI/CD, infrastructure as code, and cloud cost optimization. " +
                    "Automate deployments and improve reliability of production systems.",
                    new String[]{"AWS", "Azure", "Docker", "Kubernetes", "Terraform", "Jenkins", "Git", "Maven"},
                    3, 6,
                    "Bachelor's in Computer Science or Information Technology",
                    "Cloud",
                    "Hyderabad"),
            new SampleJob(
                    "Full-Stack Developer (Java + React)",
                    "Develop end-to-end features across the stack for our enterprise software suite. " +
                    "From database schema design to polished React UI.",
                    new String[]{"Java", "Spring Boot", "React", "TypeScript", "PostgreSQL", "Docker", "Git", "REST API"},
                    3, 7,
                    "Bachelor's in Computer Science",
                    "Enterprise Software",
                    "Chennai"),
            new SampleJob(
                    "Mobile Developer (iOS & Android)",
                    "Build high-quality native mobile applications for a leading consumer product. " +
                    "Work with product and design to ship fast and iterate.",
                    new String[]{"Swift", "Kotlin", "REST API", "GraphQL", "JavaScript"},
                    2, 5,
                    "Bachelor's in Computer Science",
                    "Mobile",
                    "Mumbai"));
}
