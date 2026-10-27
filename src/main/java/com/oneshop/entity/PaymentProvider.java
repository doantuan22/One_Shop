package com.oneshop.entity;

/** Values of {@code payments.provider}; {@code null} for COD / PAY_AT_STORE rows, which no gateway handles. */
public enum PaymentProvider {
    /** Simulated gateway: dev, demo and test only. Nothing it records is real money. */
    MOCK,
    /** payOS hosted checkout: the only provider whose result is real money. */
    PAYOS
}
