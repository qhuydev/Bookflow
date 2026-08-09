package com.bookflow.businesses;

import com.bookflow.BookFlowApplication;
import com.bookflow.support.PostgresTestcontainerConfiguration;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BookFlowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class BusinessConfigurationIT {
    private static final String PASSWORD = "Business configuration password 2026!";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clearTables() { jdbc.execute("TRUNCATE TABLE business_memberships, businesses, refresh_tokens, auth_sessions, users CASCADE"); }

    @Test
    void ownerAndAdminCanPartiallyUpdateConfigurationWithoutLosingOtherValues() throws Exception {
        MockMvc mvc = mvc();
        Login owner = login(mvc, "owner-" + UUID.randomUUID() + "@example.test");
        Login admin = login(mvc, "admin-" + UUID.randomUUID() + "@example.test");
        UUID business = business("config-" + UUID.randomUUID(), "ACTIVE");
        membership(business, owner.userId(), "OWNER", "ACTIVE");
        membership(business, admin.userId(), "ADMIN", "ACTIVE");

        MvcResult ownerUpdate = patch(mvc, owner.token(), business, """
                {"name":"  New Salon  ","slug":"  NEW-SALON  ","currencyCode":"usd",
                 "cancellationPolicy":"strict","maxBookingAdvanceDays":30}
                """);
        assertThat(ownerUpdate.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(ownerUpdate.getResponse().getContentAsString(), "$.slug")).isEqualTo("new-salon");
        assertThat(jdbc.queryForObject("SELECT currency_code FROM businesses WHERE id=?", String.class, business)).isEqualTo("USD");
        assertThat(jdbc.queryForObject("SELECT time_zone FROM businesses WHERE id=?", String.class, business)).isEqualTo("UTC");

        MvcResult adminUpdate = patch(mvc, admin.token(), business, "{\"timeZone\":\"Asia/Ho_Chi_Minh\",\"type\":\"SPA\"}");
        assertThat(adminUpdate.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT name FROM businesses WHERE id=?", String.class, business)).isEqualTo("New Salon");
        assertThat(jdbc.queryForObject("SELECT business_type || ':' || time_zone FROM businesses WHERE id=?", String.class, business)).isEqualTo("SPA:Asia/Ho_Chi_Minh");
    }

    @Test
    void staffOtherTenantAndInactiveMembershipCannotUpdate() throws Exception {
        MockMvc mvc = mvc();
        Login owner = login(mvc, "owner-" + UUID.randomUUID() + "@example.test");
        Login staff = login(mvc, "staff-" + UUID.randomUUID() + "@example.test");
        Login outsider = login(mvc, "outsider-" + UUID.randomUUID() + "@example.test");
        UUID business = business("protected-" + UUID.randomUUID(), "ACTIVE");
        membership(business, owner.userId(), "OWNER", "ACTIVE");
        membership(business, staff.userId(), "STAFF", "ACTIVE");
        assertThat(patch(mvc, staff.token(), business, "{\"name\":\"Denied\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patch(mvc, outsider.token(), business, "{\"name\":\"Hidden\"}").getResponse().getStatus()).isEqualTo(404);
        jdbc.update("UPDATE business_memberships SET status='SUSPENDED' WHERE tenant_id=? AND user_id=?", business, owner.userId());
        assertThat(patch(mvc, owner.token(), business, "{\"name\":\"Inactive\"}").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void validatesRequestAndMapsSlugConflictWithoutLeakingDatabase() throws Exception {
        MockMvc mvc = mvc();
        Login owner = login(mvc, "owner-" + UUID.randomUUID() + "@example.test");
        UUID business = business("one-" + UUID.randomUUID(), "ACTIVE");
        UUID duplicate = business("duplicate-" + UUID.randomUUID(), "ACTIVE");
        membership(business, owner.userId(), "OWNER", "ACTIVE");
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/businesses/{id}", business)
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", (String) JsonPath.read(csrf.getResponse().getContentAsString(), "$.token"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"No JWT\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(patch(mvc, owner.token(), business, "{}").getResponse().getStatus()).isEqualTo(400);
        assertThat(patch(mvc, owner.token(), business, "{\"currencyCode\":\"not-a-currency\"}").getResponse().getStatus()).isEqualTo(400);
        MvcResult conflict = patch(mvc, owner.token(), business, "{\"slug\":\"" + jdbc.queryForObject("SELECT slug FROM businesses WHERE id=?", String.class, duplicate) + "\"}");
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(conflict.getResponse().getContentAsString()).doesNotContain("businesses_slug_key", "SQLException", "stack");
    }

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private Login login(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?", UUID.class, email);
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        MvcResult result = mvc.perform(post("/api/v1/auth/login").cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andReturn();
        return new Login(id, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
    }
    private MvcResult patch(MockMvc mvc, String token, UUID id, String body) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/businesses/{id}", id).cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", (String) JsonPath.read(csrf.getResponse().getContentAsString(), "$.token")).header("Authorization", "Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }
    private UUID business(String slug, String status) { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO businesses (id,name,slug,business_type,time_zone,status) VALUES (?,?,?,'SALON','UTC',?)",id,"Business "+slug,slug,status); return id; }
    private void membership(UUID business, UUID user, String role, String status) { jdbc.update("INSERT INTO business_memberships (id,tenant_id,user_id,role,status) VALUES (?,?,?,?,?)",UUID.randomUUID(),business,user,role,status); }
    private record Login(UUID userId, String token) { }
}
