package com.bookflow.employees;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BookFlowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class EmployeeManagementIT {
    private static final String PASSWORD = "Employee management password 2026!";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clear() {
        jdbc.execute("TRUNCATE TABLE employee_branch_assignments, employees, branches, business_memberships, businesses, refresh_tokens, auth_sessions, users CASCADE");
    }

    @Test void ownerAdminAndStaffFollowEmployeePermissionsAndCodeRules() throws Exception {
        MockMvc mvc = mvc(); Login owner = login(mvc), admin = login(mvc), staff = login(mvc);
        UUID business = business("employee-permissions", "ACTIVE");
        member(business, owner.id(), "OWNER", "ACTIVE"); member(business, admin.id(), "ADMIN", "ACTIVE"); member(business, staff.id(), "STAFF", "ACTIVE");

        MvcResult created = create(mvc, owner.token(), business, employeeBody(" emp-01 ", "First Employee"));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        UUID employee = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        assertThat(JsonPath.<String>read(created.getResponse().getContentAsString(), "$.code")).isEqualTo("EMP-01");
        assertThat(update(mvc, admin.token(), business, employee, "{\"fullName\":\"Changed Employee\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT full_name FROM employees WHERE id=?", String.class, employee)).isEqualTo("Changed Employee");
        assertThat(create(mvc, owner.token(), business, employeeBody("emp-01", "Duplicate")).getResponse().getStatus()).isEqualTo(409);
        assertThat(list(mvc, staff.token(), business).getResponse().getStatus()).isEqualTo(200);
        assertThat(create(mvc, staff.token(), business, employeeBody("emp-02", "Denied")).getResponse().getStatus()).isEqualTo(403);
        assertThat(update(mvc, staff.token(), business, employee, "{\"bio\":\"Denied\"}").getResponse().getStatus()).isEqualTo(403);
    }

    @Test void tenantIsolationArchiveAndInactiveMembershipReturnNeutralNotFound() throws Exception {
        MockMvc mvc = mvc(); Login a = login(mvc), b = login(mvc);
        UUID businessA = business("employee-a", "ACTIVE"), businessB = business("employee-b", "ACTIVE");
        member(businessA, a.id(), "OWNER", "ACTIVE"); member(businessB, b.id(), "OWNER", "ACTIVE");
        UUID employee = id(create(mvc, a.token(), businessA, employeeBody("same", "A")));
        assertThat(create(mvc, b.token(), businessB, employeeBody("same", "B")).getResponse().getStatus()).isEqualTo(201);
        assertThat(getEmployee(mvc, b.token(), businessA, employee).getResponse().getStatus()).isEqualTo(404);
        assertThat(update(mvc, a.token(), businessB, employee, "{\"bio\":\"cross\"}").getResponse().getStatus()).isEqualTo(404);
        assertThat(archive(mvc, b.token(), businessA, employee).getResponse().getStatus()).isEqualTo(404);
        assertThat(archive(mvc, a.token(), businessA, employee).getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT status FROM employees WHERE id=?", String.class, employee)).isEqualTo("ARCHIVED");
        assertThat(getEmployee(mvc, a.token(), businessA, employee).getResponse().getStatus()).isEqualTo(404);
        assertThat(list(mvc, a.token(), businessA).getResponse().getContentAsString()).isEqualTo("[]");
        jdbc.update("UPDATE business_memberships SET status='SUSPENDED' WHERE tenant_id=?", businessB);
        assertThat(list(mvc, b.token(), businessB).getResponse().getStatus()).isEqualTo(404);
    }

    @Test void assignmentsAreTenantScopedActiveOnlyAndIdempotent() throws Exception {
        MockMvc mvc = mvc(); Login owner = login(mvc), other = login(mvc);
        UUID business = business("employee-assign", "ACTIVE"), foreign = business("employee-foreign", "ACTIVE");
        member(business, owner.id(), "OWNER", "ACTIVE"); member(foreign, other.id(), "OWNER", "ACTIVE");
        UUID employee = id(create(mvc, owner.token(), business, employeeBody("assign", "Assignable")));
        UUID first = branch(business, "one", "ACTIVE"), second = branch(business, "two", "ACTIVE"), foreignBranch = branch(foreign, "foreign", "ACTIVE");
        assertThat(assign(mvc, owner.token(), business, employee, first).getResponse().getStatus()).isEqualTo(204);
        assertThat(assign(mvc, owner.token(), business, employee, first).getResponse().getStatus()).isEqualTo(204);
        assertThat(assign(mvc, owner.token(), business, employee, second).getResponse().getStatus()).isEqualTo(204);
        assertThat(branches(mvc, owner.token(), business, employee).getResponse().getContentAsString()).contains(first.toString(), second.toString());
        assertThat(assign(mvc, owner.token(), business, employee, foreignBranch).getResponse().getStatus()).isEqualTo(404);
        assertThat(unassign(mvc, owner.token(), business, employee, first).getResponse().getStatus()).isEqualTo(204);
        assertThat(unassign(mvc, owner.token(), business, employee, first).getResponse().getStatus()).isEqualTo(204);
        jdbc.update("UPDATE branches SET status='ARCHIVED' WHERE id=?", second);
        assertThat(assign(mvc, owner.token(), business, employee, second).getResponse().getStatus()).isEqualTo(404);
    }

    @Test void jwtAndCsrfAreRequiredAndInvalidPathUuidIsBadRequest() throws Exception {
        MockMvc mvc = mvc(); Login owner = login(mvc); UUID business = business("employee-security", "ACTIVE"); member(business, owner.id(), "OWNER", "ACTIVE");
        MvcResult csrf = csrf(mvc);
        assertThat(mvc.perform(post("/api/v1/businesses/{id}/employees", business)
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", (String) JsonPath.read(csrf.getResponse().getContentAsString(), "$.token"))
                .contentType(MediaType.APPLICATION_JSON).content(employeeBody("x", "No auth"))).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/v1/businesses/{id}/employees", business)
                .header("Authorization", "Bearer " + owner.token()).contentType(MediaType.APPLICATION_JSON)
                .content(employeeBody("x", "Missing CSRF"))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/businesses/not-a-uuid/employees").header("Authorization", "Bearer " + owner.token())).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private Login login(MockMvc mvc) throws Exception { String email="employee-"+UUID.randomUUID()+"@example.test"; mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isCreated()); UUID id=jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?", UUID.class, email); MvcResult csrf=csrf(mvc); MvcResult result=mvc.perform(post("/api/v1/auth/login").cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", (String) JsonPath.read(csrf.getResponse().getContentAsString(), "$.token")).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andReturn(); return new Login(id, (String) JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken")); }
    private MvcResult csrf(MockMvc mvc) throws Exception { return mvc.perform(get("/api/v1/auth/csrf")).andReturn(); }
    private MvcResult create(MockMvc mvc,String token,UUID business,String body)throws Exception{return mutate(mvc,post("/api/v1/businesses/{id}/employees",business),token,body);}
    private MvcResult update(MockMvc mvc,String token,UUID b,UUID e,String body)throws Exception{return mutate(mvc,patch("/api/v1/businesses/{b}/employees/{e}",b,e),token,body);}
    private MvcResult archive(MockMvc mvc,String token,UUID b,UUID e)throws Exception{return mutate(mvc,delete("/api/v1/businesses/{b}/employees/{e}",b,e),token,null);}
    private MvcResult assign(MockMvc mvc,String token,UUID b,UUID e,UUID branch)throws Exception{return mutate(mvc,put("/api/v1/businesses/{b}/employees/{e}/branches/{branch}",b,e,branch),token,null);}
    private MvcResult unassign(MockMvc mvc,String token,UUID b,UUID e,UUID branch)throws Exception{return mutate(mvc,delete("/api/v1/businesses/{b}/employees/{e}/branches/{branch}",b,e,branch),token,null);}
    private MvcResult list(MockMvc mvc,String token,UUID b)throws Exception{return mvc.perform(get("/api/v1/businesses/{id}/employees",b).header("Authorization","Bearer "+token)).andReturn();}
    private MvcResult getEmployee(MockMvc mvc,String token,UUID b,UUID e)throws Exception{return mvc.perform(get("/api/v1/businesses/{b}/employees/{e}",b,e).header("Authorization","Bearer "+token)).andReturn();}
    private MvcResult branches(MockMvc mvc,String token,UUID b,UUID e)throws Exception{return mvc.perform(get("/api/v1/businesses/{b}/employees/{e}/branches",b,e).header("Authorization","Bearer "+token)).andReturn();}
    private MvcResult mutate(MockMvc mvc, MockHttpServletRequestBuilder builder,String token,String body)throws Exception{MvcResult csrf=csrf(mvc); if(body!=null) builder.contentType(MediaType.APPLICATION_JSON).content(body); return mvc.perform(builder.cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",(String) JsonPath.read(csrf.getResponse().getContentAsString(),"$.token")).header("Authorization","Bearer "+token)).andReturn();}
    private UUID business(String slug,String status){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO businesses (id,name,slug,business_type,time_zone,status) VALUES (?,?,?,'SALON','UTC',?)",id,"Business "+slug,slug,status);return id;}
    private void member(UUID business,UUID user,String role,String status){jdbc.update("INSERT INTO business_memberships (id,tenant_id,user_id,role,status) VALUES (?,?,?,?,?)",UUID.randomUUID(),business,user,role,status);}
    private UUID branch(UUID business,String code,String status){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO branches (id,tenant_id,code,name,address_line1,city,country_code,time_zone,status) VALUES (?,?,?,?,'1 Main','City','VN','UTC',?)",id,business,code.toUpperCase(),"Branch "+code,status);return id;}
    private String employeeBody(String code,String name){return "{\"code\":\""+code+"\",\"fullName\":\""+name+"\"}";}
    private UUID id(MvcResult result)throws Exception{assertThat(result.getResponse().getStatus()).isEqualTo(201);return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(),"$.id"));}
    private record Login(UUID id,String token) {}
}
