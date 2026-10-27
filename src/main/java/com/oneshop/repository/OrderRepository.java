package com.oneshop.repository;

import com.oneshop.entity.Order;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    String STORE_SOURCE = " (exists (select f.id from OrderFulfillment f where f.order.id=o.id and f.store.id=:storeId)"
            + " or (o.store.id=:storeId and not exists (select f.id from OrderFulfillment f where f.order.id=o.id)))";

    /**
     * Admin sees every status, including historical orders of inactive Stores. All filters are optional: chain-wide
     * Store part, free text (exact Order id or a contains-pattern over customer name / e-mail, {@code !} as escape),
     * order status, payment status, fulfillment type and a createdAt range ({@code toExclusive} excludes).
     */
    @EntityGraph(attributePaths = {"store", "user"})
    @Query("select o from Order o where (:storeId is null or exists (select f.id from OrderFulfillment f "
            + "where f.order.id=o.id and f.store.id=:storeId) or (o.store.id=:storeId and not exists "
            + "(select f.id from OrderFulfillment f where f.order.id=o.id)))"
            + " and (:q = '' or cast(o.id as string) = :q or lower(o.user.fullName) like :like escape '!'"
            + " or lower(o.user.email) like :like escape '!')"
            + " and (:orderStatus is null or o.orderStatus = :orderStatus)"
            + " and (:paymentStatus is null or o.paymentStatus = :paymentStatus)"
            + " and (:type is null or o.fulfillmentType = :type)"
            + " and (:from is null or o.createdAt >= :from) and (:toExclusive is null or o.createdAt < :toExclusive)"
            + " order by o.createdAt desc, o.id desc")
    Page<Order> searchAdmin(@Param("storeId") Long storeId, @Param("q") String q, @Param("like") String like,
            @Param("orderStatus") OrderStatus orderStatus, @Param("paymentStatus") OrderPaymentStatus paymentStatus,
            @Param("type") FulfillmentType type, @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive, Pageable pageable);

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

    // ---- Payment case classification (Admin list, case detail, Dashboard). Mirrors PaymentCaseCategory. ----

    String ONLINE = "o.paymentMethod = com.oneshop.entity.PaymentMethod.ONLINE";
    String PENDING = "o.orderStatus = com.oneshop.entity.OrderStatus.PENDING_PAYMENT";
    /** A collected payment whose simulated refund failed or never finished. */
    String REFUND_OPEN = "(exists (select 1 from Payment p where p.order = o and p.status = com.oneshop.entity.PaymentStatus.SUCCESS"
            + " and p.refundStatus in (com.oneshop.entity.PaymentRefundStatus.SIMULATION_FAILED,"
            + " com.oneshop.entity.PaymentRefundStatus.PENDING_SIMULATION, com.oneshop.entity.PaymentRefundStatus.MANUAL_REQUESTED))"
            + " or exists (select 1 from PaymentRefund r where r.order = o and r.status in ("
            + "com.oneshop.entity.PaymentRefundStatus.SIMULATION_FAILED, com.oneshop.entity.PaymentRefundStatus.PENDING_SIMULATION,"
            + " com.oneshop.entity.PaymentRefundStatus.MANUAL_REQUESTED)))";
    /** An attempt the gateway answered UNKNOWN to (MockLedger marks it with the ~UNKNOWN suffix while still PENDING). */
    String IN_DOUBT = "exists (select 1 from Payment p where p.order = o and p.status = com.oneshop.entity.PaymentStatus.PENDING"
            + " and p.transactionCode like '%~UNKNOWN')";
    /** Needs an Administrator: NEEDS_ATTENTION (any method), or ONLINE overdue while still pending, or an open refund. */
    String ATTENTION = "(o.orderStatus = com.oneshop.entity.OrderStatus.NEEDS_ATTENTION or (" + ONLINE + " and (("
            + PENDING + " and o.createdAt <= :cutoff) or " + REFUND_OPEN + ")))";
    String RECONCILING = "(" + ONLINE + " and " + PENDING + " and o.createdAt > :cutoff and " + IN_DOUBT + ")";
    String WAITING = "(" + ONLINE + " and " + PENDING + " and o.createdAt > :cutoff and not " + IN_DOUBT + ")";
    String VIEW_FILTER = "(:view = 'all'"
            + " or (:view = 'attention' and " + ATTENTION + ")"
            + " or (:view = 'reconciling' and " + RECONCILING + ")"
            + " or (:view = 'waiting' and " + WAITING + ")"
            + " or (:view = 'paid' and o.paymentStatus = com.oneshop.entity.OrderPaymentStatus.PAID and not " + ATTENTION + ")"
            + " or (:view = 'refunded' and o.paymentStatus = com.oneshop.entity.OrderPaymentStatus.REFUNDED and not " + ATTENTION + ")"
            + " or (:view = 'failed' and o.orderStatus = com.oneshop.entity.OrderStatus.CANCELLED and o.paymentStatus in ("
            + "com.oneshop.entity.OrderPaymentStatus.UNPAID, com.oneshop.entity.OrderPaymentStatus.FAILED) and not " + ATTENTION + "))";

    /**
     * Admin payment list: ONLINE Orders of one {@code view} (attention, reconciling, waiting, paid, refunded, failed or
     * all), optionally searched by Order id or customer name / e-mail ({@code like} is a lower-case pattern using
     * {@code !} as escape character, {@code q} the raw id text; both empty for no search) and optionally limited to a
     * createdAt range ({@code toExclusive} excludes). Read only.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("select o from Order o where " + ONLINE + " and " + VIEW_FILTER
            + " and (:from is null or o.createdAt >= :from) and (:toExclusive is null or o.createdAt < :toExclusive)"
            + " and (:q = '' or cast(o.id as string) = :q"
            + " or lower(o.user.email) like :like escape '!' or lower(o.user.fullName) like :like escape '!')"
            + " order by o.createdAt desc, o.id desc")
    Page<Order> searchPaymentCases(@Param("view") String view, @Param("q") String q, @Param("like") String like,
                                   @Param("cutoff") java.time.LocalDateTime cutoff,
                                   @Param("from") java.time.LocalDateTime from,
                                   @Param("toExclusive") java.time.LocalDateTime toExclusive, Pageable pageable);

    @Query("select count(o) from Order o where " + ONLINE + " and " + VIEW_FILTER)
    long countPaymentCases(@Param("view") String view, @Param("cutoff") java.time.LocalDateTime cutoff);

    /** Admin order list restricted to the Orders that need an Administrator (same rule as the Dashboard number). */
    @EntityGraph(attributePaths = {"store", "user"})
    @Query("select o from Order o where " + ATTENTION + " and (:storeId is null or" + STORE_SOURCE + ")"
            + " order by o.createdAt desc, o.id desc")
    Page<Order> searchAdminAttention(@Param("storeId") Long storeId, @Param("cutoff") java.time.LocalDateTime cutoff,
                                     Pageable pageable);

    /** Dashboard: Orders (each once) that need an Administrator, chain-wide. */
    @Query("select count(o) from Order o where " + ATTENTION)
    long countAttention(@Param("cutoff") java.time.LocalDateTime cutoff);

    /** Dashboard: ONLINE Orders waiting for the gateway evidence, chain-wide. */
    @Query("select count(o) from Order o where " + RECONCILING)
    long countReconciling(@Param("cutoff") java.time.LocalDateTime cutoff);

    /** Dashboard per Store: Orders that need an Administrator and have a part at this Store (each Order once per Store). */
    @Query("select count(o) from Order o where" + STORE_SOURCE + " and " + ATTENTION)
    long countAttentionByStoreId(@Param("storeId") Long storeId, @Param("cutoff") java.time.LocalDateTime cutoff);
}
