package com.alfaizunawebid.baseapp.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) untuk payload webhook dari payment provider.
 * Mengabaikan field tambahan yang tidak dikenal agar tidak memicu error deserialization.
 * ---
 * Data Transfer Object (DTO) for payment provider webhook payloads.
 * Ignores unknown properties to ensure backward/forward compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    @NotBlank(message = "transaction_id is required")
    @JsonProperty("transaction_id")
    private String transactionId;

    @NotBlank(message = "order_number is required")
    @JsonProperty("order_number")
    private String orderNumber;

    @NotNull(message = "gross_amount is required")
    @JsonProperty("gross_amount")
    private BigDecimal grossAmount;

    @JsonProperty("payment_type")
    private String paymentType;

    @NotBlank(message = "transaction_status is required")
    @JsonProperty("transaction_status")
    private String transactionStatus; // contoh: settlement, capture, pending, deny, expire, cancel
}
