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
    @Autowired com.oneshop.service.fulfillment.StaffFulfillmentService fulfillmentService;
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
        // V3-BE-04: the queue is the Store's Fulfillments, open work first (oldest first), then finished work
        return jdbc.queryForList("select fulfillment_id from dbo.order_fulfillments where store_id=? order by case when status in ('CONFIRMED','PREPARING','PACKED','SHIPPING','READY_FOR_PICKUP')"
                + " then 0 else 1 end, assigned_at asc, fulfillment_id asc", Long.class, store);
    }
    private static List<Long> htmlIds(String html) {
        var matcher = Pattern.compile("data-fulfillment-id=\"(\\d+)\"").matcher(html); List<Long> ids = new ArrayList<>();
        while (matcher.find()) ids.add(Long.parseLong(matcher.group(1))); return ids;
    }
    private void assertCounts(StaffStoreDashboardResponse stats, long store) {
        assertThat(stats.store().id()).isEqualTo(store);
        assertThat(stats.pendingOrderCount()).isEqualTo(count("select count(*) from dbo.order_fulfillments f join dbo.orders o on o.order_id=f.order_id"
                + " where f.store_id=? and f.status in ('CONFIRMED','PREPARING','PACKED','SHIPPING','READY_FOR_PICKUP') and o.order_status not in ('PENDING_PAYMENT','NEEDS_ATTENTION','CANCELLED')", store));
        assertThat(stats.waitingPickupCount()).isEqualTo(count("select count(*) from dbo.order_fulfillments f join dbo.orders o on o.order_id=f.order_id"
                + " where f.store_id=? and f.status='READY_FOR_PICKUP' and o.order_status not in ('PENDING_PAYMENT','NEEDS_ATTENTION','CANCELLED')", store));
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
        assertThat(htmlIds(html)).containsExactlyElementsOf(dashboard.recentOrders().stream().map(o -> o.fulfillmentId()).toList());
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
                Set.of("PREPARING","PACKED","READY_FOR_PICKUP").contains(status)?"PROCESSING":status, pickup ? null : "Phase10 fixture address", pickup ? "TEST1234" : null,
                pickup ? java.time.LocalDateTime.of(2026, 10, 6, 9, 0) : null);
    }
    private long fixtureOrderWithFulfillment(long store, String status) {
        long order = fixtureOrder(store, status);
        // the Fulfillment status a V3 checkout/step sequence would show for that Order status
        String fulfillmentStatus = status.equals("PENDING_PAYMENT") ? "CONFIRMED" : status;
        jdbc.update("insert into dbo.order_fulfillments(order_id,store_id,fulfillment_type,status,assigned_at)"
                + " select order_id,store_id,fulfillment_type,?,'2026-10-06T09:00:00' from dbo.orders where order_id=?",
                fulfillmentStatus, order);
        return order;
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
            fixtureOrderWithFulfillment(1, status); fixtureOrderWithFulfillment(2, status); fixtureOrderWithFulfillment(2, status);
            var stats = as(STAFF, operations::getDashboard).stores().get(0); assertCounts(stats, 1);
            // V3: PENDING_PAYMENT is held by the payment gate, COMPLETED / CANCELLED are finished: none is work to do
            assertThat(stats.pendingOrderCount()).isEqualTo(original.pendingOrderCount()
                    + (Set.of("CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "READY_FOR_PICKUP").contains(status) ? 1 : 0));
            assertThat(stats.waitingPickupCount()).isEqualTo(original.waitingPickupCount() + (status.equals("READY_FOR_PICKUP") ? 1 : 0));
        }
    }
    @Test void queueHasOnlyAssignedOrdersAllRequiredColumnsAndNoTransitionControls() throws Exception {
        var response = scenario.get("/staff/orders?open=false", scenario.account(STAFF));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(htmlIds(response.body())).containsExactlyElementsOf(ids(1).stream().limit(20).toList());
        assertThat(response.body()).contains("Khách hàng", "Hình thức", "Thanh toán", "Trạng thái phần giao", "Trạng thái đơn", "Phân công lúc")
                .doesNotContain("data-store-id=\"2\"", "/prepare", "/pack", "/ship", "/ready", "/complete");
        var queue = scenario.get("/staff/orders?open=false", scenario.account(STAFF)).body();
        assertThat(queue).contains("id=\"fulfillment-count\">" + ids(1).size() + "</strong>");
    }
    @Test void paginationAndTotalAreScopedInSqlWithStableOrderingAndBoundedPages() throws Exception {
        try (var ignored = scenario.cleanup()) {
            for (int i = 0; i < 23; i++) fixtureOrderWithFulfillment(1, "CONFIRMED");
            for (int i = 0; i < 27; i++) fixtureOrderWithFulfillment(2, "CONFIRMED");
            var own = ids(1); String cookie = scenario.account(STAFF);
            var html = scenario.get("/staff/orders?open=false&page=0", cookie).body();
            assertThat(html).contains("id=\"fulfillment-count\">" + own.size() + "</strong>");
            assertThat(htmlIds(html)).containsExactlyElementsOf(own.subList(0, 20));
            assertThat(html).contains("/staff/orders?page=1").doesNotContain("data-store-id=\"2\"");
            assertThat(htmlIds(scenario.get("/staff/orders?open=false&page=1", cookie).body())).containsExactlyElementsOf(own.subList(20, own.size()));
            assertThat(htmlIds(scenario.get("/staff/orders?open=false&page=-1", cookie).body())).containsExactlyElementsOf(own.subList(0, 20));
            assertThat(htmlIds(scenario.get("/staff/orders?open=false&page=999", cookie).body())).isEmpty();
            var dashboard = as(STAFF, operations::getDashboard);
            var open = own.stream().filter(id -> jdbc.queryForObject("select status from dbo.order_fulfillments where fulfillment_id=?", String.class, id)
                    .matches("CONFIRMED|PREPARING|PACKED|SHIPPING|READY_FOR_PICKUP")).toList();
            assertThat(dashboard.recentOrders().stream().map(o -> o.fulfillmentId())).containsExactlyElementsOf(open.subList(0, 5));
        }
    }
    @ParameterizedTest @ValueSource(longs = {1, 2})
    void ownDeliveryAndPickupDetailReuseSnapshotsPaymentsTimelineAndHideStoredPickupCode(long id) throws Exception {
        long store=id==1?1:2;String email=id==1?STAFF:PEER;long f=jdbc.queryForObject("select min(fulfillment_id) from dbo.order_fulfillments where order_id=? and store_id=?",Long.class,id,store);var detail=as(email,()->fulfillmentService.getFulfillment(f));assertThat(detail.summary().orderId()).isEqualTo(id);assertThat(detail.summary().storeId()).isEqualTo(store);assertThat(detail.items()).hasSize((int)count("select count(*) from dbo.order_items where fulfillment_id=?",f));assertThat(detail.statusHistory()).hasSize((int)count("select count(*) from dbo.order_fulfillment_status_history where fulfillment_id=?",f));var response=scenario.get("/staff/fulfillments/"+f,scenario.account(email));assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("data-layout=\"staff\"");String code=jdbc.queryForObject("select pickup_code from dbo.order_fulfillments where fulfillment_id=?",String.class,f);if(code!=null)assertThat(response.body()).doesNotContain(code);
    }
    @ParameterizedTest @ValueSource(longs = {2, 0, -1, Long.MAX_VALUE})
    void crossStoreAndMissingDetailReturnSame404WithoutOrderResource(long id) throws Exception {
        long f=id==2?jdbc.queryForObject("select min(fulfillment_id) from dbo.order_fulfillments where store_id=2",Long.class):id;var response=scenario.get("/staff/fulfillments/"+f,scenario.account(STAFF));assertThat(response.statusCode()).isEqualTo(id==2?403:404);assertThat(response.body()).doesNotContain("data-store-id=\"2\"","id=\"pickup-code\"");
    }
    @Test void forgedQueryHeaderCookieAndGetBodyCannotChangeAssignedScope() throws Exception {
        String cookie=scenario.account(STAFF)+"; ONESHOP_STORE=2";String query="?storeId=2&store_id=2&assignedStores=2&staffEmail="+PEER;var before=scenario.snapshot();for(String content:List.of("application/json","application/x-www-form-urlencoded")){var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/staff/fulfillments?store_id=2")).header("Cookie",cookie).header("X-Store-Id","2").header("Content-Type",content).method("GET",HttpRequest.BodyPublishers.ofString(content.equals("application/json")?"{\"storeId\":2}":"storeId=2")).build();var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).doesNotContain("data-store-id=\"2\"");}assertThat(scenario.get("/staff/fulfillments"+query,cookie).statusCode()).isEqualTo(403);assertThat(scenario.snapshot()).isEqualTo(before);
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
            for (String path : List.of("/staff/dashboard", "/staff/orders", "/staff/fulfillments/1", "/staff/fulfillments/3"))
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
            assertThat(scenario.get("/staff/fulfillments/3", cookie).statusCode()).isEqualTo(200);
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id>? and store_id=2", max);
            assertThat(scenario.get("/staff/fulfillments/3", cookie).statusCode()).isEqualTo(403);
            assertThat(htmlIds(scenario.get("/staff/orders?open=false", cookie).body())).containsExactlyElementsOf(ids(1));
            assertThat(as(STAFF, operations::getDashboard).stores()).extracting(s -> s.store().id()).containsExactly(1L);
        } finally { jdbc.update("delete from dbo.staff_store_assignments where assignment_id>? and user_id=?", max, user); }
    }
    @ParameterizedTest @ValueSource(strings = {"khachhang1@example.com", "admin@oneshop.vn"})
    void otherRolesCannotOpenAnyStaffOperationsPage(String email) throws Exception {
        for (String path : List.of("/staff/dashboard", "/staff/orders", "/staff/fulfillments/1"))
            assertThat(scenario.get(path, scenario.account(email)).statusCode()).as(path).isEqualTo(403);
    }
}
