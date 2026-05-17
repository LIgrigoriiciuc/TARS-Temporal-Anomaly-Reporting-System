package com.tars.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenDenylistService {
    private final StringRedisTemplate redisTemplate;

    // stores the JWT string as a Redis key with TTL equal to the remaining JWT lifetime
    // When JWT expires naturally, Redis auto-deletes the key, no cleanup needed
    // to be used for logout
    public void blacklistToken(String token, long expirationMs) {
        redisTemplate.opsForValue().set(token, "blacklisted", Duration.ofMillis(expirationMs));
    }

    // O(1) check if token is blacklisted. Redis hash table lookup is very fast, even with millions of keys.
    public boolean isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(token);
    }

    // Blacklist the entire user (to be used for deactivation)
    // A user could theoretically have multiple active tokens (logged in from multiple devices).
    // TTL = 8 hours; after 8 hours, any token they had would have expired naturally anyway
    public void blacklistUser(Long userId) {
        redisTemplate.opsForValue().set("user_blocked:" + userId, "blocked", Duration.ofHours(8)); // max JWT lifetime
    }

    public boolean isUserBlacklisted(Long userId) {
        return redisTemplate.hasKey("user_blocked:" + userId);
    }
}
