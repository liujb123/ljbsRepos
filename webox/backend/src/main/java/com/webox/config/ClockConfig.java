package com.webox.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for business rules.
 *
 * <p>Everything that depends on the current time - most importantly the meal-slot cut-off
 * logic - takes this {@link Clock} as a parameter instead of calling {@code LocalDateTime.now()},
 * so the rules can be pinned in unit tests by injecting {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public ZoneId businessZone(WeBoxProperties properties) {
        return ZoneId.of(properties.timeZone());
    }

    @Bean
    public Clock clock(ZoneId businessZone) {
        return Clock.system(businessZone);
    }
}
