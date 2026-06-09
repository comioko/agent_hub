package com.agenthub.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation_participant")
public class ConversationParticipant {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long userId;
    private Long agentId;
    private String role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;
}
