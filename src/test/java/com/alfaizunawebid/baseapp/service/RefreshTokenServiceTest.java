package com.alfaizunawebid.baseapp.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alfaizunawebid.baseapp.model.RefreshToken;
import com.alfaizunawebid.baseapp.model.Role;
import com.alfaizunawebid.baseapp.model.User;
import com.alfaizunawebid.baseapp.repository.RefreshTokenRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", 604800000L); // 7 hari

        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("encoded_pass")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("createRefreshToken() harus membuat token baru dengan revoked=false dan expiry di masa depan")
    void testCreateRefreshToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken created = refreshTokenService.createRefreshToken(testUser);

        assertNotNull(created);
        assertNotNull(created.getToken());
        assertEquals(testUser, created.getUser());
        assertFalse(created.isRevoked());
        assertTrue(created.getExpiryDate().isAfter(Instant.now()));

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("findByToken() harus memanggil repository.findByToken()")
    void testFindByToken() {
        String tokenStr = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(10L)
                .token(tokenStr)
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(refreshToken));

        Optional<RefreshToken> result = refreshTokenService.findByToken(tokenStr);

        assertTrue(result.isPresent());
        assertEquals(tokenStr, result.get().getToken());
        verify(refreshTokenRepository).findByToken(tokenStr);
    }

    @Test
    @DisplayName("verifyExpiration() harus berhasil mengembalikan token jika masih aktif")
    void testVerifyExpiration_Success() {
        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .token(UUID.randomUUID().toString())
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        RefreshToken verified = refreshTokenService.verifyExpiration(token);

        assertEquals(token, verified);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("verifyExpiration() harus melempar exception jika token berstatus revoked")
    void testVerifyExpiration_RevokedToken() {
        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .token(UUID.randomUUID().toString())
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.verifyExpiration(token)
        );

        assertTrue(ex.getMessage().contains("revoked"));
    }

    @Test
    @DisplayName("verifyExpiration() harus menghapus token dari DB dan melempar exception jika sudah expired")
    void testVerifyExpiration_ExpiredToken() {
        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .token(UUID.randomUUID().toString())
                .user(testUser)
                .expiryDate(Instant.now().minusSeconds(60)) // Kadaluarsa 1 menit lalu
                .revoked(false)
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.verifyExpiration(token)
        );

        assertTrue(ex.getMessage().contains("expired"));
        verify(refreshTokenRepository).delete(token);
    }

    @Test
    @DisplayName("revokeToken() harus mengubah flag revoked menjadi true dan menyimpannya")
    void testRevokeToken_Success() {
        String tokenStr = "test-uuid-token";
        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .token(tokenStr)
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));

        refreshTokenService.revokeToken(tokenStr);

        assertTrue(token.isRevoked());
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("revokeToken() melempar exception jika token tidak ditemukan")
    void testRevokeToken_NotFound() {
        String tokenStr = "non-existent-token";
        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.revokeToken(tokenStr)
        );

        verify(refreshTokenRepository, never()).save(any());
    }
}
