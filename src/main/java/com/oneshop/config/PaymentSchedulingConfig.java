package com.oneshop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} only when the payment timeout worker is enabled. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "oneshop.payment.timeout-worker-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentSchedulingConfig {
}
