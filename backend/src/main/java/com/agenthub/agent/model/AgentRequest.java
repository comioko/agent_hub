package com.agenthub.agent.model;

import com.agenthub.model.entity.Message;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentRequest {
    private Long userId;
    private Long conversationId;
    private String content;
    private String systemPrompt;
    private List<Message> history;
    private List<ToolDefinition> tools = new ArrayList<>();
    private List<ToolCall> toolCalls = new ArrayList<>();
    private List<ToolExecutionResult> toolResults = new ArrayList<>();
}
