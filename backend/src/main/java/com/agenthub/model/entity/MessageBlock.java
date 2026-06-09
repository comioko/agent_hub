package com.agenthub.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message_block")
public class MessageBlock {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;
    private String blockType;
    private String content;
    private String language;
    private String metadata;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
