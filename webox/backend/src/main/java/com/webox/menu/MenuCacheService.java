package com.webox.menu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webox.config.WeBoxProperties;
import com.webox.menu.dto.MenuDishView;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed cache for the whole-day menu snapshot.
 *
 * <p><b>Redis is an accelerator, never the source of truth.</b> Every operation degrades to
 * "cache miss" when Redis is unreachable, which sends the caller to MySQL and keeps the API
 * working. The fallback is deliberately <i>not</i> an in-process map: a local cache would go
 * stale per instance and would silently impersonate Redis.
 *
 * <p>Invalidation is explicit and narrow:
 * <ul>
 *   <li>Any dish write -&gt; {@link #evictAll()} (a dish change affects every date).</li>
 *   <li>A daily-menu write -&gt; {@link #evict(LocalDate)} for that date only.</li>
 *   <li>The TTL is a safety net for anything that slips through.</li>
 * </ul>
 * Deletion uses a SCAN cursor rather than {@code KEYS}, which would block the server.
 */
@Service
public class MenuCacheService {

    private static final Logger log = LoggerFactory.getLogger(MenuCacheService.class);
    private static final String KEY_PREFIX = "webox:menu:";
    private static final int SCAN_BATCH = 200;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();

    public MenuCacheService(StringRedisTemplate redis, ObjectMapper objectMapper, WeBoxProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(properties.menuCache().ttlMinutes());
    }

    public String key(LocalDate date) {
        return KEY_PREFIX + date;
    }

    public Optional<List<MenuDishView>> get(LocalDate date) {
        try {
            String json = redis.opsForValue().get(key(date));
            if (json == null) {
                misses.incrementAndGet();
                return Optional.empty();
            }
            List<MenuDishView> items = objectMapper.readValue(json, new TypeReference<List<MenuDishView>>() {
            });
            hits.incrementAndGet();
            return Optional.of(items);
        } catch (Exception ex) {
            fallbacks.incrementAndGet();
            log.warn("Menu cache GET failed for {} - serving from MySQL instead: {}", date, rootMessage(ex));
            return Optional.empty();
        }
    }

    public void put(LocalDate date, List<MenuDishView> items) {
        try {
            redis.opsForValue().set(key(date), objectMapper.writeValueAsString(items), ttl);
        } catch (Exception ex) {
            log.warn("Menu cache SET failed for {} - continuing without cache: {}", date, rootMessage(ex));
        }
    }

    public void evict(LocalDate date) {
        try {
            redis.delete(key(date));
            log.debug("Evicted menu snapshot for {}", date);
        } catch (Exception ex) {
            log.warn("Menu cache evict failed for {} (TTL will still expire it): {}", date, rootMessage(ex));
        }
    }

    /** Drops every cached menu snapshot. Called after any dish mutation. */
    public void evictAll() {
        try {
            Long deleted = redis.execute((RedisCallback<Long>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(SCAN_BATCH).build();
                long total = 0;
                List<byte[]> batch = new ArrayList<>(SCAN_BATCH);
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= SCAN_BATCH) {
                            total += nullSafe(connection.keyCommands().del(batch.toArray(new byte[0][])));
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    total += nullSafe(connection.keyCommands().del(batch.toArray(new byte[0][])));
                }
                return total;
            });
            log.debug("Evicted {} menu snapshot key(s)", deleted);
        } catch (Exception ex) {
            log.warn("Menu cache evictAll failed (TTL will still expire entries): {}", rootMessage(ex));
        }
    }

    public CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), fallbacks.get(), ttl.toMinutes());
    }

    private static long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    /**
     * @param hits              reads served from Redis
     * @param misses            reads that had to load from MySQL
     * @param fallbacks         reads that failed because Redis was unreachable
     * @param ttlMinutes        configured snapshot lifetime
     */
    public record CacheStats(long hits, long misses, long fallbacks, long ttlMinutes) {
    }
}
