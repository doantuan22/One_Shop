package com.oneshop;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProductionDefaultsTest {
    @Test
    void productionDisablesMockGatewayAndOnlineCheckoutUnlessExplicitlyOverridden() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("environment", Map.of()));
        sources.addLast(new ResourcePropertySource("classpath:application-prod.properties"));
        sources.addLast(new ResourcePropertySource("classpath:application.properties"));
        var resolver = new PropertySourcesPropertyResolver(sources);

        assertThat(resolver.getProperty("oneshop.payment.mock-gateway-enabled")).isEqualTo("false");
        assertThat(resolver.getProperty("oneshop.checkout.online-enabled")).isEqualTo("false");
        assertThat(resolver.getProperty("oneshop.payment.timeout-worker-enabled")).isEqualTo("false");
    }
}
