package com.agenthub.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("agent_session")
public class AgentSession {
    @TableId(type = IdType.ASSIGN_UUID)
    private String sessionId;

    private Long userId;
    private String task;
    private Long conversationId;

    private String status; // RUNNING, WAITING_TOOL, COMPLETED, FAILED, CANCELLED

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<AgentMessage> messages = new ArrayList<>();

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<ToolResult> toolResults = new ArrayList<>();

    private Integer iterationCount;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @Data
    public static class AgentMessage {
        private String role; // "user", "assistant", "system"
        private String content;
        private List<ToolCall> toolCalls;
        private String toolCallId;

        public AgentMessage() {}

        public AgentMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    public static class ToolCall {
        private String id;
        private String name;
        private String arguments;
        private String result;
        private boolean completed;
        private String error;

        public ToolCall() {}

        public ToolCall(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }

    @Data
    public static class ToolResult {
        private String toolCallId;
        private String toolName;
        private String result;
        private LocalDateTime executedAt;

        public ToolResult() {}

        public ToolResult(String toolCallId, String toolName, String result) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.result = result;
            this.executedAt = LocalDateTime.now();
        }
    }
}
