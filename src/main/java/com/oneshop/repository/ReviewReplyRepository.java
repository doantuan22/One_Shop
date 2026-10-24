package com.oneshop.repository;

import com.oneshop.entity.ReviewReply;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {

    @EntityGraph(attributePaths = {"review", "staff"})
    Optional<ReviewReply> findByReviewId(Long reviewId);

    boolean existsByReviewId(Long reviewId);
}

