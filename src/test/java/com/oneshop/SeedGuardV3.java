package com.oneshop;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V3 fixture guard for tests that must really commit (concurrent transactions, fault injection): it
 * remembers the tables a V3 checkout touches and puts them back afterwards, so the test database is left as found.
 *
 * <p>It removes every checkout session, Order, OrderFulfillment (and its history), OrderItem, Payment, status
 * history entry and InventoryMovement created after {@link #remember()}, restores quantity / reserved quantity /
 * price / status of all StoreProducts, restores Product prices, and restores the cart lines (same ids, Product,
 * nullable StoreProduct, quantity).
 *
 * <p>Never runs against a database whose name does not end with {@code _test}: see {@link #isTestDatabase()}.
 */
final class SeedGuardV3 {

    private record StoreProductState(long id, int quantity, int reserved, BigDecimal price, String status,
                                     LocalDateTime updatedAt) {
    }

    private record ProductState(long id, BigDecimal price, String status) {
    }

    private record StoreState(long id, String status, boolean delivery, boolean pickup) {
    }

    private record CartLine(long id, long cartId, long productId, Long storeProductId, int quantity,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private record CartState(long id, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** A seeded ONLINE Order that the payment timeout scan would otherwise cancel (its payment window is long over). */
    private record ShieldedOrder(long id, LocalDateTime createdAt) {
    }

    private final JdbcTemplate jdbc;
    private long maxOrder;
    private long maxCheckout;
    private long maxMovement;
    private long maxHistory;
    private long maxFulfillment;
    private long maxStoreProduct;
    private List<StoreProductState> storeProducts;
    private List<ProductState> products;
    private List<StoreState> stores;
    private List<CartLine> cartLines;
    private List<CartState> carts;
    private List<ShieldedOrder> shielded = List.of();

    SeedGuardV3(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The only databases these tests may write to. */
    boolean isTestDatabase() {
        String name = jdbc.queryForObject("select db_name()", String.class);
        return name != null && name.toLowerCase(java.util.Locale.ROOT).matches("oneshop[a-z0-9_]*_test");
    }

    void remember() {
        requireTestDatabase();
        maxOrder = max("orders", "order_id");
        maxCheckout = max("checkout_sessions", "checkout_id");
        maxMovement = max("inventory_movements", "movement_id");
        maxHistory = max("order_status_history", "history_id");
        maxFulfillment = max("order_fulfillments", "fulfillment_id");
        maxStoreProduct = max("store_products", "store_product_id");
        storeProducts = jdbc.query("select store_product_id, quantity, reserved_quantity, price, status, updated_at "
                        + "from dbo.store_products",
                (rs, i) -> new StoreProductState(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getBigDecimal(4),
                        rs.getString(5), rs.getTimestamp(6).toLocalDateTime()));
        products = jdbc.query("select product_id, price, status from dbo.products",
                (rs, i) -> new ProductState(rs.getLong(1), rs.getBigDecimal(2), rs.getString(3)));
        stores = jdbc.query("select store_id, status, delivery_enabled, pickup_enabled from dbo.stores",
                (rs, i) -> new StoreState(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4)));
        cartLines = jdbc.query("select cart_item_id, cart_id, product_id, store_product_id, quantity, created_at, "
                        + "updated_at from dbo.cart_items",
                (rs, i) -> new CartLine(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getObject(4) == null ? null : rs.getLong(4), rs.getInt(5),
                        rs.getTimestamp(6).toLocalDateTime(), rs.getTimestamp(7).toLocalDateTime()));
        carts = jdbc.query("select cart_id, created_at, updated_at from dbo.carts",
                (rs, i) -> new CartState(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                        rs.getTimestamp(3).toLocalDateTime()));
        // V3-BE-05: the timeout scan is global. Seeded ONLINE Orders still waiting for payment look expired, so they
        // are given a fresh created_at for the duration of a test and put back by restore().
        shielded = jdbc.query("select order_id, created_at from dbo.orders where payment_method = 'ONLINE' "
                        + "and order_status in ('PENDING_PAYMENT', 'NEEDS_ATTENTION') and order_id <= ?",
                (rs, i) -> new ShieldedOrder(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime()), maxOrder);
        for (ShieldedOrder order : shielded) {
            jdbc.update("update dbo.orders set created_at = ? where order_id = ?", LocalDateTime.now().withNano(0),
                    order.id());
        }
    }

    void restore() {
        requireTestDatabase();
        for (ShieldedOrder order : shielded) {
            jdbc.update("update dbo.orders set created_at = ? where order_id = ?", order.createdAt(), order.id());
        }
        jdbc.update("delete from dbo.inventory_movements where movement_id > ?", maxMovement);
        jdbc.update("delete from dbo.order_status_history where history_id > ?", maxHistory);
        jdbc.update("delete from dbo.order_fulfillment_status_history where fulfillment_id > ?", maxFulfillment);
        jdbc.update("delete from dbo.payment_refunds where order_id > ?", maxOrder);
        jdbc.update("delete from dbo.payments where order_id > ?", maxOrder);
        jdbc.update("delete from dbo.order_items where order_id > ?", maxOrder);
        jdbc.update("delete from dbo.order_fulfillments where fulfillment_id > ?", maxFulfillment);
        jdbc.update("delete from dbo.orders where order_id > ?", maxOrder);
        jdbc.update("delete from dbo.checkout_sessions where checkout_id > ?", maxCheckout);
        // StoreProducts a test had to create (BE-04); nothing references them once Orders and movements are gone
        jdbc.update("delete from dbo.cart_items where store_product_id > ?", maxStoreProduct);
        jdbc.update("delete from dbo.store_products where store_product_id > ?", maxStoreProduct);

        for (StoreProductState state : storeProducts) {
            jdbc.update("update dbo.store_products set quantity = ?, reserved_quantity = ?, status = ?, "
                            + "price = ?, updated_at = ? where store_product_id = ?",
                    state.quantity(), state.reserved(), state.status(), state.price(), state.updatedAt(), state.id());
        }
        for (ProductState state : products) {
            jdbc.update("update dbo.products set price = ?, status = ? where product_id = ? "
                    + "and (price <> ? or status <> ?)", state.price(), state.status(), state.id(), state.price(),
                    state.status());
        }

        for (StoreState state : stores) {
            jdbc.update("update dbo.stores set status = ?, delivery_enabled = ?, pickup_enabled = ? "
                            + "where store_id = ? and (status <> ? or delivery_enabled <> ? or pickup_enabled <> ?)",
                    state.status(), state.delivery(), state.pickup(), state.id(), state.status(), state.delivery(),
                    state.pickup());
        }

        Set<Long> remembered = cartLines.stream().map(CartLine::id).collect(Collectors.toSet());
        List<Long> present = jdbc.queryForList("select cart_item_id from dbo.cart_items", Long.class);
        present.stream().filter(id -> !remembered.contains(id))
                .forEach(id -> jdbc.update("delete from dbo.cart_items where cart_item_id = ?", id));
        for (CartLine line : cartLines) {
            if (present.contains(line.id())) {
                jdbc.update("update dbo.cart_items set quantity = ?, created_at = ?, updated_at = ? "
                                + "where cart_item_id = ? and (quantity <> ?)",
                        line.quantity(), line.createdAt(), line.updatedAt(), line.id(), line.quantity());
            } else {
                // one batch = one connection, which IDENTITY_INSERT needs
                jdbc.execute("SET IDENTITY_INSERT dbo.cart_items ON; "
                        + "INSERT INTO dbo.cart_items (cart_item_id, cart_id, product_id, store_product_id, quantity, "
                        + "created_at, updated_at) VALUES (" + line.id() + ", " + line.cartId() + ", "
                        + line.productId() + ", " + (line.storeProductId() == null ? "NULL" : line.storeProductId())
                        + ", " + line.quantity() + ", '" + line.createdAt() + "', '" + line.updatedAt() + "'); "
                        + "SET IDENTITY_INSERT dbo.cart_items OFF;");
            }
        }
        Set<Long> rememberedCarts = carts.stream().map(CartState::id).collect(Collectors.toSet());
        jdbc.queryForList("select cart_id from dbo.carts", Long.class).stream()
                .filter(id -> !rememberedCarts.contains(id))
                .forEach(id -> jdbc.update("delete from dbo.carts where cart_id = ?", id));
        for (CartState state : carts) {
            jdbc.update("update dbo.carts set created_at = ?, updated_at = ? where cart_id = ?",
                    state.createdAt(), state.updatedAt(), state.id());
        }
    }

    private void requireTestDatabase() {
        if (!isTestDatabase()) {
            throw new IllegalStateException("Fixture writes require an isolated oneshop*_test database.");
        }
    }

    /** Counts of the tables a checkout writes to, for "nothing was created" assertions. */
    Map<String, Long> counts() {
        return Map.of(
                "checkout_sessions", count("checkout_sessions"),
                "orders", count("orders"),
                "order_fulfillments", count("order_fulfillments"),
                "order_fulfillment_status_history", count("order_fulfillment_status_history"),
                "order_items", count("order_items"),
                "inventory_movements", count("inventory_movements"),
                "order_status_history", count("order_status_history"),
                "payments", count("payments"),
                "cart_items", count("cart_items"));
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from dbo." + table, Long.class);
        return value == null ? 0 : value;
    }

    private long max(String table, String column) {
        Long value = jdbc.queryForObject("select coalesce(max(" + column + "), 0) from dbo." + table, Long.class);
        return value == null ? 0 : value;
    }
}
