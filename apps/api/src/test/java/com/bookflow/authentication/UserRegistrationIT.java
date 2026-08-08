package com.bookflow.authentication;

import com.bookflow.BookFlowApplication;
import com.bookflow.authentication.password.Argon2idPasswordHasher;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BookFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "debug=false"
)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class UserRegistrationIT {

    private static final String VALID_PASSWORD = "BookFlow registration 2026!";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Argon2idPasswordHasher passwordHasher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @AfterEach
    void verifiesRegistrationDoesNotCreateSessionsOrRefreshTokens() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void registersNormalizedUserWithArgon2idHashAndSafeResponse() throws Exception {
        String rawEmail = "  Huy@Example.COM  ";
        MvcResult result = register(rawEmail, VALID_PASSWORD)
                .andExpect(status().isCreated())
                .andReturn();

        String body = responseBody(result);
        Map<String, Object> response = JsonPath.read(body, "$");
        assertThat(response).containsOnlyKeys("id", "email", "status", "createdAt");
        assertThat(response.get("email")).isEqualTo("huy@example.com");
        assertThat(response.get("status")).isEqualTo("ACTIVE");
        assertThat(body).doesNotContain(VALID_PASSWORD, "passwordHash", "password_hash", "token", "session");

        Map<String, Object> user = jdbcTemplate.queryForMap("""
                SELECT normalized_email, password_hash, status, password_changed_at,
                       last_login_at, created_at, updated_at
                FROM users WHERE normalized_email = ?
                """, "huy@example.com");
        String storedHash = (String) user.get("password_hash");
        assertThat(user.get("normalized_email")).isEqualTo("huy@example.com");
        assertThat(user.get("status")).isEqualTo("ACTIVE");
        assertThat(user.get("password_changed_at")).isNotNull();
        assertThat(user.get("created_at")).isNotNull();
        assertThat(user.get("updated_at")).isNotNull();
        assertThat(user.get("last_login_at")).isNull();
        assertThat(storedHash).startsWith("$argon2id$").isNotEqualTo(VALID_PASSWORD);
        assertThat(passwordHasher.matches(VALID_PASSWORD, storedHash)).isTrue();
    }

    @Test
    void rejectsDuplicateNormalizedEmailWithoutDatabaseInternals() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        register("  " + email.toUpperCase() + "  ", VALID_PASSWORD)
                .andExpect(status().isCreated());

        MvcResult duplicate = register(email, VALID_PASSWORD)
                .andExpect(status().isConflict())
                .andReturn();

        String body = responseBody(duplicate);
        assertThat(JsonPath.<String>read(body, "$.code")).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(body).doesNotContain(
                "users_normalized_email_key",
                "PSQLException",
                "SQLException",
                "stackTrace",
                VALID_PASSWORD
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE normalized_email = ?",
                Integer.class,
                email
        )).isEqualTo(1);
    }

    @Test
    void allowsOnlyOneConcurrentRegistrationForTheSameNormalizedEmail() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.test";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(
                    executor.submit(concurrentRegistration("  " + email.toUpperCase() + "  ", ready, start)),
                    executor.submit(concurrentRegistration(email, ready, start))
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(results.getFirst().get(20, TimeUnit.SECONDS), results.getLast().get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE normalized_email = ?",
                Integer.class,
                email
        )).isEqualTo(1);
    }

    @Test
    void documentsTheRegistrationContractInOpenApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = responseBody(result);
        assertThat(JsonPath.<String>read(body, "$.paths['/api/v1/auth/register'].post.responses['201'].description"))
                .isEqualTo("User registered");
        assertThat(JsonPath.<String>read(body, "$.paths['/api/v1/auth/register'].post.responses['409'].description"))
                .isEqualTo("Email already registered");
    }

    private Callable<Integer> concurrentRegistration(
            String email,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent registration test did not start.");
            }
            return register(email, VALID_PASSWORD).andReturn().getResponse().getStatus();
        };
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
