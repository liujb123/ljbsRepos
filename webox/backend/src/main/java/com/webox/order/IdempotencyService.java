package com.webox.order;

import com.webox.config.WeBoxProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis fast path for order idempotency.
 *
 * <p>This only short-circuits duplicate submissions before they reach the database. The
 * guarantee itself lives in the {@code uk_orders_user_client_request} unique index, so this
 * class deliberately <b>fails open</b>: if Redis is unreachable the request proceeds and MySQL
 * still prevents a second order.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "webox:idem:order:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public IdempotencyService(StringRedisTemplate redis, WeBoxProperties properties) {
        this.redis = redis;
        this.ttl = Duration.ofHours(properties.order().idempotencyTtlHours());
    }

    private String key(Long userId, String clientRequestId) {
        return KEY_PREFIX + userId + ":" + clientRequestId;
    }

    /**
     * @return {@code true} when this caller is the first to submit the key (or when Redis is
     *         unavailable, in which case the database unique index takes over).
     */
    public boolean tryAcquire(Long userId, String clientRequestId) {
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(key(userId, clientRequestId), "1", ttl);
            return !Boolean.FALSE.equals(acquired);
        } catch (Exception ex) {
            log.warn("Idempotency fast path unavailable; relying on the database constraint: {}",
                    ex.getMessage());
            return true;
        }
    }

    /**
     * Releases the key so the employee can retry after a failed attempt (validation error,
     * insufficient data, and so on). Without this a rejected order would look like a duplicate.
     */
    public void release(Long userId, String clientRequestId) {
        try {
            redis.delete(key(userId, clientRequestId));
        } catch (Exception ex) {
            log.warn("Could not release idempotency key (it will expire in {}): {}", ttl, ex.getMessage());
        }
    }
}
