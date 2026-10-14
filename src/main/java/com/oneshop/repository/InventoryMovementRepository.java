package com.oneshop.repository;

import com.oneshop.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /** True when stock of this Order was already put back (the guard against restoring twice). */
    boolean existsByReferenceOrderIdAndType(Long orderId, com.oneshop.entity.InventoryMovementType type);

    /** Called after the Order lock; skip unrelated uncommitted inserts during an unindexed FK scan. */
    @Query(value = "select cast(case when exists (select 1 from dbo.inventory_movements with "
            + "(readpast, readcommittedlock, rowlock) where reference_order_id=:orderId and type='CANCEL_ORDER') "
            + "then 1 else 0 end as bit)", nativeQuery = true)
    boolean hasRestorationForLockedOrder(@Param("orderId") Long orderId);

    long countByReferenceOrderIdAndType(Long orderId, com.oneshop.entity.InventoryMovementType type);

    /** Inventory history of one StoreProduct, newest first. */
    Page<InventoryMovement> findByStoreProductIdOrderByCreatedAtDesc(Long storeProductId, Pageable pageable);

    /** Staff history: both resource and actual Store predicate; retain system movements with nullable staff. */
    @EntityGraph(attributePaths = "staff")
    Page<InventoryMovement> findByStoreProductIdAndStoreProductStoreIdInOrderByCreatedAtDescIdDesc(
            Long storeProductId, Collection<Long> assignedStoreIds, Pageable pageable);
}
