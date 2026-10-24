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
class CheckoutDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void preparingShowsTheChosenLinesByStoreAndSuggestsTheDefaultAddress() throws Exception {
        long l=selected(7,2);var before=checkoutState();var p=checkout.prepare(ALICE,List.of(l));
        assertThat(p.selection().items()).hasSize(1);assertThat(carts.getCart(ALICE).items()).hasSize(1);assertThat(p.receiverName()).isNotBlank();assertThat(p.receiverPhone()).isNotBlank();assertThat(p.plan()).isNotNull();assertThat(checkoutState()).isEqualTo(before);
        assertThatThrownBy(()->checkout.prepare(BOB,List.of(l))).isInstanceOf(BadRequestException.class);assertThatThrownBy(()->checkout.prepare(ALICE,List.of())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void checkoutOfOneStoreCreatesOneSessionOneOrderAndItsSnapshots() throws Exception {
        long l=selected(7,2);long n=count("select count(*) from dbo.checkout_sessions");var r=place(List.of(l));long id=r.orders().getFirst().orderId();
        assertThat(count("select count(*) from dbo.checkout_sessions")).isEqualTo(n+1);assertThat(r.orders()).hasSize(1);assertThat(count("select count(*) from dbo.order_fulfillments where order_id=?",id)).isEqualTo(1);
        assertThat(count("select sum(quantity) from dbo.order_items where order_id=? and fulfillment_id is not null",id)).isEqualTo(2);assertThat(data.orderStatus(id)).isEqualTo("CONFIRMED");assertThat(r.totalAmount()).isEqualByComparingTo(jdbc.queryForObject("select price*2+30000 from dbo.products where sku=?",BigDecimal.class,SERUM));
    }

    @Test
    void tc06_checkoutOfThreeStoresCreatesOneSessionAndOneOrderPerStore() throws Exception {
        // V3: the Customer consents to independent COD orders. ONLINE is one Order with many Fulfillments.
        var r=splitCod();assertThat(r.orders()).hasSize(2);assertThat(r.orders()).allSatisfy(o->{assertThat(o.paymentMethod()).isEqualTo(PaymentMethod.COD);assertThat(count("select count(*) from dbo.order_fulfillments where order_id=?",o.orderId())).isEqualTo(1);});
        assertThat(count("select count(distinct store_id) from dbo.order_fulfillments where order_id in (?,?)",r.orders().get(0).orderId(),r.orders().get(1).orderId())).isEqualTo(2);
        assertThat(r.totalAmount()).isEqualByComparingTo(jdbc.queryForObject("select price*4+60000 from dbo.products where sku=?",BigDecimal.class,SERUM));
    }

    @Test
    void everyOrderStartsItsTimelineWithACreationEntryWrittenByTheSystem() throws Exception {
        var r=splitCod();for(var o:r.orders()){assertThat(data.orderHistory(o.orderId())).isEqualTo(1);assertThat(jdbc.queryForMap("select old_status,new_status,changed_by_user_id from dbo.order_status_history where order_id=?",o.orderId())).containsEntry("old_status",null).containsEntry("new_status","CONFIRMED").containsEntry("changed_by_user_id",null);assertThat(data.fulfillmentHistory(part(o.orderId()))).isEqualTo(1);}
    }

    @Test
    void stockIsDeductedWithOneOrderMovementPerLine() throws Exception {
        long l=selected(8,3);long id=place(List.of(l)).orders().getFirst().orderId();assertThat(jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?",Integer.class,data.storeProduct(SERUM,"OS-THUDUC"))).isEqualTo(5);
        assertThat(jdbc.queryForMap("select type,quantity_before,quantity_change,quantity_after,staff_id from dbo.inventory_movements where reference_order_id=?",id)).containsEntry("type","ORDER").containsEntry("quantity_before",8).containsEntry("quantity_change",-3).containsEntry("quantity_after",5).containsEntry("staff_id",null);
    }

    @Test
    void buyingTheWholeStockLeavesZeroNeverLess() throws Exception {
        long l=selected(4,4);long id=place(List.of(l)).orders().getFirst().orderId();assertThat(jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?",Integer.class,data.storeProduct(SERUM,"OS-THUDUC"))).isZero();assertThat(count("select count(*) from dbo.inventory_movements where reference_order_id=? and quantity_after=0",id)).isEqualTo(1);
    }

    @Test
    void onlyTheCheckedOutLinesLeaveTheCart() throws Exception {
        long l=selected(8,1);data.isolate(SHAMPOO,"OS-THUDUC",8);long other=line(ALICE,SHAMPOO,1,false);place(List.of(l));assertThat(carts.getCart(ALICE).cartItemIds()).containsExactly(other);
    }

    @Test
    void duplicateCartItemIdsAreOrderedOnce() throws Exception {
        long l=selected(8,1);long id=place(List.of(l,l,l)).orders().getFirst().orderId();assertThat(count("select sum(quantity) from dbo.order_items where order_id=?",id)).isEqualTo(1);assertThat(count("select count(*) from dbo.inventory_movements where reference_order_id=?",id)).isEqualTo(1);
    }

    @Test
    void tc08_checkoutRequestCannotCarryAPriceTotalStatusOrOwner() throws Exception {
        var names=Arrays.stream(PlaceOrderRequest.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();assertThat(names).doesNotContain("price","totalAmount","userId","orderStatus","paymentStatus","storeId");long l=selected(9,1);var r=postAs(ALICE,"/checkout",checkoutForm(l,FulfillmentType.DELIVERY,PaymentMethod.COD,null)+"&price=1&totalAmount=1&userId="+userId(BOB)+"&orderStatus=COMPLETED&paymentStatus=PAID&storeId=2",true);assertThat(r.statusCode()).isEqualTo(302);long id=jdbc.queryForObject("select max(order_id) from dbo.orders",Long.class);assertThat(data.orderStatus(id)).isEqualTo("CONFIRMED");assertThat(data.paymentStatus(id)).isEqualTo("UNPAID");assertThat(count("select count(*) from dbo.orders where order_id=? and user_id=?",id,userId(ALICE))).isEqualTo(1);
    }

    @Test
    void tc08_priceComesFromTheStoreProductAtTheMomentOfCheckoutNotFromTheCartPage() throws Exception {
        // V3 canonical price is Product.price; obsolete StoreProduct.price cannot override it.
        long l=selected(9,1);checkout.prepare(ALICE,List.of(l));jdbc.update("update dbo.products set price=price+1234 where sku=?",SERUM);jdbc.update("update dbo.store_products set price=1 where product_id=?",data.productId(SERUM));long id=place(List.of(l)).orders().getFirst().orderId();assertThat(jdbc.queryForObject("select unit_price from dbo.order_items where order_id=?",BigDecimal.class,id)).isEqualByComparingTo(jdbc.queryForObject("select price from dbo.products where sku=?",BigDecimal.class,SERUM));
    }

    @Test
    void tc14_orderItemsKeepTheirSnapshotWhenPriceAndProductNameChangeLater() throws Exception {
        long id=cod();var before=state(id).get("order_items");jdbc.update("update dbo.products set name=N'Changed after sale',price=price+1000 where sku=?",SERUM);assertThat(state(id).get("order_items")).isEqualTo(before);assertThat(customers.getOrder(ALICE,id).items()).extracting(OrderItemResponse::productName).contains(before.getFirst().get("product_name").toString());
    }

    @Test
    void aCustomerCannotCheckOutAnotherCustomersLines() throws Exception {
        long l=selected(9,1);var before=checkoutState();assertThatThrownBy(()->checkout.placeOrder(BOB,request(List.of(l),FulfillmentType.DELIVERY,PaymentMethod.COD,null,false,null,null))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void aCheckoutCanOnlyBeReadByItsOwner() throws Exception {
        long l=selected(9,1);var r=place(List.of(l));assertThat(checkout.getCheckout(ALICE,r.checkoutId()).orders()).hasSize(1);assertThatThrownBy(()->checkout.getCheckout(BOB,r.checkoutId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checkoutIsRefusedWhenALineIsNoLongerOnSale() throws Exception {
        long l=selected(9,1);jdbc.update("update dbo.products set status='INACTIVE' where sku=?",SERUM);var before=checkoutState();assertThatThrownBy(()->place(List.of(l))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void checkoutIsRefusedWhenStockDroppedSinceTheLineWasAdded() throws Exception {
        long l=selected(9,2);jdbc.update("update dbo.store_products set quantity=0 where product_id=?",data.productId(SERUM));var before=checkoutState();assertThatThrownBy(()->place(List.of(l))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void oneInvalidStoreGroupRefusesTheWholeCheckout() throws Exception {
        long l=selected(9,1);data.isolate(SHAMPOO,"OS-GOVAP",9);long x=line(ALICE,SHAMPOO,1,false);jdbc.update("update dbo.products set status='INACTIVE' where sku=?",SHAMPOO);var before=checkoutState();assertThatThrownBy(()->place(List.of(l,x))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void fulfillmentAndPaymentCombinationsAreChecked() throws Exception {
        long l=selected(9,1);for(var t:FulfillmentType.values())for(var m:PaymentMethod.values())if((t==FulfillmentType.DELIVERY&&m==PaymentMethod.PAY_AT_STORE)||(t==FulfillmentType.STORE_PICKUP&&m==PaymentMethod.COD)){var before=checkoutState();assertThatThrownBy(()->checkout.placeOrder(ALICE,request(List.of(l),t,m,data.storeId("OS-THUDUC"),false,null,null))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);}
    }

    @Test
    void deliveryNeedsAnAddressAndEveryOrderNeedsAReceiver() throws Exception {
        long l=selected(9,1);for(String blank:List.of(""," ")){var before=checkoutState();assertThatThrownBy(()->checkout.placeOrder(ALICE,new PlaceOrderRequest(List.of(l),FulfillmentType.DELIVERY,PaymentMethod.COD,blank,"0912345678",ADDRESS,null,false,null,null))).isInstanceOf(BadRequestException.class);assertThatThrownBy(()->checkout.placeOrder(ALICE,new PlaceOrderRequest(List.of(l),FulfillmentType.DELIVERY,PaymentMethod.COD,"Name","0912345678",blank,null,false,null,null))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);}
    }

    @Test
    void aStoreOnlyOffersTheWaysOfReceivingItSupports() throws Exception {
        long l=selected(9,1);jdbc.update("update dbo.stores set pickup_enabled=0 where store_id=?",data.storeId("OS-THUDUC"));var before=checkoutState();assertThatThrownBy(()->checkout.placeOrder(ALICE,request(List.of(l),FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,data.storeId("OS-THUDUC"),false,null,null))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void storeOfAnOrderComesFromTheCartLinesNotFromTheRequest() throws Exception {
        long l=selected(9,1);long id=place(List.of(l)).orders().getFirst().orderId();assertThat(count("select count(*) from dbo.order_fulfillments where order_id=? and store_id=?",id,data.storeId("OS-THUDUC"))).isEqualTo(1);
    }

    @Test
    void databaseRejectsAnOrderWithAForbiddenFulfillmentAndPaymentCombination() throws Exception {
        long id=cod();var before=state(id);assertThatThrownBy(()->tx.execute(s->{jdbc.update("update dbo.orders set payment_method='INVALID' where order_id=?",id);return null;})).isInstanceOf(DataIntegrityViolationException.class);assertThat(state(id)).isEqualTo(before);
    }

    @Test
    void databaseRejectsNegativeStock() throws Exception {
        long l=selected(9,1);var before=checkoutState();assertThatThrownBy(()->tx.execute(s->{jdbc.update("update dbo.store_products set quantity=-1 where product_id=?",data.productId(SERUM));return null;})).isInstanceOf(DataIntegrityViolationException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void databaseRejectsAnOrderItemWhoseSubtotalIsNotPriceTimesQuantity() throws Exception {
        long id=cod();var before=state(id);assertThatThrownBy(()->tx.execute(s->{jdbc.update("update dbo.order_items set subtotal=subtotal+1 where order_id=?",id);return null;})).isInstanceOf(DataIntegrityViolationException.class);assertThat(state(id)).isEqualTo(before);
    }
}
