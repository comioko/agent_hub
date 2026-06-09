package com.agenthub.controller;

import com.agenthub.model.dto.ApiResponse;
import com.agenthub.model.dto.CodeExecutionResult;
import com.agenthub.model.entity.MessageBlock;
import com.agenthub.model.entity.User;
import com.agenthub.service.CodeBlockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.agenthub.repository.MessageBlockMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/code-blocks")
public class CodeBlockController {

    private final CodeBlockService codeBlockService;
    private final MessageBlockMapper messageBlockMapper;

    public CodeBlockController(CodeBlockService codeBlockService, MessageBlockMapper messageBlockMapper) {
        this.codeBlockService = codeBlockService;
        this.messageBlockMapper = messageBlockMapper;
    }

    @PutMapping("/{blockId}")
    public ResponseEntity<ApiResponse<MessageBlock>> updateCodeBlock(
            @PathVariable Long blockId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User currentUser) {
        MessageBlock updated = codeBlockService.updateCodeBlock(blockId, request.get("content"));
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{blockId}/execute")
    public ResponseEntity<ApiResponse<CodeExecutionResult>> executeCode(
            @PathVariable Long blockId,
            @AuthenticationPrincipal User currentUser) {
        MessageBlock block = messageBlockMapper.selectById(blockId);
        if (block == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Block not found"));
        }

        CodeExecutionResult result = codeBlockService.executeCode(block);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
