package com.agenthub.service;

import com.agenthub.config.JwtConfig;
import com.agenthub.exception.BusinessException;
import com.agenthub.model.dto.AuthResponse;
import com.agenthub.model.dto.LoginRequest;
import com.agenthub.model.dto.RegisterRequest;
import com.agenthub.model.entity.User;
import com.agenthub.repository.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtConfig jwtConfig) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtConfig = jwtConfig;
    }

    public AuthResponse register(RegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        userMapper.insert(user);

        String token = generateToken(user.getId());
        return buildAuthResponse(user, token);
    }

    public AuthResponse login(LoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = generateToken(user.getId());
        return buildAuthResponse(user, token);
    }

    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getExpiration());
        SecretKey key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact();
    }

    public Long validateTokenAndGetUserId(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        AuthResponse.UserVO userVO = new AuthResponse.UserVO(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getAvatarUrl()
        );
        return new AuthResponse(token, userVO);
    }
}
