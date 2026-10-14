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
class Phase9IntegrationWebDatabaseTest extends V3RegressionTestSupport {

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void tc15FullHttpLifecycleWithOptionalCatalogChanges(boolean changeCatalog) throws Exception {
        lifecycle(FulfillmentType.DELIVERY,PaymentMethod.COD,true,changeCatalog);lifecycle(FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,true,changeCatalog);lifecycle(FulfillmentType.STORE_PICKUP,PaymentMethod.ONLINE,true,changeCatalog);
    }

    @Test
    void mixedFailedPaymentRestoresOnlyItsOrderAndSiblingsStillCompleteOverHttp() throws Exception {
        long failed=data.onlineTwoStores("OS-THUDUC",SERUM,"OS-GOVAP",SHAMPOO);long sibling=data.payAtStorePickup("OS-THUDUC",CHEEK,1);var untouched=state(sibling).get("order_items");assertThat(postAs(ALICE,"/orders/"+failed+"/payments/"+data.attemptId(failed)+"/simulate","scenario=FAILED",true).statusCode()).isEqualTo(302);assertThat(data.cancelRestores(failed)).isEqualTo(2);toStage(sibling,OrderFulfillmentStatus.COMPLETED);assertThat(data.successfulPayments(sibling)).isEqualTo(1);assertThat(state(sibling).get("order_items")).isEqualTo(untouched);
    }

    @Test
    void allThreeStaffScopesAndOtherCustomerStayIsolatedWithinSameCheckout() throws Exception {
        long id=order(false);for(String store:List.of("OS-THUDUC","OS-GOVAP")){long f=data.fulfillmentOf(id,store);assertThat(getAs(staffFor(store),"/staff/fulfillments/"+f).statusCode()).isEqualTo(200);assertThat(getAs(store.equals("OS-THUDUC")?STAFF_GOVAP:STAFF_THUDUC,"/staff/fulfillments/"+f).statusCode()).isEqualTo(403);assertThat(getAs(STAFF_QUAN7,"/staff/fulfillments/"+f).statusCode()).isEqualTo(403);}assertThat(getAs(BOB,"/orders/"+id).statusCode()).isEqualTo(404);
    }

    @Test
    void csrfEncodedPathsRolesAndCrossFlowPrepareAreEnforcedOverHttp() throws Exception {
        long id=cod();var before=state(id);String path="/staff/fulfillments/"+part(id)+"/prepare";assertThat(postAs(STAFF_THUDUC,path,"",false).statusCode()).isEqualTo(403);assertThat(postAs(STAFF_THUDUC,path.replace("prepare","%70repare"),"",false).statusCode()).isIn(400,403);for(String e:List.of(ALICE,ADMIN))assertThat(postAs(e,path,"",true).statusCode()).isEqualTo(403);assertThat(state(id)).isEqualTo(before);act(id,FulfillmentAction.PREPARE);unchanged(id,()->act(id,FulfillmentAction.MARK_READY),FulfillmentConflictException.class);assertNoLegacyMutation(id);
    }

    @Test
    void multipleAssignmentsAllowOnlyActiveStoreAndUseAuthenticatedActor() throws Exception {
        assign(STAFF_GOVAP,"OS-THUDUC");long id=cod();data.asStaff(STAFF_GOVAP,()->staff.perform(part(id),FulfillmentAction.PREPARE,null));assertThat(count("select count(*) from dbo.order_fulfillment_status_history where fulfillment_id=? and changed_by_user_id=?",part(id),userId(STAFF_GOVAP))).isEqualTo(1);jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=? and store_id=1",userId(STAFF_GOVAP));unchanged(id,()->data.asStaff(STAFF_GOVAP,()->staff.perform(part(id),FulfillmentAction.PACK,null)),AccessDeniedException.class);
    }

    @ParameterizedTest @CsvSource({"DELIVERY,PAY_AT_STORE", "STORE_PICKUP,COD"})
    void invalidMatrixPairingsAreRejectedByBusinessAndActualSqlChecks(String type, String method) throws Exception {
        long l=selected(5,1);var before=checkoutState();assertThatThrownBy(()->checkout.placeOrder(ALICE,request(List.of(l),FulfillmentType.valueOf(type),PaymentMethod.valueOf(method),data.storeId("OS-THUDUC"),false,null,null))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
        // Approved V3 schema constrains enum domains; the cross-field matrix is enforced by the service.
        long id=cod();var saved=state(id);assertThatThrownBy(()->tx.execute(s->{jdbc.update("update dbo.orders set fulfillment_type='INVALID' where order_id=?",id);return null;})).isInstanceOf(DataIntegrityViolationException.class);assertThat(state(id)).isEqualTo(saved);
    }

    @Test
    void listQueryCountsStayConstantWhenThreeMoreStoreOrdersAreAddedAndDetailsRemainBounded() throws Exception {
        long id=order(false);var stats=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);try{data.asStaff(STAFF_THUDUC,()->staff.getQueue(FulfillmentQueueFilter.none(),0));stats.clear();data.asStaff(STAFF_THUDUC,()->staff.getQueue(FulfillmentQueueFilter.none(),0));long first=stats.getPrepareStatementCount();for(int i=0;i<3;i++)cod();stats.clear();data.asStaff(STAFF_THUDUC,()->staff.getQueue(FulfillmentQueueFilter.none(),0));assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(first+1);stats.clear();data.asStaff(STAFF_THUDUC,()->staff.getFulfillment(data.fulfillmentOf(id,"OS-THUDUC")));assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(18);}finally{stats.setStatisticsEnabled(false);}
    }

    @ParameterizedTest @ValueSource(strings = {"FAILED_RESTORE", "COD_COMPLETE", "PICKUP_READY", "PAY_AT_STORE_COMPLETE"})
    void composedSubsystemWritesRollbackTogetherIncludingSiblings(String stage) throws Exception {
        if(stage.equals("FAILED_RESTORE")){long id=data.onlineTwoStores("OS-THUDUC",SERUM,"OS-GOVAP",SHAMPOO);var before=state(id);inject("RESTORE");assertThatThrownBy(()->settle(id,false)).isInstanceOf(IllegalStateException.class);reset(movementRows);assertThat(state(id)).isEqualTo(before);}else rollbackStep(stage.equals("COD_COMPLETE")?FulfillmentType.DELIVERY:FulfillmentType.STORE_PICKUP,stage.equals("COD_COMPLETE")?PaymentMethod.COD:PaymentMethod.PAY_AT_STORE,stage.equals("PICKUP_READY"),"HISTORY_SAVE");
    }

    @Test
    void pickupSiblingCommitsWhileDeliveryPackTransactionRemainsOpen() throws Exception {
        long first=cod();toStage(first,OrderFulfillmentStatus.PREPARING);long second=data.payAtStorePickup("OS-GOVAP",SHAMPOO,1);long secondPart=part(second);holdWhileIndependent(()->act(first,FulfillmentAction.PACK),()->{data.asStaff(STAFF_GOVAP,()->staff.perform(secondPart,FulfillmentAction.PREPARE,null));assertThat(jdbc.queryForObject("select status from dbo.order_fulfillments where fulfillment_id=?",String.class,secondPart)).isEqualTo("PREPARING");});assertThat(data.fulfillmentStatus(part(first))).isEqualTo("PACKED");
    }

    @ParameterizedTest @ValueSource(strings = {"PAYMENT_RESULT", "DELIVERY_PREPARE", "COD_COMPLETE", "PICKUP_READY", "PICKUP_COMPLETE"})
    void sameOrderRepresentativeRacesKeepSiblingAggregatesAndNeverDuplicateReceiptsOrHistory(String stage) throws Exception {
        if(stage.equals("PAYMENT_RESULT")){long id=online();var r=race(()->{settle(id,true);return null;},()->{settle(id,true);return null;});assertThat(r).containsOnly("ok");assertThat(data.successfulPayments(id)).isEqualTo(1);assertThat(data.orderHistory(id)).isEqualTo(2);}else {boolean pick=stage.startsWith("PICKUP");transitionRace(pick?FulfillmentType.STORE_PICKUP:FulfillmentType.DELIVERY,pick?PaymentMethod.PAY_AT_STORE:PaymentMethod.COD,stage.equals("DELIVERY_PREPARE")?FulfillmentAction.PREPARE:stage.equals("COD_COMPLETE")?FulfillmentAction.COMPLETE_DELIVERY:stage.equals("PICKUP_READY")?FulfillmentAction.MARK_READY:FulfillmentAction.COMPLETE_PICKUP);}
    }
}
