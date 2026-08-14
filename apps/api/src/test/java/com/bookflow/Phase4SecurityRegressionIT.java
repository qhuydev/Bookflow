package com.bookflow;

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
class Phase4SecurityRegressionIT {
    private static final String PASSWORD = "Phase four audit password 2026!";

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE TABLE employee_services, branch_services, employee_branch_assignments, employees, services, branches, business_memberships, businesses, refresh_tokens, auth_sessions, users CASCADE");
    }

    @Test
    void rolesTenantIsolationMemberLifecycleAndServiceAssignmentsUseCurrentDatabaseState() throws Exception {
        MockMvc mvc = mvc();
        Login owner = login(mvc, "owner");
        Login admin = login(mvc, "admin");
        Login staff = login(mvc, "staff");
        Login target = login(mvc, "target");
        Login outsider = login(mvc, "outsider");
        UUID businessA = business("phase4-a");
        UUID businessB = business("phase4-b");
        member(businessA, owner.id(), "OWNER");
        member(businessA, admin.id(), "ADMIN");
        member(businessA, staff.id(), "STAFF");
        member(businessB, outsider.id(), "OWNER");

        UUID branchA = id(mutate(mvc, post("/api/v1/businesses/{b}/branches", businessA), owner.token(), branchBody("A-MAIN")), 201);
        UUID branchB = id(mutate(mvc, post("/api/v1/businesses/{b}/branches", businessB), outsider.token(), branchBody("B-MAIN")), 201);
        UUID employeeA = id(mutate(mvc, post("/api/v1/businesses/{b}/employees", businessA), owner.token(), employeeBody("EMP-A", "Employee A")), 201);
        UUID employeeWithoutBranch = id(mutate(mvc, post("/api/v1/businesses/{b}/employees", businessA), owner.token(), employeeBody("EMP-NONE", "No Branch")), 201);
        assertThat(mutate(mvc, put("/api/v1/businesses/{b}/employees/{e}/branches/{branch}", businessA, employeeA, branchA), owner.token(), null).getResponse().getStatus()).isEqualTo(204);

        String inviteBody = "{\"email\":\"" + target.email() + "\",\"role\":\"STAFF\"}";
        UUID memberId = id(mutate(mvc, post("/api/v1/businesses/{b}/members", businessA), owner.token(), inviteBody), 201);
        assertThat(getWithToken(mvc, "/api/v1/businesses/{b}/members", admin.token(), businessA).getResponse().getStatus()).isEqualTo(200);
        assertThat(mutate(mvc, post("/api/v1/businesses/{b}/members", businessA), admin.token(), inviteBody).getResponse().getStatus()).isEqualTo(403);
        assertThat(getWithToken(mvc, "/api/v1/businesses/{b}/members", staff.token(), businessA).getResponse().getStatus()).isEqualTo(403);
        assertThat(getWithToken(mvc, "/api/v1/businesses/{b}/members", outsider.token(), businessA).getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT status FROM business_memberships WHERE tenant_id=? AND id=?", String.class, businessA, memberId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT user_id IS NULL FROM employees WHERE tenant_id=? AND id=?", Boolean.class, businessA, employeeA)).isTrue();
        MvcResult linkMember = mutate(mvc, put("/api/v1/businesses/{b}/members/{m}/employee/{e}", businessA, memberId, employeeA), owner.token(), null);
        assertThat(linkMember.getResponse().getStatus()).as(linkMember.getResponse().getContentAsString()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT user_id FROM employees WHERE id=?", UUID.class, employeeA)).isEqualTo(target.id());
        assertThat(mutate(mvc, delete("/api/v1/businesses/{b}/members/{m}/employee", businessA, memberId), owner.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT user_id IS NULL FROM employees WHERE id=?", Boolean.class, employeeA)).isTrue();

        UUID service = id(mutate(mvc, post("/api/v1/businesses/{b}/services", businessA), owner.token(), serviceBody("Audit Service")), 201);
        assertThat(mutate(mvc, put("/api/v1/businesses/{b}/services/{s}/branches/{branch}", businessA, service, branchA), admin.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertThat(mutate(mvc, put("/api/v1/businesses/{b}/services/{s}/employees/{e}", businessA, service, employeeA), admin.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertThat(mutate(mvc, put("/api/v1/businesses/{b}/services/{s}/employees/{e}", businessA, service, employeeWithoutBranch), owner.token(), null).getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc, put("/api/v1/businesses/{b}/services/{s}/branches/{branch}", businessA, service, branchB), owner.token(), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(mutate(mvc, patch("/api/v1/businesses/{b}/services/{s}", businessA, service), admin.token(), "{\"name\":\"Changed Service\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(mutate(mvc, patch("/api/v1/businesses/{b}/services/{s}", businessA, service), staff.token(), "{\"name\":\"Denied\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(getWithToken(mvc, "/api/v1/businesses/{b}/services", staff.token(), businessA).getResponse().getStatus()).isEqualTo(200);
        assertThat(getWithToken(mvc, "/api/v1/businesses/{b}/services/{s}", outsider.token(), businessA, service).getResponse().getStatus()).isEqualTo(404);

        assertThat(getWithToken(mvc, "/api/v1/businesses/{b}/branches", target.token(), businessA).getResponse().getStatus()).isEqualTo(200);
        assertThat(mutate(mvc, delete("/api/v1/businesses/{b}/members/{m}", businessA, memberId), owner.token(), null).getResponse().getStatus()).isEqualTo(204);
        assertThat(getWithToken(mvc, "/api/v1/businesses/{b}/branches", target.token(), businessA).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void publicCatalogNeedsNoAuthAndNeverLeaksInactiveForeignOrPrivateFields() throws Exception {
        MockMvc mvc = mvc();
        UUID businessA = business("public-a");
        UUID businessB = business("public-b");
        UUID branchA = branch(businessA, "PUB-A", "ACTIVE");
        UUID archivedBranch = branch(businessA, "PUB-OLD", "ARCHIVED");
        UUID foreignBranch = branch(businessB, "PUB-B", "ACTIVE");
        UUID employeeA = employee(businessA, "PUBLIC-EMP", "Public Employee", "ACTIVE");
        UUID archivedEmployee = employee(businessA, "OLD-EMP", "Archived Employee", "ARCHIVED");
        UUID serviceA = service(businessA, "Public Service", "ACTIVE");
        UUID archivedService = service(businessA, "Archived Service", "ARCHIVED");
        jdbc.update("INSERT INTO employee_branch_assignments(tenant_id,employee_id,branch_id) VALUES (?,?,?)", businessA, employeeA, branchA);
        jdbc.update("INSERT INTO branch_services(tenant_id,branch_id,service_id) VALUES (?,?,?)", businessA, branchA, serviceA);
        jdbc.update("INSERT INTO employee_services(tenant_id,employee_id,service_id) VALUES (?,?,?)", businessA, employeeA, serviceA);

        String profile = mvc.perform(get("/api/v1/public/businesses/public-a")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String branches = mvc.perform(get("/api/v1/public/businesses/public-a/branches")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String services = mvc.perform(get("/api/v1/public/businesses/public-a/services").param("branchId", branchA.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String employees = mvc.perform(get("/api/v1/public/businesses/public-a/employees").param("branchId", branchA.toString()).param("serviceId", serviceA.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(profile).contains("public-a").doesNotContain("tenantId", "membership", "createdAt", "status");
        assertThat(branches).contains(branchA.toString()).doesNotContain(archivedBranch.toString(), foreignBranch.toString(), "status", "createdAt");
        assertThat(services).contains(serviceA.toString()).doesNotContain(archivedService.toString(), "tenantId", "businessId", "status", "createdAt");
        assertThat(employees).contains(employeeA.toString(), "Public Employee").doesNotContain(archivedEmployee.toString(), "phone", "email", "userId", "tenantId", "status", "createdAt");
        mvc.perform(get("/api/v1/public/businesses/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/businesses/public-a/services").param("branchId", foreignBranch.toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/businesses/public-a/employees").param("branchId", branchA.toString()).param("serviceId", archivedService.toString())).andExpect(status().isNotFound());
    }

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private Login login(MockMvc mvc, String prefix) throws Exception { String email=prefix+"-"+UUID.randomUUID()+"@example.test"; mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isCreated()); UUID id=jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?",UUID.class,email); MvcResult csrf=csrf(mvc); MvcResult result=mvc.perform(post("/api/v1/auth/login").cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",JsonPath.<String>read(csrf.getResponse().getContentAsString(),"$.token")).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andReturn(); return new Login(id,email,JsonPath.read(result.getResponse().getContentAsString(),"$.accessToken")); }
    private MvcResult csrf(MockMvc mvc) throws Exception { return mvc.perform(get("/api/v1/auth/csrf")).andReturn(); }
    private MvcResult mutate(MockMvc mvc, MockHttpServletRequestBuilder request, String token, String body) throws Exception { MvcResult csrf=csrf(mvc); if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(body); return mvc.perform(request.cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",JsonPath.<String>read(csrf.getResponse().getContentAsString(),"$.token")).header("Authorization","Bearer "+token)).andReturn(); }
    private MvcResult getWithToken(MockMvc mvc,String path,String token,Object...variables)throws Exception{return mvc.perform(get(path,variables).header("Authorization","Bearer "+token)).andReturn();}
    private UUID id(MvcResult result,int status) throws Exception {assertThat(result.getResponse().getStatus()).isEqualTo(status);return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(),"$.id"));}
    private UUID business(String slug){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO businesses(id,name,slug,business_type,time_zone,status) VALUES (?,?,?,'SALON','UTC','ACTIVE')",id,"Business "+slug,slug);return id;}
    private void member(UUID business,UUID user,String role){jdbc.update("INSERT INTO business_memberships(id,tenant_id,user_id,role,status) VALUES (?,?,?,?,'ACTIVE')",UUID.randomUUID(),business,user,role);}
    private UUID branch(UUID business,String code,String status){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO branches(id,tenant_id,code,name,address_line1,city,country_code,time_zone,status) VALUES (?,?,?,?, '1 Main','City','VN','UTC',?)",id,business,code,"Branch "+code,status);return id;}
    private UUID employee(UUID business,String code,String name,String status){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO employees(id,tenant_id,code,full_name,phone,email,bio,status) VALUES (?,?,?,?, '+84123456789', 'private@example.test', 'Public bio', ?)",id,business,code,name,status);return id;}
    private UUID service(UUID business,String name,String status){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO services(id,tenant_id,name,price,currency,duration_minutes,status) VALUES (?,?,?,100000,'VND',60,?)",id,business,name,status);return id;}
    private String branchBody(String code){return "{\"code\":\""+code+"\",\"name\":\"Branch "+code+"\",\"addressLine1\":\"1 Main\",\"city\":\"City\",\"countryCode\":\"VN\"}";}
    private String employeeBody(String code,String name){return "{\"code\":\""+code+"\",\"fullName\":\""+name+"\"}";}
    private String serviceBody(String name){return "{\"name\":\""+name+"\",\"price\":100000,\"currency\":\"VND\",\"durationMinutes\":60,\"bufferBeforeMinutes\":0,\"bufferAfterMinutes\":0}";}
    private record Login(UUID id,String email,String token) {}
}
