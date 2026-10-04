package com.oneshop.repository;

import com.oneshop.entity.Review;
import com.oneshop.entity.VisibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, VisibilityStatus status);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
