package com.agenthub.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_provider")
public class AgentProvider {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String apiBase;
    private String apiKeyEncrypted;
    private Boolean enabled;
    private String configJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
