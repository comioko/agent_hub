package com.agenthub.agent;

import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.model.entity.Agent;
import org.springframework.stereotype.Component;

@Component
public class SingleAgentHandler {

    private final AgentCore agentCore;

    public SingleAgentHandler(AgentCore agentCore) {
        this.agentCore = agentCore;
    }

    public AgentResponse handle(Agent agent, AgentRequest request) {
        if (agent == null || !agent.getEnabled()) {
            AgentResponse error = new AgentResponse();
            error.setContent("Agent is not available");
            return error;
        }

        return agentCore.generate(agent, request);
    }
}
