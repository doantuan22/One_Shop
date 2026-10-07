package com.oneshop;

import com.oneshop.dto.request.StockAdjustRequest;
import com.oneshop.entity.InventoryMovement;
import com.oneshop.entity.InventoryMovementType;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.service.StaffInventoryService;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real Tomcat/JWT/CSRF, services, transactions and SQL Server; repository spy only injects write failures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class StaffInventoryDatabaseIntegrationTest {
    private static final String STAFF = "staff.thuduc@oneshop.vn", PEER = "staff.govap@oneshop.vn";
    @Autowired StaffInventoryService inventory;
    @Autowired StoreProductRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @PersistenceContext EntityManager entityManager;
    @MockitoSpyBean InventoryMovementRepository movements;
    @LocalServerPort int port;
    private Phase9IntegrationScenario scenario;
    private Phase9IntegrationScenario.Cleanup cleanup;
    private final List<Long> fixtureStocks = new ArrayList<>(), fixtureProducts = new ArrayList<>();

    @BeforeEach void remember() {
        SecurityContextHolder.clearContext(); fixtureStocks.clear(); fixtureProducts.clear();
        scenario = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port); cleanup = scenario.cleanup();
    }
    @AfterEach void restore() {
        reset(movements); SecurityContextHolder.clearContext();
        for (long id : fixtureStocks) jdbc.update("delete from dbo.store_products where store_product_id=?", id);
        for (long id : fixtureProducts) jdbc.update("delete from dbo.products where product_id=?", id);
        cleanup.close();
    }
    private <T> T as(String email, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")))); SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private long scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private long stock(long store) { return scalar("select min(store_product_id) from dbo.store_products where store_id=?", store); }
    private int quantity(long id) { return jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?", Integer.class, id); }
    private String adjustPath(long id) { return "/staff/stock/" + id + "/adjust"; }
    private String historyPath(long id) { return "/staff/inventory-history/" + id; }
    private boolean adjust(long id, int qty, String note) { return as(STAFF, () -> inventory.adjust(id, new StockAdjustRequest(qty, note))); }
    private List<Map<String, Object>> history(long id) {
        return jdbc.queryForList("select * from dbo.inventory_movements where store_product_id=? order by created_at desc,movement_id desc", id);
    }
    private static List<Long> htmlIds(String html, String attribute) {
        var matcher = Pattern.compile(attribute + "=\"(\\d+)\"").matcher(html); List<Long> result = new ArrayList<>();
        while (matcher.find()) result.add(Long.parseLong(matcher.group(1))); return result;
    }
    private List<Long> stockIds(long store) {
        return jdbc.queryForList("select sp.store_product_id from dbo.store_products sp join dbo.stores s on s.store_id=sp.store_id join dbo.products p on p.product_id=sp.product_id where sp.store_id=? order by s.name,p.name,sp.store_product_id", Long.class, store);
    }

    @Test void stockAndHistorySelectionShowOnlyOwnSkusAllStatusesAndExactQuantity() throws Exception {
        var page = as(STAFF, () -> inventory.getStock(0));
        assertThat(page.getTotalElements()).isEqualTo(stockIds(1).size()); assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getContent()).allSatisfy(s -> { assertThat(s.storeId()).isEqualTo(1L); assertThat(s.quantity()).isEqualTo(quantity(s.storeProductId())); });
        for (String path : List.of("/staff/stock", "/staff/inventory-history")) {
            var response = scenario.get(path + "?storeId=2&store_id=2&staffEmail=" + PEER, scenario.account(STAFF) + "; ONESHOP_STORE=2");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(htmlIds(response.body(), "data-store-product-id")).containsExactlyElementsOf(stockIds(1));
            assertThat(response.body()).contains("data-layout=\"staff\"", "SKU / Sản phẩm", "Tồn thực tế").doesNotContain("data-store-id=\"2\"");
            for (var item : page.getContent()) {
                String row = response.body().substring(response.body().indexOf("data-store-product-id=\"" + item.storeProductId() + "\""));
                row = row.substring(0, row.indexOf("</tr>"));
                assertThat(row).contains(item.sku(), item.productName(), "data-field=\"exact-quantity\">" + item.quantity() + "</td>");
            }
        }
        assertThat(as(STAFF, () -> inventory.getStock(-1)).getContent()).isEqualTo(page.getContent());
        assertThat(as(STAFF, () -> inventory.getStock(999)).getContent()).isEmpty();
    }
    @Test void stockPaginationAndTotalStayScopedWhenMoreThanTwentyProductsExist() throws Exception {
        for (int i = 0; i < 25; i++) {
            long product = jdbc.queryForObject("insert into dbo.products(sku,name,category_id,brand_id,status) output inserted.product_id select ?,?,category_id,brand_id,'ACTIVE' from dbo.products where product_id=1",
                    Long.class, "P104-" + UUID.randomUUID(), "Phase104 stock fixture " + i);
            fixtureProducts.add(product);
            long own = jdbc.queryForObject("insert into dbo.store_products(store_id,product_id,price,quantity,status) output inserted.store_product_id values(1,?,1000,?,'INACTIVE')", Long.class, product, i);
            fixtureStocks.add(own);
            long peer = jdbc.queryForObject("insert into dbo.store_products(store_id,product_id,price,quantity,status) output inserted.store_product_id values(2,?,2000,99,'ACTIVE')", Long.class, product);
            fixtureStocks.add(peer);
        }
        var ids = stockIds(1); var first = as(STAFF, () -> inventory.getStock(0));
        assertThat(first.getTotalElements()).isEqualTo(ids.size()); assertThat(first.getContent()).hasSize(20);
        assertThat(first.getContent().stream().map(s -> s.storeProductId())).containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(as(STAFF, () -> inventory.getStock(1)).getContent().stream().map(s -> s.storeProductId())).containsExactlyElementsOf(ids.subList(20, ids.size()));
        String cookie = scenario.account(STAFF);
        var html = scenario.get("/staff/stock?store_id=2", cookie).body();
        assertThat(htmlIds(html, "data-store-product-id")).containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(html).contains("/staff/stock?page=1").doesNotContain("data-store-id=\"2\"");
        assertThat(htmlIds(scenario.get("/staff/stock?page=1", cookie).body(), "data-store-product-id")).containsExactlyElementsOf(ids.subList(20, ids.size()));
        assertThat(scenario.get("/staff/inventory-history", cookie).body()).contains("/staff/inventory-history?page=1");
    }
    @ParameterizedTest @ValueSource(ints = {0, 3, Integer.MAX_VALUE})
    void validHttpAdjustmentWritesExactlyOneAccurateMovementAndKeepsOtherResourcesUnchanged(int qty) throws Exception {
        long id = stock(1); int previous = quantity(id); long count = scalar("select count(*) from dbo.inventory_movements");
        var before = scenario.snapshot(); String note = "  Kiểm kê <b>count</b>  ";
        var response = scenario.post(scenario.account(STAFF), adjustPath(id), "newQuantity=" + qty + "&note=" + Phase9IntegrationScenario.enc(note)
                + "&store_id=2&storeId=2&staffEmail=" + PEER + "&staff_id=6&quantityBefore=999&quantityChange=999&price=1&status=INACTIVE");
        assertThat(response.statusCode()).isEqualTo(302); assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith(historyPath(id) + "?success=adjusted");
        assertThat(quantity(id)).isEqualTo(qty); assertThat(scalar("select count(*) from dbo.inventory_movements")).isEqualTo(count + 1);
        var movement = history(id).get(0);
        assertThat(movement).containsEntry("type", "STOCK_ADJUST").containsEntry("quantity_before", previous)
                .containsEntry("quantity_after", qty).containsEntry("quantity_change", qty - previous)
                .containsEntry("staff_id", scalar("select user_id from dbo.users where email=?", STAFF)).containsEntry("note", note.trim())
                .containsEntry("reference_order_id", null); assertThat(movement.get("created_at")).isNotNull();
        var after = scenario.snapshot();
        for (String table : Phase9IntegrationScenario.TABLES) if (!Set.of("store_products", "inventory_movements").contains(table)) assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        assertThat(after.get("store_products").stream().filter(r -> !r.get("store_product_id").equals(id)).toList())
                .isEqualTo(before.get("store_products").stream().filter(r -> !r.get("store_product_id").equals(id)).toList());
        var stockBefore = new HashMap<>(before.get("store_products").stream().filter(r -> r.get("store_product_id").equals(id)).findFirst().orElseThrow());
        var stockAfter = new HashMap<>(after.get("store_products").stream().filter(r -> r.get("store_product_id").equals(id)).findFirst().orElseThrow());
        for (String field : List.of("quantity", "updated_at")) { stockBefore.remove(field); stockAfter.remove(field); }
        assertThat(stockAfter).isEqualTo(stockBefore);
        var html = scenario.get(historyPath(id), scenario.account(STAFF)).body();
        assertThat(html).contains("STOCK_ADJUST", "Kiểm kê &lt;b&gt;count&lt;/b&gt;").doesNotContain("<b>count</b>");
        assertThat(as(STAFF, () -> inventory.getHistory(id, 0)).movements().getContent().get(0).quantityAfter()).isEqualTo(qty);
    }
    @Test void postingTheCurrentQuantityIsAnExplicitNoChangeWithNoZeroMovement() throws Exception {
        long id = stock(1); var before = scenario.snapshot();
        assertThat(adjust(id, quantity(id), "Same count")).isFalse();
        var response = scenario.post(scenario.account(STAFF), adjustPath(id), "newQuantity=" + quantity(id) + "&note=Same+count");
        assertThat(response.statusCode()).isEqualTo(302); assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("?success=unchanged");
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "", "abc", "2147483648", "1.5"})
    void invalidOrNegativeQuantityIsRejectedOnBackendWithoutAnyWrite(String qty) throws Exception {
        long id = stock(1); var before = scenario.snapshot();
        var response = scenario.post(scenario.account(STAFF), adjustPath(id), "newQuantity=" + qty + "&note=Invalid+count");
        assertThat(response.statusCode()).isEqualTo(400); assertThat(response.body()).contains("id=\"newQuantity\"", "text-danger");
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"", "   ", "LONG"})
    void staffAdjustmentRequiresAnAuditNoteWithinTheExistingColumnLimit(String note) throws Exception {
        var before = scenario.snapshot(); String input = note.equals("LONG") ? "x".repeat(501) : note;
        assertThat(scenario.post(scenario.account(STAFF), adjustPath(stock(1)), "newQuantity=1&note=" + Phase9IntegrationScenario.enc(input)).statusCode()).isEqualTo(400);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @Test void serviceValidationAlsoRejectsNegativeMissingQuantityAndMissingNote() {
        var before = scenario.snapshot();
        for (var request : List.of(new StockAdjustRequest(-1, "Reason"), new StockAdjustRequest(null, "Reason"), new StockAdjustRequest(1, "")))
            assertThatThrownBy(() -> as(STAFF, () -> inventory.adjust(stock(1), request))).isInstanceOf(ConstraintViolationException.class);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(longs = {2, Long.MAX_VALUE})
    void crossStoreAndMissingStockFormsOrHistoryReturnNoResource(long resource) throws Exception {
        long id = resource == 2 ? stock(2) : resource; String cookie = scenario.account(STAFF);
        assertThatThrownBy(() -> as(STAFF, () -> inventory.getStoreProduct(id))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> as(STAFF, () -> inventory.getHistory(id, 0))).isInstanceOf(ResourceNotFoundException.class);
        for (String path : List.of(adjustPath(id), historyPath(id))) {
            var response = scenario.get(path + "?store_id=1&storeId=1&staffEmail=" + PEER, cookie);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).doesNotContain("id=\"current-quantity\"", "data-movement-id", "id=\"newQuantity\"");
        }
        var before = scenario.snapshot();
        assertThat(scenario.post(cookie, adjustPath(id), "newQuantity=1&note=Forged&storeId=1&staffEmail=" + PEER).statusCode()).isEqualTo(resource == 2 ? 403 : 404);
        assertThat(scenario.snapshot()).isEqualTo(before);
        assertThat(scenario.post(cookie, adjustPath(id), "newQuantity=-1&note=Forged").statusCode()).isEqualTo(404);
    }
    @Test void historyIncludesSystemOrderCancellationAndStaffAdjustmentWithAccurateActors() throws Exception {
        var f = scenario.mixed(); scenario.onlineResult(f, false);
        long id = scalar("select store_product_id from dbo.order_items where order_id=?", f.b());
        int previous = quantity(id); String note = "Count after cancellation";
        assertThat(as(PEER, () -> inventory.adjust(id, new StockAdjustRequest(previous + 2, note)))).isTrue();
        var result = as(PEER, () -> inventory.getHistory(id, 0));
        assertThat(result.stock().storeId()).isEqualTo(2L);
        assertThat(result.movements().getContent()).extracting(m -> m.type()).contains(InventoryMovementType.ORDER, InventoryMovementType.CANCEL_ORDER, InventoryMovementType.STOCK_ADJUST);
        assertThat(result.movements().getContent()).filteredOn(m -> m.type() == InventoryMovementType.CANCEL_ORDER)
                .allSatisfy(m -> { assertThat(m.staffId()).isNull(); assertThat(m.staffName()).isEqualTo("Hệ thống"); });
        var latest = result.movements().getContent().get(0); assertThat(latest.quantityBefore()).isEqualTo(previous);
        assertThat(latest.quantityAfter()).isEqualTo(previous + 2); assertThat(latest.quantityChange()).isEqualTo(2); assertThat(latest.note()).isEqualTo(note);
        var html = scenario.get(historyPath(id), scenario.account(PEER));
        assertThat(html.statusCode()).isEqualTo(200); assertThat(html.body()).contains("ORDER", "CANCEL_ORDER", "STOCK_ADJUST", "Hệ thống", latest.staffName(), note);
    }
    @Test void inventoryHistoryPaginationHasStableOrderAndCountsOnlyTheScopedProduct() throws Exception {
        long id = stock(1); int previous = quantity(id);
        for (int i = 1; i <= 25; i++) adjust(id, previous + i, "Count " + i);
        as(PEER, () -> inventory.adjust(stock(2), new StockAdjustRequest(99, "Peer only")));
        var ids = history(id).stream().map(r -> (Long) r.get("movement_id")).toList();
        var first = as(STAFF, () -> inventory.getHistory(id, 0));
        assertThat(first.movements().getTotalElements()).isEqualTo(ids.size()); assertThat(first.movements().getSize()).isEqualTo(20);
        assertThat(first.movements().getContent().stream().map(m -> m.id())).containsExactlyElementsOf(ids.subList(0, 20));
        String cookie = scenario.account(STAFF); var response = scenario.get(historyPath(id) + "?store_id=2", cookie);
        assertThat(htmlIds(response.body(), "data-movement-id")).containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(response.body()).contains(historyPath(id) + "?page=1").doesNotContain("Peer only");
        assertThat(htmlIds(scenario.get(historyPath(id) + "?page=1", cookie).body(), "data-movement-id")).containsExactlyElementsOf(ids.subList(20, ids.size()));
        assertThat(as(STAFF, () -> inventory.getHistory(id, 999)).movements().getContent()).isEmpty();
    }
    @Test void noActiveAssignmentDeniesStockHistoryAndAdjustmentWithTheSameJwt() throws Exception {
        String cookie = scenario.account(STAFF); long id = stock(1);
        var assignment = jdbc.queryForMap("select * from dbo.staff_store_assignments where user_id=(select user_id from dbo.users where email=?) and store_id=1", STAFF);
        try {
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id=?", assignment.get("assignment_id"));
            var before = scenario.snapshot();
            for (String path : List.of("/staff/stock", "/staff/inventory-history", historyPath(id), adjustPath(id)))
                assertThat(scenario.get(path + "?store_id=1", cookie).statusCode()).isEqualTo(403);
            assertThat(scenario.post(cookie, adjustPath(id), "newQuantity=1&note=Count").statusCode()).isEqualTo(403);
            assertThatThrownBy(() -> adjust(id, 1, "Count")).isInstanceOf(AccessDeniedException.class);
            assertThat(scenario.snapshot()).isEqualTo(before);
        } finally { jdbc.update("update dbo.staff_store_assignments set status=? where assignment_id=?", assignment.get("status"), assignment.get("assignment_id")); }
    }
    @Test void multipleActiveAssignmentsAllowOnlyTheirStockAndRevocationBlocksFurtherWrites() throws Exception {
        long user = scalar("select user_id from dbo.users where email=?", STAFF), max = scalar("select coalesce(max(assignment_id),0) from dbo.staff_store_assignments");
        long peer = stock(2); String cookie = scenario.account(STAFF);
        try {
            jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())", user, user);
            assertThat(as(STAFF, () -> inventory.getStock(0)).getContent()).extracting(s -> s.storeId()).contains(1L, 2L).doesNotContain(4L, 5L);
            assertThat(adjust(peer, quantity(peer) + 1, "Assigned both")).isTrue();
            assertThat(history(peer).get(0)).containsEntry("staff_id", user);
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id>? and store_id=2", max);
            var before = scenario.snapshot();
            assertThat(scenario.get(historyPath(peer), cookie).statusCode()).isEqualTo(404);
            assertThat(scenario.post(cookie, adjustPath(peer), "newQuantity=1&note=Revoked&store_id=2").statusCode()).isEqualTo(403);
            assertThat(scenario.snapshot()).isEqualTo(before);
        } finally { jdbc.update("delete from dbo.staff_store_assignments where assignment_id>? and user_id=?", max, user); }
    }
    @ParameterizedTest @ValueSource(strings = {"khachhang1@example.com", "admin@oneshop.vn"})
    void customerAndAdminCannotUseStaffInventoryRoutes(String email) throws Exception {
        String cookie = scenario.account(email); var before = scenario.snapshot();
        for (String path : List.of("/staff/stock", "/staff/inventory-history", adjustPath(stock(1)), historyPath(stock(1))))
            assertThat(scenario.get(path, cookie).statusCode()).isEqualTo(403);
        assertThat(scenario.post(cookie, adjustPath(stock(1)), "newQuantity=1&note=Count").statusCode()).isEqualTo(403);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @Test void guestsAndCustomerCatalogCannotReadExactInventory() throws Exception {
        long id = stock(1);
        for (String path : List.of("/staff/stock", adjustPath(id), historyPath(id))) assertThat(scenario.get(path, "").statusCode()).isEqualTo(302);
        long product = scalar("select product_id from dbo.store_products where store_product_id=?", id);
        var page = scenario.get("/products/" + product, ""); assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).doesNotContain("data-field=\"exact-quantity\"", "id=\"current-quantity\"", "data-movement-id");
        var admin = scenario.get("/admin/store-products/" + id + "/edit", scenario.account("admin@oneshop.vn"));
        assertThat(admin.statusCode()).isEqualTo(200); assertThat(admin.body()).contains("name=\"quantity\"", "value=\"" + quantity(id) + "\"");
    }
    @Test void stockFormsRequireCsrfForCookieBearerAndEncodedPaths() throws Exception {
        long id = stock(1); String cookie = scenario.account(STAFF), bearer = "Bearer " + cookie.substring("ONESHOP_TOKEN=".length());
        var before = scenario.snapshot();
        var form = scenario.get(adjustPath(id), cookie); assertThat(form.statusCode()).isEqualTo(200);
        assertThat(form.body()).contains("name=\"_csrf\"", "name=\"newQuantity\"", "name=\"note\"");
        for (String path : List.of(adjustPath(id), adjustPath(id).replace("stock", "%73tock").replace("adjust", "%61djust"))) {
            assertThat(scenario.raw(path, "newQuantity=1&note=Count", "Cookie", cookie).statusCode()).isEqualTo(403);
            assertThat(scenario.raw(path, "newQuantity=1&note=Count", "Authorization", bearer).statusCode()).isEqualTo(403);
        }
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"AFTER_STOCK_SQL", "AFTER_MOVEMENT_SQL"})
    void writeFailureRollsBackRealSqlStockAndMovementTogether(String stage) throws Exception {
        long id = stock(1); int after = quantity(id) + 1; var before = scenario.snapshot();
        doAnswer(invocation -> {
            // Force the dirty stock UPDATE before the injected failure, observing actual SQL in this transaction.
            products.flush(); assertThat(quantity(id)).isEqualTo(after);
            if (stage.equals("AFTER_MOVEMENT_SQL")) {
                // A Spring Data interface spy cannot callRealMethod; persist/flush the same pending entity as Phase 9 probes.
                entityManager.persist(invocation.getArgument(0)); entityManager.flush();
                assertThat(history(id).get(0)).containsEntry("quantity_after", after);
            }
            throw new DataIntegrityViolationException("Injected failure after real SQL writes");
        }).when(movements).save(any(InventoryMovement.class));
        try {
            assertThatThrownBy(() -> adjust(id, after, "Failure fixture")).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(scenario.snapshot()).isEqualTo(before);
            var response = scenario.post(scenario.account(STAFF), adjustPath(id), "newQuantity=" + after + "&note=Failure");
            assertThat(response.statusCode()).isEqualTo(409); assertThat(scenario.snapshot()).isEqualTo(before);
        } finally { reset(movements); }
    }
    private static final class Abort extends RuntimeException { }
    @Test void outerRollbackAlsoRestoresCommittedLookingStockAuditAndTimestampRows() {
        long id = stock(1); int after = quantity(id) + 1; var before = scenario.snapshot();
        assertThatThrownBy(() -> as(STAFF, () -> new TransactionTemplate(transactions).execute(tx -> {
            inventory.adjust(id, new StockAdjustRequest(after, "Outer rollback")); products.flush();
            assertThat(quantity(id)).isEqualTo(after); assertThat(history(id).get(0)).containsEntry("quantity_after", after);
            throw new Abort();
        }))).isExactlyInstanceOf(Abort.class);
        assertThat(scenario.snapshot()).isEqualTo(before);
    }
    @Test void concurrentAdjustmentsKeepSerialBeforeAfterValuesAndOneMovementPerActualChange() throws Exception {
        long id = stock(1); int original = quantity(id); long max = scalar("select coalesce(max(movement_id),0) from dbo.inventory_movements");
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return adjust(id, original + 1, "First count"); });
            var b = pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return adjust(id, original + 2, "Second count"); });
            start.countDown(); assertThat(a.get(20, TimeUnit.SECONDS)).isTrue(); assertThat(b.get(20, TimeUnit.SECONDS)).isTrue();
            var entries = jdbc.queryForList("select * from dbo.inventory_movements where movement_id>? order by movement_id", max);
            assertThat(entries).hasSize(2); assertThat(entries.get(0)).containsEntry("quantity_before", original);
            assertThat(entries.get(1).get("quantity_before")).isEqualTo(entries.get(0).get("quantity_after"));
            assertThat(quantity(id)).isEqualTo(entries.get(1).get("quantity_after"));
            for (var entry : entries) assertThat((Integer) entry.get("quantity_change"))
                    .isEqualTo((Integer) entry.get("quantity_after") - (Integer) entry.get("quantity_before"));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
}
