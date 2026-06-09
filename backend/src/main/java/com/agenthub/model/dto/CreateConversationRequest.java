package com.agenthub.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateConversationRequest {
    private String title;

    // For single chat
    private Long agentId;

    // For group chat (multiple agents)
    private List<Long> agentIds;

    private Long userId;

    // Helper to determine if it's a group chat
    public boolean isGroupChat() {
        return agentIds != null && !agentIds.isEmpty();
    }
}
