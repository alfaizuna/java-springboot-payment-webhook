package com.alfaizunawebid.baseapp.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alfaizunawebid.baseapp.dto.CreateOrderRequest;
import com.alfaizunawebid.baseapp.model.Order;
import com.alfaizunawebid.baseapp.model.OrderStatus;
import com.alfaizunawebid.baseapp.repository.OrderRepository;
import com.alfaizunawebid.baseapp.security.HmacSignatureValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller untuk mengelola order dan menyediakan simulator webhook untuk testing.
 * ---
 * Controller to manage orders and provide a webhook simulator for testing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final HmacSignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    /**
     * Membuat order baru dengan status awal PENDING.
     * ---
     * Creates a new order with initial PENDING status.
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = Order.builder()
                .orderNumber(request.getOrderNumber())
                .amount(request.getAmount())
                .status(OrderStatus.PENDING)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Created new order: [{}] with amount [{}]", saved.getOrderNumber(), saved.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Cek status order terkini.
     * ---
     * Retrieves the current order status.
     */
    @GetMapping("/{orderNumber}/status")
    public ResponseEntity<?> getOrderStatus(@PathVariable String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .map(order -> ResponseEntity.ok(Map.of(
                        "orderNumber", order.getOrderNumber(),
                        "amount", order.getAmount(),
                        "status", order.getStatus(),
                        "updatedAt", order.getUpdatedAt()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "message", "Order not found"
                )));
    }

    /**
     * Helper Simulator: Menghasilkan payload dan valid HMAC signature untuk pengujian di Postman / Curl.
     * ---
     * Helper Simulator: Generates payload and valid HMAC signature for Postman / Curl testing.
     */
    @PostMapping("/{orderNumber}/simulate-webhook")
    public ResponseEntity<?> simulateWebhook(@PathVariable String orderNumber) throws JsonProcessingException {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElse(null);

        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found"));
        }

        // Siapkan mock payload seolah-olah dari Payment Gateway
        // Prepare mock payload mimicking the Payment Gateway
        Map<String, Object> mockPayload = Map.of(
                "transaction_id", "TRX-" + UUID.randomUUID().toString().substring(0, 8),
                "order_number", order.getOrderNumber(),
                "gross_amount", order.getAmount(),
                "payment_type", "qris",
                "transaction_status", "settlement"
        );

        String rawJson = objectMapper.writeValueAsString(mockPayload);

        // Hitung HMAC signature yang valid
        // Compute valid HMAC signature
        String validSignature = signatureValidator.calculateHmac(
                rawJson, 
                "super-secret-webhook-key-change-in-prod"
        );

        return ResponseEntity.ok(Map.of(
                "description", "Use the values below to test POST /api/v1/webhooks/payment",
                "header_name", "X-Signature",
                "valid_signature", validSignature,
                "raw_payload", rawJson
        ));
    }
}
