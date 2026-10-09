package com.oneshop;

import com.oneshop.dto.request.*;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.*;
import org.junit.jupiter.api.*;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/** Unconditional Phase 11 acceptance on real production HTTP/JWT/CSRF/JPA and seeded SQL Server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
class Phase11AdminDatabaseIntegrationTest {
    private static final String ADMIN = "admin@oneshop.vn", STAFF = "staff.thuduc@oneshop.vn";
    private static final String CUSTOMER = Phase9IntegrationScenario.CUSTOMER;
    private static final Map<String, String> IDS = Map.of(
            "users", "user_id", "stores", "store_id", "staff_store_assignments", "assignment_id",
            "reviews", "review_id", "products", "product_id", "store_products", "store_product_id");
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminUserService users;
    @Autowired AdminStaffAssignmentService assignments;
    @Autowired AdminOperationsService operations;
    @Autowired AdminReviewService reviews;
    @Autowired PasswordEncoder encoder;
    @LocalServerPort int port;
    private Phase9IntegrationScenario scenario;
    private SeedGuard guard;
    private Map<String, List<Map<String, Object>>> baseline;
    private Map<String, List<Map<String, Object>>> business;

    @BeforeEach void remember() {
        SecurityContextHolder.clearContext();
        scenario = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port);
        guard = new SeedGuard(jdbc); guard.remember();
        baseline = new HashMap<>();
        IDS.keySet().forEach(t -> baseline.put(t, rows(t)));
        business = scenario.snapshot();
    }
    @AfterEach void restore() {
        SecurityContextHolder.clearContext();
        guard.restore();
        // Tests never delete original rows. Remove only fixture IDs, then restore every original column/time.
        for (String table : List.of("staff_store_assignments", "reviews", "store_products", "products", "stores", "users")) {
            var remembered = baseline.get(table);
            String key = IDS.get(table);
            Set<Long> originalIds = new HashSet<>();
            remembered.forEach(r -> originalIds.add(((Number) r.get(key)).longValue()));
            for (long id : jdbc.queryForList("select " + key + " from dbo." + table, Long.class)) {
                if (!originalIds.contains(id)) jdbc.update("delete from dbo." + table + " where " + key + "=?", id);
            }
            for (var row : remembered) {
                var columns = row.keySet().stream().filter(k -> !k.equals(key)).toList();
                List<Object> args = new ArrayList<>();
                columns.forEach(k -> args.add(row.get(k))); args.add(row.get(key));
                jdbc.update("update dbo." + table + " set " + String.join(",",
                        columns.stream().map(k -> k + "=?").toList()) + " where " + key + "=?", args.toArray());
            }
        }
        IDS.keySet().forEach(t -> assertThat(rows(t).equals(baseline.get(t))).as("exact fixture cleanup: " + t).isTrue());
        assertThat(scenario.snapshot().equals(business)).as("exact order/stock/cart cleanup").isTrue();
    }
    private List<Map<String, Object>> rows(String table) { return jdbc.queryForList("select * from dbo." + table + " order by 1"); }
    private long scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private long user(String email) { return scalar("select user_id from dbo.users where email=?", email); }
    private long stock(long store) { return scalar("select min(store_product_id) from dbo.store_products where store_id=?", store); }
    private long assignment() { return scalar("select assignment_id from dbo.staff_store_assignments where user_id=? and store_id=1", user(STAFF)); }
    private String admin() throws Exception { return scenario.account(ADMIN); }
    private String page(String path) throws Exception {
        var response = scenario.get(path, admin());
        assertThat(response.statusCode()).as(path).isEqualTo(200); return response.body();
    }
    private HttpResponse<String> post(String path, String body) throws Exception { return scenario.post(admin(), path, body); }
    private <T> T asAdmin(Supplier<T> task) { return as(ADMIN, "ADMIN", task); }
    private <T> T as(String email, String role, Supplier<T> task) {
        var prior = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        try { return task.get(); } finally { SecurityContextHolder.setContext(prior); }
    }
    private static List<Long> ids(String html, String attribute) {
        var matcher = Pattern.compile(attribute + "=\"(\\d+)\"").matcher(html); List<Long> result = new ArrayList<>();
        while (matcher.find()) result.add(Long.parseLong(matcher.group(1))); return result;
    }
    private List<Long> orderIds(Long store) {
        return store == null
                ? jdbc.queryForList("select order_id from dbo.orders order by created_at desc,order_id desc", Long.class)
                : jdbc.queryForList("select order_id from dbo.orders where store_id=? order by created_at desc,order_id desc", Long.class, store);
    }
    private String storeForm(long id, String status) {
        var s = jdbc.queryForMap("select * from dbo.stores where store_id=?", id);
        return "code=" + Phase9IntegrationScenario.enc((String) s.get("code")) + "&name=" + Phase9IntegrationScenario.enc((String) s.get("name"))
                + "&address=" + Phase9IntegrationScenario.enc((String) s.get("address"))
                + "&provinceCity=" + Phase9IntegrationScenario.enc((String) s.get("province_city"))
                + "&area=" + Phase9IntegrationScenario.enc((String) s.get("area"))
                + "&phone=0912345678&openingHours=08%3A00-21%3A00&deliveryEnabled=true&pickupEnabled=true&status=" + status;
    }
    private String userForm(long id, String role, String status) {
        var u = jdbc.queryForMap("select email,full_name from dbo.users where user_id=?", id);
        return "email=" + Phase9IntegrationScenario.enc((String) u.get("email"))
                + "&fullName=" + Phase9IntegrationScenario.enc((String) u.get("full_name"))
                + "&phone=0912345678&role=" + role + "&status=" + status;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/stores", "/admin/categories", "/admin/brands", "/admin/products",
            "/admin/store-products", "/admin/staff-assignments", "/admin/inventory", "/admin/orders", "/admin/users", "/admin/reviews"})
    void customerAndStaffCannotReadAnyAdminPage(String path) throws Exception {
        for (String email : List.of(CUSTOMER, STAFF)) assertThat(scenario.get(path, scenario.account(email)).statusCode()).isEqualTo(403);
        assertThat(scenario.get(path, "").statusCode()).isEqualTo(302);
    }
    @Test void customerAndStaffCannotWriteAdminAndNoBusinessRowsChange() throws Exception {
        var before = scenario.snapshot();
        var scope = scenario.scope();
        long review = scalar("select min(review_id) from dbo.reviews");
        for (String email : List.of(CUSTOMER, STAFF)) {
            String cookie = scenario.account(email);
            for (String path : List.of("/admin/users/" + user(STAFF), "/admin/staff-assignments",
                    "/admin/staff-assignments/" + assignment() + "/status", "/admin/reviews/" + review + "/status",
                    "/admin/stores/1", "/admin/store-products/" + stock(1))) {
                assertThat(scenario.post(cookie, path, "status=INACTIVE").statusCode()).as(path).isEqualTo(403);
            }
        }
        assertThat(scenario.snapshot().equals(before)).isTrue();
        assertThat(scenario.scope().equals(scope)).isTrue();
    }
    @Test void newAdminServicesEnforceRoleEvenWithoutHttp() {
        for (String role : List.of("CUSTOMER", "STAFF")) {
            assertThatThrownBy(() -> as(CUSTOMER, role, () -> operations.getOrders(null, 0))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> as(CUSTOMER, role, () -> users.getUsers(null, 0))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> as(CUSTOMER, role, () -> assignments.assign(new StaffAssignmentRequest(user(STAFF), 2L, ActiveStatus.ACTIVE)))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> as(CUSTOMER, role, () -> { reviews.setStatus(1L, VisibilityStatus.HIDDEN); return null; })).isInstanceOf(AccessDeniedException.class);
        }
    }
    @Test void adminGetsEveryPageAndFormsUseAdminLayoutCsrf() throws Exception {
        for (String path : List.of("/admin", "/admin/stores", "/admin/categories", "/admin/brands", "/admin/products",
                "/admin/store-products", "/admin/inventory", "/admin/orders", "/admin/users", "/admin/users/new",
                "/admin/users/" + user(STAFF) + "/edit", "/admin/staff-assignments", "/admin/reviews")) {
            assertThat(page(path)).contains("data-layout=\"admin\"").doesNotContain("Sắp có");
        }
        for (String path : List.of("/admin/users/new", "/admin/staff-assignments", "/admin/reviews")) {
            assertThat(page(path)).contains("name=\"_csrf\"");
        }
    }
    @Test void writesRequireCsrfForCookieAndInvalidRequestsDoNotMutate() throws Exception {
        var before = scenario.scope();
        for (String path : List.of("/admin/users", "/admin/users/" + user(STAFF), "/admin/staff-assignments",
                "/admin/staff-assignments/" + assignment() + "/status", "/admin/reviews/1/status")) {
            assertThat(scenario.raw(path, "status=INACTIVE", "Cookie", admin()).statusCode()).isEqualTo(403);
        }
        assertThat(scenario.scope().equals(before)).isTrue();
    }
    @Test void reuseStoreCrudCreatesAndEditsFinderMetadata() throws Exception {
        String code = "P11-" + UUID.randomUUID().toString().substring(0, 12);
        assertThat(post("/admin/stores", "code=" + code + "&name=Phase11+Store&address=11+Street&provinceCity=HCM&area=ThuDuc"
                + "&phone=0912345678&openingHours=08%3A00-21%3A00&deliveryEnabled=true&pickupEnabled=true&status=ACTIVE").statusCode()).isEqualTo(302);
        long id = scalar("select store_id from dbo.stores where code=?", code);
        assertThat(post("/admin/stores/" + id, storeForm(id, "INACTIVE")).statusCode()).isEqualTo(302);
        var s = jdbc.queryForMap("select * from dbo.stores where store_id=?", id);
        assertThat(s.get("status")).isEqualTo("INACTIVE"); assertThat(s.get("opening_hours")).isEqualTo("08:00-21:00");
        assertThat(page("/admin/stores")).contains("Phase11 Store");
    }
    @Test void assignmentRevocationAndReactivationAffectTheSameOldJwtImmediately() throws Exception {
        String staff = scenario.account(STAFF); long id = assignment();
        var assignedAt = jdbc.queryForObject("select assigned_at from dbo.staff_store_assignments where assignment_id=?", Object.class, id);
        assertThat(scenario.get("/staff/orders", staff).statusCode()).isEqualTo(200);
        assertThat(post("/admin/staff-assignments/" + id + "/status", "status=INACTIVE").statusCode()).isEqualTo(302);
        assertThat(scenario.get("/staff/orders", staff).statusCode()).isEqualTo(403);
        assertThat(post("/admin/staff-assignments", "userId=" + user(STAFF) + "&storeId=1&status=ACTIVE").statusCode()).isEqualTo(302);
        assertThat(scenario.get("/staff/orders", staff).statusCode()).isEqualTo(200);
        assertThat(assignment()).isEqualTo(id);
        assertThat(jdbc.queryForObject("select assigned_at from dbo.staff_store_assignments where assignment_id=?", Object.class, id)).isEqualTo(assignedAt);
        assertThat(scalar("select count(*) from dbo.staff_store_assignments where user_id=? and store_id=1", user(STAFF))).isEqualTo(1);
    }
    @Test void multipleAssignmentsAndStoreChangeDoNotTrustBrowserSelector() throws Exception {
        String staff = scenario.account(STAFF); long id = assignment();
        assertThat(post("/admin/staff-assignments", "userId=" + user(STAFF) + "&storeId=2&status=ACTIVE").statusCode()).isEqualTo(302);
        var html = scenario.get("/staff/stock?storeId=5", staff + "; ONESHOP_STORE=5").body();
        assertThat(ids(html, "data-store-id")).contains(1L, 2L).doesNotContain(5L);
        assertThat(post("/admin/staff-assignments/" + id + "/status", "status=INACTIVE").statusCode()).isEqualTo(302);
        html = scenario.get("/staff/stock", staff).body();
        assertThat(ids(html, "data-store-id")).contains(2L).doesNotContain(1L);
    }
    @Test void assignmentsRejectCustomerAdminInactiveStaffAndInactiveStore() throws Exception {
        long staff = user(STAFF);
        for (String body : List.of("userId=" + user(CUSTOMER) + "&storeId=2&status=ACTIVE",
                "userId=" + user(ADMIN) + "&storeId=2&status=ACTIVE", "userId=" + staff + "&storeId=5&status=ACTIVE")) {
            var before = rows("staff_store_assignments");
            assertThat(post("/admin/staff-assignments", body).statusCode()).isEqualTo(200);
            assertThat(rows("staff_store_assignments").equals(before)).isTrue();
        }
        assertThat(post("/admin/users/" + staff, userForm(staff, "STAFF", "INACTIVE")).statusCode()).isEqualTo(302);
        assertThat(post("/admin/staff-assignments", "userId=" + staff + "&storeId=2&status=ACTIVE").statusCode()).isEqualTo(200);
    }
    @Test void userCreateUsesBcryptAndNeverRendersPasswordOrHash() throws Exception {
        String email = "phase11." + UUID.randomUUID() + "@example.com";
        String password = "Phase11@123";
        assertThat(post("/admin/users", "email=" + email + "&fullName=Phase11+Staff&phone=0912345678&role=STAFF&status=ACTIVE&password=" + password).statusCode()).isEqualTo(302);
        long id = user(email);
        String hash = jdbc.queryForObject("select password_hash from dbo.users where user_id=?", String.class, id);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(page("/admin/users/" + id + "/edit")).contains(email).doesNotContain(password, hash, "name=\"password\"");
        assertThat(page("/admin/users?role=STAFF")).doesNotContain(hash, password);
        assertThat(post("/admin/staff-assignments", "userId=" + id + "&storeId=2&status=ACTIVE").statusCode()).isEqualTo(302);
    }
    @Test void invalidUserFormsDuplicateEmailAndInvalidEnumDoNotWriteOrReflectPassword() throws Exception {
        var before = rows("users");
        assertThat(post("/admin/users", "email=bad&fullName=&role=STAFF&status=ACTIVE&password=secret").statusCode()).isEqualTo(200);
        var duplicate = post("/admin/users", "email=" + STAFF + "&fullName=Duplicate&role=STAFF&status=ACTIVE&password=Phase11@123");
        assertThat(duplicate.statusCode()).isEqualTo(200); assertThat(duplicate.body()).doesNotContain("Phase11@123");
        assertThat(post("/admin/users", "email=new@example.com&fullName=New&role=ROOT&status=ACTIVE&password=Phase11@123").statusCode()).isEqualTo(200);
        assertThat(post("/admin/users", "email=new@example.com&fullName=New&role=STAFF&status=ACTIVE&password=x").statusCode()).isEqualTo(200);
        assertThat(rows("users").equals(before)).isTrue();
    }
    @Test void userRoleStatusChangesRevokeOldJwtAndDoNotRestoreOldAssignments() throws Exception {
        String staffCookie = scenario.account(STAFF); long staff = user(STAFF);
        assertThat(post("/admin/users/" + staff, userForm(staff, "CUSTOMER", "ACTIVE")).statusCode()).isEqualTo(302);
        assertThat(scenario.get("/staff/orders", staffCookie).statusCode()).isEqualTo(403);
        assertThat(scalar("select count(*) from dbo.staff_store_assignments where user_id=? and status='ACTIVE'", staff)).isZero();
        assertThat(post("/admin/users/" + staff, userForm(staff, "STAFF", "ACTIVE")).statusCode()).isEqualTo(302);
        assertThat(scenario.get("/staff/orders", staffCookie).statusCode()).isEqualTo(403);
        assertThat(post("/admin/staff-assignments/" + assignment() + "/status", "status=ACTIVE").statusCode()).isEqualTo(302);
        assertThat(scenario.get("/staff/orders", staffCookie).statusCode()).isEqualTo(200);
        assertThat(post("/admin/users/" + staff, userForm(staff, "STAFF", "INACTIVE")).statusCode()).isEqualTo(302);
        assertThat(scenario.get("/staff/orders", staffCookie).statusCode()).isEqualTo(302);
    }
    @Test void userCannotChangeEmailPasswordOrDisableOwnAdmin() throws Exception {
        long id = user(ADMIN);
        var before = rows("users");
        assertThat(post("/admin/users/" + id, userForm(id, "CUSTOMER", "ACTIVE")).statusCode()).isEqualTo(200);
        assertThat(post("/admin/users/" + id, userForm(id, "ADMIN", "INACTIVE")).statusCode()).isEqualTo(200);
        assertThat(post("/admin/users/" + id, userForm(id, "ADMIN", "ACTIVE").replace("email=admin%40oneshop.vn", "email=other%40example.com")).statusCode()).isEqualTo(200);
        assertThat(post("/admin/users/" + id, userForm(id, "ADMIN", "ACTIVE") + "&password=Phase11@123").statusCode()).isEqualTo(200);
        assertThat(rows("users").equals(before)).isTrue();
    }
    @Test void inventoryUsesExistingExactStockQueryAndFiltersEveryStoreIncludingInactive() throws Exception {
        for (long store : List.of(1L, 2L, 4L, 5L)) {
            var expected = jdbc.queryForList("select sp.store_product_id from dbo.store_products sp join dbo.stores s on s.store_id=sp.store_id"
                    + " join dbo.products p on p.product_id=sp.product_id where sp.store_id=? order by s.name,p.name,sp.store_product_id", Long.class, store);
            String html = page("/admin/inventory?storeId=" + store);
            assertThat(ids(html, "data-store-product-id")).containsExactlyElementsOf(expected.stream().limit(20).toList());
            assertThat(html).contains("id=\"stock-count\">" + expected.size() + "</strong>");
            for (long id : expected.stream().limit(20).toList()) {
                String row = html.substring(html.indexOf("data-store-product-id=\"" + id + "\"")); row = row.substring(0, row.indexOf("</tr>"));
                int quantity = jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?", Integer.class, id);
                assertThat(row).contains("data-field=\"exact-quantity\">" + quantity + "</td>");
            }
        }
        assertThat(ids(page("/admin/inventory"), "data-store-id")).contains(1L, 2L);
        assertThat(page("/admin/inventory?storeId=999999")).contains("id=\"stock-count\">0</strong>");
    }
    @Test void adminOrderFilterMatchesSqlAcrossAllStoresAndDetailReusesSnapshotPaymentHistory() throws Exception {
        var before = scenario.snapshot();
        assertThat(ids(page("/admin/orders"), "data-order-id")).containsExactlyElementsOf(orderIds(null).stream().limit(20).toList());
        for (long store : List.of(1L, 2L, 3L, 4L, 5L)) {
            String html = page("/admin/orders?storeId=" + store + "&staffEmail=" + STAFF);
            var expected = orderIds(store);
            assertThat(ids(html, "data-order-id")).containsExactlyElementsOf(expected.stream().limit(20).toList());
            assertThat(html).contains("id=\"order-count\">" + expected.size() + "</strong>");
        }
        for (long id : orderIds(null)) {
            String html = page("/admin/orders/" + id);
            assertThat(html).contains("Lịch sử trạng thái", "Sản phẩm đã đặt", "Checkout #").doesNotContain("id=\"pickup-code\"");
            for (String name : jdbc.queryForList("select product_name from dbo.order_items where order_id=?", String.class, id)) assertThat(html).contains(name);
        }
        assertThat(scenario.get("/admin/orders/99999999", admin()).statusCode()).isEqualTo(404);
        assertThat(page("/admin/orders?storeId=99999999")).contains("id=\"order-count\">0</strong>");
        assertThat(scenario.snapshot().equals(before)).isTrue();
    }
    @Test void orderStockUserAssignmentReviewPaginationKeepsFiltersAndStableTotals() throws Exception {
        for (int i = 0; i < 23; i++) {
            jdbc.update("insert into dbo.orders(checkout_id,user_id,store_id,fulfillment_type,payment_method,payment_status,order_status,"
                    + "receiver_name,receiver_phone,shipping_address,total_amount)"
                    + " select top 1 checkout_id,user_id,1,'DELIVERY','COD','UNPAID','CONFIRMED',receiver_name,receiver_phone,'11 Pagination Street',total_amount from dbo.orders order by order_id");
            long p = jdbc.queryForObject("insert into dbo.products(sku,name,category_id,brand_id,status) output inserted.product_id"
                    + " select top 1 ?,?,category_id,brand_id,'ACTIVE' from dbo.products order by product_id", Long.class, "P11-" + UUID.randomUUID(), "Phase11 pagination " + i);
            jdbc.update("insert into dbo.store_products(store_id,product_id,price,quantity,status) values(1,?,1000,7,'ACTIVE')", p);
            jdbc.update("insert into dbo.reviews(user_id,product_id,rating,comment,status) values(?,?,5,'Phase11 pagination','ACTIVE')", user(CUSTOMER), p);
            var u = asAdmin(() -> users.create(new AdminUserRequest("p11." + UUID.randomUUID() + "@example.com", "Phase11 Staff", "", RoleName.STAFF, ActiveStatus.ACTIVE, "Phase11@123")));
            asAdmin(() -> assignments.assign(new StaffAssignmentRequest(u.id(), 1L, ActiveStatus.ACTIVE)));
        }
        var ids = orderIds(1L);
        assertThat(ids(page("/admin/orders?storeId=1"), "data-order-id")).containsExactlyElementsOf(ids.stream().limit(20).toList());
        assertThat(ids(page("/admin/orders?storeId=1&page=1"), "data-order-id")).containsExactlyElementsOf(ids.stream().skip(20).limit(20).toList());
        assertThat(page("/admin/orders?storeId=1")).contains("storeId=1", "page=1", "id=\"order-count\">" + ids.size() + "</strong>");
        for (String path : List.of("/admin/inventory?storeId=1", "/admin/users?role=STAFF", "/admin/staff-assignments?storeId=1", "/admin/reviews")) {
            assertThat(page(path)).contains("page=1");
            assertThat(page(path + (path.contains("?") ? "&" : "?") + "page=1")).contains("page=0");
        }
        assertThat(asAdmin(() -> users.getUsers(RoleName.STAFF, 0)).getTotalElements()).isGreaterThan(23);
    }
    @Test void reviewManagementOnlyChangesVisibilityAndEscapesContent() throws Exception {
        long id = scalar("select min(review_id) from dbo.reviews");
        var before = jdbc.queryForMap("select * from dbo.reviews where review_id=?", id);
        jdbc.update("update dbo.reviews set comment=? where review_id=?", "<script>alert('review')</script>", id);
        assertThat(page("/admin/reviews")).contains("&lt;script&gt;").doesNotContain("<script>alert");
        assertThat(post("/admin/reviews/" + id + "/status", "status=HIDDEN").statusCode()).isEqualTo(302);
        var after = jdbc.queryForMap("select * from dbo.reviews where review_id=?", id);
        assertThat(after.get("status")).isEqualTo("HIDDEN");
        for (String key : List.of("user_id", "product_id", "rating", "created_at")) assertThat(after.get(key)).isEqualTo(before.get(key));
        assertThat(post("/admin/reviews/" + id + "/status", "status=ACTIVE").statusCode()).isEqualTo(302);
        assertThat(post("/admin/reviews/" + id + "/status", "status=INACTIVE").statusCode()).isEqualTo(400);
        assertThat(post("/admin/reviews/99999999/status", "status=HIDDEN").statusCode()).isEqualTo(404);
    }
    @Test void overviewMatchesIndependentSqlForEveryStoreIncludingInactive() throws Exception {
        var result = asAdmin(() -> operations.getOverview(null));
        assertThat(result).hasSize((int) scalar("select count(*) from dbo.stores"));
        for (var row : result) {
            long id = row.store().id();
            assertThat(row.totalOrders()).isEqualTo(scalar("select count(*) from dbo.orders where store_id=?", id));
            assertThat(row.processingOrders()).isEqualTo(scalar("select count(*) from dbo.orders where store_id=? and order_status in ('CONFIRMED','PREPARING','PACKED','SHIPPING','READY_FOR_PICKUP')", id));
            assertThat(row.completedOrders()).isEqualTo(scalar("select count(*) from dbo.orders where store_id=? and order_status='COMPLETED'", id));
            assertThat(row.cancelledOrders()).isEqualTo(scalar("select count(*) from dbo.orders where store_id=? and order_status='CANCELLED'", id));
            assertThat(row.stockRows()).isEqualTo(scalar("select count(*) from dbo.store_products where store_id=?", id));
            assertThat(row.lowStockRows()).isEqualTo(scalar("select count(*) from dbo.store_products where store_id=? and status='ACTIVE' and quantity between 1 and 5", id));
            assertThat(row.outOfStockRows()).isEqualTo(scalar("select count(*) from dbo.store_products where store_id=? and status='ACTIVE' and quantity=0", id));
            assertThat(row.assignedStaff()).isEqualTo(scalar("select count(*) from dbo.staff_store_assignments a join dbo.users u on u.user_id=a.user_id join dbo.roles r on r.role_id=u.role_id"
                    + " join dbo.stores s on s.store_id=a.store_id where a.store_id=? and a.status='ACTIVE' and u.status='ACTIVE' and r.name='STAFF' and s.status='ACTIVE'", id));
        }
        assertThat(ids(page("/admin"), "data-overview-store-id")).hasSize(result.size());
        assertThat(ids(page("/admin?storeId=2"), "data-overview-store-id")).containsExactly(2L);
    }
    @Test void tc18InactiveStoreRejectsNewCheckoutButAdminAndCustomerKeepHistoricalOrders() throws Exception {
        var mixed = scenario.mixed(); // Production checkout creates Orders for Stores 1, 2 and 4.
        String customer = scenario.account(CUSTOMER);
        long sp = scalar("select store_product_id from dbo.order_items where order_id=?", mixed.a());
        assertThat(scenario.post(customer, "/cart/items", "storeProductId=" + sp + "&quantity=1").statusCode()).isEqualTo(302);
        long cartLine = scalar("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id join dbo.users u on u.user_id=c.user_id"
                + " where u.email=? and ci.store_product_id=?", CUSTOMER, sp);
        assertThat(post("/admin/stores/1", storeForm(1, "INACTIVE")).statusCode()).isEqualTo(302);
        var before = scenario.snapshot();
        var response = scenario.post(customer, "/checkout", "cartItemIds=" + cartLine + "&groups[0].storeId=1&groups[0].fulfillmentType=DELIVERY"
                + "&groups[0].paymentMethod=COD&groups[0].receiverName=Customer&groups[0].receiverPhone=0912345678&groups[0].shippingAddress=11+Street");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot().equals(before)).isTrue();
        assertThat(scenario.post(customer, "/cart/items", "storeProductId=" + sp + "&quantity=1").statusCode()).isEqualTo(400);
        assertThat(page("/admin/orders?storeId=1")).contains("data-order-id=\"" + mixed.a() + "\"");
        assertThat(page("/admin/orders/" + mixed.a())).contains("Sản phẩm đã đặt");
        assertThat(scenario.get("/orders/" + mixed.a(), customer).statusCode()).isEqualTo(200);
        assertThat(scenario.get("/staff/orders", scenario.account(STAFF)).statusCode()).isEqualTo(403);
        assertThat(ids(page("/admin/inventory?storeId=1"), "data-store-product-id")).contains(sp);
    }
    @Test void productStoreAndStoreProductWithHistoryAreSoftDisabledAndNotHardDeleted() throws Exception {
        var mixed = scenario.mixed(); long order = mixed.a();
        long sp = scalar("select store_product_id from dbo.order_items where order_id=?", order);
        long p = scalar("select product_id from dbo.store_products where store_product_id=?", sp);
        var aggregate = scenario.aggregate(order);
        var product = jdbc.queryForMap("select * from dbo.products where product_id=?", p);
        assertThat(post("/admin/products/" + p, "sku=" + product.get("sku") + "&name=Phase11+Hidden+Product&categoryId=" + product.get("category_id")
                + "&brandId=" + product.get("brand_id") + "&description=Phase11&status=HIDDEN").statusCode()).isEqualTo(302);
        var stock = jdbc.queryForMap("select * from dbo.store_products where store_product_id=?", sp);
        assertThat(post("/admin/store-products/" + sp, "storeId=2&productId=999&price=" + stock.get("price") + "&quantity=" + stock.get("quantity")
                + "&status=INACTIVE&note=Phase11").statusCode()).isEqualTo(302);
        assertThat(post("/admin/stores/1", storeForm(1, "INACTIVE")).statusCode()).isEqualTo(302);
        assertThat(scalar("select count(*) from dbo.products where product_id=? and status='HIDDEN'", p)).isEqualTo(1);
        assertThat(scalar("select count(*) from dbo.store_products where store_product_id=? and store_id=1 and product_id=? and status='INACTIVE'", sp, p)).isEqualTo(1);
        assertThat(scalar("select count(*) from dbo.stores where store_id=1 and status='INACTIVE'")).isEqualTo(1);
        assertThat(scenario.aggregate(order).equals(aggregate)).isTrue();
        assertThat(page("/admin/orders/" + order)).contains("Sản phẩm đã đặt");
        for (String path : List.of("/admin/products/" + p + "/delete", "/admin/stores/1/delete", "/admin/store-products/" + sp + "/delete")) {
            assertThat(post(path, "").statusCode()).isEqualTo(404);
        }
    }
}
