package com.oneshop.repository;

import com.oneshop.entity.Order;
import com.oneshop.entity.FulfillmentType;
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
    String STORE_SOURCE = " (exists (select f.id from OrderFulfillment f where f.order.id=o.id and f.store.id=:storeId)"
            + " or (o.store.id=:storeId and not exists (select f.id from OrderFulfillment f where f.order.id=o.id)))";

    /** Admin sees every status, including historical orders of inactive Stores. */
    @EntityGraph(attributePaths = {"store", "user"})
    @Query("select o from Order o where (:storeId is null or exists (select f.id from OrderFulfillment f "
            + "where f.order.id=o.id and f.store.id=:storeId) or (o.store.id=:storeId and not exists "
            + "(select f.id from OrderFulfillment f where f.order.id=o.id))) order by o.createdAt desc, o.id desc")
    Page<Order> searchAdmin(@Param("storeId") Long storeId, Pageable pageable);

    @Query("select count(o) from Order o where" + STORE_SOURCE)
    long countByStoreId(@Param("storeId") Long storeId);

    @Query("select count(o) from Order o where" + STORE_SOURCE + " and o.orderStatus=:status")
    long countByStoreIdAndOrderStatus(@Param("storeId") Long storeId, @Param("status") OrderStatus status);

    Optional<Order> findByIdAndUserEmail(Long orderId, String email);

    @EntityGraph(attributePaths = "store")
    List<Order> findByUserEmailOrderByCreatedAtDescIdDesc(String email);

    /** Serialize state transitions and payment attempts/results on this Order before reading Payment or stock. */
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

    /** Store ids come exclusively from ACTIVE Staff assignments, never from browser input. */
    @EntityGraph(attributePaths = "store")
    List<Order> findByStoreIdInAndFulfillmentTypeOrderByCreatedAtDescIdDesc(Collection<Long> storeIds, FulfillmentType fulfillmentType);

    @EntityGraph(attributePaths = "store")
    Optional<Order> findByIdAndStoreIdIn(Long orderId, Collection<Long> storeIds);

    Page<Order> findByStoreIdAndOrderStatusIn(Long storeId, Collection<OrderStatus> statuses, Pageable pageable);

    @Query("select count(o) from Order o where" + STORE_SOURCE + " and o.orderStatus in :statuses")
    long countByStoreIdAndOrderStatusIn(@Param("storeId") Long storeId, @Param("statuses") Collection<OrderStatus> statuses);

    long countByStoreIdAndFulfillmentTypeAndOrderStatus(Long storeId, FulfillmentType type, OrderStatus status);

    /** Bounded, stable queue of ALL fulfillment/status types within the authenticated Staff's assignments. */
    @EntityGraph(attributePaths = {"store", "user"})
    Page<Order> findByStoreIdInOrderByCreatedAtDescIdDesc(Collection<Long> storeIds, Pageable pageable);

    /** Operational pickup queue: assigned Stores, pickup fulfillment and explicitly open states in SQL. */
    @EntityGraph(attributePaths = {"store", "user"})
    Page<Order> findByStoreIdInAndFulfillmentTypeAndOrderStatusInOrderByCreatedAtDescIdDesc(
            Collection<Long> storeIds, FulfillmentType type, Collection<OrderStatus> statuses, Pageable pageable);

    // ---- V3-BE-05: payment timeout / reconciliation candidates ----

    /**
     * ONLINE Orders a scan has to look at: still pending and past the payment cutoff, or pending with a settled-but-
     * unapplied ledger entry (a declined attempt, or a collection not applied: crash recovery), or waiting for an
     * administrator because of a payment result (UNPAID NEEDS_ATTENTION). Ids only; every decision re-reads under lock.
     */
    @Query("select o.id from Order o where o.paymentMethod = com.oneshop.entity.PaymentMethod.ONLINE and ("
            + "o.id > :afterId and ("
            + "(o.orderStatus = com.oneshop.entity.OrderStatus.PENDING_PAYMENT and (o.createdAt <= :cutoff or exists ("
            + "select 1 from Payment p where p.order = o and (p.status = com.oneshop.entity.PaymentStatus.FAILED"
            + " or (p.status = com.oneshop.entity.PaymentStatus.PENDING and p.paidAt is not null)))))"
            + " or (o.orderStatus = com.oneshop.entity.OrderStatus.NEEDS_ATTENTION"
            + " and o.paymentStatus = com.oneshop.entity.OrderPaymentStatus.UNPAID))) order by o.id")
    List<Long> findPaymentCandidateIds(@Param("cutoff") java.time.LocalDateTime cutoff,
                                       @Param("afterId") long afterId, Pageable pageable);

    /** Admin list: ONLINE Orders whose payment needs a person (or is being reconciled). */
    @EntityGraph(attributePaths = {"user"})
    @Query("select o from Order o where o.paymentMethod = com.oneshop.entity.PaymentMethod.ONLINE and ("
            + "o.orderStatus = com.oneshop.entity.OrderStatus.NEEDS_ATTENTION or (o.orderStatus = "
            + "com.oneshop.entity.OrderStatus.PENDING_PAYMENT and o.createdAt <= :cutoff) or exists ("
            + "select 1 from Payment p where p.order = o and p.status = com.oneshop.entity.PaymentStatus.SUCCESS"
            + " and p.refundStatus in (com.oneshop.entity.PaymentRefundStatus.SIMULATION_FAILED,"
            + " com.oneshop.entity.PaymentRefundStatus.PENDING_SIMULATION))) order by o.createdAt desc, o.id desc")
    org.springframework.data.domain.Page<Order> findPaymentCasesForAdmin(@Param("cutoff") java.time.LocalDateTime cutoff,
                                                                         Pageable pageable);
}
