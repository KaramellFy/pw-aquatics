package com.pwaquatics.shop.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.RateLimitedException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * In-memory token-bucket rate limiting per (limit name, client key).
 *
 * <p>State lives in this instance only, which is fine for the single-instance deployment
 * described in docs/ARCHITECTURE.md. Move to a shared store before scaling out.
 */
@Component
public class RateLimiter {

    private final AppProperties properties;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public RateLimiter(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Consumes one token for {@code key} under the limit named {@code limitName}
     * (configured in {@code app.rate-limits}).
     *
     * @throws RateLimitedException when the client has no tokens left
     */
    public void consume(String limitName, String key) {
        AppProperties.RateLimit limit = properties.rateLimit(limitName);
        Bucket bucket = buckets.get(limitName + ':' + key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillIntervally(limit.capacity(), limit.period())
                        .build())
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitedException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }
}
