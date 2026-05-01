package com.tars.service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenDenylistService {
    private final StringRedisTemplate redisTemplate;

    public void blacklistToken(String token, long expirationMs) {
        // the Redis key will be the token, the "blacklisted" value
        redisTemplate.opsForValue().set(token, "blacklisted", Duration.ofMillis(expirationMs));
    }

    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(token));
    }
}
