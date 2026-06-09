package com.agenthub.agent;

import com.agenthub.agent.model.AgentRequest;
import com.agenthub.agent.model.AgentResponse;
import com.agenthub.agent.model.AgentTask;
import com.agenthub.model.entity.Agent;
import com.agenthub.model.entity.Message;

import java.util.List;

public interface Orchestrator {
    /**
     * 判断是否需要 Orchestrator 介入
     */
    boolean shouldOrchestrate(Message message);

    /**
     * 拆解用户消息，返回待执行任务列表
     */
    List<AgentTask> decompose(String userMessage, List<Agent> participants);

    /**
     * 汇总子 Agent 结果
     */
    String aggregate(List<AgentResponse> responses);
}
