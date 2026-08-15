package com.bookflow.authentication.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Set;

@Configuration(proxyBeanMethods=false) @Profile("!test") @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AuthenticationSecurityConfiguration {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    @Bean
    SecurityFilterChain security(HttpSecurity http,
                                 org.springframework.security.oauth2.jwt.JwtDecoder bookFlowJwtDecoder)
            throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .addObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filter) {
                                filter.setRequireCsrfProtectionMatcher(request ->
                                                !SAFE_METHODS.contains(request.getMethod())
                                                && !("POST".equals(request.getMethod())
                                                && (request.getContextPath() + "/api/v1/auth/register")
                                                .equals(request.getRequestURI())));
                                return filter;
                            }
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/businesses/**").authenticated()
                        .requestMatchers("/api/v1/customer/**").authenticated()
                        .requestMatchers("/api/v1/auth/logout-all").authenticated()
                        .anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(bookFlowJwtDecoder)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthenticationProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        var origins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? java.util.List.<String>of()
                : properties.cors().allowedOrigins().stream().filter(origin -> !origin.isBlank()).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of(
                "Authorization", "Content-Type", "X-XSRF-TOKEN", "Idempotency-Key"
        ));
        configuration.setExposedHeaders(java.util.List.of("Location"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
