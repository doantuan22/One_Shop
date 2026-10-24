package com.oneshop.repository;

import com.oneshop.entity.Review;
import com.oneshop.entity.VisibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, VisibilityStatus status);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<Review> findByOrderItemId(Long orderItemId);

    boolean existsByUserIdAndOrderItemId(Long userId, Long orderItemId);

    boolean existsByOrderItemId(Long orderItemId);

    @Query("select r.orderItem.id from Review r where r.user.id = :userId and r.orderItem.id in :itemIds")
    List<Long> findReviewedOrderItemIds(@Param("userId") Long userId, @Param("itemIds") Collection<Long> itemIds);

    /** Newest visible reviews of a Product, bounded by the Pageable; the reviewer is loaded with them. */
    @EntityGraph(attributePaths = {"user", "product", "order", "orderItem.order.user", "orderItem.fulfillment.store",
            "orderItem.fulfillment.order", "orderItem.storeProduct.product", "orderItem.storeProduct.store", "reviewReply"})
    Page<Review> findByProductIdAndStatusOrderByCreatedAtDescIdDesc(Long productId, VisibilityStatus status,
                                                                   Pageable pageable);

    long countByProductIdAndStatus(Long productId, VisibilityStatus status);

    @Query("select avg(r.rating) from Review r where r.product.id = :productId and r.status = :status")
    Double averageRating(@Param("productId") Long productId, @Param("status") VisibilityStatus status);

    /** Of the given Products, the ids the user has already reviewed (whatever the review status). */
    @Query("select r.product.id from Review r where r.user.id = :userId and r.product.id in :productIds")
    List<Long> findReviewedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    @EntityGraph(attributePaths = {"user", "product", "order", "orderItem.order.user", "orderItem.fulfillment.store",
            "orderItem.fulfillment.order", "orderItem.storeProduct.product", "orderItem.storeProduct.store", "reviewReply"})
    Page<Review> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /**
     * Dynamic admin moderation list (text / visibility / replied filters). Redeclared with the same
     * {@link EntityGraph} as the unfiltered page view so moderation keeps identical eager loading.
     */
    @EntityGraph(attributePaths = {"user", "product", "order", "orderItem.order.user", "orderItem.fulfillment.store",
            "orderItem.fulfillment.order", "orderItem.storeProduct.product", "orderItem.storeProduct.store", "reviewReply"})
    Page<Review> findAll(Specification<Review> spec, Pageable pageable);

    long countByStatus(VisibilityStatus status);

    /** Effective "chưa phản hồi": neither the 1--1 reply record nor any legacy reply column exists. */
    long countByReviewReplyIsNullAndReplyIsNull();

    /** SQL-scoped provenance; both content and count exclude untraceable/mismatched historical reviews. */
    String STAFF_SOURCE = " from Review r join r.orderItem oi join oi.order o join oi.fulfillment f"
            + " join oi.storeProduct sp where f.store.id in :storeIds and r.status = :status"
            + " and f.order.id = o.id and sp.store.id = f.store.id and r.user.id = o.user.id"
            + " and r.product.id = sp.product.id and (r.order is null or r.order.id = o.id)"
            + " and f.status = com.oneshop.entity.OrderFulfillmentStatus.COMPLETED"
            + " and o.orderStatus <> com.oneshop.entity.OrderStatus.CANCELLED"
            + " and o.paymentStatus = com.oneshop.entity.OrderPaymentStatus.PAID";

    @EntityGraph(attributePaths = {"user", "product", "order", "orderItem.order.user", "orderItem.fulfillment.store",
            "orderItem.fulfillment.order", "orderItem.storeProduct.product", "orderItem.storeProduct.store", "reviewReply"})
    @Query(value = "select r" + STAFF_SOURCE + " order by r.createdAt desc, r.id desc",
            countQuery = "select count(r)" + STAFF_SOURCE)
    Page<Review> findByOrderStoreIds(@Param("storeIds") Collection<Long> storeIds,
                                     @Param("status") VisibilityStatus status, Pageable pageable);

    @Query("select r from Review r where r.id = :id")
    Optional<Review> findForReply(@Param("id") Long id);

    /** Lock the review before reading its status or its optional Reply (also used by Admin moderation). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);
}
