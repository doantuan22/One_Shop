package com.oneshop.repository;

import com.oneshop.entity.OrderReturnImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderReturnImageRepository extends JpaRepository<OrderReturnImage, Long> {

    List<OrderReturnImage> findByOrderReturnIdOrderBySortOrderAscIdAsc(Long returnId);
}
