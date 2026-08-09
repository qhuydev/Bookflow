package com.bookflow.businesses;

import com.bookflow.BookFlowApplication;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.bookflow.businesses.authorization.TenantAuthorizationService;
import com.bookflow.businesses.authorization.TenantPermission;
import com.bookflow.businesses.authorization.TenantPermissionDeniedException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BookFlowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class BusinessQueryIT {
    private static final String PASSWORD = "Business query password 2026!";

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantAuthorizationService tenantAuthorization;

    @AfterEach
    void clearTables() {
        jdbc.execute("TRUNCATE TABLE business_memberships, businesses, refresh_tokens, auth_sessions, users CASCADE");
    }

    @Test
    void userSeesOnlyOwnActiveBusinessesAndOwnMembership() throws Exception {
        MockMvc mvc = secureMvc();
        Login first = registerAndLogin(mvc, "first-" + UUID.randomUUID() + "@example.test");
        Login second = registerAndLogin(mvc, "second-" + UUID.randomUUID() + "@example.test");
        UUID ownerBusiness = insertBusiness("owner-" + UUID.randomUUID(), "ACTIVE");
        UUID staffBusiness = insertBusiness("staff-" + UUID.randomUUID(), "ACTIVE");
        UUID otherBusiness = insertBusiness("other-" + UUID.randomUUID(), "ACTIVE");
        insertMembership(ownerBusiness, first.userId(), "OWNER", "ACTIVE");
        insertMembership(staffBusiness, first.userId(), "STAFF", "ACTIVE");
        insertMembership(otherBusiness, second.userId(), "ADMIN", "ACTIVE");

        MvcResult result = mvc.perform(get("/api/v1/businesses").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.length()")).isEqualTo(2);
        assertThat(body).contains(ownerBusiness.toString(), staffBusiness.toString()).doesNotContain(otherBusiness.toString(), second.userId().toString());
        assertThat(JsonPath.<String>read(body, "$[0].membership.status")).isEqualTo("ACTIVE");
        assertThat(JsonPath.<String>read(body, "$[0].membership.role")).isIn("OWNER", "STAFF");
    }

    @Test
    void inaccessibleInactiveAndEmptyResultsDoNotExposeTenant() throws Exception {
        MockMvc mvc = secureMvc();
        Login first = registerAndLogin(mvc, "reader-" + UUID.randomUUID() + "@example.test");
        Login second = registerAndLogin(mvc, "other-" + UUID.randomUUID() + "@example.test");
        UUID otherBusiness = insertBusiness("private-" + UUID.randomUUID(), "ACTIVE");
        UUID suspendedMembership = insertBusiness("suspended-member-" + UUID.randomUUID(), "ACTIVE");
        UUID suspendedBusiness = insertBusiness("suspended-business-" + UUID.randomUUID(), "SUSPENDED");
        insertMembership(otherBusiness, second.userId(), "ADMIN", "ACTIVE");
        insertMembership(suspendedMembership, first.userId(), "OWNER", "SUSPENDED");
        insertMembership(suspendedBusiness, first.userId(), "OWNER", "ACTIVE");

        assertThat(mvc.perform(get("/api/v1/businesses").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo("[]");
        MvcResult hidden = mvc.perform(get("/api/v1/businesses/{id}", otherBusiness)
                .header("Authorization", "Bearer " + first.accessToken())).andExpect(status().isNotFound()).andReturn();
        assertThat(JsonPath.<String>read(hidden.getResponse().getContentAsString(), "$.code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(hidden.getResponse().getContentAsString()).doesNotContain("tenant", "membership", "SQLException", "stack");
        assertThat(mvc.perform(get("/api/v1/businesses/{id}", suspendedMembership)
                .header("Authorization", "Bearer " + first.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/v1/businesses/{id}", suspendedBusiness)
                .header("Authorization", "Bearer " + first.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void invalidBusinessIdAndMissingOrInvalidJwtUseSecurityAndErrorContracts() throws Exception {
        MockMvc mvc = secureMvc();
        assertThat(mvc.perform(get("/api/v1/businesses")).andReturn().getResponse().getStatus()).isEqualTo(401);
        Login login = registerAndLogin(mvc, "auth-" + UUID.randomUUID() + "@example.test");
        assertThat(mvc.perform(get("/api/v1/businesses/not-a-uuid").header("Authorization", "Bearer " + login.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(get("/api/v1/businesses").header("Authorization", "Bearer invalid-jwt"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void currentMembershipStateAndPermissionMatrixAreEnforcedAgainstPostgres() throws Exception {
        MockMvc mvc = secureMvc();
        Login owner = registerAndLogin(mvc, "owner-" + UUID.randomUUID() + "@example.test");
        Login admin = registerAndLogin(mvc, "admin-" + UUID.randomUUID() + "@example.test");
        Login staff = registerAndLogin(mvc, "staff-" + UUID.randomUUID() + "@example.test");
        UUID ownerBusiness = insertBusiness("owner-role-" + UUID.randomUUID(), "ACTIVE");
        UUID adminBusiness = insertBusiness("admin-role-" + UUID.randomUUID(), "ACTIVE");
        UUID staffBusiness = insertBusiness("staff-role-" + UUID.randomUUID(), "ACTIVE");
        insertMembership(ownerBusiness, owner.userId(), "OWNER", "ACTIVE");
        insertMembership(adminBusiness, admin.userId(), "ADMIN", "ACTIVE");
        insertMembership(staffBusiness, staff.userId(), "STAFF", "ACTIVE");

        assertThat(tenantAuthorization.requirePermission(owner.userId(), ownerBusiness, TenantPermission.BUSINESS_CLOSE).membershipRole()).isEqualTo(com.bookflow.businesses.domain.MembershipRole.OWNER);
        assertThat(tenantAuthorization.requirePermission(admin.userId(), adminBusiness, TenantPermission.BUSINESS_CONFIGURATION_MANAGE).membershipRole()).isEqualTo(com.bookflow.businesses.domain.MembershipRole.ADMIN);
        assertThat(tenantAuthorization.requirePermission(staff.userId(), staffBusiness, TenantPermission.BUSINESS_VIEW).membershipRole()).isEqualTo(com.bookflow.businesses.domain.MembershipRole.STAFF);
        assertThatThrownBy(() -> tenantAuthorization.requirePermission(staff.userId(), staffBusiness, TenantPermission.BUSINESS_CONFIGURATION_MANAGE))
                .isInstanceOf(TenantPermissionDeniedException.class);

        assertThat(mvc.perform(get("/api/v1/businesses/{id}", staffBusiness).header("Authorization", "Bearer " + staff.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        jdbc.update("UPDATE business_memberships SET status='REVOKED', revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=?", staffBusiness, staff.userId());
        assertThat(mvc.perform(get("/api/v1/businesses/{id}", staffBusiness).header("Authorization", "Bearer " + staff.accessToken()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    private MockMvc secureMvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    private Login registerAndLogin(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?", UUID.class, email);
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Login(userId, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private UUID insertBusiness(String slug, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO businesses (id, name, slug, business_type, time_zone, status) VALUES (?, ?, ?, 'SALON', 'UTC', ?)",
                id, "Business " + slug, slug, status);
        return id;
    }

    private void insertMembership(UUID businessId, UUID userId, String role, String status) {
        jdbc.update("INSERT INTO business_memberships (id, tenant_id, user_id, role, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), businessId, userId, role, status);
    }

    private record Login(UUID userId, String accessToken) { }
}
