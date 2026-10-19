package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.ProductRequest;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Regressions found by tracing real V3 forms/services against isolated SQL Server. */
class RepositoryHardeningDatabaseIntegrationTest extends V3RegressionTestSupport {
    @org.springframework.beans.factory.annotation.Autowired com.oneshop.service.ProductService productAdmin;

    @Test void outOfRangeStaffDateFiltersCannotCauseAServerError() throws Exception {
        for(String date:List.of("+999999999-12-31","0001-01-01","9999-12-31")) {
            assertThat(getAs(STAFF_THUDUC,"/staff/fulfillments?from="+enc(date)+"&to="+enc(date)).statusCode())
                    .as(date).isEqualTo(200);
        }
    }

    @Test void chainStockCannotOverflowTheCartQuantity() {
        long id=selected(Integer.MAX_VALUE,1);
        data.stockAt(SERUM,"OS-GOVAP",Integer.MAX_VALUE);
        carts.updateItemQuantity(ALICE,id,Integer.MAX_VALUE);
        assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i->assertThat(i.selectable()).isTrue());
        assertThatThrownBy(()->carts.addItem(ALICE,AddCartItemRequest.forProduct(data.productId(SERUM),1)))
                .isInstanceOf(BadRequestException.class);
        assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i->assertThat(i.quantity()).isEqualTo(Integer.MAX_VALUE));
    }

    @Test void productServiceRejectsInvalidPricesWithoutDependingOnMvcValidation() {
        long id=data.productId(SERUM);
        var before=jdbc.queryForMap("select * from dbo.products where product_id=?",id);
        var form=new ProductRequest();form.setSku(before.get("sku").toString());
        for(String price:List.of("-1","0.001","10000000000000000")) {
            form.setPrice(new java.math.BigDecimal(price));
            assertThatThrownBy(()->productAdmin.updateProduct(id,form)).isInstanceOf(BadRequestException.class);
            assertThat(jdbc.queryForMap("select * from dbo.products where product_id=?",id)).isEqualTo(before);
        }
        form.setPrice(java.math.BigDecimal.TEN);form.setOriginalPrice(java.math.BigDecimal.ONE);
        assertThatThrownBy(()->productAdmin.updateProduct(id,form)).isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForMap("select * from dbo.products where product_id=?",id)).isEqualTo(before);
    }
    @Test void aCentralCartLineWithLegacyMetadataCanGrowAcrossStores() {
        long id = selected(2, 1);
        data.stockAt(SERUM, "OS-GOVAP", 5);
        carts.updateItemQuantity(ALICE, id, 6);
        assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i -> {
            assertThat(i.quantity()).isEqualTo(6);
            assertThat(i.selectable()).isTrue();
        });
    }

    @Test void legacyAddResolvesTheSkuButUsesChainStock() {
        selected(2, 1);
        data.stockAt(SERUM, "OS-GOVAP", 5);
        carts.addItem(ALICE, new AddCartItemRequest(data.storeProduct(SERUM, "OS-THUDUC"), 5));
        assertThat(carts.getCart(ALICE).items()).singleElement().satisfies(i -> assertThat(i.quantity()).isEqualTo(6));
    }

    @Test void conflictingProductAndStoreProductCannotCorruptCartProvenance() {
        selected(10, 1);
        data.stockAt(SHAMPOO, "OS-THUDUC", 5);
        var before = jdbc.queryForList("select * from dbo.cart_items order by cart_item_id");
        assertThatThrownBy(() -> carts.addItem(ALICE, new AddCartItemRequest(data.productId(SERUM),
                data.storeProduct(SHAMPOO, "OS-THUDUC"), 1))).isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForList("select * from dbo.cart_items order by cart_item_id")).isEqualTo(before);
    }

    @Test void stockAdjustmentBelowLegacyReservationIsABusinessRefusal() {
        selected(10, 1);
        long id = data.storeProduct(SERUM, "OS-THUDUC");
        jdbc.update("update dbo.store_products set reserved_quantity=2 where store_product_id=?", id);
        var before = jdbc.queryForList("select * from dbo.store_products where store_product_id=?", id);
        long movements = count("select count(*) from dbo.inventory_movements");
        assertThatThrownBy(() -> inventory.adjustStock(id, 1, ADMIN, "Audit reserved stock"))
                .isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForList("select * from dbo.store_products where store_product_id=?", id)).isEqualTo(before);
        assertThat(count("select count(*) from dbo.inventory_movements")).isEqualTo(movements);
    }

    @Test void anUnpricedCartLineIsNotSelectableLikeCheckout() {
        selected(10, 1);
        jdbc.update("update dbo.products set price=0 where product_id=?", data.productId(SERUM));
        assertThat(carts.getCart(ALICE).items()).singleElement()
                .satisfies(i -> assertThat(i.status()).isEqualTo(CartItemStatus.UNAVAILABLE));
    }

    @Test void adminProductFormRoundTripsBothOnlinePrices() throws Exception {
        long id = data.productId(SERUM);
        jdbc.update("update dbo.products set price=123456.78,original_price=150000 where product_id=?", id);
        var before = jdbc.queryForMap("select * from dbo.products where product_id=?", id);
        var page = getAs(ADMIN, "/admin/products/" + id + "/edit");
        assertThat(page.statusCode()).isEqualTo(200);
        String price = input(page.body(), "price"), original = input(page.body(), "originalPrice");
        assertThat(new java.math.BigDecimal(price)).isEqualByComparingTo("123456.78");
        assertThat(new java.math.BigDecimal(original)).isEqualByComparingTo("150000");
        String body = "sku="+enc(before.get("sku").toString())+"&name="+enc(before.get("name").toString())
                +"&description="+enc((String)before.get("description"))
                +"&categoryId="+before.get("category_id")+"&brandId="+before.get("brand_id")+"&status=ACTIVE"
                +"&price="+price+"&originalPrice="+original;
        assertThat(postAs(ADMIN,"/admin/products/"+id,body,true).statusCode()).isEqualTo(302);
        assertThat(jdbc.queryForObject("select price from dbo.products where product_id=?",java.math.BigDecimal.class,id))
                .isEqualByComparingTo("123456.78");
        assertThat(jdbc.queryForObject("select original_price from dbo.products where product_id=?",java.math.BigDecimal.class,id))
                .isEqualByComparingTo("150000");
    }

    @Test void invalidProductPricesStayOnTheFormAndPreserveDatabase() throws Exception {
        long id = data.productId(SERUM);
        var row = jdbc.queryForMap("select * from dbo.products where product_id=?",id);
        for (String prices : List.of("price=-1", "price=1.001", "price=100&originalPrice=50",
                "price=10000000000000000", "price=10&originalPrice=-1")) {
            String body="sku="+enc(row.get("sku").toString())+"&name="+enc(row.get("name").toString())
                    +"&description="+enc((String)row.get("description"))
                    +"&categoryId="+row.get("category_id")+"&brandId="+row.get("brand_id")+"&status=ACTIVE&"+prices;
            var result=postAs(ADMIN,"/admin/products/"+id,body,true);
            assertThat(result.statusCode()).as(prices).isEqualTo(200);
            assertThat(result.body()).contains("is-invalid");
            assertThat(jdbc.queryForMap("select * from dbo.products where product_id=?",id)).isEqualTo(row);
        }
    }

    private static String input(String html,String name) {
        var tag=Pattern.compile("<input\\b[^>]*\\bname=\""+name+"\"[^>]*>").matcher(html);
        assertThat(tag.find()).as("rendered input %s",name).isTrue();
        var value=Pattern.compile("\\bvalue=\"([^\"]*)\"").matcher(tag.group());
        assertThat(value.find()).isTrue();return value.group(1);
    }
}
