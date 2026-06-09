package com.agenthub.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAgentRequest {
    @NotBlank(message = "Agent name is required")
    @Size(max = 100, message = "Agent name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    private String avatarUrl;

    private String systemPrompt;

    @NotBlank(message = "Provider is required")
    private String provider;

    private String providerModel;

    private Boolean enabled = true;

    private Boolean isOrchestrator = false;

    private String configJson;
}
