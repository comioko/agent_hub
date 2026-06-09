package com.agenthub.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Online status management - tracks user online/offline status via Redis.
 * Falls back gracefully if Redis is unavailable.
 */
@Service
@ConditionalOnProperty(name = "spring.data.redis.host")
public class OnlineStatusService {

    private static final Logger log = LoggerFactory.getLogger(OnlineStatusService.class);

    private static final String ONLINE_USERS_KEY = "online:users";
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redisTemplate;
    private volatile boolean available = false;

    public OnlineStatusService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        checkConnection();
    }

    private void checkConnection() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            this.available = true;
            log.info("Redis connection established for online status");
        } catch (Exception e) {
            this.available = false;
            log.warn("Redis unavailable, online status disabled: {}", e.getMessage());
        }
    }

    private boolean isAvailable() {
        return available;
    }

    /**
     * Mark user as online (with heartbeat TTL).
     */
    public void setOnline(Long userId) {
        if (!isAvailable()) return;
        try {
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId.toString());
            redisTemplate.opsForValue().set("online:heartbeat:" + userId, "1", HEARTBEAT_TTL);
        } catch (Exception e) {
            log.warn("Failed to set online status for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Mark user as offline.
     */
    public void setOffline(Long userId) {
        if (!isAvailable()) return;
        try {
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
            redisTemplate.delete("online:heartbeat:" + userId);
        } catch (Exception e) {
            log.warn("Failed to set offline status for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Refresh heartbeat to keep user online.
     */
    public void heartbeat(Long userId) {
        if (!isAvailable()) return;
        try {
            String key = "online:heartbeat:" + userId;
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                redisTemplate.expire(key, HEARTBEAT_TTL);
            } else {
                // Heartbeat key expired, user is offline - re-add them
                setOnline(userId);
            }
        } catch (Exception e) {
            log.warn("Failed to refresh heartbeat for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Check if a specific user is online.
     */
    public boolean isOnline(Long userId) {
        if (!isAvailable()) return false;
        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId.toString());
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.warn("Failed to check online status for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get all online user IDs.
     */
    public Set<Long> getOnlineUsers() {
        if (!isAvailable()) return Set.of();
        try {
            Set<String> members = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
            if (members == null || members.isEmpty()) {
                return Set.of();
            }
            return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Failed to get online users: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * Get count of online users.
     */
    public long getOnlineCount() {
        if (!isAvailable()) return 0;
        try {
            Long size = redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.warn("Failed to get online count: {}", e.getMessage());
            return 0;
        }
    }
}
