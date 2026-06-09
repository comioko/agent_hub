package com.agenthub.service;

import com.agenthub.exception.BusinessException;
import com.agenthub.model.dto.AgentVO;
import com.agenthub.model.dto.CreateAgentRequest;
import com.agenthub.model.dto.UpdateAgentRequest;
import com.agenthub.model.entity.Agent;
import com.agenthub.model.entity.User;
import com.agenthub.repository.AgentMapper;
import com.agenthub.repository.UserMapper;
import com.agenthub.service.cache.AgentCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private final AgentMapper agentMapper;
    private final UserMapper userMapper;
    private final AgentCacheService agentCacheService;

    public AgentService(AgentMapper agentMapper,
                       UserMapper userMapper,
                       @Autowired(required = false) AgentCacheService agentCacheService) {
        this.agentMapper = agentMapper;
        this.userMapper = userMapper;
        this.agentCacheService = agentCacheService;
    }

    /**
     * 获取所有可用的 Agent（包括系统 Agent 和用户 Agent）
     * 需要认证
     */
    public List<AgentVO> getAllAvailableAgents(Long userId) {
        // Try cache first
        if (agentCacheService != null) {
            List<Agent> cached = agentCacheService.getCachedAgentList();
            if (!cached.isEmpty()) {
                return cached.stream()
                    .map(a -> toAgentVO(a, userId))
                    .collect(Collectors.toList());
            }
        }

        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getEnabled, true);
        wrapper.orderByAsc(Agent::getCreatedAt);
        List<Agent> agents = agentMapper.selectList(wrapper);

        // Cache the result
        if (agentCacheService != null && !agents.isEmpty()) {
            agentCacheService.cacheAgentList(agents);
        }

        return agents.stream()
            .map(a -> toAgentVO(a, userId))
            .collect(Collectors.toList());
    }

    /**
     * 获取系统 Agent（不需要认证）
     */
    public List<AgentVO> getSystemAgents() {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Agent::getOwnerId);
        wrapper.eq(Agent::getEnabled, true);
        wrapper.orderByAsc(Agent::getCreatedAt);
        List<Agent> agents = agentMapper.selectList(wrapper);
        return agents.stream()
            .map(a -> toAgentVO(a, null))
            .collect(Collectors.toList());
    }

    /**
     * 获取用户自己的 Agent
     */
    public List<AgentVO> getUserAgents(Long userId) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getOwnerId, userId);
        wrapper.orderByDesc(Agent::getCreatedAt);
        List<Agent> agents = agentMapper.selectList(wrapper);
        return agents.stream()
            .map(a -> toAgentVO(a, userId))
            .collect(Collectors.toList());
    }

    /**
     * 获取单个 Agent
     */
    public AgentVO getAgent(Long agentId, Long userId) {
        Agent agent = null;
        if (agentCacheService != null) {
            agent = agentCacheService.getCachedAgent(agentId);
        }
        if (agent == null) {
            agent = agentMapper.selectById(agentId);
            if (agent != null && agentCacheService != null) {
                agentCacheService.cacheAgent(agent);
            }
        }
        if (agent == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Agent not found");
        }
        if (!agent.getEnabled() && (agent.getOwnerId() == null || !agent.getOwnerId().equals(userId))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Agent not available");
        }
        return toAgentVO(agent, userId);
    }

    /**
     * 创建用户 Agent
     */
    @Transactional
    public AgentVO createAgent(CreateAgentRequest request, User currentUser) {
        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setAvatarUrl(request.getAvatarUrl());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setProvider(request.getProvider());
        agent.setProviderModel(request.getProviderModel());
        agent.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        agent.setIsOrchestrator(request.getIsOrchestrator() != null ? request.getIsOrchestrator() : false);
        agent.setConfigJson(request.getConfigJson());
        // 用户创建的 Agent 设置 owner_id
        agent.setOwnerId(currentUser.getId());
        // 生成唯一 code
        agent.setCode("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        agentMapper.insert(agent);
        // Invalidate cache
        if (agentCacheService != null) {
            agentCacheService.invalidateAgentList();
            agentCacheService.invalidateAgent(agent.getId());
        }
        return toAgentVO(agent, currentUser.getId());
    }

    /**
     * 更新用户 Agent
     */
    @Transactional
    public AgentVO updateAgent(Long agentId, UpdateAgentRequest request, User currentUser) {
        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Agent not found");
        }
        // 只能修改自己的 Agent
        if (agent.getOwnerId() == null || !agent.getOwnerId().equals(currentUser.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You can only edit your own agents");
        }

        if (request.getName() != null) {
            agent.setName(request.getName());
        }
        if (request.getDescription() != null) {
            agent.setDescription(request.getDescription());
        }
        if (request.getAvatarUrl() != null) {
            agent.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getSystemPrompt() != null) {
            agent.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getProvider() != null) {
            agent.setProvider(request.getProvider());
        }
        if (request.getProviderModel() != null) {
            agent.setProviderModel(request.getProviderModel());
        }
        if (request.getEnabled() != null) {
            agent.setEnabled(request.getEnabled());
        }
        if (request.getIsOrchestrator() != null) {
            agent.setIsOrchestrator(request.getIsOrchestrator());
        }
        if (request.getConfigJson() != null) {
            agent.setConfigJson(request.getConfigJson());
        }

        agentMapper.updateById(agent);
        // Invalidate cache
        if (agentCacheService != null) {
            agentCacheService.invalidateAgentList();
            agentCacheService.invalidateAgent(agentId);
        }
        return toAgentVO(agent, currentUser.getId());
    }

    /**
     * 删除用户 Agent
     */
    @Transactional
    public void deleteAgent(Long agentId, User currentUser) {
        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Agent not found");
        }
        // 只能删除自己的 Agent
        if (agent.getOwnerId() == null || !agent.getOwnerId().equals(currentUser.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You can only delete your own agents");
        }
        agentMapper.deleteById(agentId);
        // Invalidate cache
        if (agentCacheService != null) {
            agentCacheService.invalidateAgentList();
            agentCacheService.invalidateAgent(agentId);
        }
    }

    /**
     * 将 Agent 实体转换为 AgentVO
     */
    private AgentVO toAgentVO(Agent agent, Long currentUserId) {
        AgentVO vo = new AgentVO();
        vo.setId(agent.getId());
        vo.setCode(agent.getCode());
        vo.setName(agent.getName());
        vo.setDescription(agent.getDescription());
        vo.setAvatarUrl(agent.getAvatarUrl());
        vo.setSystemPrompt(agent.getSystemPrompt());
        vo.setProvider(agent.getProvider());
        vo.setProviderModel(agent.getProviderModel());
        vo.setEnabled(agent.getEnabled());
        vo.setIsOrchestrator(agent.getIsOrchestrator());
        vo.setConfigJson(agent.getConfigJson());
        vo.setCreatedAt(agent.getCreatedAt());
        vo.setUpdatedAt(agent.getUpdatedAt());

        // 判断是否为系统 Agent
        vo.setIsSystem(agent.getOwnerId() == null);

        // 如果是用户 Agent，获取创建者用户名
        if (agent.getOwnerId() != null) {
            User owner = userMapper.selectById(agent.getOwnerId());
            if (owner != null) {
                vo.setOwnerUsername(owner.getUsername());
            }
        }

        return vo;
    }
}
