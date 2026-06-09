package com.agenthub.agent.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {
    private String content;
    private String finishReason;
    private List<ArtifactBlock> blocks = new ArrayList<>();
    private List<ToolCall> toolCalls = new ArrayList<>();
    private boolean hasToolCalls = false;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ArtifactBlock {
        private String type;
        private String content;
        private String language;
        private String metadata;
    }

    public void addCodeBlock(String content, String language) {
        ArtifactBlock block = new ArtifactBlock();
        block.setType("CODE");
        block.setContent(content);
        block.setLanguage(language);
        this.blocks.add(block);
    }
}
