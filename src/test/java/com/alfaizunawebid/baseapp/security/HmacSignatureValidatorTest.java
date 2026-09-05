package com.alfaizunawebid.baseapp.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.alfaizunawebid.baseapp.exception.InvalidSignatureException;

/**
 * Unit test untuk HmacSignatureValidator.
 * Memastikan proteksi kriptografi berfungsi terhadap berbagai skenario fraud.
 * ---
 * Unit tests for HmacSignatureValidator.
 * Ensures cryptographic protection works against various fraud scenarios.
 */
class HmacSignatureValidatorTest {

    private HmacSignatureValidator validator;
    private final String testSecret = "my-secret-test-key-12345";

    @BeforeEach
    void setUp() {
        validator = new HmacSignatureValidator(testSecret);
    }

    @Test
    @DisplayName("Should successfully validate a valid signature")
    void shouldValidateValidSignature() {
        String payload = "{\"order_number\":\"ORD-101\",\"amount\":50000}";
        String validSignature = validator.calculateHmac(payload, testSecret);

        assertNotNull(validSignature);
        // Tidak melempar exception = Lolos verifikasi
        // No exception thrown = Verification passed
        assertDoesNotThrow(() -> validator.validateSignature(payload, validSignature));
    }

    @Test
    @DisplayName("Should throw InvalidSignatureException when signature is altered/tampered")
    void shouldRejectTamperedSignature() {
        String payload = "{\"order_number\":\"ORD-101\",\"amount\":50000}";
        String fakeSignature = "deadbeef1234567890abcdef";

        assertThrows(InvalidSignatureException.class, () -> 
            validator.validateSignature(payload, fakeSignature)
        );
    }

    @Test
    @DisplayName("Should throw InvalidSignatureException when payload body is modified (Data Tampering)")
    void shouldRejectTamperedPayload() {
        String originalPayload = "{\"order_number\":\"ORD-101\",\"amount\":50000}";
        String validSignature = validator.calculateHmac(originalPayload, testSecret);

        // Hacker mengubah nominal amount jadi 0 rupiah
        // Attacker alters the amount to 0
        String tamperedPayload = "{\"order_number\":\"ORD-101\",\"amount\":0}";

        assertThrows(InvalidSignatureException.class, () -> 
            validator.validateSignature(tamperedPayload, validSignature)
        );
    }

    @Test
    @DisplayName("Should throw InvalidSignatureException when signature header is missing or blank")
    void shouldRejectMissingSignatureHeader() {
        String payload = "{\"order_number\":\"ORD-101\",\"amount\":50000}";

        assertThrows(InvalidSignatureException.class, () -> 
            validator.validateSignature(payload, null)
        );

        assertThrows(InvalidSignatureException.class, () -> 
            validator.validateSignature(payload, "   ")
        );
    }
}
