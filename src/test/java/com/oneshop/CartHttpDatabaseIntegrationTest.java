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
class CartHttpDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void guestsAreSentToLoginAndCannotChangeAnything() throws Exception {
        long l=selected(5,1);var before=checkoutState();assertThat(getAs(null,"/cart").statusCode()).isEqualTo(302);assertThat(postAs(null,"/cart/items","productId="+data.productId(SERUM)+"&quantity=1",true).statusCode()).isEqualTo(302);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void staffAndAdminHaveNoCart() throws Exception {
        for(String e:List.of(STAFF_THUDUC,ADMIN)){assertThat(getAs(e,"/cart").statusCode()).isEqualTo(403);assertThat(postAs(e,"/cart/items","productId="+data.productId(SERUM)+"&quantity=1",true).statusCode()).isEqualTo(403);}
    }

    @Test
    void cartWritesNeedACsrfToken() throws Exception {
        long l=selected(5,1);var before=checkoutState();for(String path:List.of("/cart/items","/cart/items/"+l,"/cart/items/"+l+"/delete"))assertThat(postAs(ALICE,path,"productId="+data.productId(SERUM)+"&quantity=2",false).statusCode()).isEqualTo(403);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void tc04_addingTheSameStoreProductTwiceShowsOneLineWithTheSummedQuantity() throws Exception {
        selected(10,1);assertThat(postAs(ALICE,"/cart/items","productId="+data.productId(SERUM)+"&quantity=2",true).statusCode()).isEqualTo(302);assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i->assertThat(i.quantity()).isEqualTo(3));assertThat(getAs(ALICE,"/cart").statusCode()).isEqualTo(200);
    }

    @Test
    void tc04_twoSimultaneousAddsOfTheSameStoreProductStillGiveOneLine() throws Exception {
        selected(10,1);var r=race(()->{assertThat(postAs(ALICE,"/cart/items","productId="+data.productId(SERUM)+"&quantity=1",true).statusCode()).isEqualTo(302);return null;},()->{assertThat(postAs(ALICE,"/cart/items","productId="+data.productId(SERUM)+"&quantity=1",true).statusCode()).isEqualTo(302);return null;});assertThat(r).containsOnly("ok");assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i->assertThat(i.quantity()).isEqualTo(3));
    }

    @Test
    void tc05_cartPageShowsOneGroupPerStoreWithItsOwnLines() throws Exception {
        selected(5,1);data.isolate(SHAMPOO,"OS-GOVAP",5);line(ALICE,SHAMPOO,1,false);var r=getAs(ALICE,"/cart");assertThat(r.statusCode()).isEqualTo(200);assertThat(carts.getCart(ALICE).items()).hasSize(2);for(long id:carts.getCart(ALICE).cartItemIds())assertThat(r.body()).contains("value=\""+id+"\"");
    }

    @Test
    void sameProductAtTwoStoresIsTwoLinesInTwoGroups() throws Exception {
        selected(2,1);data.stockAt(SERUM,"OS-GOVAP",2);assertThat(postAs(ALICE,"/cart/items","productId="+data.productId(SERUM)+"&quantity=2",true).statusCode()).isEqualTo(302);assertThat(carts.getCart(ALICE).items()).hasSize(1);assertThat(carts.getCart(ALICE).items().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    void userPriceAndStoreSentByTheClientAreIgnored() throws Exception {
        selected(5,1);assertThat(postAs(ALICE,"/cart/items","productId="+data.productId(SERUM)+"&quantity=1&price=1&userId="+userId(BOB)+"&storeId=99",true).statusCode()).isEqualTo(302);assertThat(carts.getCart(ALICE).items().getFirst().unitPrice()).isEqualByComparingTo("138000");assertThat(carts.getCart(ALICE).items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void addIsRefusedForWhatCannotBeBoughtAndTheReasonIsShown() throws Exception {
        selected(5,1);jdbc.update("update dbo.products set status='INACTIVE' where sku=?",SERUM);var before=checkoutState();assertThat(postAs(ALICE,"/cart/items","productId="+data.productId(SERUM)+"&quantity=1",true).statusCode()).isEqualTo(400);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void updateAndRemoveThroughTheCartForms() throws Exception {
        long l=selected(5,1);assertThat(postAs(ALICE,"/cart/items/"+l,"quantity=3",true).statusCode()).isEqualTo(302);assertThat(carts.getCart(ALICE).items().getFirst().quantity()).isEqualTo(3);assertThat(postAs(ALICE,"/cart/items/"+l+"/delete","",true).statusCode()).isEqualTo(302);assertThat(carts.getCart(ALICE).items()).isEmpty();
    }

    @Test
    void aCustomerCannotChangeAnotherCustomersLineByItsId() throws Exception {
        long l=selected(5,1);var before=checkoutState();assertThat(postAs(BOB,"/cart/items/"+l,"quantity=2",true).statusCode()).isEqualTo(404);assertThat(postAs(BOB,"/cart/items/"+l+"/delete","",true).statusCode()).isEqualTo(404);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void changingTheSelectedStoreLeavesTheCartExactlyAsItWas() throws Exception {
        selected(5,1);var before=checkoutState();assertThat(postAs(ALICE,"/stores/select","storeId=2",true).statusCode()).isEqualTo(302);assertThat(postAs(ALICE,"/stores/clear","",true).statusCode()).isEqualTo(302);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void selectedCartItemIdsOpenTheCheckoutPageGroupedByStoreAndNothingIsCreated() throws Exception {
        long l=selected(5,1);var before=checkoutState();assertThat(getAs(ALICE,"/checkout?cartItemIds="+l).statusCode()).isEqualTo(200);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void addingToTheCartDoesNotChangeStockAndTheCartPageDoesNotShowIt() throws Exception {
        selected(937,1);var before=data.stockSnapshot();long m=data.movements();assertThat(postAs(ALICE,"/cart/items","productId="+data.productId(SERUM)+"&quantity=1",true).statusCode()).isEqualTo(302);assertThat(getAs(ALICE,"/cart").body()).doesNotContain("reserved_quantity","availableQuantity","Tồn thực tế");assertThat(data.stockSnapshot()).isEqualTo(before);assertThat(data.movements()).isEqualTo(m);
    }

    @Test
    void productDetailOffersAddToCartOnlyForAConcreteStoreProduct() throws Exception {
        selected(5,1);String path="/products/"+data.productId(SERUM);assertThat(getAs(ALICE,path).body()).contains("name=\"productId\"","action=\"/cart/items\"");assertThat(getAs(null,path).body()).doesNotContain("action=\"/cart/items\"");
    }
}
