package com.tars.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenDenylistServiceTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    // mock the ValueOperations interface because .opsForValue() returns it
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenDenylistService tokenDenylistService;

    @Test
    void blacklistToken_Success() {
        String token = "mock.jwt.token";
        long expirationMs = 5000L;
        // when redisTemplate.opsForValue() is called, return the mocked operations object
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenDenylistService.blacklistToken(token, expirationMs);
        verify(valueOperations, times(1)).set(token, "blacklisted", Duration.ofMillis(expirationMs));
    }

    @Test
    void isTokenBlacklisted_ReturnsTrue() {
        String token = "blacklisted.jwt.token";
        when(redisTemplate.hasKey(token)).thenReturn(true);
        boolean result = tokenDenylistService.isTokenBlacklisted(token);
        assertTrue(result);
        verify(redisTemplate, times(1)).hasKey(token);
    }

    @Test
    void isTokenBlacklisted_ReturnsFalse() {
        String token = "clean.jwt.token";
        when(redisTemplate.hasKey(token)).thenReturn(false);
        boolean result = tokenDenylistService.isTokenBlacklisted(token);
        assertFalse(result);
    }

    @Test
    void blacklistUser_Success() {
        Long userId = 42L;
        String expectedKey = "user_blocked:42";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenDenylistService.blacklistUser(userId);
        verify(valueOperations, times(1)).set(expectedKey, "blocked", Duration.ofHours(8));
    }

    @Test
    void isUserBlacklisted_ReturnsTrue() {
        Long userId = 42L;
        String expectedKey = "user_blocked:42";
        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);
        boolean result = tokenDenylistService.isUserBlacklisted(userId);
        assertTrue(result);
    }

    @Test
    void isUserBlacklisted_ReturnsFalse() {
        Long userId = 99L;
        String expectedKey = "user_blocked:99";
        when(redisTemplate.hasKey(expectedKey)).thenReturn(false);
        boolean result = tokenDenylistService.isUserBlacklisted(userId);
        assertFalse(result);
    }
}
