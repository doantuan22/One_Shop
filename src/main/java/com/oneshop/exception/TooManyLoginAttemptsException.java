package com.oneshop.exception;

/** Login refused without checking the password because too many attempts were made recently. */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau " + minutes(retryAfterSeconds) + " phút.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static long minutes(long seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }
}
