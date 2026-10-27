package com.oneshop.service.payment.payos;

/** Failure talking to payOS, classified by what it tells us about the money. */
public abstract class PayosException extends RuntimeException {

    protected PayosException(String message, Throwable cause) {
        super(message, cause);
    }

    /** payOS definitely refused the request (4xx / error code): nothing was created. */
    public static final class Rejected extends PayosException {
        public Rejected(String message, Throwable cause) { super(message, cause); }
    }

    /** Timeout, network error or 5xx: the outcome is UNKNOWN, never "failed". */
    public static final class Unavailable extends PayosException {
        public Unavailable(String message, Throwable cause) { super(message, cause); }
    }

    public static final class NotFound extends PayosException {
        public NotFound(String message, Throwable cause) { super(message, cause); }
    }

    public static final class InvalidSignature extends PayosException {
        public InvalidSignature(String message, Throwable cause) { super(message, cause); }
    }

    /** Credentials missing: payOS is not configured on this server. */
    public static final class NotConfigured extends PayosException {
        public NotConfigured(String message) { super(message, null); }
    }
}
