package com.bookflow.authentication;

import com.bookflow.BookFlowApplication;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.bookflow.authentication.repository.UserAuthenticationRepository;
import com.bookflow.authentication.config.AuthenticationProperties;
import com.bookflow.authentication.token.RefreshTokenGenerator;
import com.bookflow.authentication.token.JwtSigningMaterial;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.bookflow.authentication.domain.RotatedRefreshToken;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BookFlowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class UserLoginIT {
    private static final String PASSWORD = "BookFlow login password 2026!";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAuthenticationRepository repository;
    @Autowired TransactionTemplate transactions;
    @Autowired RefreshTokenGenerator refreshTokens;
    @Autowired JwtSigningMaterial signingMaterial;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired AuthenticationProperties authenticationProperties;

    @AfterEach
    void clearAuthenticationTables() {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, auth_sessions, users CASCADE");
    }

    @Test
    void loginRequiresCsrfAndPersistsOnlyRefreshHash() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "login-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  " + email.toUpperCase() + "  \",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        MvcResult missingCsrf = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        assertThat(missingCsrf.getResponse().getStatus()).isEqualTo(403);

        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        var csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  " + email.toUpperCase() + "  \",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = login.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.tokenType")).isEqualTo("Bearer");
        assertThat(JsonPath.<String>read(body, "$.accessToken")).contains(".");
        assertThat(body).doesNotContain("refreshToken", "passwordHash", PASSWORD);
        String setCookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("bookflow_refresh=", "HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions s JOIN users u ON s.user_id=u.id WHERE u.normalized_email=?", Integer.class, email)).isEqualTo(1);
        String rawRefreshToken = login.getResponse().getCookie("bookflow_refresh").getValue();
        String hash = jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
        assertThat(hash).isEqualTo(refreshTokens.sha256(rawRefreshToken)).hasSize(64).isNotEqualTo(rawRefreshToken);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash=?", Integer.class, rawRefreshToken)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM users WHERE normalized_email=?", Boolean.class, email)).isTrue();
    }

    @Test
    void unknownEmailAndWrongPasswordReturnTheSameSafeUnauthorizedError() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "wrong-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isCreated());
        assertThat(login(mvc, email, "incorrect password unchanged!").getResponse().getStatus()).isEqualTo(401);
        MvcResult unknown = login(mvc, "unknown-"+UUID.randomUUID()+"@example.test", PASSWORD);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(unknown.getResponse().getContentAsString(), "$.code")).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions s JOIN users u ON s.user_id=u.id WHERE u.normalized_email=?", Integer.class, email)).isZero();
    }

    @Test
    void documentsLoginCsrfHeaderExampleAndProblemDetailErrorsInOpenApi() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        String document = result.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(document, "$.paths['/api/v1/auth/login'].post.parameters[0].name"))
                .isEqualTo("X-XSRF-TOKEN");
        assertThat(JsonPath.<String>read(document, "$.paths['/api/v1/auth/login'].post.parameters[0].in"))
                .isEqualTo("header");
        assertThat(JsonPath.<Boolean>read(document, "$.paths['/api/v1/auth/login'].post.parameters[0].required"))
                .isTrue();
        assertThat(document).contains("demo.user@example.test", "BookFlow demo password 2026!");
        assertThat(document).doesNotContain("\"401\":{\"description\":\"Invalid credentials\",\"content\":{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/LoginResponse\"}}");
    }

    @Test
    void refreshRotatesTokenAndReuseCommitsFamilyRevocation() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        LoginMaterial material = registeredLogin(mvc, "rotate-" + UUID.randomUUID() + "@example.test");
        MvcResult refreshed = refresh(mvc, material.refreshToken());
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        String next = refreshed.getResponse().getCookie("bookflow_refresh").getValue();
        assertThat(jdbc.queryForObject("SELECT status FROM refresh_tokens WHERE token_hash=(SELECT token_hash FROM refresh_tokens ORDER BY created_at LIMIT 1)", String.class)).isEqualTo("ROTATED");
        assertThat(refresh(mvc, material.refreshToken()).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT status FROM auth_sessions", String.class)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE status='REVOKED'", Integer.class)).isGreaterThan(0);
    }

    @Test
    void concurrentRefreshAllowsOnlyOneSuccess() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String token = registeredLogin(mvc, "race-" + UUID.randomUUID() + "@example.test").refreshToken();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> first = executor.submit(() -> { start.await(); return refresh(mvc, token).getResponse().getStatus(); });
            Future<Integer> second = executor.submit(() -> { start.await(); return refresh(mvc, token).getResponse().getStatus(); });
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
        }
    }

    @Test
    void logoutCurrentAndLogoutAllKeepOtherUsersUntouched() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "logout-" + UUID.randomUUID() + "@example.test";
        LoginMaterial first = registeredLogin(mvc, email);
        LoginMaterial second = loginMaterial(mvc, email, PASSWORD);
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/v1/auth/logout").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"), new jakarta.servlet.http.Cookie("bookflow_refresh", first.refreshToken())).header("X-XSRF-TOKEN", csrfToken)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE status='ACTIVE'", Integer.class)).isEqualTo(1);
        String other = "other-" + UUID.randomUUID() + "@example.test";
        LoginMaterial otherLogin = registeredLogin(mvc, other);
        MvcResult allCsrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String allToken = JsonPath.read(allCsrf.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/v1/auth/logout-all").cookie(allCsrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", allToken).header("Authorization", "Bearer " + second.accessToken())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions s JOIN users u ON u.id=s.user_id WHERE u.normalized_email=? AND s.status='ACTIVE'", Integer.class, email)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions s JOIN users u ON u.id=s.user_id WHERE u.normalized_email=? AND s.status='ACTIVE'", Integer.class, other)).isEqualTo(1);
    }

    @Test
    void jwtAndCsrfProtectLogoutAllWithoutLeakingAuthenticationDetails() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        LoginMaterial material = registeredLogin(mvc, "jwt-" + UUID.randomUUID() + "@example.test");
        assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(1).containsValue(jwtDecoder);
        assertThat(jwtDecoder).isSameAs(signingMaterial.decoder());
        var decoded = jwtDecoder.decode(material.accessToken());
        assertThat(decoded.getSubject()).isNotBlank();
        assertThat(decoded.getHeaders().get("typ")).isEqualTo("at+jwt");
        UUID authenticatedUserId = UUID.fromString(decoded.getSubject());
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        assertThat(mvc.perform(post("/api/v1/auth/logout-all").cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/v1/auth/logout-all").cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token).header("Authorization", "Bearer " + material.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND status='ACTIVE'", Integer.class, authenticatedUserId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND status='REVOKED' AND revoke_reason='LOGOUT_ALL'", Integer.class, authenticatedUserId)).isEqualTo(1);
        MvcResult freshCsrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String fresh = JsonPath.read(freshCsrf.getResponse().getContentAsString(), "$.token");
        MvcResult bad = mvc.perform(post("/api/v1/auth/logout-all").cookie(freshCsrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", fresh).header("Authorization", "Bearer " + material.accessToken().substring(0, material.accessToken().length() - 2) + "xx")).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(401);
        assertThat(bad.getResponse().getContentAsString()).doesNotContain("signature", "key", "token value");
    }

    @Test
    void rfc9068JwtValidationRejectsWrongTypeMissingTypeExpirySignatureIssuerAndAudience() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        LoginMaterial material = registeredLogin(mvc, "jwt-validation-" + UUID.randomUUID() + "@example.test");
        var valid = jwtDecoder.decode(material.accessToken());
        UUID userId = UUID.fromString(valid.getSubject());
        UUID sessionId = UUID.fromString(valid.getClaimAsString("sid"));
        Instant now = Instant.now();

        assertBearerRejected(mvc, issueToken(signingMaterial.encoder(), userId, sessionId, "JWT",
                authenticationProperties.accessToken().issuer(), authenticationProperties.accessToken().audience(), now, now.plusSeconds(300)));
        assertBearerRejected(mvc, issueToken(signingMaterial.encoder(), userId, sessionId, null,
                authenticationProperties.accessToken().issuer(), authenticationProperties.accessToken().audience(), now, now.plusSeconds(300)));
        assertBearerRejected(mvc, issueToken(signingMaterial.encoder(), userId, sessionId, "at+jwt",
                authenticationProperties.accessToken().issuer(), authenticationProperties.accessToken().audience(), now.minusSeconds(120), now.minusSeconds(60)));
        assertBearerRejected(mvc, issueToken(differentKeyEncoder(), userId, sessionId, "at+jwt",
                authenticationProperties.accessToken().issuer(), authenticationProperties.accessToken().audience(), now, now.plusSeconds(300)));
        assertBearerRejected(mvc, issueToken(signingMaterial.encoder(), userId, sessionId, "at+jwt",
                "urn:bookflow:wrong-issuer", authenticationProperties.accessToken().audience(), now, now.plusSeconds(300)));
        assertBearerRejected(mvc, issueToken(signingMaterial.encoder(), userId, sessionId, "at+jwt",
                authenticationProperties.accessToken().issuer(), "wrong-audience", now, now.plusSeconds(300)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND status='ACTIVE'", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void csrfRejectsRefreshLogoutAndLogoutAllBeforeBusinessLogic() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        LoginMaterial material = registeredLogin(mvc, "csrf-" + UUID.randomUUID() + "@example.test");
        assertThat(mvc.perform(post("/api/v1/auth/refresh").cookie(new jakarta.servlet.http.Cookie("bookflow_refresh", material.refreshToken()))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post("/api/v1/auth/logout").cookie(new jakarta.servlet.http.Cookie("bookflow_refresh", material.refreshToken()))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", "Bearer " + material.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(403);

        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(mvc.perform(post("/api/v1/auth/refresh").cookie(csrfCookie,
                new jakarta.servlet.http.Cookie("bookflow_refresh", material.refreshToken()))
                .header("X-XSRF-TOKEN", "wrong-token")).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post("/api/v1/auth/logout").cookie(csrfCookie,
                new jakarta.servlet.http.Cookie("bookflow_refresh", material.refreshToken()))
                .header("X-XSRF-TOKEN", "wrong-token")).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post("/api/v1/auth/logout-all").cookie(csrfCookie)
                .header("X-XSRF-TOKEN", "wrong-token")
                .header("Authorization", "Bearer " + material.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE status='ACTIVE'", Integer.class)).isGreaterThan(0);
    }

    @Test
    void transactionRollbackLeavesRefreshTokenActiveWhenRotationFailsMidway() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        LoginMaterial material = registeredLogin(mvc, "rollback-" + UUID.randomUUID() + "@example.test");
        String hash = refreshTokens.sha256(material.refreshToken());
        UUID familyId = jdbc.queryForObject("SELECT family_id FROM refresh_tokens WHERE token_hash=?", UUID.class, hash);
        int tokensBefore = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE family_id=?", Integer.class, familyId);
        try {
            transactions.executeWithoutResult(status -> {
                var current = repository.lockRefreshToken(hash);
                var generated = refreshTokens.generate();
                repository.rotateRefreshToken(current, new RotatedRefreshToken(generated.rawToken(), generated.hash()), java.time.Instant.now().plusSeconds(60), current.inactivityExpiresAt(), current.absoluteExpiresAt());
                throw new IllegalStateException("intentional test rollback");
            });
        } catch (IllegalStateException ignored) { }
        assertThat(jdbc.queryForObject("SELECT status FROM refresh_tokens WHERE token_hash=?", String.class, hash)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT replaced_by_token_id IS NULL FROM refresh_tokens WHERE token_hash=?", Boolean.class, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE family_id=?", Integer.class, familyId)).isEqualTo(tokensBefore);
    }

    private MvcResult login(MockMvc mvc, String email, String password) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        return mvc.perform(post("/api/v1/auth/login").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}")).andReturn();
    }

    private LoginMaterial registeredLogin(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isCreated());
        return loginMaterial(mvc, email, PASSWORD);
    }
    private LoginMaterial loginMaterial(MockMvc mvc, String email, String password) throws Exception {
        MvcResult result = login(mvc, email, password);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return new LoginMaterial(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"), result.getResponse().getCookie("bookflow_refresh").getValue());
    }
    private MvcResult refresh(MockMvc mvc, String token) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        return mvc.perform(post("/api/v1/auth/refresh").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"), new jakarta.servlet.http.Cookie("bookflow_refresh", token)).header("X-XSRF-TOKEN", csrfToken)).andReturn();
    }

    private void assertBearerRejected(MockMvc mvc, String accessToken) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        MvcResult result = mvc.perform(post("/api/v1/auth/logout-all")
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Jwt", "signature", "issuer", "audience", "token value", "stackTrace");
    }

    private String issueToken(JwtEncoder encoder, UUID userId, UUID sessionId, String type,
                              String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        JwsHeader.Builder headers = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingMaterial.keyId());
        if (type != null) {
            headers.type(type);
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("client_id", authenticationProperties.accessToken().clientId())
                .claim("sid", sessionId.toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(headers.build(), claims)).getTokenValue();
    }

    private JwtEncoder differentKeyEncoder() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate())
                .keyID(signingMaterial.keyId())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
    }

    private record LoginMaterial(String accessToken, String refreshToken) { }
}
