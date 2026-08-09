package com.bookflow.authentication.token;

import com.bookflow.authentication.config.AuthenticationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component @Profile("!test")
public class AccessTokenIssuer {
    private final JwtSigningMaterial keys; private final AuthenticationProperties props;
    public AccessTokenIssuer(JwtSigningMaterial keys, AuthenticationProperties props) { this.keys=keys; this.props=props; }
    public IssuedAccessToken issue(UUID userId, UUID sessionId, Instant now) {
        Instant exp=now.plusSeconds(props.accessToken().expiresInSeconds());
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(props.accessToken().issuer()).subject(userId.toString()).audience(List.of(props.accessToken().audience())).issuedAt(now).expiresAt(exp).id(UUID.randomUUID().toString()).claim("client_id",props.accessToken().clientId()).claim("sid",sessionId.toString()).build();
        JwsHeader header=JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.keyId()).type("at+jwt").build();
        return new IssuedAccessToken(keys.encoder().encode(JwtEncoderParameters.from(header,claims)).getTokenValue(), props.accessToken().expiresInSeconds());
    }
    public record IssuedAccessToken(String token,long expiresInSeconds) { }
}
