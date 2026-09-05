package com.alfaizunawebid.baseapp.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alfaizunawebid.baseapp.exception.InvalidSignatureException;

import lombok.extern.slf4j.Slf4j;

/**
 * Komponen untuk memvalidasi HMAC SHA-256 signature pada webhook payment.
 * Memastikan keaslian (authenticity) dan integritas data (integrity) payload.
 * ---
 * Component to validate HMAC SHA-256 signatures on payment webhooks.
 * Ensures data authenticity and payload integrity.
 */
@Slf4j
@Component
public class HmacSignatureValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // Kunci rahasia bersama yang disepakati dengan payment gateway.
    // Shared secret key agreed upon with the payment gateway.
    private final String secretKey;

    public HmacSignatureValidator(
            @Value("${payment.webhook.secret-key:default-webhook-secret-key-for-dev}") String secretKey
    ) {
        this.secretKey = secretKey;
    }

    /**
     * Memvalidasi apakah signature yang dikirim gateway cocok dengan payload.
     * Melempar InvalidSignatureException jika tidak cocok.
     * ---
     * Validates whether the incoming signature matches the calculated payload HMAC.
     * Throws InvalidSignatureException if verification fails.
     *
     * @param rawPayload        Isi mentah request body / Raw request body string
     * @param incomingSignature Signature yang diterima dari header HTTP / Signature received from HTTP header
     */
    public void validateSignature(String rawPayload, String incomingSignature) {
        if (incomingSignature == null || incomingSignature.isBlank()) {
            log.warn("Webhook rejected: Missing signature header");
            throw new InvalidSignatureException("Signature header is missing");
        }

        if (rawPayload == null) {
            log.warn("Webhook rejected: Empty payload");
            throw new InvalidSignatureException("Payload body cannot be null");
        }

        String calculatedSignature = calculateHmac(rawPayload, this.secretKey);

        // PENTING: Gunakan MessageDigest.isEqual() untuk mencegah Timing Attack.
        // Jangan gunakan String.equals() karena rentan terhadap time-based leakage!
        // ---
        // IMPORTANT: Use MessageDigest.isEqual() to prevent Timing Attacks.
        // Never use String.equals() as it is vulnerable to time-based leakage!
        boolean isValid = MessageDigest.isEqual(
                calculatedSignature.getBytes(StandardCharsets.UTF_8),
                incomingSignature.trim().getBytes(StandardCharsets.UTF_8)
        );

        if (!isValid) {
            log.warn("Webhook rejected: Invalid signature. Expected [{}], received [{}]", 
                    calculatedSignature, incomingSignature);
            throw new InvalidSignatureException("Invalid HMAC signature");
        }

        log.debug("Webhook HMAC signature verified successfully");
    }

    /**
     * Menghitung nilai hash HMAC-SHA256 dari raw payload menggunakan secret key.
     * Mengembalikan hasil dalam format string Hexadecimal (lowercase).
     * ---
     * Computes the HMAC-SHA256 hash of the raw payload using the secret key.
     * Returns the digest as a lowercase Hexadecimal string.
     *
     * @param payload Data teks yang akan di-hash / Text data to hash
     * @param secret  Kunci rahasia / Secret key
     * @return String hexadecimal dari hasil HMAC / Hexadecimal string of HMAC digest
     */
    public String calculateHmac(String payload, String secret) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), 
                    HMAC_ALGORITHM
            );
            
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Konversi byte array ke hex string (standar Java 17+)
            // Convert byte array to hex string (standard in Java 17+)
            return HexFormat.of().formatHex(hmacBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Cryptographic error while calculating HMAC", e);
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
