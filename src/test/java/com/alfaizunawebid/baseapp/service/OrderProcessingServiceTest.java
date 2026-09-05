package com.alfaizunawebid.baseapp.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alfaizunawebid.baseapp.dto.WebhookPayload;
import com.alfaizunawebid.baseapp.model.Order;
import com.alfaizunawebid.baseapp.model.OrderStatus;
import com.alfaizunawebid.baseapp.model.PaymentTransactionLog;
import com.alfaizunawebid.baseapp.repository.OrderRepository;
import com.alfaizunawebid.baseapp.repository.PaymentTransactionLogRepository;

/**
 * Unit test untuk OrderProcessingService.
 * Menguji State Machine order dan deteksi pemalsuan nominal (amount tampering).
 * ---
 * Unit tests for OrderProcessingService.
 * Tests order state machine rules and amount tampering detection.
 */
@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentTransactionLogRepository logRepository;

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-001")
                .amount(new BigDecimal("150000.00"))
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Should successfully mark order as PAID when settlement webhook is received")
    void shouldUpdateOrderToPaidOnSettlement() {
        WebhookPayload payload = WebhookPayload.builder()
                .transactionId("TRX-999")
                .orderNumber("ORD-001")
                .grossAmount(new BigDecimal("150000.00"))
                .transactionStatus("settlement")
                .paymentType("qris")
                .build();

        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(pendingOrder));

        orderProcessingService.processWebhook(payload, "{}");

        // Verifikasi status order berubah jadi PAID dan disimpan
        // Verify order status transitions to PAID and is saved
        assertEquals(OrderStatus.PAID, pendingOrder.getStatus());
        verify(orderRepository).save(pendingOrder);
        // Verifikasi audit log selalu disimpan
        // Verify audit log is always persisted
        verify(logRepository).save(any(PaymentTransactionLog.class));
    }

    @Test
    @DisplayName("Should guard state: do not alter order if already PAID")
    void shouldNotAlterAlreadyPaidOrder() {
        pendingOrder.setStatus(OrderStatus.PAID);

        WebhookPayload payload = WebhookPayload.builder()
                .transactionId("TRX-999")
                .orderNumber("ORD-001")
                .grossAmount(new BigDecimal("150000.00"))
                .transactionStatus("expire") // status terlambat datang
                .paymentType("qris")
                .build();

        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(pendingOrder));

        orderProcessingService.processWebhook(payload, "{}");

        // Status harus tetap PAID, dan orderRepository.save tidak dipanggil
        // Status must remain PAID, and orderRepository.save should not be invoked
        assertEquals(OrderStatus.PAID, pendingOrder.getStatus());
        verify(orderRepository, never()).save(pendingOrder);
        // Audit log tetap harus dicatat
        // Audit log must still be recorded
        verify(logRepository).save(any(PaymentTransactionLog.class));
    }

    @Test
    @DisplayName("Should mark order as FAILED when gross_amount does not match (Amount Tampering fraud)")
    void shouldDetectAmountMismatchAsFraud() {
        WebhookPayload payload = WebhookPayload.builder()
                .transactionId("TRX-999")
                .orderNumber("ORD-001")
                .grossAmount(new BigDecimal("1000.00")) // Seharusnya 150000, cuma bayar 1000
                .transactionStatus("settlement")
                .paymentType("qris")
                .build();

        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(pendingOrder));

        orderProcessingService.processWebhook(payload, "{}");

        // Order harus ditandai FAILED karena fraud
        // Order must be marked FAILED due to fraud attempt
        assertEquals(OrderStatus.FAILED, pendingOrder.getStatus());
        verify(orderRepository).save(pendingOrder);
    }
}
