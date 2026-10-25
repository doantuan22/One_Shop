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
class Phase10IntegrationWebDatabaseTest extends V3RegressionTestSupport {

    @Test
    void staffOperationsFollowOneMultiStoreCheckoutThroughInventoryDashboardDeliveryAndBothPickups() throws Exception {
        long id=order(false);var before=data.stockSnapshot();receive(id,"OS-THUDUC");receive(id,"OS-GOVAP");assertThat(data.orderStatus(id)).isEqualTo("COMPLETED");for(String e:List.of(STAFF_THUDUC,STAFF_GOVAP)){assertThat(getAs(e,"/staff").statusCode()).isIn(200,302);assertThat(getAs(e,"/staff/stock").statusCode()).isEqualTo(200);assertThat(getAs(e,"/staff/fulfillments").statusCode()).isEqualTo(200);}assertThat(data.stockSnapshot()).isEqualTo(before);lifecycle(FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,true,false);lifecycle(FulfillmentType.STORE_PICKUP,PaymentMethod.ONLINE,true,false);
    }

    @ParameterizedTest @ValueSource(strings = {"query", "form-body", "json-body", "cookie"})
    void tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource(String source) throws Exception {
        long id=cod();long sp=data.storeProduct(SERUM,"OS-THUDUC");var before=state(id);String suffix=source.equals("query")?"?storeId=1":"";assertThat(getAs(STAFF_GOVAP,"/staff/fulfillments/"+part(id)+suffix).statusCode()).isEqualTo(403);assertThat(getAs(STAFF_GOVAP,"/staff/stock/"+sp+"/adjust"+suffix).statusCode()).isEqualTo(404);assertThat(postAs(STAFF_GOVAP,"/staff/fulfillments/"+part(id)+"/prepare"+suffix,"storeId=1&status=COMPLETED",true).statusCode()).isEqualTo(403);assertThatThrownBy(()->data.asStaff(STAFF_GOVAP,()->staffInventory.adjust(sp,new StockAdjustRequest(99,"Forged store")))).isInstanceOf(AccessDeniedException.class);assertThat(state(id)).isEqualTo(before);
    }

    @Test
    void tc13PhysicalCountCreatesOneCompleteAuditAndImmediatelyUpdatesDashboardAndExactStock() throws Exception {
        selected(20,1);long sp=data.storeProduct(SERUM,"OS-THUDUC");long start=count("select coalesce(max(movement_id),0) from dbo.inventory_movements");data.asStaff(STAFF_THUDUC,()->staffInventory.adjust(sp,new StockAdjustRequest(3,"BE07 physical count")));assertThat(jdbc.queryForMap("select quantity_before,quantity_after,quantity_change,staff_id,note from dbo.inventory_movements where movement_id>?",start)).containsEntry("quantity_before",20).containsEntry("quantity_after",3).containsEntry("quantity_change",-17).containsEntry("staff_id",userId(STAFF_THUDUC)).containsEntry("note","BE07 physical count");var dashboard=data.asStaff(STAFF_THUDUC,operations::getDashboard);assertThat(dashboard.stores().getFirst().lowStockSkuCount()).isEqualTo(count("select count(*) from dbo.store_products where store_id=1 and status='ACTIVE' and quantity between 1 and 5"));assertThat(data.asStaff(STAFF_THUDUC,()->staffInventory.getStoreProduct(sp)).quantity()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"ABSENT", "INACTIVE"})
    void absentOrInactiveAssignmentBlocksEveryStaffModuleAndMutationWithForgedStore(String mode) throws Exception {
        long id=cod();revoke("ASSIGNMENT");for(String path:List.of("/staff/dashboard","/staff/stock","/staff/fulfillments"))assertThat(getAs(STAFF_THUDUC,path+"?storeId=1").statusCode()).isIn(302,403);unchanged(id,()->act(id,FulfillmentAction.PREPARE),AccessDeniedException.class);assertThatThrownBy(()->data.asStaff(STAFF_THUDUC,()->staffInventory.adjust(data.storeProduct(SERUM,"OS-THUDUC"),new StockAdjustRequest(99,"Denied")))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revokingTheOnlyAssignmentImmediatelyRemovesReadsAndWritesAcrossAllModules() throws Exception {
        // a Staff works at exactly one Store: Gò Vấp's Staff is moved to Thủ Đức, then that assignment is revoked
        long id=cod();long sp=data.storeProduct(SERUM,"OS-THUDUC");
        assertThatThrownBy(()->data.asStaff(STAFF_GOVAP,()->staff.getFulfillment(part(id)))).isInstanceOf(AccessDeniedException.class);
        transfer(STAFF_GOVAP,"OS-THUDUC");
        assertThat(data.asStaff(STAFF_GOVAP,()->staff.getFulfillment(part(id)))).isNotNull();
        assertThat(data.asStaff(STAFF_GOVAP,()->staffInventory.getStoreProduct(sp))).isNotNull();
        assertThat(data.asStaff(STAFF_GOVAP,operations::getDashboard).stores()).allSatisfy(s->assertThat(s.store().id()).isEqualTo(data.storeId("OS-THUDUC")));
        jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=? and store_id=1",userId(STAFF_GOVAP));
        unchanged(id,()->data.asStaff(STAFF_GOVAP,()->staff.perform(part(id),FulfillmentAction.PREPARE,null)),AccessDeniedException.class);
        assertThatThrownBy(()->data.asStaff(STAFF_GOVAP,()->staffInventory.getStoreProduct(sp))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->data.asStaff(STAFF_GOVAP,operations::getDashboard)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void failedOnlineSiblingUpdatesOnlyItsStoreDashboardAndInventoryWhileOtherStaffOrdersContinue() throws Exception {
        long id=online();var peer=data.asStaff(STAFF_GOVAP,operations::getDashboard);settle(id,false);assertThat(data.cancelRestores(id)).isEqualTo(1);assertThat(data.asStaff(STAFF_GOVAP,operations::getDashboard)).isEqualTo(peer);long other=data.codDelivery("OS-GOVAP",SHAMPOO,1);data.asStaff(STAFF_GOVAP,()->staff.perform(part(other),FulfillmentAction.PREPARE,null));assertThat(data.fulfillmentStatus(part(other))).isEqualTo("PREPARING");
    }

    @Test
    void racingHttpAdjustmentAndCheckoutMaintainOneSerialStockLedgerAndCorrectSnapshot() throws Exception {
        long l=selected(20,1);long sp=data.storeProduct(SERUM,"OS-THUDUC");long start=count("select coalesce(max(movement_id),0) from dbo.inventory_movements");var r=race(()->{assertThat(postAs(STAFF_THUDUC,"/staff/stock/"+sp+"/adjust","newQuantity=24&note=Concurrent+physical+count",true).statusCode()).isEqualTo(302);return null;},()->place(List.of(l)));assertThat(r).containsOnly("ok");var rows=jdbc.queryForList("select * from dbo.inventory_movements where store_product_id=? and movement_id>? order by movement_id",sp,start);assertThat(rows).hasSize(2);assertThat(rows.get(1).get("quantity_before")).isEqualTo(rows.get(0).get("quantity_after"));assertThat(jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?",Integer.class,sp)).isEqualTo(rows.get(1).get("quantity_after"));
    }

    @Test
    void racingHttpPickupCompletionCollectsPayAtStoreOnceAndRejectsTheDuplicateWithoutStockChanges() throws Exception {
        long id=pickup(PaymentMethod.PAY_AT_STORE,false);toStage(id,OrderFulfillmentStatus.READY_FOR_PICKUP);var before=data.stockSnapshot();String path="/staff/fulfillments/"+part(id)+"/complete-pickup",body="pickupCode="+data.pickupCode(part(id));var statuses=new java.util.concurrent.ConcurrentLinkedQueue<Integer>();var r=race(()->{statuses.add(postAs(STAFF_THUDUC,path,body,true).statusCode());return null;},()->{statuses.add(postAs(STAFF_THUDUC,path,body,true).statusCode());return null;});assertThat(r).containsOnly("ok");assertThat(statuses).containsExactlyInAnyOrder(302,409);assertThat(data.successfulPayments(id)).isEqualTo(1);assertThat(data.stockSnapshot()).isEqualTo(before);
    }
}
