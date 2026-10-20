package com.oneshop.service.payment;

import java.util.Arrays;

/**
 * The one classification of an ONLINE Order's payment used by the Admin payment list, the case detail and the Admin
 * Dashboard, so all three always agree. The categories are mutually exclusive and are evaluated in this order:
 *
 * <ol>
 *   <li>{@link #ATTENTION}: an Administrator has to act. The Order is {@code NEEDS_ATTENTION}; or it is still
 *       {@code PENDING_PAYMENT} after its payment deadline (nothing decided it in time); or money was collected and the
 *       simulated refund failed / never finished.</li>
 *   <li>{@link #RECONCILING}: still inside the payment deadline and the gateway answered UNKNOWN. The system waits for
 *       evidence; nobody has to act yet.</li>
 *   <li>{@link #WAITING}: inside the deadline, the customer simply has not paid yet (normal).</li>
 *   <li>{@link #PAID}, {@link #REFUNDED} (simulated refund recorded), {@link #FAILED} (cancelled without money): final.</li>
 * </ol>
 *
 * <p>The {@code SQL} counterparts of these rules live in {@code OrderRepository}; a database test keeps both equal.
 */
public enum PaymentCaseCategory {
    ATTENTION("attention", "Cần xử lý"),
    RECONCILING("reconciling", "Chờ đối soát"),
    WAITING("waiting", "Đang chờ thanh toán"),
    PAID("paid", "Đã thanh toán"),
    REFUNDED("refunded", "Đã hoàn tiền (mô phỏng)"),
    FAILED("failed", "Thất bại / đã hủy"),
    OTHER("other", "Khác");

    private final String key;
    private final String label;

    PaymentCaseCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** The list filter named by a request parameter (named by its key); {@code null} for "all" or anything unknown. */
    public static PaymentCaseCategory fromKey(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values()).filter(c -> c != OTHER && c.key.equalsIgnoreCase(value.trim())).findFirst()
                .orElse(null);
    }
}
