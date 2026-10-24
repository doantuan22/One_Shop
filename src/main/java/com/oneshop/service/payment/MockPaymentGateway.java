package com.oneshop.service.payment;

import com.oneshop.config.PaymentProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Mock payment gateway over the durable ledger of {@link MockLedger}. It keeps no state of its own, so a restart
 * loses nothing: every answer is derived from the {@code payments} rows.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final PaymentProperties properties;
    private final Environment environment;

    public MockPaymentGateway(PaymentProperties properties) {
        this(properties, new StandardEnvironment());
    }

    @Autowired
    public MockPaymentGateway(PaymentProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public boolean enabled() {
        return properties.isMockGatewayEnabled() && !environment.acceptsProfiles(Profiles.of("prod"));
    }

    @Override
    public GatewayVerification verify(Order order, List<Payment> attempts) {
        // An inconsistent persisted ledger is uncertain evidence, never a collected/not-collected conclusion.
        List<Payment> online = attempts.stream().filter(p -> p.getMethod() == PaymentMethod.ONLINE).toList();
        if (online.stream().anyMatch(p -> order == null || order.getId() == null || order.getTotalAmount() == null
                || p.getOrder() == null || !order.getId().equals(p.getOrder().getId()) || p.getAmount() == null
                || p.getAmount().compareTo(order.getTotalAmount()) != 0)) {
            return new GatewayVerification(GatewayVerification.Result.UNKNOWN, null, null);
        }
        Payment collected = online.stream()
                .filter(p -> {
                    LedgerState state = MockLedger.stateOf(p);
                    return state == LedgerState.SETTLED || state == LedgerState.CAPTURED;
                })
                .min(Comparator.comparing(Payment::getPaidAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (collected != null) {
            return new GatewayVerification(GatewayVerification.Result.SUCCESS,
                    MockLedger.reference(collected.getTransactionCode()), collected.getPaidAt());
        }
        Payment doubtful = online.stream().filter(p -> MockLedger.stateOf(p) == LedgerState.IN_DOUBT).findFirst()
                .orElse(null);
        if (doubtful != null) {
            return new GatewayVerification(GatewayVerification.Result.UNKNOWN,
                    MockLedger.reference(doubtful.getTransactionCode()), doubtful.getPaidAt());
        }
        Payment declined = online.stream().filter(p -> MockLedger.stateOf(p) == LedgerState.DECLINED).findFirst()
                .orElse(null);
        if (declined != null) {
            return new GatewayVerification(GatewayVerification.Result.FAILED,
                    MockLedger.reference(declined.getTransactionCode()), null);
        }
        String reference = online.isEmpty() ? null : MockLedger.reference(online.get(0).getTransactionCode());
        return new GatewayVerification(GatewayVerification.Result.NOT_PAID, reference, null);
    }

    @Override
    public void capture(Payment attempt, LocalDateTime at) {
        LedgerState state = MockLedger.stateOf(attempt);
        if (state != LedgerState.AWAITING && state != LedgerState.IN_DOUBT) {
            throw new BadRequestException("Lần thanh toán không còn ở trạng thái chờ cổng thanh toán.");
        }
        attempt.setPaidAt(at.withNano(0));
        attempt.setTransactionCode(MockLedger.reference(attempt.getTransactionCode()));
    }

    @Override
    public void decline(Payment attempt) {
        LedgerState state = MockLedger.stateOf(attempt);
        if (state != LedgerState.AWAITING && state != LedgerState.IN_DOUBT) {
            throw new BadRequestException("Lần thanh toán không còn ở trạng thái chờ cổng thanh toán.");
        }
        attempt.setStatus(com.oneshop.entity.PaymentStatus.FAILED);
        attempt.setPaidAt(null);
        attempt.setTransactionCode(MockLedger.reference(attempt.getTransactionCode()));
    }

    @Override
    public void markInDoubt(Payment attempt, LocalDateTime submittedAt) {
        if (MockLedger.stateOf(attempt) != LedgerState.AWAITING) {
            throw new BadRequestException("Lần thanh toán không còn ở trạng thái chờ cổng thanh toán.");
        }
        attempt.setPaidAt(submittedAt.withNano(0));
        attempt.setTransactionCode(MockLedger.withDoubt(attempt.getTransactionCode()));
    }

    @Override
    public void settleInDoubt(Payment attempt, boolean collected) {
        if (MockLedger.stateOf(attempt) != LedgerState.IN_DOUBT) {
            throw new BadRequestException("Lần thanh toán không ở trạng thái chưa xác định.");
        }
        if (collected) {
            attempt.setTransactionCode(MockLedger.reference(attempt.getTransactionCode()));
        } else {
            decline(attempt);
        }
    }
}
