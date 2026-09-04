package com.alfaizunawebid.baseapp.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alfaizunawebid.baseapp.dto.ChangePasswordRequest;
import com.alfaizunawebid.baseapp.dto.UpdateUserRequest;
import com.alfaizunawebid.baseapp.dto.UserResponse;
import com.alfaizunawebid.baseapp.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints untuk mengelola profil user dan manajemen data user (Admin)")
public class UserController {

    private final UserService userService;

    /**
     * Mengambil data user yang sedang login
     * GET /api/v1/users/me
     */
    @Operation(summary = "Get current user profile", description = "Mengambil data profil user yang sedang login dari token JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profil user berhasil diambil"),
            @ApiResponse(responseCode = "403", description = "Access denied / Unauthorized")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }

    /**
     * Memperbarui profil (nama) user yang sedang login
     * PUT /api/v1/users/me
     */
    @Operation(summary = "Update current user profile", description = "Memperbarui profil (nama) user yang sedang login.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profil user berhasil diperbarui"),
            @ApiResponse(responseCode = "400", description = "Format request tidak valid"),
            @ApiResponse(responseCode = "403", description = "Access denied / Unauthorized")
    })
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.updateCurrentUser(request));
    }

    /**
     * Mengubah password user yang sedang login
     * PATCH /api/v1/users/me/password
     */
    @Operation(summary = "Change password", description = "Mengubah password user yang sedang login dengan memverifikasi old password.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password berhasil diubah"),
            @ApiResponse(responseCode = "400", description = "Password lama salah atau konfirmasi password tidak cocok"),
            @ApiResponse(responseCode = "403", description = "Access denied / Unauthorized")
    })
    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    /**
     * Mengambil daftar semua user (Khusus ADMIN)
     * GET /api/v1/users?page=0&size=10
     */
    @Operation(summary = "Get all users (Admin only)", description = "Mengambil daftar seluruh user dengan pagination dan sorting (Hanya untuk ADMIN).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daftar user berhasil diambil"),
            @ApiResponse(responseCode = "403", description = "Forbidden (Bukan ADMIN)")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /**
     * Menghapus user berdasarkan ID (Khusus ADMIN)
     * DELETE /api/v1/users/{id}
     */
    @Operation(summary = "Delete user by ID (Admin only)", description = "Menghapus akun user berdasarkan ID (Hanya untuk ADMIN).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User berhasil dihapus"),
            @ApiResponse(responseCode = "404", description = "User tidak ditemukan"),
            @ApiResponse(responseCode = "403", description = "Forbidden (Bukan ADMIN)")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable Long id
    ) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }
}
