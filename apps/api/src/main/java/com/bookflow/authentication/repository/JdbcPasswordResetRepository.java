package com.bookflow.authentication.repository;

import com.bookflow.authentication.domain.StoredPasswordResetToken;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcPasswordResetRepository implements PasswordResetRepository {
    private final JdbcTemplate jdbc;

    public JdbcPasswordResetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findUserIdByNormalizedEmail(String normalizedEmail) {
        return jdbc.query(
                "SELECT id FROM users WHERE normalized_email=? AND status='ACTIVE'",
                (rs, row) -> rs.getObject("id", UUID.class),
                normalizedEmail
        ).stream().findFirst();
    }

    @Override
    public void revokeActiveTokens(UUID userId, Instant now, String reason) {
        jdbc.update("""
                UPDATE password_reset_tokens
                SET status='REVOKED', revoked_at=?, revoke_reason=?, updated_at=?
                WHERE user_id=? AND status='ACTIVE'
                """, Timestamp.from(now), reason, Timestamp.from(now), userId);
    }

    @Override
    public void createToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        jdbc.update("""
                INSERT INTO password_reset_tokens
                    (id, user_id, token_hash, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, id, userId, tokenHash, Timestamp.from(expiresAt), Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public StoredPasswordResetToken lockByHash(String tokenHash) {
        return jdbc.query("""
                SELECT id, user_id, status, expires_at
                FROM password_reset_tokens
                WHERE token_hash=?
                FOR UPDATE
                """, (rs, row) -> new StoredPasswordResetToken(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("status"),
                        rs.getTimestamp("expires_at").toInstant()
                ), tokenHash).stream().findFirst().orElse(null);
    }

    @Override
    public void resetPasswordAndRevokeAuthentication(
            StoredPasswordResetToken token,
            String passwordHash,
            Instant now
    ) {
        Timestamp timestamp = Timestamp.from(now);
        jdbc.update("""
                UPDATE users
                SET password_hash=?, password_changed_at=?, updated_at=?
                WHERE id=?
                """, passwordHash, timestamp, timestamp, token.userId());
        jdbc.update("""
                UPDATE password_reset_tokens
                SET status='USED', used_at=?, updated_at=?
                WHERE id=? AND status='ACTIVE'
                """, timestamp, timestamp, token.id());
        revokeActiveTokens(token.userId(), now, "PASSWORD_RESET_COMPLETED");
        jdbc.update("""
                UPDATE refresh_tokens
                SET status='REVOKED', revoked_at=?, revoke_reason='PASSWORD_RESET', updated_at=?
                WHERE user_id=? AND status IN ('ACTIVE', 'ROTATED')
                """, timestamp, timestamp, token.userId());
        jdbc.update("""
                UPDATE auth_sessions
                SET status='REVOKED', revoked_at=?, revoke_reason='PASSWORD_RESET', updated_at=?
                WHERE user_id=? AND status='ACTIVE'
                """, timestamp, timestamp, token.userId());
    }
}
