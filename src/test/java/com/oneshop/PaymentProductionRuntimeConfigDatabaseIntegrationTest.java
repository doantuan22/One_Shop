package com.oneshop;

import com.oneshop.config.CheckoutProperties;
import com.oneshop.config.PaymentProperties;
import com.oneshop.service.payment.MockPaymentGateway;
import com.oneshop.service.payment.PaymentTimeoutWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-001 / BUG-002 at runtime: a real application context started with the {@code prod} profile (against a disposable
 * *_test database) must have the mock gateway, ONLINE checkout and the background timeout worker switched off, and
 * the effective values must be the ones the beans really hold, not just the ones written in a properties file.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("prod")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PaymentProductionRuntimeConfigDatabaseIntegrationTest {

    @Autowired Environment environment;
    @Autowired PaymentProperties payment;
    @Autowired CheckoutProperties checkout;
    @Autowired MockPaymentGateway gateway;
    @Autowired ApplicationContext context;

    @Test
    @DisplayName("BUG-002 prod: mock gateway and ONLINE checkout are off by default")
    void productionHasNoMockGatewayAndNoOnlineCheckout() {
        assertThat(environment.getActiveProfiles()).contains("prod");
        assertThat(payment.isMockGatewayEnabled()).isFalse();
        assertThat(gateway.enabled()).isFalse();
        assertThat(checkout.isOnlineEnabled()).isFalse();
    }

    @Test
    @DisplayName("BUG-001 prod: the payment timeout worker is not started unless it is switched on explicitly")
    void timeoutWorkerIsNotStartedByDefault() {
        assertThat(payment.isTimeoutWorkerEnabled()).isFalse();
        assertThat(context.getBeanNamesForType(PaymentTimeoutWorker.class)).isEmpty();
    }
}
