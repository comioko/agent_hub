package com.agenthub.controller;

import com.agenthub.agent.AgentLoop;
import com.agenthub.exception.BusinessException;
import com.agenthub.model.dto.ApiResponse;
import com.agenthub.model.entity.Agent;
import com.agenthub.model.entity.AgentSession;
import com.agenthub.model.entity.User;
import com.agenthub.repository.AgentMapper;
import com.agenthub.service.AgentSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for Agent task management.
 * Handles creating, monitoring, and cancelling agent tasks.
 */
@RestController
@RequestMapping("/api/tasks")
public class AgentTaskController {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskController.class);

    private final AgentSessionService sessionService;
    private final AgentMapper agentMapper;
    private final AgentLoop agentLoop;

    public AgentTaskController(AgentSessionService sessionService,
                               AgentMapper agentMapper,
                               AgentLoop agentLoop) {
        this.sessionService = sessionService;
        this.agentMapper = agentMapper;
        this.agentLoop = agentLoop;
    }

    /**
     * Create a new agent task.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskVO>> createTask(
            @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        log.info("Creating task for user: {}, agent: {}, task: {}",
                currentUser.getId(), request.getAgentId(), request.getTask());

        // Validate agent exists and is enabled
        Agent agent = agentMapper.selectById(request.getAgentId());
        if (agent == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Agent not found");
        }
        if (!agent.getEnabled()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Agent is not enabled");
        }

        // Create session
        AgentSession session = sessionService.createSession(
                currentUser.getId(),
                request.getTask(),
                request.getConversationId()
        );

        // Start execution asynchronously
        agentLoop.startExecution(session.getSessionId(), agent, request.getTask());

        // Build response
        TaskVO taskVO = new TaskVO();
        taskVO.setSessionId(session.getSessionId());
        taskVO.setTask(session.getTask());
        taskVO.setStatus(session.getStatus());
        taskVO.setCreatedAt(session.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success(taskVO));
    }

    /**
     * Get task status.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<TaskVO>> getTask(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User currentUser) {

        AgentSession session = sessionService.getSession(sessionId, currentUser.getId());

        TaskVO taskVO = new TaskVO();
        taskVO.setSessionId(session.getSessionId());
        taskVO.setTask(session.getTask());
        taskVO.setStatus(session.getStatus());
        taskVO.setCreatedAt(session.getCreatedAt());
        taskVO.setUpdatedAt(session.getUpdatedAt());
        taskVO.setIterationCount(session.getIterationCount());
        taskVO.setErrorMessage(session.getErrorMessage());

        // Include messages summary
        if (session.getMessages() != null) {
            taskVO.setMessageCount(session.getMessages().size());
        }
        if (session.getToolResults() != null) {
            taskVO.setToolCallCount(session.getToolResults().size());
        }

        return ResponseEntity.ok(ApiResponse.success(taskVO));
    }

    /**
     * Get all tasks for current user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskVO>>> getTasks(
            @AuthenticationPrincipal User currentUser) {

        List<AgentSession> sessions = sessionService.getUserSessions(currentUser.getId());

        List<TaskVO> tasks = sessions.stream().map(session -> {
            TaskVO vo = new TaskVO();
            vo.setSessionId(session.getSessionId());
            vo.setTask(session.getTask());
            vo.setStatus(session.getStatus());
            vo.setCreatedAt(session.getCreatedAt());
            vo.setUpdatedAt(session.getUpdatedAt());
            vo.setIterationCount(session.getIterationCount());
            return vo;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    /**
     * Cancel a task.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> cancelTask(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User currentUser) {

        log.info("Cancelling task: {} for user: {}", sessionId, currentUser.getId());

        // Request interruption
        boolean interrupted = agentLoop.interrupt(sessionId);

        // Update status
        sessionService.cancelSession(sessionId, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Get messages for a task.
     */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<TaskMessagesVO>> getTaskMessages(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User currentUser) {

        AgentSession session = sessionService.getSession(sessionId, currentUser.getId());

        TaskMessagesVO vo = new TaskMessagesVO();
        vo.setSessionId(session.getSessionId());
        vo.setStatus(session.getStatus());

        if (session.getMessages() != null) {
            vo.setMessages(session.getMessages().stream().map(msg -> {
                MessageVO messageVO = new MessageVO();
                messageVO.setRole(msg.getRole());
                messageVO.setContent(msg.getContent());
                messageVO.setToolCallId(msg.getToolCallId());
                return messageVO;
            }).collect(Collectors.toList()));
        }

        if (session.getToolResults() != null) {
            vo.setToolResults(session.getToolResults().stream().map(tr -> {
                ToolResultVO trVO = new ToolResultVO();
                trVO.setToolCallId(tr.getToolCallId());
                trVO.setToolName(tr.getToolName());
                trVO.setResult(tr.getResult());
                trVO.setExecutedAt(tr.getExecutedAt());
                return trVO;
            }).collect(Collectors.toList()));
        }

        return ResponseEntity.ok(ApiResponse.success(vo));
    }

    /**
     * Subscribe to task events via SSE.
     */
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTask(@PathVariable String sessionId,
                                 @AuthenticationPrincipal User currentUser) {
        // Verify user has access to this session
        AgentSession session = sessionService.getSession(sessionId, currentUser.getId());
        if (session == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Session not found");
        }

        return agentLoop.subscribe(sessionId);
    }

    // ==================== Request/Response DTOs ====================

    public static class CreateTaskRequest {
        private Long agentId;
        private String task;
        private Long conversationId;

        public Long getAgentId() { return agentId; }
        public void setAgentId(Long agentId) { this.agentId = agentId; }
        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    }

    public static class TaskVO {
        private String sessionId;
        private String task;
        private String status;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private Integer iterationCount;
        private String errorMessage;
        private Integer messageCount;
        private Integer toolCallCount;

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public Integer getIterationCount() { return iterationCount; }
        public void setIterationCount(Integer iterationCount) { this.iterationCount = iterationCount; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Integer getMessageCount() { return messageCount; }
        public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
        public Integer getToolCallCount() { return toolCallCount; }
        public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }
    }

    public static class TaskMessagesVO {
        private String sessionId;
        private String status;
        private List<MessageVO> messages;
        private List<ToolResultVO> toolResults;

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<MessageVO> getMessages() { return messages; }
        public void setMessages(List<MessageVO> messages) { this.messages = messages; }
        public List<ToolResultVO> getToolResults() { return toolResults; }
        public void setToolResults(List<ToolResultVO> toolResults) { this.toolResults = toolResults; }
    }

    public static class MessageVO {
        private String role;
        private String content;
        private String toolCallId;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    }

    public static class ToolResultVO {
        private String toolCallId;
        private String toolName;
        private String result;
        private java.time.LocalDateTime executedAt;

        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public java.time.LocalDateTime getExecutedAt() { return executedAt; }
        public void setExecutedAt(java.time.LocalDateTime executedAt) { this.executedAt = executedAt; }
    }
}
