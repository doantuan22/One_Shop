package com.oneshop.repository;

import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    Optional<Payment> findByIdAndOrderId(Long paymentId, Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByIdAsc(Long orderId, PaymentStatus status);
}
