package com.abhishek.resumeanalyzer.entities;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link User}.
 *
 * <p>The authenticated {@code User} is the Spring Security principal, and
 * {@code Authentication.getName()} calls its {@code toString()} after the
 * Hibernate session has closed, so it must never touch lazy associations.</p>
 */
class UserTest {

    @Test
    @DisplayName("toString does not initialise the lazy createdBy association")
    void toStringSkipsLazyCreatedBy() {
        // Behaves like an uninitialised Hibernate proxy whose session has closed
        User detachedProxy = new User() {
            @Override
            public String toString() {
                throw new LazyInitializationException("no Session");
            }

            @Override
            public int hashCode() {
                throw new LazyInitializationException("no Session");
            }
        };

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("recruiter");
        user.setCreatedBy(detachedProxy);

        assertThatCode(user::toString).doesNotThrowAnyException();
        assertThatCode(user::hashCode).doesNotThrowAnyException();
        assertThat(user.toString()).contains("recruiter");
    }

    @Test
    @DisplayName("toString never exposes the password hash")
    void toStringHidesPasswordHash() {
        User user = new User();
        user.setPasswordHash("$2a$10$secret-hash");

        assertThat(user.toString()).doesNotContain("secret-hash");
    }
}
