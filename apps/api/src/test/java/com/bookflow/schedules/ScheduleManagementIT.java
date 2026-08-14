package com.bookflow.schedules;

import com.bookflow.BookFlowApplication;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
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

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes=BookFlowApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class ScheduleManagementIT {
    private static final String PASSWORD="Schedule management password 2026!";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clear(){jdbc.execute("TRUNCATE TABLE schedule_breaks,schedule_exceptions,working_schedule_rules,employee_branch_assignments,employees,branches,business_memberships,businesses,refresh_tokens,auth_sessions,users CASCADE");}

    @Test void workingRuleCrudSupportsSplitShiftsAndHalfOpenOverlapPolicy() throws Exception {
        MockMvc mvc=mvc(); Login owner=login(mvc),admin=login(mvc),staff=login(mvc);
        UUID business=business("schedule-rules"),branch=branch(business,"MAIN"),otherBranch=branch(business,"OTHER"),employee=employee(business,"EMP-1");assign(business,employee,branch);
        member(business,owner.id(),"OWNER","ACTIVE");member(business,admin.id(),"ADMIN","ACTIVE");member(business,staff.id(),"STAFF","ACTIVE");

        MvcResult morning=mutate(mvc,"POST",rules(business,employee),owner.token(),rule(branch,"MONDAY","09:00","12:00","2026-08-01","2026-12-31"));
        assertThat(morning.getResponse().getStatus()).isEqualTo(201);UUID morningId=id(morning);
        assertThat(mutate(mvc,"POST",rules(business,employee),owner.token(),rule(branch,"MONDAY","12:00","18:00","2026-08-01",null)).getResponse().getStatus()).isEqualTo(201);
        assertThat(mutate(mvc,"POST",rules(business,employee),owner.token(),rule(branch,"MONDAY","11:00","13:00","2026-08-01",null)).getResponse().getStatus()).isEqualTo(409);
        assertThat(mutate(mvc,"POST",rules(business,employee),owner.token(),rule(branch,"MONDAY","09:00","12:00","2027-01-01","2027-12-31")).getResponse().getStatus()).isEqualTo(201);
        assertThat(mutate(mvc,"POST",rules(business,employee),owner.token(),rule(branch,"TUESDAY","18:00","09:00","2026-08-01",null)).getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc,"POST",rules(business,employee),owner.token(),rule(branch,"TUESDAY","09:00","18:00","2026-09-01","2026-08-01")).getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc,"POST",rules(business,employee),owner.token(),rule(otherBranch,"TUESDAY","09:00","18:00","2026-08-01",null)).getResponse().getStatus()).isEqualTo(404);

        assertThat(get(mvc,rules(business,employee),staff.token()).getResponse().getStatus()).isEqualTo(200);
        assertThat(mutate(mvc,"POST",rules(business,employee),staff.token(),rule(branch,"FRIDAY","09:00","12:00","2026-08-01",null)).getResponse().getStatus()).isEqualTo(403);
        assertThat(mutate(mvc,"POST",rules(business,employee),admin.token(),rule(branch,"FRIDAY","09:00","12:00","2026-08-01",null)).getResponse().getStatus()).isEqualTo(201);
        assertThat(mutate(mvc,"PATCH",rules(business,employee)+"/"+morningId,owner.token(),"{\"endLocalTime\":\"11:30\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(mutate(mvc,"DELETE",rules(business,employee)+"/"+morningId,owner.token(),null).getResponse().getStatus()).isEqualTo(204);
        assertThat(get(mvc,rules(business,employee)+"/"+morningId,owner.token()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test void breakCrudEnforcesContainmentOverlapAndTenantScope() throws Exception {
        MockMvc mvc=mvc();Login owner=login(mvc),foreign=login(mvc);UUID business=business("break-a"),foreignBusiness=business("break-b");
        UUID branch=branch(business,"A"),employee=employee(business,"A");assign(business,employee,branch);member(business,owner.id(),"OWNER","ACTIVE");member(foreignBusiness,foreign.id(),"OWNER","ACTIVE");
        UUID rule=id(mutate(mvc,"POST",rules(business,employee),owner.token(),rule(branch,"MONDAY","09:00","18:00","2026-08-01",null)));String base=rules(business,employee)+"/"+rule+"/breaks";
        UUID first=id(mutate(mvc,"POST",base,owner.token(),breakBody("12:00","12:30")));
        assertThat(mutate(mvc,"POST",base,owner.token(),breakBody("12:30","13:00")).getResponse().getStatus()).isEqualTo(201);
        assertThat(mutate(mvc,"PATCH",rules(business,employee)+"/"+rule,owner.token(),"{\"endLocalTime\":\"12:15\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc,"POST",base,owner.token(),breakBody("12:15","12:45")).getResponse().getStatus()).isEqualTo(409);
        assertThat(mutate(mvc,"POST",base,owner.token(),breakBody("08:00","09:30")).getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc,"POST",base,owner.token(),breakBody("13:00","13:00")).getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc,"PATCH",base+"/"+first,owner.token(),"{\"startLocalTime\":\"11:30\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(get(mvc,base,owner.token()).getResponse().getStatus()).isEqualTo(200);
        assertThat(get(mvc,rules(foreignBusiness,employee)+"/"+rule+"/breaks",foreign.token()).getResponse().getStatus()).isEqualTo(404);
        assertThat(mutate(mvc,"DELETE",base+"/"+first,owner.token(),null).getResponse().getStatus()).isEqualTo(204);
    }

    @Test void exceptionCrudSupportsFullDayPartialAndOverride() throws Exception {
        MockMvc mvc=mvc();Login owner=login(mvc);UUID business=business("exceptions"),branch=branch(business,"A"),unassigned=branch(business,"B"),employee=employee(business,"A");assign(business,employee,branch);member(business,owner.id(),"OWNER","ACTIVE");String base=exceptions(business,employee);
        UUID full=id(mutate(mvc,"POST",base,owner.token(),exception(branch,"2026-08-20","TIME_OFF",null,null,"Nghỉ phép")));
        assertThat(mutate(mvc,"POST",base,owner.token(),exception(branch,"2026-08-21","TIME_OFF","09:00","12:00",null)).getResponse().getStatus()).isEqualTo(201);
        assertThat(mutate(mvc,"POST",base,owner.token(),exception(branch,"2026-08-22","WORKING_OVERRIDE","18:00","21:00",null)).getResponse().getStatus()).isEqualTo(201);
        assertThat(mutate(mvc,"POST",base,owner.token(),exception(branch,"2026-08-23","WORKING_OVERRIDE",null,null,null)).getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc,"POST",base,owner.token(),exception(branch,"2026-08-23","TIME_OFF","12:00","11:00",null)).getResponse().getStatus()).isEqualTo(400);
        assertThat(mutate(mvc,"POST",base,owner.token(),exception(unassigned,"2026-08-23","TIME_OFF",null,null,null)).getResponse().getStatus()).isEqualTo(404);
        assertThat(mutate(mvc,"PATCH",base+"/"+full,owner.token(),"{\"note\":\"Đã duyệt\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(get(mvc,base,owner.token()).getResponse().getStatus()).isEqualTo(200);
        assertThat(mutate(mvc,"DELETE",base+"/"+full,owner.token(),null).getResponse().getStatus()).isEqualTo(204);
    }

    @Test void tenantIsolationRevocationAndDatabaseForeignKeysAreEnforced() throws Exception {
        MockMvc mvc=mvc();Login a=login(mvc),b=login(mvc);UUID ta=business("tenant-a"),tb=business("tenant-b"),ba=branch(ta,"A"),bb=branch(tb,"B"),ea=employee(ta,"A");assign(ta,ea,ba);member(ta,a.id(),"OWNER","ACTIVE");member(tb,b.id(),"OWNER","ACTIVE");
        UUID rule=id(mutate(mvc,"POST",rules(ta,ea),a.token(),rule(ba,"MONDAY","09:00","18:00","2026-08-01",null)));
        assertThat(get(mvc,rules(tb,ea),b.token()).getResponse().getStatus()).isEqualTo(404);
        assertThat(mutate(mvc,"PATCH",rules(tb,ea)+"/"+rule,b.token(),"{\"endLocalTime\":\"17:00\"}").getResponse().getStatus()).isEqualTo(404);
        assertThat(mutate(mvc,"POST",exceptions(ta,ea),a.token(),exception(bb,"2026-08-20","TIME_OFF",null,null,null)).getResponse().getStatus()).isEqualTo(404);
        assertThatThrownBy(()->jdbc.update("INSERT INTO working_schedule_rules (id,tenant_id,branch_id,employee_id,weekday,start_local_time,end_local_time,effective_from) VALUES (?,?,?,?,'MONDAY','09:00','10:00','2026-08-01')",UUID.randomUUID(),ta,bb,ea)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("UPDATE business_memberships SET status='REVOKED',revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=?",ta,a.id());
        assertThat(get(mvc,rules(ta,ea),a.token()).getResponse().getStatus()).isEqualTo(404);
        assertThat(get(mvc,rules(ta,ea),null).getResponse().getStatus()).isEqualTo(401);
    }

    private MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    private Login login(MockMvc mvc)throws Exception{String email="schedule-"+UUID.randomUUID()+"@example.test";mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isCreated());UUID id=jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?",UUID.class,email);MvcResult c=csrf(mvc);MvcResult l=mvc.perform(post("/api/v1/auth/login").cookie(c.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",JsonPath.<String>read(c.getResponse().getContentAsString(),"$.token")).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andReturn();return new Login(id,JsonPath.read(l.getResponse().getContentAsString(),"$.accessToken"));}
    private MvcResult csrf(MockMvc mvc)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/csrf")).andReturn();}
    private MvcResult mutate(MockMvc mvc,String method,String url,String token,String body)throws Exception{MvcResult c=csrf(mvc);var builder=switch(method){case"POST"->post(url);case"PATCH"->patch(url);case"DELETE"->delete(url);default->throw new IllegalArgumentException();};builder.cookie(c.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",JsonPath.<String>read(c.getResponse().getContentAsString(),"$.token")).header("Authorization","Bearer "+token);if(body!=null)builder.contentType(MediaType.APPLICATION_JSON).content(body);return mvc.perform(builder).andReturn();}
    private MvcResult get(MockMvc mvc,String url,String token)throws Exception{var builder=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);if(token!=null)builder.header("Authorization","Bearer "+token);return mvc.perform(builder).andReturn();}
    private UUID id(MvcResult result)throws Exception{return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(),"$.id"));}
    private String rules(UUID b,UUID e){return "/api/v1/businesses/"+b+"/employees/"+e+"/schedule-rules";}private String exceptions(UUID b,UUID e){return "/api/v1/businesses/"+b+"/employees/"+e+"/schedule-exceptions";}
    private String rule(UUID branch,String weekday,String start,String end,String from,String to){return "{\"branchId\":\""+branch+"\",\"weekday\":\""+weekday+"\",\"startLocalTime\":\""+start+"\",\"endLocalTime\":\""+end+"\",\"effectiveFrom\":\""+from+"\",\"effectiveTo\":"+(to==null?"null":"\""+to+"\"")+"}";}
    private String breakBody(String start,String end){return "{\"startLocalTime\":\""+start+"\",\"endLocalTime\":\""+end+"\"}";}
    private String exception(UUID branch,String date,String type,String start,String end,String note){return "{\"branchId\":\""+branch+"\",\"date\":\""+date+"\",\"type\":\""+type+"\",\"startLocalTime\":"+(start==null?"null":"\""+start+"\"")+",\"endLocalTime\":"+(end==null?"null":"\""+end+"\"")+",\"note\":"+(note==null?"null":"\""+note+"\"")+"}";}
    private UUID business(String slug){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO businesses (id,name,slug,business_type,time_zone,status) VALUES (?,?,?,'SALON','Asia/Ho_Chi_Minh','ACTIVE')",id,"Business "+slug,slug+"-"+UUID.randomUUID());return id;}private UUID branch(UUID tenant,String code){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO branches (id,tenant_id,code,name,address_line1,city,country_code,time_zone,status) VALUES (?,?,?,?,'1 Main','HCM','VN','Asia/Ho_Chi_Minh','ACTIVE')",id,tenant,code+UUID.randomUUID().toString().substring(0,4).toUpperCase(),"Branch "+code);return id;}private UUID employee(UUID tenant,String code){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO employees (id,tenant_id,code,full_name,status) VALUES (?,?,?,?, 'ACTIVE')",id,tenant,code+UUID.randomUUID().toString().substring(0,4).toUpperCase(),"Employee "+code);return id;}private void assign(UUID t,UUID e,UUID b){jdbc.update("INSERT INTO employee_branch_assignments (tenant_id,employee_id,branch_id) VALUES (?,?,?)",t,e,b);}private void member(UUID b,UUID u,String role,String status){jdbc.update("INSERT INTO business_memberships (id,tenant_id,user_id,role,status) VALUES (?,?,?,?,?)",UUID.randomUUID(),b,u,role,status);}private record Login(UUID id,String token){}
}
