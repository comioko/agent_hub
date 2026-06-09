package com.agenthub.service.cache;

import com.agenthub.model.dto.MessageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Session context cache - caches recent message history for fast retrieval.
 * Falls back gracefully if Redis is unavailable.
 */
@Service
@ConditionalOnProperty(name = "spring.data.redis.host")
public class SessionCacheService {

    private static final Logger log = LoggerFactory.getLogger(SessionCacheService.class);

    private static final String SESSION_KEY_PREFIX = "session:history:";
    private static final int DEFAULT_TTL_MINUTES = 60;
    private static final int MAX_CACHED_MESSAGES = 50;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private volatile boolean available = false;

    public SessionCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        checkConnection();
    }

    private void checkConnection() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            this.available = true;
            log.info("Redis connection established for session cache");
        } catch (Exception e) {
            this.available = false;
            log.warn("Redis unavailable, session cache disabled: {}", e.getMessage());
        }
    }

    private boolean isAvailable() {
        return available;
    }

    /**
     * Cache conversation message history.
     */
    public void cacheMessages(Long conversationId, List<MessageVO> messages) {
        if (!isAvailable()) return;
        try {
            String key = SESSION_KEY_PREFIX + conversationId;
            // Only cache last N messages
            List<MessageVO> toCache = messages.size() > MAX_CACHED_MESSAGES
                ? messages.subList(messages.size() - MAX_CACHED_MESSAGES, messages.size())
                : messages;
            String json = objectMapper.writeValueAsString(toCache);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(DEFAULT_TTL_MINUTES));
        } catch (Exception e) {
            log.warn("Failed to cache messages for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Get cached message history for a conversation.
     */
    public List<MessageVO> getCachedMessages(Long conversationId) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String key = SESSION_KEY_PREFIX + conversationId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<MessageVO>>() {});
        } catch (Exception e) {
            log.warn("Failed to get cached messages for conversation {}: {}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Invalidate cached history when new message is sent.
     */
    public void invalidateMessages(Long conversationId) {
        if (!isAvailable()) return;
        try {
            String key = SESSION_KEY_PREFIX + conversationId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate cache for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Invalidate all sessions for a user.
     */
    public void invalidateUserSessions(Long userId) {
        if (!isAvailable()) return;
        try {
            String pattern = SESSION_KEY_PREFIX + "*";
            redisTemplate.delete(redisTemplate.keys(pattern));
        } catch (Exception e) {
            log.warn("Failed to invalidate user sessions for user {}: {}", userId, e.getMessage());
        }
    }
}
