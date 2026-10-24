package com.oneshop.repository;

import com.oneshop.entity.OrderFulfillmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderFulfillmentStatusHistoryRepository extends JpaRepository<OrderFulfillmentStatusHistory, Long> {

    List<OrderFulfillmentStatusHistory> findByFulfillmentIdOrderByChangedAtAscIdAsc(Long fulfillmentId);
}

