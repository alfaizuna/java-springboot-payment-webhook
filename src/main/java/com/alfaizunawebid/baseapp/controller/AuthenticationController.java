package com.alfaizunawebid.baseapp.controller;

import java.util.Base64;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alfaizunawebid.baseapp.dto.AuthenticationRequest;
import com.alfaizunawebid.baseapp.dto.AuthenticationResponse;
import com.alfaizunawebid.baseapp.dto.RefreshTokenRequest;
import com.alfaizunawebid.baseapp.dto.RegisterRequest;
import com.alfaizunawebid.baseapp.service.AuthenticationService;
import com.alfaizunawebid.baseapp.service.JwtService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints untuk registrasi, login, refresh token, logout, dan public key")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    /**
     * Register user baru
     * POST /api/v1/auth/register
     */
    @Operation(summary = "Register user baru", description = "Mendaftarkan user baru dengan role USER secara default.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User berhasil didaftarkan"),
            @ApiResponse(responseCode = "400", description = "Validasi request body gagal atau email sudah terdaftar")
    })
    @SecurityRequirements // Public endpoint
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(authenticationService.register(request));
    }

    /**
     * Login user yang sudah terdaftar
     * POST /api/v1/auth/login
     */
    @Operation(summary = "Login user", description = "Autentikasi kredensial user dan mengembalikan JWT Access Token (RS256) serta Refresh Token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login berhasil"),
            @ApiResponse(responseCode = "400", description = "Format request tidak valid"),
            @ApiResponse(responseCode = "401", description = "Email atau password salah")
    })
    @SecurityRequirements // Public endpoint
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody AuthenticationRequest request
    ) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    /**
     * Refresh access token menggunakan refresh token
     * POST /api/v1/auth/refresh-token
     */
    @Operation(summary = "Refresh access token", description = "Membuat Access Token baru menggunakan Refresh Token yang valid dan belum expired/revoked.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Access token berhasil diperbarui"),
            @ApiResponse(responseCode = "400", description = "Request tidak valid atau refresh token expired/revoked")
    })
    @SecurityRequirements // Public endpoint
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthenticationResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(authenticationService.refreshToken(request));
    }

    /**
     * Logout user:
     * - Blacklist Access Token ke Redis
     * - Revoke Refresh Token di DB
     * POST /api/v1/auth/logout
     */
    @Operation(summary = "Logout user", description = "Blacklist Access Token ke Redis (dengan TTL sisa masa berlaku token) dan revoke Refresh Token di database.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Berhasil logout")
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RefreshTokenRequest request
    ) {
        authenticationService.logout(authHeader, request);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Mengambil RSA Public Key (PEM format) untuk resource server / verifikasi client
     * GET /api/v1/auth/public-key
     */
    @Operation(summary = "Get RSA Public Key", description = "Mengambil Public Key RSA dalam format PEM X.509 untuk verifikasi tanda tangan JWT oleh client atau resource server terdistribusi.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Public Key berhasil diambil")
    })
    @SecurityRequirements // Public endpoint
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        String base64Key = Base64.getEncoder().encodeToString(jwtService.getPublicKey().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" +
                base64Key.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
        return ResponseEntity.ok(Map.of("publicKey", pem));
    }
}
