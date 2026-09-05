package com.alfaizunawebid.baseapp.model;

/**
 * Status siklus hidup sebuah pesanan (State Machine).
 * ---
 * Lifecycle statuses of an order (State Machine).
 */
public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    EXPIRED
}
