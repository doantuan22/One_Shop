package com.oneshop.security;

import com.oneshop.config.LoginThrottleProperties;
import com.oneshop.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-28T03:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private LoginAttemptLimiter limiter(boolean enabled) {
        return new LoginAttemptLimiter(new LoginThrottleProperties(enabled, 5, 3, 15, 15), clock);
    }

    private void advance(Duration d) {
        now.set(now.get().plus(d));
    }

    @Test
    void theSixthAttemptOnOneAccountIsRefusedEvenWithoutAnyFailureRecorded() {
        LoginAttemptLimiter limiter = limiter(true);
        for (int i = 0; i < 5; i++) {
            limiter.acquire("alice@test", "1.1.1.1");
        }
        assertThatThrownBy(() -> limiter.acquire("alice@test", "1.1.1.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyLoginAttemptsException) e).getRetryAfterSeconds()).isEqualTo(900));
    }

    @Test
    void identifiersAreCaseAndWhitespaceInsensitiveAndOtherAccountsAreUnaffected() {
        LoginAttemptLimiter limiter = limiter(true);
        for (int i = 0; i < 5; i++) {
            limiter.acquire(i % 2 == 0 ? "Alice@Test" : "  alice@test ", null);
        }
        assertThatThrownBy(() -> limiter.acquire("ALICE@TEST", null)).isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.acquire("bob@test", null)).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginClearsTheAccountCounter() {
        LoginAttemptLimiter limiter = limiter(true);
        for (int i = 0; i < 4; i++) {
            limiter.acquire("alice@test", null);
        }
        limiter.recordSuccess("alice@test");
        for (int i = 0; i < 5; i++) {
            limiter.acquire("alice@test", null);
        }
        assertThatThrownBy(() -> limiter.acquire("alice@test", null)).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void theLockStaysEvenForTheRightPasswordAndEndsAfterTheLockTime() {
        LoginAttemptLimiter limiter = limiter(true);
        for (int i = 0; i < 5; i++) {
            limiter.acquire("alice@test", null);
        }
        assertThatThrownBy(() -> limiter.acquire("alice@test", null)).isInstanceOf(TooManyLoginAttemptsException.class);

        advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.acquire("alice@test", null))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyLoginAttemptsException) e).getRetryAfterSeconds()).isBetween(1L, 61L));

        advance(Duration.ofMinutes(2));
        assertThatCode(() -> limiter.acquire("alice@test", null)).doesNotThrowAnyException();
    }

    @Test
    void attemptsOutsideTheWindowStartFromZeroAgain() {
        LoginAttemptLimiter limiter = limiter(true);
        for (int i = 0; i < 4; i++) {
            limiter.acquire("alice@test", null);
        }
        advance(Duration.ofMinutes(16));
        for (int i = 0; i < 5; i++) {
            limiter.acquire("alice@test", null);
        }
        assertThatThrownBy(() -> limiter.acquire("alice@test", null)).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void anAddressSweepingManyAccountsIsLockedAfterItsWrongLogins() {
        LoginAttemptLimiter limiter = limiter(true);
        for (int i = 0; i < 3; i++) {
            limiter.acquire("user" + i, "9.9.9.9");
            limiter.recordFailure("9.9.9.9");
        }
        assertThatThrownBy(() -> limiter.acquire("someone-else", "9.9.9.9"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.acquire("someone-else", "8.8.8.8")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginsDoNotCountAgainstTheAddress() {
        LoginAttemptLimiter limiter = limiter(true);
        for (int i = 0; i < 20; i++) {
            limiter.acquire("customer" + i, "9.9.9.9");
            limiter.recordSuccess("customer" + i);
        }
        assertThatCode(() -> limiter.acquire("another", "9.9.9.9")).doesNotThrowAnyException();
    }

    @Test
    void whenDisabledNothingIsEverRefused() {
        LoginAttemptLimiter limiter = limiter(false);
        for (int i = 0; i < 50; i++) {
            limiter.acquire("alice@test", "1.1.1.1");
            limiter.recordFailure("1.1.1.1");
        }
    }
}
