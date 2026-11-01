package com.abhishek.resumeanalyzer.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against GraphQL operations without access control.
 *
 * <p>{@code POST /graphql} is reachable without logging in, so an operation that lacks
 * {@link PreAuthorize} can be called anonymously. This test fails the build if any
 * query or mutation (or its controller class) is missing the annotation.</p>
 */
class ResolverSecurityTest {

    @Test
    @DisplayName("Every GraphQL query and mutation declares @PreAuthorize")
    void everyOperationIsSecured() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> operations = new ArrayList<>();
        List<String> unsecured = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.abhishek.resumeanalyzer.resolver")) {
            Class<?> resolver = Class.forName(candidate.getBeanClassName());
            boolean classSecured = resolver.isAnnotationPresent(PreAuthorize.class);
            for (Method method : resolver.getDeclaredMethods()) {
                if (method.isAnnotationPresent(QueryMapping.class) || method.isAnnotationPresent(MutationMapping.class)) {
                    String name = resolver.getSimpleName() + "." + method.getName();
                    operations.add(name);
                    if (!classSecured && !method.isAnnotationPresent(PreAuthorize.class)) {
                        unsecured.add(name);
                    }
                }
            }
        }

        assertThat(operations).as("resolver scan found operations").hasSizeGreaterThan(50);
        assertThat(unsecured).as("GraphQL operations callable without login").isEmpty();
    }
}
