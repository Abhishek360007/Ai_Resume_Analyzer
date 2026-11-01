package com.abhishek.resumeanalyzer.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Open-EntityManager-in-View for every request except long-lived streams.
 *
 * <p>Spring Boot's built-in OSIV (disabled via {@code spring.jpa.open-in-view=false})
 * keeps the request's EntityManager, and its pooled JDBC connection, open until
 * the request completes. For the Server-Sent Events endpoint that is the lifetime
 * of the browser tab, so every logged-in user pinned a connection and a handful of
 * open tabs exhausted the pool, making the whole application unavailable.</p>
 *
 * <p>This registers the same interceptor for all other requests (GraphQL field
 * resolution still relies on lazy loading) and excludes the streaming endpoints.</p>
 */
@Configuration
@RequiredArgsConstructor
public class OpenEntityManagerInViewConfig implements WebMvcConfigurer {

    /** Endpoints that hold the request open indefinitely and must not pin a connection. */
    static final String[] STREAMING_ENDPOINTS = {"/api/upload/status/events"};

    // Optional so web-only test slices (@WebMvcTest) without JPA can still load this config
    private final ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        EntityManagerFactory entityManagerFactory = entityManagerFactoryProvider.getIfAvailable();
        if (entityManagerFactory == null) {
            return;
        }
        OpenEntityManagerInViewInterceptor interceptor = new OpenEntityManagerInViewInterceptor();
        interceptor.setEntityManagerFactory(entityManagerFactory);
        registry.addWebRequestInterceptor(interceptor).excludePathPatterns(STREAMING_ENDPOINTS);
    }
}
