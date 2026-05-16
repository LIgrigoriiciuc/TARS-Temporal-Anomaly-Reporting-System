package com.tars.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenDenylistService {

    private final StringRedisTemplate redisTemplate;

    // Adds token to Redis with TTL matching remaining JWT lifetime
    public void blacklistToken(String token, long expirationMs) {
        redisTemplate.opsForValue().set(token, "blacklisted", Duration.ofMillis(expirationMs));
    }

    // O(1) check — NFR-13
    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(token));
    }

    // Blacklist entire user (for deactivation)
    public void blacklistUser(Long userId) {
        redisTemplate.opsForValue().set("user_blocked:" + userId, "blocked",
                Duration.ofHours(8)); // max JWT lifetime
    }

    public boolean isUserBlacklisted(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("user_blocked:" + userId));
    }
}
