package com.agenthub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UserVO user;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserVO {
        private Long id;
        private String username;
        private String nickname;
        private String avatarUrl;
    }
}
