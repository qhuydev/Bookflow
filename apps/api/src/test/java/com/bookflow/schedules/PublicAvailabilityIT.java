package com.bookflow.schedules;

import com.bookflow.BookFlowApplication;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BookFlowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"bookflow.availability.slot-step-minutes=15", "bookflow.availability.default-lead-time-minutes=60"})
@ActiveProfiles("testcontainers")
@Import({PostgresTestcontainerConfiguration.class, PublicAvailabilityIT.FixedClockConfiguration.class})
class PublicAvailabilityIT {
    private static final LocalDate THURSDAY = LocalDate.of(2026, 8, 20);
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE TABLE schedule_breaks,schedule_exceptions,working_schedule_rules,employee_services,branch_services,employee_branch_assignments,employees,services,branches,business_memberships,businesses,refresh_tokens,auth_sessions,users CASCADE");
    }

    @Test
    void publicEndpointAggregatesEligibleEmployeesAndBatchLoadedScheduleDeterministically() throws Exception {
        Fixture f = fixture("availability", "Asia/Ho_Chi_Minh", 30, 60, 0, 0);
        UUID first = employee(f.business(), "EMP-A", "ACTIVE"), second = employee(f.business(), "EMP-B", "ACTIVE");
        eligible(f, first); eligible(f, second);
        UUID firstMorning = rule(f, first, THURSDAY, "09:00", "12:00");
        rule(f, first, THURSDAY, "13:00", "18:00");
        rule(f, second, THURSDAY, "09:00", "12:00");
        jdbc.update("INSERT INTO schedule_breaks(id,tenant_id,schedule_rule_id,start_local_time,end_local_time) VALUES (?,?,?,'10:00','10:30')", UUID.randomUUID(), f.business(), firstMorning);

        MvcResult result = mvc().perform(request(f).param("date", THURSDAY.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.slots[0].start").value("2026-08-20T09:00:00+07:00"))
                .andReturn();

        List<String> employeesAtNine = JsonPath.read(result.getResponse().getContentAsString(), "$.slots[0].employeeIds");
        assertThat(employeesAtNine).containsExactlyInAnyOrder(first.toString(), second.toString());
        assertThat(result.getResponse().getContentAsString()).doesNotContain(
                "tenantId", "scheduleRule", "break", "exception", "email", "phone", "userId", "status");

        MvcResult filtered = mvc().perform(request(f).param("date", THURSDAY.toString()).param("employeeId", first.toString()))
                .andExpect(status().isOk()).andReturn();
        List<List<String>> employeeLists = JsonPath.read(filtered.getResponse().getContentAsString(), "$.slots[*].employeeIds");
        assertThat(employeeLists).allSatisfy(ids -> assertThat(ids).containsExactly(first.toString()));
    }

    @Test
    void exceptionsBreaksBuffersAndEmptyAvailabilityUseThePureEngineContract() throws Exception {
        Fixture f = fixture("exceptions", "UTC", 30, 60, 15, 15);
        UUID employee = employee(f.business(), "EMP", "ACTIVE"); eligible(f, employee);
        UUID morning = rule(f, employee, THURSDAY, "09:00", "12:00");
        rule(f, employee, THURSDAY, "13:00", "18:00");
        jdbc.update("INSERT INTO schedule_breaks(id,tenant_id,schedule_rule_id,start_local_time,end_local_time) VALUES (?,?,?,'10:30','11:00')", UUID.randomUUID(), f.business(), morning);
        exception(f, employee, THURSDAY, "TIME_OFF", "14:00", "15:00");
        exception(f, employee, THURSDAY, "WORKING_OVERRIDE", "18:00", "20:00");

        String body = mvc().perform(request(f).param("date", THURSDAY.toString()).param("employeeId", employee.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> starts = JsonPath.read(body, "$.slots[*].start");
        assertThat(starts).contains("2026-08-20T09:15:00Z", "2026-08-20T15:15:00Z", "2026-08-20T18:15:00Z")
                .doesNotContain("2026-08-20T10:00:00Z", "2026-08-20T14:00:00Z");

        exception(f, employee, THURSDAY, "TIME_OFF", null, null);
        mvc().perform(request(f).param("date", THURSDAY.toString()).param("employeeId", employee.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots").isEmpty());
    }

    @Test
    void leadTimeAndInclusiveBusinessHorizonReturnSlotsOrAnEmptySuccessfulResult() throws Exception {
        Fixture f = fixture("policy", "UTC", 6, 30, 0, 0);
        UUID employee = employee(f.business(), "EMP", "ACTIVE"); eligible(f, employee);
        LocalDate today = LocalDate.of(2026, 8, 14);
        rule(f, employee, today, "10:00", "13:00");
        rule(f, employee, THURSDAY, "09:00", "12:00");

        mvc().perform(request(f).param("date", today.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots[0].start").value("2026-08-14T11:00:00Z"));
        mvc().perform(request(f).param("date", THURSDAY.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots").isNotEmpty());
        mvc().perform(request(f).param("date", "2026-08-21"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots").isEmpty());
    }

    @Test
    void invalidInactiveForeignAndUnassignedResourcesAreHiddenAsNotFound() throws Exception {
        Fixture a = fixture("tenant-a", "UTC", 30, 60, 0, 0);
        Fixture b = fixture("tenant-b", "UTC", 30, 60, 0, 0);
        UUID eligible = employee(a.business(), "GOOD", "ACTIVE"); eligible(a, eligible);
        UUID noBranch = employee(a.business(), "NO-BRANCH", "ACTIVE");
        jdbc.update("INSERT INTO employee_services(tenant_id,employee_id,service_id) VALUES (?,?,?)", a.business(), noBranch, a.service());
        UUID noService = employee(a.business(), "NO-SERVICE", "ACTIVE");
        jdbc.update("INSERT INTO employee_branch_assignments(tenant_id,employee_id,branch_id) VALUES (?,?,?)", a.business(), noService, a.branch());
        UUID archived = employee(a.business(), "ARCHIVED", "ARCHIVED"); eligible(a, archived);

        MockMvc mvc = mvc();
        mvc.perform(request(a).param("date", THURSDAY.toString())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/businesses/missing/availability").param("branchId", a.branch().toString()).param("serviceId", a.service().toString()).param("date", THURSDAY.toString())).andExpect(status().isNotFound());
        mvc.perform(get(path(a)).param("branchId", a.branch().toString()).param("serviceId", b.service().toString()).param("date", THURSDAY.toString())).andExpect(status().isNotFound());
        mvc.perform(get(path(a)).param("branchId", b.branch().toString()).param("serviceId", a.service().toString()).param("date", THURSDAY.toString())).andExpect(status().isNotFound());
        for (UUID invalid : List.of(noBranch, noService, archived, employee(b.business(), "FOREIGN", "ACTIVE"))) {
            mvc.perform(request(a).param("date", THURSDAY.toString()).param("employeeId", invalid.toString()))
                    .andExpect(status().isNotFound());
        }
        jdbc.update("UPDATE branches SET status='ARCHIVED' WHERE id=?", a.branch());
        mvc.perform(request(a).param("date", THURSDAY.toString())).andExpect(status().isNotFound());
        mvc.perform(get(path(a)).param("branchId", "not-a-uuid").param("serviceId", a.service().toString()).param("date", THURSDAY.toString())).andExpect(status().isBadRequest());
        mvc.perform(request(a).param("date", "20-08-2026")).andExpect(status().isBadRequest());
    }

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private String path(Fixture f) { return "/api/v1/public/businesses/" + f.slug() + "/availability"; }
    private MockHttpServletRequestBuilder request(Fixture f) {
        return get(path(f)).param("branchId", f.branch().toString()).param("serviceId", f.service().toString());
    }
    private Fixture fixture(String slug, String zone, int horizon, int duration, int before, int after) {
        UUID business = UUID.randomUUID(), branch = UUID.randomUUID(), service = UUID.randomUUID();
        jdbc.update("INSERT INTO businesses(id,name,slug,business_type,time_zone,status,max_booking_advance_days) VALUES (?,?,?,'SALON',?,'ACTIVE',?)", business, "Business " + slug, slug, zone, horizon);
        jdbc.update("INSERT INTO branches(id,tenant_id,code,name,address_line1,city,country_code,time_zone,status) VALUES (?,?,?,?,'1 Main','City','VN',?,'ACTIVE')", branch, business, "B" + slug.toUpperCase().replace("-", ""), "Branch " + slug, zone);
        jdbc.update("INSERT INTO services(id,tenant_id,name,price,currency,duration_minutes,buffer_before_minutes,buffer_after_minutes,status) VALUES (?,?,?,100,'VND',?,?,?,'ACTIVE')", service, business, "Service " + slug, duration, before, after);
        jdbc.update("INSERT INTO branch_services(tenant_id,branch_id,service_id) VALUES (?,?,?)", business, branch, service);
        return new Fixture(slug, business, branch, service);
    }
    private UUID employee(UUID business, String code, String status) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO employees(id,tenant_id,code,full_name,status) VALUES (?,?,?,?,?)", id, business, code, "Employee " + code, status); return id; }
    private void eligible(Fixture f, UUID employee) { jdbc.update("INSERT INTO employee_branch_assignments(tenant_id,employee_id,branch_id) VALUES (?,?,?)", f.business(), employee, f.branch()); jdbc.update("INSERT INTO employee_services(tenant_id,employee_id,service_id) VALUES (?,?,?)", f.business(), employee, f.service()); }
    private UUID rule(Fixture f, UUID employee, LocalDate date, String start, String end) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO working_schedule_rules(id,tenant_id,branch_id,employee_id,weekday,start_local_time,end_local_time,effective_from) VALUES (?,?,?,?,?,?::time,?::time,?)", id, f.business(), f.branch(), employee, date.getDayOfWeek().name(), start, end, date.minusDays(1)); return id; }
    private void exception(Fixture f, UUID employee, LocalDate date, String type, String start, String end) { jdbc.update("INSERT INTO schedule_exceptions(id,tenant_id,branch_id,employee_id,exception_date,type,start_local_time,end_local_time) VALUES (?,?,?,?,?,?,?::time,?::time)", UUID.randomUUID(), f.business(), f.branch(), employee, date, type, start, end); }
    private record Fixture(String slug, UUID business, UUID branch, UUID service) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary Clock availabilityTestClock() {
            return Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
