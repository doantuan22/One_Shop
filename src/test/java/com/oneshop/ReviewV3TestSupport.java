package com.oneshop;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.repository.*;
import com.oneshop.service.*;
import com.oneshop.service.fulfillment.FulfillmentAction;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.SimulatedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Committing SQL Server tests: accept only an explicitly named isolated {@code *_test} database and restore every fixture. */
@SpringBootTest(properties = {"app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true", "oneshop.payment.timeout-worker-enabled=false"})
@ActiveProfiles("dev")
abstract class ReviewV3TestSupport {
    static final String ADMIN = "admin@oneshop.vn";
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewService reviews;
    @Autowired AdminReviewService adminReviews;
    @Autowired ReviewRepository reviewRepository;
    @Autowired ReviewReplyRepository replyRepository;
    @Autowired StaffFulfillmentService staff;
    @Autowired PaymentReconciliationService payment;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItems;
    @Autowired ProductRepository products;
    @Autowired StoreRepository stores;
    @Autowired StoreProductRepository stocks;
    @Autowired PlatformTransactionManager transactionManager;
    FulfillmentFixtures data;
    TransactionTemplate tx;
    private SeedGuardV3 guard;
    private long maxReview, maxReply, maxAssignment;
    private List<Map<String, Object>> assignments, users;
    private Map<String, List<Map<String, Object>>> before;

    @BeforeEach
    void rememberReviews() {
        assertThat(jdbc.queryForObject("select db_name()", String.class)).endsWithIgnoringCase("_test");
        assertThat(count("select count(*) from sys.indexes where object_id=object_id('dbo.reviews') "
                + "and name='UX_reviews_order_item' and is_unique=1 and has_filter=1")).isEqualTo(1);
        before = snapshot();
        maxReview = max("reviews", "review_id");
        maxReply = max("review_replies", "reply_id");
        maxAssignment = max("staff_store_assignments", "assignment_id");
        assignments = jdbc.queryForList("select * from dbo.staff_store_assignments");
        users = jdbc.queryForList("select user_id,status from dbo.users");
        guard = new SeedGuardV3(jdbc);
        guard.remember();
        tx = new TransactionTemplate(transactionManager);
        data = new FulfillmentFixtures(jdbc, carts, checkout, cartRepository, cartItems, products, stores, stocks);
    }

    @AfterEach
    void restoreReviews() {
        if (guard == null) return;
        jdbc.update("delete from dbo.review_replies where reply_id > ? or review_id > ?", maxReply, maxReview);
        jdbc.update("delete from dbo.reviews where review_id > ?", maxReview);
        jdbc.update("delete from dbo.staff_store_assignments where assignment_id > ?", maxAssignment);
        for (var a : assignments) {
            jdbc.update("update dbo.staff_store_assignments set status=?,assigned_at=? where assignment_id=?",
                    a.get("status"), a.get("assigned_at"), a.get("assignment_id"));
        }
        for (var u : users) jdbc.update("update dbo.users set status=? where user_id=?", u.get("status"), u.get("user_id"));
        guard.restore();
        assertThat(snapshot()).as("exact fixture cleanup: all 23 tables' row counts and content checksums")
                .isEqualTo(before);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("select name from sys.tables where is_ms_shipped=0 order by name", String.class)) {
            result.put(table, jdbc.queryForList("select count_big(*) as n, checksum_agg(binary_checksum(*)) as c from dbo.[" + table + "]"));
        }
        return result;
    }

    private long max(String table, String column) {
        return jdbc.queryForObject("select coalesce(max(" + column + "),0) from dbo." + table, Long.class);
    }

    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    long userId(String email) { return jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, email); }

    long order(boolean splitSku) {
        long id = splitSku ? data.onlineSplitSameSku(SERUM, "OS-THUDUC", 2, "OS-GOVAP", 2, 3)
                : data.onlineTwoStores("OS-THUDUC", SERUM, "OS-GOVAP", SHAMPOO);
        customer(ALICE, () -> payment.simulate(ALICE, id, data.attemptId(id), SimulatedResult.SUCCESS));
        return id;
    }

    long partiallyReceived(boolean splitSku) {
        long id = order(splitSku);
        receive(id, "OS-THUDUC");
        ship(id, "OS-GOVAP");
        assertThat(data.orderStatus(id)).isEqualTo("PARTIALLY_COMPLETED");
        return id;
    }

    long item(long order, String store) {
        return jdbc.queryForObject("select oi.order_item_id from dbo.order_items oi join dbo.order_fulfillments f "
                + "on f.fulfillment_id=oi.fulfillment_id join dbo.stores s on s.store_id=f.store_id "
                + "where oi.order_id=? and s.code=?", Long.class, order, store);
    }

    void ship(long order, String store) {
        for (var action : List.of(FulfillmentAction.PREPARE, FulfillmentAction.PACK, FulfillmentAction.SHIP)) {
            data.asStaff(staffFor(store), () -> staff.perform(data.fulfillmentOf(order, store), action, null));
        }
    }

    void receive(long order, String store) {
        long part = data.fulfillmentOf(order, store);
        if ("CONFIRMED".equals(data.fulfillmentStatus(part))) ship(order, store);
        data.asStaff(staffFor(store), () -> staff.perform(part, FulfillmentAction.COMPLETE_DELIVERY, null));
    }

    String staffFor(String store) { return store.equals("OS-THUDUC") ? STAFF_THUDUC : STAFF_GOVAP; }

    <T> T customer(String email, Supplier<T> action) { return data.asRole(email, "ROLE_CUSTOMER", action); }
    <T> T admin(Supplier<T> action) { return data.asRole(ADMIN, "ROLE_ADMIN", action); }

    ReviewResponse submit(long orderItem) { return submit(ALICE, orderItem, 5, "Đã nhận hàng", null); }

    ReviewResponse submit(String email, long item, Integer rating, String text, Long orderId) {
        return customer(email, () -> reviews.submit(email, new ReviewRequest(null, rating, text, item), orderId));
    }

    void reply(String email, long review, String text) {
        data.asStaff(email, () -> { reviews.reply(review, text); return null; });
    }

    void assign(String email, String store) {
        jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status) "
                + "select u.user_id,s.store_id,'ACTIVE' from dbo.users u cross join dbo.stores s "
                + "where u.email=? and s.code=?", email, store);
    }
}
