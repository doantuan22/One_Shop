package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Data builders for the V3-BE-04 database tests. Everything is created through the real checkout (never by inserting
 * rows), then only the facts a later phase would produce (an ONLINE payment result) are written with SQL. Callers use
 * {@link SeedGuardV3} to put the test database back.
 */
final class FulfillmentFixtures {

    static final String ALICE = "khachhang1@example.com";
    static final String BOB = "khachhang2@example.com";
    static final String STAFF_THUDUC = "staff.thuduc@oneshop.vn";
    static final String STAFF_GOVAP = "staff.govap@oneshop.vn";
    static final String STAFF_QUAN7 = "staff.quan7@oneshop.vn";
    static final String STAFF_QUAN10 = "staff.quan10@oneshop.vn";
    static final String ADDRESS = "12 Đường Thử Nghiệm, Phường 1, Quận 1";

    static final String SERUM = "COCOON-SERUM-30";
    static final String SHAMPOO = "COCOON-SHAM-310";
    static final String CHEEK = "BBIA-CHEEK-06";

    final JdbcTemplate jdbc;
    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final StoreProductRepository storeProducts;

    FulfillmentFixtures(JdbcTemplate jdbc, CartService cartService, CheckoutService checkoutService,
                        CartRepository carts, CartItemRepository cartItems, ProductRepository products,
                        StoreRepository stores, StoreProductRepository storeProducts) {
        this.jdbc = jdbc;
        this.cartService = cartService;
        this.checkoutService = checkoutService;
        this.carts = carts;
        this.cartItems = cartItems;
        this.products = products;
        this.stores = stores;
        this.storeProducts = storeProducts;
    }

    record Fulfillment(long id, long storeId, String status) {
    }

    // ---------------------------------------------------------------- identity

    <T> T asStaff(String email, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    void asStaff(String email, Runnable action) {
        asStaff(email, () -> {
            action.run();
            return null;
        });
    }

    <T> T asRole(String email, String role, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null,
                List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    // ---------------------------------------------------------------- catalog

    long productId(String sku) {
        return products.findBySku(sku).orElseThrow().getId();
    }

    long storeId(String code) {
        return stores.findByCode(code).orElseThrow().getId();
    }

    long storeProduct(String sku, String storeCode) {
        return storeProducts.findByStoreIdAndProductId(storeId(storeCode), productId(sku)).orElseThrow().getId();
    }

    /** Makes sure the StoreProduct exists (the seed does not stock every SKU everywhere; the guard removes it later). */
    private void ensureStoreProduct(String sku, String storeCode) {
        long store = storeId(storeCode);
        long product = productId(sku);
        if (storeProducts.findByStoreIdAndProductId(store, product).isEmpty()) {
            jdbc.update("insert into dbo.store_products (store_id, product_id, price, quantity, reserved_quantity, "
                    + "status) select ?, product_id, price, 0, 0, 'ACTIVE' from dbo.products where product_id = ?",
                    store, product);
        }
    }

    /** Sets the quantity of {@code sku} at one Store, leaving every other Store untouched. */
    void stockAt(String sku, String storeCode, int quantity) {
        ensureStoreProduct(sku, storeCode);
        jdbc.update("update dbo.store_products set quantity = ?, reserved_quantity = 0, status = 'ACTIVE' "
                + "where store_product_id = ?", quantity, storeProduct(sku, storeCode));
    }

    int quantityAt(String sku, String storeCode) {
        return jdbc.queryForObject("select quantity from dbo.store_products where store_product_id = ?",
                Integer.class, storeProduct(sku, storeCode));
    }

    /** Only {@code storeCode} sells {@code sku} (quantity {@code quantity}); every other Store has none of it. */
    void isolate(String sku, String storeCode, int quantity) {
        ensureStoreProduct(sku, storeCode);
        long only = storeProduct(sku, storeCode);
        jdbc.update("update dbo.store_products set quantity = 0, reserved_quantity = 0 "
                + "where product_id = ? and store_product_id <> ?", productId(sku), only);
        jdbc.update("update dbo.store_products set quantity = ?, reserved_quantity = 0, status = 'ACTIVE' "
                + "where store_product_id = ?", quantity, only);
    }

    // ---------------------------------------------------------------- orders

    private long addToCart(String email, String sku, int quantity) {
        carts.findByUserEmail(email).ifPresent(c -> cartItems.deleteAll(cartItems.findByCartId(c.getId())));
        cartService.addItem(email, AddCartItemRequest.forProduct(productId(sku), quantity));
        return cartId(email, sku);
    }

    private long cartId(String email, String sku) {
        return carts.findByUserEmail(email).map(c -> cartItems.findByCartIdOrderByIdAsc(c.getId())).orElseThrow()
                .stream().filter(line -> line.getProduct().getSku().equals(sku)).findFirst().orElseThrow().getId();
    }

    private long addMore(String email, String sku, int quantity) {
        cartService.addItem(email, AddCartItemRequest.forProduct(productId(sku), quantity));
        return cartId(email, sku);
    }

    /** One COD DELIVERY Order of one Store with one line. Returns the Order id. */
    long codDelivery(String storeCode, String sku, int quantity) {
        isolate(sku, storeCode, quantity + 20);
        long line = addToCart(ALICE, sku, quantity);
        return place(ALICE, new PlaceOrderRequest(List.of(line), FulfillmentType.DELIVERY, PaymentMethod.COD,
                "Người Nhận", "0912345678", ADDRESS, null, false, null, null));
    }

    /** One STORE_PICKUP Order paid at the Store. */
    long payAtStorePickup(String storeCode, String sku, int quantity) {
        isolate(sku, storeCode, quantity + 20);
        long line = addToCart(ALICE, sku, quantity);
        return place(ALICE, new PlaceOrderRequest(List.of(line), FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE, "Người Nhận", "0912345678", null, storeId(storeCode), false, null, null));
    }

    /** One ONLINE STORE_PICKUP Order (PENDING_PAYMENT until {@link #markPaid}). */
    long onlinePickup(String storeCode, String sku, int quantity) {
        isolate(sku, storeCode, quantity + 20);
        long line = addToCart(ALICE, sku, quantity);
        return place(ALICE, new PlaceOrderRequest(List.of(line), FulfillmentType.STORE_PICKUP, PaymentMethod.ONLINE,
                "Người Nhận", "0912345678", null, storeId(storeCode), false, null, null));
    }

    /** One ONLINE DELIVERY Order whose two lines come from two Stores (PENDING_PAYMENT until {@link #markPaid}). */
    long onlineTwoStores(String storeA, String skuA, String storeB, String skuB) {
        isolate(skuA, storeA, 20);
        isolate(skuB, storeB, 20);
        long first = addToCart(ALICE, skuA, 1);
        long second = addMore(ALICE, skuB, 1);
        return place(ALICE, new PlaceOrderRequest(List.of(first, second), FulfillmentType.DELIVERY,
                PaymentMethod.ONLINE, "Người Nhận", "0912345678", ADDRESS, null, false, null, null));
    }

    private long place(String email, PlaceOrderRequest request) {
        CheckoutResultResponse result = checkoutService.placeOrder(email, request);
        if (result.orders().size() != 1) {
            throw new IllegalStateException("expected one Order, got " + result.orders().size());
        }
        return result.orders().get(0).orderId();
    }

    /**
     * One ONLINE DELIVERY Order whose single SKU is split between two Stores because neither holds the whole
     * quantity (PENDING_PAYMENT until paid).
     */
    long onlineSplitSameSku(String sku, String storeA, int qtyA, String storeB, int qtyB, int requested) {
        isolate(sku, storeA, qtyA);
        stockAt(sku, storeB, qtyB);
        long line = addToCart(ALICE, sku, requested);
        return place(ALICE, new PlaceOrderRequest(List.of(line), FulfillmentType.DELIVERY, PaymentMethod.ONLINE,
                "Người Nhận", "0912345678", ADDRESS, null, false, null, null));
    }

    /** Moves the creation time of an Order back, which is how a payment deadline is brought closer in tests. */
    void ageOrder(long orderId, long minutes) {
        jdbc.update("update dbo.orders set created_at = ? where order_id = ?",
                java.time.LocalDateTime.now().minusMinutes(minutes).withNano(0), orderId);
    }

    /** Id of the (single) payment attempt of an Order. */
    long attemptId(long orderId) {
        return jdbc.queryForObject("select min(payment_id) from dbo.payments where order_id = ?", Long.class, orderId);
    }

    java.util.Map<String, Object> payment(long paymentId) {
        return jdbc.queryForMap("select * from dbo.payments where payment_id = ?", paymentId);
    }

    List<java.util.Map<String, Object>> payments(long orderId) {
        return jdbc.queryForList("select * from dbo.payments where order_id = ? order by payment_id", orderId);
    }

    int cancelRestores(long orderId) {
        return jdbc.queryForObject("select count(*) from dbo.inventory_movements where reference_order_id = ? "
                + "and type = 'CANCEL_ORDER'", Integer.class, orderId);
    }

    /** Writes what a verified ONLINE payment result (BE-05) would write: Payment SUCCESS, Order PAID + CONFIRMED. */
    void markPaid(long orderId) {
        jdbc.update("update dbo.payments set status = 'SUCCESS', paid_at = SYSDATETIME() where order_id = ?", orderId);
        jdbc.update("update dbo.orders set payment_status = 'PAID', order_status = 'CONFIRMED' where order_id = ?",
                orderId);
    }

    void setOrderStatus(long orderId, String status) {
        jdbc.update("update dbo.orders set order_status = ? where order_id = ?", status, orderId);
    }

    // ---------------------------------------------------------------- reads

    List<Fulfillment> fulfillments(long orderId) {
        return jdbc.query("select fulfillment_id, store_id, status from dbo.order_fulfillments where order_id = ? "
                        + "order by fulfillment_id", (rs, i) -> new Fulfillment(rs.getLong(1), rs.getLong(2),
                rs.getString(3)), orderId);
    }

    long fulfillmentOf(long orderId, String storeCode) {
        long store = storeId(storeCode);
        return fulfillments(orderId).stream().filter(f -> f.storeId() == store).findFirst().orElseThrow().id();
    }

    long onlyFulfillment(long orderId) {
        List<Fulfillment> all = fulfillments(orderId);
        if (all.size() != 1) {
            throw new IllegalStateException("expected one Fulfillment, got " + all.size());
        }
        return all.get(0).id();
    }

    String fulfillmentStatus(long fulfillmentId) {
        return jdbc.queryForObject("select status from dbo.order_fulfillments where fulfillment_id = ?",
                String.class, fulfillmentId);
    }

    String orderStatus(long orderId) {
        return jdbc.queryForObject("select order_status from dbo.orders where order_id = ?", String.class, orderId);
    }

    String paymentStatus(long orderId) {
        return jdbc.queryForObject("select payment_status from dbo.orders where order_id = ?", String.class, orderId);
    }

    String pickupCode(long fulfillmentId) {
        return jdbc.queryForObject("select pickup_code from dbo.order_fulfillments where fulfillment_id = ?",
                String.class, fulfillmentId);
    }

    int successfulPayments(long orderId) {
        return jdbc.queryForObject("select count(*) from dbo.payments where order_id = ? and status = 'SUCCESS'",
                Integer.class, orderId);
    }

    int fulfillmentHistory(long fulfillmentId) {
        return jdbc.queryForObject("select count(*) from dbo.order_fulfillment_status_history "
                + "where fulfillment_id = ?", Integer.class, fulfillmentId);
    }

    int orderHistory(long orderId) {
        return jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id = ?",
                Integer.class, orderId);
    }

    /** quantity, reserved_quantity of every StoreProduct, and the number of inventory movements. */
    Map<Long, List<Integer>> stockSnapshot() {
        Map<Long, List<Integer>> snapshot = new java.util.LinkedHashMap<>();
        jdbc.query("select store_product_id, quantity, reserved_quantity from dbo.store_products "
                + "order by store_product_id", rs -> {
            snapshot.put(rs.getLong(1), new ArrayList<>(List.of(rs.getInt(2), rs.getInt(3))));
        });
        return snapshot;
    }

    long movements() {
        return jdbc.queryForObject("select count(*) from dbo.inventory_movements", Long.class);
    }
}
