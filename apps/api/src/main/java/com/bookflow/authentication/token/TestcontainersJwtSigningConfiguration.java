package com.bookflow.authentication.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.bookflow.authentication.config.AuthenticationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPairGenerator;

@Configuration(proxyBeanMethods = false)
@Profile("testcontainers")
class TestcontainersJwtSigningConfiguration {
    @Bean
    JwtSigningMaterial jwtSigningMaterial(AuthenticationProperties properties) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate())
                .keyID("test-key")
                .build();
        return new JwtSigningMaterial(new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key))), BookFlowJwtDecoderFactory.create((java.security.interfaces.RSAPublicKey) pair.getPublic(), properties), "test-key");
    }
}
