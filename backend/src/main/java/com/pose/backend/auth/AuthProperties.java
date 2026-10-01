package com.pose.backend.auth;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "pose.security.auth")
public record AuthProperties(Duration refreshTokenTtl) {
    public AuthProperties {
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(7);
    }

    @AssertTrue(message = "refresh-token-ttl must be at least one second")
    public boolean isRefreshTokenTtlPositive() {
        return refreshTokenTtl.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}
