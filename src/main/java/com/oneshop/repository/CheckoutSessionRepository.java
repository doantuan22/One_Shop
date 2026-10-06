package com.oneshop.repository;

import com.oneshop.entity.CheckoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {

    List<CheckoutSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** A checkout addressed through its owner, so the id of another customer's checkout finds nothing. */
    Optional<CheckoutSession> findByIdAndUserEmail(Long id, String email);
}
