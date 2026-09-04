package com.alfaizunawebid.baseapp.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.alfaizunawebid.baseapp.dto.AuthenticationRequest;
import com.alfaizunawebid.baseapp.dto.RefreshTokenRequest;
import com.alfaizunawebid.baseapp.dto.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // Variabel statis untuk menyimpan state antar-tahap dalam test alur
    private static final String TEST_EMAIL = "integration_test_" + System.currentTimeMillis() + "@example.com";
    private static final String TEST_PASSWORD = "Password123!";
    private static final String TEST_NAME = "Integration Tester";

    private static String accessToken;
    private static String refreshToken;
    private static String refreshedAccessToken;

    @Test
    @Order(1)
    @DisplayName("1. Register User Baru: Harus menghasilkan HTTP 200 dan token JWT RS256")
    void testRegisterUser() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = responseJson.get("token").asText();
        refreshToken = responseJson.get("refreshToken").asText();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
    }

    @Test
    @Order(2)
    @DisplayName("2. Akses Protected Endpoint: Harus berhasil dengan HTTP 200 dan membawa data profil")
    void testAccessProtectedEndpoint_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.name").value(TEST_NAME))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Akses Protected Endpoint Tanpa Token: Harus ditolak dengan HTTP 403")
    void testAccessProtectedEndpoint_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("4. Login User: Kredensial valid harus mengembalikan token baru")
    void testLoginUser() throws Exception {
        AuthenticationRequest loginRequest = AuthenticationRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = responseJson.get("token").asText();
        refreshToken = responseJson.get("refreshToken").asText();
    }

    @Test
    @Order(5)
    @DisplayName("5. Refresh Token Flow: Harus menghasilkan Access Token baru dan dapat digunakan")
    void testRefreshTokenFlow() throws Exception {
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        refreshedAccessToken = responseJson.get("token").asText();

        assertNotNull(refreshedAccessToken);
        assertFalse(refreshedAccessToken.isBlank());

        // Verifikasi token baru dapat mengakses endpoint protected
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    @Test
    @Order(6)
    @DisplayName("6. Logout & Redis Blacklist: Token yang di-logout harus di-blacklist dan ditolak")
    void testLogoutAndBlacklist() throws Exception {
        RefreshTokenRequest logoutBody = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        // 1. Panggil endpoint logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + refreshedAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // 2. Akses kembali dengan token yang baru saja di-logout -> HARUS DITOLAK (HTTP 403)
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isForbidden());

        // 3. Coba refresh dengan refresh token yang sudah di-revoke -> HARUS DITOLAK (HTTP 400)
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutBody)))
                .andExpect(status().isBadRequest());
    }
}
