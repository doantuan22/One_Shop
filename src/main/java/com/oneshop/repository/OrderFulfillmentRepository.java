package com.oneshop.repository;

import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderFulfillmentRepository extends JpaRepository<OrderFulfillment, Long> {

    List<OrderFulfillment> findByOrderId(Long orderId);

    /** The Fulfillments of several Orders with their Store: one query for a checkout result. */
    @EntityGraph(attributePaths = "store")
    List<OrderFulfillment> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    List<OrderFulfillment> findByOrderIdOrderByAssignedAtAscIdAsc(Long orderId);

    Optional<OrderFulfillment> findByOrderIdAndStoreId(Long orderId, Long storeId);

    @EntityGraph(attributePaths = {"order", "store"})
    Page<OrderFulfillment> findByStoreIdAndStatusIn(Long storeId, Collection<OrderFulfillmentStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "store"})
    Page<OrderFulfillment> findByStoreIdInAndStatusIn(Collection<Long> storeIds, Collection<OrderFulfillmentStatus> statuses, Pageable pageable);

    long countByStoreIdAndStatusIn(Long storeId, Collection<OrderFulfillmentStatus> statuses);

    // ---- V3-BE-04: Fulfillment-centric Staff operations. Store ids always come from the Staff assignments. ----

    /** Scalar scope lookup (no entity is loaded, so nothing stale can enter the persistence context). */
    @Query("select f.order.id as orderId, f.store.id as storeId, f.fulfillmentType as fulfillmentType from OrderFulfillment f where f.id = :id")
    Optional<FulfillmentKey> findKeyById(@Param("id") Long id);

    /** Locks one Fulfillment row. The caller already holds the Order lock (lock order: Order, then Fulfillment). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from OrderFulfillment f where f.id = :id")
    Optional<OrderFulfillment> findByIdForUpdate(@Param("id") Long id);

    /** Locks every Fulfillment of one Order in id order, after the Order lock, for the status aggregation. */
    @Query(value = "select f.* from dbo.order_fulfillments f with "
            + "(updlock, rowlock, index(UQ_order_fulfillments_order_store), forceseek) "
            + "where f.order_id = :orderId order by f.fulfillment_id", nativeQuery = true)
    List<OrderFulfillment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /**
     * Staff queue: the Fulfillments of the assigned Stores. Collections are never empty (the caller passes every
     * value when a filter is not used) and {@code orderId = 0} means "any Order". Open work comes first, oldest
     * first; finished work follows, newest first.
     */
    @EntityGraph(attributePaths = {"order", "order.user", "store"})
    @Query(value = "select f from OrderFulfillment f where f.store.id in :storeIds and f.status in :statuses"
            + " and f.fulfillmentType in :types and (:orderId = 0 or f.order.id = :orderId)"
            + " and f.assignedAt >= :fromAt and f.assignedAt < :toAt"
            + " order by case when f.status in (com.oneshop.entity.OrderFulfillmentStatus.CONFIRMED,"
            + " com.oneshop.entity.OrderFulfillmentStatus.PREPARING, com.oneshop.entity.OrderFulfillmentStatus.PACKED,"
            + " com.oneshop.entity.OrderFulfillmentStatus.SHIPPING, com.oneshop.entity.OrderFulfillmentStatus.READY_FOR_PICKUP)"
            + " then 0 else 1 end asc, f.assignedAt asc, f.id asc",
            countQuery = "select count(f) from OrderFulfillment f where f.store.id in :storeIds and f.status in :statuses"
            + " and f.fulfillmentType in :types and (:orderId = 0 or f.order.id = :orderId)"
            + " and f.assignedAt >= :fromAt and f.assignedAt < :toAt")
    Page<OrderFulfillment> searchForStaff(@Param("storeIds") Collection<Long> storeIds,
                                          @Param("statuses") Collection<OrderFulfillmentStatus> statuses,
                                          @Param("types") Collection<com.oneshop.entity.FulfillmentType> types,
                                          @Param("orderId") long orderId,
                                          @Param("fromAt") LocalDateTime fromAt, @Param("toAt") LocalDateTime toAt,
                                          Pageable pageable);

    /** Detail read, scoped to the assigned Stores in SQL. */
    @EntityGraph(attributePaths = {"order", "order.user", "store"})
    @Query("select f from OrderFulfillment f where f.id = :id and f.store.id in :storeIds")
    Optional<OrderFulfillment> findByIdAndStoreIdIn(@Param("id") Long id, @Param("storeIds") Collection<Long> storeIds);

    /** The Fulfillments of one customer's Orders (customer order detail), with their Stores. */
    @EntityGraph(attributePaths = "store")
    @Query("select f from OrderFulfillment f where f.order.id = :orderId order by f.id asc")
    List<OrderFulfillment> findWithStoreByOrderId(@Param("orderId") Long orderId);

    /** Per Store and status counts for the dashboard, with how many of them are held by the Order's payment gate. */
    @Query("select f.store.id as storeId, f.status as status, count(f) as total,"
            + " sum(case when o.orderStatus in (com.oneshop.entity.OrderStatus.PENDING_PAYMENT,"
            + " com.oneshop.entity.OrderStatus.NEEDS_ATTENTION, com.oneshop.entity.OrderStatus.CANCELLED)"
            + " then 1 else 0 end) as blocked"
            + " from OrderFulfillment f join f.order o where f.store.id in :storeIds group by f.store.id, f.status")
    List<StatusCount> countByStoreAndStatus(@Param("storeIds") Collection<Long> storeIds);

    /** Current statuses of every Fulfillment of an Order, read from the database (aggregation input). */
    @Query("select f.status from OrderFulfillment f where f.order.id = :orderId")
    List<OrderFulfillmentStatus> findStatusesByOrderId(@Param("orderId") Long orderId);

    long countByOrderId(Long orderId);

    boolean existsByStoreIdAndPickupCode(Long storeId, String pickupCode);

    interface FulfillmentKey {
        Long getOrderId();

        Long getStoreId();

        com.oneshop.entity.FulfillmentType getFulfillmentType();
    }

    interface StatusCount {
        Long getStoreId();

        OrderFulfillmentStatus getStatus();

        long getTotal();

        long getBlocked();
    }
}
