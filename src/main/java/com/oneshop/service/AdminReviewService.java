package com.oneshop.service;

import com.oneshop.dto.response.AdminReviewResponse;
import com.oneshop.entity.Review;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.ReviewRepository;
import com.oneshop.service.review.ReviewReadMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminReviewService {
    private final ReviewRepository reviews;
    public AdminReviewService(ReviewRepository reviews) { this.reviews = reviews; }
    public Page<AdminReviewResponse> getReviews(int page) {
        return reviews.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(0, page), 20))
                .map(ReviewReadMapper::adminReview);
    }

    /**
     * Filtered moderation list. {@code q} matches customer identity or product coding, {@code status}
     * narrows visibility and {@code replied} (null = all) narrows the staff-answer state.
     */
    public Page<AdminReviewResponse> getReviews(int page, String q, VisibilityStatus status, Boolean replied) {
        var spec = adminSpec(q, status, replied);
        var pageable = PageRequest.of(Math.max(0, page), 20);
        return (spec == null
                ? reviews.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : reviews.findAll(spec, pageable)).map(ReviewReadMapper::adminReview);
    }

    private Specification<Review> adminSpec(String q, VisibilityStatus status, Boolean replied) {
        boolean hasText = q != null && !q.isBlank();
        if (status == null && replied == null && !hasText) return null;
        return (root, query, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            if (status != null) ands.add(cb.equal(root.get("status"), status));
            if (replied != null) {
                if (replied) {
                    ands.add(cb.or(cb.isNotNull(root.get("reviewReply")), cb.isNotNull(root.get("reply"))));
                } else {
                    ands.add(cb.isNull(root.get("reviewReply")));
                    ands.add(cb.isNull(root.get("reply")));
                }
            }
            if (hasText) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                ands.add(cb.or(
                        cb.like(cb.lower(root.get("user").get("email")), like),
                        cb.like(cb.lower(root.get("user").get("fullName")), like),
                        cb.like(cb.lower(root.get("product").get("sku")), like),
                        cb.like(cb.lower(root.get("product").get("name")), like)));
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
    }

    /** Live dashboard counts for the moderation screen header. */
    public record AdminReviewStats(long total, long unanswered, long hidden) { }
    public AdminReviewStats stats() {
        return new AdminReviewStats(reviews.count(), reviews.countByReviewReplyIsNullAndReplyIsNull(),
                reviews.countByStatus(VisibilityStatus.HIDDEN));
    }

    @Transactional
    public void setStatus(Long id, @NotNull VisibilityStatus status) {
        var review = reviews.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá."));
        review.setStatus(status);
        reviews.flush();
    }
}
