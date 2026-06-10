package com.agenthub.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private String senderType;
    private Long senderId;
    private String content;
    private String messageType;
    private Long parentId;
    private Boolean pinned = false;
    private String status;  // PENDING, STREAMING, COMPLETED, FAILED
    private String contextType = "AUTO";  // AUTO, PINNED, EXCLUDED
    private Integer contextPriority = 0;  // Higher = more important for long-term context

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
