package com.agenthub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {
    private Long id;
    private Long conversationId;
    private String senderType;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private String messageType;
    private Long parentId;
    private Boolean pinned = false;
    private String status;
    private String contextType;  // AUTO, PINNED, EXCLUDED
    private Integer contextPriority;  // Higher = more important for long-term context
    private LocalDateTime createdAt;
    private List<MessageBlockVO> blocks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageBlockVO {
        private Long id;
        private String blockType;
        private String content;
        private String language;
        private String metadata;
    }
}
