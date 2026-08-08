package com.bookflow.authentication.repository;

import com.bookflow.authentication.application.EmailAlreadyRegisteredException;
import com.bookflow.authentication.domain.NewUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.sql.Timestamp;

@Repository
@Profile("!test")
public class JdbcUserRegistrationRepository implements UserRegistrationRepository {

    private static final String NORMALIZED_EMAIL_UNIQUE_CONSTRAINT = "users_normalized_email_key";

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(NewUser user) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (
                        id, normalized_email, password_hash, status,
                        password_changed_at, last_login_at, created_at, updated_at
                    ) VALUES (?, ?, ?, 'ACTIVE', ?, NULL, ?, ?)
                    """,
                    user.id(),
                    user.normalizedEmail(),
                    user.passwordHash(),
                    Timestamp.from(user.registeredAt()),
                    Timestamp.from(user.registeredAt()),
                    Timestamp.from(user.registeredAt())
            );
        } catch (DataIntegrityViolationException exception) {
            if (isNormalizedEmailUniqueViolation(exception)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
    }

    private boolean isNormalizedEmailUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(NORMALIZED_EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
