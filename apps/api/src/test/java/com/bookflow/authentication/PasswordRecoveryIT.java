package com.bookflow.authentication;

import com.bookflow.BookFlowApplication;
import com.bookflow.authentication.application.PasswordRecoveryService;
import com.bookflow.authentication.domain.StoredPasswordResetToken;
import com.bookflow.authentication.notification.PasswordResetNotificationPort;
import com.bookflow.authentication.password.Argon2idPasswordHasher;
import com.bookflow.authentication.repository.JdbcPasswordResetRepository;
import com.bookflow.authentication.repository.PasswordResetRepository;
import com.bookflow.authentication.token.PasswordResetTokenGenerator;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BookFlowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers")
@Import({PostgresTestcontainerConfiguration.class, PasswordRecoveryIT.PasswordRecoveryTestConfiguration.class})
class PasswordRecoveryIT {
    private static final String OLD_PASSWORD = "BookFlow old password 2026!";
    private static final String NEW_PASSWORD = "BookFlow new password 2026!";

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordRecoveryService recovery;
    @Autowired PasswordResetTokenGenerator tokens;
    @Autowired Argon2idPasswordHasher passwords;
    @Autowired CapturingNotificationPort notifications;
    @Autowired MutableClock clock;
    @Autowired FailingPasswordResetRepository repository;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE password_reset_tokens, refresh_tokens, auth_sessions, users CASCADE");
        notifications.clear();
        notifications.fail.set(false);
        repository.failAfterReset.set(false);
        clock.reset();
    }

    @Test
    void forgotPasswordIsEnumerationSafeAndStoresOnlyHash() throws Exception {
        MockMvc mvc = mvc();
        String email = "forgot-" + UUID.randomUUID() + "@example.test";
        register(mvc, email);

        MvcResult existing = forgot(mvc, "  " + email.toUpperCase() + "  ");
        MvcResult absent = forgot(mvc, "absent-" + UUID.randomUUID() + "@example.test");

        assertThat(existing.getResponse().getStatus()).isEqualTo(202);
        assertThat(absent.getResponse().getStatus()).isEqualTo(202);
        assertThat(existing.getResponse().getContentAsString()).isEqualTo(absent.getResponse().getContentAsString());
        Notification notification = notifications.take();
        assertThat(notification.email()).isEqualTo(email);
        String storedHash = jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class);
        assertThat(storedHash).isEqualTo(tokens.sha256(notification.rawToken())).isNotEqualTo(notification.rawToken());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash=?", Integer.class,
                notification.rawToken())).isZero();
        assertThat(notifications.size()).isZero();
    }

    @Test
    void notificationFailureDoesNotRevealThatAnAccountExists() throws Exception {
        MockMvc mvc = mvc();
        String email = "notification-failure-" + UUID.randomUUID() + "@example.test";
        register(mvc, email);
        notifications.fail.set(true);

        MvcResult response = forgot(mvc, email);

        assertThat(response.getResponse().getStatus()).isEqualTo(202);
        assertThat(response.getResponse().getContentAsString()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    void resetConsumesOnceAndRevokesEveryAuthenticationArtifactForOnlyThatUser() throws Exception {
        MockMvc mvc = mvc();
        String email = "reset-" + UUID.randomUUID() + "@example.test";
        register(mvc, email);
        login(mvc, email, OLD_PASSWORD);
        forgot(mvc, email);
        String raw = notifications.take().rawToken();

        reset(mvc, raw, NEW_PASSWORD).andExpect(status().isNoContent());

        String passwordHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE normalized_email=?", String.class, email);
        assertThat(passwords.matches(NEW_PASSWORD, passwordHash)).isTrue();
        assertThat(passwords.matches(OLD_PASSWORD, passwordHash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE status='ACTIVE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE status='USED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE status='ACTIVE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE status='ACTIVE'", Integer.class)).isZero();
        MvcResult reused = reset(mvc, raw, NEW_PASSWORD).andReturn();
        assertThat(reused.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(reused.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("AUTH_PASSWORD_RESET_INVALID");
    }

    @Test
    void invalidExpiredAndUsedTokensShareTheSamePublicError() throws Exception {
        MockMvc mvc = mvc();
        String email = "expiry-" + UUID.randomUUID() + "@example.test";
        register(mvc, email);
        forgot(mvc, email);
        String expired = notifications.take().rawToken();
        clock.advance(Duration.ofMinutes(31));

        MvcResult invalid = reset(mvc, "not-a-real-token", NEW_PASSWORD).andReturn();
        MvcResult expiredResult = reset(mvc, expired, NEW_PASSWORD).andReturn();
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(expiredResult.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(invalid.getResponse().getContentAsString(), "$.code"))
                .isEqualTo(JsonPath.<String>read(expiredResult.getResponse().getContentAsString(), "$.code"));
    }

    @Test
    void concurrentResetAllowsOnlyOneSuccess() throws Exception {
        MockMvc mvc = mvc();
        String email = "concurrent-reset-" + UUID.randomUUID() + "@example.test";
        register(mvc, email);
        forgot(mvc, email);
        String raw = notifications.take().rawToken();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> resetResult(start, raw));
            var second = executor.submit(() -> resetResult(start, raw));
            start.countDown();
            assertThat(java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "INVALID");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE status='USED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void resetRollsBackPasswordTokenAndSessionWhenPersistenceFailsAfterWrites() throws Exception {
        MockMvc mvc = mvc();
        String email = "rollback-reset-" + UUID.randomUUID() + "@example.test";
        register(mvc, email);
        login(mvc, email, OLD_PASSWORD);
        forgot(mvc, email);
        String raw = notifications.take().rawToken();
        repository.failAfterReset.set(true);

        assertThatThrownBy(() -> recovery.resetPassword(raw, NEW_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("intentional reset persistence failure");

        String passwordHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE normalized_email=?", String.class, email);
        assertThat(passwords.matches(OLD_PASSWORD, passwordHash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM password_reset_tokens", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE status='ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE status='ACTIVE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void forgotAndResetRequireCsrf() throws Exception {
        MockMvc mvc = mvc();
        assertThat(mvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"csrf@example.test\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"token\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    private String resetResult(CountDownLatch start, String raw) throws InterruptedException {
        start.await();
        try {
            recovery.resetPassword(raw, NEW_PASSWORD);
            return "SUCCESS";
        } catch (RuntimeException exception) {
            return "INVALID";
        }
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void register(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + OLD_PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private void login(MockMvc mvc, String email, String password) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
    }

    private MvcResult forgot(MockMvc mvc, String email) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        return mvc.perform(post("/api/v1/auth/forgot-password")
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions reset(MockMvc mvc, String raw, String password)
            throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        return mvc.perform(post("/api/v1/auth/reset-password")
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"" + password + "\"}"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordRecoveryTestConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        CapturingNotificationPort capturingNotificationPort() {
            return new CapturingNotificationPort();
        }

        @Bean
        @Primary
        FailingPasswordResetRepository failingPasswordResetRepository(JdbcPasswordResetRepository delegate) {
            return new FailingPasswordResetRepository(delegate);
        }
    }

    static final class MutableClock extends Clock {
        private final Instant initial = Instant.now().plusSeconds(60);
        private volatile Instant now = initial;

        void advance(Duration duration) { now = now.plus(duration); }
        void reset() { now = initial; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class CapturingNotificationPort implements PasswordResetNotificationPort {
        private final ConcurrentLinkedQueue<Notification> values = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean fail = new AtomicBoolean();
        @Override public void sendPasswordReset(String email, String rawToken, Instant expiresAt) {
            if (fail.get()) {
                throw new IllegalStateException("intentional notification failure");
            }
            values.add(new Notification(email, rawToken, expiresAt));
        }
        Notification take() { return values.remove(); }
        int size() { return values.size(); }
        void clear() { values.clear(); }
    }

    static final class FailingPasswordResetRepository implements PasswordResetRepository {
        private final PasswordResetRepository delegate;
        private final AtomicBoolean failAfterReset = new AtomicBoolean();
        FailingPasswordResetRepository(PasswordResetRepository delegate) { this.delegate = delegate; }
        @Override public java.util.Optional<UUID> findUserIdByNormalizedEmail(String email) {
            return delegate.findUserIdByNormalizedEmail(email);
        }
        @Override public void revokeActiveTokens(UUID userId, Instant now, String reason) {
            delegate.revokeActiveTokens(userId, now, reason);
        }
        @Override public void createToken(UUID id, UUID userId, String hash, Instant expiresAt, Instant now) {
            delegate.createToken(id, userId, hash, expiresAt, now);
        }
        @Override public StoredPasswordResetToken lockByHash(String hash) { return delegate.lockByHash(hash); }
        @Override public void resetPasswordAndRevokeAuthentication(
                StoredPasswordResetToken token, String passwordHash, Instant now
        ) {
            delegate.resetPasswordAndRevokeAuthentication(token, passwordHash, now);
            if (failAfterReset.get()) {
                throw new IllegalStateException("intentional reset persistence failure");
            }
        }
    }

    private record Notification(String email, String rawToken, Instant expiresAt) { }
}
