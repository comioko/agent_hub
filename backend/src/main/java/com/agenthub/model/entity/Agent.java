package com.agenthub.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent")
public class Agent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
    private String avatarUrl;
    private String systemPrompt;
    private String provider;
    private String providerModel;
    private String cliPath;
    private String cliArgs;
    private Boolean enabled;
    private Boolean isOrchestrator;
    private String configJson;

    // owner_id 为 null 表示系统 Agent，有值表示用户创建的 Agent
    private Long ownerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
