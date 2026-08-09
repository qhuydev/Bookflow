package com.bookflow.businesses;

import com.bookflow.BookFlowApplication;
import com.bookflow.businesses.api.CreateBusinessRequest;
import com.bookflow.businesses.application.BusinessCreationService;
import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.repository.BusinessCreationRepository;
import com.bookflow.businesses.repository.JdbcBusinessCreationRepository;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
@Import({PostgresTestcontainerConfiguration.class, BusinessCreationIT.FailingMembershipConfiguration.class})
class BusinessCreationIT {
    private static final String PASSWORD = "Business create password 2026!";

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessCreationService businessCreationService;
    @Autowired FailingMembershipRepository failingMembershipRepository;

    @AfterEach
    void clearTables() {
        failingMembershipRepository.failMembershipInsert.set(false);
        jdbc.execute("TRUNCATE TABLE business_memberships, businesses, refresh_tokens, auth_sessions, users CASCADE");
    }

    @Test
    void authenticatedUserCreatesNormalizedBusinessAndActiveOwnerMembership() throws Exception {
        MockMvc mvc = secureMvc();
        Login login = registerAndLogin(mvc, "owner-" + UUID.randomUUID() + "@example.test");

        MvcResult created = create(mvc, login.accessToken(), "{\"name\":\"  Huy Hair Studio  \",\"slug\":\"  HUY-HAIR-STUDIO  \",\"type\":\"SALON\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(created.getResponse().getHeader("Location")).matches("/api/v1/businesses/[0-9a-f-]{36}");
        String body = created.getResponse().getContentAsString();
        UUID businessId = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(JsonPath.<String>read(body, "$.name")).isEqualTo("Huy Hair Studio");
        assertThat(JsonPath.<String>read(body, "$.slug")).isEqualTo("huy-hair-studio");
        assertThat(JsonPath.<String>read(body, "$.membership.role")).isEqualTo("OWNER");
        assertThat(JsonPath.<String>read(body, "$.membership.status")).isEqualTo("ACTIVE");
        assertThat(body).doesNotContain("password", "token", "userId", "tenantId");

        assertThat(jdbc.queryForObject("SELECT name FROM businesses WHERE id=?", String.class, businessId)).isEqualTo("Huy Hair Studio");
        assertThat(jdbc.queryForObject("SELECT slug FROM businesses WHERE id=?", String.class, businessId)).isEqualTo("huy-hair-studio");
        assertThat(jdbc.queryForObject("SELECT status FROM businesses WHERE id=?", String.class, businessId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM business_memberships WHERE user_id=?", UUID.class, login.userId())).isEqualTo(businessId);
        assertThat(jdbc.queryForObject("SELECT role || ':' || status FROM business_memberships WHERE user_id=?", String.class, login.userId())).isEqualTo("OWNER:ACTIVE");
        assertThat(jdbc.queryForObject("SELECT created_at IS NOT NULL AND updated_at IS NOT NULL FROM businesses WHERE id=?", Boolean.class, businessId)).isTrue();
    }

    @Test
    void securityAndCsrfProtectBusinessCreationAndUnknownFieldsAreRejected() throws Exception {
        MockMvc mvc = secureMvc();
        String request = validRequest("protected-" + UUID.randomUUID());
        MvcResult csrf = csrf(mvc);
        assertThat(mvc.perform(post("/api/v1/businesses").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", csrfToken(csrf)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/v1/businesses").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", csrfToken(csrf)).header("Authorization", "Bearer not-a-jwt")
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn().getResponse().getStatus()).isEqualTo(401);

        Login login = registerAndLogin(mvc, "security-" + UUID.randomUUID() + "@example.test");
        assertThat(mvc.perform(post("/api/v1/businesses").header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn().getResponse().getStatus()).isEqualTo(403);
        MvcResult rejected = create(mvc, login.accessToken(), request.substring(0, request.length() - 1) + ",\"ownerId\":\"" + UUID.randomUUID() + "\"}");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(rejected.getResponse().getContentAsString()).doesNotContain("exception", "stack", "SQLException");
        assertThatThrownBy(() -> businessCreationService.create(UUID.randomUUID(),
                new CreateBusinessRequest("No user", "no-user", "SALON", "UTC")))
                .isInstanceOf(CurrentBusinessUserUnavailableException.class);
    }

    @Test
    void invalidBusinessInputReturnsBadRequestBeforeDatabaseConstraint() throws Exception {
        MockMvc mvc = secureMvc();
        Login login = registerAndLogin(mvc, "validation-" + UUID.randomUUID() + "@example.test");
        List<String> requests = List.of(
                "{\"name\":\"   \",\"slug\":\"valid-slug\",\"type\":\"SALON\",\"timeZone\":\"UTC\"}",
                "{\"name\":\"Name\",\"slug\":\"bad slug\",\"type\":\"SALON\",\"timeZone\":\"UTC\"}",
                "{\"name\":\"Name\",\"slug\":\"valid-slug\",\"type\":\"INVALID\",\"timeZone\":\"UTC\"}",
                "{\"name\":\"Name\",\"slug\":\"valid-slug\",\"type\":\"SALON\",\"timeZone\":\"Not/A_Time_Zone\"}",
                "{\"name\":\"" + "a".repeat(201) + "\",\"slug\":\"valid-slug\",\"type\":\"SALON\",\"timeZone\":\"UTC\"}"
        );
        for (String request : requests) {
            assertThat(create(mvc, login.accessToken(), request).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM businesses", Integer.class)).isZero();
    }

    @Test
    void duplicateAndConcurrentSlugReturnConflictWithoutOrphanedBusinesses() throws Exception {
        MockMvc mvc = secureMvc();
        Login first = registerAndLogin(mvc, "first-" + UUID.randomUUID() + "@example.test");
        Login second = registerAndLogin(mvc, "second-" + UUID.randomUUID() + "@example.test");
        String slug = "same-" + UUID.randomUUID();
        assertThat(create(mvc, first.accessToken(), validRequest(slug)).getResponse().getStatus()).isEqualTo(201);
        MvcResult duplicate = create(mvc, second.accessToken(), validRequest(slug));
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(duplicate.getResponse().getContentAsString(), "$.code")).isEqualTo("BUSINESS_SLUG_ALREADY_EXISTS");
        assertThat(duplicate.getResponse().getContentAsString()).doesNotContain("businesses_slug_key", "SQLException", "stack");

        String racingSlug = "race-" + UUID.randomUUID();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> one = executor.submit(() -> { start.await(); return create(mvc, first.accessToken(), validRequest(racingSlug)).getResponse().getStatus(); });
            Future<Integer> two = executor.submit(() -> { start.await(); return create(mvc, second.accessToken(), validRequest(racingSlug)).getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM businesses WHERE slug=?", Integer.class, racingSlug)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_memberships m JOIN businesses b ON b.id=m.tenant_id WHERE b.slug=? AND m.role='OWNER' AND m.status='ACTIVE'", Integer.class, racingSlug)).isEqualTo(1);
    }

    @Test
    void membershipInsertFailureRollsBackBusinessOnPostgres() {
        UUID userId = registerDirectly("rollback-" + UUID.randomUUID() + "@example.test");
        failingMembershipRepository.failMembershipInsert.set(true);
        assertThatThrownBy(() -> businessCreationService.create(userId,
                new CreateBusinessRequest("Rollback business", "rollback-" + UUID.randomUUID(), "SALON", "UTC")))
                .isInstanceOf(IllegalStateException.class).hasMessage("intentional membership failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM businesses", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_memberships", Integer.class)).isZero();
    }

    private MockMvc secureMvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    private Login registerAndLogin(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?", UUID.class, email);
        MvcResult token = csrf(mvc);
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(token.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", csrfToken(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Login(userId, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private UUID registerDirectly(String email) {
        String hash = "$argon2id$v=19$m=19456,t=2,p=1$MDEyMzQ1Njc4OWFiY2RlZg$JECBTIy1wUcdeUcLS8qOgcKL4RXVtvroYGABSMcFPhQ";
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, normalized_email, password_hash, status, password_changed_at) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)", id, email, hash);
        return id;
    }

    private MvcResult create(MockMvc mvc, String accessToken, String body) throws Exception {
        MvcResult csrf = csrf(mvc);
        return mvc.perform(post("/api/v1/businesses").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", csrfToken(csrf)).header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult csrf(MockMvc mvc) throws Exception { return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn(); }
    private String csrfToken(MvcResult csrf) throws Exception { return JsonPath.read(csrf.getResponse().getContentAsString(), "$.token"); }
    private String validRequest(String slug) { return "{\"name\":\"Business " + slug + "\",\"slug\":\"" + slug + "\",\"type\":\"SALON\",\"timeZone\":\"UTC\"}"; }

    private record Login(UUID userId, String accessToken) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingMembershipConfiguration {
        @Bean
        @Primary
        FailingMembershipRepository failingMembershipRepository(JdbcBusinessCreationRepository delegate) {
            return new FailingMembershipRepository(delegate);
        }
    }

    static final class FailingMembershipRepository implements BusinessCreationRepository {
        private final JdbcBusinessCreationRepository delegate;
        private final AtomicBoolean failMembershipInsert = new AtomicBoolean(false);

        FailingMembershipRepository(JdbcBusinessCreationRepository delegate) { this.delegate = delegate; }
        @Override public boolean hasActiveUser(UUID userId) { return delegate.hasActiveUser(userId); }
        @Override public Business insertBusiness(UUID id, String name, String slug, BusinessType type, String timeZone, BusinessStatus status) { return delegate.insertBusiness(id, name, slug, type, timeZone, status); }
        @Override public void insertInitialOwnerMembership(UUID tenantId, UUID userId) {
            if (failMembershipInsert.get()) { throw new IllegalStateException("intentional membership failure"); }
            delegate.insertInitialOwnerMembership(tenantId, userId);
        }
    }
}
