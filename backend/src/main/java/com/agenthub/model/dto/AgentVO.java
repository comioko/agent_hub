package com.agenthub.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String avatarUrl;
    private String systemPrompt;
    private String provider;
    private String providerModel;
    private Boolean enabled;
    private Boolean isOrchestrator;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // true = 系统 Agent, false = 用户创建的 Agent
    private Boolean isSystem;
    // 创建者用户名（仅用户 Agent 有值）
    private String ownerUsername;
}
