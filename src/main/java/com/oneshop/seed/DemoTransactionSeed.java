package com.oneshop.seed;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.User;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.ReviewService;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.payment.CustomerOrderCancellationService;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.review.ReviewReplyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Explicit, opt-in DEV fixture. Every transaction uses the application's real service and state machine. */
@Component
@Profile("demo-seed & !prod")
@ConditionalOnProperty(name = "oneshop.seed.transactions", havingValue = "true")
public class DemoTransactionSeed implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoTransactionSeed.class);
    private static final String ADDRESS = "Khu giao nhận mô phỏng OneShop, Phường Thủ Đức, TP. Hồ Chí Minh, Việt Nam";
    private final JdbcTemplate jdbc;
    private final CartService cart;
    private final CheckoutService checkout;
    private final PaymentReconciliationService payments;
    private final CustomerOrderCancellationService cancellations;
    private final StaffFulfillmentService staff;
    private final ReviewService reviews;
    private final ReviewReplyService replies;
    private final UserRepository users;
    private final TransactionTemplate transaction;
    private final String databaseHost;

    public DemoTransactionSeed(JdbcTemplate jdbc, CartService cart, CheckoutService checkout,
            PaymentReconciliationService payments, CustomerOrderCancellationService cancellations,
            StaffFulfillmentService staff, ReviewService reviews, ReviewReplyService replies,
            UserRepository users, PlatformTransactionManager manager,
            @Value("${DB_HOST:localhost}") String databaseHost) {
        this.jdbc = jdbc; this.cart = cart; this.checkout = checkout; this.payments = payments;
        this.cancellations = cancellations; this.staff = staff; this.reviews = reviews;
        this.replies = replies; this.users = users; this.transaction = new TransactionTemplate(manager);
        this.databaseHost = databaseHost;
    }

    @Override
    public void run(String... args) {
        String db = jdbc.queryForObject("SELECT DB_NAME()", String.class);
        String approved = System.getenv("ONESHOP_SEED_DATABASE");
        if (!db.equals(approved) || !(db.equals("oneshop") || db.matches("oneshop_[a-zA-Z0-9_]+_(test|demo)"))) {
            throw new IllegalStateException("Explicit ONESHOP_SEED_DATABASE must match a demo database.");
        }
        String host = databaseHost.toLowerCase(java.util.Locale.ROOT);
        String machine = System.getenv().getOrDefault("COMPUTERNAME", "").toLowerCase(java.util.Locale.ROOT);
        String edition = jdbc.queryForObject("SELECT CAST(SERVERPROPERTY('Edition') AS nvarchar(128))", String.class);
        if (!(List.of("localhost", "127.0.0.1", ".").contains(host) || host.equals(machine))
                || edition == null || !(edition.contains("Developer") || edition.contains("Express"))) {
            throw new IllegalStateException("Demo transaction seeding requires a local Developer/Express SQL Server.");
        }
        Integer unrelated = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM dbo.users WHERE email NOT LIKE '%@example.com')"
                + "+(SELECT COUNT(*) FROM dbo.stores WHERE address NOT LIKE N'%mô phỏng%')", Integer.class);
        if (unrelated == null || unrelated != 0) {
            throw new IllegalStateException("Demo-only users and simulated branch addresses are required before transaction seeding.");
        }
        try {
            transaction.executeWithoutResult(status -> {
                // Lock prevents two explicit invocations from building the same fixture concurrently.
                jdbc.queryForObject("SELECT user_id FROM dbo.users WITH (UPDLOCK,HOLDLOCK) WHERE user_name=N'admin'", Long.class);
                int count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.orders", Integer.class);
                if (count > 0) {
                    int marked = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.orders WHERE receiver_name LIKE N'DEMO-SEED %'", Integer.class);
                    if (count == marked && count >= 14) {
                        log.info("Demo transactions already present ({}); no duplicate records created.", count);
                        return;
                    }
                    throw new IllegalStateException("Transaction seed requires an empty order ledger or its complete marked fixture.");
                }
                for (int n = 0; n < 14; n++) createScenario(n);
                log.info("DEMO_TRANSACTION_SEED_PASS: 14 scenarios created through production services.");
            });
        } finally { SecurityContextHolder.clearContext(); }
    }

    private void createScenario(int n) {
        String email = "khachhang" + (n % 6 + 1) + "@example.com";
        as(email, "CUSTOMER");
        for (Long id : cart.getCart(email).cartItemIds()) cart.removeItem(email, id);
        boolean multi = n == 7 || n == 8 || n == 12;
        boolean pickup = n == 5 || n == 6;
        List<String> skus = multi ? (n == 8 ? List.of("CERAVE-CREAM-177", "BIODERMA-SENSIBIO-250", "NIVEA-CREME-150")
                : List.of("CERAVE-CREAM-177", "BIODERMA-SENSIBIO-250")) : List.of("SIMPLE-REFRESH-150");
        for (String sku : skus) {
            Long id = jdbc.queryForObject("SELECT product_id FROM dbo.products WHERE sku=?", Long.class, sku);
            cart.addItem(email, AddCartItemRequest.forProduct(id, 1));
        }
        var ids = cart.getCart(email).cartItemIds();
        FulfillmentType type = pickup ? FulfillmentType.STORE_PICKUP : FulfillmentType.DELIVERY;
        PaymentMethod method = n >= 7 ? PaymentMethod.ONLINE : pickup ? PaymentMethod.PAY_AT_STORE : PaymentMethod.COD;
        Long storeId = pickup ? jdbc.queryForObject("SELECT store_id FROM dbo.stores WHERE code='OS-THUDUC'", Long.class) : null;
        var preview = checkout.preview(email, ids, type, method, storeId, ADDRESS, null);
        if (preview.plan() == null) throw new IllegalStateException("No allocation for scenario " + n + ": " + preview.planProblem());
        var result = checkout.placeOrder(email, new PlaceOrderRequest(ids, type, method,
                "DEMO-SEED Khách kiểm thử " + (n + 1), "0000000000", ADDRESS, storeId,
                false, preview.plan().signature(), preview.grandTotal()));
        Long orderId = result.orders().getFirst().orderId();
        if (method == PaymentMethod.ONLINE && n != 8 && n != 11) {
            Long paymentId = jdbc.queryForObject("SELECT payment_id FROM dbo.payments WHERE order_id=?", Long.class, orderId);
            payments.simulate(email, orderId, paymentId, n == 10
                    ? PaymentReconciliationService.SimulatedResult.FAILED
                    : PaymentReconciliationService.SimulatedResult.SUCCESS);
        }
        if (n == 9) {
            User customer = users.findByEmail(email).orElseThrow();
            cancellations.cancel(customer, orderId);
        }
        if (n <= 6 || n == 7 || n == 12 || n == 13) {
            var parts = jdbc.queryForList("SELECT fulfillment_id,store_id FROM dbo.order_fulfillments WHERE order_id=? ORDER BY fulfillment_id", orderId);
            for (int p = 0; p < parts.size(); p++) {
                Long fulfillmentId = ((Number) parts.get(p).get("fulfillment_id")).longValue();
                Long supplyingStore = ((Number) parts.get(p).get("store_id")).longValue();
                String staffEmail = jdbc.queryForObject("SELECT u.email FROM dbo.users u JOIN dbo.staff_store_assignments a ON a.user_id=u.user_id WHERE a.store_id=? AND a.status='ACTIVE'", String.class, supplyingStore);
                as(staffEmail, "STAFF");
                int steps = switch (n) {
                    case 1 -> 0; case 2 -> 1; case 3 -> 2; case 4 -> 3; case 5 -> 2;
                    case 12 -> p == 0 ? 4 : 3; default -> 4;
                };
                if (steps > 0) staff.perform(fulfillmentId, FulfillmentAction.PREPARE, null);
                if (pickup) {
                    if (steps >= 2) staff.perform(fulfillmentId, FulfillmentAction.MARK_READY, null);
                    if (steps >= 4) {
                        String code = jdbc.queryForObject("SELECT pickup_code FROM dbo.order_fulfillments WHERE fulfillment_id=?", String.class, fulfillmentId);
                        staff.handover(fulfillmentId, code);
                    }
                } else {
                    if (steps >= 2) staff.perform(fulfillmentId, FulfillmentAction.PACK, null);
                    if (steps >= 3) staff.perform(fulfillmentId, FulfillmentAction.SHIP, "DEMO-SEED-TRACK-" + fulfillmentId);
                    if (steps >= 4) staff.perform(fulfillmentId, FulfillmentAction.COMPLETE_DELIVERY, null);
                }
            }
        }
        if (n == 0 || n == 6 || n == 7) {
            as(email, "CUSTOMER");
            Long itemId = jdbc.queryForObject("SELECT MIN(order_item_id) FROM dbo.order_items WHERE order_id=?", Long.class, orderId);
            reviews.submit(email, new ReviewRequest(null, n == 6 ? 4 : 5,
                    "[Dữ liệu mô phỏng] Đơn kiểm thử đã nhận hàng, bao bì đúng sản phẩm.", itemId), orderId);
            Long reviewId = jdbc.queryForObject("SELECT review_id FROM dbo.reviews WHERE order_item_id=?", Long.class, itemId);
            String staffEmail = jdbc.queryForObject("SELECT u.email FROM dbo.order_items i JOIN dbo.store_products sp ON sp.store_product_id=i.store_product_id JOIN dbo.staff_store_assignments a ON a.store_id=sp.store_id AND a.status='ACTIVE' JOIN dbo.users u ON u.user_id=a.user_id WHERE i.order_item_id=?", String.class, itemId);
            as(staffEmail, "STAFF");
            replies.reply(reviewId, "[Dữ liệu mô phỏng] OneShop xác nhận phản hồi của chi nhánh cung cấp hàng.");
        }
        log.info("Demo scenario {}/14: Order {} created and transitioned through services.", n + 1, orderId);
    }

    private static void as(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
