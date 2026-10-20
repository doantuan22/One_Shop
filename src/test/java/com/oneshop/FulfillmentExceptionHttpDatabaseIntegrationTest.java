package com.oneshop;

import com.oneshop.service.fulfillment.FulfillmentAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPEN-10 through the real HTTP stack (production security chain, CSRF, roles, redirects): the Admin Order page shows
 * the failed delivery with the decisions, the two POSTs follow the rules, and no other role or forged field can move
 * anything. Same {@code *_test} database guard as the other V3 HTTP tests.
 */
class FulfillmentExceptionHttpDatabaseIntegrationTest extends V3RegressionTestSupport {

    private long failedCod() {
        long order = data.codDelivery("OS-THUDUC", SERUM, 1);
        long part = data.onlyFulfillment(order);
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP)) {
            data.asStaff(STAFF_THUDUC, () -> staff.perform(part, step, step == FulfillmentAction.SHIP ? "TRK-HTTP" : null));
        }
        data.asStaff(STAFF_THUDUC, () -> staff.perform(part, FulfillmentAction.FAIL_DELIVERY, "Khách không nhận, gọi 3 lần"));
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");
        return order;
    }

    private static String path(long order, long part, String action) {
        return "/admin/orders/" + order + "/fulfillments/" + part + "/" + action;
    }

    private String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    @Test
    @DisplayName("OPEN10-H1 the Admin Order page shows the failed delivery, the Store, the goods, the stock and both decisions")
    void adminSeesTheException() throws Exception {
        long order = failedCod();
        long part = data.onlyFulfillment(order);

        HttpResponse<String> page = getAs(ADMIN, "/admin/orders/" + order);

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("id=\"fulfillment-exception\"", "Phần giao #" + part, "Khách không nhận, gọi 3 lần",
                "Phương án A: giao lại", "Phương án B: xác nhận không thể giao", "id=\"redeliver-form\"",
                "id=\"undeliverable-form\"", "name=\"_csrf\"", "Tồn hiện tại tại chi nhánh");
        assertThat(page.body()).contains(path(order, part, "redeliver"), path(order, part, "undeliverable"));
        assertThat(getAs(ADMIN, "/admin/orders/" + cod()).body()).doesNotContain("id=\"fulfillment-exception\"");
        assertThat(getAs(ADMIN, "/admin/orders?attention=true").body()).contains("data-order-id=\"" + order + "\"");
        assertThat(getAs(ADMIN, "/admin").body()).contains("attention=true");
    }

    @Test
    @DisplayName("OPEN10-H2 redeliver over HTTP: 302 back to the Order, the part is PACKED, the panel is gone; a replay is a clean refusal")
    void redeliverOverHttp() throws Exception {
        long order = failedCod();
        long part = data.onlyFulfillment(order);

        HttpResponse<String> ok = postAs(ADMIN, path(order, part, "redeliver"),
                "goodsAtStore=true&reason=" + enc("Hàng còn nguyên tại chi nhánh"), true);

        assertThat(ok.statusCode()).isEqualTo(302);
        assertThat(location(ok)).endsWith("/admin/orders/" + order + "?exception=redelivered");
        assertThat(data.fulfillmentStatus(part)).isEqualTo("PACKED");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        HttpResponse<String> after = getAs(ADMIN, "/admin/orders/" + order + "?exception=redelivered");
        assertThat(after.body()).contains("Đã mở giao lại").doesNotContain("id=\"fulfillment-exception\"");

        HttpResponse<String> replay = postAs(ADMIN, path(order, part, "redeliver"),
                "goodsAtStore=true&reason=" + enc("Gửi lại lần hai"), true);
        assertThat(location(replay)).endsWith("exception=refused:FAILURE_ALREADY_RESOLVED");
        assertThat(getAs(ADMIN, "/admin/orders/" + order + "?exception=refused:FAILURE_ALREADY_RESOLVED").body())
                .contains("đã được xử lý trước đó");
        assertThat(data.fulfillmentStatus(part)).isEqualTo("PACKED");
    }

    @Test
    @DisplayName("OPEN10-H3 undeliverable over HTTP closes a COD Order; invalid input changes nothing")
    void undeliverableOverHttp() throws Exception {
        long order = failedCod();
        long part = data.onlyFulfillment(order);
        int stock = jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = (select top 1 "
                + "store_product_id from dbo.order_items where order_id = ?)", Integer.class, order);

        assertThat(location(postAs(ADMIN, path(order, part, "undeliverable"), "goods=RETURNED&reason=ab", true)))
                .endsWith("exception=invalid:reason");
        assertThat(location(postAs(ADMIN, path(order, part, "undeliverable"), "reason=" + enc("Có lý do hợp lệ"), true)))
                .endsWith("exception=invalid:goods");
        assertThat(location(postAs(ADMIN, path(order, part, "undeliverable"), "goods=EVERYTHING&reason=" + enc("Có lý do hợp lệ"), true)))
                .endsWith("exception=invalid:goods");
        assertThat(location(postAs(ADMIN, path(order, part, "redeliver"), "reason=" + enc("Có lý do hợp lệ"), true)))
                .endsWith("exception=invalid:goodsAtStore");
        assertThat(data.fulfillmentStatus(part)).isEqualTo("FAILED");

        HttpResponse<String> ok = postAs(ADMIN, path(order, part, "undeliverable"),
                "goods=RETURNED&reason=" + enc("Hàng đã về kho, kiểm nguyên vẹn"), true);

        assertThat(location(ok)).endsWith("exception=closed");
        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.fulfillmentStatus(part)).isEqualTo("CANCELLED");
        int restored = jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = (select top 1 "
                + "store_product_id from dbo.order_items where order_id = ?)", Integer.class, order);
        assertThat(restored).isEqualTo(stock + 1);
        assertThat(location(postAs(ADMIN, path(order, part, "undeliverable"), "goods=RETURNED&reason=" + enc("Gửi lại"), true)))
                .endsWith("exception=refused:FAILURE_ALREADY_RESOLVED");
        assertThat(jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? and "
                + "type = 'CANCEL_ORDER'", Integer.class, order)).isEqualTo(1);
        assertThat(getAs(ADMIN, "/admin/orders?attention=true").body()).doesNotContain("data-order-id=\"" + order + "\"");
    }

    @Test
    @DisplayName("OPEN10-H4 Staff, Customer and guests cannot decide; forged status, amount or quantity fields are ignored; CSRF is required even with a Bearer token")
    void accessControl() throws Exception {
        long order = failedCod();
        long part = data.onlyFulfillment(order);
        String body = "goods=RETURNED&goodsAtStore=true&reason=" + enc("Thử quyền truy cập")
                + "&orderStatus=COMPLETED&paymentStatus=PAID&status=PACKED&quantity=99&amount=1&storeId=2";

        for (String action : List.of("redeliver", "undeliverable")) {
            for (String who : new String[] {STAFF_THUDUC, ALICE}) {
                assertThat(postAs(who, path(order, part, action), body, true).statusCode()).as(who + " " + action).isEqualTo(403);
            }
            assertThat(postAs(null, path(order, part, action), body, true).statusCode()).as("guest " + action).isIn(302, 401, 403);
            assertThat(postAs(ADMIN, path(order, part, action), body, false).statusCode()).as("admin without CSRF " + action).isEqualTo(403);
            String bearer = jwt.generateToken(userDetails.loadUserByUsername(ADMIN));
            assertThat(send("POST", path(order, part, action), body, "Authorization", "Bearer " + bearer).statusCode())
                    .as("a Bearer header does not replace the CSRF token " + action).isEqualTo(403);
        }
        assertThat(getAs(STAFF_THUDUC, "/admin/orders/" + order).statusCode()).isEqualTo(403);
        assertThat(getAs(ALICE, "/admin/orders/" + order).statusCode()).isEqualTo(403);
        assertThat(data.fulfillmentStatus(part)).isEqualTo("FAILED");
        assertThat(data.orderStatus(order)).isEqualTo("NEEDS_ATTENTION");

        // as an Administrator the forged fields still decide nothing: only the fixed decision runs
        HttpResponse<String> ok = postAs(ADMIN, path(order, part, "redeliver"), body, true);
        assertThat(ok.statusCode()).isEqualTo(302);
        assertThat(data.fulfillmentStatus(part)).isEqualTo("PACKED");
        assertThat(data.orderStatus(order)).isEqualTo("PROCESSING");
        assertThat(data.paymentStatus(order)).isEqualTo("UNPAID");
    }

    @Test
    @DisplayName("OPEN10-H5 a Fulfillment that is not part of the Order is 404; a healthy Order refuses both decisions")
    void wrongOrderAndHealthyOrder() throws Exception {
        long order = failedCod();
        long healthy = cod();
        long healthyPart = data.onlyFulfillment(healthy);
        long part = data.onlyFulfillment(order);

        assertThat(postAs(ADMIN, path(order, healthyPart, "redeliver"), "goodsAtStore=true&reason=" + enc("Không thuộc đơn"), true)
                .statusCode()).isEqualTo(404);
        assertThat(postAs(ADMIN, path(999_999, part, "undeliverable"), "goods=LOST&reason=" + enc("Đơn không tồn tại"), true)
                .statusCode()).isEqualTo(404);
        assertThat(location(postAs(ADMIN, path(healthy, healthyPart, "undeliverable"), "goods=LOST&reason=" + enc("Đơn đang bình thường"), true)))
                .endsWith("exception=refused:FAILURE_ALREADY_RESOLVED");
        assertThat(data.fulfillmentStatus(healthyPart)).isEqualTo("CONFIRMED");
        assertThat(data.fulfillmentStatus(part)).isEqualTo("FAILED");
    }
}
