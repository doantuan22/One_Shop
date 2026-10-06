package com.oneshop;

import com.oneshop.dto.response.StaffDashboardResponse;
import com.oneshop.dto.response.StaffStoreDashboardResponse;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.service.StaffOperationsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/** Real JWT, Tomcat, SiteMesh, services, transactions, scoped SQL queries; no mocked data or authorization. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class StaffOperationsDatabaseIntegrationTest {
    private static final String STAFF = "staff.thuduc@oneshop.vn", PEER = "staff.govap@oneshop.vn";
    @Autowired StaffOperationsService operations;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    private Phase9IntegrationScenario scenario;
    private Map<String, List<Map<String, Object>>> before, scopeBefore;

    @BeforeEach void remember() {
        SecurityContextHolder.clearContext();
        scenario = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port);
        before = scenario.snapshot(); scopeBefore = scenario.scope();
    }
    @AfterEach void unchanged() {
        SecurityContextHolder.clearContext();
        assertThat(scenario.snapshot()).as("All reads and fixture cleanup preserve nine business tables exactly").isEqualTo(before);
        assertThat(scenario.scope()).as("Assignment fixtures restored exactly").isEqualTo(scopeBefore);
    }
    private <T> T as(String email, Supplier<T> read) {
        var previous = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        SecurityContextHolder.setContext(context);
        try { return read.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private List<Long> ids(long store) {
        return jdbc.queryForList("select order_id from dbo.orders where store_id=? order by created_at desc,order_id desc", Long.class, store);
    }
    private static List<Long> htmlIds(String html) {
        var matcher = Pattern.compile("data-order-id=\"(\\d+)\"").matcher(html); List<Long> ids = new ArrayList<>();
        while (matcher.find()) ids.add(Long.parseLong(matcher.group(1))); return ids;
    }
    private void assertCounts(StaffStoreDashboardResponse stats, long store) {
        assertThat(stats.store().id()).isEqualTo(store);
        assertThat(stats.pendingOrderCount()).isEqualTo(count("select count(*) from dbo.orders where store_id=? and order_status in ('CONFIRMED','PREPARING','PACKED','SHIPPING')", store));
        assertThat(stats.waitingPickupCount()).isEqualTo(count("select count(*) from dbo.orders where store_id=? and fulfillment_type='STORE_PICKUP' and order_status='READY_FOR_PICKUP'", store));
        assertThat(stats.lowStockSkuCount()).isEqualTo(count("select count(*) from dbo.store_products where store_id=? and status='ACTIVE' and quantity between 1 and 5", store));
        assertThat(stats.outOfStockSkuCount()).isEqualTo(count("select count(*) from dbo.store_products where store_id=? and status='ACTIVE' and quantity=0", store));
    }
    private void assertDashboardHtml(String html, StaffDashboardResponse dashboard) {
        for (var stats : dashboard.stores()) {
            assertThat(html).contains("data-dashboard-store-id=\"" + stats.store().id() + "\"", stats.store().name(), stats.store().address());
            String section = html.substring(html.indexOf("data-dashboard-store-id=\"" + stats.store().id() + "\""));
            section = section.substring(0, section.indexOf("</section>"));
            assertThat(section).contains("data-metric=\"pending-orders\" data-value=\"" + stats.pendingOrderCount() + "\"",
                    "data-metric=\"waiting-pickup\" data-value=\"" + stats.waitingPickupCount() + "\"",
                    "data-metric=\"low-stock\" data-value=\"" + stats.lowStockSkuCount() + "\"",
                    "data-metric=\"out-of-stock\" data-value=\"" + stats.outOfStockSkuCount() + "\"");
        }
        assertThat(htmlIds(html)).containsExactlyElementsOf(dashboard.recentOrders().stream().map(o -> o.order().orderId()).toList());
    }
    private long fixtureOrder(long store, String status) {
        boolean pickup = status.equals("READY_FOR_PICKUP");
        return jdbc.queryForObject("""
                insert into dbo.orders(checkout_id,user_id,store_id,fulfillment_type,payment_method,payment_status,
                  order_status,receiver_name,receiver_phone,shipping_address,pickup_code,ready_at,total_amount,created_at,updated_at)
                output inserted.order_id
                select checkout_id,user_id,?,?,?,'UNPAID',?,'Phase10 read fixture','0912345678',?,?,?,total_amount,
                  '2026-10-06T09:00:00','2026-10-06T09:00:00' from dbo.orders where order_id=1
                """, Long.class, store, pickup ? "STORE_PICKUP" : "DELIVERY", pickup ? "PAY_AT_STORE" : "ONLINE",
                status, pickup ? null : "Phase10 fixture address", pickup ? "TEST1234" : null,
                pickup ? java.time.LocalDateTime.of(2026, 10, 6, 9, 0) : null);
    }

    @Test void dashboardAndLandingShowOnlyAssignedStoreWithExactSqlCounts() throws Exception {
        var dashboard = as(STAFF, operations::getDashboard);
        assertThat(dashboard.stores()).hasSize(1); assertCounts(dashboard.stores().get(0), 1);
        assertThat(dashboard.lowStockThreshold()).isEqualTo(5);
        for (String path : List.of("/staff", "/staff/", "/staff/dashboard")) {
            var response = scenario.get(path, scenario.account(STAFF));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("data-layout=\"staff\"", "<title>Tổng quan chi nhánh | OneShop Staff</title>")
                    .doesNotContain("data-dashboard-store-id=\"2\"", "data-store-id=\"2\"");
            assertDashboardHtml(response.body(), dashboard);
        }
    }
    @Test void lowStockBoundariesInactiveSkusAndOtherStoreInventoryAreCountedSeparately() throws Exception {
        try (var ignored = scenario.cleanup()) {
            var products = jdbc.queryForList("select store_product_id from dbo.store_products where store_id=1 order by 1", Long.class);
            assertThat(products).hasSizeGreaterThanOrEqualTo(5);
            jdbc.update("update dbo.store_products set quantity=100,status='ACTIVE' where store_id=1");
            int[] quantities = {0, 1, 5, 6, 0};
            for (int i = 0; i < quantities.length; i++) jdbc.update("update dbo.store_products set quantity=?,status=? where store_product_id=?",
                    quantities[i], i == 4 ? "INACTIVE" : "ACTIVE", products.get(i));
            jdbc.update("update dbo.store_products set quantity=0,status='ACTIVE' where store_id=2");
            var dashboard = as(STAFF, operations::getDashboard); var stats = dashboard.stores().get(0);
            assertCounts(stats, 1); assertThat(stats.lowStockSkuCount()).isEqualTo(2); assertThat(stats.outOfStockSkuCount()).isEqualTo(1);
            assertDashboardHtml(scenario.get("/staff/dashboard", scenario.account(STAFF)).body(), dashboard);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING_PAYMENT", "CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "READY_FOR_PICKUP", "COMPLETED", "CANCELLED"})
    void eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String status) {
        try (var ignored = scenario.cleanup()) {
            var original = as(STAFF, operations::getDashboard).stores().get(0);
            fixtureOrder(1, status); fixtureOrder(2, status); fixtureOrder(2, status);
            var stats = as(STAFF, operations::getDashboard).stores().get(0); assertCounts(stats, 1);
            assertThat(stats.pendingOrderCount()).isEqualTo(original.pendingOrderCount()
                    + (Set.of("CONFIRMED", "PREPARING", "PACKED", "SHIPPING").contains(status) ? 1 : 0));
            assertThat(stats.waitingPickupCount()).isEqualTo(original.waitingPickupCount() + (status.equals("READY_FOR_PICKUP") ? 1 : 0));
        }
    }
    @Test void queueHasOnlyAssignedOrdersAllRequiredColumnsAndNoTransitionControls() throws Exception {
        var response = scenario.get("/staff/orders", scenario.account(STAFF));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(htmlIds(response.body())).containsExactlyElementsOf(ids(1));
        assertThat(response.body()).contains("Khách hàng", "Hình thức nhận", "Thanh toán", "Trạng thái đơn", "Tổng tiền", "Ngày tạo")
                .doesNotContain("data-store-id=\"2\"", "/prepare", "/pack", "/ship", "/ready", "/complete");
        var orders = as(STAFF, () -> operations.getOrders(0));
        assertThat(orders.getTotalElements()).isEqualTo(ids(1).size());
        assertThat(orders.getContent()).allSatisfy(o -> { assertThat(o.order().storeId()).isEqualTo(1L); assertThat(o.customerName()).isNotBlank(); });
    }
    @Test void paginationAndTotalAreScopedInSqlWithStableOrderingAndBoundedPages() throws Exception {
        try (var ignored = scenario.cleanup()) {
            for (int i = 0; i < 23; i++) fixtureOrder(1, "CONFIRMED");
            for (int i = 0; i < 27; i++) fixtureOrder(2, "CONFIRMED");
            var own = ids(1); String cookie = scenario.account(STAFF);
            var first = as(STAFF, () -> operations.getOrders(0));
            assertThat(first.getTotalElements()).isEqualTo(own.size()); assertThat(first.getSize()).isEqualTo(20);
            assertThat(first.getContent().stream().map(o -> o.order().orderId())).containsExactlyElementsOf(own.subList(0, 20));
            var html = scenario.get("/staff/orders?page=0", cookie).body();
            assertThat(htmlIds(html)).containsExactlyElementsOf(own.subList(0, 20));
            assertThat(html).contains("/staff/orders?page=1").doesNotContain("data-store-id=\"2\"");
            assertThat(htmlIds(scenario.get("/staff/orders?page=1", cookie).body())).containsExactlyElementsOf(own.subList(20, own.size()));
            assertThat(htmlIds(scenario.get("/staff/orders?page=-1", cookie).body())).containsExactlyElementsOf(own.subList(0, 20));
            assertThat(as(STAFF, () -> operations.getOrders(999)).getContent()).isEmpty();
            var dashboard = as(STAFF, operations::getDashboard);
            assertThat(dashboard.recentOrders().stream().map(o -> o.order().orderId())).containsExactlyElementsOf(own.subList(0, 5));
        }
    }
    @ParameterizedTest @ValueSource(longs = {1, 2})
    void ownDeliveryAndPickupDetailReuseSnapshotsPaymentsTimelineAndHideStoredPickupCode(long id) throws Exception {
        String email = id == 1 ? STAFF : PEER;
        var response = as(email, () -> operations.getOrder(id)); var detail = response.detail(); var row = scenario.order(id);
        assertThat(detail.order().orderId()).isEqualTo(id); assertThat(detail.order().storeId()).isEqualTo(row.get("store_id"));
        assertThat(detail.order().receiverName()).isEqualTo(row.get("receiver_name"));
        assertThat(detail.order().receiverPhone()).isEqualTo(row.get("receiver_phone"));
        assertThat(detail.order().totalAmount()).isEqualTo(row.get("total_amount"));
        assertThat(detail.order().paymentStatus().name()).isEqualTo(row.get("payment_status"));
        assertThat(detail.order().orderStatus().name()).isEqualTo(row.get("order_status"));
        assertThat(detail.order().nextAction()).isNull(); assertThat(detail.order().pickupCode()).isNull();
        assertThat(detail.items()).hasSize((int) count("select count(*) from dbo.order_items where order_id=?", id));
        assertThat(detail.items()).extracting(i -> i.productName()).containsExactlyInAnyOrderElementsOf(
                jdbc.queryForList("select product_name from dbo.order_items where order_id=?", String.class, id));
        assertThat(detail.payments()).hasSize(scenario.receipts(id).size()); assertThat(detail.history()).hasSize(scenario.history(id).size());
        var page = scenario.get("/staff/orders/" + id, scenario.account(email)); assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-layout=\"staff\"", response.customerName(), detail.order().storeName(), detail.order().receiverName(), "Sản phẩm đã đặt")
                .doesNotContain("id=\"pickup-code\"", "/prepare", "/pack", "/ship", "/ready", "/complete");
        for (var item : detail.items()) assertThat(page.body()).contains(item.productName());
        if (id == 2) {
            assertThat(detail.order().readyAt()).isNotNull();
            assertThat(detail.history()).anySatisfy(h -> assertThat(h.note()).contains("[ẩn mã nhận hàng]"));
            assertThat(page.body()).contains("Sẵn sàng nhận lúc").doesNotContain(scenario.code(id));
            assertThat(scenario.get("/staff/orders/pickup/2", scenario.account(email)).body()).doesNotContain(scenario.code(id));
            assertThat(scenario.get("/orders/2", scenario.account(Phase9IntegrationScenario.CUSTOMER)).body()).contains(scenario.code(id));
        }
    }
    @ParameterizedTest @ValueSource(longs = {2, 0, -1, Long.MAX_VALUE})
    void crossStoreAndMissingDetailReturnSame404WithoutOrderResource(long id) throws Exception {
        assertThatThrownBy(() -> as(STAFF, () -> operations.getOrder(id))).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy đơn hàng của chi nhánh.");
        var page = scenario.get("/staff/orders/" + id, scenario.account(STAFF));
        assertThat(page.statusCode()).isEqualTo(404);
        assertThat(page.body()).contains("Không tìm thấy đơn hàng của chi nhánh.")
                .doesNotContain("id=\"order-status\"", "id=\"order-customer\"", "Sản phẩm đã đặt", scenario.code(2));
    }
    @Test void forgedQueryHeaderCookieAndGetBodyCannotChangeAssignedScope() throws Exception {
        String cookie = scenario.account(STAFF) + "; ONESHOP_STORE=2";
        String query = "?storeId=2&store_id=2&assignedStores=2&staffEmail=" + PEER + "&userId=6";
        var dashboard = as(STAFF, operations::getDashboard);
        assertDashboardHtml(scenario.get("/staff/dashboard" + query, cookie).body(), dashboard);
        assertThat(htmlIds(scenario.get("/staff/orders" + query, cookie).body())).containsExactlyElementsOf(ids(1));
        assertThat(scenario.get("/staff/orders/1" + query, cookie).statusCode()).isEqualTo(200);
        assertThat(scenario.get("/staff/orders/2?storeId=1&store_id=1", cookie).statusCode()).isEqualTo(404);
        for (String contentType : List.of("application/json", "application/x-www-form-urlencoded")) {
            for (String path : List.of("/staff/dashboard", "/staff/orders", "/staff/orders/2")) {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Cookie", cookie)
                        .header("X-Store-Id", "2").header("Content-Type", contentType)
                        .method("GET", HttpRequest.BodyPublishers.ofString(contentType.equals("application/json")
                                ? "{\"store_id\":2,\"storeId\":2}" : "store_id=2&storeId=2")).build();
                var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(path.endsWith("/2") ? 404 : 200);
                if (path.equals("/staff/dashboard")) assertDashboardHtml(response.body(), dashboard);
                if (path.equals("/staff/orders")) assertThat(htmlIds(response.body())).containsExactlyElementsOf(ids(1));
                assertThat(response.body()).doesNotContain("data-store-id=\"2\"", "data-dashboard-store-id=\"2\"", scenario.code(2));
            }
        }
        assertThat(scenario.post(cookie, "/staff/orders", "storeId=2&store_id=2").statusCode()).isEqualTo(405);
    }
    @Test void noActiveAssignmentDeniesAllDataRoutesEvenWithOldJwtAndForgedStore() throws Exception {
        String cookie = scenario.account(STAFF);
        long user = count("select user_id from dbo.users where email=?", STAFF);
        var assignment = jdbc.queryForMap("select * from dbo.staff_store_assignments where user_id=? and store_id=1", user);
        try {
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id=?", assignment.get("assignment_id"));
            assertThatThrownBy(() -> as(STAFF, operations::getDashboard)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> as(STAFF, () -> operations.getOrders(0))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> as(STAFF, () -> operations.getOrder(1L))).isInstanceOf(AccessDeniedException.class);
            for (String path : List.of("/staff/dashboard", "/staff/orders", "/staff/orders/1", "/staff/orders/2"))
                assertThat(scenario.get(path + "?store_id=1&storeId=1", cookie).statusCode()).as(path).isEqualTo(403);
            var landing = scenario.get("/staff", cookie);
            assertThat(landing.statusCode()).isEqualTo(200);
            assertThat(landing.body()).contains("id=\"no-store-scope\"").doesNotContain("data-dashboard-store-id", "Đơn cần xử lý", "<table");
        } finally { jdbc.update("update dbo.staff_store_assignments set status=? where assignment_id=?", assignment.get("status"), assignment.get("assignment_id")); }
        assertThat(scenario.get("/staff/dashboard", cookie).statusCode()).isEqualTo(200);
    }
    @Test void multipleAssignmentsKeepPerStoreCountsAndRevocationImmediatelyRemovesPeerResources() throws Exception {
        long user = count("select user_id from dbo.users where email=?", STAFF);
        long max = count("select coalesce(max(assignment_id),0) from dbo.staff_store_assignments");
        String cookie = scenario.account(STAFF);
        try {
            jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())", user, user);
            var dashboard = as(STAFF, operations::getDashboard);
            assertThat(dashboard.stores()).extracting(s -> s.store().id()).containsExactlyInAnyOrder(1L, 2L);
            dashboard.stores().forEach(s -> assertCounts(s, s.store().id()));
            assertDashboardHtml(scenario.get("/staff/dashboard", cookie).body(), dashboard);
            assertThat(as(STAFF, () -> operations.getOrders(0)).getContent()).extracting(o -> o.order().storeId()).contains(1L, 2L).doesNotContain(4L, 5L);
            assertThat(scenario.get("/staff/orders/2", cookie).statusCode()).isEqualTo(200);
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id>? and store_id=2", max);
            assertThat(scenario.get("/staff/orders/2", cookie).statusCode()).isEqualTo(404);
            assertThat(htmlIds(scenario.get("/staff/orders", cookie).body())).containsExactlyElementsOf(ids(1));
            assertThat(as(STAFF, operations::getDashboard).stores()).extracting(s -> s.store().id()).containsExactly(1L);
        } finally { jdbc.update("delete from dbo.staff_store_assignments where assignment_id>? and user_id=?", max, user); }
    }
    @ParameterizedTest @ValueSource(strings = {"khachhang1@example.com", "admin@oneshop.vn"})
    void otherRolesCannotOpenAnyStaffOperationsPage(String email) throws Exception {
        for (String path : List.of("/staff/dashboard", "/staff/orders", "/staff/orders/1"))
            assertThat(scenario.get(path, scenario.account(email)).statusCode()).as(path).isEqualTo(403);
    }
}
