package com.alfaizunawebid.baseapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alfaizunawebid.baseapp.model.User;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/demo")
@Tag(name = "Demo / Role Check", description = "Endpoints demonstrasi proteksi endpoint berbasis role")
public class DemoController {

    /**
     * Endpoint yang bisa diakses semua user yang sudah login
     * GET /api/v1/demo/hello
     */
    @Operation(summary = "Demo authenticated user", description = "Akses endpoint untuk semua user yang telah terautentikasi (USER maupun ADMIN).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Akses berhasil"),
            @ApiResponse(responseCode = "403", description = "Access Denied / Token tidak valid")
    })
    @GetMapping("/hello")
    public ResponseEntity<Map<String, Object>> hello() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        return ResponseEntity.ok(Map.of(
                "message", "Hello, " + currentUser.getName() + "!",
                "email", currentUser.getEmail(),
                "role", currentUser.getRole().name(),
                "authorities", auth.getAuthorities().toString()
        ));
    }

    /**
     * Endpoint khusus ADMIN
     * GET /api/v1/demo/admin
     */
    @Operation(summary = "Demo admin only", description = "Akses endpoint khusus pengguna dengan role ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Akses admin berhasil"),
            @ApiResponse(responseCode = "403", description = "Forbidden (Bukan role ADMIN)")
    })
    @GetMapping("/admin")
    public ResponseEntity<Map<String, String>> adminOnly() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome, Admin! This is a restricted area."
        ));
    }
}
