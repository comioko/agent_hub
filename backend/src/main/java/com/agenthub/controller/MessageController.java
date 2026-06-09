package com.agenthub.controller;

import com.agenthub.model.dto.ApiResponse;
import com.agenthub.model.dto.MessageVO;
import com.agenthub.model.dto.SendMessageRequest;
import com.agenthub.model.entity.MessageBlock;
import com.agenthub.model.entity.MessageVersion;
import com.agenthub.model.entity.User;
import com.agenthub.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageVO>> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal User currentUser) {
        MessageVO message = messageService.sendMessage(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<ApiResponse<List<MessageVO>>> getConversationMessages(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User currentUser) {
        List<MessageVO> messages = messageService.getConversationMessages(conversationId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal User currentUser) {
        return messageService.subscribe(currentUser.getId());
    }

    @PutMapping("/{messageId}/pin")
    public ResponseEntity<ApiResponse<Void>> pinMessage(
            @PathVariable Long messageId,
            @RequestParam boolean pinned,
            @AuthenticationPrincipal User currentUser) {
        messageService.pinMessage(messageId, currentUser.getId(), pinned);
        return ResponseEntity.ok(ApiResponse.success("Message pinned", null));
    }

    @GetMapping("/conversation/{conversationId}/pinned")
    public ResponseEntity<ApiResponse<List<MessageVO>>> getPinnedMessages(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User currentUser) {
        List<MessageVO> messages = messageService.getPinnedMessages(conversationId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/{messageId}/blocks")
    public ResponseEntity<ApiResponse<List<MessageBlock>>> getMessageBlocks(
            @PathVariable Long messageId,
            @AuthenticationPrincipal User currentUser) {
        List<MessageBlock> blocks = messageService.getMessageBlocks(messageId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(blocks));
    }

    @GetMapping("/{messageId}/versions")
    public ResponseEntity<ApiResponse<List<MessageVersion>>> getMessageVersions(
            @PathVariable Long messageId,
            @AuthenticationPrincipal User currentUser) {
        List<MessageVersion> versions = messageService.getMessageVersions(messageId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @PostMapping("/{messageId}/versions")
    public ResponseEntity<ApiResponse<MessageVersion>> saveVersion(
            @PathVariable Long messageId,
            @RequestBody SaveVersionRequest request,
            @AuthenticationPrincipal User currentUser) {
        MessageVersion version = messageService.saveVersion(messageId, currentUser.getId(), request.getContent());
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    public static class SaveVersionRequest {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
