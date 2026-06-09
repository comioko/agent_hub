package com.agenthub.service.cache;

import com.agenthub.model.entity.Agent;
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
 * Agent configuration cache - caches agent list and individual agent configs.
 * Falls back gracefully if Redis is unavailable.
 */
@Service
@ConditionalOnProperty(name = "spring.data.redis.host")
public class AgentCacheService {

    private static final Logger log = LoggerFactory.getLogger(AgentCacheService.class);

    private static final String AGENT_LIST_KEY = "agent:list";
    private static final String AGENT_KEY_PREFIX = "agent:config:";
    private static final int AGENT_LIST_TTL_MINUTES = 10;
    private static final int AGENT_CONFIG_TTL_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private volatile boolean available = false;

    public AgentCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        checkConnection();
    }

    private void checkConnection() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            this.available = true;
            log.info("Redis connection established for agent cache");
        } catch (Exception e) {
            this.available = false;
            log.warn("Redis unavailable, agent cache disabled: {}", e.getMessage());
        }
    }

    private boolean isAvailable() {
        return available;
    }

    /**
     * Cache the full list of enabled agents.
     */
    public void cacheAgentList(List<Agent> agents) {
        if (!isAvailable()) return;
        try {
            String json = objectMapper.writeValueAsString(agents);
            redisTemplate.opsForValue().set(AGENT_LIST_KEY, json, Duration.ofMinutes(AGENT_LIST_TTL_MINUTES));
        } catch (Exception e) {
            log.warn("Failed to cache agent list: {}", e.getMessage());
        }
    }

    /**
     * Get cached agent list.
     */
    public List<Agent> getCachedAgentList() {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String json = redisTemplate.opsForValue().get(AGENT_LIST_KEY);
            if (json == null || json.isEmpty()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<Agent>>() {});
        } catch (Exception e) {
            log.warn("Failed to get cached agent list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Cache individual agent config.
     */
    public void cacheAgent(Agent agent) {
        if (!isAvailable() || agent == null) return;
        try {
            String key = AGENT_KEY_PREFIX + agent.getId();
            String json = objectMapper.writeValueAsString(agent);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(AGENT_CONFIG_TTL_MINUTES));
        } catch (Exception e) {
            log.warn("Failed to cache agent {}: {}", agent.getId(), e.getMessage());
        }
    }

    /**
     * Get cached agent config.
     */
    public Agent getCachedAgent(Long agentId) {
        if (!isAvailable()) return null;
        try {
            String key = AGENT_KEY_PREFIX + agentId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, Agent.class);
        } catch (Exception e) {
            log.warn("Failed to get cached agent {}: {}", agentId, e.getMessage());
            return null;
        }
    }

    /**
     * Invalidate agent list cache (call when agents are added/updated/deleted).
     */
    public void invalidateAgentList() {
        if (!isAvailable()) return;
        try {
            redisTemplate.delete(AGENT_LIST_KEY);
        } catch (Exception e) {
            log.warn("Failed to invalidate agent list cache: {}", e.getMessage());
        }
    }

    /**
     * Invalidate individual agent cache.
     */
    public void invalidateAgent(Long agentId) {
        if (!isAvailable()) return;
        try {
            String key = AGENT_KEY_PREFIX + agentId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate agent cache {}: {}", agentId, e.getMessage());
        }
    }
}
