package com.oneshop.repository;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndUserEmail(Long orderId, String email);

    /** Serialize all payment attempts/results on this Order before reading any Payment or stock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** Order history of a customer. */
    @EntityGraph(attributePaths = "store")
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** A customer may only read their own Order: the user id comes from the authenticated principal. */
    Optional<Order> findByIdAndUserId(Long orderId, Long userId);

    List<Order> findByCheckoutSessionId(Long checkoutId);

    /** The Orders created by one checkout, each with its Store. */
    @EntityGraph(attributePaths = "store")
    List<Order> findByCheckoutSessionIdOrderByIdAsc(Long checkoutId);

    // ---- Store-scoped queries for Staff. storeId must come from the Staff assignment, never from a request. ----

    Optional<Order> findByIdAndStoreId(Long orderId, Long storeId);

    Page<Order> findByStoreIdAndOrderStatusIn(Long storeId, Collection<OrderStatus> statuses, Pageable pageable);

    long countByStoreIdAndOrderStatusIn(Long storeId, Collection<OrderStatus> statuses);
}
