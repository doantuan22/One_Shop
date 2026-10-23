package com.oneshop.repository;

import com.oneshop.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
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

    /**
     * Central Staff movement log across the assigned Stores: newest first, optional text (SKU / product / Store /
     * note / exact Order id), movement type, recording Staff and a createdAt range; never a single-source filter —
     * the scope always comes from the assigned {@code storeIds}.
     */
    String LOG_SOURCE = " from InventoryMovement m"
            + " where m.storeProduct.store.id in :storeIds"
            + " and (:type is null or m.type = :type)"
            + " and (:staffId is null or m.staff.id = :staffId)"
            + " and (:q = '' or lower(m.storeProduct.product.sku) like :like escape '!'"
            + "   or lower(m.storeProduct.product.name) like :like escape '!'"
            + "   or lower(m.storeProduct.store.name) like :like escape '!'"
            + "   or lower(m.note) like :like escape '!'"
            + "   or cast(m.referenceOrder.id as string) = :q)"
            + " and (:fromAt is null or m.createdAt >= :fromAt)"
            + " and (:toAt is null or m.createdAt < :toAt)";

    @EntityGraph(attributePaths = {"storeProduct.product", "storeProduct.store", "staff", "referenceOrder"})
    @Query(value = "select m" + LOG_SOURCE + " order by m.createdAt desc, m.id desc",
            countQuery = "select count(m)" + LOG_SOURCE)
    Page<InventoryMovement> searchLog(@Param("storeIds") Collection<Long> storeIds,
                                      @Param("type") com.oneshop.entity.InventoryMovementType type,
                                      @Param("staffId") Long staffId, @Param("q") String q,
                                      @Param("like") String like, @Param("fromAt") LocalDateTime fromAt,
                                      @Param("toAt") LocalDateTime toAt, Pageable pageable);
}
