package com.oneshop.repository;

import com.oneshop.entity.Review;
import com.oneshop.entity.VisibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, VisibilityStatus status);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /** Newest visible reviews of a Product, bounded by the Pageable; the reviewer is loaded with them. */
    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductIdAndStatusOrderByCreatedAtDescIdDesc(Long productId, VisibilityStatus status,
                                                                   Pageable pageable);

    long countByProductIdAndStatus(Long productId, VisibilityStatus status);

    @Query("select avg(r.rating) from Review r where r.product.id = :productId and r.status = :status")
    Double averageRating(@Param("productId") Long productId, @Param("status") VisibilityStatus status);

    /** Of the given Products, the ids the user has already reviewed (whatever the review status). */
    @Query("select r.product.id from Review r where r.user.id = :userId and r.product.id in :productIds")
    List<Long> findReviewedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
