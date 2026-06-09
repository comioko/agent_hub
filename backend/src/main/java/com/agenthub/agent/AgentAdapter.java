package com.agenthub.agent;

import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.ProviderMeta;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AgentAdapter {
    AgentResponse generate(AgentRequest request);
    Flux<String> generateStream(AgentRequest request);
    ProviderMeta getProviderMeta();
    boolean isAvailable();

    /**
     * Generate response with tool calls support.
     */
    default AgentResponse generateWithTools(AgentRequest request) {
        return generate(request);
    }

    /**
     * Stream response with tool calls support.
     */
    default Flux<AgentResponse> generateStreamWithTools(AgentRequest request) {
        return Flux.empty();
    }
}
