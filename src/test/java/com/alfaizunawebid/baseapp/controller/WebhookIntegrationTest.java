package com.alfaizunawebid.baseapp.controller;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.alfaizunawebid.baseapp.model.Order;
import com.alfaizunawebid.baseapp.model.OrderStatus;
import com.alfaizunawebid.baseapp.repository.OrderRepository;
import com.alfaizunawebid.baseapp.repository.PaymentTransactionLogRepository;
import com.alfaizunawebid.baseapp.security.HmacSignatureValidator;

/**
 * End-to-End Integration Test untuk Payment Webhook Handler menggunakan Testcontainers.
 * Menguji integrasi nyata HTTP Layer, Spring Security, HMAC Validation, Redis Idempotency, dan PostgreSQL.
 * ---
 * End-to-End Integration Test for Payment Webhook Handler using Testcontainers.
 * Validates real interaction between HTTP Layer, Spring Security, HMAC Validation, Redis Idempotency, and PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class WebhookIntegrationTest {

    // 1. Jalankan PostgreSQL Container asli
    // 1. Spin up a real PostgreSQL container
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // 2. Jalankan Redis Container asli
    // 2. Spin up a real Redis container
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // 3. Sambungkan dynamic properties ke Spring Application Context
    // 3. Bind dynamic container ports to Spring Application Context
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("payment.webhook.secret-key", () -> "test-secret-key-for-testcontainers");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentTransactionLogRepository logRepository;

    @Autowired
    private HmacSignatureValidator signatureValidator;

    private final String secretKey = "test-secret-key-for-testcontainers";

    @BeforeEach
    void cleanUp() {
        logRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("E2E: Valid webhook should verify HMAC, update order in PostgreSQL, and record audit log")
    void shouldProcessValidWebhookSuccessfully() throws Exception {
        // Step 1: Siapkan order di database dengan status PENDING
        // Step 1: Seed a PENDING order in PostgreSQL
        String orderNumber = "ORD-IT-001";
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .amount(new BigDecimal("100000.00"))
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.save(order);

        // Step 2: Siapkan mock payload dan valid signature
        // Step 2: Prepare mock webhook payload and compute valid HMAC
        String transactionId = "TRX-" + UUID.randomUUID().toString().substring(0, 8);
        String rawPayload = String.format(
                "{\"transaction_id\":\"%s\",\"order_number\":\"%s\",\"gross_amount\":100000.00,\"payment_type\":\"qris\",\"transaction_status\":\"settlement\"}",
                transactionId, orderNumber
        );
        String validSignature = signatureValidator.calculateHmac(rawPayload, secretKey);

        // Step 3: Kirim HTTP POST ke endpoint webhook
        // Step 3: Send HTTP POST request to webhook endpoint
        mockMvc.perform(post("/api/v1/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", validSignature)
                .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("Payment webhook processed successfully")));

        // Step 4: Verifikasi PostgreSQL: status order harus menjadi PAID
        // Step 4: Verify PostgreSQL: order status must now be PAID
        Order updatedOrder = orderRepository.findByOrderNumber(orderNumber).orElse(null);
        assertNotNull(updatedOrder);
        assertEquals(OrderStatus.PAID, updatedOrder.getStatus());

        // Step 5: Verifikasi audit log tercatat
        // Step 5: Verify transaction audit log is persisted
        assertEquals(1, logRepository.count());
    }

    @Test
    @DisplayName("E2E: Duplicate webhook retry must be intercepted by Redis Idempotency")
    void shouldIgnoreDuplicateWebhookRetry() throws Exception {
        String orderNumber = "ORD-IT-002";
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .amount(new BigDecimal("50000.00"))
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.save(order);

        String transactionId = "TRX-DUPLICATE-TEST";
        String rawPayload = String.format(
                "{\"transaction_id\":\"%s\",\"order_number\":\"%s\",\"gross_amount\":50000.00,\"payment_type\":\"bank_transfer\",\"transaction_status\":\"settlement\"}",
                transactionId, orderNumber
        );
        String validSignature = signatureValidator.calculateHmac(rawPayload, secretKey);

        // Request 1: Sukses diproses
        // Request 1: Successfully processed
        mockMvc.perform(post("/api/v1/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", validSignature)
                .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Payment webhook processed successfully")));

        // Request 2 (Retry): Harus dicegat oleh Redis idempotency
        // Request 2 (Retry): Must be intercepted by Redis idempotency
        mockMvc.perform(post("/api/v1/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", validSignature)
                .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("Duplicate notification ignored")));
    }

    @Test
    @DisplayName("E2E: Spoofed webhook with invalid HMAC signature must be rejected with 401 Unauthorized")
    void shouldRejectSpoofedWebhookRequest() throws Exception {
        String rawPayload = "{\"transaction_id\":\"TRX-FAKE\",\"order_number\":\"ORD-FAKE\",\"gross_amount\":10000.00,\"transaction_status\":\"settlement\"}";

        mockMvc.perform(post("/api/v1/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", "fake-tampered-signature-123")
                .content(rawPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid HMAC signature")));
    }
}
