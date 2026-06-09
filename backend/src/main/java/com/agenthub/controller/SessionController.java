package com.agenthub.controller;

import com.agenthub.model.dto.ApiResponse;
import com.agenthub.model.dto.ConversationVO;
import com.agenthub.model.dto.CreateConversationRequest;
import com.agenthub.model.entity.User;
import com.agenthub.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationVO>> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal User currentUser) {
        ConversationVO conversation = sessionService.createConversation(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationVO>>> getUserConversations(
            @AuthenticationPrincipal User currentUser) {
        List<ConversationVO> conversations = sessionService.getUserConversations(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationVO>> getConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User currentUser) {
        ConversationVO conversation = sessionService.getConversation(conversationId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User currentUser) {
        sessionService.deleteConversation(conversationId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted", null));
    }

    @PutMapping("/{conversationId}/pin")
    public ResponseEntity<ApiResponse<Void>> pinConversation(
            @PathVariable Long conversationId,
            @RequestParam boolean pinned,
            @AuthenticationPrincipal User currentUser) {
        sessionService.pinConversation(conversationId, currentUser.getId(), pinned);
        return ResponseEntity.ok(ApiResponse.success("Conversation pinned", null));
    }

    @PutMapping("/{conversationId}/archive")
    public ResponseEntity<ApiResponse<Void>> archiveConversation(
            @PathVariable Long conversationId,
            @RequestParam boolean archived,
            @AuthenticationPrincipal User currentUser) {
        sessionService.archiveConversation(conversationId, currentUser.getId(), archived);
        return ResponseEntity.ok(ApiResponse.success("Conversation archived", null));
    }

    @PutMapping("/{conversationId}/title")
    public ResponseEntity<ApiResponse<Void>> updateConversationTitle(
            @PathVariable Long conversationId,
            @RequestParam String title,
            @AuthenticationPrincipal User currentUser) {
        sessionService.updateConversationTitle(conversationId, currentUser.getId(), title);
        return ResponseEntity.ok(ApiResponse.success("Conversation title updated", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ConversationVO>>> searchConversations(
            @RequestParam String keyword,
            @AuthenticationPrincipal User currentUser) {
        List<ConversationVO> conversations = sessionService.searchConversations(keyword, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }
}
