package com.alfaizunawebid.baseapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alfaizunawebid.baseapp.model.PaymentTransactionLog;

public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLog, Long> {
}
