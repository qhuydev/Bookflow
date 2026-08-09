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
import java.util.List;

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

    @Test
    void enforcesPasswordResetTokenConstraintsAndIndexes() throws SQLException {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "reset-schema-" + userId + "@example.test");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        insertPasswordResetToken(UUID.randomUUID(), userId, hash('e'), "ACTIVE",
                now.plus(30, ChronoUnit.MINUTES), now, null, null, null);

        assertThatThrownBy(() -> insertPasswordResetToken(UUID.randomUUID(), userId, hash('e'), "ACTIVE",
                now.plus(30, ChronoUnit.MINUTES), now, null, null, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertPasswordResetToken(UUID.randomUUID(), UUID.randomUUID(), hash('f'), "ACTIVE",
                now.plus(30, ChronoUnit.MINUTES), now, null, null, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertPasswordResetToken(UUID.randomUUID(), userId, hash('g'), "ACTIVE",
                now, now, null, null, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertPasswordResetToken(UUID.randomUUID(), userId, hash('h'), "USED",
                now.plus(30, ChronoUnit.MINUTES), now, null, null, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertPasswordResetToken(UUID.randomUUID(), userId, hash('i'), "REVOKED",
                now.plus(30, ChronoUnit.MINUTES), now, null, null, null))
                .isInstanceOf(SQLException.class);

        assertThat(passwordResetIndexes()).contains(
                "idx_password_reset_tokens_user_status",
                "idx_password_reset_tokens_status_expires_at",
                "password_reset_tokens_token_hash_key"
        );
    }

    private void insertPasswordResetToken(
            UUID id,
            UUID userId,
            String tokenHash,
            String status,
            Instant expiresAt,
            Instant createdAt,
            Instant usedAt,
            Instant revokedAt,
            String revokeReason
    ) throws SQLException {
        String sql = """
                INSERT INTO password_reset_tokens (
                    id, user_id, token_hash, status, expires_at, used_at,
                    revoked_at, revoke_reason, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setString(3, tokenHash);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.from(expiresAt));
            statement.setTimestamp(6, usedAt == null ? null : Timestamp.from(usedAt));
            statement.setTimestamp(7, revokedAt == null ? null : Timestamp.from(revokedAt));
            statement.setString(8, revokeReason);
            statement.setTimestamp(9, Timestamp.from(createdAt));
            statement.setTimestamp(10, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
    }

    private List<String> passwordResetIndexes() throws SQLException {
        String sql = "SELECT indexname FROM pg_indexes WHERE schemaname='public' AND tablename='password_reset_tokens'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            var indexes = new java.util.ArrayList<String>();
            while (resultSet.next()) {
                indexes.add(resultSet.getString(1));
            }
            return indexes;
        }
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
