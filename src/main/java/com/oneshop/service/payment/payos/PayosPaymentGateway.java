package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentProvider;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.service.payment.GatewayVerification;
import com.oneshop.service.payment.PaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * The real gateway. It never calls payOS and never inspects {@code MockLedger}: it only reads the evidence that
 * {@link PayosEvidence} has already stored on the {@code payments} rows (webhook or link query) and turns it into the
 * answer the reconciliation engine expects. Consequently a verification is a pure function of committed rows.
 *
 * <pre>
 * a row with provider_status PAID, amount paid == amount, bank time known  -> SUCCESS (at the BANK time)
 * PROCESSING, UNDERPAID, over-payment, PAID without a bank time, mismatch   -> UNKNOWN (never guessed)
 * PENDING / CANCELLED / EXPIRED / FAILED / no evidence                       -> NOT_PAID
 * </pre>
 *
 * <p>FAILED is never answered: a customer who closes the payOS page or whose link expires has not "failed" the Order;
 * the Order keeps its payment deadline and the customer may open a new link until then.
 */
@Component
@ConditionalOnProperty(name = "oneshop.payment.provider", havingValue = "PAYOS")
public class PayosPaymentGateway implements PaymentGateway {

    private final PayosProperties properties;

    public PayosPaymentGateway(PayosProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYOS;
    }

    /** Simulation is never allowed with real money. */
    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public boolean onlineAvailable() {
        return properties.isConfigured();
    }

    @Override
    public GatewayVerification verify(Order order, List<Payment> attempts) {
        List<Payment> rows = attempts.stream()
                .filter(p -> p.getMethod() == PaymentMethod.ONLINE && p.getProvider() == PaymentProvider.PAYOS).toList();
        if (rows.stream().anyMatch(p -> order == null || order.getId() == null || order.getTotalAmount() == null
                || p.getOrder() == null || !order.getId().equals(p.getOrder().getId()) || p.getAmount() == null
                || p.getAmount().compareTo(order.getTotalAmount()) != 0)) {
            return new GatewayVerification(GatewayVerification.Result.UNKNOWN, null, null);
        }
        Payment collected = rows.stream().filter(PayosPaymentGateway::fullyPaid)
                .min(Comparator.comparing(Payment::getProviderPaidAt)).orElse(null);
        if (collected != null) {
            return new GatewayVerification(GatewayVerification.Result.SUCCESS, reference(collected),
                    collected.getProviderPaidAt(), collected.getId());
        }
        Payment uncertain = rows.stream().filter(PayosPaymentGateway::uncertain).findFirst().orElse(null);
        if (uncertain != null) {
            return new GatewayVerification(GatewayVerification.Result.UNKNOWN, reference(uncertain), null,
                    uncertain.getId());
        }
        return new GatewayVerification(GatewayVerification.Result.NOT_PAID, rows.isEmpty() ? null : reference(rows.get(0)),
                null, rows.isEmpty() ? null : rows.get(0).getId());
    }

    /** Evidence that exactly the expected amount reached the bank, and when. */
    static boolean fullyPaid(Payment p) {
        Long expected = PayosMath.exactVnd(p.getAmount());
        return "PAID".equals(p.getProviderStatus()) && expected != null && expected.equals(p.getProviderAmountPaid())
                && p.getProviderPaidAt() != null;
    }

    /** Money may have moved but is not proven to be exactly the amount (or not provably timed). */
    static boolean uncertain(Payment p) {
        if (fullyPaid(p)) {
            return false;
        }
        String status = p.getProviderStatus();
        boolean moneySeen = p.getProviderAmountPaid() != null && p.getProviderAmountPaid() > 0;
        return "PROCESSING".equals(status) || "UNDERPAID".equals(status) || "PAID".equals(status) || moneySeen;
    }

    static String reference(Payment p) {
        return p.getProviderReference() != null ? p.getProviderReference() : "PAYOS-" + p.getProviderOrderCode();
    }

    @Override
    public void applyCollected(Payment attempt, LocalDateTime at) {
        attempt.setStatus(PaymentStatus.SUCCESS);
        attempt.setPaidAt(at.withNano(0));
    }

    // the simulation operations are not available for real money
    @Override
    public void capture(Payment attempt, LocalDateTime at) {
        throw unsupported();
    }

    @Override
    public void decline(Payment attempt) {
        throw unsupported();
    }

    @Override
    public void markInDoubt(Payment attempt, LocalDateTime submittedAt) {
        throw unsupported();
    }

    @Override
    public void settleInDoubt(Payment attempt, boolean collected) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Thanh toán payOS không có chế độ mô phỏng.");
    }
}
