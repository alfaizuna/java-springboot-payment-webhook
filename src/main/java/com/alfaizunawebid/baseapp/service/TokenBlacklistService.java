package com.alfaizunawebid.baseapp.service;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "blacklist:token:";
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Memasukkan token ke dalam Redis Blacklist dengan TTL sesuai sisa masa berlakunya.
     * @param token JWT Access Token
     * @param remainingTimeMs Sisa waktu kadaluarsa token dalam milidetik
     */
    public void blacklistToken(String token, long remainingTimeMs) {
        if (remainingTimeMs > 0) {
            String key = BLACKLIST_KEY_PREFIX + token;
            redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofMillis(remainingTimeMs));
            log.info("Token blacklisted in Redis with TTL: {} ms", remainingTimeMs);
        }
    }

    /**
     * Memeriksa apakah token ada di dalam Redis Blacklist.
     * Operasi lookup di Redis berkecepatan O(1).
     * @param token JWT Access Token
     * @return true jika token di-blacklist, false jika tidak
     */
    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = BLACKLIST_KEY_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
