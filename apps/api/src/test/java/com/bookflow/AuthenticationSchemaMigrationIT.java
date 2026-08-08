package com.bookflow;

import com.bookflow.support.PostgresTestcontainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class AuthenticationSchemaMigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void enforcesUniqueNormalizedEmail() throws SQLException {
        insertUser(UUID.randomUUID(), "member@example.test");

        assertThatThrownBy(() -> insertUser(UUID.randomUUID(), "member@example.test"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void enforcesSessionTokenRelationshipAndAuthenticationConstraints() throws SQLException {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID firstSessionId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        UUID firstTokenId = UUID.randomUUID();
        UUID replacementTokenId = UUID.randomUUID();

        insertUser(firstUserId, "first-" + firstUserId + "@example.test");
        insertUser(secondUserId, "second-" + secondUserId + "@example.test");
        insertSession(firstSessionId, firstUserId);
        insertSession(secondSessionId, firstUserId);
        insertRefreshToken(firstTokenId, firstUserId, firstSessionId, hash('a'), null);

        assertThatThrownBy(() -> insertRefreshToken(
                UUID.randomUUID(), secondUserId, firstSessionId, hash('b'), null
        )).isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> insertRefreshToken(
                UUID.randomUUID(), firstUserId, secondSessionId, hash('a'), null
        )).isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> insertRefreshToken(
                UUID.randomUUID(), firstUserId, firstSessionId, hash('c'), null
        )).isInstanceOf(SQLException.class);

        rotateToken(firstTokenId);
        assertThatThrownBy(() -> insertRefreshToken(
                UUID.randomUUID(), firstUserId, secondSessionId, hash('d'), firstTokenId
        )).isInstanceOf(SQLException.class);
        insertRefreshToken(replacementTokenId, firstUserId, firstSessionId, hash('d'), firstTokenId);
        linkReplacement(firstTokenId, replacementTokenId);

        assertThat(readTokenStatus(firstTokenId)).isEqualTo("ROTATED");
        assertThat(readTokenParent(replacementTokenId)).isEqualTo(firstTokenId);
        assertThat(readTokenReplacement(firstTokenId)).isEqualTo(replacementTokenId);

        assertThatThrownBy(() -> insertSessionWithInvalidExpiry(UUID.randomUUID(), firstUserId))
                .isInstanceOf(SQLException.class);
    }

    private void insertUser(UUID id, String normalizedEmail) throws SQLException {
        String sql = """
                INSERT INTO users (id, normalized_email, password_hash)
                VALUES (?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setString(2, normalizedEmail);
            statement.setString(3, "$argon2id$test-schema-only");
            statement.executeUpdate();
        }
    }

    private void insertSession(UUID sessionId, UUID userId) throws SQLException {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertSession(sessionId, userId, issuedAt.plus(7, ChronoUnit.DAYS), issuedAt.plus(30, ChronoUnit.DAYS));
    }

    private void insertSessionWithInvalidExpiry(UUID sessionId, UUID userId) throws SQLException {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertSession(sessionId, userId, issuedAt.plus(31, ChronoUnit.DAYS), issuedAt.plus(30, ChronoUnit.DAYS));
    }

    private void insertSession(
            UUID sessionId,
            UUID userId,
            Instant inactivityExpiresAt,
            Instant absoluteExpiresAt
    ) throws SQLException {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String sql = """
                INSERT INTO auth_sessions (
                    id, user_id, status, issued_at, last_used_at,
                    inactivity_expires_at, absolute_expires_at
                ) VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, userId);
            statement.setTimestamp(3, Timestamp.from(issuedAt));
            statement.setTimestamp(4, Timestamp.from(issuedAt));
            statement.setTimestamp(5, Timestamp.from(inactivityExpiresAt));
            statement.setTimestamp(6, Timestamp.from(absoluteExpiresAt));
            statement.executeUpdate();
        }
    }

    private void insertRefreshToken(
            UUID tokenId,
            UUID userId,
            UUID familyId,
            String tokenHash,
            UUID parentTokenId
    ) throws SQLException {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String sql = """
                INSERT INTO refresh_tokens (
                    id, user_id, family_id, token_hash, parent_token_id, status,
                    issued_at, last_used_at, inactivity_expires_at, absolute_expires_at
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tokenId);
            statement.setObject(2, userId);
            statement.setObject(3, familyId);
            statement.setString(4, tokenHash);
            statement.setObject(5, parentTokenId);
            statement.setTimestamp(6, Timestamp.from(issuedAt));
            statement.setTimestamp(7, Timestamp.from(issuedAt));
            statement.setTimestamp(8, Timestamp.from(issuedAt.plus(7, ChronoUnit.DAYS)));
            statement.setTimestamp(9, Timestamp.from(issuedAt.plus(30, ChronoUnit.DAYS)));
            statement.executeUpdate();
        }
    }

    private void rotateToken(UUID tokenId) throws SQLException {
        String sql = "UPDATE refresh_tokens SET status = 'ROTATED', rotated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tokenId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void linkReplacement(UUID tokenId, UUID replacementTokenId) throws SQLException {
        String sql = "UPDATE refresh_tokens SET replaced_by_token_id = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, replacementTokenId);
            statement.setObject(2, tokenId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private String readTokenStatus(UUID tokenId) throws SQLException {
        return readTokenUuidOrStatus(tokenId, "status");
    }

    private UUID readTokenParent(UUID tokenId) throws SQLException {
        return UUID.fromString(readTokenUuidOrStatus(tokenId, "parent_token_id"));
    }

    private UUID readTokenReplacement(UUID tokenId) throws SQLException {
        return UUID.fromString(readTokenUuidOrStatus(tokenId, "replaced_by_token_id"));
    }

    private String readTokenUuidOrStatus(UUID tokenId, String column) throws SQLException {
        String sql = "SELECT " + column + " FROM refresh_tokens WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tokenId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private String hash(char character) {
        return String.valueOf(character).repeat(64);
    }
}
