package com.webox.order;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Human-readable order numbers of the form {@code WB20260324-000123}.
 *
 * <p>A Redis counter gives a short, monotonic, per-day sequence. When Redis is unavailable the
 * generator falls back to a random suffix - the {@code UNIQUE(order_no)} index is what actually
 * guarantees uniqueness, and the caller retries on collision.
 */
@Service
public class OrderNoGenerator {

    private static final Logger log = LoggerFactory.getLogger(OrderNoGenerator.class);
    private static final String KEY_PREFIX = "webox:orderno:";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public OrderNoGenerator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next(LocalDate businessDate) {
        String day = businessDate.format(DAY);
        try {
            String counterKey = KEY_PREFIX + day;
            Long sequence = redis.opsForValue().increment(counterKey);
            if (sequence != null) {
                if (sequence == 1L) {
                    // Keep the counter for a couple of days so numbers restart per day.
                    redis.expire(counterKey, Duration.ofDays(2));
                }
                return format(day, sequence);
            }
        } catch (Exception ex) {
            log.warn("Order number counter unavailable; using a random sequence: {}", ex.getMessage());
        }
        // Fallback keeps exactly the same shape as the counter path: WB<yyyyMMdd>-<6 digits>.
        // Uniqueness is still guaranteed by the UNIQUE(order_no) index, and the caller retries.
        return format(day, RANDOM.nextInt(1_000_000));
    }

    private static String format(String day, long sequence) {
        return "WB" + day + "-" + String.format("%06d", sequence);
    }
}
