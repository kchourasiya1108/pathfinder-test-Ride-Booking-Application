package com.project.ridebooking.RideBookingApplication.Cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Simple in-memory cache manager simulating Redis behavior.
 * 
 * BUG CONTRIBUTION: This cache has no proper invalidation mechanism
 * when concurrent updates happen, leading to stale data reads.
 */
@Component
@Slf4j
public class RedisCacheManager {
    
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    
    public RedisCacheManager() {
        // Schedule cleanup of expired entries every 30 seconds
        cleaner.scheduleAtFixedRate(this::cleanupExpired, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Put value in cache with TTL.
     * BUG: No invalidation callback when underlying data changes.
     */
    public void put(String key, Object value, long ttl, TimeUnit unit) {
        long expiryTime = System.currentTimeMillis() + unit.toMillis(ttl);
        cache.put(key, new CacheEntry(value, expiryTime));
        log.debug("Cache put: key={}, ttl={} {}", key, ttl, unit);
    }
    
    /**
     * Get value from cache.
     * BUG: Returns stale data without checking if underlying DB data has changed.
     */
    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        
        if (entry == null) {
            log.debug("Cache miss: key={}", key);
            return null;
        }
        
        if (System.currentTimeMillis() > entry.expiryTime) {
            // Entry expired but not yet cleaned up
            log.debug("Cache entry expired: key={}", key);
            cache.remove(key);
            return null;
        }
        
        log.debug("Cache hit: key={}", key);
        return entry.value;
    }
    
    /**
     * Delete from cache.
     * NOTE: This is called after DB update, but race window exists.
     */
    public void delete(String key) {
        cache.remove(key);
        log.debug("Cache delete: key={}", key);
    }
    
    /**
     * Invalidate all entries matching pattern.
     * BUG: Pattern matching not implemented properly - uses simple contains check.
     */
    public void invalidatePattern(String pattern) {
        cache.keySet().removeIf(key -> key.contains(pattern));
        log.debug("Cache invalidate pattern: {}", pattern);
    }
    
    /**
     * Check if key exists and is not expired.
     */
    public boolean hasKey(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiryTime) {
            cache.remove(key);
            return false;
        }
        return true;
    }
    
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now > entry.getValue().expiryTime);
    }
    
    private static class CacheEntry {
        final Object value;
        final long expiryTime;
        
        CacheEntry(Object value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }
}
