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
class OrderStateDatabaseIntegrationTest extends V3RegressionTestSupport {

    @ParameterizedTest
        @EnumSource(FulfillmentType.class)
    void validTransitionHasOneHistoryActorTimestampNoteAndNoSideEffects(FulfillmentType type) throws Exception {
        long id=single(type,type==FulfillmentType.DELIVERY?PaymentMethod.COD:PaymentMethod.PAY_AT_STORE,false);var before=state(id);act(id,FulfillmentAction.PREPARE);
        assertThat(data.orderStatus(id)).isEqualTo("PROCESSING");assertThat(data.fulfillmentHistory(part(id))).isEqualTo(2);
        assertThat(jdbc.queryForMap("select top 1 * from dbo.order_fulfillment_status_history where fulfillment_id=? order by history_id desc",part(id))).containsEntry("changed_by_user_id",userId(STAFF_THUDUC));
        assertThat(state(id).get("stock")).isEqualTo(before.get("stock"));assertThat(state(id).get("movements")).isEqualTo(before.get("movements"));
    }

    @ParameterizedTest
        @CsvSource({"DELIVERY,CONFIRMED,CONFIRMED", "DELIVERY,CONFIRMED,PACKED", "DELIVERY,PREPARING,CONFIRMED",
                "DELIVERY,PREPARING,READY_FOR_PICKUP", "STORE_PICKUP,PREPARING,PACKED", "STORE_PICKUP,PREPARING,SHIPPING",
                "DELIVERY,COMPLETED,CANCELLED", "DELIVERY,COMPLETED,PREPARING", "STORE_PICKUP,CANCELLED,CONFIRMED",
                "STORE_PICKUP,CANCELLED,PREPARING", "DELIVERY,CONFIRMED,CANCELLED"})
    void invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType type, OrderStatus from, OrderStatus target) throws Exception {
        long id=single(type,type==FulfillmentType.DELIVERY?PaymentMethod.COD:PaymentMethod.PAY_AT_STORE,false);
        unchanged(id,()->legacyState.transition(id,from,target,null,null),BadRequestException.class);
    }

    @ParameterizedTest
        @ValueSource(booleans = {false, true})
    void historyFailureRollsBackAlreadyFlushedOrderAndOptionalFlushedHistory(boolean flushHistory) throws Exception {
        rollbackStep(FulfillmentType.DELIVERY,PaymentMethod.COD,false,flushHistory?"AFTER_HISTORY_FLUSH":"ORDER_FLUSH");
    }

    @RepeatedTest(3)
    void concurrentDuplicateHasOneWinnerAndExactlyOneAudit() throws Exception {
        transitionRace(FulfillmentType.DELIVERY,PaymentMethod.COD,FulfillmentAction.PREPARE);
    }

    @Test
    void packedVersusShippingCannotSkipFromStalePreparingIntent() throws Exception {
        long id=cod();act(id,FulfillmentAction.PREPARE);unchanged(id,()->act(id,FulfillmentAction.SHIP),FulfillmentConflictException.class);act(id,FulfillmentAction.PACK);act(id,FulfillmentAction.SHIP);assertThat(data.orderStatus(id)).isEqualTo("SHIPPING");
    }

    @ParameterizedTest
        @ValueSource(booleans = {true, false})
    void paymentOutcomesUseCentralPolicyAndKeepSystemHistory(boolean success) throws Exception {
        long id=online();settle(id,success);assertThat(data.orderStatus(id)).isEqualTo(success?"CONFIRMED":"CANCELLED");assertThat(data.paymentStatus(id)).isEqualTo(success?"PAID":"FAILED");assertThat(count("select count(*) from dbo.order_status_history where order_id=? and changed_by_user_id is null",id)).isEqualTo(2);
    }

    @Test
    void repeatedPaymentSuccessAfterPreparingDoesNotRepeatHistoryOrRewindOrder() throws Exception {
        long id=online();settle(id,true);act(id,FulfillmentAction.PREPARE);var before=state(id);settle(id,true);assertThat(state(id)).isEqualTo(before);assertThat(data.orderStatus(id)).isEqualTo("PROCESSING");
    }

    @Test
    void noteLimitIsCheckedBeforeWritesAndNullSystemActorIsStored() throws Exception {
        long id=cod();unchanged(id,()->act(id,FulfillmentAction.FAIL_DELIVERY,"x".repeat(501)),FulfillmentConflictException.class);
        assertThat(jdbc.queryForObject("select changed_by_user_id from dbo.order_fulfillment_status_history where fulfillment_id=?",Long.class,part(id))).isNull();
    }

    @Test
    void genericPrimitiveCannotUsePaymentEdgesAndPaymentHelpersRequireTransaction() throws Exception {
        long id=online();unchanged(id,()->legacyState.transition(id,OrderStatus.PENDING_PAYMENT,OrderStatus.CONFIRMED,null,"forged"),BadRequestException.class);
        assertThatThrownBy(()->paymentService.initializeOnlineAttempt(new Order())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
