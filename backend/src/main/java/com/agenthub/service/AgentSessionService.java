package com.agenthub.service;

import com.agenthub.exception.BusinessException;
import com.agenthub.model.entity.AgentSession;
import com.agenthub.repository.AgentSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);

    private final AgentSessionMapper agentSessionMapper;

    // In-memory tracking for active sessions (for interruption support)
    private final ConcurrentHashMap<String, Boolean> activeSessions = new ConcurrentHashMap<>();

    public AgentSessionService(AgentSessionMapper agentSessionMapper) {
        this.agentSessionMapper = agentSessionMapper;
    }

    public AgentSession createSession(Long userId, String task, Long conversationId) {
        AgentSession session = new AgentSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTask(task);
        session.setConversationId(conversationId);
        session.setStatus("RUNNING");
        session.setIterationCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        agentSessionMapper.insert(session);
        activeSessions.put(session.getSessionId(), true);

        log.info("Created agent session: {} for user: {}", session.getSessionId(), userId);
        return session;
    }

    public AgentSession getSession(String sessionId, Long userId) {
        AgentSession session = agentSessionMapper.findBySessionIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return session;
    }

    public List<AgentSession> getUserSessions(Long userId) {
        return agentSessionMapper.findByUserId(userId);
    }

    public void updateSession(AgentSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        agentSessionMapper.updateById(session);
    }

    public void addMessage(String sessionId, AgentSession.AgentMessage message) {
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session != null) {
            session.getMessages().add(message);
            session.setUpdatedAt(LocalDateTime.now());
            agentSessionMapper.updateById(session);
        }
    }

    public void addToolResult(String sessionId, AgentSession.ToolResult toolResult) {
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session != null) {
            session.getToolResults().add(toolResult);
            session.setUpdatedAt(LocalDateTime.now());
            agentSessionMapper.updateById(session);
        }
    }

    public void updateStatus(String sessionId, String status) {
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setStatus(status);
            session.setUpdatedAt(LocalDateTime.now());
            agentSessionMapper.updateById(session);

            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                activeSessions.remove(sessionId);
            }
        }
    }

    public void incrementIteration(String sessionId) {
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setIterationCount(session.getIterationCount() + 1);
            session.setUpdatedAt(LocalDateTime.now());
            agentSessionMapper.updateById(session);
        }
    }

    public void setError(String sessionId, String errorMessage) {
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setStatus("FAILED");
            session.setErrorMessage(errorMessage);
            session.setUpdatedAt(LocalDateTime.now());
            agentSessionMapper.updateById(session);
            activeSessions.remove(sessionId);
        }
    }

    public void cancelSession(String sessionId, Long userId) {
        AgentSession session = getSession(sessionId, userId);
        if (session != null) {
            session.setStatus("CANCELLED");
            session.setUpdatedAt(LocalDateTime.now());
            agentSessionMapper.updateById(session);
            activeSessions.remove(sessionId);
            log.info("Cancelled agent session: {}", sessionId);
        }
    }

    public boolean isActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    public boolean requestInterruption(String sessionId) {
        if (activeSessions.containsKey(sessionId)) {
            activeSessions.put(sessionId, false);
            log.info("Interruption requested for session: {}", sessionId);
            return true;
        }
        return false;
    }

    public boolean shouldContinue(String sessionId) {
        Boolean active = activeSessions.get(sessionId);
        return active != null && active;
    }
}
