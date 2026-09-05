package com.alfaizunawebid.baseapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alfaizunawebid.baseapp.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
}
