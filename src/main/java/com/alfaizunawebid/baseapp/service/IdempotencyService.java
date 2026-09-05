package com.alfaizunawebid.baseapp.service;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service untuk menangani idempotency webhook menggunakan Redis.
 * Mencegah pemrosesan ganda (double-processing) akibat retry otomatis dari payment gateway.
 * ---
 * Service to manage webhook idempotency using Redis.
 * Prevents double-processing caused by automated payment gateway retries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "payment:webhook:idempotency:";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    // Default masa berlaku key (TTL): 24 jam
    // Default Time-To-Live (TTL): 24 hours
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Mencoba mengklaim idempotency key secara atomic (SETNX).
     * Mengembalikan true jika key baru pertama kali diproses.
     * Mengembalikan false jika key sudah ada (berarti request duplikat/retry).
     * ---
     * Attempts to atomically claim an idempotency key (SETNX).
     * Returns true if this is the first time processing this key.
     * Returns false if key already exists (indicating a duplicate/retry request).
     *
     * @param transactionId ID unik transaksi dari payment provider / Unique transaction ID
     * @return true jika berhasil diklaim, false jika duplikat / true if claimed, false if duplicate
     */
    public boolean acquireLock(String transactionId) {
        String redisKey = buildKey(transactionId);

        // setIfAbsent setara dengan perintah Redis: SET key value NX EX ttl
        // Operasi ini ATOMIC: tidak akan terjadi race condition meski ditembak paralel
        // ---
        // setIfAbsent is equivalent to Redis: SET key value NX EX ttl
        // This operation is ATOMIC: safe from race conditions even under parallel requests
        Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, STATUS_PROCESSING, DEFAULT_TTL);

        boolean isAcquired = Boolean.TRUE.equals(success);

        if (isAcquired) {
            log.info("Idempotency lock acquired for transactionId: [{}]", transactionId);
        } else {
            log.warn("Duplicate request detected for transactionId: [{}]. Skipping processing.", transactionId);
        }

        return isAcquired;
    }

    /**
     * Memperbarui status idempotency key menjadi COMPLETED setelah proses database berhasil.
     * ---
     * Updates the idempotency key status to COMPLETED after successful database operations.
     *
     * @param transactionId ID unik transaksi / Unique transaction ID
     */
    public void markAsCompleted(String transactionId) {
        String redisKey = buildKey(transactionId);
        redisTemplate.opsForValue().set(redisKey, STATUS_COMPLETED, DEFAULT_TTL);
        log.info("Transaction [{}] marked as COMPLETED in idempotency store", transactionId);
    }

    /**
     * Melepaskan idempotency key jika terjadi error transien (agar gateway bisa retry).
     * ---
     * Releases the idempotency key in case of transient system failure (allowing gateway to retry).
     *
     * @param transactionId ID unik transaksi / Unique transaction ID
     */
    public void releaseLock(String transactionId) {
        String redisKey = buildKey(transactionId);
        redisTemplate.delete(redisKey);
        log.warn("Released idempotency lock for transactionId: [{}] due to processing failure", transactionId);
    }

    /**
     * Helper untuk membentuk format key Redis yang konsisten.
     * ---
     * Helper to construct a consistent Redis key format.
     */
    private String buildKey(String transactionId) {
        return IDEMPOTENCY_KEY_PREFIX + transactionId;
    }
}
