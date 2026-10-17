package com.webox.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the {@code webox.*} configuration tree.
 * Every value can be overridden by an environment variable (see application.yml).
 */
@ConfigurationProperties(prefix = "webox")
public record WeBoxProperties(
        String timeZone,
        Jwt jwt,
        Cors cors,
        Storage storage,
        MenuCache menuCache,
        Order order,
        Seed seed) {

    public record Jwt(String secret, int ttlHours) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(String uploadDir) {
    }

    public record MenuCache(int ttlMinutes) {
    }

    public record Order(int maxItemsPerOrder, int idempotencyTtlHours, int maxAdvanceDays) {
    }

    public record Seed(boolean enabled, String adminEmail, String adminPassword) {
    }
}
