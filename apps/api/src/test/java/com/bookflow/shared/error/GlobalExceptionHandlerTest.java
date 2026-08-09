package com.bookflow.shared.error;

import bookflow.testfixture.ErrorHandlingTestController;
import com.bookflow.BookFlowApplication;
import com.bookflow.businesses.authorization.TenantPermissionDeniedException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BookFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "debug=false"
)
@ActiveProfiles("test")
@Import(ErrorHandlingTestController.class)
class GlobalExceptionHandlerTest {

    private static final Set<String> STANDARD_FIELDS = Set.of(
            "type",
            "title",
            "status",
            "detail",
            "instance",
            "code",
            "timestamp"
    );

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void returnsStableViolationsForBeanValidationWithoutRejectedValues() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "code": "secret-rejected-value"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        Map<String, Object> problem = assertProblem(
                result,
                ApiErrorCode.VALIDATION_ERROR,
                "/test/errors/body",
                true
        );
        List<Map<String, Object>> violations = violations(problem);

        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(violation -> violation.get("field"))
                .containsExactly("code", "name");
        assertThat(violations).extracting(violation -> violation.get("code"))
                .containsExactly("Size", "NotBlank");
        assertThat(violations).allSatisfy(violation -> {
            assertThat(violation.keySet()).containsExactlyInAnyOrder("field", "code", "message");
            assertThat((String) violation.get("message")).isNotBlank();
        });
        assertThat(new HashSet<>(violations)).hasSameSizeAs(violations);
        assertThat(responseBody(result))
                .doesNotContain("secret-rejected-value", "rejectedValue");
    }

    @Test
    void returnsReadableParameterNameForMethodValidationWithoutQueryString() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/errors/parameter")
                        .queryParam("limit", "0")
                        .queryParam("tracking", "secret-query-value"))
                .andExpect(status().isBadRequest())
                .andReturn();

        Map<String, Object> problem = assertProblem(
                result,
                ApiErrorCode.VALIDATION_ERROR,
                "/test/errors/parameter",
                true
        );
        List<Map<String, Object>> violations = violations(problem);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.get("field")).isEqualTo("limit");
            assertThat(violation.get("code")).isEqualTo("Min");
        });
        assertThat(responseBody(result))
                .doesNotContain("ErrorHandlingTestController", "validateParameter", "secret-query-value");
    }

    @Test
    void returnsMalformedRequestWithoutParserDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertProblem(result, ApiErrorCode.MALFORMED_REQUEST, "/test/errors/body", false);
        assertThat(responseBody(result)).doesNotContain(
                "HttpMessageNotReadableException",
                "JsonEOFException",
                "Unexpected end-of-input",
                "stackTrace"
        );
    }

    @Test
    void returnsInvalidRequestForMissingRequestParameter() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/errors/required"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertProblem(result, ApiErrorCode.INVALID_REQUEST, "/test/errors/required", false);
    }

    @Test
    void returnsSafePublicDetailForMissingBusinessResource() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/errors/resource-not-found"))
                .andExpect(status().isNotFound())
                .andReturn();

        Map<String, Object> problem = assertProblem(
                result,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "/test/errors/resource-not-found",
                false
        );
        assertThat(problem.get("detail")).isEqualTo("The requested test resource was not found.");
    }

    @Test
    void mapsActiveMembershipPermissionDenialToSafeForbiddenProblem() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/businesses/example/configuration");
        var response = new GlobalExceptionHandler().handleTenantPermissionDenied(
                new TenantPermissionDeniedException(),
                new ServletWebRequest(request)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isInstanceOf(org.springframework.http.ProblemDetail.class);
        org.springframework.http.ProblemDetail problem = (org.springframework.http.ProblemDetail) response.getBody();
        assertThat(problem.getProperties()).containsEntry("code", "TENANT_PERMISSION_DENIED");
        assertThat(problem.getDetail()).doesNotContain("OWNER", "ADMIN", "STAFF", "SQL");
    }

    @Test
    void returnsProblemDetailForUnknownEndpointWithoutStaticResourceInternals() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/route-that-does-not-exist"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertProblem(
                result,
                ApiErrorCode.ENDPOINT_NOT_FOUND,
                "/api/route-that-does-not-exist",
                false
        );
        assertThat(responseBody(result)).doesNotContain("NoResourceFoundException", "static resource");
    }

    @Test
    void returnsMethodNotAllowedProblem() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/errors/body"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();

        assertProblem(result, ApiErrorCode.METHOD_NOT_ALLOWED, "/test/errors/body", false);
    }

    @Test
    void returnsUnsupportedMediaTypeProblem() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn();

        assertProblem(result, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "/test/errors/body", false);
    }

    @Test
    void hidesUnexpectedExceptionMessageClassAndStackTrace() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        Map<String, Object> problem = assertProblem(
                result,
                ApiErrorCode.INTERNAL_ERROR,
                "/test/errors/unexpected",
                false
        );
        assertThat(problem.get("detail")).isEqualTo("An unexpected error occurred.");
        assertThat(responseBody(result)).doesNotContain(
                "fake-secret-internal-detail",
                "IllegalStateException",
                "stackTrace",
                "exception",
                "cause"
        );
    }

    private Map<String, Object> assertProblem(
            MvcResult result,
            ApiErrorCode expectedCode,
            String expectedPath,
            boolean hasViolations
    ) {
        String contentType = result.getResponse().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();

        Map<String, Object> problem = JsonPath.read(responseBody(result), "$");
        assertThat(problem.get("type")).isEqualTo(expectedCode.type().toString());
        assertThat(problem.get("title")).isEqualTo(expectedCode.title());
        assertThat(problem.get("status")).isEqualTo(expectedCode.status().value());
        assertThat(problem.get("code")).isEqualTo(expectedCode.name());
        assertThat(problem.get("instance")).isEqualTo(expectedPath);
        assertThat(problem.get("instance").toString()).doesNotContain("?");
        assertThat(problem.get("timestamp")).isInstanceOf(String.class);
        assertThatCode(() -> Instant.parse((String) problem.get("timestamp"))).doesNotThrowAnyException();

        Set<String> expectedFields = new HashSet<>(STANDARD_FIELDS);
        if (hasViolations) {
            expectedFields.add("violations");
        }
        assertThat(problem.keySet()).containsExactlyInAnyOrderElementsOf(expectedFields);
        return problem;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> violations(Map<String, Object> problem) {
        Object value = problem.get("violations");
        assertThat(value).isInstanceOf(List.class);
        return new ArrayList<>((List<Map<String, Object>>) value);
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
