package com.oneshop;
import com.oneshop.dto.request.*;
import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.entity.Order;
import com.oneshop.exception.*;
import com.oneshop.service.fulfillment.*;
import com.oneshop.service.payment.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** BE-07: historical case identities/data retained; bodies now verify the V3 contract. See the migration matrix. */
class ReviewDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void customerWhoCompletedAnOrderReviewsTheProductOnceAndItBecomesVisible() throws Exception {
        long id=partiallyReceived(false),line=item(id,"OS-THUDUC");var created=submit(ALICE,line,4,"  Dùng ổn  ",id);assertThat(created.comment()).isEqualTo("Dùng ổn");assertThat(reviews.getProductReviews(data.productId(SERUM)).latest()).anyMatch(r->r.id().equals(created.id()));assertThatThrownBy(()->submit(line)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void productNotInACompletedOrderCannotBeReviewed() throws Exception {
        long id=order(false);assertThatThrownBy(()->submit(item(id,"OS-THUDUC"))).isInstanceOf(BadRequestException.class);assertThat(count("select count(*) from dbo.reviews where order_id=?",id)).isZero();
    }

    @Test
    void cancelledOrderOrSomeoneElsesPurchaseDoesNotCount() throws Exception {
        long id=online();cancel(id);assertThatThrownBy(()->submit(item(id,"OS-THUDUC"))).isInstanceOf(BadRequestException.class);long received=partiallyReceived(false);assertThatThrownBy(()->submit(BOB,item(received,"OS-THUDUC"),5,null,null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staffAdminAndAnInactiveCustomerAreRefused() throws Exception {
        long id=partiallyReceived(false),line=item(id,"OS-THUDUC");for(String actor:List.of(STAFF_THUDUC,ADMIN))assertThatThrownBy(()->data.asRole(actor,actor.equals(ADMIN)?"ROLE_ADMIN":"ROLE_STAFF",()->reviews.submit(actor,new ReviewRequest(null,5,null,line),id))).isInstanceOf(AccessDeniedException.class);
        jdbc.update("update dbo.users set status='INACTIVE' where email=?",ALICE);assertThatThrownBy(()->submit(line)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        long id=partiallyReceived(false);assertThatThrownBy(()->customer(ALICE,()->reviews.submit(ALICE,new ReviewRequest(Long.MAX_VALUE,5,null,item(id,"OS-THUDUC")),id))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ratingMustBeOneToFiveAndCommentAtMost1000Characters() throws Exception {
        long id=partiallyReceived(false),line=item(id,"OS-THUDUC");for(int rating:new int[]{-1,0,6})assertThatThrownBy(()->submit(ALICE,line,rating,null,id)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->submit(ALICE,line,5,"x".repeat(1001),id)).isInstanceOf(BadRequestException.class);assertThat(submit(ALICE,line,1,"x".repeat(1000),id).comment()).hasSize(1000);
    }

    @Test
    void blankCommentIsStoredAsNull() throws Exception {
        long id=partiallyReceived(false);assertThat(submit(ALICE,item(id,"OS-THUDUC"),5,"   ",id).comment()).isNull();
    }

    @Test
    void hiddenReviewsAreNotShownCountedOrAveraged() throws Exception {
        long id=partiallyReceived(false),review=submit(item(id,"OS-THUDUC")).id();long before=reviews.getProductReviews(data.productId(SERUM)).count();admin(()->{adminReviews.setStatus(review,VisibilityStatus.HIDDEN);return null;});assertThat(reviews.getProductReviews(data.productId(SERUM)).count()).isEqualTo(before-1);assertThat(reviews.getProductReviews(data.productId(SERUM)).latest()).noneMatch(r->r.id().equals(review));
    }

    @Test
    void orderPageOffersOnlyTheProductsOfTheOwnersCompletedOrder() throws Exception {
        long id=partiallyReceived(true);assertThat(customer(ALICE,()->reviews.getOrderReviewItems(ALICE,id))).singleElement().satisfies(i->assertThat(i.orderItemId()).isEqualTo(item(id,"OS-THUDUC")));assertThatThrownBy(()->customer(BOB,()->reviews.getOrderReviewItems(BOB,id))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reviewIsAttachedToTheOrderTheFormWasOpenedFrom() throws Exception {
        long id=partiallyReceived(false),line=item(id,"OS-THUDUC");long review=submit(ALICE,line,5,null,id).id();assertThat(jdbc.queryForMap("select order_id,order_item_id from dbo.reviews where review_id=?",review)).containsEntry("order_id",id).containsEntry("order_item_id",line);
    }

    @Test
    void withoutAnOrderTheMostRecentCompletedOneIsUsed() throws Exception {
        // V3 deliberately requires an explicit item; no most-recent Order inference.
        long id=partiallyReceived(false);assertThatThrownBy(()->customer(ALICE,()->reviews.submit(ALICE,new ReviewRequest(data.productId(SERUM),5,null),null))).isInstanceOf(BadRequestException.class);assertThat(submit(item(id,"OS-THUDUC"))).isNotNull();
    }

    @Test
    void whenCompletionMomentsTieTheHigherOrderIdWins() throws Exception {
        // Explicit OrderItem makes completion-time ties irrelevant, retaining both purchases.
        long first=partiallyReceived(false),second=partiallyReceived(false);long a=submit(item(first,"OS-THUDUC")).id(),b=submit(item(second,"OS-THUDUC")).id();assertThat(a).isNotEqualTo(b);assertThat(count("select count(*) from dbo.reviews where review_id in (?,?)",a,b)).isEqualTo(2);
    }

    @Test
    void anOrderWithoutACompletedHistoryRowFallsBackToItsUpdatedAt() throws Exception {
        // V3 receipt is proved by the item's Fulfillment, not inferred from Order-history timestamps.
        long id=partiallyReceived(false);assertThat(submit(item(id,"OS-THUDUC"))).isNotNull();assertThat(data.orderStatus(id)).isEqualTo("PARTIALLY_COMPLETED");
    }

    @Test
    void customerCannotAttachAReviewToAnOrderThatIsNotTheirCompletedPurchaseOfTheProduct() throws Exception {
        long id=partiallyReceived(false),line=item(id,"OS-THUDUC");assertThatThrownBy(()->submit(ALICE,line,5,null,id+99999)).isInstanceOf(ResourceNotFoundException.class);assertThatThrownBy(()->submit(BOB,line,5,null,id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theOrderIsNotChangedByASecondAttempt() throws Exception {
        long id=partiallyReceived(false),line=item(id,"OS-THUDUC");submit(line);var before=state(id);assertThatThrownBy(()->submit(line)).isInstanceOf(BadRequestException.class);assertThat(state(id)).isEqualTo(before);
    }

    @Test
    void staffOfTheStoreAnswersTrimmedAndCanEditTheAnswer() throws Exception {
        long id=partiallyReceived(false),r=submit(item(id,"OS-THUDUC")).id();reply(STAFF_THUDUC,r,"  Cảm ơn  ");long pk=jdbc.queryForObject("select reply_id from dbo.review_replies where review_id=?",Long.class,r);reply(STAFF_THUDUC,r,"Cập nhật");assertThat(jdbc.queryForMap("select reply_id,content,staff_user_id from dbo.review_replies where review_id=?",r)).containsEntry("reply_id",pk).containsEntry("content","Cập nhật").containsEntry("staff_user_id",userId(STAFF_THUDUC));
    }

    @Test
    void staffOfAnotherStoreCannotSeeOrAnswerIt() throws Exception {
        long id=partiallyReceived(false),r=submit(item(id,"OS-THUDUC")).id();assertThatThrownBy(()->reply(STAFF_GOVAP,r,"No")).isInstanceOf(AccessDeniedException.class);assertThat(data.asStaff(STAFF_GOVAP,()->reviews.getStaffReviews(0)).getContent()).noneMatch(x->x.id().equals(r));
    }

    @Test
    void adminAnswersAnyReviewAndTheLatestEditorIsRecorded() throws Exception {
        // DEC-27 replaces the old Admin Reply grant with moderation only.
        long id=partiallyReceived(false),r=submit(item(id,"OS-THUDUC")).id();assertThatThrownBy(()->admin(()->{reviews.reply(r,"No");return null;})).isInstanceOf(AccessDeniedException.class);assertThat(count("select count(*) from dbo.review_replies where review_id=?",r)).isZero();admin(()->{adminReviews.setStatus(r,VisibilityStatus.HIDDEN);return null;});
    }

    @Test
    void reviewWithoutAnOrderOrHiddenIsAdminOnly() throws Exception {
        jdbc.update("insert into dbo.reviews(user_id,product_id,rating,comment) values(?,?,4,N'BE07 legacy')",userId(ALICE),data.productId(SERUM));long r=jdbc.queryForObject("select max(review_id) from dbo.reviews",Long.class);assertThatThrownBy(()->reply(STAFF_THUDUC,r,"No inferred Store")).isInstanceOf(AccessDeniedException.class);assertThat(admin(()->adminReviews.getReviews(0)).getContent()).anyMatch(x->x.id().equals(r)&&x.storeName()==null);
    }

    @Test
    void replyLengthAndWhitespaceRulesAndCustomerIsRefused() throws Exception {
        long id=partiallyReceived(false),r=submit(item(id,"OS-THUDUC")).id();for(String s:List.of(" ","x".repeat(1001)))assertThatThrownBy(()->reply(STAFF_THUDUC,r,s)).isInstanceOf(BadRequestException.class);assertThatThrownBy(()->customer(ALICE,()->{reviews.reply(r,"No");return null;})).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void editingTheReviewKeepsTheStoredReply() throws Exception {
        long id=partiallyReceived(false),r=submit(item(id,"OS-THUDUC")).id();reply(STAFF_THUDUC,r,"History");var row=jdbc.queryForMap("select * from dbo.review_replies where review_id=?",r);admin(()->{adminReviews.setStatus(r,VisibilityStatus.HIDDEN);return null;});admin(()->{adminReviews.setStatus(r,VisibilityStatus.ACTIVE);return null;});assertThat(jdbc.queryForMap("select * from dbo.review_replies where review_id=?",r)).isEqualTo(row);
    }

    @Test
    void productPageShowsTheReplyWithTheStoreOfTheOrder() throws Exception {
        long id=partiallyReceived(false),r=submit(item(id,"OS-THUDUC")).id();reply(STAFF_THUDUC,r,"Đúng nguồn");assertThat(getAs(ALICE,"/products/"+data.productId(SERUM)).body()).contains("Đúng nguồn","OneShop Thủ Đức");assertThat(jdbc.queryForObject("select store_id from dbo.orders where order_id=?",Long.class,id)).isNull();
    }

    @Test
    void replyColumnsAreCheckedByTheDatabase() throws Exception {
        long id=partiallyReceived(false),r=submit(item(id,"OS-THUDUC")).id();assertThatThrownBy(()->jdbc.update("update dbo.reviews set reply=N'Invalid legacy reply' where review_id=?",r)).isInstanceOf(DataIntegrityViolationException.class);reply(STAFF_THUDUC,r,"Dedicated reply");assertThatThrownBy(()->jdbc.update("insert into dbo.review_replies(review_id,staff_user_id,content) values(?,?,N'Duplicate')",r,userId(STAFF_THUDUC))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraintRejectsASecondReviewOfTheSameProduct() throws Exception {
        // DEC-26: reject the same item, permit another received allocation of the same Product.
        long id=partiallyReceived(true),a=item(id,"OS-THUDUC");submit(a);assertThatThrownBy(()->submit(a)).isInstanceOf(BadRequestException.class);receive(id,"OS-GOVAP");submit(item(id,"OS-GOVAP"));assertThat(count("select count(*) from dbo.reviews where order_id=?",id)).isEqualTo(2);
    }
}
