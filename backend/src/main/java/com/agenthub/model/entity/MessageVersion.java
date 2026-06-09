package com.agenthub.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message_version")
public class MessageVersion {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;
    private String content;
    private Integer versionNumber;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
