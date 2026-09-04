package com.alfaizunawebid.baseapp.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.alfaizunawebid.baseapp.model.Role;
import com.alfaizunawebid.baseapp.model.User;

import io.jsonwebtoken.security.SignatureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private KeyPair keyPair;
    private User testUser;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        // Generate RSA 2048-bit key pair untuk testing
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        keyPair = keyPairGen.generateKeyPair();

        jwtService = new JwtService(keyPair.getPrivate(), keyPair.getPublic());
        // Set default expiration 1 jam (3600000 ms)
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);

        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("generateToken() harus menghasilkan token JWT RS256 yang valid")
    void testGenerateToken() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token);
        assertFalse(token.isBlank());
        // Memastikan format JWT: header.payload.signature (3 bagian dipisahkan titik)
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
    }

    @Test
    @DisplayName("extractUsername() harus mengekstrak email pengguna dari token")
    void testExtractUsername() {
        String token = jwtService.generateToken(testUser);
        String extractedEmail = jwtService.extractUsername(token);

        assertEquals("test@example.com", extractedEmail);
    }

    @Test
    @DisplayName("isTokenValid() harus mengembalikan true untuk token aktif dan user yang cocok")
    void testIsTokenValid_Success() {
        String token = jwtService.generateToken(testUser);
        boolean isValid = jwtService.isTokenValid(token, testUser);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("isTokenValid() harus mengembalikan false jika user berbeda")
    void testIsTokenValid_DifferentUser() {
        String token = jwtService.generateToken(testUser);

        User anotherUser = User.builder()
                .id(2L)
                .name("Other User")
                .email("other@example.com")
                .password("secret")
                .role(Role.USER)
                .build();

        boolean isValid = jwtService.isTokenValid(token, anotherUser);
        assertFalse(isValid);
    }

    @Test
    @DisplayName("isTokenExpired() harus mengembalikan true untuk token yang masa berlakunya minus/habis")
    void testIsTokenExpired() {
        // Set expiration ke masa lampau (-1000 ms)
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
        String expiredToken = jwtService.generateToken(testUser);

        // jjWT akan melempar ExpiredJwtException saat parse token yang sudah expired
        assertThrows(Exception.class, () -> jwtService.isTokenExpired(expiredToken));
    }

    @Test
    @DisplayName("Signature verification harus gagal jika token diverifikasi dengan Public Key berbeda")
    void testInvalidSignature() throws NoSuchAlgorithmException {
        // Generate pasangan kunci lain
        KeyPairGenerator otherGen = KeyPairGenerator.getInstance("RSA");
        otherGen.initialize(2048);
        KeyPair otherKeyPair = otherGen.generateKeyPair();

        // Sign dengan keyPair pertama
        String token = jwtService.generateToken(testUser);

        // Buat service dengan Public Key yang salah
        JwtService invalidVerifier = new JwtService(otherKeyPair.getPrivate(), otherKeyPair.getPublic());
        ReflectionTestUtils.setField(invalidVerifier, "jwtExpiration", 3600000L);

        assertThrows(SignatureException.class, () -> invalidVerifier.extractUsername(token));
    }

    @Test
    @DisplayName("getRemainingExpiration() harus mengembalikan sisa waktu token lebih besar dari 0")
    void testGetRemainingExpiration() {
        String token = jwtService.generateToken(testUser);
        long remaining = jwtService.getRemainingExpiration(token);

        assertTrue(remaining > 0);
        assertTrue(remaining <= 3600000L);
    }
}
