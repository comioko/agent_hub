package com.agenthub.controller;

import com.agenthub.model.dto.*;
import com.agenthub.model.entity.User;
import com.agenthub.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 获取所有可用的 Agent（需要认证，返回系统 Agent + 用户自己的 Agent）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentVO>>> getAgents(
            @AuthenticationPrincipal User currentUser) {
        List<AgentVO> agents = agentService.getAllAvailableAgents(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    /**
     * 获取单个 Agent
     */
    @GetMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentVO>> getAgent(
            @PathVariable Long agentId,
            @AuthenticationPrincipal User currentUser) {
        AgentVO agent = agentService.getAgent(agentId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    /**
     * 获取用户自己的 Agent 列表
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<AgentVO>>> getMyAgents(
            @AuthenticationPrincipal User currentUser) {
        List<AgentVO> agents = agentService.getUserAgents(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    /**
     * 创建用户 Agent
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AgentVO>> createAgent(
            @Valid @RequestBody CreateAgentRequest request,
            @AuthenticationPrincipal User currentUser) {
        AgentVO agent = agentService.createAgent(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    /**
     * 更新用户 Agent
     */
    @PutMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentVO>> updateAgent(
            @PathVariable Long agentId,
            @Valid @RequestBody UpdateAgentRequest request,
            @AuthenticationPrincipal User currentUser) {
        AgentVO agent = agentService.updateAgent(agentId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    /**
     * 删除用户 Agent
     */
    @DeleteMapping("/{agentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAgent(
            @PathVariable Long agentId,
            @AuthenticationPrincipal User currentUser) {
        agentService.deleteAgent(agentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Agent deleted", null));
    }
}
