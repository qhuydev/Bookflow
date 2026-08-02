package com.bookflow.shared.config;

import com.bookflow.BookFlowApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BookFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "debug=false"
)
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    private static final String DESCRIPTION =
            "REST API for the BookFlow booking and scheduling platform";

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void exposesOpenApiDocumentWithBookFlowMetadataAndNoInternalConfiguration() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.info.title").value("BookFlow API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.info.description").value(DESCRIPTION))
                .andExpect(jsonPath("$.paths").isMap())
                .andReturn();

        String body = responseBody(result);
        String openApiVersion = JsonPath.read(body, "$.openapi");
        Map<String, Object> paths = JsonPath.read(body, "$.paths");

        assertThat(openApiVersion).matches("3\\.[01]\\.\\d+");
        assertThat(paths).isEmpty();
        assertThat(body).doesNotContain(
                "/actuator",
                "jdbc:postgresql",
                "BOOKFLOW_DB_PASSWORD",
                "password",
                "container",
                "D:\\\\BookFlow"
        );
    }

    @Test
    void servesSwaggerUiAndConfiguresItToLoadTheOpenApiDocument() throws Exception {
        MvcResult index = mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        assertThat(responseBody(index)).containsIgnoringCase("swagger ui");

        MvcResult initializer = mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(responseBody(initializer)).contains("/v3/api-docs");
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
