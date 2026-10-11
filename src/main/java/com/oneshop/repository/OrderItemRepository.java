package com.oneshop.repository;

import com.oneshop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** Item snapshots of several Orders in one query. */
    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    /** True when the user has a COMPLETED Order of their own that contains the Product (a review precondition). */
    @Query("select count(oi) > 0 from OrderItem oi"
            + " where oi.order.user.id = :userId and oi.order.orderStatus = com.oneshop.entity.OrderStatus.COMPLETED"
            + " and oi.storeProduct.product.id = :productId")
    boolean hasCompletedPurchase(@Param("userId") Long userId, @Param("productId") Long productId);

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
