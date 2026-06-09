package com.agenthub.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAgentRequest {
    @Size(max = 100, message = "Agent name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    private String avatarUrl;

    private String systemPrompt;

    private String provider;

    private String providerModel;

    private Boolean enabled;

    private Boolean isOrchestrator;

    private String configJson;
}
