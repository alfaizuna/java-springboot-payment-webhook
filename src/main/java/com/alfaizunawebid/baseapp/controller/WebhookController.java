package com.alfaizunawebid.baseapp.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alfaizunawebid.baseapp.dto.WebhookPayload;
import com.alfaizunawebid.baseapp.security.HmacSignatureValidator;
import com.alfaizunawebid.baseapp.service.IdempotencyService;
import com.alfaizunawebid.baseapp.service.OrderProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller untuk menangani incoming payment webhook callbacks.
 * Menerapkan verifikasi HMAC signature dan Redis idempotency lock.
 * ---
 * Controller handling incoming payment webhook callbacks.
 * Enforces HMAC signature verification and Redis idempotency locks.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final HmacSignatureValidator signatureValidator;
    private final IdempotencyService idempotencyService;
    private final OrderProcessingService orderProcessingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawPayload
    ) {
        log.info("Received payment webhook notification");

        // Langkah 1: Verifikasi HMAC Signature (Menolak request palsu)
        // Step 1: Verify HMAC Signature (Rejects spoofed/forged requests)
        signatureValidator.validateSignature(rawPayload, signature);

        // Langkah 2: Parse raw JSON string ke DTO WebhookPayload
        // Step 2: Parse raw JSON string to WebhookPayload DTO
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WebhookPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook JSON payload", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Malformed JSON payload"
            ));
        }

        String transactionId = payload.getTransactionId();

        // Langkah 3: Redis Idempotency Check (Mencegah double-processing)
        // Step 3: Redis Idempotency Check (Prevents duplicate processing)
        boolean lockAcquired = idempotencyService.acquireLock(transactionId);
        if (!lockAcquired) {
            log.info("Duplicate webhook callback received for transaction [{}]. Returning 200 OK immediately.",
                    transactionId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Duplicate notification ignored"
            ));
        }

        // Langkah 4: Proses bisnis transaksi order
        // Step 4: Execute order business processing
        try {
            orderProcessingService.processWebhook(payload, rawPayload);
            idempotencyService.markAsCompleted(transactionId);
        } catch (Exception e) {
            log.error("Internal error processing order for transaction [{}]. Releasing lock for retry.",
                    transactionId, e);
            idempotencyService.releaseLock(transactionId);
            throw e;
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Payment webhook processed successfully"
        ));
    }
}
