package com.oneshop.repository;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Order history of a customer. */
    @EntityGraph(attributePaths = "store")
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findByCheckoutSessionId(Long checkoutId);

    // ---- Store-scoped queries for Staff. storeId must come from the Staff assignment, never from a request. ----

    Optional<Order> findByIdAndStoreId(Long orderId, Long storeId);

    Page<Order> findByStoreIdAndOrderStatusIn(Long storeId, Collection<OrderStatus> statuses, Pageable pageable);

    long countByStoreIdAndOrderStatusIn(Long storeId, Collection<OrderStatus> statuses);
}
