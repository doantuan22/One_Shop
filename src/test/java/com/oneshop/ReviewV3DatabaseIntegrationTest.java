package com.oneshop;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** BE06-01..27: real checkout, payment and per-branch delivery; committed then fully restored. */
class ReviewV3DatabaseIntegrationTest extends ReviewV3TestSupport {
    @Test void completedPartIsReviewableWhileOtherPartShips() {
        long order = partiallyReceived(false);
        long first = item(order, "OS-THUDUC"), second = item(order, "OS-GOVAP");
        var response = submit(first);
        var stored = jdbc.queryForMap("select * from dbo.reviews where review_id=?", response.id());
        assertThat(stored.get("order_item_id")).isEqualTo(first);
        assertThat(stored.get("order_id")).isEqualTo(order);
        assertThat(customer(ALICE, () -> reviews.getOrderReviewItems(ALICE, order)))
                .singleElement().satisfies(i -> { assertThat(i.orderItemId()).isEqualTo(first); assertThat(i.reviewed()).isTrue(); });
        assertThatThrownBy(() -> submit(second)).isInstanceOf(BadRequestException.class);
        assertThat(data.orderStatus(order)).isEqualTo("PARTIALLY_COMPLETED");
    }

    @Test void sameSkuReceivedLineDoesNotGrantPermissionForShippingLine() {
        long order = partiallyReceived(true);
        submit(item(order, "OS-THUDUC"));
        assertThatThrownBy(() -> submit(item(order, "OS-GOVAP"))).isInstanceOf(BadRequestException.class);
        assertThat(count("select count(*) from dbo.reviews where order_id=?", order)).isEqualTo(1);
    }

    @Test void sameSkuFromTwoCompletedStoresHasTwoDistinctReviewsAndReplyScopes() {
        long order = partiallyReceived(true);
        long a = submit(item(order, "OS-THUDUC")).id();
        receive(order, "OS-GOVAP");
        long b = submit(item(order, "OS-GOVAP")).id();
        reply(STAFF_THUDUC, a, "Phản hồi A");
        reply(STAFF_GOVAP, b, "Phản hồi B");
        assertThatThrownBy(() -> reply(STAFF_THUDUC, b, "Sai nguồn")).isInstanceOf(AccessDeniedException.class);
        assertThat(count("select count(*) from dbo.reviews where order_id=?", order)).isEqualTo(2);
        var publicRows = reviews.getProductReviews(data.productId(SERUM)).latest();
        assertThat(publicRows).filteredOn(r -> r.id().equals(a) || r.id().equals(b)).hasSize(2);
        assertThat(publicRows).filteredOn(r -> r.id().equals(b)).singleElement()
                .satisfies(r -> assertThat(r.replyStoreName()).isEqualTo("OneShop Gò Vấp"));
    }

    @Test void duplicateItemRejectedEvenAfterModerationHidesTheOriginal() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        long review = submit(line).id();
        admin(() -> { adminReviews.setStatus(review, VisibilityStatus.HIDDEN); return null; });
        assertThatThrownBy(() -> submit(line)).isInstanceOf(BadRequestException.class);
        assertThat(count("select count(*) from dbo.reviews where order_item_id=?", line)).isEqualTo(1);
    }

    @Test void customerCannotReviewAnotherCustomersItemOrForgeEmail() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        assertThatThrownBy(() -> submit(BOB, line, 5, "Không phải của tôi", null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> customer(BOB, () -> reviews.submit(ALICE, new ReviewRequest(null, 5, null, line), order)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> customer(BOB, () -> reviews.getProductReviewItems(ALICE, data.productId(SERUM))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void orderAndProductContextCannotBeForged() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        assertThatThrownBy(() -> submit(ALICE, line, 5, null, order + 12345)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> customer(ALICE, () -> reviews.submit(ALICE,
                new ReviewRequest(data.productId(SHAMPOO), 5, null, line), order))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> customer(ALICE, () -> reviews.submit(ALICE, new ReviewRequest(1L, 5, null), order)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void noPartReceivedMeansNoFormsAndSubmissionRefused() {
        long order = order(false);
        assertThat(customer(ALICE, () -> reviews.getOrderReviewItems(ALICE, order))).isEmpty();
        assertThatThrownBy(() -> submit(item(order, "OS-THUDUC"))).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 6, -1})
    void invalidRatingRefusedAtServiceBoundary(Integer rating) {
        long order = partiallyReceived(false);
        assertThatThrownBy(() -> submit(ALICE, item(order, "OS-THUDUC"), rating, null, order))
                .isInstanceOf(BadRequestException.class);
        assertThat(count("select count(*) from dbo.reviews where order_id=?", order)).isZero();
    }

    @Test void contentBoundaryAndEscapableMarkupAreStoredAsPlainText() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        assertThatThrownBy(() -> submit(ALICE, line, 5, "x".repeat(1001), order)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> submit(ALICE, line, 5, "bad\u0000text", order)).isInstanceOf(BadRequestException.class);
        var row = submit(ALICE, line, 1, " <script>x</script> ", order);
        assertThat(row.comment()).isEqualTo("<script>x</script>");
    }

    @Test void refundOrCancellationNeverGrantsReviewEligibility() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        jdbc.update("update dbo.orders set payment_status='REFUNDED' where order_id=?", order);
        assertThatThrownBy(() -> submit(line)).isInstanceOf(BadRequestException.class);
        assertThat(customer(ALICE, () -> reviews.getOrderReviewItems(ALICE, order))).isEmpty();
    }

    @Test void inactiveCustomerIsRejectedEvenWithAStaleAuthenticatedContext() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        jdbc.update("update dbo.users set status='INACTIVE' where email=?", ALICE);
        assertThatThrownBy(() -> submit(line)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void staffCreatesAndEditsOneDedicatedReplyWithoutEditingTheReview() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        var before = jdbc.queryForMap("select * from dbo.reviews where review_id=?", id);
        reply(STAFF_THUDUC, id, "  Cảm ơn bạn  ");
        var first = jdbc.queryForMap("select * from dbo.review_replies where review_id=?", id);
        reply(STAFF_THUDUC, id, "Đã cập nhật");
        var second = jdbc.queryForMap("select * from dbo.review_replies where review_id=?", id);
        assertThat(second.get("reply_id")).isEqualTo(first.get("reply_id"));
        assertThat(second.get("created_at")).isEqualTo(first.get("created_at"));
        assertThat(second.get("staff_user_id")).isEqualTo(userId(STAFF_THUDUC));
        assertThat(second.get("content")).isEqualTo("Đã cập nhật");
        assertThat(jdbc.queryForMap("select * from dbo.reviews where review_id=?", id)).isEqualTo(before);
    }

    @Test void staffQueueAndDetailUseFulfillmentStoreNotOrderStore() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        assertThat(jdbc.queryForObject("select store_id from dbo.orders where order_id=?", Long.class, order)).isNull();
        assertThat(data.asStaff(STAFF_THUDUC, () -> reviews.getStaffReviews(0)).getContent())
                .anyMatch(r -> r.id().equals(id));
        assertThat(data.asStaff(STAFF_GOVAP, () -> reviews.getStaffReviews(0)).getContent())
                .noneMatch(r -> r.id().equals(id));
        assertThatThrownBy(() -> data.asStaff(STAFF_GOVAP, () -> reviews.getStaffReview(id)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void revokedAssignmentBlocksReplyOnNextRequest() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=?", userId(STAFF_THUDUC));
        assertThatThrownBy(() -> reply(STAFF_THUDUC, id, "Không còn quyền")).isInstanceOf(AccessDeniedException.class);
        assertThat(count("select count(*) from dbo.review_replies where review_id=?", id)).isZero();
    }

    @Test void staffRepliesOnlyForTheStoreTheyCurrentlyWorkAt() {
        long order = partiallyReceived(true), a = submit(item(order, "OS-THUDUC")).id();
        receive(order, "OS-GOVAP");
        long b = submit(item(order, "OS-GOVAP")).id();
        // works at Quận 7: neither review belongs to that Store
        assertThatThrownBy(() -> reply(STAFF_QUAN7, a, "Chưa được giao")).isInstanceOf(AccessDeniedException.class);
        // moved to Thủ Đức: only the Thủ Đức review is theirs
        transfer(STAFF_QUAN7, "OS-THUDUC");
        reply(STAFF_QUAN7, a, "Nguồn A");
        assertThatThrownBy(() -> reply(STAFF_QUAN7, b, "Chưa được giao")).isInstanceOf(AccessDeniedException.class);
        // moved to Gò Vấp: the Gò Vấp review is theirs, the Thủ Đức one is no longer listed
        transfer(STAFF_QUAN7, "OS-GOVAP");
        reply(STAFF_QUAN7, b, "Nguồn B");
        assertThat(data.asStaff(STAFF_QUAN7, () -> reviews.getStaffReviews(0)).getContent())
                .extracting(r -> r.id()).contains(b).doesNotContain(a);
    }

    @Test void inactiveStoreOrStaffAccountCannotReply() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        jdbc.update("update dbo.stores set status='INACTIVE' where code='OS-THUDUC'");
        assertThatThrownBy(() -> reply(STAFF_THUDUC, id, "No")).isInstanceOf(AccessDeniedException.class);
        jdbc.update("update dbo.stores set status='ACTIVE' where code='OS-THUDUC'");
        jdbc.update("update dbo.users set status='INACTIVE' where email=?", STAFF_THUDUC);
        assertThatThrownBy(() -> reply(STAFF_THUDUC, id, "No")).isInstanceOf(AccessDeniedException.class);
    }

    @Test void legacyWithoutOrderItemIsVisibleToAdminAndPublicButNotStaff() {
        long product = data.productId(SERUM);
        jdbc.update("insert into dbo.reviews(user_id,product_id,rating,comment,reply,replied_by,replied_at) "
                + "values(?,?,4,N'Lịch sử V2',N'Phản hồi V2',?,SYSDATETIME())", userId(ALICE), product, userId(STAFF_THUDUC));
        long id = jdbc.queryForObject("select max(review_id) from dbo.reviews", Long.class);
        var original = jdbc.queryForMap("select * from dbo.reviews where review_id=?", id);
        assertThatThrownBy(() -> reply(STAFF_THUDUC, id, "Không đoán nguồn")).isInstanceOf(AccessDeniedException.class);
        assertThat(data.asStaff(STAFF_THUDUC, () -> reviews.getStaffReviews(0)).getContent()).noneMatch(r -> r.id().equals(id));
        assertThat(admin(() -> adminReviews.getReviews(0)).getContent()).filteredOn(r -> r.id().equals(id))
                .singleElement().satisfies(r -> { assertThat(r.storeName()).isNull(); assertThat(r.reply()).isEqualTo("Phản hồi V2"); });
        assertThat(reviews.getProductReviews(product).latest()).anyMatch(r -> r.id().equals(id) && r.replyStoreName() == null);
        assertThat(jdbc.queryForMap("select * from dbo.reviews where review_id=?", id)).isEqualTo(original);
    }

    @Test void sourceMismatchCannotGrantReplyAuthorityOrAcceptCustomerSubmission() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        long id = submit(line).id();
        jdbc.update("update dbo.order_items set fulfillment_id=? where order_item_id=?", data.fulfillmentOf(order, "OS-GOVAP"), line);
        assertThatThrownBy(() -> reply(STAFF_GOVAP, id, "Nguồn giả")).isInstanceOf(AccessDeniedException.class);
        assertThat(data.asStaff(STAFF_GOVAP, () -> reviews.getStaffReviews(0)).getContent()).noneMatch(r -> r.id().equals(id));
    }

    @Test void aFulfillmentFromAnotherOrderCannotBeUsedAsThePurchasedSource() {
        long first = partiallyReceived(false), second = partiallyReceived(false);
        long line = item(first, "OS-THUDUC");
        jdbc.update("update dbo.order_items set fulfillment_id=? where order_item_id=?",
                data.fulfillmentOf(second, "OS-THUDUC"), line);
        assertThatThrownBy(() -> submit(line)).isInstanceOf(BadRequestException.class);
        assertThat(count("select count(*) from dbo.reviews where order_item_id=?", line)).isZero();
    }

    @Test void inconsistentHistoricalUserOrProductNeverGrantsStaffScope() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        jdbc.update("update dbo.reviews set product_id=? where review_id=?", data.productId(SHAMPOO), id);
        assertThatThrownBy(() -> reply(STAFF_THUDUC, id, "Sai Product")).isInstanceOf(AccessDeniedException.class);
        assertThat(data.asStaff(STAFF_THUDUC, () -> reviews.getStaffReviews(0)).getContent()).noneMatch(r -> r.id().equals(id));
        jdbc.update("update dbo.reviews set product_id=?,user_id=? where review_id=?", data.productId(SERUM), userId(BOB), id);
        assertThatThrownBy(() -> reply(STAFF_THUDUC, id, "Sai Customer")).isInstanceOf(AccessDeniedException.class);
    }

    @Test void editingAProvenLegacyReviewPreservesItsOriginalReplyColumns() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        jdbc.update("update dbo.reviews set reply=N'Phản hồi lịch sử',replied_by=?,replied_at=SYSDATETIME() where review_id=?",
                userId(STAFF_THUDUC), id);
        var original = jdbc.queryForMap("select * from dbo.reviews where review_id=?", id);
        reply(STAFF_THUDUC, id, "Phản hồi mới");
        assertThat(jdbc.queryForMap("select * from dbo.reviews where review_id=?", id)).isEqualTo(original);
        assertThat(admin(() -> adminReviews.getReviews(0)).getContent()).filteredOn(r -> r.id().equals(id))
                .singleElement().satisfies(r -> {
                    assertThat(r.reply()).isEqualTo("Phản hồi mới");
                    assertThat(r.legacyReply()).isEqualTo("Phản hồi lịch sử");
                });
    }

    @Test void invalidReplyAndRolledBackReplyEditKeepTheExistingReply() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        reply(STAFF_THUDUC, id, "Đã lưu trước đó");
        var original = jdbc.queryForMap("select * from dbo.review_replies where review_id=?", id);
        for (String text : new String[]{" ", "x".repeat(1001), "bad\u0000text"}) {
            assertThatThrownBy(() -> reply(STAFF_THUDUC, id, text)).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> tx.execute(status -> {
            reply(STAFF_THUDUC, id, "Sửa rồi rollback");
            throw new IllegalStateException("after update flush");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForMap("select * from dbo.review_replies where review_id=?", id)).isEqualTo(original);
    }

    @Test void adminModerationPreservesReplyAndCannotReplyEvenThroughService() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        reply(STAFF_THUDUC, id, "Phản hồi chính thức");
        var before = jdbc.queryForMap("select * from dbo.review_replies where review_id=?", id);
        admin(() -> { adminReviews.setStatus(id, VisibilityStatus.HIDDEN); return null; });
        assertThat(reviews.getProductReviews(data.productId(SERUM)).latest()).noneMatch(r -> r.id().equals(id));
        assertThatThrownBy(() -> reply(STAFF_THUDUC, id, "Không sửa đánh giá ẩn")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin(() -> { reviews.reply(id, "Admin"); return null; })).isInstanceOf(AccessDeniedException.class);
        admin(() -> { adminReviews.setStatus(id, VisibilityStatus.ACTIVE); return null; });
        assertThat(jdbc.queryForMap("select * from dbo.review_replies where review_id=?", id)).isEqualTo(before);
    }

    @Test void unauthorizedRolesCannotModerateOrSubmit() {
        long order = partiallyReceived(false), id = submit(item(order, "OS-THUDUC")).id();
        assertThatThrownBy(() -> customer(ALICE, () -> { adminReviews.setStatus(id, VisibilityStatus.HIDDEN); return null; }))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> data.asStaff(STAFF_THUDUC, () -> reviews.submit(ALICE,
                new ReviewRequest(null, 5, null, item(order, "OS-GOVAP")), order))).isInstanceOf(AccessDeniedException.class);
    }

    @Test void reviewAndReplyFlushesRollbackAtomically() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        assertThatThrownBy(() -> tx.execute(status -> {
            submit(line);
            assertThat(count("select count(*) from dbo.reviews where order_item_id=?", line)).isEqualTo(1);
            throw new IllegalStateException("forced after SQL flush");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("select count(*) from dbo.reviews where order_item_id=?", line)).isZero();
        long id = submit(line).id();
        assertThatThrownBy(() -> tx.execute(status -> {
            reply(STAFF_THUDUC, id, "Sẽ rollback");
            assertThat(count("select count(*) from dbo.review_replies where review_id=?", id)).isEqualTo(1);
            throw new IllegalStateException("forced after Reply flush");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("select count(*) from dbo.review_replies where review_id=?", id)).isZero();
    }

    @Test void sqlUniqueAndForeignKeysRejectInvalidRowsWithoutAffectingExistingRows() {
        long order = partiallyReceived(false), line = item(order, "OS-THUDUC");
        long id = submit(line).id();
        assertThatThrownBy(() -> jdbc.update("insert into dbo.reviews(user_id,product_id,order_item_id,rating) values(?,?,?,5)",
                userId(ALICE), data.productId(SERUM), line)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into dbo.reviews(user_id,product_id,order_item_id,rating) values(?,?,?,5)",
                userId(ALICE), data.productId(SERUM), Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
        reply(STAFF_THUDUC, id, "Một phản hồi");
        assertThatThrownBy(() -> jdbc.update("insert into dbo.review_replies(review_id,staff_user_id,content) values(?,?,N'Trùng')",
                id, userId(STAFF_THUDUC))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into dbo.reviews(user_id,product_id,rating) values(?,?,6)",
                userId(BOB), data.productId(SERUM))).isInstanceOf(DataIntegrityViolationException.class);
        long unReplied = submit(itemAfterReceivingOtherPart(order)).id();
        assertThatThrownBy(() -> jdbc.update("insert into dbo.review_replies(review_id,staff_user_id,content) values(?,?,N'FK')",
                unReplied, Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("select count(*) from dbo.review_replies where review_id=?", id)).isEqualTo(1);
    }

    private long itemAfterReceivingOtherPart(long order) {
        receive(order, "OS-GOVAP");
        return item(order, "OS-GOVAP");
    }
}

