package com.bookflow.authentication.ratelimit;

import com.bookflow.authentication.config.AuthenticationProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "bookflow.authentication.rate-limit", name = "enabled", havingValue = "true")
public class RedisAuthenticationRateLimiter implements AuthenticationRateLimiter {
    private static final String KEY_PREFIX = "bookflow:auth:rate-limit:v1:";
    private static final DefaultRedisScript<List> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final ClientIpResolver clientIpResolver;
    private final AuthenticationProperties.RateLimit properties;

    public RedisAuthenticationRateLimiter(
            StringRedisTemplate redis,
            ClientIpResolver clientIpResolver,
            AuthenticationProperties authenticationProperties
    ) {
        this.redis = redis;
        this.clientIpResolver = clientIpResolver;
        this.properties = authenticationProperties.rateLimit();
    }

    @Override
    public void check(String action, String accountIdentifier, HttpServletRequest request) {
        try {
            enforce(key(action, "ip", clientIpResolver.resolve(request)), properties.ipLimit());
            if (accountIdentifier != null && !accountIdentifier.isBlank()) {
                enforce(key(action, "account", accountIdentifier), properties.accountLimit());
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (RateLimitUnavailableException exception) {
            if (!properties.failOpen()) {
                throw exception;
            }
        } catch (DataAccessException exception) {
            if (!properties.failOpen()) {
                throw new RateLimitUnavailableException(exception);
            }
        }
    }

    private void enforce(String key, long limit) {
        @SuppressWarnings("unchecked")
        List<Long> result = redis.execute(
                INCREMENT_WITH_TTL,
                List.of(key),
                Long.toString(properties.windowSeconds() * 1_000)
        );
        if (result == null || result.size() != 2) {
            throw new RateLimitUnavailableException(new IllegalStateException("Redis script returned no result."));
        }
        long count = result.get(0);
        long ttlMillis = result.get(1);
        if (count > limit) {
            throw new RateLimitExceededException((ttlMillis + 999) / 1_000);
        }
    }

    private String key(String action, String dimension, String rawIdentifier) {
        return KEY_PREFIX + action + ':' + dimension + ':' + sha256(rawIdentifier);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
