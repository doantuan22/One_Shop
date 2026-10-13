package com.oneshop.service.fulfillment;

import com.oneshop.exception.FulfillmentConflictException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits wrong pickup-code guesses per Fulfillment: after {@value #MAX_FAILURES} wrong codes the code is locked for
 * {@value #LOCK_MINUTES} minutes. It lives in memory on purpose: a wrong code rolls the surrounding transaction back,
 * so the failure counter must not depend on it. A restart clears the counter, which only re-opens a few guesses.
 */
@Component
public class PickupAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final int LOCK_MINUTES = 15;
    /** Above this many remembered Fulfillments, entries whose window has passed are dropped. */
    private static final int PRUNE_ABOVE = 5_000;

    private final Clock clock;
    private final Map<Long, Attempts> attempts = new ConcurrentHashMap<>();

    public PickupAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    private static final class Attempts {
        int failures;
        Instant lockedUntil;
        Instant lastFailure;
    }

    /** @throws FulfillmentConflictException while the code of this Fulfillment is locked */
    public void requireNotLocked(Long fulfillmentId) {
        Attempts state = attempts.get(fulfillmentId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.lockedUntil != null) {
                if (clock.instant().isBefore(state.lockedUntil)) {
                    throw new FulfillmentConflictException(FulfillmentConflictException.PICKUP_CODE_LOCKED,
                            "Nhập sai mã nhận hàng quá nhiều lần. Vui lòng thử lại sau " + LOCK_MINUTES + " phút.");
                }
                state.lockedUntil = null;
                state.failures = 0;
            }
        }
    }

    public void recordFailure(Long fulfillmentId) {
        Attempts state = attempts.computeIfAbsent(fulfillmentId, id -> new Attempts());
        synchronized (state) {
            state.failures++;
            state.lastFailure = clock.instant();
            if (state.failures >= MAX_FAILURES) {
                state.lockedUntil = state.lastFailure.plus(Duration.ofMinutes(LOCK_MINUTES));
            }
        }
        if (attempts.size() > PRUNE_ABOVE) {
            Instant cutoff = clock.instant().minus(Duration.ofMinutes(LOCK_MINUTES));
            attempts.entrySet().removeIf(entry -> {
                Attempts other = entry.getValue();
                synchronized (other) {
                    return other.lastFailure != null && other.lastFailure.isBefore(cutoff);
                }
            });
        }
    }

    public void reset(Long fulfillmentId) {
        attempts.remove(fulfillmentId);
    }
}
