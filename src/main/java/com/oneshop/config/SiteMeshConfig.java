package com.oneshop.config;

import jakarta.servlet.DispatcherType;
import org.sitemesh.builder.SiteMeshFilterBuilder;
import org.sitemesh.config.ConfigurableSiteMeshFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;

/**
 * Registers SiteMesh 3 as the decorator (layout) mechanism. Every HTML response is wrapped by the
 * decorator served at {@link #DECORATOR_PATH} (see DecoratorController and templates/layouts/main.html).
 */
@Configuration
public class SiteMeshConfig {

    public static final String DECORATOR_PATH = "/decorators/main";

    /** Runs after Spring Security (order -100) so that security redirects and 401/403 are never decorated. */
    private static final int FILTER_ORDER = 0;

    @Bean
    public FilterRegistrationBean<OneShopSiteMeshFilter> siteMeshFilter() {
        FilterRegistrationBean<OneShopSiteMeshFilter> registration = new FilterRegistrationBean<>(
                new OneShopSiteMeshFilter());
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(FILTER_ORDER);
        return registration;
    }

    public static class OneShopSiteMeshFilter extends ConfigurableSiteMeshFilter {

        @Override
        protected void applyCustomConfiguration(SiteMeshFilterBuilder builder) {
            builder.setDecoratorPrefix("").addDecoratorPath("/*", DECORATOR_PATH)
                    .addExcludedPath("/decorators/*")
                    .addExcludedPath("/api/*")
                    .addExcludedPath("/health")
                    .addExcludedPath("/css/*")
                    .addExcludedPath("/js/*")
                    .addExcludedPath("/images/*")
                    .addExcludedPath("/vendor/*");
        }
    }
}
