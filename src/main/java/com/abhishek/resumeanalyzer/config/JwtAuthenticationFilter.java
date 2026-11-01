package com.abhishek.resumeanalyzer.config;

import com.abhishek.resumeanalyzer.entities.User;
import com.abhishek.resumeanalyzer.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * JWT Authentication Filter to validate JWT tokens on each request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            boolean isSseEndpoint = request.getRequestURI() != null
                    && request.getRequestURI().endsWith("/api/upload/status/events");
            String jwt = getJwtFromRequest(request, isSseEndpoint);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                // The SSE endpoint may only be opened with a short-lived SSE token
                // (it travels in the URL, so it must not be the 15-minute access
                // token). Conversely, SSE tokens are not accepted anywhere else.
                if (isSseEndpoint && !tokenProvider.isSseToken(jwt)) {
                    log.warn("Rejected non-SSE token on SSE endpoint: {}", request.getRequestURI());
                } else if (!isSseEndpoint && tokenProvider.isSseToken(jwt)) {
                    log.warn("Rejected SSE token on non-SSE endpoint: {}", request.getRequestURI());
                } else {
                    String userId = tokenProvider.getUserIdFromToken(jwt);
                    String role = tokenProvider.getRoleFromToken(jwt);

                    // Load user from database
                    User user = userRepository.findById(UUID.fromString(userId))
                            .orElse(null);

                    if (user != null && user.isActive()) {
                        // Create authentication token
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, Collections.singletonList(authority));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        // Set authentication in security context
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Set authentication for user: {}", user.getUsername());
                    } else {
                        log.warn("User not found or inactive: {}", userId);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from the Authorization header or the {@code token}
     * query parameter.
     *
     * <p>The query-parameter path exists for Server-Sent Events only: the
     * browser's {@code EventSource} API cannot set HTTP headers, so the token
     * is carried in the request URL. It is never honoured on other endpoints,
     * which prevents an access token from leaking via the query string.</p>
     */
    private String getJwtFromRequest(HttpServletRequest request, boolean isSseEndpoint) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        if (isSseEndpoint) {
            String tokenParam = request.getParameter("token");
            if (StringUtils.hasText(tokenParam)) {
                return tokenParam;
            }
        }
        return null;
    }
}
