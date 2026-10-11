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
        return as(email, "CUSTOMER", () -> reviews.submit(email, new ReviewRequest(productId, rating, comment), null));
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
                new ReviewRequest(completedProduct, 5, null), null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> as(ADMIN, "ADMIN", () -> reviews.submit(ADMIN,
                new ReviewRequest(completedProduct, 5, null), null))).isInstanceOf(AccessDeniedException.class);
        // an authenticated customer cannot act in the name of another e-mail
        assertThatThrownBy(() -> as(OTHER, "CUSTOMER", () -> reviews.submit(BUYER,
                new ReviewRequest(completedProduct, 5, null), null))).isInstanceOf(AccessDeniedException.class);

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

    // ---------------------------------------------------------------- which Order a review is attached to

    private static final String STAFF_THU_DUC = "staff.thuduc@oneshop.vn";   // Store 1
    private static final String STAFF_GO_VAP = "staff.govap@oneshop.vn";     // Store 2

    /** Seed Order 1 (Store 1, DELIVERY) contains the same Product as Order 5; make it COMPLETED at {@code when}. */
    private void completeOrderOne(String when) {
        jdbc.update("update dbo.orders set order_status='COMPLETED', payment_status='PAID' where order_id=1");
        jdbc.update("insert into dbo.order_status_history(order_id, old_status, new_status, changed_by_user_id, note, changed_at)"
                + " values (1,'SHIPPING','COMPLETED',?,'test',?)", userId(STAFF_THU_DUC), when);
    }

    private void setOrderFiveCompletedAt(String when) {
        jdbc.update("update dbo.order_status_history set changed_at=? where order_id=5 and new_status='COMPLETED'", when);
    }

    private Long storedOrderId(String email, long productId) {
        return jdbc.queryForObject("select order_id from dbo.reviews where user_id=? and product_id=?", Long.class,
                userId(email), productId);
    }

    private ReviewResponse submitFromOrder(String email, long productId, Long orderId) {
        return as(email, "CUSTOMER", () -> reviews.submit(email, new ReviewRequest(productId, 5, "ok"), orderId));
    }

    @Test
    void reviewIsAttachedToTheOrderTheFormWasOpenedFrom() {
        completeOrderOne("2026-10-01 10:00:00");
        setOrderFiveCompletedAt("2026-09-01 10:00:00");   // Order 1 is the newer one

        submitFromOrder(BUYER, completedProduct, 5L);

        assertThat(storedOrderId(BUYER, completedProduct)).isEqualTo(5L);
    }

    @Test
    void withoutAnOrderTheMostRecentCompletedOneIsUsed() {
        completeOrderOne("2026-10-01 10:00:00");
        setOrderFiveCompletedAt("2026-09-01 10:00:00");

        submit(BUYER, completedProduct, 4, "newest order");

        assertThat(storedOrderId(BUYER, completedProduct)).isEqualTo(1L);
    }

    @Test
    void whenCompletionMomentsTieTheHigherOrderIdWins() {
        completeOrderOne("2026-09-01 10:00:00");
        setOrderFiveCompletedAt("2026-09-01 10:00:00");

        submit(BUYER, completedProduct, 4, "tie");

        assertThat(storedOrderId(BUYER, completedProduct)).isEqualTo(5L);
    }

    @Test
    void anOrderWithoutACompletedHistoryRowFallsBackToItsUpdatedAt() {
        jdbc.update("update dbo.orders set order_status='COMPLETED', payment_status='PAID', updated_at='2026-10-05 10:00:00' where order_id=1");
        setOrderFiveCompletedAt("2026-09-01 10:00:00");

        submit(BUYER, completedProduct, 4, "fallback");

        assertThat(storedOrderId(BUYER, completedProduct)).isEqualTo(1L);
    }

    @Test
    void customerCannotAttachAReviewToAnOrderThatIsNotTheirCompletedPurchaseOfTheProduct() {
        // Order 1 is still SHIPPING; Order 4 is somebody else's and cancelled; 999999 does not exist
        for (Long orderId : new Long[]{1L, 4L, 999_999L}) {
            assertThatThrownBy(() -> submitFromOrder(BUYER, completedProduct, orderId)).as("order " + orderId)
                    .isInstanceOf(BadRequestException.class);
        }
        // Order 5 is the customer's COMPLETED order, but does not contain this other product
        assertThatThrownBy(() -> submitFromOrder(BUYER, inProgressProduct, 5L)).isInstanceOf(BadRequestException.class);
        // another customer cannot use the buyer's Order 5
        assertThatThrownBy(() -> submitFromOrder(OTHER, completedProduct, 5L)).isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForObject("select count(*) from dbo.reviews", Integer.class)).isZero();
    }

    @Test
    void theOrderIsNotChangedByASecondAttempt() {
        completeOrderOne("2026-10-01 10:00:00");
        submitFromOrder(BUYER, completedProduct, 5L);

        assertThatThrownBy(() -> submitFromOrder(BUYER, completedProduct, 1L)).isInstanceOf(BadRequestException.class);

        assertThat(storedOrderId(BUYER, completedProduct)).isEqualTo(5L);
    }

    // ---------------------------------------------------------------- replies

    private long reviewForOrder(long orderId, String status) {
        // inserted directly so the test controls the Order: user 5 reviewed product 3 from this Order
        return jdbc.queryForObject("insert into dbo.reviews(user_id, product_id, rating, comment, status, order_id)"
                + " output inserted.review_id values (?,?,5,'Tot',?,?)", Long.class,
                userId(BUYER), completedProduct, status, orderId);
    }

    private void replyAs(String email, String role, long reviewId, String text) {
        as(email, role, () -> {
            reviews.reply(reviewId, text);
            return null;
        });
    }

    private String replyText(long reviewId) {
        return jdbc.queryForObject("select reply from dbo.reviews where review_id=?", String.class, reviewId);
    }

    private String repliedBy(long reviewId) {
        return jdbc.queryForObject("select u.email from dbo.reviews r join dbo.users u on u.user_id=r.replied_by"
                + " where r.review_id=?", String.class, reviewId);
    }

    @Test
    void staffOfTheStoreAnswersTrimmedAndCanEditTheAnswer() {
        completeOrderOne("2026-10-01 10:00:00");
        long review = reviewForOrder(1, "ACTIVE");               // Order 1 is at Store 1

        replyAs(STAFF_THU_DUC, "STAFF", review, "  Cảm ơn bạn đã ủng hộ  ");

        assertThat(replyText(review)).isEqualTo("Cảm ơn bạn đã ủng hộ");
        assertThat(repliedBy(review)).isEqualTo(STAFF_THU_DUC);
        assertThat(jdbc.queryForObject("select replied_at from dbo.reviews where review_id=?", Object.class, review)).isNotNull();

        replyAs(STAFF_THU_DUC, "STAFF", review, "Chúng tôi đã cập nhật");
        assertThat(replyText(review)).isEqualTo("Chúng tôi đã cập nhật");
        assertThat(jdbc.queryForObject("select count(*) from dbo.reviews where reply is not null", Integer.class)).isEqualTo(1);
    }

    @Test
    void staffOfAnotherStoreCannotSeeOrAnswerIt() {
        completeOrderOne("2026-10-01 10:00:00");
        long review = reviewForOrder(1, "ACTIVE");

        assertThatThrownBy(() -> replyAs(STAFF_GO_VAP, "STAFF", review, "Không phải của tôi"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(replyText(review)).isNull();
        assertThat(as(STAFF_GO_VAP, "STAFF", () -> reviews.getStaffReviews(0)).getContent()).isEmpty();
        assertThat(as(STAFF_THU_DUC, "STAFF", () -> reviews.getStaffReviews(0)).getContent())
                .extracting(r -> r.id()).containsExactly(review);
    }

    @Test
    void adminAnswersAnyReviewAndTheLatestEditorIsRecorded() {
        completeOrderOne("2026-10-01 10:00:00");
        long withOrder = reviewForOrder(1, "ACTIVE");
        replyAs(STAFF_THU_DUC, "STAFF", withOrder, "Từ nhân viên");

        replyAs(ADMIN, "ADMIN", withOrder, "Admin sửa lại");

        assertThat(replyText(withOrder)).isEqualTo("Admin sửa lại");
        assertThat(repliedBy(withOrder)).isEqualTo(ADMIN);
    }

    @Test
    void reviewWithoutAnOrderOrHiddenIsAdminOnly() {
        completeOrderOne("2026-10-01 10:00:00");
        long noOrder = jdbc.queryForObject("insert into dbo.reviews(user_id, product_id, rating, status)"
                + " output inserted.review_id values (?,?,4,'ACTIVE')", Long.class, userId(BUYER), completedProduct);
        long hidden = jdbc.queryForObject("insert into dbo.reviews(user_id, product_id, rating, status, order_id)"
                + " output inserted.review_id values (?,?,4,'HIDDEN',1)", Long.class, userId(OTHER), completedProduct);

        for (long id : new long[]{noOrder, hidden}) {
            assertThatThrownBy(() -> replyAs(STAFF_THU_DUC, "STAFF", id, "x")).isInstanceOf(ResourceNotFoundException.class);
            replyAs(ADMIN, "ADMIN", id, "Admin trả lời");
            assertThat(replyText(id)).isEqualTo("Admin trả lời");
        }
        assertThat(as(STAFF_THU_DUC, "STAFF", () -> reviews.getStaffReviews(0)).getContent()).isEmpty();
    }

    @Test
    void replyLengthAndWhitespaceRulesAndCustomerIsRefused() {
        completeOrderOne("2026-10-01 10:00:00");
        long review = reviewForOrder(1, "ACTIVE");

        for (String bad : new String[]{null, "", "   ", "x".repeat(1001)}) {
            assertThatThrownBy(() -> replyAs(STAFF_THU_DUC, "STAFF", review, bad)).isInstanceOf(BadRequestException.class);
        }
        assertThat(replyText(review)).isNull();

        replyAs(STAFF_THU_DUC, "STAFF", review, "x");
        assertThat(replyText(review)).isEqualTo("x");
        replyAs(STAFF_THU_DUC, "STAFF", review, "y".repeat(1000));
        assertThat(replyText(review)).hasSize(1000);

        assertThatThrownBy(() -> replyAs(BUYER, "CUSTOMER", review, "tự trả lời"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> replyAs(STAFF_THU_DUC, "STAFF", 99_999_999L, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void editingTheReviewKeepsTheStoredReply() {
        completeOrderOne("2026-10-01 10:00:00");
        long review = reviewForOrder(1, "ACTIVE");
        replyAs(STAFF_THU_DUC, "STAFF", review, "Cảm ơn");

        // the reply lives in its own columns: changing the review text does not touch it
        jdbc.update("update dbo.reviews set comment='Đã sửa', rating=4 where review_id=?", review);

        assertThat(replyText(review)).isEqualTo("Cảm ơn");
    }

    @Test
    void productPageShowsTheReplyWithTheStoreOfTheOrder() {
        completeOrderOne("2026-10-01 10:00:00");
        long review = reviewForOrder(1, "ACTIVE");
        replyAs(STAFF_THU_DUC, "STAFF", review, "Cảm ơn bạn");

        var shown = reviews.getProductReviews(completedProduct).latest();

        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).reply()).isEqualTo("Cảm ơn bạn");
        assertThat(shown.get(0).replyStoreName()).isEqualTo(
                jdbc.queryForObject("select s.name from dbo.orders o join dbo.stores s on s.store_id=o.store_id where o.order_id=1", String.class));
    }

    @Test
    void replyColumnsAreCheckedByTheDatabase() {
        long review = jdbc.queryForObject("insert into dbo.reviews(user_id, product_id, rating, status)"
                + " output inserted.review_id values (?,?,4,'ACTIVE')", Long.class, userId(BUYER), completedProduct);
        long admin = userId(ADMIN);

        // a reply needs its author and moment, and all three are NULL together
        assertThatThrownBy(() -> jdbc.update("update dbo.reviews set reply='x' where review_id=?", review))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update dbo.reviews set replied_by=? where review_id=?", admin, review))
                .isInstanceOf(DataIntegrityViolationException.class);
        // blank reply is refused even with author and moment
        assertThatThrownBy(() -> jdbc.update(
                "update dbo.reviews set reply='   ', replied_by=?, replied_at=sysdatetime() where review_id=?", admin, review))
                .isInstanceOf(DataIntegrityViolationException.class);
        // the complete form is accepted
        assertThat(jdbc.update("update dbo.reviews set reply='ok', replied_by=?, replied_at=sysdatetime() where review_id=?",
                admin, review)).isEqualTo(1);
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
