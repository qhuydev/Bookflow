package com.bookflow.branches;

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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes=BookFlowApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers") @Import(PostgresTestcontainerConfiguration.class)
class BranchManagementIT {
    private static final String PASSWORD="Branch management password 2026!";
    @Autowired WebApplicationContext context; @Autowired JdbcTemplate jdbc;
    @AfterEach void clear(){ jdbc.execute("TRUNCATE TABLE branches, business_memberships, businesses, refresh_tokens, auth_sessions, users CASCADE"); }

    @Test void ownerAndAdminCreateStaffViewsAndStaffCannotMutate() throws Exception {
        MockMvc mvc=mvc(); Login owner=login(mvc), admin=login(mvc), staff=login(mvc); UUID business=business("branch-a", "ACTIVE", "Asia/Ho_Chi_Minh"); member(business,owner.id(),"OWNER","ACTIVE"); member(business,admin.id(),"ADMIN","ACTIVE"); member(business,staff.id(),"STAFF","ACTIVE");
        MvcResult created=post(mvc,owner.token(),business,body("q1-main")); assertThat(created.getResponse().getStatus()).isEqualTo(201); UUID id=UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(),"$.id"));
        assertThat(JsonPath.<String>read(created.getResponse().getContentAsString(),"$.code")).isEqualTo("Q1-MAIN"); assertThat(JsonPath.<String>read(created.getResponse().getContentAsString(),"$.timeZone")).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(get(mvc,staff.token(),business).getResponse().getStatus()).isEqualTo(200);
        assertThat(patch(mvc,staff.token(),business,id,"{\"name\":\"Denied\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(delete(mvc,staff.token(),business,id).getResponse().getStatus()).isEqualTo(403);
        assertThat(post(mvc,admin.token(),business,body("q2" )).getResponse().getStatus()).isEqualTo(201);
    }
    @Test void isolatesTenantsAndAllowsSameCodeAcrossBusinesses() throws Exception {
        MockMvc mvc=mvc(); Login a=login(mvc), b=login(mvc); UUID ba=business("tenant-a","ACTIVE","UTC"), bb=business("tenant-b","ACTIVE","UTC"); member(ba,a.id(),"OWNER","ACTIVE"); member(bb,b.id(),"OWNER","ACTIVE");
        UUID branchA=UUID.fromString(JsonPath.read(post(mvc,a.token(),ba,body("same")).getResponse().getContentAsString(),"$.id"));
        assertThat(post(mvc,b.token(),bb,body("same")).getResponse().getStatus()).isEqualTo(201);
        assertThat(get(mvc,b.token(),ba,branchA).getResponse().getStatus()).isEqualTo(404);
        assertThat(patch(mvc,a.token(),bb,branchA,"{\"name\":\"Cross\"}").getResponse().getStatus()).isEqualTo(404);
    }
    @Test void mapsDuplicateCodeAndValidationAndHonorsCsrfAndJwt() throws Exception {
        MockMvc mvc=mvc(); Login owner=login(mvc); UUID business=business("validation","ACTIVE","UTC"); member(business,owner.id(),"OWNER","ACTIVE"); post(mvc,owner.token(),business,body("unique"));
        assertThat(post(mvc,owner.token(),business,body("unique")).getResponse().getStatus()).isEqualTo(409);
        assertThat(post(mvc,owner.token(),business,"{}").getResponse().getStatus()).isEqualTo(400);
        MvcResult csrf=csrf(mvc); assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/businesses/{id}/branches",business).cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",(String)JsonPath.read(csrf.getResponse().getContentAsString(),"$.token")).contentType(MediaType.APPLICATION_JSON).content(body("unauth"))).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/businesses/{id}/branches",business).header("Authorization","Bearer "+owner.token()).contentType(MediaType.APPLICATION_JSON).content(body("csrf"))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/businesses/not-a-uuid/branches").header("Authorization","Bearer "+owner.token())).andReturn().getResponse().getStatus()).isEqualTo(400);
    }
    @Test void partialUpdateAndArchiveAreSafeAndArchiveIsIdempotent() throws Exception {
        MockMvc mvc=mvc(); Login owner=login(mvc); UUID business=business("archive","ACTIVE","UTC"); member(business,owner.id(),"OWNER","ACTIVE"); UUID branch=UUID.fromString(JsonPath.read(post(mvc,owner.token(),business,body("archive-me")).getResponse().getContentAsString(),"$.id"));
        assertThat(patch(mvc,owner.token(),business,branch,"{\"name\":\"Changed\"}").getResponse().getStatus()).isEqualTo(200); assertThat(jdbc.queryForObject("SELECT code FROM branches WHERE id=?",String.class,branch)).isEqualTo("ARCHIVE-ME");
        assertThat(delete(mvc,owner.token(),business,branch).getResponse().getStatus()).isEqualTo(204); assertThat(jdbc.queryForObject("SELECT status FROM branches WHERE id=?",String.class,branch)).isEqualTo("ARCHIVED");
        assertThat(delete(mvc,owner.token(),business,branch).getResponse().getStatus()).isEqualTo(204); assertThat(get(mvc,owner.token(),business).getResponse().getContentAsString()).isEqualTo("[]"); assertThat(get(mvc,owner.token(),business,branch).getResponse().getStatus()).isEqualTo(404); assertThat(patch(mvc,owner.token(),business,branch,"{\"name\":\"No\"}").getResponse().getStatus()).isEqualTo(404);
    }
    @Test void hidesBranchesWhenMembershipOrBusinessIsInactive() throws Exception {
        MockMvc mvc=mvc(); Login owner=login(mvc); UUID business=business("inactive","ACTIVE","UTC"); member(business,owner.id(),"OWNER","ACTIVE"); post(mvc,owner.token(),business,body("one")); jdbc.update("UPDATE business_memberships SET status='SUSPENDED' WHERE tenant_id=?",business); assertThat(get(mvc,owner.token(),business).getResponse().getStatus()).isEqualTo(404);
    }
    private MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    private Login login(MockMvc mvc)throws Exception {String email="branch-"+UUID.randomUUID()+"@example.test";mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isCreated()); UUID id=jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?",UUID.class,email);MvcResult c=csrf(mvc);MvcResult l=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login").cookie(c.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",(String)JsonPath.read(c.getResponse().getContentAsString(),"$.token")).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andReturn();return new Login(id,JsonPath.read(l.getResponse().getContentAsString(),"$.accessToken"));}
    private MvcResult csrf(MockMvc mvc)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/csrf")).andReturn();}
    private MvcResult post(MockMvc mvc,String token,UUID b,String body)throws Exception{return mutate(mvc,org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/businesses/{id}/branches",b),token,body);}
    private MvcResult patch(MockMvc mvc,String token,UUID b,UUID branch,String body)throws Exception{return mutate(mvc,org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/businesses/{id}/branches/{branch}",b,branch),token,body);}
    private MvcResult delete(MockMvc mvc,String token,UUID b,UUID branch)throws Exception{return mutate(mvc,org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/businesses/{id}/branches/{branch}",b,branch),token,null);}
    private MvcResult mutate(MockMvc mvc,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,String token,String body)throws Exception{MvcResult c=csrf(mvc);if(body!=null)builder.contentType(MediaType.APPLICATION_JSON).content(body);return mvc.perform(builder.cookie(c.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",(String)JsonPath.read(c.getResponse().getContentAsString(),"$.token")).header("Authorization","Bearer "+token)).andReturn();}
    private MvcResult get(MockMvc mvc,String token,UUID b)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/businesses/{id}/branches",b).header("Authorization","Bearer "+token)).andReturn();}
    private MvcResult get(MockMvc mvc,String token,UUID b,UUID branch)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/businesses/{id}/branches/{branch}",b,branch).header("Authorization","Bearer "+token)).andReturn();}
    private UUID business(String slug,String status,String tz){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO businesses (id,name,slug,business_type,time_zone,status) VALUES (?,?,?,'SALON',?,?)",id,"Business "+slug,slug,tz,status);return id;} private void member(UUID b,UUID u,String role,String status){jdbc.update("INSERT INTO business_memberships (id,tenant_id,user_id,role,status) VALUES (?,?,?,?,?)",UUID.randomUUID(),b,u,role,status);} private String body(String code){return "{\"code\":\""+code+"\",\"name\":\"Branch "+code+"\",\"addressLine1\":\"1 Main Street\",\"city\":\"Ho Chi Minh City\",\"countryCode\":\"VN\"}";} private record Login(UUID id,String token){}
}
