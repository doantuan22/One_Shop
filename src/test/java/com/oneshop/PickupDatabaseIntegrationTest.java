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
class PickupDatabaseIntegrationTest extends V3RegressionTestSupport {

    @ParameterizedTest
        @EnumSource(value=PaymentMethod.class,names={"PAY_AT_STORE","ONLINE"})
    void tc10AndOnlineLifecyclePreserveInventoryAndSnapshots(PaymentMethod method) throws Exception {
        lifecycle(FulfillmentType.STORE_PICKUP,method,false,false);
    }

    @ParameterizedTest @NullAndEmptySource
        @ValueSource(strings={" ","WRONG","ABCDEFGH9","ABCD2340","ABCD234!","ZZZZ9999"})
    void wrongAndMalformedCodeDoNotMutateAnyRows(String supplied) throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.READY_FOR_PICKUP);
        unchanged(id,()->act(id,FulfillmentAction.COMPLETE_PICKUP,supplied),RuntimeException.class);assertThat(data.successfulPayments(id)).isZero();
    }

    @ParameterizedTest @EnumSource(PickupAction.class)
    void pendingOnlineWrongFlowScopeAndOwnershipAreProtected(PickupAction action) throws Exception {
        long id=pickup(PaymentMethod.ONLINE,false);unchanged(id,()->act(id,pickupAction(action),null),FulfillmentConflictException.class);
        unchanged(id,()->data.asStaff(STAFF_GOVAP,()->staff.perform(part(id),pickupAction(action),null)),AccessDeniedException.class);
    }

    @ParameterizedTest @CsvSource({"ONLINE,PREPARE","ONLINE,READY","ONLINE,COMPLETE","PAY_AT_STORE,PREPARE","PAY_AT_STORE,READY","PAY_AT_STORE,COMPLETE"})
    void inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod method,PickupAction action) throws Exception {
        long id=pickup(method,method==PaymentMethod.ONLINE);toStage(id,pickupAction(action).getFrom());
        jdbc.update("update dbo.orders set payment_status=? where order_id=?",method==PaymentMethod.ONLINE?"UNPAID":"PAID",id);
        if(method==PaymentMethod.ONLINE||action==PickupAction.COMPLETE)unchanged(id,()->act(id,pickupAction(action),data.pickupCode(part(id))),RuntimeException.class);
        else {act(id,pickupAction(action));assertThat(data.successfulPayments(id)).isZero();}
    }

    @ParameterizedTest @EnumSource(PickupAction.class)
    void successfulOfflineReceiptCannotBeCollectedOrRepairedAtAnyStage(PickupAction action) throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.COMPLETED);var before=state(id);
        assertThatThrownBy(()->act(id,pickupAction(action),data.pickupCode(part(id)))).isInstanceOf(FulfillmentConflictException.class);
        assertThat(state(id)).isEqualTo(before);assertThat(data.successfulPayments(id)).isEqualTo(1);
    }

    @ParameterizedTest @CsvSource({"CONFIRMED,READY","CONFIRMED,COMPLETE","PREPARING,PREPARE","PREPARING,COMPLETE","READY_FOR_PICKUP,PREPARE","READY_FOR_PICKUP,READY"})
    void skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus stage,PickupAction action) throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.valueOf(stage.name()));
        unchanged(id,()->act(id,pickupAction(action),data.pickupCode(part(id))),FulfillmentConflictException.class);
    }

    @Test
    void cancelledOnlinePickupIsTerminal() throws Exception {
        long id=pickup(PaymentMethod.ONLINE,false);cancel(id);for(var a:PickupAction.values())unchanged(id,()->act(id,pickupAction(a),null),FulfillmentConflictException.class);assertThat(data.cancelRestores(id)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"BEFORE_TRANSITION","ORDER_FLUSH","HISTORY_SAVE","AFTER_HISTORY_FLUSH"})
    void readyFieldsAndHistoryRollBackAtEveryFailurePoint(String point) throws Exception {
        rollbackStep(FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,true,point);
    }

    @ParameterizedTest @ValueSource(strings={"PAYMENT_SAVE","AFTER_PAYMENT_FLUSH","AFTER_PAID","AFTER_PICKED_UP","BEFORE_TRANSITION","HISTORY_SAVE","AFTER_HISTORY_FLUSH"})
    void payAtStoreCompletionRollsBackEveryWrite(String point) throws Exception {
        rollbackStep(FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,false,point);
    }

    @ParameterizedTest @ValueSource(strings={"BEFORE_TRANSITION","HISTORY_SAVE","AFTER_HISTORY_FLUSH"})
    void onlineCompletionRollsBackTimeStatusAndHistoryWithoutTouchingReceipt(String point) throws Exception {
        rollbackStep(FulfillmentType.STORE_PICKUP,PaymentMethod.ONLINE,false,point);
    }

    @ParameterizedTest @CsvSource({"PAY_AT_STORE,READY,1","PAY_AT_STORE,READY,2","PAY_AT_STORE,READY,3","PAY_AT_STORE,COMPLETE,1","PAY_AT_STORE,COMPLETE,2","PAY_AT_STORE,COMPLETE,3","ONLINE,COMPLETE,1","ONLINE,COMPLETE,2","ONLINE,COMPLETE,3"})
    void sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod method,PickupAction action,int round) throws Exception {
        transitionRace(FulfillmentType.STORE_PICKUP,method,pickupAction(action));
    }

    @Test
    void codeVisibilityOwnerAndReadOnlyListsAreEnforced() throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.READY_FOR_PICKUP);String code=data.pickupCode(part(id));
        assertThat(code).hasSize(8);var before=state(id);var staffPage=getAs(STAFF_THUDUC,"/staff/fulfillments/"+part(id));
        assertThat(staffPage.statusCode()).isEqualTo(200);assertThat(staffPage.body()).doesNotContain(code);
        assertThat(getAs(ALICE,"/orders/"+id).body()).contains(code);assertThat(getAs(BOB,"/orders/"+id).statusCode()).isEqualTo(404);
        assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"ASSIGNMENT","STORE","USER"})
    void inactiveScopeRejectsReadAndEveryAction(String inactive) throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);revoke(inactive);
        assertThatThrownBy(()->data.asStaff(STAFF_THUDUC,()->staff.getFulfillment(part(id)))).isInstanceOf(AccessDeniedException.class);
        for(var a:PickupAction.values())unchanged(id,()->act(id,pickupAction(a),null),AccessDeniedException.class);
    }

    @Test
    void purchaseSnapshotsRemainAfterCatalogueChange() throws Exception {
        lifecycle(FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,false,true);
    }

    @Test
    void mixedCheckoutPickupDoesNotMutateNeighborOrdersOrPayments() throws Exception {
        long neighbor=cod();var before=state(neighbor);long id=pickup(PaymentMethod.PAY_AT_STORE,false);var neighbors=jdbc.queryForMap("select * from dbo.orders where order_id=?",neighbor);
        toStage(id,OrderFulfillmentStatus.COMPLETED);assertThat(jdbc.queryForMap("select * from dbo.orders where order_id=?",neighbor)).isEqualTo(neighbors);
        assertThat(data.successfulPayments(neighbor)).isZero();assertThat(data.orderStatus(id)).isEqualTo("COMPLETED");
    }

    @Test
    void payAtStoreHelperRequiresExistingTransaction() throws Exception {
        assertThatThrownBy(()->paymentService.recordOfflineCollected(new Order(),new OrderFulfillment())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void existingSeedCodeConventionWorksWithoutRewritingHistoricalCode() throws Exception {
        var before=jdbc.queryForMap("select * from dbo.order_fulfillments where pickup_code='PK8391'");long f=((Number)before.get("fulfillment_id")).longValue();
        assertThat(getAs(BOB,"/orders/"+before.get("order_id")).body()).contains("PK8391");assertThat(getAs(STAFF_THUDUC,"/staff/fulfillments/"+f).body()).doesNotContain("PK8391");
        assertThat(jdbc.queryForMap("select * from dbo.order_fulfillments where fulfillment_id=?",f)).isEqualTo(before);
    }

    @ParameterizedTest @EnumSource(value=PaymentMethod.class,names={"ONLINE","PAY_AT_STORE"})
    void realHttpPickupCustomerCodeWrongCodeAndForgedFields(PaymentMethod method) throws Exception {
        lifecycle(FulfillmentType.STORE_PICKUP,method,true,false);
    }
}
