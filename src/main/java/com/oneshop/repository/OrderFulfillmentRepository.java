package com.oneshop.repository;

import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderFulfillmentRepository extends JpaRepository<OrderFulfillment, Long> {

    List<OrderFulfillment> findByOrderId(Long orderId);

    List<OrderFulfillment> findByOrderIdOrderByAssignedAtAscIdAsc(Long orderId);

    Optional<OrderFulfillment> findByOrderIdAndStoreId(Long orderId, Long storeId);

    @EntityGraph(attributePaths = {"order", "store"})
    Page<OrderFulfillment> findByStoreIdAndStatusIn(Long storeId, Collection<OrderFulfillmentStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "store"})
    Page<OrderFulfillment> findByStoreIdInAndStatusIn(Collection<Long> storeIds, Collection<OrderFulfillmentStatus> statuses, Pageable pageable);

    long countByStoreIdAndStatusIn(Long storeId, Collection<OrderFulfillmentStatus> statuses);
}

