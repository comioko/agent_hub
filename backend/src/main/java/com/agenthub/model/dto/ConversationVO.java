package com.agenthub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {
    private Long id;
    private String title;
    private String type;
    private Long ownerId;
    private Boolean pinned = false;
    private Boolean archived = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ParticipantVO> participants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantVO {
        private Long id;
        private Long userId;
        private Long agentId;
        private String name;
        private String avatarUrl;
        private String role;
        private String type;
    }
}
