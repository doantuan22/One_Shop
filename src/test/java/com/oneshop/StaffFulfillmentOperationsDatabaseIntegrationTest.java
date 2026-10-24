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
class StaffFulfillmentOperationsDatabaseIntegrationTest extends V3RegressionTestSupport {

    @ParameterizedTest @EnumSource(value = PaymentMethod.class, names = {"COD", "ONLINE"})
    void deliveryHttpUiFollowsEveryStateAndUsesExistingPaymentHistoryRules(PaymentMethod method) throws Exception {
        lifecycle(FulfillmentType.DELIVERY,method,true,false);
    }

    @ParameterizedTest @EnumSource(value = PaymentMethod.class, names = {"PAY_AT_STORE", "ONLINE"})
    void pickupHttpUiVerifiesCustomerCodeAndCompletesPaymentBeforeHistory(PaymentMethod method) throws Exception {
        lifecycle(FulfillmentType.STORE_PICKUP,method,true,false);
    }

    @ParameterizedTest @CsvSource({"DELIVERY,pack", "DELIVERY,ship", "DELIVERY,complete", "STORE_PICKUP,ready", "STORE_PICKUP,complete"})
    void jumpingAheadOfConfirmedIsRejectedRegardlessOfForgedTargetStatus(FulfillmentType type, String action) throws Exception {
        long id=single(type,type==FulfillmentType.DELIVERY?PaymentMethod.COD:PaymentMethod.PAY_AT_STORE,false);String path=action.equals("complete")?(type==FulfillmentType.DELIVERY?"complete-delivery":"complete-pickup"):action.equals("ready")?"ready-for-pickup":action;var before=state(id);assertThat(postAs(STAFF_THUDUC,"/staff/fulfillments/"+part(id)+"/"+path,"targetStatus=COMPLETED&paymentStatus=PAID",true).statusCode()).isEqualTo(409);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest @EnumSource(FulfillmentType.class)
    void actionsOfOtherFulfillmentCannotMutateAnOwnOrder(FulfillmentType type) throws Exception {
        long id=single(type,type==FulfillmentType.DELIVERY?PaymentMethod.COD:PaymentMethod.PAY_AT_STORE,false);act(id,FulfillmentAction.PREPARE);unchanged(id,()->act(id,type==FulfillmentType.DELIVERY?FulfillmentAction.MARK_READY:FulfillmentAction.PACK),FulfillmentConflictException.class);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"WRONG999", "abc", "ABCDEFGH"})
    void wrongOrMissingPickupCodeCannotCollectPaymentOrComplete(String code) throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.READY_FOR_PICKUP);var before=state(id);var response=postAs(STAFF_THUDUC,"/staff/fulfillments/"+part(id)+"/complete-pickup","pickupCode="+enc(code),true);assertThat(response.statusCode()).isIn(400,409);assertThat(state(id)).isEqualTo(before);assertThat(data.successfulPayments(id)).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"delivery/prepare", "delivery/pack", "delivery/ship", "delivery/complete", "pickup/prepare", "pickup/ready", "pickup/complete"})
    void everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String action) throws Exception {
        long id=action.startsWith("delivery")?cod():pickup(PaymentMethod.PAY_AT_STORE,false);String a=action.substring(action.indexOf('/')+1);String path=a.equals("complete")?(action.startsWith("delivery")?"complete-delivery":"complete-pickup"):a.equals("ready")?"ready-for-pickup":a;var before=state(id);assertThat(postAs(STAFF_GOVAP,"/staff/fulfillments/"+part(id)+"/"+path,"storeId=1&pickupCode=FORGED",true).statusCode()).isEqualTo(403);assertThat(state(id)).isEqualTo(before);
    }

    @Test
    void payAtStoreInconsistentPriorPaidStateOrDuplicateReceiptCannotCompleteAgain() throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.READY_FOR_PICKUP);jdbc.update("update dbo.orders set payment_status='PAID' where order_id=?",id);unchanged(id,()->act(id,FulfillmentAction.COMPLETE_PICKUP,data.pickupCode(part(id))),RuntimeException.class);assertThat(data.successfulPayments(id)).isZero();
    }

    @Test
    void pickupQueueUsesAssignedStoreTypeOpenStatesPaginationAndHidesAllCodes() throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.READY_FOR_PICKUP);String code=data.pickupCode(part(id));var filter=new FulfillmentQueueFilter(null,null,FulfillmentType.STORE_PICKUP,null,null,null,true);var q=data.asStaff(STAFF_THUDUC,()->staff.getQueue(filter,0));assertThat(q.getContent()).anyMatch(f->f.fulfillmentId().equals(part(id)));assertThat(q.getContent()).allSatisfy(f->{assertThat(f.storeId()).isEqualTo(data.storeId("OS-THUDUC"));assertThat(f.fulfillmentType()).isEqualTo(FulfillmentType.STORE_PICKUP);});assertThat(getAs(STAFF_THUDUC,"/staff/fulfillments?type=STORE_PICKUP&openOnly=true").body()).doesNotContain(code);assertThat(getAs(STAFF_THUDUC,"/staff/fulfillments/"+part(id)).body()).doesNotContain(code);
    }

    @ParameterizedTest @ValueSource(strings = {"delivery/prepare", "pickup/prepare"})
    void newActionsRequireCsrfForCookieBearerAndEncodedPaths(String action) throws Exception {
        long id=action.startsWith("delivery")?cod():pickup(PaymentMethod.PAY_AT_STORE,false);String path="/staff/fulfillments/"+part(id)+"/prepare";var before=state(id);assertThat(postAs(STAFF_THUDUC,path,"",false).statusCode()).isEqualTo(403);assertThat(send("POST",path,"","Authorization","Bearer "+cookie(STAFF_THUDUC).substring(14)).statusCode()).isEqualTo(403);assertThat(send("POST",path.replace("/prepare","/%70repare"),"","Cookie",cookie(STAFF_THUDUC)).statusCode()).isIn(400,403);assertThat(state(id)).isEqualTo(before);
    }

    @Test
    void absentAssignmentDeniesQueueAndMutationsUsingTheExistingJwt() throws Exception {
        long id=cod();revoke("ASSIGNMENT");assertThat(getAs(STAFF_THUDUC,"/staff/fulfillments").statusCode()).isEqualTo(403);var before=state(id);assertThat(postAs(STAFF_THUDUC,"/staff/fulfillments/"+part(id)+"/prepare","storeId=1",true).statusCode()).isEqualTo(403);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"khachhang1@example.com", "admin@oneshop.vn"})
    void otherRolesCannotReachNewQueueOrMutateOrders(String email) throws Exception {
        long id=cod();assertThat(getAs(email,"/staff/fulfillments").statusCode()).isEqualTo(403);var before=state(id);assertThat(postAs(email,"/staff/fulfillments/"+part(id)+"/prepare","",true).statusCode()).isEqualTo(403);assertThat(state(id)).isEqualTo(before);
    }

    @Test
    void multipleAssignmentsAllowPeerPickupThenRevocationBlocksTheNextMutationImmediately() throws Exception {
        assign(STAFF_GOVAP,"OS-THUDUC");long id=pickup(PaymentMethod.PAY_AT_STORE,false);assertThat(postAs(STAFF_GOVAP,"/staff/fulfillments/"+part(id)+"/prepare","",true).statusCode()).isEqualTo(302);jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=? and store_id=?",userId(STAFF_GOVAP),data.storeId("OS-THUDUC"));unchanged(id,()->data.asStaff(STAFF_GOVAP,()->staff.perform(part(id),FulfillmentAction.MARK_READY,null)),AccessDeniedException.class);
    }

    @Test
    void missingOrderActionReturns404WithoutMutation() throws Exception {
        long id=cod();var before=state(id);assertThat(postAs(STAFF_THUDUC,"/staff/fulfillments/"+Long.MAX_VALUE+"/prepare","",true).statusCode()).isEqualTo(404);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"PICKUP_READY", "PAY_AT_STORE_COMPLETE", "COD_COMPLETE"})
    void wrapperKeepsScopeFulfillmentReceiptHistoryAndTimestampsInOneRollbackTransaction(String stage) throws Exception {
        rollbackStep(stage.equals("COD_COMPLETE")?FulfillmentType.DELIVERY:FulfillmentType.STORE_PICKUP,stage.equals("COD_COMPLETE")?PaymentMethod.COD:PaymentMethod.PAY_AT_STORE,stage.equals("PICKUP_READY"),"HISTORY_SAVE");
    }

    @Test
    void racingPickupCompletionCreatesOnePaymentAndOneCompletionWithNoInventoryWrites() throws Exception {
        transitionRace(FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,FulfillmentAction.COMPLETE_PICKUP);
    }
}
