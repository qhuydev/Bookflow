package com.bookflow.authentication.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.bookflow.authentication.token.JwtSigningMaterial;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationProperties.class)
class AuthenticationPropertiesConfiguration {
    @Bean
    @Profile("!test")
    JwtDecoder bookFlowJwtDecoder(JwtSigningMaterial signingMaterial) {
        return signingMaterial.decoder();
    }
}
