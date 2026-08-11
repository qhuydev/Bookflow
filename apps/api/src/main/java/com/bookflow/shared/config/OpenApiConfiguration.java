package com.bookflow.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Bean
    OpenAPI bookFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BookFlow API")
                        .version("v1")
                        .description("REST API for the BookFlow booking and scheduling platform"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Dán access token JWT sau khi đăng nhập; không thêm tiền tố Bearer.")))
                .paths(new Paths());
    }

    @Bean
    OpenApiCustomizer csrfHeaderForUnsafeOperations() {
        return openApi -> openApi.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> {
                    if (requiresCsrf(path, method) && lacksCsrfParameter(operation)) {
                        operation.addParametersItem(new Parameter()
                                .in("header")
                                .name(CSRF_HEADER)
                                .required(true)
                                .description("Gọi GET /api/v1/auth/csrf, rồi dán trường token vào đây. Cookie XSRF-TOKEN phải được giữ trong cùng phiên trình duyệt.")
                                .schema(new StringSchema()));
                    }
                })
        );
    }

    private boolean requiresCsrf(String path, PathItem.HttpMethod method) {
        return (method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PATCH || method == PathItem.HttpMethod.DELETE)
                && !"/api/v1/auth/register".equals(path);
    }

    private boolean lacksCsrfParameter(Operation operation) {
        return operation.getParameters() == null || operation.getParameters().stream()
                .noneMatch(parameter -> CSRF_HEADER.equalsIgnoreCase(parameter.getName())
                        && "header".equalsIgnoreCase(parameter.getIn()));
    }
}
