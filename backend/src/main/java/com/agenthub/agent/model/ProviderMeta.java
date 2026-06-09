package com.agenthub.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderMeta {
    private String providerCode;
    private String providerName;
    private String modelName;
    private boolean available;
}
