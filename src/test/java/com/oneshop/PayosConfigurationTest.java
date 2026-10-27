package com.oneshop;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayosConfigurationTest {

    private PropertySourcesPropertyResolver prod(Map<String, Object> env) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("environment", env));
        sources.addLast(new ResourcePropertySource("classpath:application-prod.properties"));
        sources.addLast(new ResourcePropertySource("classpath:application.properties"));
        return new PropertySourcesPropertyResolver(sources);
    }

    @Test
    void productionStaysOnTheClosedMockProviderUntilPayosIsChosenExplicitly() throws IOException {
        var resolver = prod(Map.of());
        assertThat(resolver.getProperty("oneshop.payment.provider")).isEqualTo("MOCK");
        assertThat(resolver.getProperty("oneshop.payment.mock-gateway-enabled")).isEqualTo("false");
        assertThat(resolver.getProperty("oneshop.checkout.online-enabled")).isEqualTo("false");
        assertThat(resolver.getProperty("oneshop.payment.timeout-worker-enabled")).isEqualTo("false");
        assertThat(resolver.getProperty("oneshop.payos.client-id")).isEmpty();
        assertThat(resolver.getProperty("oneshop.payos.api-key")).isEmpty();
        assertThat(resolver.getProperty("oneshop.payos.checksum-key")).isEmpty();
    }

    @Test
    void payosKeysComeOnlyFromTheEnvironment() throws IOException {
        var resolver = prod(Map.of("ONESHOP_PAYMENT_PROVIDER", "PAYOS", "PAYOS_CLIENT_ID", "cid",
                "PAYOS_API_KEY", "key", "PAYOS_CHECKSUM_KEY", "sum", "ONESHOP_PUBLIC_BASE_URL", "https://shop.example.com"));
        assertThat(resolver.getProperty("oneshop.payment.provider")).isEqualTo("PAYOS");
        assertThat(resolver.getProperty("oneshop.payos.api-key")).isEqualTo("key");
        assertThat(resolver.getProperty("oneshop.payos.public-base-url")).isEqualTo("https://shop.example.com");
    }

    @Test
    void noKeyIsCommittedInTheRepositoryConfiguration() throws IOException {
        for (String file : new String[]{"src/main/resources/application.properties",
                "src/main/resources/application-prod.properties", "src/main/resources/application-dev.properties",
                ".env.example"}) {
            Path path = Path.of(file);
            if (!Files.exists(path)) {
                continue;
            }
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || !trimmed.toUpperCase().contains("PAYOS")) {
                    continue;
                }
                if (trimmed.matches("(?i).*(api[-_]?key|checksum[-_]?key|client[-_]?id)\\s*=.*")) {
                    String value = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                    assertThat(value).as(file + ": " + trimmed).matches("(\\$\\{[A-Z_]+:\\})?");
                }
            }
        }
    }

    @Test
    void theConfigurationReportsWhatIsMissing() {
        PayosProperties p = new PayosProperties();
        assertThat(p.isConfigured()).isFalse();
        assertThat(p.problem()).contains("PAYOS_CLIENT_ID");
        p.setClientId("a");
        p.setApiKey("b");
        p.setChecksumKey("c");
        assertThat(p.problem()).contains("ONESHOP_PUBLIC_BASE_URL");
        p.setPublicBaseUrl("https://shop.example.com/path");
        assertThat(p.isConfigured()).isFalse(); // an origin only
        p.setPublicBaseUrl("https://shop.example.com/");
        assertThat(p.isConfigured()).isTrue();
        assertThat(p.origin()).isEqualTo("https://shop.example.com");
        p.setPublicBaseUrl("http://localhost:8080");
        assertThat(p.isConfigured()).isTrue(); // local development tunnel only
    }

    @Test
    void providerEnumHasExactlyTheTwoSupportedValues() {
        assertThat(PaymentProvider.values()).containsExactly(PaymentProvider.MOCK, PaymentProvider.PAYOS);
    }
}
