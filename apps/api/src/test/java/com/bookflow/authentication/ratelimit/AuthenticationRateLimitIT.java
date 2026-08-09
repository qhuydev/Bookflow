package com.bookflow.authentication.ratelimit;

import com.bookflow.BookFlowApplication;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.bookflow.support.RedisTestcontainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;

import java.util.UUID;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BookFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "bookflow.authentication.rate-limit.enabled=true",
                "bookflow.authentication.rate-limit.fail-open=false",
                "bookflow.authentication.rate-limit.window-seconds=5",
                "bookflow.authentication.rate-limit.account-limit=2",
                "bookflow.authentication.rate-limit.ip-limit=100",
                "bookflow.authentication.rate-limit.trusted-proxies="
        }
)
@ActiveProfiles("testcontainers")
@Import({PostgresTestcontainerConfiguration.class, RedisTestcontainerConfiguration.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationRateLimitIT {
    @Autowired WebApplicationContext context;
    @Autowired AuthenticationRateLimiter limiter;
    @Autowired ClientIpResolver clientIpResolver;
    @Autowired StringRedisTemplate redis;
    @Autowired GenericContainer<?> redisContainer;

    @AfterEach
    void clearRateLimits() {
        if (redisContainer.isRunning()) {
            var keys = redis.keys("bookflow:auth:rate-limit:v1:*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
    }

    @Test
    @Order(1)
    void endpointReturns429WithRetryAfterWithoutAccountEnumeration() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "limited-" + UUID.randomUUID() + "@example.test";

        MvcResult first = forgot(mvc, email);
        MvcResult second = forgot(mvc, email.toUpperCase());
        MvcResult limited = forgot(mvc, "  " + email + "  ");
        MvcResult differentAccount = forgot(mvc, "other-" + UUID.randomUUID() + "@example.test");

        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        assertThat(second.getResponse().getStatus()).isEqualTo(202);
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(JsonPath.<String>read(limited.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("AUTH_RATE_LIMITED");
        assertThat(differentAccount.getResponse().getStatus()).isEqualTo(202);

        var keys = redis.keys("bookflow:auth:rate-limit:v1:*");
        assertThat(keys).isNotEmpty().allMatch(key -> key.matches(
                "bookflow:auth:rate-limit:v1:[a-z-]+:(ip|account):[0-9a-f]{64}"
        ));
        assertThat(keys).allMatch(key -> !key.contains(email));
    }

    @Test
    @Order(2)
    void luaCounterIsAtomicAndKeepsATtlUnderConcurrency() throws Exception {
        String identifier = "atomic-" + UUID.randomUUID() + "@example.test";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.9");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(10)) {
            var futures = java.util.stream.IntStream.range(0, 10)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        try {
                            limiter.check("concurrency", identifier, request);
                            allowed.incrementAndGet();
                        } catch (RateLimitExceededException exception) {
                            rejected.incrementAndGet();
                        }
                        return null;
                    })).toList();
            start.countDown();
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(allowed).hasValue(2);
        assertThat(rejected).hasValue(8);
        String accountKey = redis.keys("bookflow:auth:rate-limit:v1:concurrency:account:*").iterator().next();
        assertThat(redis.opsForValue().get(accountKey)).isEqualTo("10");
        Long ttlSeconds = redis.getExpire(accountKey);
        assertThat(ttlSeconds).isNotNull().isPositive().isLessThanOrEqualTo(5L);
    }

    @Test
    @Order(3)
    void untrustedForwardedForDoesNotOverrideRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.8");
        assertThat(clientIpResolver.resolve(request)).isEqualTo("10.0.0.10");
    }

    @Test
    @Order(4)
    void everyAuthenticationMutationUsesItsOwnRateLimitNamespace() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "rate-wiring-" + UUID.randomUUID() + "@example.test";
        String password = "BookFlow rate wiring password 2026!";

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        assertRateLimitKeysExist("register");

        MvcResult login = postWithCsrf(mvc, "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie refreshToken = login.getResponse().getCookie("bookflow_refresh");
        assertRateLimitKeysExist("login");

        assertThat(forgot(mvc, email).getResponse().getStatus()).isEqualTo(202);
        assertRateLimitKeysExist("forgot-password");

        MvcResult reset = postWithCsrf(mvc, "/api/v1/auth/reset-password",
                "{\"token\":\"invalid-reset-token\",\"newPassword\":\"" + password + "\"}");
        assertThat(reset.getResponse().getStatus()).isEqualTo(400);
        assertRateLimitKeysExist("reset-password");

        MvcResult refresh = postWithCsrf(mvc, "/api/v1/auth/refresh", null, refreshToken);
        assertThat(refresh.getResponse().getStatus()).isEqualTo(200);
        Cookie rotatedRefreshToken = refresh.getResponse().getCookie("bookflow_refresh");
        assertRateLimitKeysExist("refresh");

        assertThat(postWithCsrf(mvc, "/api/v1/auth/logout", null, rotatedRefreshToken)
                .getResponse().getStatus()).isEqualTo(204);
        assertRateLimitKeysExist("logout");

        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/v1/auth/logout-all")
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        assertRateLimitKeysExist("logout-all");
    }

    @Test
    @Order(99)
    void redisFailureUsesConfiguredFailClosedPolicy() {
        redisContainer.stop();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.10");
        assertThatThrownBy(() -> limiter.check("login", "failure@example.test", request))
                .isInstanceOf(RateLimitUnavailableException.class);
    }

    private MvcResult forgot(MockMvc mvc, String email) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        return mvc.perform(post("/api/v1/auth/forgot-password")
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token)
                        .with(request -> { request.setRemoteAddr("127.0.0.5"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn();
    }

    private MvcResult postWithCsrf(MockMvc mvc, String path, String body, Cookie... cookies) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var request = post(path)
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", JsonPath.<String>read(csrf.getResponse().getContentAsString(), "$.token"));
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request).andReturn();
    }

    private void assertRateLimitKeysExist(String action) {
        assertThat(redis.keys("bookflow:auth:rate-limit:v1:" + action + ":*"))
                .isNotNull()
                .hasSize(2)
                .allMatch(key -> key.matches(
                        "bookflow:auth:rate-limit:v1:[a-z-]+:(ip|account):[0-9a-f]{64}"
                ));
    }
}
