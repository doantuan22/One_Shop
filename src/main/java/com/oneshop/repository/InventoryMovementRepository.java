package com.oneshop.repository;

import com.oneshop.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Collection;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /** Inventory history of one StoreProduct, newest first. */
    Page<InventoryMovement> findByStoreProductIdOrderByCreatedAtDesc(Long storeProductId, Pageable pageable);

    /** Staff history: both resource and actual Store predicate; retain system movements with nullable staff. */
    @EntityGraph(attributePaths = "staff")
    Page<InventoryMovement> findByStoreProductIdAndStoreProductStoreIdInOrderByCreatedAtDescIdDesc(
            Long storeProductId, Collection<Long> assignedStoreIds, Pageable pageable);
}
