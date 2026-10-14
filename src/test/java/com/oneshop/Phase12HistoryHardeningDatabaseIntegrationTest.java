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
class Phase12HistoryHardeningDatabaseIntegrationTest extends V3RegressionTestSupport {

    @ParameterizedTest @ValueSource(strings = {"COD", "PAY_AT_STORE", "ONLINE", "ONLINE_ATTEMPT", "DELIVERY_ONLINE", "LEGACY_COD", "LEGACY_PAY_AT_STORE"})
    void customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String method) throws Exception {
        boolean on=method.contains("ONLINE");boolean pickup=method.contains("PAY_AT_STORE")||method.equals("ONLINE")||method.equals("ONLINE_ATTEMPT");long id=single(pickup?FulfillmentType.STORE_PICKUP:FulfillmentType.DELIVERY,on?PaymentMethod.ONLINE:pickup?PaymentMethod.PAY_AT_STORE:PaymentMethod.COD,false);var items=state(id).get("order_items");var before=data.stockSnapshot();cancel(id);assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");assertThat(state(id).get("order_items")).isEqualTo(items);assertThat(data.cancelRestores(id)).isEqualTo(1);assertThat(data.orderHistory(id)).isEqualTo(2);assertThat(data.stockSnapshot().get(data.storeProduct(SERUM,"OS-THUDUC")).getFirst()).isEqualTo(before.get(data.storeProduct(SERUM,"OS-THUDUC")).getFirst()+1);cancel(id);assertThat(data.cancelRestores(id)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "PACKED", "SHIPPING", "COMPLETED", "READY_FOR_PICKUP", "PAID_ONLINE"})
    void cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String stage) throws Exception {
        // DEC-19: paid ONLINE before PREPARING is cancellable with full simulated refund.
        if(stage.equals("PAID_ONLINE")){long id=single(FulfillmentType.DELIVERY,PaymentMethod.ONLINE,true);cancel(id);assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");assertThat(data.paymentStatus(id)).isEqualTo("REFUNDED");assertThat(count("select count(*) from dbo.payments where order_id=? and refund_status='SIMULATED_REFUNDED' and refund_amount=amount",id)).isEqualTo(1);}
        else {long id=stage.equals("READY_FOR_PICKUP")?pickup(PaymentMethod.PAY_AT_STORE,false):cod();toStage(id,OrderFulfillmentStatus.valueOf(stage));unchanged(id,()->cancel(id),RuntimeException.class);}
    }

    @Test
    void cancellationRequiresOwnedActiveCustomerAndCsrfEvenForBearerAndEncodedPaths() throws Exception {
        long id=online();String path="/orders/"+id+"/cancel";var before=state(id);assertThat(postAs(BOB,path,"",true).statusCode()).isEqualTo(404);for(String e:List.of(ADMIN,STAFF_THUDUC))assertThat(postAs(e,path,"",true).statusCode()).isEqualTo(403);assertThat(postAs(ALICE,path,"",false).statusCode()).isEqualTo(403);assertThat(send("POST",path,"","Authorization","Bearer "+cookie(ALICE).substring(14)).statusCode()).isEqualTo(403);assertThat(postAs(ALICE,path.replace("/cancel","/%63ancel"),"",false).statusCode()).isIn(400,403);assertThat(state(id)).isEqualTo(before);
    }

    @Test
    void inactiveCustomerCannotCancelUsingAnAlreadyIssuedJwt() throws Exception {
        long id=online();String token=cookie(ALICE);jdbc.update("update dbo.users set status='INACTIVE' where email=?",ALICE);var before=state(id);assertThat(send("POST","/orders/"+id+"/cancel","","Cookie",token).statusCode()).isIn(302,403);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"PAYMENT_FLUSH","FIRST_RESTORE","HISTORY_FLUSH","OUTER_TRANSACTION"})
    void cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry(String failure) throws Exception {
        long id=online();var before=state(id);if(failure.equals("OUTER_TRANSACTION")){assertThatThrownBy(()->tx.execute(s->{cancel(id);entityManager.flush();throw new IllegalStateException("after cancellation flush");})).isInstanceOf(IllegalStateException.class);}else{inject(failure.equals("PAYMENT_FLUSH")?"CLOSE_PENDING_FLUSH":failure.equals("FIRST_RESTORE")?"RESTORE":"ORDER_HISTORY");assertThatThrownBy(()->cancel(id)).isInstanceOf(IllegalStateException.class);reset(movementRows,orderHistory,paymentRows);}assertThat(state(id)).isEqualTo(before);cancel(id);assertThat(data.cancelRestores(id)).isEqualTo(1);assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");
    }

    @Test
    void simultaneousDuplicateHttpCancellationsRestoreExactlyOnce() throws Exception {
        long id=online();var r=race(()->{assertThat(postAs(ALICE,"/orders/"+id+"/cancel","",true).statusCode()).isEqualTo(302);return null;},()->{assertThat(postAs(ALICE,"/orders/"+id+"/cancel","",true).statusCode()).isEqualTo(302);return null;});assertThat(r).containsOnly("ok");assertThat(data.cancelRestores(id)).isEqualTo(1);assertThat(data.orderHistory(id)).isEqualTo(2);assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");
    }

    @ParameterizedTest @ValueSource(strings={"PAYMENT_SUCCESS","PAYMENT_FAILURE","STAFF_PREPARE","STOCK_ADJUST"})
    void cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit(String rival) throws Exception {
        long id=rival.equals("STAFF_PREPARE")||rival.equals("STOCK_ADJUST")?cod():online();long sp=data.storeProduct(SERUM,"OS-THUDUC");var r=race(()->{cancel(id);return null;},()->{switch(rival){case "PAYMENT_SUCCESS"->settle(id,true);case "PAYMENT_FAILURE"->settle(id,false);case "STAFF_PREPARE"->act(id,FulfillmentAction.PREPARE);default->data.asStaff(STAFF_THUDUC,()->staffInventory.adjust(sp,new StockAdjustRequest(30,"Race physical count")));}return null;});assertThat(r.stream().filter("ok"::equals).count()).isGreaterThanOrEqualTo(1);assertThat(data.cancelRestores(id)).isLessThanOrEqualTo(1);assertThat(data.successfulPayments(id)).isLessThanOrEqualTo(1);if(data.orderStatus(id).equals("CANCELLED")){assertThat(data.cancelRestores(id)).isEqualTo(1);assertThat(data.fulfillmentStatus(part(id))).isEqualTo("CANCELLED");}else assertThat(data.fulfillmentStatus(part(id))).isEqualTo("PREPARING");
    }

    @Test
    void cancellationAfterStoreIsInactiveStillRestoresTheOriginalStockAndPreservesHistory() throws Exception {
        long id=online();var before=state(id).get("order_items");jdbc.update("update dbo.stores set status='INACTIVE' where store_id=?",data.storeId("OS-THUDUC"));cancel(id);assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");assertThat(data.cancelRestores(id)).isEqualTo(1);assertThat(state(id).get("order_items")).isEqualTo(before);
    }

    @Test
    void inconsistentSuccessfulReceiptBlocksCustomerCancellationAtomically() throws Exception {
        long id=online();jdbc.update("update dbo.payments set status='SUCCESS',paid_at=sysdatetime() where order_id=?",id);cancel(id);assertThat(data.cancelRestores(id)).isEqualTo(1);assertThat(data.paymentStatus(id)).isEqualTo("REFUNDED");assertThat(count("select count(*) from dbo.payments where order_id=? and refund_status='SIMULATED_REFUNDED'",id)).isEqualTo(1);
    }

    @Test
    void stockOverflowRejectsCancellationAndRollsBackClosedPendingAttempt() throws Exception {
        long id=online();jdbc.update("update dbo.store_products set quantity=2147483647 where store_product_id=?",data.storeProduct(SERUM,"OS-THUDUC"));unchanged(id,()->cancel(id),BadRequestException.class);assertThat(data.payment(data.attemptId(id))).containsEntry("status","PENDING");
    }

    @Test
    void internalCancellationPrimitivesRequireAnExistingTransaction() throws Exception {
        long id=online();var o=jdbc.queryForObject("select order_id from dbo.orders where order_id=?",Long.class,id);assertThat(o).isEqualTo(id);assertThatThrownBy(()->paymentService.closePendingForCustomerCancellation(new Order())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);assertThatThrownBy(()->inventory.restoreForCancelledOrder(new Order())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void concurrentCancellationsOfMultiItemOrdersWithOppositeItemOrderRestoreEverySnapshotExactlyOnce() throws Exception {
        data.isolate(SERUM,"OS-THUDUC",20);data.isolate(SHAMPOO,"OS-THUDUC",20);long a=line(ALICE,SERUM,1,true),b=line(ALICE,SHAMPOO,1,false);long first=place(List.of(a,b)).orders().getFirst().orderId();a=line(ALICE,SHAMPOO,1,true);b=line(ALICE,SERUM,1,false);long second=place(List.of(a,b)).orders().getFirst().orderId();var r=race(()->{cancel(first);return null;},()->{cancel(second);return null;});assertThat(r).containsOnly("ok");assertThat(data.cancelRestores(first)).isEqualTo(2);assertThat(data.cancelRestores(second)).isEqualTo(2);assertThat(jdbc.queryForList("select quantity from dbo.store_products where store_product_id in (?,?)",Integer.class,data.storeProduct(SERUM,"OS-THUDUC"),data.storeProduct(SHAMPOO,"OS-THUDUC"))).containsOnly(20);
    }

    @Test
    void failureOnSecondItemRestorationRollsBackFirstItemAndPaymentClosure() throws Exception {
        long id=data.onlineTwoStores("OS-THUDUC",SERUM,"OS-GOVAP",SHAMPOO);var before=state(id);var calls=new java.util.concurrent.atomic.AtomicInteger();doAnswer(i->{Object e=i.getArgument(0);entityManager.persist(e);entityManager.flush();if(calls.incrementAndGet()==2)throw new IllegalStateException("second restoration");return e;}).when(movementRows).save(any());assertThatThrownBy(()->cancel(id)).isInstanceOf(IllegalStateException.class);assertThat(calls.get()).isEqualTo(2);reset(movementRows);assertThat(state(id)).isEqualTo(before);cancel(id);assertThat(data.cancelRestores(id)).isEqualTo(2);
    }

    @Test
    void cancellingDifferentStoreCanCommitWhileFirstCancellationTransactionRemainsOpen() throws Exception {
        long first=cod();long second=data.payAtStorePickup("OS-GOVAP",SHAMPOO,1);holdWhileIndependent(()->cancel(first),()->{cancel(second);assertThat(jdbc.queryForObject("select order_status from dbo.orders where order_id=?",String.class,second)).isEqualTo("CANCELLED");});assertThat(data.cancelRestores(first)).isEqualTo(1);assertThat(data.cancelRestores(second)).isEqualTo(1);
    }

    @Test
    void existingSqlDataHasCompleteTimelinesConservedStockAndNoDuplicateOrderMovements() throws Exception {
        assertThat(count("select count(*) from dbo.orders o where not exists (select 1 from dbo.order_status_history h where h.order_id=o.order_id)")).isZero();assertThat(count("select count(*) from dbo.store_products where quantity<0 or reserved_quantity<0 or reserved_quantity>quantity")).isZero();assertThat(count("select count(*) from (select reference_order_id,store_product_id,type from dbo.inventory_movements where type in ('ORDER','CANCEL_ORDER') group by reference_order_id,store_product_id,type having count(*)>1) d")).isZero();assertThat(count("select count(*) from dbo.order_items oi left join dbo.order_fulfillments f on f.fulfillment_id=oi.fulfillment_id where f.fulfillment_id is null or f.order_id<>oi.order_id")).isZero();
    }

    @Test
    void adminStockEditMustLockBeforeLoadingQuantityToPreserveConcurrentMovementLedger() throws Exception {
        long l=selected(20,1);long sp=data.storeProduct(SERUM,"OS-THUDUC");long start=count("select coalesce(max(movement_id),0) from dbo.inventory_movements");var req=new StoreProductRequest();req.setPrice(new BigDecimal("129000"));req.setStatus(ActiveStatus.ACTIVE);req.setQuantity(29);var r=race(()->stockService.updateStoreProduct(sp,req,ADMIN),()->{inventory.adjustStock(sp,24,ADMIN,"Concurrent inventory count");return null;});assertThat(r).containsOnly("ok");var rows=jdbc.queryForList("select * from dbo.inventory_movements where store_product_id=? and movement_id>? order by movement_id",sp,start);assertThat(rows).hasSize(2);assertThat(rows.get(0).get("quantity_before")).isEqualTo(20);assertThat(rows.get(1).get("quantity_before")).isEqualTo(rows.get(0).get("quantity_after"));assertThat(jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?",Integer.class,sp)).isEqualTo(rows.get(1).get("quantity_after"));
    }
}
