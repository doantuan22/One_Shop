package com.oneshop;

import com.oneshop.dto.response.AdminPaymentCaseResponse;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.AdminOperationsService;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.PaymentCaseCategory;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.SimulatedResult;
import com.oneshop.service.payment.PaymentTimeoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Admin "Cần xử lý" and the payment console on the real SQL Server: one classification (PaymentCaseCategory) shared by
 * the console list, the case detail and the Dashboard; an UNKNOWN result inside the deadline is "Chờ đối soát" and is
 * not an Admin task; listing never changes a payment state. Orders come from the real checkout; the deadline is moved
 * with {@code orders.created_at}. These tests commit; {@link SeedGuardV3} puts the test database back, so they only run
 * on a {@code *_test} database. They never touch {@code oneshop}.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true",
        "oneshop.payment.timeout-worker-enabled=false"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class AdminAttentionAndPaymentCasesDatabaseIntegrationTest {

    static final String ADMIN = "admin@example.com";
    static final String ALICE = "khachhang1@example.com";
    static final String SERUM = "SIMPLE-PURIFY-150";
    static final String TONER = "CERAVE-HYDRA-236";

    @Autowired
    PaymentReconciliationService reconciliation;
    @Autowired
    PaymentTimeoutService timeouts;
    @Autowired
    AdminPaymentService adminPayments;
    @Autowired
    AdminOperationsService operations;
    @Autowired
    CustomerOrderService customerOrders;
    @Autowired
    CartService cartService;
    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartRepository cartRepository;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    StoreProductRepository storeProductRepository;
    @Autowired
    JdbcTemplate jdbc;

    private SeedGuardV3 guard;
    private FulfillmentFixtures data;
    private boolean remembered;

    @BeforeEach
    void remember() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run with DB_NAME=oneshop_test");
        guard.remember();
        remembered = true;
        data = new FulfillmentFixtures(jdbc, cartService, checkoutService, cartRepository, cartItemRepository,
                productRepository, storeRepository, storeProductRepository);
    }

    @AfterEach
    void restore() {
        if (remembered) {
            guard.restore();
        }
    }

    // ------------------------------------------------------------------ helpers

    private <T> T asAdmin(Supplier<T> action) {
        return data.asRole(ADMIN, "ROLE_ADMIN", action);
    }

    private void simulate(long orderId, SimulatedResult scenario) {
        reconciliation.simulate(ALICE, orderId, data.attemptId(orderId), scenario);
    }

    private List<AdminPaymentCaseResponse> all(PaymentCaseCategory view, String search) {
        List<AdminPaymentCaseResponse> rows = new ArrayList<>();
        for (int page = 0; ; page++) {
            int p = page;
            var result = asAdmin(() -> adminPayments.getCases(view, search, p));
            rows.addAll(result.getContent());
            if (result.isLast()) {
                return rows;
            }
        }
    }

    private PaymentCaseCategory categoryOf(long orderId) {
        return all(null, String.valueOf(orderId)).stream().filter(c -> c.orderId() == orderId).findFirst()
                .orElseThrow().category();
    }

    private boolean inView(PaymentCaseCategory view, long orderId) {
        return all(view, String.valueOf(orderId)).stream().anyMatch(c -> c.orderId() == orderId);
    }

    private long attention() {
        return asAdmin(() -> operations.getChainSummary()).ordersNeedingAttention();
    }

    private long reconciling() {
        return asAdmin(() -> operations.getChainSummary()).reconcilingOrders();
    }

    private Map<String, Long> counts() {
        return asAdmin(() -> adminPayments.getCaseCounts());
    }

    private String fingerprint() {
        return jdbc.queryForObject("select cast(checksum_agg(binary_checksum(order_id, order_status, payment_status)) as varchar(40))"
                + " + '|' + cast((select checksum_agg(binary_checksum(payment_id, status, transaction_code, paid_at, refund_status))"
                + " from dbo.payments) as varchar(40)) from dbo.orders", String.class);
    }

    // ================================================================== classification

    @Test
    @DisplayName("ADM-01 inside the deadline: waiting for the customer is normal; UNKNOWN is 'Chờ đối soát'; neither is an Admin task")
    void insideTheDeadline() {
        long attentionBefore = attention();
        long reconcilingBefore = reconciling();

        long waiting = data.onlinePickup("OS-THUDUC", SERUM, 1);
        long unknown = data.onlinePickup("OS-THUDUC", TONER, 1);
        simulate(unknown, SimulatedResult.UNKNOWN);

        assertThat(categoryOf(waiting)).isEqualTo(PaymentCaseCategory.WAITING);
        assertThat(categoryOf(unknown)).isEqualTo(PaymentCaseCategory.RECONCILING);
        assertThat(PaymentCaseCategory.RECONCILING.label()).isEqualTo("Chờ đối soát");
        assertThat(inView(PaymentCaseCategory.RECONCILING, unknown)).isTrue();
        assertThat(inView(PaymentCaseCategory.ATTENTION, unknown)).isFalse();
        assertThat(inView(PaymentCaseCategory.ATTENTION, waiting)).isFalse();
        assertThat(attention()).as("nothing here needs an Admin").isEqualTo(attentionBefore);
        assertThat(reconciling()).isEqualTo(reconcilingBefore + 1);
        assertThat(data.orderStatus(unknown)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("ADM-02 past the deadline, still pending: an Admin task (also with an UNKNOWN attempt inside the reconciliation window)")
    void pastTheDeadline() {
        long attentionBefore = attention();
        long reconcilingBefore = reconciling();
        long silent = data.onlinePickup("OS-THUDUC", SERUM, 1);
        long unknown = data.onlinePickup("OS-THUDUC", TONER, 1);
        simulate(unknown, SimulatedResult.UNKNOWN);

        data.ageOrder(silent, 16);
        data.ageOrder(unknown, 16);

        assertThat(categoryOf(silent)).isEqualTo(PaymentCaseCategory.ATTENTION);
        assertThat(categoryOf(unknown)).isEqualTo(PaymentCaseCategory.ATTENTION);
        assertThat(attention()).isEqualTo(attentionBefore + 2);
        assertThat(reconciling()).as("past the deadline it is no longer merely waiting for evidence")
                .isEqualTo(reconcilingBefore);
        assertThat(data.orderStatus(silent)).as("listing decided nothing").isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("ADM-03 NEEDS_ATTENTION after the reconciliation window is an Admin task, for ONLINE and for a COD exception alike")
    void needsAttention() {
        long attentionBefore = attention();
        long online = data.onlinePickup("OS-THUDUC", SERUM, 1);
        simulate(online, SimulatedResult.UNKNOWN);
        data.ageOrder(online, 31);
        timeouts.scan();
        long cod = data.codDelivery("OS-THUDUC", TONER, 1);
        data.setOrderStatus(cod, "NEEDS_ATTENTION"); // a Fulfillment FAILED: the exception is not a payment case

        assertThat(data.orderStatus(online)).isEqualTo("NEEDS_ATTENTION");
        assertThat(categoryOf(online)).isEqualTo(PaymentCaseCategory.ATTENTION);
        assertThat(attention()).as("both Orders, each once").isEqualTo(attentionBefore + 2);
        assertThat(all(null, String.valueOf(cod))).as("a COD Order is not a payment case").isEmpty();
    }

    @Test
    @DisplayName("ADM-04 final states: paid, failed (cancelled without money) and refunded (simulated) are not Admin tasks")
    void finalStates() {
        long attentionBefore = attention();
        long paid = data.onlinePickup("OS-THUDUC", SERUM, 1);
        simulate(paid, SimulatedResult.SUCCESS);
        long failed = data.onlinePickup("OS-THUDUC", TONER, 1);
        simulate(failed, SimulatedResult.FAILED);
        long refunded = data.onlinePickup("OS-GOVAP", SERUM, 1);
        simulate(refunded, SimulatedResult.SUCCESS);
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, refunded);
            return null;
        });

        assertThat(categoryOf(paid)).isEqualTo(PaymentCaseCategory.PAID);
        assertThat(categoryOf(failed)).isEqualTo(PaymentCaseCategory.FAILED);
        assertThat(categoryOf(refunded)).isEqualTo(PaymentCaseCategory.REFUNDED);
        assertThat(attention()).isEqualTo(attentionBefore);
    }

    @Test
    @DisplayName("ADM-05 an open (failed) simulated refund is an Admin task until it is retried successfully")
    void openRefund() {
        long attentionBefore = attention();
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        simulate(order, SimulatedResult.SUCCESS);
        data.asRole(ALICE, "ROLE_CUSTOMER", () -> {
            customerOrders.cancelOrder(ALICE, order);
            return null;
        });
        assertThat(categoryOf(order)).isEqualTo(PaymentCaseCategory.REFUNDED);

        jdbc.update("update dbo.payments set refund_status = 'SIMULATION_FAILED' where order_id = ? and status = 'SUCCESS'", order);

        assertThat(categoryOf(order)).isEqualTo(PaymentCaseCategory.ATTENTION);
        assertThat(attention()).isEqualTo(attentionBefore + 1);
        asAdmin(() -> adminPayments.retryRefund(order));
        assertThat(categoryOf(order)).isEqualTo(PaymentCaseCategory.REFUNDED);
        assertThat(attention()).isEqualTo(attentionBefore);
    }

    @Test
    @DisplayName("ADM-06 Orders Staff are handling normally never count as needing an Admin")
    void normalStaffWorkIsNotAdminWork() {
        long attentionBefore = attention();
        long cod = data.codDelivery("OS-THUDUC", SERUM, 1);
        long pickup = data.payAtStorePickup("OS-THUDUC", TONER, 1);
        int applied = 0;
        for (String status : List.of("CONFIRMED", "PROCESSING", "PREPARING", "PACKED", "SHIPPING", "READY_FOR_PICKUP",
                "PARTIALLY_COMPLETED", "COMPLETED")) {
            for (long order : new long[] {cod, pickup}) {
                try {
                    data.setOrderStatus(order, status);
                    applied++;
                } catch (org.springframework.dao.DataIntegrityViolationException notAStatusOfThisOrder) {
                    continue; // the schema CHECKs forbid some combinations (for example SHIPPING for a pickup)
                }
                assertThat(attention()).as("status " + status).isEqualTo(attentionBefore);
            }
        }
        assertThat(applied).as("the statuses the schema allows were really exercised").isGreaterThanOrEqualTo(8);
        data.setOrderStatus(cod, "CANCELLED");
        assertThat(attention()).isEqualTo(attentionBefore);
    }

    // ================================================================== consistency

    @Test
    @DisplayName("ADM-07 every row of every filter carries that filter's category; the filters partition all ONLINE Orders; tab counts match the lists")
    void filtersListsAndCountsAgree() {
        long waiting = data.onlinePickup("OS-THUDUC", SERUM, 1);
        long unknown = data.onlinePickup("OS-THUDUC", TONER, 1);
        simulate(unknown, SimulatedResult.UNKNOWN);
        long overdue = data.onlinePickup("OS-GOVAP", SERUM, 1);
        data.ageOrder(overdue, 40);
        long paid = data.onlinePickup("OS-GOVAP", TONER, 1);
        simulate(paid, SimulatedResult.SUCCESS);
        long failed = data.onlinePickup("OS-QUAN7", SERUM, 1);
        simulate(failed, SimulatedResult.FAILED);

        Map<String, Long> counts = counts();
        long sum = 0;
        for (PaymentCaseCategory category : PaymentCaseCategory.values()) {
            if (category == PaymentCaseCategory.OTHER) {
                continue;
            }
            List<AdminPaymentCaseResponse> rows = all(category, null);
            assertThat(rows).as(category.name()).allMatch(c -> c.category() == category)
                    .hasSize(counts.get(category.key()).intValue());
            sum += rows.size();
        }
        List<AdminPaymentCaseResponse> everything = all(null, null);
        assertThat(counts.get("all")).isEqualTo(everything.size());
        assertThat(sum + everything.stream().filter(c -> c.category() == PaymentCaseCategory.OTHER).count())
                .as("no ONLINE Order is in two filters, none is lost").isEqualTo(everything.size());
        assertThat(everything).extracting(AdminPaymentCaseResponse::orderId)
                .contains(waiting, unknown, overdue, paid, failed);
        long dbOnline = jdbc.queryForObject("select count(*) from dbo.orders where payment_method = 'ONLINE'", Long.class);
        assertThat(everything).as("one row per Order, never per attempt or per Fulfillment").hasSize((int) dbOnline);
    }

    @Test
    @DisplayName("ADM-08 Dashboard 'Cần Admin xử lý' = ONLINE attention cases + non-ONLINE NEEDS_ATTENTION; Orders are counted once")
    void dashboardMatchesTheConsole() {
        data.ageOrder(data.onlinePickup("OS-THUDUC", SERUM, 1), 20);
        long split = data.onlineTwoStores("OS-THUDUC", TONER, "OS-GOVAP", SERUM);
        data.ageOrder(split, 20); // one ONLINE Order supplied by two Stores
        long cod = data.codDelivery("OS-THUDUC", "VASELINE-LIP-ORIG-48", 1);
        data.setOrderStatus(cod, "NEEDS_ATTENTION");

        long consoleAttention = counts().get("attention");
        long nonOnlineExceptions = jdbc.queryForObject("select count(*) from dbo.orders where payment_method <> 'ONLINE'"
                + " and order_status = 'NEEDS_ATTENTION'", Long.class);
        assertThat(attention()).isEqualTo(consoleAttention + nonOnlineExceptions);

        long goVap = storeRepository.findByCode("OS-GOVAP").orElseThrow().getId();
        long thuDuc = storeRepository.findByCode("OS-THUDUC").orElseThrow().getId();
        var overview = asAdmin(() -> operations.getOverview(null));
        long perStoreGoVap = overview.stream().filter(r -> r.store().id() == goVap).findFirst().orElseThrow().attentionOrders();
        long perStoreThuDuc = overview.stream().filter(r -> r.store().id() == thuDuc).findFirst().orElseThrow().attentionOrders();
        long independentGoVap = jdbc.queryForObject("select count(*) from dbo.orders o where exists (select 1 from "
                + "dbo.order_fulfillments f where f.order_id = o.order_id and f.store_id = ?) and (o.order_status = "
                + "'NEEDS_ATTENTION' or (o.payment_method = 'ONLINE' and o.order_status = 'PENDING_PAYMENT' and "
                + "o.created_at <= dateadd(minute, -15, sysdatetime())))", Long.class, goVap);
        assertThat(perStoreGoVap).isGreaterThanOrEqualTo(1).isEqualTo(independentGoVap);
        assertThat(perStoreThuDuc).isGreaterThanOrEqualTo(2);
        assertThat(attention()).as("the two-Store Order is one Order chain-wide")
                .isLessThan(overview.stream().mapToLong(r -> r.attentionOrders()).sum());
    }

    @Test
    @DisplayName("ADM-09 search: by Order id, customer e-mail or name; wildcards are literal; over-long text is harmless")
    void search() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);

        assertThat(all(null, String.valueOf(order))).extracting(AdminPaymentCaseResponse::orderId).containsExactly(order);
        assertThat(all(null, "KHACHHANG1@example")).extracting(AdminPaymentCaseResponse::orderId).contains(order);
        String name = jdbc.queryForObject("select full_name from dbo.users where email = ?", String.class, ALICE);
        assertThat(all(null, name.substring(0, 3).toLowerCase())).extracting(AdminPaymentCaseResponse::orderId).contains(order);
        assertThat(all(null, "%")).as("a literal percent sign matches nothing").isEmpty();
        assertThat(all(null, "_")).isEmpty();
        assertThat(all(null, "khong-ton-tai-" + "x".repeat(300))).isEmpty();
        assertThat(all(PaymentCaseCategory.WAITING, String.valueOf(order))).hasSize(1);
        assertThat(all(PaymentCaseCategory.PAID, String.valueOf(order))).isEmpty();
    }

    @Test
    @DisplayName("ADM-10 listing, counting and opening a case never change an Order or a payment")
    void readsChangeNothing() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 1);
        simulate(order, SimulatedResult.UNKNOWN);
        data.ageOrder(order, 20);
        String before = fingerprint();
        long history = data.orderHistory(order);

        for (PaymentCaseCategory category : PaymentCaseCategory.values()) {
            all(category == PaymentCaseCategory.OTHER ? null : category, null);
        }
        counts();
        attention();
        reconciling();
        asAdmin(() -> operations.getOverview(null));
        var detail = asAdmin(() -> adminPayments.getCase(order));

        assertThat(detail.summary().category()).isEqualTo(PaymentCaseCategory.ATTENTION);
        assertThat(fingerprint()).isEqualTo(before);
        assertThat(data.orderHistory(order)).isEqualTo(history);
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("ADM-11 only an administrator can read the console and the Dashboard numbers")
    void onlyAdmins() {
        for (String[] who : new String[][] {{ALICE, "ROLE_CUSTOMER"}, {"staff.thuduc@example.com", "ROLE_STAFF"}}) {
            try {
                data.asRole(who[0], who[1], () -> adminPayments.getCases(PaymentCaseCategory.ATTENTION, null, 0));
                throw new AssertionError("role " + who[1] + " must not read the payment console");
            } catch (org.springframework.security.access.AccessDeniedException expected) {
                // fine
            }
            try {
                data.asRole(who[0], who[1], () -> operations.getChainSummary());
                throw new AssertionError("role " + who[1] + " must not read the Dashboard numbers");
            } catch (org.springframework.security.access.AccessDeniedException expected) {
                // fine
            }
        }
    }
}
