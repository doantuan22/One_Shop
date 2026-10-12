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
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, VisibilityStatus status);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<Review> findByOrderItemId(Long orderItemId);

    boolean existsByUserIdAndOrderItemId(Long userId, Long orderItemId);

    /** Newest visible reviews of a Product, bounded by the Pageable; the reviewer is loaded with them. */
    @EntityGraph(attributePaths = {"user", "order", "order.store"})
    Page<Review> findByProductIdAndStatusOrderByCreatedAtDescIdDesc(Long productId, VisibilityStatus status,
                                                                   Pageable pageable);

    long countByProductIdAndStatus(Long productId, VisibilityStatus status);

    @Query("select avg(r.rating) from Review r where r.product.id = :productId and r.status = :status")
    Double averageRating(@Param("productId") Long productId, @Param("status") VisibilityStatus status);

    /** Of the given Products, the ids the user has already reviewed (whatever the review status). */
    @Query("select r.product.id from Review r where r.user.id = :userId and r.product.id in :productIds")
    List<Long> findReviewedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    @EntityGraph(attributePaths = {"user", "product", "order", "order.store"})
    Page<Review> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /**
     * Visible reviews whose Order belongs to one of the given Stores. The Store ids come from the Staff assignments,
     * never from a request. The inner join leaves out reviews that have no Order (only Admin sees those).
     */
    @EntityGraph(attributePaths = {"user", "product", "order", "order.store"})
    @Query(value = "select r from Review r join r.order o where o.store.id in :storeIds and r.status = :status"
            + " order by r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Review r join r.order o where o.store.id in :storeIds and r.status = :status")
    Page<Review> findByOrderStoreIds(@Param("storeIds") Collection<Long> storeIds,
                                     @Param("status") VisibilityStatus status, Pageable pageable);

    /** One review with its Order and Store, for the reply authorization check. */
    @Query("select r from Review r left join fetch r.order o left join fetch o.store where r.id = :id")
    Optional<Review> findForReply(@Param("id") Long id);
}
