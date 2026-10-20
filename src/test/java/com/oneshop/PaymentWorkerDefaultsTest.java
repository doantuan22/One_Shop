package com.oneshop;

import com.oneshop.config.PaymentProperties;
import com.oneshop.config.PaymentSchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payment timeout worker WRITES (it cancels overdue ONLINE Orders and restores stock), so it must never start
 * merely because the application started on a database whose data has to be kept. It is an explicit opt-in.
 *
 * <p>The shipped {@code application.properties} / {@code application-prod.properties} are read on their own (the
 * surefire JVM forces the worker off with a system property, which would otherwise hide a wrong default). No database.
 */
class PaymentWorkerDefaultsTest {

    private static PropertySourcesPropertyResolver resolverOf(Map<String, Object> overrides, String... files)
            throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        if (!overrides.isEmpty()) {
            sources.addLast(new MapPropertySource("overrides", overrides));
        }
        for (String file : files) {
            sources.addLast(new ResourcePropertySource("classpath:" + file));
        }
        return new PropertySourcesPropertyResolver(sources);
    }

    @Test
    void javaDefaultIsOff() {
        assertThat(new PaymentProperties().isTimeoutWorkerEnabled()).isFalse();
    }

    @Test
    void theShippedConfigurationLeavesTheWorkerOff() throws IOException {
        for (String[] files : new String[][] {{"application.properties"},
                {"application-prod.properties", "application.properties"}}) {
            assertThat(resolverOf(Map.of(), files).getProperty("oneshop.payment.timeout-worker-enabled"))
                    .as(String.join("+", files)).isEqualTo("false");
        }
    }

    @Test
    void anExplicitEnvironmentSettingTurnsItOn() throws IOException {
        var resolver = resolverOf(Map.of("ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED", "true"), "application.properties");
        assertThat(resolver.getProperty("oneshop.payment.timeout-worker-enabled")).isEqualTo("true");
    }

    @Test
    void productionKeepsOnlineClosedBecauseThereIsNoGatewayThere() throws IOException {
        assertThat(resolverOf(Map.of(), "application-prod.properties", "application.properties")
                .getProperty("oneshop.checkout.online-enabled")).isEqualTo("false");
    }

    @Test
    void theScheduledWorkerIsNotCreatedUnlessExplicitlyEnabled() {
        new ApplicationContextRunner().withUserConfiguration(PaymentSchedulingConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(PaymentSchedulingConfig.class));
        new ApplicationContextRunner().withUserConfiguration(PaymentSchedulingConfig.class)
                .withPropertyValues("oneshop.payment.timeout-worker-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PaymentSchedulingConfig.class));
        new ApplicationContextRunner().withUserConfiguration(PaymentSchedulingConfig.class)
                .withPropertyValues("oneshop.payment.timeout-worker-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(PaymentSchedulingConfig.class));
    }
}
