package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.StoreGroupCheckoutRequest;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.PaymentResponse;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.PaymentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real dev/validate + SQL Server + Tomcat. Every fixture comes from Phase 8; committed data is restored per test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PaymentDatabaseIntegrationTest {
    private static final String ALICE = "khachhang1@example.com";
    private static final String BOB = "khachhang2@example.com";
    @Autowired private PaymentService payments;
    @Autowired private CheckoutService checkout;
    @Autowired private CartService cart;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private InventoryMovementRepository movements;
    @MockitoSpyBean private OrderStatusHistoryRepository histories;
    @LocalServerPort private int port;
    private SeedGuard guard;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void remember() {
        guard = new SeedGuard(jdbc);
        guard.remember();
    }

    @AfterEach
    void restore() {
        reset(movements, histories);
        guard.restore();
    }

    private long sp(String sku, long storeId) {
        return jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p "
                + "on p.product_id = sp.product_id where sp.store_id = ? and p.sku = ?", Long.class, storeId, sku);
    }

    private long add(long stockId, int quantity) {
        cart.addItem(ALICE, new AddCartItemRequest(stockId, quantity));
        return jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id "
                + "join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, ALICE, stockId);
    }

    private StoreGroupCheckoutRequest group(long storeId, PaymentMethod method) {
        return new StoreGroupCheckoutRequest(storeId, method == PaymentMethod.COD ? FulfillmentType.DELIVERY : FulfillmentType.STORE_PICKUP,
                method, "Khách kiểm thử", "0912345678", method == PaymentMethod.COD ? "1 Đường Thử Nghiệm" : null);
    }

    private long order(PaymentMethod method, boolean multiItem) {
        List<Long> lines = new ArrayList<>();
        lines.add(add(sp("COCOON-SERUM-30", 1), 2));
        if (multiItem) lines.add(add(sp("BBIA-CHEEK-08", 1), 1));
        return checkout.placeOrder(ALICE, new CheckoutRequest(lines, List.of(group(1, method)))).orders().get(0).orderId();
    }

    private long online() { return order(PaymentMethod.ONLINE, false); }
    private String path(long orderId) { return "/orders/" + orderId + "/payments"; }
    private PaymentResponse attempt(long orderId) { return payments.createOnlinePaymentAttempt(ALICE, orderId); }
    private Map<String, Object> orderRow(long orderId) {
        return jdbc.queryForMap("select * from dbo.orders where order_id=?", orderId);
    }
    private Map<String, Object> paymentRow(long id) {
        return jdbc.queryForMap("select * from dbo.payments where payment_id=?", id);
    }
    private List<Map<String, Object>> stockRows() {
        return jdbc.queryForList("select store_product_id,quantity,price,status from dbo.store_products order by store_product_id");
    }
    private List<Map<String, Object>> cancelMovements(long id) {
        return jdbc.queryForList("select * from dbo.inventory_movements where reference_order_id=? and type='CANCEL_ORDER' order by store_product_id", id);
    }
    private List<Map<String, Object>> history(long id) {
        return jdbc.queryForList("select * from dbo.order_status_history where order_id=? order by history_id", id);
    }
    private void assertState(long id, String orderState, String paymentState) {
        assertThat(orderRow(id)).containsEntry("order_status", orderState).containsEntry("payment_status", paymentState);
    }
    private void assertHistory(long id, String next) {
        var entries = history(id);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("old_status", null).containsEntry("new_status", "PENDING_PAYMENT");
        assertThat(entries.get(1)).containsEntry("old_status", "PENDING_PAYMENT").containsEntry("new_status", next)
                .containsEntry("changed_by_user_id", null);
        assertThat(entries.get(1).get("changed_at")).isNotNull();
    }

    @Test
    void pendingBelongsToOrderAndUsesItsAmountAndMethod() {
        long id = online();
        assertThat(jdbc.queryForObject("select count(*) from dbo.payments where order_id=?", Integer.class, id)).isZero();
        var created = attempt(id);
        assertThat(created.orderId()).isEqualTo(id);
        assertThat(created.amount()).isEqualByComparingTo(orderRow(id).get("total_amount").toString());
        assertThat(created.method()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(created.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.paidAt()).isNull();
        assertThat(created.transactionCode()).isNull();
        assertThat(created.createdAt()).isNotNull();
        assertState(id, "PENDING_PAYMENT", "UNPAID");
        assertThat(history(id)).hasSize(1);
    }

    @Test
    void repeatedStartReusesPendingAndSchemaRemainsOneToMany() {
        long id = online();
        var first = attempt(id);
        assertThat(attempt(id).paymentId()).isEqualTo(first.paymentId());
        assertThat(payments.getOrderPayments(ALICE, id).attempts()).hasSize(1);
        // Mapping/schema stays 1-N; this is a schema check, not a retry feature.
        jdbc.update("insert into dbo.payments(order_id,method,amount,status) select order_id,payment_method,total_amount,'PENDING' "
                + "from dbo.orders where order_id=?", id);
        assertThat(payments.getOrderPayments(ALICE, id).attempts()).hasSize(2);
    }

    @Test
    void successConfirmsOrderWithAuditAndDoesNotTouchStockOrSnapshots() {
        long id = order(PaymentMethod.ONLINE, true);
        var pending = attempt(id);
        var stock = stockRows();
        var counts = guard.counts();
        var items = jdbc.queryForList("select * from dbo.order_items where order_id=?", id);
        var result = payments.markOnlinePaymentSuccess(ALICE, id, pending.paymentId());
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.paidAt()).isNotNull();
        assertThat(result.transactionCode()).matches("PAY-[0-9a-f-]{36}");
        assertThat(paymentRow(result.paymentId())).containsEntry("status", "SUCCESS");
        assertState(id, "CONFIRMED", "PAID");
        assertHistory(id, "CONFIRMED");
        assertThat(stockRows()).isEqualTo(stock);
        assertThat(guard.counts().get("inventory_movements")).isEqualTo(counts.get("inventory_movements"));
        assertThat(cancelMovements(id)).isEmpty();
        assertThat(jdbc.queryForList("select * from dbo.order_items where order_id=?", id)).isEqualTo(items);
    }

    @Test
    void failureCancelsAndRestoresEverySnapshotToItsOwnStoreProduct() {
        var stockBefore = stockRows();
        long id = order(PaymentMethod.ONLINE, true);
        var pending = attempt(id);
        var items = jdbc.queryForList("select * from dbo.order_items where order_id=? order by store_product_id", id);
        var result = payments.markOnlinePaymentFailed(ALICE, id, pending.paymentId());
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.paidAt()).isNull();
        assertThat(result.transactionCode()).startsWith("PAY-");
        assertState(id, "CANCELLED", "FAILED");
        assertHistory(id, "CANCELLED");
        assertThat(stockRows()).isEqualTo(stockBefore);
        var restored = cancelMovements(id);
        assertThat(restored).hasSize(items.size());
        for (int i = 0; i < items.size(); i++) {
            assertThat(restored.get(i)).containsEntry("store_product_id", items.get(i).get("store_product_id"))
                    .containsEntry("quantity_change", items.get(i).get("quantity"))
                    .containsEntry("reference_order_id", id).containsEntry("staff_id", null);
            int change = (Integer) restored.get(i).get("quantity_change");
            assertThat(change).isPositive();
            assertThat((Integer) restored.get(i).get("quantity_after")).isEqualTo((Integer) restored.get(i).get("quantity_before") + change);
            assertThat(restored.get(i).get("created_at")).isNotNull();
        }
        assertThat(jdbc.queryForList("select * from dbo.order_items where order_id=? order by store_product_id", id)).isEqualTo(items);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateResultDoesNotRepeatAnyWrites(boolean success) {
        long id = online();
        long pid = attempt(id).paymentId();
        var first = complete(id, pid, success);
        var order = orderRow(id);
        var record = paymentRow(pid);
        var stock = stockRows();
        var counts = guard.counts();
        var repeated = complete(id, pid, success);
        assertThat(repeated.paymentId()).isEqualTo(first.paymentId());
        assertThat(repeated.transactionCode()).isEqualTo(first.transactionCode());
        assertThat(orderRow(id)).isEqualTo(order);
        assertThat(paymentRow(pid)).isEqualTo(record);
        assertThat(stockRows()).isEqualTo(stock);
        assertThat(guard.counts()).isEqualTo(counts);
        assertThatThrownBy(() -> complete(id, pid, !success)).isInstanceOf(BadRequestException.class);
        assertThat(guard.counts()).isEqualTo(counts);
        assertThatThrownBy(() -> attempt(id)).isInstanceOf(BadRequestException.class);
    }

    private PaymentResponse complete(long id, long pid, boolean success) {
        return success ? payments.markOnlinePaymentSuccess(ALICE, id, pid) : payments.markOnlinePaymentFailed(ALICE, id, pid);
    }

    @Test
    void ownershipAndMissingIdsAreCheckedForEveryOperation() {
        long id = online();
        long pid = attempt(id).paymentId();
        var counts = guard.counts();
        assertThatThrownBy(() -> payments.getOrderPayments(BOB, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> payments.createOnlinePaymentAttempt(BOB, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> payments.markOnlinePaymentSuccess(BOB, id, pid)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> payments.markOnlinePaymentFailed(BOB, id, pid)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> payments.getOrderPayments(ALICE, Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> attempt(Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> complete(id, Long.MAX_VALUE, true)).isInstanceOf(ResourceNotFoundException.class);
        long other = online();
        assertThatThrownBy(() -> complete(other, pid, false)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(guard.counts().get("payments")).isEqualTo(counts.get("payments"));
        assertState(id, "PENDING_PAYMENT", "UNPAID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "method"})
    void inconsistentPaymentIsRefusedWithoutMutation(String field) {
        long id = online();
        long pid = attempt(id).paymentId();
        if (field.equals("amount")) jdbc.update("update dbo.payments set amount=1 where payment_id=?", pid);
        else jdbc.update("update dbo.payments set method='COD' where payment_id=?", pid);
        var stock = stockRows();
        var counts = guard.counts();
        assertThatThrownBy(() -> complete(id, pid, false)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> complete(id, pid, true)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> attempt(id)).isInstanceOf(BadRequestException.class);
        assertThat(stockRows()).isEqualTo(stock);
        assertThat(guard.counts()).isEqualTo(counts);
        assertState(id, "PENDING_PAYMENT", "UNPAID");
        assertThat(paymentRow(pid)).containsEntry("status", "PENDING");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"COD", "PAY_AT_STORE"})
    void offlineOrdersStayConfirmedUnpaid(PaymentMethod method) {
        long id = order(method, false);
        assertThatThrownBy(() -> attempt(id)).isInstanceOf(BadRequestException.class);
        // Even a malformed legacy/demo attempt must not allow ONLINE completion of an offline Order.
        jdbc.update("insert into dbo.payments(order_id,method,amount,status) select order_id,payment_method,total_amount,'PENDING' "
                + "from dbo.orders where order_id=?", id);
        long pid = payments.getOrderPayments(ALICE, id).attempts().get(0).paymentId();
        assertThatThrownBy(() -> complete(id, pid, true)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> complete(id, pid, false)).isInstanceOf(BadRequestException.class);
        assertState(id, "CONFIRMED", "UNPAID");
        assertThat(orderRow(id)).containsEntry("pickup_code", null).containsEntry("ready_at", null).containsEntry("picked_up_at", null);
        assertThat(paymentRow(pid)).containsEntry("status", "PENDING").containsEntry("paid_at", null);
    }

    @Test
    void orderNoLongerPendingCannotCompletePayment() {
        long id = online();
        long pid = attempt(id).paymentId();
        jdbc.update("update dbo.orders set order_status='CANCELLED' where order_id=?", id);
        var stock = stockRows();
        assertThatThrownBy(() -> complete(id, pid, true)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> complete(id, pid, false)).isInstanceOf(BadRequestException.class);
        assertThat(stockRows()).isEqualTo(stock);
        assertThat(paymentRow(pid)).containsEntry("status", "PENDING");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void paymentDoesNotChangeOtherOrdersInSameCheckout(boolean success) {
        CheckoutResultResponse result = checkout.placeOrder(ALICE, new CheckoutRequest(List.of(
                add(sp("COCOON-SERUM-30", 1), 1), add(sp("INNI-TONER-200", 2), 1), add(sp("INNI-CLEANS-120", 4), 1)),
                List.of(group(1, PaymentMethod.ONLINE), group(2, PaymentMethod.COD), group(4, PaymentMethod.PAY_AT_STORE))));
        long id = result.orders().stream().filter(o -> o.paymentMethod() == PaymentMethod.ONLINE).findFirst().orElseThrow().orderId();
        var others = result.orders().stream().filter(o -> !o.orderId().equals(id)).map(o -> orderRow(o.orderId())).toList();
        var session = jdbc.queryForMap("select * from dbo.checkout_sessions where checkout_id=?", result.checkoutId());
        complete(id, attempt(id).paymentId(), success);
        assertThat(result.orders().stream().filter(o -> !o.orderId().equals(id)).map(o -> orderRow(o.orderId())).toList()).isEqualTo(others);
        assertThat(jdbc.queryForMap("select * from dbo.checkout_sessions where checkout_id=?", result.checkoutId())).isEqualTo(session);
        assertThat(others).allSatisfy(row -> assertThat(row).containsEntry("payment_status", "UNPAID").containsEntry("order_status", "CONFIRMED"));
    }

    @Test
    void secondRestoreFailureRollsBackFirstFlushedItemPaymentOrderAndAudit() {
        long id = order(PaymentMethod.ONLINE, true);
        long pid = attempt(id).paymentId();
        var stock = stockRows();
        var counts = guard.counts();
        var order = orderRow(id);
        var payment = paymentRow(pid);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            InventoryMovement movement = invocation.getArgument(0);
            if (movement.getType() == InventoryMovementType.CANCEL_ORDER && calls.incrementAndGet() == 2) {
                throw new IllegalStateException("Injected second restore failure");
            }
            // Repository interfaces have no concrete method for Mockito.callRealMethod; persist via real JPA.
            entityManager.persist(movement);
            entityManager.flush(); // First stock/movement and Payment FAILED are actually written inside the transaction.
            return movement;
        }).when(movements).save(any(InventoryMovement.class));
        assertThatThrownBy(() -> complete(id, pid, false)).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(stockRows()).isEqualTo(stock);
        assertThat(guard.counts()).isEqualTo(counts);
        assertThat(orderRow(id)).isEqualTo(order);
        assertThat(paymentRow(pid)).isEqualTo(payment);
        assertThat(cancelMovements(id)).isEmpty();
        reset(movements);
        complete(id, pid, false);
        assertState(id, "CANCELLED", "FAILED");
        assertThat(cancelMovements(id)).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void historyFailureRollsBackWholeResult(boolean success) {
        long id = online();
        long pid = attempt(id).paymentId();
        var stock = stockRows();
        var counts = guard.counts();
        doThrow(new IllegalStateException("Injected history failure")).when(histories).save(any(OrderStatusHistory.class));
        assertThatThrownBy(() -> complete(id, pid, success)).isInstanceOf(IllegalStateException.class);
        assertThat(stockRows()).isEqualTo(stock);
        assertThat(guard.counts()).isEqualTo(counts);
        assertState(id, "PENDING_PAYMENT", "UNPAID");
        assertThat(paymentRow(pid)).containsEntry("status", "PENDING").containsEntry("transaction_code", null).containsEntry("paid_at", null);
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> tasks = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                tasks.add(pool.submit(() -> {
                    start.await();
                    try { return task.call(); } catch (Exception ex) { return ex; }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (var task : tasks) results.add(task.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentSameResultTransitionsAndRestoresAtMostOnce(boolean success) throws Exception {
        for (int round = 0; round < 3; round++) {
            var beforeCheckout = stockRows();
            long id = order(PaymentMethod.ONLINE, true);
            long pid = attempt(id).paymentId();
            var beforeResult = stockRows();
            var counts = guard.counts();
            var results = race(() -> complete(id, pid, success), () -> complete(id, pid, success));
            assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(PaymentResponse.class));
            assertThat(((PaymentResponse) results.get(0)).transactionCode()).isEqualTo(((PaymentResponse) results.get(1)).transactionCode());
            assertState(id, success ? "CONFIRMED" : "CANCELLED", success ? "PAID" : "FAILED");
            assertHistory(id, success ? "CONFIRMED" : "CANCELLED");
            assertThat(guard.counts().get("order_status_history") - counts.get("order_status_history")).isEqualTo(1);
            assertThat(cancelMovements(id)).hasSize(success ? 0 : 2);
            assertThat(stockRows()).isEqualTo(success ? beforeResult : beforeCheckout);
        }
    }

    @Test
    void concurrentStartCreatesOnePendingAttempt() throws Exception {
        long id = online();
        var results = race(() -> attempt(id), () -> attempt(id));
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(PaymentResponse.class));
        assertThat(((PaymentResponse) results.get(0)).paymentId()).isEqualTo(((PaymentResponse) results.get(1)).paymentId());
        assertThat(payments.getOrderPayments(ALICE, id).attempts()).hasSize(1);
    }

    @Test
    void concurrentFailuresOfDifferentOrdersSharingStockDoNotLoseRestores() throws Exception {
        var before = stockRows();
        long firstOrder = order(PaymentMethod.ONLINE, true);
        long secondOrder = order(PaymentMethod.ONLINE, true);
        long firstPayment = attempt(firstOrder).paymentId();
        long secondPayment = attempt(secondOrder).paymentId();
        var results = race(() -> complete(firstOrder, firstPayment, false), () -> complete(secondOrder, secondPayment, false));
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(PaymentResponse.class));
        assertThat(stockRows()).isEqualTo(before);
        assertHistory(firstOrder, "CANCELLED");
        assertHistory(secondOrder, "CANCELLED");
        assertThat(cancelMovements(firstOrder)).hasSize(2);
        assertThat(cancelMovements(secondOrder)).hasSize(2);
    }

    @Test
    void conflictingConcurrentResultsHaveOneWinner() throws Exception {
        long id = online();
        long pid = attempt(id).paymentId();
        var results = race(() -> complete(id, pid, true), () -> complete(id, pid, false));
        assertThat(results.stream().filter(PaymentResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(BadRequestException.class::isInstance).count()).isEqualTo(1);
        boolean success = paymentRow(pid).get("status").equals("SUCCESS");
        assertState(id, success ? "CONFIRMED" : "CANCELLED", success ? "PAID" : "FAILED");
        assertHistory(id, success ? "CONFIRMED" : "CANCELLED");
        assertThat(cancelMovements(id)).hasSize(success ? 0 : 1);
    }

    private HttpResponse<String> request(String path, String body, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0) builder.headers(headers);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private String login(String email) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}")).build(), HttpResponse.BodyHandlers.ofString());
        Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(token.find()).as("login of %s", email).isTrue();
        return "ONESHOP_TOKEN=" + token.group(1);
    }
    private HttpResponse<String> postAs(String cookie, String path, String body) throws Exception {
        var page = request("/login", null);
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String csrfCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return request(path, body + "&_csrf=" + csrf.group(1), "Cookie", cookie + "; " + csrfCookie);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void httpWorkflowIgnoresForgedFieldsAndShowsResult(boolean success) throws Exception {
        long id = online();
        String cookie = login(ALICE);
        var page = request(path(id), null, "Cookie", cookie);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Tiến hành thanh toán online", "không thu tiền thật");
        String forged = "amount=1&method=COD&userId=6&status=SUCCESS&paymentStatus=PAID&orderStatus=CONFIRMED&transactionCode=HACK&paidAt=2000-01-01";
        assertThat(postAs(cookie, path(id) + "/attempts", forged).statusCode()).isEqualTo(302);
        var attempt = payments.getOrderPayments(ALICE, id).attempts().get(0);
        assertThat(attempt.amount()).isEqualByComparingTo(orderRow(id).get("total_amount").toString());
        assertThat(attempt.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(attempt.method()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(attempt.transactionCode()).isNull();
        page = request(path(id), null, "Cookie", cookie);
        assertThat(page.body()).contains("Mô phỏng thành công", "Mô phỏng thất bại").doesNotContain("id=\"start-payment\"");
        String resultPath = path(id) + "/" + attempt.paymentId() + (success ? "/success" : "/failure");
        assertThat(postAs(cookie, resultPath, forged).statusCode()).isEqualTo(302);
        var counts = guard.counts();
        assertThat(postAs(cookie, resultPath, "").statusCode()).isEqualTo(302);
        assertThat(guard.counts()).isEqualTo(counts);
        page = request(path(id), null, "Cookie", cookie);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("PAY-", success ? "Đã xác nhận" : "Đã hủy").doesNotContain("Mô phỏng thành công");
        assertThat(request("/checkout/" + orderRow(id).get("checkout_id"), null, "Cookie", cookie).body())
                .contains(success ? "Đã thanh toán" : "Thanh toán thất bại");
    }

    @Test
    void httpOwnershipAndPaymentOrderMismatchReturn404() throws Exception {
        long id = online();
        long pid = attempt(id).paymentId();
        String bob = login(BOB);
        assertThat(request(path(id), null, "Cookie", bob).statusCode()).isEqualTo(404);
        assertThat(postAs(bob, path(id) + "/attempts", "").statusCode()).isEqualTo(404);
        assertThat(postAs(bob, path(id) + "/" + pid + "/success", "").statusCode()).isEqualTo(404);
        assertThat(postAs(bob, path(id) + "/" + pid + "/failure", "").statusCode()).isEqualTo(404);
        long other = online();
        assertThat(postAs(login(ALICE), path(other) + "/" + pid + "/failure", "").statusCode()).isEqualTo(404);
        assertState(id, "PENDING_PAYMENT", "UNPAID");
    }

    @Test
    void csrfRequiredForStartAndBothResultsEvenWithBearer() throws Exception {
        long id = online();
        long pid = attempt(id).paymentId();
        String cookie = login(ALICE);
        for (String suffix : List.of("/attempts", "/" + pid + "/success", "/" + pid + "/failure")) {
            assertThat(request(path(id) + suffix, "", "Cookie", cookie).statusCode()).isEqualTo(403);
            assertThat(request(path(id) + suffix, "", "Authorization", "Bearer " + cookie.substring("ONESHOP_TOKEN=".length())).statusCode()).isEqualTo(403);
        }
        assertState(id, "PENDING_PAYMENT", "UNPAID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"guest", "staff.thuduc@oneshop.vn", "admin@oneshop.vn"})
    void clientPaymentRoutesAreCustomerOnly(String email) throws Exception {
        long id = online();
        long pid = attempt(id).paymentId();
        String cookie = email.equals("guest") ? "" : login(email);
        int expected = email.equals("guest") ? 302 : 403;
        assertThat(request(path(id), null, "Cookie", cookie).statusCode()).isEqualTo(expected);
        for (String suffix : List.of("/attempts", "/" + pid + "/success", "/" + pid + "/failure")) {
            assertThat(postAs(cookie, path(id) + suffix, "").statusCode()).isEqualTo(expected);
        }
        assertState(id, "PENDING_PAYMENT", "UNPAID");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"COD", "PAY_AT_STORE"})
    void httpOfflineOrderHasNoOnlineControlsAndCannotStart(PaymentMethod method) throws Exception {
        long id = order(method, false);
        String cookie = login(ALICE);
        var page = request(path(id), null, "Cookie", cookie);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).doesNotContain("id=\"start-payment\"", "Mô phỏng thành công");
        assertThat(postAs(cookie, path(id) + "/attempts", "").statusCode()).isEqualTo(400);
        assertState(id, "CONFIRMED", "UNPAID");
    }
}
