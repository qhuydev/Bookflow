package com.bookflow.authentication.ratelimit;

import com.bookflow.authentication.config.AuthenticationProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ClientIpResolver {
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{1,64}$");
    private final List<String> trustedProxies;

    public ClientIpResolver(AuthenticationProperties properties) {
        this.trustedProxies = properties.rateLimit().trustedProxies() == null
                ? List.of()
                : List.copyOf(properties.rateLimit().trustedProxies());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustedProxies.contains(remoteAddress)) {
            return remoteAddress;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) {
            return remoteAddress;
        }
        String first = forwarded.split(",", 2)[0].trim();
        return IP_LITERAL.matcher(first).matches() ? first : remoteAddress;
    }
}
