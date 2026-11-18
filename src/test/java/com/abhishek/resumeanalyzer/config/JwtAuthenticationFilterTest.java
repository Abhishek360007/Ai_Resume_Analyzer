package com.abhishek.resumeanalyzer.config;

import com.abhishek.resumeanalyzer.entities.User;
import com.abhishek.resumeanalyzer.models.UserRole;
import com.abhishek.resumeanalyzer.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Each token type must be accepted in exactly one place: access tokens on the API,
 * SSE tokens on the SSE endpoint only, and refresh tokens nowhere (they are only
 * exchanged at POST /api/auth/refresh, which validates them itself).
 */
class JwtAuthenticationFilterTest {

    private static final String API = "/api/upload/resume";
    private static final String SSE = "/api/upload/status/events";

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "unit-test-secret-that-is-comfortably-longer-than-256-bits");
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpirationMs", 900_000L);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpirationMs", 604_800_000L);
        ReflectionTestUtils.setField(tokenProvider, "sseTokenExpirationMs", 60_000L);
        tokenProvider.init();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("recruiter");
        user.setEmail("recruiter@example.com");
        user.setRole(UserRole.RECRUITER);
        user.setActive(true);

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
        filter = new JwtAuthenticationFilter(tokenProvider, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Access token authenticates an API request")
    void accessTokenWorksOnTheApi() throws Exception {
        assertThat(authenticate(API, bearer(tokenProvider.generateAccessToken(user)))).isNotNull();
    }

    @Test
    @DisplayName("Refresh token is rejected as a Bearer token on the API")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        assertThat(authenticate(API, bearer(tokenProvider.generateRefreshToken(user)))).isNull();
    }

    @Test
    @DisplayName("SSE token is rejected on the API")
    void sseTokenIsRejectedOnTheApi() throws Exception {
        assertThat(authenticate(API, bearer(tokenProvider.generateSseToken(user)))).isNull();
    }

    @Test
    @DisplayName("SSE token in the query string opens the SSE stream")
    void sseTokenWorksOnTheSseEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SSE);
        request.setParameter("token", tokenProvider.generateSseToken(user));
        assertThat(authenticate(request)).isNotNull();
    }

    @Test
    @DisplayName("Access and refresh tokens are rejected on the SSE endpoint")
    void otherTokensAreRejectedOnTheSseEndpoint() throws Exception {
        MockHttpServletRequest access = new MockHttpServletRequest("GET", SSE);
        access.setParameter("token", tokenProvider.generateAccessToken(user));
        assertThat(authenticate(access)).isNull();

        SecurityContextHolder.clearContext();
        MockHttpServletRequest refresh = new MockHttpServletRequest("GET", SSE);
        refresh.setParameter("token", tokenProvider.generateRefreshToken(user));
        assertThat(authenticate(refresh)).isNull();
    }

    private MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", API);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private Authentication authenticate(String uri, MockHttpServletRequest request) throws Exception {
        request.setRequestURI(uri);
        return authenticate(request);
    }

    private Authentication authenticate(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
