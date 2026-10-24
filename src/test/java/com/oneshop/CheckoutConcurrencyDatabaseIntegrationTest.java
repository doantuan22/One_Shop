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
class CheckoutConcurrencyDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void tc07_twoCustomersRacingForTheLastUnitNeverOversell() throws Exception {
        raceStock(1,1);
    }

    @Test
    void tc07_whenStockCoversOnlyOneOfTwoLargerOrdersOnlyOneIsPlaced() throws Exception {
        raceStock(3,2);
    }

    @Test
    void customersBuyingTheSameSkusInOppositeOrderBothSucceedWithoutDeadlock() throws Exception {
        data.isolate(SERUM,"OS-THUDUC",10);data.isolate(SHAMPOO,"OS-THUDUC",10);long a=line(ALICE,SERUM,1,true),b=line(ALICE,SHAMPOO,1,false),c=line(BOB,SHAMPOO,1,true),d=line(BOB,SERUM,1,false);var r=race(()->place(List.of(a,b)),()->checkout.placeOrder(BOB,request(List.of(c,d),FulfillmentType.DELIVERY,PaymentMethod.COD,null,false,null,null)));assertThat(r).containsOnly("ok");assertThat(jdbc.queryForList("select quantity from dbo.store_products where store_product_id in (?,?)",Integer.class,data.storeProduct(SERUM,"OS-THUDUC"),data.storeProduct(SHAMPOO,"OS-THUDUC"))).containsOnly(8);
    }

    @Test
    void theSameCheckoutSubmittedTwiceAtOnceIsPlacedOnce() throws Exception {
        long l=selected(10,1);long n=count("select count(*) from dbo.orders");var r=race(()->place(List.of(l)),()->place(List.of(l)));assertThat(r.stream().filter("ok"::equals).count()).isEqualTo(1);assertThat(count("select count(*) from dbo.orders")).isEqualTo(n+1);assertThat(carts.getCart(ALICE).cartItemIds()).doesNotContain(l);
    }

    @Test
    void failureAfterTheFirstStoreGroupWasWrittenRollsBackTheWholeCheckout() throws Exception {
        long l=selected(4,4);data.stockAt(SERUM,"OS-GOVAP",4);data.isolate(SHAMPOO,"OS-GOVAP",4);long b=line(ALICE,SHAMPOO,1,false);var before=checkoutState();var calls=new java.util.concurrent.atomic.AtomicInteger();doAnswer(i->{Object e=i.getArgument(0);entityManager.persist(e);entityManager.flush();if(calls.incrementAndGet()==2)throw new IllegalStateException("second deduction");return e;}).when(movementRows).save(any());assertThatThrownBy(()->checkout.placeOrder(ALICE,request(List.of(l,b),FulfillmentType.DELIVERY,PaymentMethod.ONLINE,null,false,null,null))).isInstanceOf(IllegalStateException.class);reset(movementRows);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void afterAFailedCheckoutTheSameCartCanBeCheckedOutSuccessfully() throws Exception {
        long l=selected(4,1);var before=checkoutState();inject("ITEM");assertThatThrownBy(()->place(List.of(l))).isInstanceOf(IllegalStateException.class);reset(movementRows);assertThat(checkoutState()).isEqualTo(before);var r=place(List.of(l));assertThat(r.orders()).hasSize(1);assertThat(carts.getCart(ALICE).cartItemIds()).doesNotContain(l);
    }
}
