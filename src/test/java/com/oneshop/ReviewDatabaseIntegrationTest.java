package com.oneshop;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FSA-01 on the real SQL Server and its seed ({@code database/04_seed_data.sql}): khachhang1 (user 5) has the
 * COMPLETED Order 5 (store product 5) and open Orders 1-3; khachhang2 (user 6) has only the CANCELLED Order 4.
 * Every test writes inside a transaction that is rolled back, so the database is left as it was.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class ReviewDatabaseIntegrationTest {

    private static final String BUYER = "khachhang1@example.com";
    private static final String OTHER = "khachhang2@example.com";
    private static final String STAFF = "staff.thuduc@oneshop.vn";
    private static final String ADMIN = "admin@oneshop.vn";

    @Autowired
    private ReviewService reviews;

    @Autowired
    private JdbcTemplate jdbc;

    private long completedProduct;
    private long inProgressProduct;
    private long cancelledProduct;

    private <T> T as(String email, String role, Supplier<T> work) {
        var prior = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(prior);
        }
    }

    private ReviewResponse submit(String email, long productId, Integer rating, String comment) {
        return as(email, "CUSTOMER", () -> reviews.submit(email, new ReviewRequest(productId, rating, comment)));
    }

    private long productOfStoreProduct(long storeProductId) {
        return jdbc.queryForObject("select product_id from dbo.store_products where store_product_id=?", Long.class,
                storeProductId);
    }

    private long userId(String email) {
        return jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, email);
    }

    private int reviewRows(String email, long productId) {
        return jdbc.queryForObject("select count(*) from dbo.reviews where user_id=? and product_id=?", Integer.class,
                userId(email), productId);
    }

    @BeforeEach
    void fixture() {
        completedProduct = productOfStoreProduct(5);   // Order 5, COMPLETED, khachhang1
        inProgressProduct = productOfStoreProduct(12);  // Order 2, READY_FOR_PICKUP, khachhang1
        cancelledProduct = productOfStoreProduct(9);    // Order 4, CANCELLED, khachhang2
        // the seed already holds one review of khachhang1; start every test from "never reviewed"
        jdbc.update("delete from dbo.reviews");
        assertThat(completedProduct).isNotEqualTo(inProgressProduct);
    }

    // ---------------------------------------------------------------- who may review

    @Test
    void customerWhoCompletedAnOrderReviewsTheProductOnceAndItBecomesVisible() {
        assertThat(as(BUYER, "CUSTOMER", () -> reviews.canReview(BUYER, completedProduct))).isTrue();

        ReviewResponse created = submit(BUYER, completedProduct, 4, "  Dùng ổn, sẽ mua lại  ");

        assertThat(created.rating()).isEqualTo(4);
        assertThat(created.comment()).isEqualTo("Dùng ổn, sẽ mua lại");
        assertThat(created.reviewerName()).isNotBlank();
        assertThat(jdbc.queryForMap("select rating, comment, status from dbo.reviews where user_id=? and product_id=?",
                userId(BUYER), completedProduct))
                .containsEntry("rating", 4).containsEntry("comment", "Dùng ổn, sẽ mua lại").containsEntry("status", "ACTIVE");

        ProductReviewsResponse shown = reviews.getProductReviews(completedProduct);
        assertThat(shown.count()).isEqualTo(1);
        assertThat(shown.averageRating()).isEqualTo(4.0);
        assertThat(shown.latest()).extracting(ReviewResponse::comment).containsExactly("Dùng ổn, sẽ mua lại");

        assertThat(reviews.canReview(BUYER, completedProduct)).isFalse();
        assertThatThrownBy(() -> submit(BUYER, completedProduct, 5, "lần hai"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("đã đánh giá");
        assertThat(reviewRows(BUYER, completedProduct)).isEqualTo(1);
    }

    @Test
    void productNotInACompletedOrderCannotBeReviewed() {
        // Order 2 is READY_FOR_PICKUP and not yet received
        assertThat(reviews.canReview(BUYER, inProgressProduct)).isFalse();
        assertThatThrownBy(() -> submit(BUYER, inProgressProduct, 5, null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("đã hoàn tất");
        assertThat(reviewRows(BUYER, inProgressProduct)).isZero();
    }

    @Test
    void cancelledOrderOrSomeoneElsesPurchaseDoesNotCount() {
        assertThatThrownBy(() -> submit(OTHER, cancelledProduct, 5, null)).isInstanceOf(BadRequestException.class);
        // khachhang2 never received the product khachhang1 completed
        assertThat(reviews.canReview(OTHER, completedProduct)).isFalse();
        assertThatThrownBy(() -> submit(OTHER, completedProduct, 5, null)).isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForObject("select count(*) from dbo.reviews", Integer.class)).isZero();
    }

    @Test
    void staffAdminAndAnInactiveCustomerAreRefused() {
        assertThatThrownBy(() -> as(STAFF, "STAFF", () -> reviews.submit(STAFF,
                new ReviewRequest(completedProduct, 5, null)))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> as(ADMIN, "ADMIN", () -> reviews.submit(ADMIN,
                new ReviewRequest(completedProduct, 5, null)))).isInstanceOf(AccessDeniedException.class);
        // an authenticated customer cannot act in the name of another e-mail
        assertThatThrownBy(() -> as(OTHER, "CUSTOMER", () -> reviews.submit(BUYER,
                new ReviewRequest(completedProduct, 5, null)))).isInstanceOf(AccessDeniedException.class);

        jdbc.update("update dbo.users set status='INACTIVE' where email=?", BUYER);
        assertThatThrownBy(() -> submit(BUYER, completedProduct, 5, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(reviews.canReview(BUYER, completedProduct)).isFalse();
        assertThat(reviews.canReview(STAFF, completedProduct)).isFalse();
        assertThat(reviews.canReview(null, completedProduct)).isFalse();
    }

    @Test
    void unknownProductIsNotFound() {
        assertThatThrownBy(() -> submit(BUYER, 99_999_999L, 5, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- input rules

    @Test
    void ratingMustBeOneToFiveAndCommentAtMost1000Characters() {
        for (Integer bad : new Integer[]{null, 0, 6, -1}) {
            assertThatThrownBy(() -> submit(BUYER, completedProduct, bad, "x")).as("rating " + bad)
                    .isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> submit(BUYER, completedProduct, 5, "x".repeat(1001)))
                .isInstanceOf(BadRequestException.class);
        assertThat(reviewRows(BUYER, completedProduct)).isZero();

        // the boundaries are accepted; a blank comment is stored as null
        assertThat(submit(BUYER, completedProduct, 1, "x".repeat(1000)).comment()).hasSize(1000);
    }

    @Test
    void blankCommentIsStoredAsNull() {
        assertThat(submit(BUYER, completedProduct, 5, "   ").comment()).isNull();
        assertThat(jdbc.queryForObject("select comment from dbo.reviews where user_id=? and product_id=?", String.class,
                userId(BUYER), completedProduct)).isNull();
    }

    // ---------------------------------------------------------------- visibility and the Order page

    @Test
    void hiddenReviewsAreNotShownCountedOrAveraged() {
        submit(BUYER, completedProduct, 5, "Tốt");
        // another reviewer whose review Admin has hidden (inserted directly: the rule above is about submit())
        jdbc.update("insert into dbo.reviews(user_id, product_id, rating, comment, status) values (?,?,1,'Tệ','HIDDEN')",
                userId(OTHER), completedProduct);

        ProductReviewsResponse shown = reviews.getProductReviews(completedProduct);

        assertThat(shown.count()).isEqualTo(1);
        assertThat(shown.averageRating()).isEqualTo(5.0);
        assertThat(shown.latest()).extracting(ReviewResponse::comment).containsExactly("Tốt");
        assertThat(reviews.getProductReviews(inProgressProduct)).isEqualTo(ProductReviewsResponse.empty());

        // Admin hides the buyer's own review: it disappears, and it still counts as "already reviewed"
        jdbc.update("update dbo.reviews set status='HIDDEN' where user_id=?", userId(BUYER));
        assertThat(reviews.getProductReviews(completedProduct)).isEqualTo(ProductReviewsResponse.empty());
        assertThat(reviews.canReview(BUYER, completedProduct)).isFalse();
        assertThatThrownBy(() -> submit(BUYER, completedProduct, 5, "đăng lại"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void orderPageOffersOnlyTheProductsOfTheOwnersCompletedOrder() {
        List<ReviewableItemResponse> before = as(BUYER, "CUSTOMER", () -> reviews.getOrderReviewItems(BUYER, 5L));
        assertThat(before).extracting(ReviewableItemResponse::productId).containsExactly(completedProduct);
        assertThat(before).extracting(ReviewableItemResponse::reviewed).containsExactly(false);
        // the name is the one frozen on the Order line, even if the catalog name changes afterwards
        jdbc.update("update dbo.products set name=N'CURRENT CATALOG CHANGED' where product_id=?", completedProduct);
        assertThat(as(BUYER, "CUSTOMER", () -> reviews.getOrderReviewItems(BUYER, 5L)))
                .extracting(ReviewableItemResponse::productName)
                .containsExactly(jdbc.queryForObject("select product_name from dbo.order_items where order_id=5", String.class));

        submit(BUYER, completedProduct, 5, null);
        assertThat(as(BUYER, "CUSTOMER", () -> reviews.getOrderReviewItems(BUYER, 5L)))
                .extracting(ReviewableItemResponse::reviewed).containsExactly(true);

        // not completed (Order 1) and not the caller's (Order 5 seen by khachhang2) offer nothing
        assertThat(as(BUYER, "CUSTOMER", () -> reviews.getOrderReviewItems(BUYER, 1L))).isEmpty();
        assertThat(as(OTHER, "CUSTOMER", () -> reviews.getOrderReviewItems(OTHER, 5L))).isEmpty();
    }

    // ---------------------------------------------------------------- the database backs the rule up

    @Test
    void uniqueConstraintRejectsASecondReviewOfTheSameProduct() {
        jdbc.update("insert into dbo.reviews(user_id, product_id, rating, status) values (?,?,5,'ACTIVE')",
                userId(BUYER), completedProduct);

        assertThatThrownBy(() -> jdbc.update(
                "insert into dbo.reviews(user_id, product_id, rating, status) values (?,?,3,'ACTIVE')",
                userId(BUYER), completedProduct)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
