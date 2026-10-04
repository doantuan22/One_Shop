package com.oneshop.repository;

import com.oneshop.entity.CheckoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {

    List<CheckoutSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
