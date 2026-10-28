package com.oneshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Brute-force protection for the login endpoints (see {@code LoginAttemptLimiter}).
 *
 * @param enabled       master switch
 * @param maxAttempts   login attempts allowed per account (e-mail / user name) inside one window
 * @param ipMaxFailures wrong logins allowed per client IP inside one window (across all accounts)
 * @param windowMinutes length of the counting window
 * @param lockMinutes   how long further attempts are refused once a limit is exceeded
 */
@ConfigurationProperties(prefix = "oneshop.security.login-throttle")
public record LoginThrottleProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("30") int ipMaxFailures,
        @DefaultValue("15") int windowMinutes,
        @DefaultValue("15") int lockMinutes) {
}
