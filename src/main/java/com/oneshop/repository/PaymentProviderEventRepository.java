package com.oneshop.repository;

import com.oneshop.entity.PaymentProvider;
import com.oneshop.entity.PaymentProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentProviderEventRepository extends JpaRepository<PaymentProviderEvent, Long> {

    Optional<PaymentProviderEvent> findByProviderAndEventKey(PaymentProvider provider, String eventKey);
}
