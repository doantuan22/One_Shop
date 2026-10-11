package com.oneshop.service.impl;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;
import com.oneshop.dto.response.StaffReviewResponse;
import com.oneshop.entity.Product;
import com.oneshop.entity.Review;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.ReviewRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.ReviewService;
import com.oneshop.service.StaffStoreScopeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    /** Newest reviews shown on a Product page; the total and the average always count every visible review. */
    static final int PAGE_SIZE = 20;
    static final int MAX_COMMENT_LENGTH = 1000;
    static final int MAX_REPLY_LENGTH = 1000;
    private static final int STAFF_PAGE_SIZE = 20;

    private final ReviewRepository reviews;
    private final OrderItemRepository orderItems;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;
    private final StaffStoreScopeService staffScope;

    public ReviewServiceImpl(ReviewRepository reviews, OrderItemRepository orderItems, OrderRepository orders,
                             ProductRepository products, UserRepository users, StaffStoreScopeService staffScope) {
        this.reviews = reviews;
        this.orderItems = orderItems;
        this.orders = orders;
        this.products = products;
        this.users = users;
        this.staffScope = staffScope;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public ReviewResponse submit(String email, ReviewRequest request, Long orderId) {
        User customer = requireCustomer(email);
        if (request == null || request.productId() == null) {
            throw new BadRequestException("Yêu cầu đánh giá không hợp lệ.");
        }
        Integer rating = request.rating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new BadRequestException("rating", "Số sao phải từ 1 đến 5.");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException("comment", "Nhận xét tối đa " + MAX_COMMENT_LENGTH + " ký tự.");
        }
        Long productId = request.productId();
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm."));
        Long chosenOrderId = chooseOrder(customer.getId(), productId, orderId);
        if (reviews.existsByUserIdAndProductId(customer.getId(), productId)) {
            throw new BadRequestException("Bạn đã đánh giá sản phẩm này rồi.");
        }

        Review review = new Review();
        review.setUser(customer);
        review.setProduct(product);
        review.setOrder(orders.getReferenceById(chosenOrderId));
        review.setRating(rating);
        review.setComment(comment);
        review.setStatus(VisibilityStatus.ACTIVE);
        try {
            review = reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            // two simultaneous submissions both passed the check above: UQ_reviews_user_product decides
            throw new BadRequestException("Bạn đã đánh giá sản phẩm này rồi.");
        }
        return toResponse(review);
    }

    /**
     * The Order a new review is attached to: the one the form was opened from when given (it must be the customer's
     * COMPLETED Order containing the Product), otherwise the most recent COMPLETED Order that contains the Product.
     */
    private Long chooseOrder(Long customerId, Long productId, Long requestedOrderId) {
        if (requestedOrderId != null) {
            if (!orderItems.isCompletedPurchase(requestedOrderId, customerId, productId)) {
                throw new BadRequestException("Chỉ có thể đánh giá sản phẩm có trong đơn hàng đã hoàn tất của bạn.");
            }
            return requestedOrderId;
        }
        List<Long> newest = orderItems.findCompletedOrderIdsNewestFirst(customerId, productId, PageRequest.of(0, 1));
        if (newest.isEmpty()) {
            throw new BadRequestException("Chỉ có thể đánh giá sản phẩm có trong đơn hàng đã hoàn tất của bạn.");
        }
        return newest.get(0);
    }

    @Override
    public ProductReviewsResponse getProductReviews(Long productId) {
        if (productId == null) {
            return ProductReviewsResponse.empty();
        }
        long count = reviews.countByProductIdAndStatus(productId, VisibilityStatus.ACTIVE);
        if (count == 0) {
            return ProductReviewsResponse.empty();
        }
        Double average = reviews.averageRating(productId, VisibilityStatus.ACTIVE);
        List<ReviewResponse> latest = reviews
                .findByProductIdAndStatusOrderByCreatedAtDescIdDesc(productId, VisibilityStatus.ACTIVE,
                        PageRequest.of(0, PAGE_SIZE))
                .map(ReviewServiceImpl::toResponse).getContent();
        return new ProductReviewsResponse(count, average == null ? 0 : average, latest);
    }

    @Override
    public boolean canReview(String emailOrNull, Long productId) {
        if (emailOrNull == null || productId == null) {
            return false;
        }
        return users.findByEmail(emailOrNull)
                .filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
                .map(u -> orderItems.hasCompletedPurchase(u.getId(), productId)
                        && !reviews.existsByUserIdAndProductId(u.getId(), productId))
                .orElse(false);
    }

    @Override
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public List<ReviewableItemResponse> getOrderReviewItems(String email, Long orderId) {
        User customer = requireCustomer(email);
        if (orderId == null) {
            return List.of();
        }
        var purchased = orderItems.findProductsOfCompletedOrder(orderId, customer.getId());
        if (purchased.isEmpty()) {
            return List.of();
        }
        Set<Long> reviewed = Set.copyOf(reviews.findReviewedProductIds(customer.getId(),
                purchased.stream().map(OrderItemRepository.PurchasedProduct::getProductId).toList()));
        return purchased.stream()
                .map(p -> new ReviewableItemResponse(p.getProductId(), p.getProductName(),
                        reviewed.contains(p.getProductId())))
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('STAFF')")
    public Page<StaffReviewResponse> getStaffReviews(int page) {
        // The Store ids come from the Staff's ACTIVE assignments (StaffStoreScopeService), never from the request.
        return reviews.findByOrderStoreIds(staffScope.getAssignedStoreIds(), VisibilityStatus.ACTIVE,
                        PageRequest.of(Math.max(0, page), STAFF_PAGE_SIZE))
                .map(r -> new StaffReviewResponse(r.getId(), r.getUser().getFullName(), r.getProduct().getId(),
                        r.getProduct().getSku(), r.getProduct().getName(), r.getRating(), r.getComment(),
                        r.getCreatedAt(), r.getOrder().getId(), r.getOrder().getStore().getName(), r.getReply(),
                        r.getRepliedAt()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public void reply(Long reviewId, String text) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User actor = users.findByEmail(auth.getName())
                .filter(u -> u.isActive()
                        && (u.getRole().getName() == RoleName.STAFF || u.getRole().getName() == RoleName.ADMIN))
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không được phép phản hồi đánh giá."));

        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) {
            throw new BadRequestException("reply", "Vui lòng nhập nội dung phản hồi.");
        }
        if (body.length() > MAX_REPLY_LENGTH) {
            throw new BadRequestException("reply", "Phản hồi tối đa " + MAX_REPLY_LENGTH + " ký tự.");
        }

        Review review = reviewId == null ? null : reviews.findForReply(reviewId).orElse(null);
        if (review == null) {
            throw notFound();
        }
        if (actor.getRole().getName() == RoleName.STAFF) {
            // Same answer for "no such review", "other Store", "no Order" and "hidden": nothing leaks to Staff.
            boolean inScope = review.getOrder() != null && review.getStatus() == VisibilityStatus.ACTIVE
                    && staffScope.getAssignedStoreIds().contains(review.getOrder().getStore().getId());
            if (!inScope) {
                throw notFound();
            }
        }
        review.reply(body, actor, LocalDateTime.now().withNano(0));
        reviews.flush();
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đánh giá.");
    }

    private User requireCustomer(String email) {
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản khách hàng không hợp lệ."));
    }

    private static ReviewResponse toResponse(Review review) {
        String storeName = review.getOrder() == null ? null : review.getOrder().getStore().getName();
        return new ReviewResponse(review.getId(), review.getUser().getFullName(), review.getRating(),
                review.getComment(), review.getCreatedAt(), review.getReply(), review.getRepliedAt(), storeName);
    }
}
