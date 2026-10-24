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
class DeliveryDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void tc09CodLifecyclePreservesInventorySnapshotsAndCreatesPaymentOnlyAtCompletion() throws Exception {
        lifecycle(FulfillmentType.DELIVERY,PaymentMethod.COD,false,false);
    }

    @Test
    void onlineDeliveryNeverRewritesItsReceiptAndKeepsInventoryAndItemSnapshots() throws Exception {
        lifecycle(FulfillmentType.DELIVERY,PaymentMethod.ONLINE,false,false);
    }

    @ParameterizedTest
        @EnumSource(DeliveryAction.class)
    void onlinePendingCannotBeFulfilledOrConfirmedByStaff(DeliveryAction action) throws Exception {
        long id=online(); unchanged(id,()->act(id,deliveryAction(action)),FulfillmentConflictException.class);
        assertThat(data.orderStatus(id)).isEqualTo("PENDING_PAYMENT");
    }

    @ParameterizedTest
        @EnumSource(DeliveryAction.class)
    void inconsistentOnlineUnpaidAtAnyStageIsRejected(DeliveryAction action) throws Exception {
        long id=single(FulfillmentType.DELIVERY,PaymentMethod.ONLINE,true);toStage(id,deliveryAction(action).getFrom());
        jdbc.update("update dbo.orders set payment_status='UNPAID' where order_id=?",id);
        unchanged(id,()->act(id,deliveryAction(action)),FulfillmentConflictException.class);
    }

    @ParameterizedTest
        @EnumSource(DeliveryAction.class)
    void codPaidBeforeCompletionCannotBeFulfilledOrCollectedAgain(DeliveryAction action) throws Exception {
        long id=cod();toStage(id,deliveryAction(action).getFrom());jdbc.update("update dbo.orders set payment_status='PAID' where order_id=?",id);
        if(action==DeliveryAction.COMPLETE)unchanged(id,()->act(id,FulfillmentAction.COMPLETE_DELIVERY),RuntimeException.class);
        else {var before=state(id);act(id,deliveryAction(action));assertThat(state(id).get("stock")).isEqualTo(before.get("stock"));}
        assertThat(data.successfulPayments(id)).isZero();
    }

    @Test
    void detailAndCodCollectionUsePurchaseSnapshotsAfterCatalogNameAndPriceChange() throws Exception {
        lifecycle(FulfillmentType.DELIVERY,PaymentMethod.COD,false,true);
    }

    @ParameterizedTest
        @EnumSource(DeliveryAction.class)
    void storeScopeIsRecheckedInServiceForEveryAction(DeliveryAction action) throws Exception {
        long id=cod();toStage(id,deliveryAction(action).getFrom());
        unchanged(id,()->data.asStaff(STAFF_GOVAP,()->staff.perform(part(id),deliveryAction(action),null)),AccessDeniedException.class);
    }

    @ParameterizedTest
        @EnumSource(DeliveryAction.class)
    void pickupOrderCannotUseAnyDeliveryAction(DeliveryAction action) throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);
        if(action==DeliveryAction.PREPARE){assertNoLegacyMutation(id);act(id,FulfillmentAction.PREPARE);assertThat(data.fulfillmentStatus(part(id))).isEqualTo("PREPARING");}
        else unchanged(id,()->act(id,deliveryAction(action)),FulfillmentConflictException.class);
    }

    @ParameterizedTest
        @CsvSource({"CONFIRMED,PACK", "CONFIRMED,SHIP", "CONFIRMED,COMPLETE", "PREPARING,SHIP", "PREPARING,COMPLETE",
                "PACKED,COMPLETE", "SHIPPING,PREPARE", "PREPARING,PREPARE", "PACKED,PACK", "SHIPPING,SHIP"})
    void skipBackwardAndDuplicateActionsCannotWrite(OrderStatus current, DeliveryAction action) throws Exception {
        long id=cod();toStage(id,OrderFulfillmentStatus.valueOf(current.name()));
        unchanged(id,()->act(id,deliveryAction(action)),FulfillmentConflictException.class);
    }

    @Test
    void cancelledOnlineOrderCannotRunAnyDeliveryAction() throws Exception {
        long id=online();cancel(id);for(var a:DeliveryAction.values())unchanged(id,()->act(id,deliveryAction(a)),FulfillmentConflictException.class);
        assertThat(data.cancelRestores(id)).isEqualTo(1);
    }

    @Test
    void inconsistentSuccessfulCodReceiptCannotBeCollectedAgain() throws Exception {
        long id=cod();toStage(id,OrderFulfillmentStatus.COMPLETED);var before=state(id);
        assertThatThrownBy(()->act(id,FulfillmentAction.COMPLETE_DELIVERY)).isInstanceOf(FulfillmentConflictException.class);
        assertThat(state(id)).isEqualTo(before);assertThat(data.successfulPayments(id)).isEqualTo(1);
    }

    @ParameterizedTest
        @ValueSource(strings = {"PAYMENT_SAVE", "AFTER_PAYMENT_FLUSH", "BEFORE_TRANSITION", "HISTORY_SAVE", "AFTER_HISTORY_FLUSH"})
    void codCompletionRollsBackEveryWriteAtAllFailurePoints(String point) throws Exception {
        rollbackStep(FulfillmentType.DELIVERY,PaymentMethod.COD,false,point);
    }

    @ParameterizedTest
        @CsvSource({"PREPARE,1", "PREPARE,2", "PREPARE,3", "SHIP,1", "SHIP,2", "SHIP,3", "COMPLETE,1", "COMPLETE,2", "COMPLETE,3"})
    void realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction action, int round) throws Exception {
        transitionRace(FulfillmentType.DELIVERY,PaymentMethod.COD,deliveryAction(action));
    }

    @ParameterizedTest
        @ValueSource(strings = {"ASSIGNMENT", "STORE", "USER"})
    void inactiveAssignmentStoreOrStaffRevokesReadAndMutationScope(String inactive) throws Exception {
        long id=cod();revoke(inactive);unchanged(id,()->act(id,FulfillmentAction.PREPARE),AccessDeniedException.class);
        assertThatThrownBy(()->data.asStaff(STAFF_THUDUC,()->staff.getFulfillment(part(id)))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void readScopeSupportsMultipleAssignmentsAndOnlyDeliverySnapshots() throws Exception {
        long id=cod();assign(STAFF_GOVAP,"OS-THUDUC");
        var detail=data.asStaff(STAFF_GOVAP,()->staff.getFulfillment(part(id)));assertThat(detail.summary().fulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        assertThat(detail.items()).hasSize(1);assertThat(data.asStaff(STAFF_GOVAP,()->staff.getQueue(FulfillmentQueueFilter.none(),0)).getContent()).anyMatch(f->f.fulfillmentId().equals(part(id)));
    }

    @ParameterizedTest
        @EnumSource(value = PaymentMethod.class, names = {"COD", "ONLINE"})
    void realHttpStaffCanRunFullFlowWithSnapshotsAndForgedFieldsIgnored(PaymentMethod method) throws Exception {
        lifecycle(FulfillmentType.DELIVERY,method,true,false);
    }

    @Test
    void realHttpRejectsCrossStorePickupPendingMissingCsrfAndGetMutations() throws Exception {
        long id=online();String path="/staff/fulfillments/"+part(id)+"/prepare";
        assertThat(postAs(STAFF_GOVAP,path,"storeId=1",true).statusCode()).isEqualTo(403);
        assertThat(postAs(STAFF_THUDUC,path,"paymentStatus=PAID",true).statusCode()).isEqualTo(409);
        assertThat(postAs(STAFF_THUDUC,path,"",false).statusCode()).isEqualTo(403);
        assertThat(getAs(STAFF_THUDUC,path).statusCode()).isIn(404,405);assertThat(data.orderStatus(id)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void codCollectionHelperRequiresSurroundingTransaction() throws Exception {
        long id=cod();assertThatThrownBy(()->paymentService.recordOfflineCollected(new Order(),new OrderFulfillment()))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);assertThat(data.successfulPayments(id)).isZero();
    }
}
