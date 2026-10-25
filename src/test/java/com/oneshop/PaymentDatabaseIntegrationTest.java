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
class PaymentDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void pendingBelongsToOrderAndUsesItsAmountAndMethod() throws Exception {
        long id=online();var row=data.payment(data.attemptId(id));assertThat(row).containsEntry("order_id",id).containsEntry("method","ONLINE").containsEntry("status","PENDING");
        assertThat(row.get("amount")).isEqualTo(jdbc.queryForObject("select total_amount from dbo.orders where order_id=?",BigDecimal.class,id));assertThat(data.orderStatus(id)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void repeatedStartReusesPendingAndSchemaRemainsOneToMany() throws Exception {
        long id=online();long attempt=data.attemptId(id);assertThat(paymentService.createOnlinePaymentAttempt(ALICE,id).paymentId()).isEqualTo(attempt);
        assertThat(paymentService.createOnlinePaymentAttempt(ALICE,id).paymentId()).isEqualTo(attempt);assertThat(data.payments(id)).hasSize(1);
    }

    @Test
    void successConfirmsOrderWithAuditAndDoesNotTouchStockOrSnapshots() throws Exception {
        long id=online();var before=state(id);settle(id,true);assertThat(data.orderStatus(id)).isEqualTo("CONFIRMED");assertThat(data.paymentStatus(id)).isEqualTo("PAID");
        assertThat(state(id).get("stock")).isEqualTo(before.get("stock"));assertThat(state(id).get("movements")).isEqualTo(before.get("movements"));assertThat(data.orderHistory(id)).isEqualTo(2);
    }

    @Test
    void failureCancelsAndRestoresEverySnapshotToItsOwnStoreProduct() throws Exception {
        long id=data.onlineTwoStores("OS-THUDUC",SERUM,"OS-GOVAP",SHAMPOO);settle(id,false);
        assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");assertThat(data.cancelRestores(id)).isEqualTo(2);
        assertThat(data.quantityAt(SERUM,"OS-THUDUC")).isEqualTo(20);assertThat(data.quantityAt(SHAMPOO,"OS-GOVAP")).isEqualTo(20);
    }

    @ParameterizedTest
        @ValueSource(booleans = {true, false})
    void duplicateResultDoesNotRepeatAnyWrites(boolean success) throws Exception {
        long id=online();settle(id,success);var before=state(id);settle(id,success);assertThat(state(id)).isEqualTo(before);
        assertThat(data.cancelRestores(id)).isEqualTo(success?0:1);assertThat(data.successfulPayments(id)).isEqualTo(success?1:0);
    }

    @Test
    void ownershipAndMissingIdsAreCheckedForEveryOperation() throws Exception {
        long id=online();var before=state(id);assertThatThrownBy(()->paymentService.getOrderPayments(BOB,id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->payment.simulate(BOB,id,data.attemptId(id),PaymentReconciliationService.SimulatedResult.SUCCESS)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->payment.simulate(ALICE,id,Long.MAX_VALUE,PaymentReconciliationService.SimulatedResult.SUCCESS)).isInstanceOf(ResourceNotFoundException.class);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest
        @ValueSource(strings = {"amount", "method"})
    void inconsistentPaymentIsRefusedWithoutMutation(String field) throws Exception {
        long id=online();if(field.equals("amount"))jdbc.update("update dbo.payments set amount=amount+1 where order_id=?",id);else jdbc.update("update dbo.payments set method='COD' where order_id=?",id);
        unchanged(id,()->settle(id,true),RuntimeException.class);assertThat(data.orderStatus(id)).isEqualTo("PENDING_PAYMENT");assertThat(data.successfulPayments(id)).isZero();
    }

    @ParameterizedTest
        @EnumSource(value = PaymentMethod.class, names = {"COD", "PAY_AT_STORE"})
    void offlineOrdersStayConfirmedUnpaid(PaymentMethod method) throws Exception {
        long id=single(method==PaymentMethod.COD?FulfillmentType.DELIVERY:FulfillmentType.STORE_PICKUP,method,false);
        assertThat(data.orderStatus(id)).isEqualTo("CONFIRMED");assertThat(data.paymentStatus(id)).isEqualTo("UNPAID");assertThat(data.payments(id)).isEmpty();timeouts.scan();assertThat(data.orderStatus(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void orderNoLongerPendingCannotCompletePayment() throws Exception {
        long id=online();settle(id,true);act(id,FulfillmentAction.PREPARE);var before=state(id);
        assertThatThrownBy(()->paymentService.markOnlinePaymentSuccess(ALICE,id,data.attemptId(id))).isInstanceOf(BadRequestException.class);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest
        @ValueSource(booleans = {true, false})
    void paymentDoesNotChangeOtherOrdersInSameCheckout(boolean success) throws Exception {
        long neighbor=cod();var before=jdbc.queryForMap("select * from dbo.orders where order_id=?",neighbor);long id=online();settle(id,success);
        assertThat(jdbc.queryForMap("select * from dbo.orders where order_id=?",neighbor)).isEqualTo(before);assertThat(data.successfulPayments(neighbor)).isZero();
    }

    @Test
    void secondRestoreFailureRollsBackFirstFlushedItemPaymentOrderAndAudit() throws Exception {
        long id=data.onlineTwoStores("OS-THUDUC",SERUM,"OS-GOVAP",SHAMPOO);var before=state(id);
        var calls=new java.util.concurrent.atomic.AtomicInteger();doAnswer(i->{Object result=i.getArgument(0);entityManager.persist(result);entityManager.flush();if(calls.incrementAndGet()==2)throw new IllegalStateException("second restoration");return result;}).when(movementRows).save(any());
        assertThatThrownBy(()->settle(id,false)).isInstanceOf(IllegalStateException.class);reset(movementRows);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest
        @ValueSource(booleans = {true, false})
    void historyFailureRollsBackWholeResult(boolean success) throws Exception {
        long id=online();var before=state(id);doAnswer(i->{Object result=i.getArgument(0);entityManager.persist(result);entityManager.flush();throw new IllegalStateException("history flushed");}).when(orderHistory).save(any());
        assertThatThrownBy(()->settle(id,success)).isInstanceOf(IllegalStateException.class);reset(orderHistory);assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest
        @ValueSource(booleans = {true, false})
    void concurrentSameResultTransitionsAndRestoresAtMostOnce(boolean success) throws Exception {
        long id=online();var results=race(()->{settle(id,success);return null;},()->{settle(id,success);return null;});assertThat(results).containsExactly("ok","ok");
        assertThat(data.successfulPayments(id)).isEqualTo(success?1:0);assertThat(data.cancelRestores(id)).isEqualTo(success?0:1);
    }

    @Test
    void concurrentStartCreatesOnePendingAttempt() throws Exception {
        long id=online();var results=race(()->paymentService.createOnlinePaymentAttempt(ALICE,id),()->paymentService.createOnlinePaymentAttempt(ALICE,id));assertThat(results).containsExactly("ok","ok");assertThat(data.payments(id)).hasSize(1);
    }

    @Test
    void concurrentFailuresOfDifferentOrdersSharingStockDoNotLoseRestores() throws Exception {
        data.isolate(SERUM,"OS-THUDUC",10);long a=line(ALICE,SERUM,1,true);long first=checkout.placeOrder(ALICE,request(List.of(a),FulfillmentType.DELIVERY,PaymentMethod.ONLINE,null,false,null,null)).orders().getFirst().orderId();
        long b=line(ALICE,SERUM,1,true);long second=checkout.placeOrder(ALICE,request(List.of(b),FulfillmentType.DELIVERY,PaymentMethod.ONLINE,null,false,null,null)).orders().getFirst().orderId();assertThat(data.quantityAt(SERUM,"OS-THUDUC")).isEqualTo(8);
        assertThat(race(()->{settle(first,false);return null;},()->{settle(second,false);return null;})).containsExactly("ok","ok");assertThat(data.quantityAt(SERUM,"OS-THUDUC")).isEqualTo(10);assertThat(data.cancelRestores(first)).isEqualTo(1);assertThat(data.cancelRestores(second)).isEqualTo(1);
    }

    @Test
    void conflictingConcurrentResultsHaveOneWinner() throws Exception {
        long id=online();var results=race(()->{settle(id,true);return null;},()->{settle(id,false);return null;});
        assertThat(results).allMatch(x->x.equals("ok")||x instanceof PaymentConflictException);assertThat(data.orderStatus(id)).isIn("CONFIRMED","CANCELLED");
        assertThat(data.successfulPayments(id)).isBetween(0,1);assertThat(data.cancelRestores(id)).isBetween(0,1);
        if(data.orderStatus(id).equals("CANCELLED")&&data.successfulPayments(id)==1)assertThat(data.paymentStatus(id)).isEqualTo("REFUNDED");
    }

    @ParameterizedTest
        @ValueSource(booleans = {true, false})
    void httpWorkflowIgnoresForgedFieldsAndShowsResult(boolean success) throws Exception {
        long id=online();var result=postAs(ALICE,"/orders/"+id+"/payments/"+data.attemptId(id)+"/simulate","scenario="+(success?"SUCCESS":"FAILED")+"&amount=1&paid_at=2099-01-01&payment_status=PAID&storeId=2",true);
        assertThat(result.statusCode()).isEqualTo(302);assertThat(data.orderStatus(id)).isEqualTo(success?"CONFIRMED":"CANCELLED");assertThat(getAs(ALICE,"/orders/"+id+"/payments").statusCode()).isEqualTo(200);
    }

    @Test
    void httpOwnershipAndPaymentOrderMismatchReturn404() throws Exception {
        long first=online(),second=online();assertThat(getAs(BOB,"/orders/"+first+"/payments").statusCode()).isEqualTo(404);
        assertThat(postAs(ALICE,"/orders/"+first+"/payments/"+data.attemptId(second)+"/simulate","scenario=SUCCESS",true).statusCode()).isEqualTo(404);assertThat(data.successfulPayments(first)).isZero();
    }

    @Test
    void csrfRequiredForStartAndBothResultsEvenWithBearer() throws Exception {
        long id=online();String path="/orders/"+id+"/payments/"+data.attemptId(id)+"/simulate";var before=state(id);
        assertThat(postAs(ALICE,path,"scenario=SUCCESS",false).statusCode()).isEqualTo(403);assertThat(send("POST",path,"scenario=FAILED","Authorization","Bearer "+cookie(ALICE).substring("ONESHOP_TOKEN=".length())).statusCode()).isEqualTo(403);
        assertThat(state(id)).isEqualTo(before);
    }

    @ParameterizedTest
        @ValueSource(strings = {"guest", "staff.thuduc@example.com", "admin@example.com"})
    void clientPaymentRoutesAreCustomerOnly(String email) throws Exception {
        long id=online();String actor=email.equals("guest")?null:email;assertThat(getAs(actor,"/orders/"+id+"/payments").statusCode()).isEqualTo(actor==null?302:403);
        assertThat(data.orderStatus(id)).isEqualTo("PENDING_PAYMENT");
    }

    @ParameterizedTest
        @EnumSource(value = PaymentMethod.class, names = {"COD", "PAY_AT_STORE"})
    void httpOfflineOrderHasNoOnlineControlsAndCannotStart(PaymentMethod method) throws Exception {
        long id=single(method==PaymentMethod.COD?FulfillmentType.DELIVERY:FulfillmentType.STORE_PICKUP,method,false);var page=getAs(ALICE,"/orders/"+id+"/payments");assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).doesNotContain("/simulate");assertThatThrownBy(()->paymentService.createOnlinePaymentAttempt(ALICE,id)).isInstanceOf(BadRequestException.class);assertThat(data.payments(id)).isEmpty();
    }
}
