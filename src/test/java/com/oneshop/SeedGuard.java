package com.oneshop;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * For tests that must really commit (concurrent transactions, HTTP requests): remembers the state of the tables a
 * checkout touches and puts it back afterwards, so the development database is left as it was found.
 *
 * <p>{@link #restore()} removes every checkout, order, order item, payment, status history entry and inventory
 * movement created after {@link #remember()}, puts stock / price / status of all StoreProducts back, and restores the
 * cart lines (same ids, StoreProducts and quantities).
 */
final class SeedGuard {

    private record StoreProductState(long id, int quantity, BigDecimal price, String status, LocalDateTime updatedAt) {
    }

    private record CartLine(long id, long cartId, long storeProductId, int quantity, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private record CartState(long id, LocalDateTime createdAt, LocalDateTime updatedAt) { }

    private final JdbcTemplate jdbc;
    private long maxOrder;
    private long maxCheckout;
    private long maxMovement;
    private long maxHistory;
    private List<StoreProductState> storeProducts;
    private List<CartLine> cartLines;
    private List<CartState> carts;

    SeedGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void remember() {
        maxOrder = max("orders", "order_id");
        maxCheckout = max("checkout_sessions", "checkout_id");
        maxMovement = max("inventory_movements", "movement_id");
        maxHistory = max("order_status_history", "history_id");
        storeProducts = jdbc.query("select store_product_id, quantity, price, status, updated_at from dbo.store_products",
                (rs, i) -> new StoreProductState(rs.getLong(1), rs.getInt(2), rs.getBigDecimal(3), rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime()));
        cartLines = jdbc.query("select cart_item_id, cart_id, store_product_id, quantity, created_at, updated_at from dbo.cart_items",
                (rs, i) -> new CartLine(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4),
                        rs.getTimestamp(5).toLocalDateTime(), rs.getTimestamp(6).toLocalDateTime()));
        carts = jdbc.query("select cart_id, created_at, updated_at from dbo.carts",
                (rs, i) -> new CartState(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(), rs.getTimestamp(3).toLocalDateTime()));
    }

    void restore() {
        jdbc.update("delete from dbo.inventory_movements where movement_id > ?", maxMovement);
        jdbc.update("delete from dbo.order_status_history where history_id > ?", maxHistory);
        jdbc.update("delete from dbo.payments where order_id > ?", maxOrder);
        jdbc.update("delete from dbo.order_items where order_id > ?", maxOrder);
        jdbc.update("delete from dbo.orders where order_id > ?", maxOrder);
        jdbc.update("delete from dbo.checkout_sessions where checkout_id > ?", maxCheckout);

        for (StoreProductState state : storeProducts) {
            jdbc.update("update dbo.store_products set quantity = ?, price = ?, status = ?, updated_at = ? "
                            + "where store_product_id = ? and (quantity <> ? or price <> ? or status <> ? or updated_at <> ?)",
                    state.quantity(), state.price(), state.status(), state.updatedAt(), state.id(), state.quantity(),
                    state.price(), state.status(), state.updatedAt());
        }

        Set<Long> remembered = cartLines.stream().map(CartLine::id).collect(Collectors.toSet());
        List<Long> present = jdbc.queryForList("select cart_item_id from dbo.cart_items", Long.class);
        present.stream().filter(id -> !remembered.contains(id))
                .forEach(id -> jdbc.update("delete from dbo.cart_items where cart_item_id = ?", id));
        for (CartLine line : cartLines) {
            if (present.contains(line.id())) {
                jdbc.update("update dbo.cart_items set quantity = ?, created_at = ?, updated_at = ? where cart_item_id = ?",
                        line.quantity(), line.createdAt(), line.updatedAt(), line.id());
            } else {
                // one batch = one connection, which IDENTITY_INSERT needs
                jdbc.execute("SET IDENTITY_INSERT dbo.cart_items ON; "
                        + "INSERT INTO dbo.cart_items (cart_item_id, cart_id, store_product_id, quantity, created_at, updated_at) VALUES ("
                        + line.id() + ", " + line.cartId() + ", " + line.storeProductId() + ", " + line.quantity() + ", '"
                        + line.createdAt() + "', '" + line.updatedAt() + "'); "
                        + "SET IDENTITY_INSERT dbo.cart_items OFF;");
            }
        }
        Set<Long> rememberedCarts = carts.stream().map(CartState::id).collect(Collectors.toSet());
        jdbc.queryForList("select cart_id from dbo.carts", Long.class).stream().filter(id -> !rememberedCarts.contains(id))
                .forEach(id -> jdbc.update("delete from dbo.carts where cart_id = ?", id));
        for (CartState state : carts) {
            jdbc.update("update dbo.carts set created_at = ?, updated_at = ? where cart_id = ?",
                    state.createdAt(), state.updatedAt(), state.id());
        }
    }

    /** Counts of the tables a checkout writes to, for "nothing was created" assertions. */
    Map<String, Long> counts() {
        return Map.of(
                "checkout_sessions", count("checkout_sessions"),
                "orders", count("orders"),
                "order_items", count("order_items"),
                "inventory_movements", count("inventory_movements"),
                "order_status_history", count("order_status_history"),
                "payments", count("payments"));
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
