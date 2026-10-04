package com.oneshop.repository;

import com.oneshop.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /** Inventory history of one StoreProduct, newest first. */
    Page<InventoryMovement> findByStoreProductIdOrderByCreatedAtDesc(Long storeProductId, Pageable pageable);
}
