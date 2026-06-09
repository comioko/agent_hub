package com.agenthub.agent.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentTask {
    private Long agentId;
    private String taskDescription;
    private Map<String, Object> context = new HashMap<>();
    private int priority = 0;
}
