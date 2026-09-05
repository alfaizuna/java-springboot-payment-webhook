package com.alfaizunawebid.baseapp.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alfaizunawebid.baseapp.dto.WebhookPayload;
import com.alfaizunawebid.baseapp.model.Order;
import com.alfaizunawebid.baseapp.model.OrderStatus;
import com.alfaizunawebid.baseapp.model.PaymentTransactionLog;
import com.alfaizunawebid.baseapp.repository.OrderRepository;
import com.alfaizunawebid.baseapp.repository.PaymentTransactionLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service untuk memproses logika bisnis pesanan dan audit log transaksi payment.
 * Menjamin transisi state order berjalan sesuai aturan bisnis (State Machine).
 * ---
 * Service to process order business logic and transaction audit logs.
 * Enforces order state machine transition rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final PaymentTransactionLogRepository logRepository;

    /**
     * Memproses payload webhook, mencatat audit log, dan mengupdate status order.
     * ---
     * Processes webhook payload, saves audit log, and updates order status.
     *
     * @param payload    Objek data webhook / Parsed webhook payload
     * @param rawPayload JSON mentah untuk audit trail / Raw JSON string for audit trail
     */
    @Transactional
    public void processWebhook(WebhookPayload payload, String rawPayload) {
        // 1. Simpan audit log untuk setiap webhook yang masuk
        // 1. Record an audit log for every incoming webhook event
        PaymentTransactionLog auditLog = PaymentTransactionLog.builder()
                .transactionId(payload.getTransactionId())
                .orderNumber(payload.getOrderNumber())
                .paymentType(payload.getPaymentType())
                .grossAmount(payload.getGrossAmount())
                .transactionStatus(payload.getTransactionStatus())
                .rawPayload(rawPayload)
                .build();
        logRepository.save(auditLog);

        // 2. Cari order di database
        // 2. Look up the order in database
        Order order = orderRepository.findByOrderNumber(payload.getOrderNumber())
                .orElse(null);

        if (order == null) {
            log.warn("Order [{}] not found for webhook transaction [{}]. Skipping state update.",
                    payload.getOrderNumber(), payload.getTransactionId());
            return;
        }

        // 3. Guard Clause: Jangan update jika order sudah berstatus final (PAID)
        // 3. Guard Clause: Do not update if order is already in final state (PAID)
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order [{}] is already marked as PAID. Ignoring status transition.", order.getOrderNumber());
            return;
        }

        // 4. Verifikasi nominal (Mencegah Amount Tampering)
        // 4. Verify amount (Preventing Amount Tampering)
        if (payload.getGrossAmount().compareTo(order.getAmount()) != 0) {
            log.error("Fraud alert: Amount mismatch for order [{}]. Expected [{}], received [{}]",
                    order.getOrderNumber(), order.getAmount(), payload.getGrossAmount());
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            return;
        }

        // 5. State Machine Transition berdasarkan status dari gateway
        // 5. State Machine Transition based on gateway transaction status
        String status = payload.getTransactionStatus().toLowerCase();
        switch (status) {
            case "capture":
            case "settlement":
            case "paid":
                order.setStatus(OrderStatus.PAID);
                log.info("Order [{}] successfully marked as PAID", order.getOrderNumber());
                break;
            case "deny":
            case "cancel":
            case "failed":
                order.setStatus(OrderStatus.FAILED);
                log.warn("Order [{}] marked as FAILED due to status: [{}]", order.getOrderNumber(), status);
                break;
            case "expire":
                order.setStatus(OrderStatus.EXPIRED);
                log.info("Order [{}] marked as EXPIRED", order.getOrderNumber());
                break;
            default:
                log.info("Webhook received non-terminal status [{}] for order [{}]. No update applied.",
                        status, order.getOrderNumber());
                break;
        }

        orderRepository.save(order);
    }
}
