package com.oneshop.service.review;

import com.oneshop.entity.Review;
import com.oneshop.entity.ReviewReply;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.ReviewReplyRepository;
import com.oneshop.repository.ReviewRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.StaffStoreScopeService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Review lock serializes Reply creation/editing and moderation, including the absent-Reply case. */
@Service
public class ReviewReplyService {
    private final ReviewRepository reviews;
    private final ReviewReplyRepository replies;
    private final UserRepository users;
    private final StaffStoreScopeService scope;

    public ReviewReplyService(ReviewRepository reviews, ReviewReplyRepository replies, UserRepository users,
                              StaffStoreScopeService scope) {
        this.reviews = reviews; this.replies = replies; this.users = users; this.scope = scope;
    }

    @Transactional
    @PreAuthorize("hasRole('STAFF')")
    public void reply(Long id, String text) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var actor = users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.STAFF)
                .orElseThrow(() -> new AccessDeniedException("Chỉ nhân viên được phân công mới được phản hồi."));
        Review review = id == null ? null : reviews.findByIdForUpdate(id).orElse(null);
        if (review == null) throw new ResourceNotFoundException("Không tìm thấy đánh giá.");
        requireScope(review, scope);
        String body = ReviewContent.reply(text);
        ReviewReply reply = replies.findByReviewId(id).orElseGet(ReviewReply::new);
        reply.setReview(review);
        reply.setStaff(actor);
        reply.setContent(body);
        replies.saveAndFlush(reply);
        review.setReviewReply(reply);
    }

    public static void requireScope(Review review, StaffStoreScopeService scope) {
        var item = ReviewSource.verified(review);
        if (item == null || review.getStatus() != VisibilityStatus.ACTIVE || !ReviewSource.received(item)) {
            throw new AccessDeniedException("Đánh giá không có nguồn hàng hợp lệ để phản hồi.");
        }
        scope.requireAssignedStore(item.getFulfillment().getStore().getId());
    }
}
