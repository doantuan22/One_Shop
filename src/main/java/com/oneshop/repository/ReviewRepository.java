package com.oneshop.repository;

import com.oneshop.entity.Review;
import com.oneshop.entity.VisibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, VisibilityStatus status);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
