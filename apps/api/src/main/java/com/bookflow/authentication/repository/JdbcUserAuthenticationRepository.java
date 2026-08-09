package com.bookflow.authentication.repository;

import com.bookflow.authentication.domain.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcUserAuthenticationRepository implements UserAuthenticationRepository {
    private final JdbcTemplate jdbc;
    public JdbcUserAuthenticationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<LoginUser> findByNormalizedEmail(String email) {
        return jdbc.query("SELECT id, normalized_email, password_hash, status FROM users WHERE normalized_email = ?", (rs, row) ->
                new LoginUser(rs.getObject("id", UUID.class), rs.getString("normalized_email"), rs.getString("password_hash"), UserStatus.valueOf(rs.getString("status"))), email).stream().findFirst();
    }
    public void persistSuccessfulLogin(UUID userId, Instant now, NewAuthenticationSession session, NewRefreshToken token) {
        jdbc.update("UPDATE users SET last_login_at=?, updated_at=? WHERE id=?", Timestamp.from(now), Timestamp.from(now), userId);
        jdbc.update("INSERT INTO auth_sessions (id,user_id,status,issued_at,last_used_at,inactivity_expires_at,absolute_expires_at,created_at,updated_at) VALUES (?,?, 'ACTIVE',?,?,?,?,?,?)", session.id(),session.userId(),Timestamp.from(session.issuedAt()),Timestamp.from(session.issuedAt()),Timestamp.from(session.inactivityExpiresAt()),Timestamp.from(session.absoluteExpiresAt()),Timestamp.from(now),Timestamp.from(now));
        jdbc.update("INSERT INTO refresh_tokens (id,user_id,family_id,token_hash,status,issued_at,last_used_at,inactivity_expires_at,absolute_expires_at,created_at,updated_at) VALUES (?,?,?,?, 'ACTIVE',?,?,?,?,?,?)", token.id(),token.userId(),token.familyId(),token.tokenHash(),Timestamp.from(token.issuedAt()),Timestamp.from(token.issuedAt()),Timestamp.from(token.inactivityExpiresAt()),Timestamp.from(token.absoluteExpiresAt()),Timestamp.from(now),Timestamp.from(now));
    }
    public StoredRefreshToken lockRefreshToken(String hash) {
        return jdbc.query("SELECT id,user_id,family_id,token_hash,status,inactivity_expires_at,absolute_expires_at,rotated_at FROM refresh_tokens WHERE token_hash=? FOR UPDATE", (rs,row) -> new StoredRefreshToken(rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),rs.getObject("family_id",UUID.class),rs.getString("token_hash"),rs.getString("status"),rs.getTimestamp("inactivity_expires_at").toInstant(),rs.getTimestamp("absolute_expires_at").toInstant(),rs.getTimestamp("rotated_at") == null ? null : rs.getTimestamp("rotated_at").toInstant()), hash).stream().findFirst().orElse(null);
    }
    public void rotateRefreshToken(StoredRefreshToken current, RotatedRefreshToken replacement, Instant now, Instant inactivityExpiry, Instant absoluteExpiry) {
        UUID id=UUID.randomUUID();
        jdbc.update("UPDATE refresh_tokens SET status='ROTATED', rotated_at=?, updated_at=? WHERE id=? AND status='ACTIVE'", Timestamp.from(now), Timestamp.from(now), current.id());
        jdbc.update("INSERT INTO refresh_tokens (id,user_id,family_id,token_hash,parent_token_id,status,issued_at,last_used_at,inactivity_expires_at,absolute_expires_at,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?,?,?,?)", id,current.userId(),current.familyId(),replacement.hash(),current.id(),Timestamp.from(now),Timestamp.from(now),Timestamp.from(inactivityExpiry),Timestamp.from(absoluteExpiry),Timestamp.from(now),Timestamp.from(now));
        jdbc.update("UPDATE refresh_tokens SET replaced_by_token_id=?, updated_at=? WHERE id=?", id, Timestamp.from(now), current.id());
    }
    public void revokeFamily(UUID familyId, Instant now, String reason) {
        jdbc.update("UPDATE refresh_tokens SET status='REVOKED', revoked_at=?, revoke_reason=?, updated_at=? WHERE family_id=? AND status IN ('ACTIVE','ROTATED')", Timestamp.from(now), reason, Timestamp.from(now), familyId);
        jdbc.update("UPDATE auth_sessions SET status='REVOKED', revoked_at=?, revoke_reason=?, updated_at=? WHERE id=? AND status='ACTIVE'", Timestamp.from(now), reason, Timestamp.from(now), familyId);
    }
    public void revokeAllForUser(UUID userId, Instant now, String reason) {
        jdbc.update("UPDATE refresh_tokens SET status='REVOKED', revoked_at=?, revoke_reason=?, updated_at=? WHERE user_id=? AND status IN ('ACTIVE','ROTATED')", Timestamp.from(now), reason, Timestamp.from(now), userId);
        jdbc.update("UPDATE auth_sessions SET status='REVOKED', revoked_at=?, revoke_reason=?, updated_at=? WHERE user_id=? AND status='ACTIVE'", Timestamp.from(now), reason, Timestamp.from(now), userId);
    }
}
