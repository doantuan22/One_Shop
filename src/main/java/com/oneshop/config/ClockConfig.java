package com.oneshop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * One injectable server clock. Payment deadlines are compared with {@code LocalDateTime.now(clock)}, the same
 * time source (server local time) that writes {@code created_at}/{@code paid_at}; tests replace it with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
