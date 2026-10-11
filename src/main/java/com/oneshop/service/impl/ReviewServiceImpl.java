package com.oneshop.service.impl;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;
import com.oneshop.entity.Product;
import com.oneshop.entity.Review;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.ReviewRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.ReviewService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    /** Newest reviews shown on a Product page; the total and the average always count every visible review. */
    static final int PAGE_SIZE = 20;
    static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviews;
    private final OrderItemRepository orderItems;
    private final ProductRepository products;
    private final UserRepository users;

    public ReviewServiceImpl(ReviewRepository reviews, OrderItemRepository orderItems, ProductRepository products,
                             UserRepository users) {
        this.reviews = reviews;
        this.orderItems = orderItems;
        this.products = products;
        this.users = users;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public ReviewResponse submit(String email, ReviewRequest request) {
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
        if (!orderItems.hasCompletedPurchase(customer.getId(), productId)) {
            throw new BadRequestException("Chỉ có thể đánh giá sản phẩm có trong đơn hàng đã hoàn tất của bạn.");
        }
        if (reviews.existsByUserIdAndProductId(customer.getId(), productId)) {
            throw new BadRequestException("Bạn đã đánh giá sản phẩm này rồi.");
        }

        Review review = new Review();
        review.setUser(customer);
        review.setProduct(product);
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

    private User requireCustomer(String email) {
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản khách hàng không hợp lệ."));
    }

    private static ReviewResponse toResponse(Review review) {
        return new ReviewResponse(review.getId(), review.getUser().getFullName(), review.getRating(),
                review.getComment(), review.getCreatedAt());
    }
}
