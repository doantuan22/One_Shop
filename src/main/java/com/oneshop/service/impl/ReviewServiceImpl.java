package com.oneshop.service.impl;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;
import com.oneshop.dto.response.StaffReviewResponse;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.Review;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.ReviewRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.ReviewService;
import com.oneshop.service.StaffStoreScopeService;
import com.oneshop.service.review.ReviewContent;
import com.oneshop.service.review.ReviewReadMapper;
import com.oneshop.service.review.ReviewReplyService;
import com.oneshop.service.review.ReviewSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {
    private static final int PAGE_SIZE = 20;
    private final ReviewRepository reviews;
    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final UserRepository users;
    private final StaffStoreScopeService scope;
    private final ReviewReplyService replies;

    public ReviewServiceImpl(ReviewRepository reviews, OrderItemRepository items, OrderRepository orders,
                              UserRepository users, StaffStoreScopeService scope, ReviewReplyService replies) {
        this.reviews = reviews; this.items = items; this.orders = orders;
        this.users = users; this.scope = scope; this.replies = replies;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public ReviewResponse submit(String email, ReviewRequest request, Long orderId) {
        User customer = requireCustomer(email);
        if (request == null || request.orderItemId() == null || request.orderItemId() <= 0) {
            throw new BadRequestException("orderItemId", "Vui lòng chọn dòng hàng đã nhận để đánh giá.");
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new BadRequestException("rating", "Đánh giá từ 1 đến 5 sao.");
        }
        String comment = ReviewContent.comment(request.comment());
        var key = items.findReviewKey(request.orderItemId()).filter(k -> Objects.equals(k.getUserId(), customer.getId()))
                .orElseThrow(ReviewServiceImpl::notFound);
        if (orderId != null && !Objects.equals(orderId, key.getOrderId())) throw notFound();
        // No Order has been loaded before this parent lock. Receipt/payment facts are re-read after the lock,
        // the same first lock used by Fulfillment and cancellation. Duplicate submissions serialize here.
        var order = orders.findByIdForUpdate(key.getOrderId()).orElseThrow(ReviewServiceImpl::notFound);
        OrderItem item = items.findForReview(request.orderItemId()).orElseThrow(ReviewServiceImpl::notFound);
        if (!Objects.equals(order.getUser().getId(), customer.getId())
                || !Objects.equals(item.getOrder().getId(), order.getId())) throw notFound();
        if (!ReviewSource.received(item)) {
            throw new BadRequestException("orderItemId", "Chỉ có thể đánh giá phần hàng đã nhận và thanh toán hợp lệ.");
        }
        var product = item.getStoreProduct().getProduct();
        if (request.productId() != null && !Objects.equals(request.productId(), product.getId())) throw notFound();
        if (reviews.existsByOrderItemId(item.getId())) {
            throw new BadRequestException("Bạn đã đánh giá dòng hàng này rồi.");
        }
        Review review = new Review();
        review.setUser(customer);
        review.setProduct(product);
        review.setOrderItem(item);
        review.setOrder(order);
        review.setRating(request.rating());
        review.setComment(comment);
        review.setStatus(VisibilityStatus.ACTIVE);
        try {
            reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("Không thể lưu đánh giá trùng hoặc có nguồn hàng không hợp lệ.");
        }
        return ReviewReadMapper.publicReview(review);
    }

    @Override
    public ProductReviewsResponse getProductReviews(Long productId) {
        if (productId == null) return ProductReviewsResponse.empty();
        long count = reviews.countByProductIdAndStatus(productId, VisibilityStatus.ACTIVE);
        if (count == 0) return ProductReviewsResponse.empty();
        Double average = reviews.averageRating(productId, VisibilityStatus.ACTIVE);
        return new ProductReviewsResponse(count, average == null ? 0 : average,
                reviews.findByProductIdAndStatusOrderByCreatedAtDescIdDesc(productId, VisibilityStatus.ACTIVE,
                        PageRequest.of(0, PAGE_SIZE)).map(ReviewReadMapper::publicReview).getContent());
    }

    @Override
    public boolean canReview(String email, Long productId) {
        return getProductReviewItems(email, productId).stream().anyMatch(ReviewableItemResponse::reviewable);
    }

    @Override
    public List<ReviewableItemResponse> getProductReviewItems(String email, Long productId) {
        if (email == null || productId == null) return List.of();
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !email.equals(auth.getName())) {
            throw new AccessDeniedException("Chỉ được xem dòng hàng đã mua của tài khoản đang đăng nhập.");
        }
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
                .map(u -> reviewItems(u.getId(), items.findReceivedReviewItemsForProduct(u.getId(), productId,
                        PageRequest.of(0, PAGE_SIZE)))).orElse(List.of());
    }

    @Override
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public List<ReviewableItemResponse> getOrderReviewItems(String email, Long orderId) {
        User customer = requireCustomer(email);
        if (orderId == null) return List.of();
        orders.findByIdAndUserId(orderId, customer.getId()).orElseThrow(ReviewServiceImpl::notFound);
        return reviewItems(customer.getId(), items.findReceivedReviewItems(customer.getId(), orderId));
    }

    private List<ReviewableItemResponse> reviewItems(Long userId, List<OrderItem> received) {
        var valid = received.stream().filter(ReviewSource::received).toList();
        if (valid.isEmpty()) return List.of();
        Set<Long> reviewed = Set.copyOf(reviews.findReviewedOrderItemIds(userId,
                valid.stream().map(OrderItem::getId).toList()));
        return valid.stream().map(i -> new ReviewableItemResponse(i.getStoreProduct().getProduct().getId(),
                i.getProductName(), reviewed.contains(i.getId()), i.getId(), i.getOrder().getId(),
                i.getFulfillment().getId(), i.getFulfillment().getStore().getName(), null)).toList();
    }

    @Override
    @PreAuthorize("hasRole('STAFF')")
    public Page<StaffReviewResponse> getStaffReviews(int page) {
        return reviews.findByOrderStoreIds(scope.getAssignedStoreIds(), VisibilityStatus.ACTIVE,
                com.oneshop.service.query.Paging.page(page, PAGE_SIZE)).map(ReviewReadMapper::staffReview);
    }

    @Override
    @PreAuthorize("hasRole('STAFF')")
    public StaffReviewResponse getStaffReview(Long id) {
        Review review = id == null ? null : reviews.findById(id).orElse(null);
        if (review == null) throw notFound();
        ReviewReplyService.requireScope(review, scope);
        return ReviewReadMapper.staffReview(review);
    }

    @Override
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public void reply(Long reviewId, String text) {
        replies.reply(reviewId, text);
    }

    private User requireCustomer(String email) {
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản khách hàng không hợp lệ."));
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy dòng hàng hoặc đánh giá của bạn.");
    }
}
