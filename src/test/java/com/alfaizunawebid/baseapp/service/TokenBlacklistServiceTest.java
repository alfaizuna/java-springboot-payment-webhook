package com.alfaizunawebid.baseapp.service;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    private static final String TEST_TOKEN = "sample.jwt.token";
    private static final String REDIS_KEY = "blacklist:token:" + TEST_TOKEN;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("blacklistToken() harus menyimpan token ke Redis dengan TTL jika remainingTimeMs > 0")
    void testBlacklistToken_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        long remainingTimeMs = 60000L; // 1 menit
        tokenBlacklistService.blacklistToken(TEST_TOKEN, remainingTimeMs);

        verify(valueOperations).set(eq(REDIS_KEY), eq("blacklisted"), eq(Duration.ofMillis(remainingTimeMs)));
    }

    @Test
    @DisplayName("blacklistToken() tidak boleh memanggil Redis jika remainingTimeMs <= 0")
    void testBlacklistToken_ZeroOrNegativeTime() {
        tokenBlacklistService.blacklistToken(TEST_TOKEN, 0L);
        tokenBlacklistService.blacklistToken(TEST_TOKEN, -500L);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("isTokenBlacklisted() mengembalikan true jika key ada di Redis")
    void testIsTokenBlacklisted_True() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(Boolean.TRUE);

        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(TEST_TOKEN);

        assertTrue(isBlacklisted);
        verify(redisTemplate).hasKey(REDIS_KEY);
    }

    @Test
    @DisplayName("isTokenBlacklisted() mengembalikan false jika key tidak ada di Redis")
    void testIsTokenBlacklisted_False() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(Boolean.FALSE);

        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(TEST_TOKEN);

        assertFalse(isBlacklisted);
        verify(redisTemplate).hasKey(REDIS_KEY);
    }

    @Test
    @DisplayName("isTokenBlacklisted() mengembalikan false jika input token null atau kosong")
    void testIsTokenBlacklisted_NullOrBlank() {
        assertFalse(tokenBlacklistService.isTokenBlacklisted(null));
        assertFalse(tokenBlacklistService.isTokenBlacklisted(""));
        assertFalse(tokenBlacklistService.isTokenBlacklisted("   "));

        verify(redisTemplate, never()).hasKey(any());
    }
}
