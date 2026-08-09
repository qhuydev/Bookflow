package com.bookflow.authentication.token;

import com.bookflow.authentication.config.AuthenticationProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;

final class BookFlowJwtDecoderFactory {
    private BookFlowJwtDecoderFactory() { }

    static JwtDecoder create(RSAPublicKey publicKey, AuthenticationProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false)
                .build();
        decoder.setJwtValidator(JwtValidators.createAtJwtValidator()
                .issuer(properties.accessToken().issuer())
                .audience(properties.accessToken().audience())
                .clientId(properties.accessToken().clientId())
                .build());
        return decoder;
    }
}
