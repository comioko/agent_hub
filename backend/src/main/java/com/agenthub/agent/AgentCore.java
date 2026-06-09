package com.agenthub.agent;

import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.model.entity.Agent;
import com.agenthub.adapter.BuiltinAgentAdapter;
import com.agenthub.adapter.OpenAIAdapter;
import com.agenthub.adapter.ClaudeAdapter;
import com.agenthub.adapter.MiniMaxAdapter;
import com.agenthub.adapter.DeepSeekAdapter;
import com.agenthub.adapter.CliAgentAdapter;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentCore {

    private static final Logger log = LoggerFactory.getLogger(AgentCore.class);

    private final Map<String, AgentAdapter> adapters = new ConcurrentHashMap<>();
    private final Map<String, CliAgentAdapter> cliAdapters = new ConcurrentHashMap<>();

    public AgentCore(BuiltinAgentAdapter builtinAgentAdapter,
                     OpenAIAdapter openAIAdapter,
                     ClaudeAdapter claudeAdapter,
                     MiniMaxAdapter miniMaxAdapter,
                     DeepSeekAdapter deepSeekAdapter,
                     CliAgentAdapter cliAgentAdapter) {
        adapters.put("BUILTIN", builtinAgentAdapter);
        adapters.put("OPENAI", openAIAdapter);
        adapters.put("ANTHROPIC", claudeAdapter);
        adapters.put("MINIMAX", miniMaxAdapter);
        adapters.put("DEEPSEEK", deepSeekAdapter);

        if (cliAgentAdapter != null) {
            cliAdapters.put(cliAgentAdapter.getCliPath(), cliAgentAdapter);
            log.info("Default CLI Agent adapter registered: {}", cliAgentAdapter.getCliPath());
        }

        if (!cliAdapters.isEmpty()) {
            log.info("CLI Agent adapters available for paths: {}", cliAdapters.keySet());
        }
        log.info("AgentCore initialized with HTTP adapters: {}", adapters.keySet());
    }

    public AgentResponse generate(Agent agent, AgentRequest request) {
        if (agent == null) {
            log.error("Agent is null in AgentCore.generate");
            return createErrorResponse("Agent not found");
        }

        AgentAdapter adapter = resolveAdapter(agent);
        if (adapter == null) {
            return createErrorResponse("No agent adapter available");
        }

        try {
            log.debug("Calling adapter {} for agent {}", agent.getProvider(), agent.getName());
            return adapter.generate(request);
        } catch (Exception e) {
            log.error("Agent adapter failed", e);
            return createErrorResponse("Agent call failed: " + e.getMessage());
        }
    }

    public void registerAdapter(String providerCode, AgentAdapter adapter) {
        adapters.put(providerCode.toUpperCase(), adapter);
        log.info("Registered adapter for provider: {}", providerCode);
    }

    public AgentAdapter getAdapter(String providerCode) {
        return adapters.get(providerCode.toUpperCase());
    }

    public Flux<String> generateStream(Agent agent, AgentRequest request) {
        if (agent == null) {
            return Flux.just("Agent not found");
        }

        AgentAdapter adapter = resolveAdapter(agent);
        if (adapter == null) {
            return Flux.just("No agent adapter available");
        }

        try {
            log.debug("Calling stream adapter {} for agent {}", agent.getProvider(), agent.getName());
            return adapter.generateStream(request);
        } catch (Exception e) {
            log.error("Agent stream adapter failed", e);
            return Flux.just("Agent call failed: " + e.getMessage());
        }
    }

    private AgentAdapter resolveAdapter(Agent agent) {
        String provider = agent.getProvider().toUpperCase();

        if ("CLI".equals(provider)) {
            String cliPath = agent.getCliPath();
            if (cliPath == null || cliPath.isEmpty()) {
                cliPath = "claude";
            }

            CliAgentAdapter cliAdapter = cliAdapters.get(cliPath);
            if (cliAdapter == null) {
                cliAdapter = new CliAgentAdapter(cliPath);
                if (cliAdapter.isAvailable()) {
                    cliAdapters.put(cliPath, cliAdapter);
                    log.info("Created new CLI adapter for path: {}", cliPath);
                } else {
                    log.warn("CLI not available for path: {}", cliPath);
                    return null;
                }
            }

            if (!cliAdapter.isAvailable()) {
                log.warn("CLI adapter not available for path: {}", cliPath);
                return null;
            }

            return cliAdapter;
        }

        AgentAdapter adapter = adapters.get(provider);
        if (adapter == null || !adapter.isAvailable()) {
            log.warn("Adapter for {} not available, using BUILTIN", provider);
            adapter = adapters.get("BUILTIN");
        }
        return adapter;
    }

    private AgentResponse createErrorResponse(String message) {
        AgentResponse response = new AgentResponse();
        response.setContent(message);
        response.setFinishReason("error");
        return response;
    }
}
