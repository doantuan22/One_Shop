package com.oneshop;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpResponse;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Promotion codes through the real HTTP stack: the checkout page lists the codes and shows the discount, placing the
 * order with a code, the order pages that show it, and the Administrator screens with their access rules.
 */
class PromotionHttpDatabaseIntegrationTest extends V3RegressionTestSupport {

    private int sequence;

    private String code() {
        return "H" + (System.nanoTime() % 1_000_000_000L) + (sequence++);
    }

    private String promo(String type, int value, int min, int usageLimit) {
        String code = code();
        jdbc.update("insert into dbo.promotions (code, name, discount_type, discount_value, min_order_amount, usage_limit, "
                + "used_count, per_user_limit, status) values (?, ?, ?, ?, ?, ?, 0, 3, 'ACTIVE')",
                code, "Khuyen mai thu " + code, type, value, min, usageLimit);
        return code;
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    private String page(long line, String code) {
        return "/checkout?cartItemIds=" + line + "&fulfillmentType=DELIVERY&paymentMethod=COD&shippingAddress=" + enc(ADDRESS)
                + (code == null ? "" : "&promotionCode=" + code);
    }

    @Test
    @DisplayName("PROMOH-1 the checkout page lists the codes (usable or not, with the reason) and shows the discount of the picked one")
    void checkoutPageShowsTheCodes() throws Exception {
        String usable = promo("FIXED", 20000, 0, 10);
        String tooBig = promo("FIXED", 20000, 50_000_000, 10);
        long line = selected(21, 1);

        HttpResponse<String> plain = getAs(ALICE, page(line, null));
        assertThat(plain.statusCode()).isEqualTo(200);
        assertThat(plain.body()).contains("id=\"checkout-promotions\"", "data-promotion=\"" + usable + "\"",
                "data-promotion=\"" + tooBig + "\"").doesNotContain("id=\"discount-row\"");
        assertThat(plain.body()).containsPattern("data-promotion=\"" + tooBig + "\" data-eligible=\"false\"|data-eligible=\"false\"[^>]*data-promotion=\"" + tooBig + "\"");

        HttpResponse<String> picked = getAs(ALICE, page(line, usable));
        assertThat(picked.body()).contains("id=\"discount-row\"", "id=\"discount-amount\"", usable);
        HttpResponse<String> notUsable = getAs(ALICE, page(line, tooBig));
        assertThat(notUsable.body()).contains("id=\"promotion-problem\"").doesNotContain("id=\"discount-row\"");
    }

    @Test
    @DisplayName("PROMOH-2 placing the order with a code: 302, the discount is on the Order and shown to the customer and the Admin; a refused code re-shows the page and creates nothing")
    void placeOrderWithACode() throws Exception {
        String code = promo("FIXED", 20000, 0, 10);
        long line = selected(21, 1);
        BigDecimal goods = jdbc.queryForObject("select price from dbo.products where sku = ?", BigDecimal.class, SERUM);

        HttpResponse<String> placed = postAs(ALICE, "/checkout", checkoutForm(line, FulfillmentType.DELIVERY, PaymentMethod.COD, null)
                + "&promotionCode=" + code, true);
        assertThat(placed.statusCode()).isEqualTo(302);
        assertThat(location(placed)).contains("/checkout/").endsWith("?success=placed");
        long order = jdbc.queryForObject("select max(order_id) from dbo.orders", Long.class);
        assertThat(jdbc.queryForObject("select discount_amount from dbo.orders where order_id = ?", BigDecimal.class, order))
                .isEqualByComparingTo("20000");
        assertThat(jdbc.queryForObject("select total_amount from dbo.orders where order_id = ?", BigDecimal.class, order))
                .isEqualByComparingTo(goods.subtract(new BigDecimal("20000")).add(new BigDecimal("30000")));

        HttpResponse<String> customer = getAs(ALICE, "/orders/" + order);
        assertThat(customer.statusCode()).isEqualTo(200);
        assertThat(customer.body()).contains("id=\"order-discount\"", code);
        assertThat(getAs(ADMIN, "/admin/orders/" + order).body()).contains("id=\"order-discount\"", code);

        int orders = jdbc.queryForObject("select count(*) from dbo.orders", Integer.class);
        long second = selected(21, 1);
        String exhausted = promo("FIXED", 20000, 0, 1);
        jdbc.update("update dbo.promotions set used_count = 1 where code = ?", exhausted);
        HttpResponse<String> refused = postAs(ALICE, "/checkout", checkoutForm(second, FulfillmentType.DELIVERY, PaymentMethod.COD, null)
                + "&promotionCode=" + exhausted, true);
        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.body()).contains("id=\"checkout-promotions\"");
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders", Integer.class)).isEqualTo(orders);
    }

    @Test
    @DisplayName("PROMOH-3 the Administrator screens: list, create, delete rules; nobody else, no forged use count, no missing CSRF")
    void adminScreens() throws Exception {
        String code = promo("PERCENT", 10, 100000, 5);

        HttpResponse<String> list = getAs(ADMIN, "/admin/promotions");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("data-promotion-code=\"" + code + "\"", "id=\"promotion-new\"");
        assertThat(getAs(ADMIN, "/admin/promotions/new").body()).contains("id=\"promotion-form\"");
        assertThat(getAs(ADMIN, "/admin/promotions/" + jdbc.queryForObject("select promotion_id from dbo.promotions where code = ?",
                Long.class, code) + "/edit").statusCode()).isEqualTo(200);

        for (String who : new String[] {STAFF_THUDUC, ALICE}) {
            assertThat(getAs(who, "/admin/promotions").statusCode()).as(who).isEqualTo(403);
            assertThat(postAs(who, "/admin/promotions", "code=X", true).statusCode()).as(who).isEqualTo(403);
        }

        String fresh = code();
        String body = "code=" + fresh + "&name=" + enc("Giảm thử") + "&discountType=FIXED&discountValue=15000&minOrderAmount=0"
                + "&usageLimit=5&perUserLimit=1&status=ACTIVE&usedCount=4&version=9";
        assertThat(postAs(ADMIN, "/admin/promotions", body, false).statusCode()).as("no CSRF").isEqualTo(403);
        HttpResponse<String> created = postAs(ADMIN, "/admin/promotions", body, true);
        assertThat(created.statusCode()).isEqualTo(302);
        assertThat(location(created)).contains("/edit?success=created");
        assertThat(jdbc.queryForObject("select used_count from dbo.promotions where code = ?", Integer.class, fresh.toUpperCase()))
                .as("a forged use count is ignored").isZero();

        HttpResponse<String> invalid = postAs(ADMIN, "/admin/promotions", body.replace("discountValue=15000", "discountValue=0"), true);
        assertThat(invalid.statusCode()).isEqualTo(200);
        assertThat(invalid.body()).contains("id=\"promotion-form\"");

        long usedId = jdbc.queryForObject("select promotion_id from dbo.promotions where code = ?", Long.class, code);
        jdbc.update("update dbo.promotions set used_count = 1 where promotion_id = ?", usedId);
        jdbc.update("insert into dbo.checkout_sessions (user_id, total_amount, status) select user_id, 0, 'CREATED' from dbo.users where email = ?", ALICE);
        long checkoutId = jdbc.queryForObject("select max(checkout_id) from dbo.checkout_sessions", Long.class);
        jdbc.update("insert into dbo.promotion_usages (promotion_id, user_id, checkout_id, discount_amount) select ?, user_id, ?, 0 from dbo.users where email = ?",
                usedId, checkoutId, ALICE);
        assertThat(location(postAs(ADMIN, "/admin/promotions/" + usedId + "/delete", "", true))).endsWith("/admin/promotions?error=in-use");
        assertThat(jdbc.queryForObject("select count(*) from dbo.promotions where promotion_id = ?", Integer.class, usedId)).isEqualTo(1);

        long spare = jdbc.queryForObject("select promotion_id from dbo.promotions where code = ?", Long.class, fresh.toUpperCase());
        assertThat(location(postAs(ADMIN, "/admin/promotions/" + spare + "/delete", "", true))).endsWith("/admin/promotions?success=deleted");
    }
}
