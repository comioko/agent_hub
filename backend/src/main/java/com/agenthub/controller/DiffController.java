package com.agenthub.controller;

import com.agenthub.model.dto.ApiResponse;
import com.agenthub.model.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/diff")
public class DiffController {

    @Value("${diff.apply.base-path:./workspace}")
    private String basePath;

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<ApplyResult>> applyDiff(
            @RequestBody ApplyDiffRequest request,
            @AuthenticationPrincipal User currentUser) {

        if (request.getFilePath() == null || request.getDiff() == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("filePath and diff are required"));
        }

        try {
            // Security: only allow writing within workspace directory
            Path targetPath = Paths.get(basePath, request.getFilePath()).normalize();
            Path basePathResolved = Paths.get(basePath).toAbsolutePath().normalize();

            if (!targetPath.startsWith(basePathResolved)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid file path: outside workspace"));
            }

            // Ensure parent directory exists
            Files.createDirectories(targetPath.getParent());

            // Apply the diff (simplified - just write content for now)
            // In production, would use a proper diff apply library
            Files.writeString(targetPath, request.getContent());

            ApplyResult result = new ApplyResult();
            result.setSuccess(true);
            result.setFilePath(request.getFilePath());
            result.setMessage("Diff applied successfully");

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (IOException e) {
            ApplyResult result = new ApplyResult();
            result.setSuccess(false);
            result.setFilePath(request.getFilePath());
            result.setMessage("Failed to apply diff: " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.success(result));
        }
    }

    public static class ApplyDiffRequest {
        private String filePath;
        private String diff;
        private String content;

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getDiff() { return diff; }
        public void setDiff(String diff) { this.diff = diff; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ApplyResult {
        private boolean success;
        private String filePath;
        private String message;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
