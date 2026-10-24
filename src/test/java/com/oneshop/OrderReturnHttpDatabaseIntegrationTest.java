package com.oneshop;

import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The return flow through the real HTTP stack: the Customer order page, the multipart request, the Administrator queue
 * and decisions, the Staff work list, and the guarantees that no other role, forged field or missing CSRF token can
 * request, decide or handle a return.
 */
class OrderReturnHttpDatabaseIntegrationTest extends V3RegressionTestSupport {

    @MockitoBean
    CloudinaryService cloudinary;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void fakeUploads() {
        when(cloudinary.uploadImage(any(), anyString())).thenReturn(
                new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/v1/returns/proof.jpg", "returns/proof"));
    }

    /** A COD delivery Order of one Store, delivered just now: COMPLETED. */
    private long delivered() {
        long order = cod();
        toStage(order, OrderFulfillmentStatus.COMPLETED);
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
        return order;
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    /** multipart/form-data POST with a CSRF token; {@code photos} files named {@code images}. */
    private HttpResponse<String> multipart(String email, String path, String reason, int photos, boolean csrf, String extraField)
            throws Exception {
        String boundary = "----oneshop" + System.nanoTime();
        String cookie = cookie(email);
        String token = "";
        if (csrf) {
            var page = send("GET", "/", "", "Cookie", cookie);
            var matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
            assertThat(matcher.find()).isTrue();
            token = matcher.group(1);
            String xsrf = page.headers().allValues("Set-Cookie").stream().filter(x -> x.startsWith("XSRF-TOKEN="))
                    .map(x -> x.split(";")[0]).findFirst().orElseThrow();
            cookie = cookie + "; " + xsrf;
        }
        StringBuilder body = new StringBuilder();
        if (reason != null) {
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"reason\"\r\n\r\n")
                    .append(reason).append("\r\n");
        }
        if (extraField != null) {
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(extraField)
                    .append("\"\r\n\r\n999999\r\n");
        }
        if (csrf) {
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n")
                    .append(token).append("\r\n");
        }
        for (int i = 0; i < photos; i++) {
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"images\"; filename=\"p")
                    .append(i).append(".jpg\"\r\nContent-Type: image/jpeg\r\n\r\nfakeimage\r\n");
        }
        body.append("--").append(boundary).append("--\r\n");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "text/html")
                .header("Cookie", cookie).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private long returnOf(long order) {
        return jdbc.queryForObject("select return_id from dbo.order_returns where order_id = ?", Long.class, order);
    }

    @Test
    @DisplayName("RETH-1 the whole flow over HTTP: request, Admin approval with simulated refund, Staff restock")
    void fullFlow() throws Exception {
        long order = delivered();
        long storeProduct = jdbc.queryForObject("select store_product_id from dbo.order_items where order_id = ?", Long.class, order);
        int stock = jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = ?", Integer.class, storeProduct);

        var page = getAs(ALICE, "/orders/" + order);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("id=\"order-return\"", "id=\"return-start\"", "id=\"return-deadline\"", "3 ngày");
        var form = getAs(ALICE, "/orders/" + order + "/return");
        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(form.body()).contains("id=\"return-form\"", "enctype=\"multipart/form-data\"", "name=\"images\"", "name=\"reason\"");

        var made = multipart(ALICE, "/orders/" + order + "/return", "Sản phẩm bị lỗi khi mở hộp", 2, true, null);
        assertThat(location(made)).endsWith("/orders/" + order + "?returned");
        long id = returnOf(order);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_return_images where return_id = ?", Integer.class, id)).isEqualTo(2);
        var waiting = getAs(ALICE, "/orders/" + order + "?returned");
        assertThat(waiting.body()).contains("data-return-status=\"REQUESTED\"", "Chờ quản trị viên duyệt", "Đã gửi yêu cầu trả hàng")
                .doesNotContain("id=\"return-start\"");
        assertThat(getAs(ALICE, "/orders/" + order + "/return").statusCode()).as("already asked: back to the order").isEqualTo(302);

        var queue = getAs(ADMIN, "/admin/returns");
        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(queue.body()).contains("data-return-id=\"" + id + "\"");
        var detail = getAs(ADMIN, "/admin/returns/" + id);
        assertThat(detail.body()).contains("id=\"return-approve\"", "id=\"return-reject\"", "Sản phẩm bị lỗi khi mở hộp");
        assertThat(getAs(ADMIN, "/admin/orders/" + order).body()).contains("id=\"order-return-link\"");
        assertThat(getAs(STAFF_THUDUC, "/staff/returns").body()).doesNotContain("data-return-item=");

        var approved = postAs(ADMIN, "/admin/returns/" + id + "/approve", "", true);
        assertThat(location(approved)).endsWith("/admin/returns/" + id + "?result=approved");
        assertThat(getAs(ADMIN, "/admin/returns/" + id + "?result=approved").body()).contains("Đã duyệt yêu cầu").doesNotContain("id=\"return-approve\"");
        var paid = getAs(ALICE, "/orders/" + order);
        assertThat(paid.body()).contains("data-return-status=\"APPROVED\"", "hoàn mô phỏng", "Hoàn tiền hàng trả lại");

        var work = getAs(STAFF_THUDUC, "/staff/returns");
        assertThat(work.statusCode()).isEqualTo(200);
        assertThat(work.body()).contains("Nhập lại kho", "Xử lý (không nhập kho)", "Sản phẩm bị lỗi khi mở hộp");
        long item = jdbc.queryForObject("select return_item_id from dbo.order_return_items where return_id = ?", Long.class, id);
        var restocked = postAs(STAFF_THUDUC, "/staff/returns/items/" + item + "/restock", "note=" + enc("Còn nguyên seal"), true);
        assertThat(location(restocked)).endsWith("/staff/returns?result=restocked");
        assertThat(jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = ?", Integer.class, storeProduct))
                .isEqualTo(stock + jdbc.queryForObject("select quantity from dbo.order_items where order_id = ?", Integer.class, order));
        assertThat(jdbc.queryForObject("select status from dbo.order_returns where return_id = ?", String.class, id)).isEqualTo("COMPLETED");
        assertThat(getAs(STAFF_THUDUC, "/staff/returns?result=restocked").body()).contains("Đã nhập lại kho");
        assertThat(location(postAs(STAFF_THUDUC, "/staff/returns/items/" + item + "/restock", "", true)))
                .endsWith("refused:RETURN_ALREADY_HANDLED");
        assertThat(data.orderStatus(order)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("RETH-2 access control: only the owner asks, only an Admin decides, only assigned Staff handle; forged fields and a missing CSRF token change nothing")
    void accessControl() throws Exception {
        long order = delivered();
        String url = "/orders/" + order + "/return";

        assertThat(getAs(BOB, url).statusCode()).as("somebody else's Order").isEqualTo(404);
        assertThat(multipart(BOB, url, "Đơn của người khác", 1, true, null).statusCode()).isIn(302, 404);
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_returns where order_id = ?", Integer.class, order)).isZero();
        assertThat(getAs(STAFF_THUDUC, url).statusCode()).isEqualTo(403);
        assertThat(getAs(ADMIN, url).statusCode()).isEqualTo(403);
        assertThat(getAs(null, url).statusCode()).isIn(302, 401, 403);
        assertThat(multipart(ALICE, url, "Thiếu CSRF", 1, false, null).statusCode()).as("no CSRF").isEqualTo(403);
        assertThat(location(multipart(ALICE, url, "Thiếu ảnh minh chứng", 0, true, null))).endsWith(url + "?error=images");
        assertThat(location(multipart(ALICE, url, "ok", 1, true, null))).endsWith(url + "?error=reason");
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_returns where order_id = ?", Integer.class, order)).isZero();

        // forged amount / status in the form are ignored: the amount is the goods value, the status starts REQUESTED
        assertThat(location(multipart(ALICE, url, "Sản phẩm bị lỗi khi mở hộp", 1, true, "refundAmount"))).endsWith("?returned");
        long id = returnOf(order);
        assertThat(jdbc.queryForObject("select status from dbo.order_returns where return_id = ?", String.class, id)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("select refund_amount from dbo.order_returns where return_id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo(jdbc.queryForObject("select sum(subtotal) from dbo.order_items where order_id = ?", java.math.BigDecimal.class, order));

        for (String who : new String[] {STAFF_THUDUC, ALICE, BOB}) {
            assertThat(postAs(who, "/admin/returns/" + id + "/approve", "", true).statusCode()).as(who).isEqualTo(403);
            assertThat(getAs(who, "/admin/returns").statusCode()).as(who + " queue").isEqualTo(403);
        }
        assertThat(postAs(ADMIN, "/admin/returns/" + id + "/approve", "", false).statusCode()).as("no CSRF").isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'RETURN'",
                Integer.class, order)).isZero();

        assertThat(location(postAs(ADMIN, "/admin/returns/" + id + "/approve", "amount=1&status=REJECTED&orderStatus=CANCELLED", true)))
                .endsWith("?result=approved");
        long item = jdbc.queryForObject("select return_item_id from dbo.order_return_items where return_id = ?", Long.class, id);
        for (String who : new String[] {ALICE, ADMIN}) {
            assertThat(postAs(who, "/staff/returns/items/" + item + "/restock", "", true).statusCode()).as(who).isEqualTo(403);
        }
        assertThat(getAs(ALICE, "/staff/returns").statusCode()).isEqualTo(403);
        // Staff of another Store: refused without touching anything
        assertThat(location(postAs(STAFF_GOVAP, "/staff/returns/items/" + item + "/restock", "", true))).endsWith("result=forbidden");
        assertThat(jdbc.queryForObject("select disposition from dbo.order_return_items where return_item_id = ?", String.class, item))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from dbo.payment_refunds where order_id = ? and component = 'RETURN'",
                Integer.class, order)).isEqualTo(1);
    }

    @Test
    @DisplayName("RETH-3 after 3 days the order page says so, offers no way to start, and a forced POST is refused")
    void windowOverHttp() throws Exception {
        long order = delivered();
        jdbc.update("update dbo.order_fulfillments set completed_at = dateadd(day, -4, sysdatetime()) where order_id = ?", order);

        var page = getAs(ALICE, "/orders/" + order);
        assertThat(page.body()).contains("id=\"return-blocked\"", "Đã quá 3 ngày").doesNotContain("id=\"return-start\"");
        assertThat(getAs(ALICE, "/orders/" + order + "/return").body()).contains("id=\"return-blocked\"").doesNotContain("id=\"return-form\"");
        assertThat(location(multipart(ALICE, "/orders/" + order + "/return", "Quá hạn nhưng vẫn gửi", 1, true, null)))
                .endsWith("/return?error=refused");
        assertThat(jdbc.queryForObject("select count(*) from dbo.order_returns where order_id = ?", Integer.class, order)).isZero();
    }
}
