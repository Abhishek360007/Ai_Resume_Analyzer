package com.abhishek.resumeanalyzer.resolver;

import com.abhishek.resumeanalyzer.entities.User;
import com.abhishek.resumeanalyzer.services.AuthenticationService;
import com.abhishek.resumeanalyzer.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserResolverTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private UserResolver userResolver;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setUsername("admin");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("resetUserPassword saves the new password through AuthenticationService")
    void resetUserPasswordSavesNewPassword() {
        UUID id = UUID.randomUUID();
        User target = new User();
        target.setUsername("recruiter");
        when(userService.getUserById(id)).thenReturn(Optional.of(target));

        assertThat(userResolver.resetUserPassword(id, "NewSecret#1")).isTrue();

        verify(authenticationService).resetPassword(target, "NewSecret#1", admin, null);
    }

    @Test
    @DisplayName("resetUserPassword rejects passwords shorter than 6 characters")
    void resetUserPasswordRejectsShortPassword() {
        assertThatThrownBy(() -> userResolver.resetUserPassword(UUID.randomUUID(), "abc"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(authenticationService, never()).resetPassword(any(), any(), any(), any());
    }
}
