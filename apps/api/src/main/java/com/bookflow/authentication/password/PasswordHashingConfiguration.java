package com.bookflow.authentication.password;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Argon2PasswordProperties.class)
public class PasswordHashingConfiguration {

    @Bean
    PasswordEncoder userPasswordEncoder(Argon2PasswordProperties properties) {
        return new Argon2PasswordEncoder(
                properties.saltLength(),
                properties.hashLength(),
                properties.parallelism(),
                properties.memoryKib(),
                properties.iterations()
        );
    }
}
