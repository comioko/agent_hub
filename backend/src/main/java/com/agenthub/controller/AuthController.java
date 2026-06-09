package com.agenthub.controller;

import com.agenthub.model.dto.ApiResponse;
import com.agenthub.model.dto.AuthResponse;
import com.agenthub.model.dto.LoginRequest;
import com.agenthub.model.dto.RegisterRequest;
import com.agenthub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse.UserVO>> getCurrentUser(
            @RequestAttribute("userId") Long userId) {
        var user = authService.getUserById(userId);
        AuthResponse.UserVO userVO = new AuthResponse.UserVO(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getAvatarUrl()
        );
        return ResponseEntity.ok(ApiResponse.success(userVO));
    }
}
