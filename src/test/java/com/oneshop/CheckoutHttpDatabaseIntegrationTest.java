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
class CheckoutHttpDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void onlyCustomersReachCheckout() throws Exception {
        long l=selected(6,1);String path="/checkout?cartItemIds="+l;assertThat(getAs(null,path).statusCode()).isEqualTo(302);for(String e:List.of(STAFF_THUDUC,ADMIN))assertThat(getAs(e,path).statusCode()).isEqualTo(403);assertThat(getAs(ALICE,path).statusCode()).isEqualTo(200);
    }

    @Test
    void checkoutPageShowsOneBlockPerStoreWithItsOwnChoicesAndNoPriceFields() throws Exception {
        long l=selected(6,1);String page=getAs(ALICE,"/checkout?cartItemIds="+l).body();assertThat(page).contains("name=\"fulfillmentType\"","name=\"paymentMethod\"","name=\"planSignature\"").doesNotContain("name=\"price\"","name=\"userId\"","name=\"storeProductId\"");
    }

    @Test
    void aStoreThatDoesNotDeliverOffersPickupOnly() throws Exception {
        long l=selected(6,1);jdbc.update("update dbo.stores set delivery_enabled=0 where store_id=?",data.storeId("OS-THUDUC"));var p=checkout.preview(ALICE,List.of(l),FulfillmentType.DELIVERY,PaymentMethod.COD,null,ADDRESS);assertThat(p.plan()).isNull();var before=checkoutState();assertThat(postAs(ALICE,"/checkout",checkoutForm(l,FulfillmentType.DELIVERY,PaymentMethod.COD,null),true).statusCode()).isEqualTo(400);assertThat(checkoutState()).isEqualTo(before);assertThat(getAs(ALICE,"/checkout?cartItemIds="+l+"&fulfillmentType=STORE_PICKUP&paymentMethod=PAY_AT_STORE&pickupStoreId=1").statusCode()).isEqualTo(200);
    }

    @Test
    void linesThatCannotBeCheckedOutLeadBackToTheCartWithTheReason() throws Exception {
        long l=selected(6,1);jdbc.update("update dbo.products set status='INACTIVE' where sku=?",SERUM);var page=getAs(ALICE,"/checkout?cartItemIds="+l);assertThat(page.statusCode()).isEqualTo(400);assertThat(page.body()).contains("/cart");
    }

    @Test
    void tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices() throws Exception {
        data.isolate(SERUM,"OS-THUDUC",2);data.stockAt(SERUM,"OS-GOVAP",2);long l=line(ALICE,SERUM,4,true);var p=checkout.preview(ALICE,List.of(l),FulfillmentType.DELIVERY,PaymentMethod.COD,null,ADDRESS);var before=checkoutState();String form=checkoutForm(l,FulfillmentType.DELIVERY,PaymentMethod.COD,null);assertThat(postAs(ALICE,"/checkout",form,true).statusCode()).isEqualTo(400);assertThat(checkoutState()).isEqualTo(before);var r=postAs(ALICE,"/checkout",form+"&splitCodConsent=true&planSignature="+p.plan().signature()+"&expectedTotal="+p.grandTotal()+"&price=1&totalAmount=1&storeId=99&userId="+userId(BOB),true);assertThat(r.statusCode()).isEqualTo(302);long checkoutId=Long.parseLong(java.net.URI.create(r.headers().firstValue("Location").orElseThrow()).getPath().substring("/checkout/".length()));var result=checkout.getCheckout(ALICE,checkoutId);assertThat(result.orders()).hasSize(2);assertThat(result.totalAmount()).isEqualByComparingTo(p.grandTotal());assertThat(getAs(ALICE,"/checkout/"+checkoutId).statusCode()).isEqualTo(200);
    }

    @Test
    void tc14_resultPageKeepsShowingTheSnapshotAfterThePriceChanges() throws Exception {
        long l=selected(6,1);var result=place(List.of(l));String name=result.orders().getFirst().items().getFirst().productName();jdbc.update("update dbo.products set name=N'Changed for HTTP regression',price=price+1000 where sku=?",SERUM);assertThat(getAs(ALICE,"/checkout/"+result.checkoutId()).body()).contains(name).doesNotContain("Changed for HTTP regression");
    }

    @Test
    void oneLineOutOfStockRefusesTheWholeCheckoutAndShowsWhy() throws Exception {
        long l=selected(6,2);jdbc.update("update dbo.store_products set quantity=0 where product_id=?",data.productId(SERUM));var before=checkoutState();var r=postAs(ALICE,"/checkout",checkoutForm(l,FulfillmentType.DELIVERY,PaymentMethod.COD,null),true);assertThat(r.statusCode()).isEqualTo(400);assertThat(r.body()).contains("/cart");assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void invalidChoicesAreRefusedAndThePageKeepsWhatWasChosen() throws Exception {
        long l=selected(6,1);var before=checkoutState();var r=postAs(ALICE,"/checkout",checkoutForm(l,FulfillmentType.DELIVERY,PaymentMethod.PAY_AT_STORE,null),true);assertThat(r.statusCode()).isEqualTo(400);assertThat(r.body()).contains("name=\"receiverName\"");assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void anotherCustomersLinesCannotBeCheckedOut() throws Exception {
        long l=selected(6,1);var before=checkoutState();assertThat(getAs(BOB,"/checkout?cartItemIds="+l).statusCode()).isEqualTo(400);assertThat(postAs(BOB,"/checkout",checkoutForm(l,FulfillmentType.DELIVERY,PaymentMethod.COD,null),true).statusCode()).isEqualTo(400);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void theSameFormSubmittedAgainDoesNotOrderTwice() throws Exception {
        long l=selected(6,1);String body=checkoutForm(l,FulfillmentType.DELIVERY,PaymentMethod.COD,null);assertThat(postAs(ALICE,"/checkout",body,true).statusCode()).isEqualTo(302);var before=checkoutState();assertThat(postAs(ALICE,"/checkout",body,true).statusCode()).isEqualTo(400);assertThat(checkoutState()).isEqualTo(before);
    }
}
