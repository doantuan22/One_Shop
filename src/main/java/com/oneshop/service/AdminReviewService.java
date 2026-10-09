package com.oneshop.service;

import com.oneshop.dto.response.AdminReviewResponse;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.ReviewRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminReviewService {
    private final ReviewRepository reviews;
    public AdminReviewService(ReviewRepository reviews) { this.reviews = reviews; }
    public Page<AdminReviewResponse> getReviews(int page) {
        return reviews.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(0, page), 20)).map(r ->
                new AdminReviewResponse(r.getId(), r.getUser().getFullName(), r.getUser().getEmail(), r.getProduct().getId(),
                        r.getProduct().getSku(), r.getProduct().getName(), r.getRating(), r.getComment(), r.getStatus(), r.getCreatedAt()));
    }
    @Transactional
    public void setStatus(Long id, @NotNull VisibilityStatus status) {
        var review = reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá."));
        review.setStatus(status);
        reviews.flush();
    }
}
