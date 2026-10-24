package com.oneshop.service.payment;

import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentStatus;

import java.util.UUID;

/**
 * Encoding of the mock gateway ledger in the existing {@code payments} columns (no table, no column is added):
 *
 * <pre>
 * AWAITING   status PENDING, paid_at NULL,     transaction_code MOCK-&lt;ref&gt;
 * IN_DOUBT   status PENDING, paid_at = submit, transaction_code MOCK-&lt;ref&gt;~UNKNOWN
 * CAPTURED   status PENDING, paid_at = capture, transaction_code MOCK-&lt;ref&gt;   (money collected, not yet applied)
 * DECLINED   status FAILED,  paid_at NULL
 * SETTLED    status SUCCESS, paid_at = capture (CHECK requires it)
 * </pre>
 *
 * <p>{@code transaction_code} keeps the gateway reference as its prefix, so de-duplication is always by
 * {@link #reference(String)}. The suffix is the only marker, because {@code payments.status} has no UNKNOWN value.
 */
public final class MockLedger {

    public static final String PREFIX = "MOCK-";
    static final String DOUBT_SUFFIX = "~UNKNOWN";

    private MockLedger() {
    }

    /** A new reference made by the backend; the browser never supplies one. */
    public static String newReference() {
        return PREFIX + UUID.randomUUID();
    }

    /** The gateway reference of a stored transaction code (the in-doubt marker removed). */
    public static String reference(String code) {
        if (code == null) {
            return null;
        }
        return code.endsWith(DOUBT_SUFFIX) ? code.substring(0, code.length() - DOUBT_SUFFIX.length()) : code;
    }

    public static boolean inDoubt(String code) {
        return code != null && code.endsWith(DOUBT_SUFFIX);
    }

    public static String withDoubt(String code) {
        return inDoubt(code) ? code : code + DOUBT_SUFFIX;
    }

    /** Ledger state of one attempt row. */
    public static LedgerState stateOf(Payment payment) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return LedgerState.SETTLED;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return LedgerState.DECLINED;
        }
        if (inDoubt(payment.getTransactionCode())) {
            return LedgerState.IN_DOUBT;
        }
        return payment.getPaidAt() != null ? LedgerState.CAPTURED : LedgerState.AWAITING;
    }
}
