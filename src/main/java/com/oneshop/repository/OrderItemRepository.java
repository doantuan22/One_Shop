package com.oneshop.repository;

import com.oneshop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** Item snapshots of several Orders in one query. */
    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);
}
