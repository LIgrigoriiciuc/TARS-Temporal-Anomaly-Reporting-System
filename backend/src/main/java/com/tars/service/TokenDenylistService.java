package com.tars.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenDenylistService {
    //Spring's client for Redis, specialized for String keys and values
    private final StringRedisTemplate redisTemplate;

    // stores the JWT string as a Redis key with TTL equal to remaining JWT lifetime. When JWT expires naturally,
    // Redis auto-deletes the key — no cleanup needed.
    public void blacklistToken(String token, long expirationMs) {
        redisTemplate.opsForValue().set(token, "blacklisted", Duration.ofMillis(expirationMs));
    }

    // O(1) check — NFR-13
    //Redis uses a hash table internally
    public boolean isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(token);
    }

    // Blacklist entire user (for deactivation)
    //blacklistUser(userId) — stores "user_blocked:2" as key for 8 hours. Different prefix from tokens to avoid collisions.
    //A user could theoretically have multiple active tokens (logged in from multiple devices).
    //User blacklist — TTL = 8 hours. After 8 hours, any token they had would have expired naturally anyway, so the block is no longer needed.
    public void blacklistUser(Long userId) {
        redisTemplate.opsForValue().set("user_blocked:" + userId, "blocked",
                Duration.ofHours(8)); // max JWT lifetime
    }

    public boolean isUserBlacklisted(Long userId) {
        return redisTemplate.hasKey("user_blocked:" + userId);
    }
}

//Check 1 — login attempt:
//Spring Security calls isAccountNonLocked() which checks status == UserStatus.ACTIVE in DB. If INACTIVE → LockedException → 403. Redis not involved at all.
//Check 2 — already logged in, making requests:
//Filter gets the JWT from cookie → checks Redis isTokenBlacklisted(jwt) → if blacklisted returns 401. Then checks isUserBlacklisted(userId) → if user blocked returns 401.
