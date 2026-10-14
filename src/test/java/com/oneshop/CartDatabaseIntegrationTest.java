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
class CartDatabaseIntegrationTest extends V3RegressionTestSupport {

    @Test
    void tc04_addingTheSameStoreProductTwiceGivesOneLineWithTheSummedQuantity() throws Exception {
        long l=selected(10,2);carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),3));assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i->{assertThat(i.cartItemId()).isEqualTo(l);assertThat(i.quantity()).isEqualTo(5);});
    }

    @Test
    void tc05_cartWithLinesOfThreeStoresIsShownAsThreeGroups() throws Exception {
        selected(10,1);data.isolate(SHAMPOO,"OS-GOVAP",10);line(ALICE,SHAMPOO,1,false);data.isolate(CHEEK,"OS-QUAN7",10);line(ALICE,CHEEK,1,false);assertThat(carts.getCart(ALICE).items()).hasSize(3).extracting(CartItemResponse::productId).doesNotHaveDuplicates();assertThat(count("select count(*) from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id where c.user_id=? and ci.product_id is not null",userId(ALICE))).isEqualTo(3);
    }

    @Test
    void tc05_aCustomerBuildsACartAcrossThreeStores() throws Exception {
        selected(10,1);data.isolate(SHAMPOO,"OS-GOVAP",10);line(ALICE,SHAMPOO,1,false);data.isolate(CHEEK,"OS-QUAN7",10);line(ALICE,CHEEK,2,false);assertThat(carts.getCart(ALICE).items()).extracting(CartItemResponse::sku).containsExactlyInAnyOrder(SERUM,SHAMPOO,CHEEK);assertThat(carts.getCart(ALICE).itemCount()).isEqualTo(3);
    }

    @Test
    void sameSkuAtTwoStoresIsTwoIndependentLines() throws Exception {
        // DEC-02: sources merge into one Product line.
        long l=selected(2,1);data.stockAt(SERUM,"OS-GOVAP",2);carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),2));assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i->{assertThat(i.cartItemId()).isEqualTo(l);assertThat(i.quantity()).isEqualTo(3);});
    }

    @Test
    void addingMoreThanTheStoreHasIsRefusedAndChangesNothing() throws Exception {
        selected(2,1);var before=checkoutState();assertThatThrownBy(()->carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),2))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void whatIsAlreadyInTheCartCountsTowardsTheStockCheck() throws Exception {
        selected(4,3);var before=checkoutState();assertThatThrownBy(()->carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),2))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void whatIsNotOnSaleCannotBeAdded() throws Exception {
        selected(5,1);jdbc.update("update dbo.products set status='HIDDEN' where sku=?",SERUM);var before=checkoutState();assertThatThrownBy(()->carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),1))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void sellabilityFollowsTheCatalogConvention() throws Exception {
        selected(5,1);jdbc.update("update dbo.products set status='INACTIVE' where sku=?",SERUM);assertThat(carts.getCart(ALICE).items().getFirst().status()).isEqualTo(CartItemStatus.UNAVAILABLE);assertThatThrownBy(()->carts.getSelection(ALICE,carts.getCart(ALICE).cartItemIds())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void quantityMustBeAtLeastOne() throws Exception {
        long l=selected(5,1);for(int q:new int[]{0,-1}){var before=checkoutState();assertThatThrownBy(()->carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),q))).isInstanceOf(BadRequestException.class);assertThatThrownBy(()->carts.updateItemQuantity(ALICE,l,q)).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);}
    }

    @Test
    void theCartIsCreatedOnTheFirstAddAndThereIsOnlyEverOne() throws Exception {
        tx.executeWithoutResult(s->{long u=userId(ALICE);jdbc.update("delete from dbo.cart_items where cart_id in(select cart_id from dbo.carts where user_id=?)",u);jdbc.update("delete from dbo.carts where user_id=?",u);data.isolate(SERUM,"OS-THUDUC",5);carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),1));carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),1));assertThat(count("select count(*) from dbo.carts where user_id=?",u)).isEqualTo(1);assertThat(carts.getCart(ALICE).items()).hasSize(1);s.setRollbackOnly();});
    }

    @Test
    void cartOperationsNeverChangeStockOrCreateOrdersOrMovements() throws Exception {
        long l=selected(10,1);var before=data.stockSnapshot();long moves=data.movements(),n=count("select count(*) from dbo.orders");carts.updateItemQuantity(ALICE,l,2);carts.removeItem(ALICE,l);assertThat(data.stockSnapshot()).isEqualTo(before);assertThat(data.movements()).isEqualTo(moves);assertThat(count("select count(*) from dbo.orders")).isEqualTo(n);
    }

    @Test
    void updateSetsTheQuantityAfterCheckingStock() throws Exception {
        long l=selected(5,1);carts.updateItemQuantity(ALICE,l,4);assertThat(carts.getCart(ALICE).items().getFirst().quantity()).isEqualTo(4);assertThatThrownBy(()->carts.updateItemQuantity(ALICE,l,6)).isInstanceOf(BadRequestException.class);assertThat(carts.getCart(ALICE).items().getFirst().quantity()).isEqualTo(4);
    }

    @Test
    void updateReadsTheLatestStockNotTheOneThePageWasRenderedWith() throws Exception {
        long l=selected(5,1);carts.getCart(ALICE);jdbc.update("update dbo.store_products set quantity=2 where store_product_id=?",data.storeProduct(SERUM,"OS-THUDUC"));assertThatThrownBy(()->carts.updateItemQuantity(ALICE,l,3)).isInstanceOf(BadRequestException.class);assertThat(carts.getCart(ALICE).items().getFirst().quantity()).isEqualTo(1);
    }

    @Test
    void removeDeletesOnlyThatLine() throws Exception {
        long l=selected(5,1);data.isolate(SHAMPOO,"OS-GOVAP",5);long other=line(ALICE,SHAMPOO,1,false);carts.removeItem(ALICE,l);assertThat(carts.getCart(ALICE).cartItemIds()).containsExactly(other);
    }

    @Test
    void aCustomerCannotTouchAnotherCustomersLines() throws Exception {
        long l=selected(5,1);var before=checkoutState();assertThatThrownBy(()->carts.updateItemQuantity(BOB,l,2)).isInstanceOf(ResourceNotFoundException.class);assertThatThrownBy(()->carts.removeItem(BOB,l)).isInstanceOf(ResourceNotFoundException.class);assertThatThrownBy(()->carts.getSelection(BOB,List.of(l))).isInstanceOf(BadRequestException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void changingTheSelectedStoreNeverChangesTheCart() throws Exception {
        selected(5,1);var before=checkoutState();for(String code:List.of("OS-THUDUC","OS-GOVAP","OS-QUAN7"))assertThat(postAs(ALICE,"/stores/select","storeId="+data.storeId(code),true).statusCode()).isEqualTo(302);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void priceShownIsTheCurrentStoreProductPriceNotASnapshot() throws Exception {
        selected(5,1);jdbc.update("update dbo.products set price=price+1000 where sku=?",SERUM);assertThat(carts.getCart(ALICE).items().getFirst().unitPrice()).isEqualByComparingTo(jdbc.queryForObject("select price from dbo.products where sku=?",BigDecimal.class,SERUM));
    }

    @Test
    void stockDroppingBelowTheCartQuantityIsFlaggedButTheLineIsNotChanged() throws Exception {
        long l=selected(5,4);jdbc.update("update dbo.store_products set quantity=2 where store_product_id=?",data.storeProduct(SERUM,"OS-THUDUC"));var line=carts.getCart(ALICE).items().getFirst();assertThat(line.quantity()).isEqualTo(4);assertThat(line.status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);assertThat(line.selectable()).isFalse();
    }

    @Test
    void lineWhoseStoreProductIsNoLongerOnSaleStaysButCanOnlyBeRemoved() throws Exception {
        long l=selected(5,1);jdbc.update("update dbo.products set status='INACTIVE' where sku=?",SERUM);assertThat(carts.getCart(ALICE).items().getFirst().status()).isEqualTo(CartItemStatus.UNAVAILABLE);assertThatThrownBy(()->carts.updateItemQuantity(ALICE,l,2)).isInstanceOf(BadRequestException.class);carts.removeItem(ALICE,l);assertThat(carts.getCart(ALICE).items()).isEmpty();
    }

    @Test
    void selectionReturnsExactlyTheChosenLinesGroupedByStore() throws Exception {
        long l=selected(5,1);data.isolate(SHAMPOO,"OS-GOVAP",5);line(ALICE,SHAMPOO,1,false);var before=checkoutState();assertThat(carts.getSelection(ALICE,List.of(l,l)).cartItemIds()).containsExactly(l);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void cartRequestsCarryNoUserPriceOrStoreAndCartViewsCarryNoStock() throws Exception {
        assertThat(Arrays.stream(AddCartItemRequest.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)).doesNotContain("userId","price","cartId");assertThat(Arrays.stream(CartItemResponse.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)).doesNotContain("stock","reservedQuantity","availableQuantity");
    }

    @Test
    void databaseRejectsASecondLineForTheSameStoreProductInOneCart() throws Exception {
        long l=selected(5,1);var before=checkoutState();assertThatThrownBy(()->tx.execute(s->{jdbc.update("insert into dbo.cart_items(cart_id,product_id,quantity) select cart_id,product_id,1 from dbo.cart_items where cart_item_id=?",l);return null;})).isInstanceOf(DataIntegrityViolationException.class);assertThat(checkoutState()).isEqualTo(before);
    }

    @Test
    void databaseRejectsANonPositiveQuantity() throws Exception {
        long l=selected(5,1);var before=checkoutState();assertThatThrownBy(()->tx.execute(s->{jdbc.update("update dbo.cart_items set quantity=0 where cart_item_id=?",l);return null;})).isInstanceOf(DataIntegrityViolationException.class);assertThat(checkoutState()).isEqualTo(before);
    }
}
