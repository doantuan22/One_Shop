package com.oneshop;

import com.oneshop.service.fulfillment.FulfillmentAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPEN-10.1 through the real HTTP stack: the Admin panel shows collected / refunded / retained and the preview, the
 * decision POSTs follow the partial refund rules, the customer sees a transparent "mô phỏng" refund of their own Order
 * only, and no other role, forged field or missing CSRF token can move a cent.
 */
class PartialRefundHttpDatabaseIntegrationTest extends V3RegressionTestSupport {

    private void toShipping(String staffEmail, long part) {
        for (FulfillmentAction step : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP)) {
            data.asStaff(staffEmail, () -> staff.perform(part, step, step == FulfillmentAction.SHIP ? "TRK-" + part : null));
        }
    }

    /** ONLINE paid Order of two Stores: GoVap delivered, ThuDuc delivery failed. */
    private long mixed() {
        long order = data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        data.markPaid(order);
        jdbc.update("update dbo.payments set refund_status = 'NONE' where order_id = ?", order);
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        long goVap = data.fulfillmentOf(order, "OS-GOVAP");
        toShipping(STAFF_GOVAP, goVap);
        data.asStaff(STAFF_GOVAP, () -> staff.perform(goVap, FulfillmentAction.COMPLETE_DELIVERY, null));
        toShipping(STAFF_THUDUC, thuDuc);
        data.asStaff(STAFF_THUDUC, () -> staff.perform(thuDuc, FulfillmentAction.FAIL_DELIVERY, "Khách vắng nhà, gọi 3 lần"));
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
    @DisplayName("OPEN101-H1 the Admin panel shows collected / refunded / retained, goods status and the refund preview")
    void adminPanel() throws Exception {
        long order = mixed();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");

        HttpResponse<String> page = getAs(ADMIN, "/admin/orders/" + order);

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("id=\"fulfillment-exception\"", "Đã hoàn", "Còn giữ lại", "os-refund-preview",
                "phí giao được giữ lại", "Chưa xác nhận được (chỉ ghi nhận, ngoại lệ vẫn mở)", path(order, thuDuc, "undeliverable"));
    }

    @Test
    @DisplayName("OPEN101-H2 UNCONFIRMED over HTTP: 302 pending-verification, the panel stays with the waiting notice; LOST then closes with a partial refund")
    void unconfirmedThenLost() throws Exception {
        long order = mixed();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");

        HttpResponse<String> pending = postAs(ADMIN, path(order, thuDuc, "undeliverable"),
                "goods=UNCONFIRMED&reason=" + enc("Đang chờ vận chuyển xác minh"), true);
        assertThat(location(pending)).endsWith("/admin/orders/" + order + "?exception=pending-verification");
        HttpResponse<String> after = getAs(ADMIN, "/admin/orders/" + order + "?exception=pending-verification");
        assertThat(after.body()).contains("id=\"fulfillment-exception\"", "os-awaiting-verification");
        assertThat(data.fulfillmentStatus(thuDuc)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ?", Integer.class, order)).isZero();

        HttpResponse<String> lost = postAs(ADMIN, path(order, thuDuc, "undeliverable"),
                "goods=LOST&reason=" + enc("Vận chuyển xác nhận thất lạc hàng"), true);
        assertThat(location(lost)).endsWith("exception=closed-partial");
        assertThat(getAs(ADMIN, "/admin/orders/" + order + "?exception=closed-partial").body())
                .contains("Hoàn tiền").doesNotContain("id=\"fulfillment-exception\"");
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'GOODS' "
                + "and status = 'SIMULATED_REFUNDED'", Integer.class, order)).isEqualTo(1);
        assertThat(location(postAs(ADMIN, path(order, thuDuc, "undeliverable"), "goods=LOST&reason=" + enc("Gửi lại lần hai"), true)))
                .endsWith("exception=refused:FAILURE_ALREADY_RESOLVED");
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ?", Integer.class, order)).isEqualTo(1);
    }

    @Test
    @DisplayName("OPEN101-H3 the customer sees a transparent 'hoàn tiền mô phỏng' on their own Order; the partially-closed badge; no staff name")
    void customerView() throws Exception {
        long order = mixed();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        postAs(ADMIN, path(order, thuDuc, "undeliverable"), "goods=RETURNED&reason=" + enc("Khách đồng ý hủy phần hàng này"), true);

        HttpResponse<String> page = getAs(ALICE, "/orders/" + order);

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("id=\"order-refunds\"", "Hoàn tiền",
                "Hoàn tất một phần: có phần không giao được", "Khách đồng ý hủy phần hàng này", "Hoàn tiền hàng không giao được");
        assertThat(page.body()).doesNotContain("Người xử lý").doesNotContain("Quản trị viên");
        // somebody else's Order: not found, nothing leaks
        assertThat(getAs(BOB, "/orders/" + order).statusCode()).isEqualTo(404);
        // the Admin sees who decided
        assertThat(getAs(ADMIN, "/admin/orders/" + order).body()).contains("id=\"order-refunds\"", "Người xử lý");
        assertThat(getAs(ADMIN, "/admin/payments/" + order).body()).contains("Hoàn tiền");
    }

    @Test
    @DisplayName("OPEN101-H4 Staff, Customer and guests cannot decide or read the Admin refund views; forged amount / status fields change nothing")
    void accessControl() throws Exception {
        long order = mixed();
        long thuDuc = data.fulfillmentOf(order, "OS-THUDUC");
        String body = "goods=RETURNED&reason=" + enc("Thử quyền truy cập") + "&amount=999999&refundAmount=1&component=SHIPPING"
                + "&orderStatus=COMPLETED&paymentStatus=REFUNDED&status=CANCELLED&fulfillmentId=1";

        for (String who : new String[] {STAFF_THUDUC, ALICE, BOB}) {
            assertThat(postAs(who, path(order, thuDuc, "undeliverable"), body, true).statusCode()).as(who).isEqualTo(403);
            assertThat(getAs(who, "/admin/payments/" + order).statusCode()).as(who + " payment detail").isEqualTo(403);
        }
        assertThat(postAs(null, path(order, thuDuc, "undeliverable"), body, true).statusCode()).isIn(302, 401, 403);
        assertThat(postAs(ADMIN, path(order, thuDuc, "undeliverable"), body, false).statusCode()).as("no CSRF").isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ?", Integer.class, order)).isZero();

        HttpResponse<String> ok = postAs(ADMIN, path(order, thuDuc, "undeliverable"), body, true);
        assertThat(ok.statusCode()).isEqualTo(302);
        BigDecimalHolder goods = new BigDecimalHolder(jdbc.queryForObject("select sum(subtotal) from dbo.order_items where fulfillment_id = ?",
                java.math.BigDecimal.class, thuDuc));
        assertThat(jdbc.queryForObject("select sum(amount) from dbo.payment_refunds where order_id = ?", java.math.BigDecimal.class, order))
                .as("only the goods of the part, never a forged amount").isEqualByComparingTo(goods.value());
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'SHIPPING'",
                Integer.class, order)).isZero();
        assertThat(data.paymentStatus(order)).isEqualTo("PAID");
    }

    private record BigDecimalHolder(java.math.BigDecimal value) {
    }
}
