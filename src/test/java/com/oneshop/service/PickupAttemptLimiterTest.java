package com.oneshop.service;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.PickupAttemptLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure tests of the pickup code limiter and of the action table. */
class PickupAttemptLimiterTest {

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }
    }

    private final MovableClock clock = new MovableClock();
    private final PickupAttemptLimiter limiter = new PickupAttemptLimiter(clock);

    @Test
    @DisplayName("four wrong codes do not lock; the fifth does")
    void locksAtFive() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(1L);
        }
        assertThatCode(() -> limiter.requireNotLocked(1L)).doesNotThrowAnyException();

        limiter.recordFailure(1L);

        assertThatThrownBy(() -> limiter.requireNotLocked(1L)).isInstanceOf(FulfillmentConflictException.class)
                .extracting(e -> ((FulfillmentConflictException) e).getCode())
                .isEqualTo(FulfillmentConflictException.PICKUP_CODE_LOCKED);
    }

    @Test
    @DisplayName("the lock ends after 15 minutes and the counter starts again")
    void unlocksAfterWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(2L);
        }
        clock.advanceMinutes(14);
        assertThatThrownBy(() -> limiter.requireNotLocked(2L)).isInstanceOf(FulfillmentConflictException.class);

        clock.advanceMinutes(2);

        assertThatCode(() -> limiter.requireNotLocked(2L)).doesNotThrowAnyException();
        limiter.recordFailure(2L);
        assertThatCode(() -> limiter.requireNotLocked(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("locks are per Fulfillment and a success resets the counter")
    void perFulfillmentAndReset() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(3L);
        }
        assertThatCode(() -> limiter.requireNotLocked(4L)).doesNotThrowAnyException();

        limiter.reset(3L);

        assertThatCode(() -> limiter.requireNotLocked(3L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("actions form exactly the two state machines of the spec")
    void actionTable() {
        Set<String> delivery = new java.util.HashSet<>();
        for (FulfillmentAction action : FulfillmentAction.values()) {
            if (action.appliesTo(FulfillmentType.DELIVERY)) {
                delivery.add(action.getFrom() + ">" + action.getTo());
            }
        }
        assertThat(delivery).containsExactlyInAnyOrder("CONFIRMED>PREPARING", "PREPARING>PACKED",
                "PACKED>SHIPPING", "SHIPPING>COMPLETED", "SHIPPING>FAILED");

        Set<String> pickup = new java.util.HashSet<>();
        for (FulfillmentAction action : FulfillmentAction.values()) {
            if (action.appliesTo(FulfillmentType.STORE_PICKUP)) {
                pickup.add(action.getFrom() + ">" + action.getTo());
            }
        }
        assertThat(pickup).containsExactlyInAnyOrder("CONFIRMED>PREPARING", "PREPARING>READY_FOR_PICKUP",
                "READY_FOR_PICKUP>COMPLETED", "READY_FOR_PICKUP>FAILED");
    }

    @Test
    @DisplayName("no action leaves a terminal status or ends in CANCELLED; paths are unique")
    void actionInvariants() {
        Set<OrderFulfillmentStatus> terminal = EnumSet.of(OrderFulfillmentStatus.COMPLETED,
                OrderFulfillmentStatus.CANCELLED, OrderFulfillmentStatus.FAILED);
        for (FulfillmentAction action : FulfillmentAction.values()) {
            assertThat(terminal).doesNotContain(action.getFrom());
            assertThat(action.getTo()).isNotEqualTo(OrderFulfillmentStatus.CANCELLED);
            assertThat(FulfillmentAction.fromPath(action.getPath())).isEqualTo(action);
        }
        assertThat(Arrays.stream(FulfillmentAction.values()).map(FulfillmentAction::getPath).distinct().count())
                .isEqualTo(FulfillmentAction.values().length);
        assertThat(FulfillmentAction.fromPath("teleport")).isNull();
    }
}
