package com.oneshop.repository;

import com.oneshop.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Scalar lookup before taking the Order lock; never loads a stale Order into the persistence context. */
    @Query("select oi.order.id as orderId, oi.order.user.id as userId from OrderItem oi where oi.id = :id")
    Optional<ReviewKey> findReviewKey(@Param("id") Long id);

    interface ReviewKey {
        Long getOrderId();
        Long getUserId();
    }

    @EntityGraph(attributePaths = {"order.user", "fulfillment.order", "fulfillment.store", "storeProduct.product", "storeProduct.store"})
    @Query("select oi from OrderItem oi where oi.id = :id")
    Optional<OrderItem> findForReview(@Param("id") Long id);

    String RECEIVED = " from OrderItem oi join oi.order o join oi.fulfillment f join oi.storeProduct sp"
            + " where o.user.id = :userId and f.order.id = o.id and f.store.id = sp.store.id"
            + " and f.status = com.oneshop.entity.OrderFulfillmentStatus.COMPLETED"
            + " and o.orderStatus <> com.oneshop.entity.OrderStatus.CANCELLED"
            + " and o.paymentStatus = com.oneshop.entity.OrderPaymentStatus.PAID";

    @EntityGraph(attributePaths = {"order.user", "fulfillment.order", "fulfillment.store", "storeProduct.product", "storeProduct.store"})
    @Query("select oi" + RECEIVED + " and o.id = :orderId order by oi.id")
    List<OrderItem> findReceivedReviewItems(@Param("userId") Long userId, @Param("orderId") Long orderId);

    @EntityGraph(attributePaths = {"order.user", "fulfillment.order", "fulfillment.store", "storeProduct.product", "storeProduct.store"})
    @Query("select oi" + RECEIVED + " and sp.product.id = :productId order by oi.id desc")
    List<OrderItem> findReceivedReviewItemsForProduct(@Param("userId") Long userId,
                                                    @Param("productId") Long productId, Pageable pageable);

    List<OrderItem> findByOrderId(Long orderId);

    /** Item snapshots of Fulfillments (Staff queue/detail, customer detail), one query. */
    List<OrderItem> findByFulfillmentIdInOrderByIdAsc(Collection<Long> fulfillmentIds);

    /** Item snapshots of several Orders in one query. */
    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    /** True when the user has a COMPLETED Order of their own that contains the Product (a review precondition). */
    @Query("select count(oi) > 0 from OrderItem oi"
            + " where oi.order.user.id = :userId and oi.order.orderStatus = com.oneshop.entity.OrderStatus.COMPLETED"
            + " and oi.storeProduct.product.id = :productId")
    boolean hasCompletedPurchase(@Param("userId") Long userId, @Param("productId") Long productId);

    /** True when this Order belongs to the user, is COMPLETED and contains the Product. */
    @Query("select count(oi) > 0 from OrderItem oi"
            + " where oi.order.id = :orderId and oi.order.user.id = :userId"
            + " and oi.order.orderStatus = com.oneshop.entity.OrderStatus.COMPLETED"
            + " and oi.storeProduct.product.id = :productId")
    boolean isCompletedPurchase(@Param("orderId") Long orderId, @Param("userId") Long userId,
                                @Param("productId") Long productId);

    /**
     * Ids of the user's COMPLETED Orders that contain the Product, newest completion first: the completion moment is
     * the latest COMPLETED row of order_status_history (the Order's updated_at when it has none), ties by Order id.
     * Pass {@code PageRequest.of(0, 1)} to get the single most recent one.
     */
    @Query("select o.id from OrderItem oi join oi.order o"
            + " where o.user.id = :userId and o.orderStatus = com.oneshop.entity.OrderStatus.COMPLETED"
            + " and oi.storeProduct.product.id = :productId"
            + " order by coalesce((select max(h.changedAt) from OrderStatusHistory h where h.order.id = o.id"
            + " and h.newStatus = com.oneshop.entity.OrderStatus.COMPLETED), o.updatedAt) desc, o.id desc")
    List<Long> findCompletedOrderIdsNewestFirst(@Param("userId") Long userId, @Param("productId") Long productId,
                                                Pageable pageable);

    /**
     * The Products of one COMPLETED Order, only when that Order belongs to the given user, with the name frozen on the
     * Order line (never the current catalog name).
     */
    @Query("select sp.product.id as productId, oi.productName as productName from OrderItem oi join oi.storeProduct sp"
            + " where oi.order.id = :orderId and oi.order.user.id = :userId"
            + " and oi.order.orderStatus = com.oneshop.entity.OrderStatus.COMPLETED order by oi.id")
    List<PurchasedProduct> findProductsOfCompletedOrder(@Param("orderId") Long orderId, @Param("userId") Long userId);

    interface PurchasedProduct {
        Long getProductId();

        String getProductName();
    }
}
