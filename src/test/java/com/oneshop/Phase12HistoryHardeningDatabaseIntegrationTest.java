package com.oneshop;

import com.oneshop.dto.request.*;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.*;
import com.oneshop.service.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static com.oneshop.Phase9IntegrationScenario.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unconditional acceptance tests: production HTTP/services/JPA and the configured SQL Server.
 * Repository spies only inject persistence faults or schedule a deterministic stale-read race. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
class Phase12HistoryHardeningDatabaseIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerOrderService customers;
    @Autowired PaymentService payments;
    @Autowired DeliveryFulfillmentService delivery;
    @Autowired PickupFulfillmentService pickup;
    @Autowired InventoryService inventory;
    @Autowired StoreProductService catalogStock;
    @Autowired OrderService stateMachine;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean InventoryMovementRepository movements;
    @MockitoSpyBean OrderStatusHistoryRepository histories;
    @MockitoSpyBean PaymentRepository paymentRows;
    @MockitoSpyBean StoreProductRepository stockRows;
    @PersistenceContext EntityManager em;
    @LocalServerPort int port;
    private static final String ADMIN = "admin@oneshop.vn";
    private Phase9IntegrationScenario scenario() { return new Phase9IntegrationScenario(jdbc, "http://localhost:" + port); }
    private <T> T as(String email, String role, Supplier<T> work) {
        var prior = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        try { return work.get(); } finally { SecurityContextHolder.setContext(prior); }
    }
    private void cancel(long id) { as(CUSTOMER, "CUSTOMER", () -> { customers.cancelOrder(CUSTOMER, id); return null; }); }
    private long sp(long id) { return jdbc.queryForObject("select store_product_id from dbo.order_items where order_id=?", Long.class, id); }
    private int quantity(long sp) { return jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?", Integer.class, sp); }
    private List<Map<String,Object>> restores(long id) { return jdbc.queryForList("select * from dbo.inventory_movements where reference_order_id=? and type='CANCEL_ORDER' order by movement_id", id); }
    private void resetFaults() { reset(movements, histories, paymentRows, stockRows); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(12, TimeUnit.SECONDS)) throw new AssertionError("SQL race barrier timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (var job : List.of(first, second)) results.add(pool.submit(() -> {
                ready.countDown(); await(go);
                try { return job.call(); } catch (Exception e) { return e; }
            }));
            await(ready); go.countDown();
            return List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
        } finally { go.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue(); }
    }
    private void assertCancelled(Phase9IntegrationScenario s, long id, String old, int before) {
        assertThat(s.order(id)).containsEntry("order_status", "CANCELLED").containsEntry("payment_status", "UNPAID");
        assertThat(quantity(sp(id))).isEqualTo(before + 1);
        assertThat(restores(id)).singleElement().satisfies(row -> {
            assertThat(number(row.get("store_product_id"))).isEqualTo(sp(id));
            assertThat(number(row.get("quantity_before"))).isEqualTo(before);
            assertThat(number(row.get("quantity_after"))).isEqualTo(before + 1);
            assertThat(number(row.get("quantity_change"))).isEqualTo(1);
            assertThat(row.get("staff_id")).isNull();
            assertThat(row.get("note").toString()).contains("khách hủy");
        });
        assertThat(s.history(id)).hasSize(2);
        var history = s.history(id).get(1);
        assertThat(history).containsEntry("old_status", old).containsEntry("new_status", "CANCELLED");
        assertThat(number(history.get("changed_by_user_id"))).isEqualTo(jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, CUSTOMER));
        assertThat(history.get("changed_at")).isNotNull();
        assertThat(history.get("note").toString()).contains("Khách hàng");
    }

    @ParameterizedTest @ValueSource(strings = {"COD", "PAY_AT_STORE", "ONLINE", "ONLINE_ATTEMPT", "DELIVERY_ONLINE", "LEGACY_COD", "LEGACY_PAY_AT_STORE"})
    void customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String method) throws Exception {
        var s = scenario();
        try (var ignored = s.cleanup()) {
            var f = s.mixed();
            long id = method.equals("COD") || method.equals("LEGACY_COD") || method.equals("DELIVERY_ONLINE") ? f.a()
                    : method.equals("PAY_AT_STORE") || method.equals("LEGACY_PAY_AT_STORE") ? f.c() : f.b();
            if (method.equals("DELIVERY_ONLINE")) id=singleDeliveryOnlineOrder();
            if (method.equals("ONLINE_ATTEMPT")) payments.createOnlinePaymentAttempt(CUSTOMER, id);
            if (method.startsWith("LEGACY_")) jdbc.update("insert into dbo.payments(order_id,method,amount,status) select order_id,payment_method,total_amount,'PENDING' from dbo.orders where order_id=?",id);
            var items = jdbc.queryForList("select * from dbo.order_items where order_id=?", id);
            Object total = s.order(id).get("total_amount"); String old = s.order(id).get("order_status").toString(); int before = quantity(sp(id));
            assertThat(s.get("/orders/" + id, s.account(CUSTOMER)).body()).contains("/orders/" + id + "/cancel");
            long target=id;
            s.isolated(f, id, false, () -> assertThat(s.post(s.account(CUSTOMER), "/orders/" + target + "/cancel",
                "price=1&total_amount=1&quantity=900&storeId=5&userId=6&status=COMPLETED").statusCode()).isEqualTo(302));
            assertCancelled(s, id, old, before);
            assertThat(s.order(id).get("total_amount")).isEqualTo(total);
            assertThat(jdbc.queryForList("select * from dbo.order_items where order_id=?", id)).isEqualTo(items);
            if (method.equals("ONLINE_ATTEMPT") || method.startsWith("LEGACY_")) {
                assertThat(s.receipts(id)).singleElement().satisfies(row -> {
                    assertThat(row).containsEntry("status", "FAILED");
                    assertThat(row.get("paid_at")).isNull();
                    assertThat(row.get("transaction_code")).isNotNull();
                });
                long payment = number(s.receipts(id).get(0).get("payment_id"));
                var stable = s.snapshot();
                assertThatThrownBy(() -> payments.markOnlinePaymentSuccess(CUSTOMER, target, payment)).isInstanceOf(BadRequestException.class);
                assertThatThrownBy(() -> payments.markOnlinePaymentFailed(CUSTOMER, target, payment)).isInstanceOf(BadRequestException.class);
                assertThat(s.snapshot()).isEqualTo(stable);
            } else assertThat(s.receipts(id)).isEmpty();
            var stable = s.snapshot(); cancel(id); cancel(id);
            assertThat(s.snapshot()).as("Repeated cancellation writes nothing, including timestamps").isEqualTo(stable);
            assertThat(s.get("/orders/" + id, s.account(CUSTOMER)).body()).doesNotContain("/orders/" + id + "/cancel");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "PACKED", "SHIPPING", "COMPLETED", "READY_FOR_PICKUP", "PAID_ONLINE"})
    void cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String stage) throws Exception {
        var s = scenario();
        try (var ignored = s.cleanup()) {
            var f = s.mixed(); long id = f.a();
            if (stage.equals("PAID_ONLINE")) { id = f.b(); long p = payments.createOnlinePaymentAttempt(CUSTOMER, id).paymentId(); payments.markOnlinePaymentSuccess(CUSTOMER, id, p); }
            else if (stage.equals("READY_FOR_PICKUP")) { id=f.c(); pickup.startPreparingPickup(STAFF.get(4L),id); pickup.markReadyForPickup(STAFF.get(4L),id); }
            else {
                delivery.startPreparingDelivery(STAFF.get(1L), id);
                if (!stage.equals("PREPARING")) delivery.markDeliveryPacked(STAFF.get(1L), id);
                if (stage.equals("SHIPPING") || stage.equals("COMPLETED")) delivery.markDeliveryShipping(STAFF.get(1L), id);
                if (stage.equals("COMPLETED")) delivery.completeDelivery(STAFF.get(1L), id);
            }
            var before = s.snapshot();
            assertThat(s.post(s.account(CUSTOMER), "/orders/" + id + "/cancel", "").statusCode()).isEqualTo(400);
            assertThat(s.snapshot()).isEqualTo(before);
            assertThat(s.get("/orders/" + id, s.account(CUSTOMER)).body()).doesNotContain("/orders/" + id + "/cancel");
        }
    }

    @Test void cancellationRequiresOwnedActiveCustomerAndCsrfEvenForBearerAndEncodedPaths() throws Exception {
        var s = scenario();
        try (var ignored = s.cleanup()) {
            var f=s.mixed(); var before=s.snapshot(); String path="/orders/"+f.a()+"/cancel";
            assertThat(s.post(s.account(OTHER_CUSTOMER),path,"userId=1").statusCode()).isEqualTo(404);
            for(String email:List.of(ADMIN,STAFF.get(1L))) assertThat(s.post(s.account(email),path,"").statusCode()).isEqualTo(403);
            assertThat(s.post("",path,"").statusCode()).isEqualTo(302);
            assertThat(s.post(s.account(CUSTOMER),"/orders/9223372036854775807/cancel","").statusCode()).isEqualTo(404);
            for(String p:List.of(path,"/orders/"+f.a()+"/%63ancel","/%6frders/"+f.a()+"/cancel")) {
                assertThat(s.raw(p,"","Cookie",s.account(CUSTOMER)).statusCode()).isEqualTo(403);
                assertThat(s.raw(p,"","Authorization","Bearer "+s.account(CUSTOMER).substring("ONESHOP_TOKEN=".length())).statusCode()).isEqualTo(403);
            }
            assertThatThrownBy(() -> as(OTHER_CUSTOMER,"CUSTOMER",()->{customers.cancelOrder(CUSTOMER,f.a()); return null;})).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> as(ADMIN,"ADMIN",()->{customers.cancelOrder(CUSTOMER,f.a()); return null;})).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThat(s.snapshot()).isEqualTo(before);
        }
    }

    @Test void inactiveCustomerCannotCancelUsingAnAlreadyIssuedJwt() throws Exception {
        var s=scenario();
        try(var ignored=s.cleanup()) {
            var f=s.mixed(); String token=s.account(CUSTOMER);
            var user=jdbc.queryForMap("select * from dbo.users where email=?",CUSTOMER); var before=s.snapshot();
            try {
                jdbc.update("update dbo.users set status='INACTIVE' where email=?",CUSTOMER);
                assertThat(s.post(token,"/orders/"+f.a()+"/cancel","").statusCode()).isIn(302,401,403);
                assertThatThrownBy(()->cancel(f.a())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                assertThat(s.snapshot()).isEqualTo(before);
            } finally {jdbc.update("update dbo.users set status=?,updated_at=? where email=?",user.get("status"),user.get("updated_at"),CUSTOMER);}
        }
    }

    static final class Abort extends RuntimeException {}
    @ParameterizedTest @ValueSource(strings={"PAYMENT_FLUSH","FIRST_RESTORE","HISTORY_FLUSH","OUTER_TRANSACTION"})
    void cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry(String failure) throws Exception {
        var s=scenario(); var guard=s.cleanup();
        try {
            var f=s.mixed(); long id=f.b(); payments.createOnlinePaymentAttempt(CUSTOMER,id);
            var before=s.snapshot();
            switch(failure) {
                case "PAYMENT_FLUSH" -> doAnswer(inv->{em.flush(); throw new Abort();}).when(paymentRows).flush();
                case "FIRST_RESTORE" -> doAnswer(inv->{em.persist(inv.getArgument(0)); em.flush(); throw new Abort();}).when(movements).save(any(InventoryMovement.class));
                case "HISTORY_FLUSH" -> doAnswer(inv->{em.persist(inv.getArgument(0)); em.flush(); assertThat(s.order(id).get("order_status")).isEqualTo("CANCELLED"); throw new Abort();}).when(histories).save(any(OrderStatusHistory.class));
                default -> {}
            }
            assertThatThrownBy(()-> {
                if(failure.equals("OUTER_TRANSACTION")) new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
                    cancel(id); em.flush();
                    assertThat(s.order(id)).containsEntry("order_status","CANCELLED");
                    assertThat(restores(id)).hasSize(1); throw new Abort();
                });
                else cancel(id);
            }).isInstanceOf(Abort.class);
            assertThat(s.snapshot()).as("Exact DB rows/ids/timestamps after rollback").isEqualTo(before);
            resetFaults(); cancel(id); assertThat(restores(id)).hasSize(1); assertThat(s.history(id)).hasSize(2);
        } finally { resetFaults(); guard.close(); }
    }

    @Test void simultaneousDuplicateHttpCancellationsRestoreExactlyOnce() throws Exception {
        var s=scenario();
        try(var ignored=s.cleanup()) {
            var f=s.mixed(); String cookie=s.account(CUSTOMER); int before=quantity(sp(f.a()));
            var results=race(()->s.post(cookie,"/orders/"+f.a()+"/cancel","").statusCode(),()->s.post(cookie,"/orders/"+f.a()+"/cancel","").statusCode());
            assertThat(results).containsExactly(302,302); assertCancelled(s,f.a(),"CONFIRMED",before);
        }
    }

    @ParameterizedTest @ValueSource(strings={"PAYMENT_SUCCESS","PAYMENT_FAILURE","STAFF_PREPARE","STOCK_ADJUST"})
    void cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit(String rival) throws Exception {
        var s=scenario();
        try(var ignored=s.cleanup()) {
            var f=s.mixed(); long id=rival.startsWith("PAYMENT")?f.b():f.a();
            long p=rival.startsWith("PAYMENT")?payments.createOnlinePaymentAttempt(CUSTOMER,id).paymentId():0;
            int initial=quantity(sp(id)); long stock=sp(id);
            long max=jdbc.queryForObject("select coalesce(max(movement_id),0) from dbo.inventory_movements",Long.class);
            var results=race(()->{cancel(id);return "CANCEL";},()-> {
                switch(rival){
                    case "PAYMENT_SUCCESS" -> payments.markOnlinePaymentSuccess(CUSTOMER,id,p);
                    case "PAYMENT_FAILURE" -> payments.markOnlinePaymentFailed(CUSTOMER,id,p);
                    case "STAFF_PREPARE" -> delivery.startPreparingDelivery(STAFF.get(1L),id);
                    case "STOCK_ADJUST" -> inventory.adjustStock(stock,initial+7,ADMIN,"Phase12 racing count");
                } return "RIVAL";
            });
            assertThat(results).allMatch(v->v instanceof String || v instanceof BadRequestException);
            String status=s.order(id).get("order_status").toString();
            assertThat(s.history(id)).hasSize(2);
            assertThat(restores(id)).hasSize(status.equals("CANCELLED")?1:0);
            if(!rival.equals("STOCK_ADJUST")) assertThat(quantity(stock)).isEqualTo(initial+(status.equals("CANCELLED")?1:0));
            else {
                assertThat(results).containsExactly("CANCEL","RIVAL");
                var ledger=jdbc.queryForList("select * from dbo.inventory_movements where store_product_id=? and movement_id>? order by movement_id",stock,max);
                assertThat(ledger).hasSize(2); int previous=initial;
                for(var row:ledger){assertThat(number(row.get("quantity_before"))).isEqualTo(previous);previous=(int)number(row.get("quantity_after"));}
                assertThat(quantity(stock)).isEqualTo(previous);
            }
            if(rival.equals("PAYMENT_SUCCESS")) {
                assertThat(s.order(id).get("payment_status")).isEqualTo(status.equals("CANCELLED")?"UNPAID":"PAID");
                assertThat(s.receipts(id).get(0).get("status")).isEqualTo(status.equals("CANCELLED")?"FAILED":"SUCCESS");
            }
        }
    }

    @Test void cancellationAfterStoreIsInactiveStillRestoresTheOriginalStockAndPreservesHistory() throws Exception {
        var s=scenario();
        try(var ignored=s.cleanup()) {
            var f=s.mixed();var store=jdbc.queryForMap("select * from dbo.stores where store_id=1"); int before=quantity(sp(f.a()));
            try {
                jdbc.update("update dbo.stores set status='INACTIVE' where store_id=1");
                cancel(f.a()); assertCancelled(s,f.a(),"CONFIRMED",before);
                assertThat(s.get("/orders/"+f.a(),s.account(CUSTOMER)).statusCode()).isEqualTo(200);
                assertThat(s.get("/admin/orders/"+f.a(),s.account(ADMIN)).statusCode()).isEqualTo(200);
            } finally {jdbc.update("update dbo.stores set status=?,updated_at=? where store_id=1",store.get("status"),store.get("updated_at"));}
        }
    }

    @Test void inconsistentSuccessfulReceiptBlocksCustomerCancellationAtomically() throws Exception {
        var s=scenario();
        try(var ignored=s.cleanup()) {
            var f=s.mixed();long p=payments.createOnlinePaymentAttempt(CUSTOMER,f.b()).paymentId();
            jdbc.update("update dbo.payments set status='SUCCESS',paid_at=sysdatetime(),transaction_code=? where payment_id=?","PH12-"+UUID.randomUUID(),p);
            var before=s.snapshot();
            assertThatThrownBy(()->cancel(f.b())).isInstanceOf(BadRequestException.class);
            assertThat(s.snapshot()).isEqualTo(before);
        }
    }

    @Test void stockOverflowRejectsCancellationAndRollsBackClosedPendingAttempt() throws Exception {
        var s=scenario();
        try(var ignored=s.cleanup()) {
            var f=s.mixed(); payments.createOnlinePaymentAttempt(CUSTOMER,f.b());
            jdbc.update("update dbo.store_products set quantity=2147483647 where store_product_id=?",sp(f.b()));
            var before=s.snapshot();
            assertThatThrownBy(()->cancel(f.b())).isInstanceOf(BadRequestException.class);
            assertThat(s.snapshot()).isEqualTo(before);
        }
    }

    @Test void internalCancellationPrimitivesRequireAnExistingTransaction() {
        assertThatThrownBy(()->stateMachine.cancelByCustomer(null,null)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->payments.closePendingForCustomerCancellation(null)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->inventory.restoreForCancelledOrder(null,"reason")).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }


    private long singleDeliveryOnlineOrder() {
        long stock=jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id where sp.store_id=1 and p.sku='COCOON-SERUM-30'",Long.class);
        carts.addItem(CUSTOMER,new AddCartItemRequest(stock,1));
        long line=jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?",Long.class,CUSTOMER,stock);
        carts.updateItemQuantity(CUSTOMER,line,1);
        return checkout.placeOrder(CUSTOMER,new CheckoutRequest(List.of(line),List.of(new StoreGroupCheckoutRequest(1L,FulfillmentType.DELIVERY,PaymentMethod.ONLINE,"Phase12 customer","0912345678","12 Test Street")))).orders().get(0).orderId();
    }

    private long multiItemOrder(boolean reverse) {
        List<Long> stocks=jdbc.queryForList("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id where sp.store_id=1 and p.sku in ('COCOON-SERUM-30','BBIA-CHEEK-08') order by sp.store_product_id",Long.class);
        assertThat(stocks).hasSize(2); if(reverse) Collections.reverse(stocks);
        List<Long> lines=new ArrayList<>();
        for(long stock:stocks) {
            carts.addItem(CUSTOMER,new AddCartItemRequest(stock,1));
            long line=jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?",Long.class,CUSTOMER,stock);
            carts.updateItemQuantity(CUSTOMER,line,stock==stocks.get(0)?2:3); lines.add(line);
        }
        return checkout.placeOrder(CUSTOMER,new CheckoutRequest(lines,List.of(new StoreGroupCheckoutRequest(1L,FulfillmentType.STORE_PICKUP,PaymentMethod.ONLINE,"Phase12 customer","0912345678",null)))).orders().get(0).orderId();
    }

    @Test void concurrentCancellationsOfMultiItemOrdersWithOppositeItemOrderRestoreEverySnapshotExactlyOnce() throws Exception {
        var s=scenario();
        try(var ignored=s.cleanup()) {
            var stock=jdbc.queryForList("select store_product_id,quantity from dbo.store_products order by store_product_id");
            long first=multiItemOrder(false),second=multiItemOrder(true);
            var items=jdbc.queryForList("select * from dbo.order_items where order_id in (?,?) order by order_item_id",first,second);
            var results=race(()->{cancel(first);return "OK";},()->{cancel(second);return "OK";});
            assertThat(results).containsExactly("OK","OK");
            assertThat(jdbc.queryForList("select store_product_id,quantity from dbo.store_products order by store_product_id")).isEqualTo(stock);
            for(long id:List.of(first,second)) {
                assertThat(s.history(id)).hasSize(2); assertThat(restores(id)).hasSize(2);
                for(var item:items) if(number(item.get("order_id"))==id) {
                    var movement=restores(id).stream().filter(m->m.get("store_product_id").equals(item.get("store_product_id"))).findFirst().orElseThrow();
                    assertThat(movement.get("quantity_change")).isEqualTo(item.get("quantity"));
                    assertThat(number(movement.get("quantity_before"))+number(item.get("quantity"))).isEqualTo(number(movement.get("quantity_after")));
                }
            }
            var stable=s.snapshot();cancel(first);cancel(second);assertThat(s.snapshot()).isEqualTo(stable);
            assertThat(jdbc.queryForList("select * from dbo.order_items where order_id in (?,?) order by order_item_id",first,second)).isEqualTo(items);
        }
    }

    @Test void failureOnSecondItemRestorationRollsBackFirstItemAndPaymentClosure() throws Exception {
        var s=scenario();var guard=s.cleanup();
        try {
            long id=multiItemOrder(false);payments.createOnlinePaymentAttempt(CUSTOMER,id);var before=s.snapshot();
            var count=new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(inv->{em.persist(inv.getArgument(0));em.flush();if(count.incrementAndGet()==2)throw new Abort();return inv.getArgument(0);}).when(movements).save(any(InventoryMovement.class));
            assertThatThrownBy(()->cancel(id)).isInstanceOf(Abort.class);assertThat(count.get()).isEqualTo(2);
            assertThat(s.snapshot()).isEqualTo(before);
            resetFaults();cancel(id);assertThat(restores(id)).hasSize(2);assertThat(s.history(id)).hasSize(2);
        } finally {resetFaults();guard.close();}
    }

    @Test void cancellingDifferentStoreCanCommitWhileFirstCancellationTransactionRemainsOpen() throws Exception {
        var s=scenario();var pool=Executors.newSingleThreadExecutor();var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var ignored=s.cleanup()) {
            var f=s.mixed();
            var first=pool.submit(()->new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
                cancel(f.a());em.flush();held.countDown();await(release);
            }));
            try {
                await(held);cancel(f.c());
                // Read by Order PK while the peer is held. Scanning the movement table here would itself
                // wait on its unrelated uncommitted insert under SQL Server READ COMMITTED.
                assertThat(s.order(f.c()).get("order_status")).isEqualTo("CANCELLED");
                assertThat(first.isDone()).as("Peer transaction is still open when second cancellation commits").isFalse();
            } finally {release.countDown();first.get(20,TimeUnit.SECONDS);}
            assertThat(s.order(f.a()).get("order_status")).isEqualTo("CANCELLED");assertThat(restores(f.a())).hasSize(1);
            assertThat(restores(f.c())).hasSize(1);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(20,TimeUnit.SECONDS)).isTrue();}
    }


    @Test void existingSqlDataHasCompleteTimelinesConservedStockAndNoDuplicateOrderMovements() {
        var s=scenario(); var before=s.snapshot();
        assertThat(jdbc.queryForList("""
            select o.order_id from dbo.orders o
            outer apply (select top 1 h.new_status from dbo.order_status_history h
                where h.order_id=o.order_id order by h.changed_at desc,h.history_id desc) last
            where last.new_status is null or last.new_status<>o.order_status
            """)).as("Every current order matches its last status history").isEmpty();
        assertThat(jdbc.queryForList("""
            with timeline as (select *,row_number() over(partition by order_id order by changed_at,history_id) rn,
                lag(new_status) over(partition by order_id order by changed_at,history_id) previous
                from dbo.order_status_history)
            select history_id from timeline where (rn=1 and old_status is not null)
                or (rn>1 and (old_status is null or old_status<>previous or old_status=new_status))
            """)).as("Timelines start at NULL and every transition connects without repeated status").isEmpty();
        assertThat(jdbc.queryForList("""
            select reference_order_id,store_product_id,type from dbo.inventory_movements
            where type in ('ORDER','CANCEL_ORDER')
            group by reference_order_id,store_product_id,type having count(*)>1
            """)).as("No duplicate deductions/restores per Order and StoreProduct").isEmpty();
        assertThat(jdbc.queryForList("""
            with item as (select order_id,store_product_id,sum(quantity) qty from dbo.order_items
                group by order_id,store_product_id)
            select item.order_id,item.store_product_id from item
            join dbo.orders o on o.order_id=item.order_id
            join dbo.store_products sp on sp.store_product_id=item.store_product_id
            outer apply (select sum(case when type='ORDER' then quantity_change else 0 end) deducted,
                sum(case when type='CANCEL_ORDER' then quantity_change else 0 end) restored
                from dbo.inventory_movements m where m.reference_order_id=item.order_id
                  and m.store_product_id=item.store_product_id) movement
            where sp.store_id<>o.store_id or coalesce(movement.deducted,0)<>-item.qty
                or coalesce(movement.restored,0)<>case when o.order_status='CANCELLED' then item.qty else 0 end
            """)).as("Snapshot quantities reconcile ORDER/CANCEL_ORDER within the same Store").isEmpty();
        assertThat(jdbc.queryForList("""
            with ledger as (select *,lag(quantity_after) over(partition by store_product_id order by created_at,movement_id) previous,
                row_number() over(partition by store_product_id order by created_at desc,movement_id desc) latest
                from dbo.inventory_movements)
            select m.movement_id from ledger m join dbo.store_products sp on sp.store_product_id=m.store_product_id
            where m.quantity_before<0 or m.quantity_after<0
                or cast(m.quantity_after as bigint)<>cast(m.quantity_before as bigint)+m.quantity_change
                or (m.previous is not null and m.quantity_before<>m.previous)
                or (m.latest=1 and m.quantity_after<>sp.quantity)
            """)).as("Persisted stock ledger is continuous and ends at exact current quantity").isEmpty();
        assertThat(jdbc.queryForList("""
            select p.payment_id from dbo.payments p join dbo.orders o on o.order_id=p.order_id
            where p.method<>o.payment_method or p.amount<>o.total_amount
                or (p.status='SUCCESS' and (p.paid_at is null or o.payment_status<>'PAID' or o.order_status='CANCELLED'))
                or (p.status='PENDING' and p.method='ONLINE' and (o.order_status<>'PENDING_PAYMENT' or o.payment_status<>'UNPAID'))
            """)).as("Existing receipts remain consistent with their Orders").isEmpty();
        assertThat(jdbc.queryForList("""
            select name from sys.check_constraints where is_not_trusted=1 or is_disabled=1
            union all select name from sys.foreign_keys where is_not_trusted=1 or is_disabled=1
            """)).as("SQL Server constraints remain enabled and trusted").isEmpty();
        assertThat(s.snapshot()).as("Read-only audit leaves existing business rows unchanged").isEqualTo(before);
    }

    @Test void adminStockEditMustLockBeforeLoadingQuantityToPreserveConcurrentMovementLedger() throws Exception {
        var s=scenario();var guard=s.cleanup();var pool=Executors.newSingleThreadExecutor();
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            long stock=jdbc.queryForObject("select store_product_id from dbo.store_products where store_id=1 and product_id=(select product_id from dbo.products where sku='COCOON-SERUM-30')",Long.class);
            int original=quantity(stock);
            var row=jdbc.queryForMap("select * from dbo.store_products where store_product_id=?",stock);
            long max=jdbc.queryForObject("select coalesce(max(movement_id),0) from dbo.inventory_movements",Long.class);
            var request=new StoreProductRequest(); request.setPrice((BigDecimal)row.get("price"));request.setStatus(ActiveStatus.ACTIVE);request.setQuantity(original+9);
            doAnswer(inv->{
                if(Thread.currentThread().getName().equals("phase12-admin-edit")) {reached.countDown();await(release);}
                return mockingDetails(stockRows).getMockCreationSettings().getDefaultAnswer().answer(inv);
            }).when(stockRows).findByIdForUpdate(stock);
            var future=pool.submit(()->{Thread.currentThread().setName("phase12-admin-edit");return catalogStock.updateStoreProduct(stock,request,ADMIN);});
            await(reached);
            inventory.adjustStock(stock,original+4,ADMIN,"Concurrent adjustment committed before Admin lock");
            release.countDown(); future.get(20,TimeUnit.SECONDS);
            var ledger=jdbc.queryForList("select * from dbo.inventory_movements where store_product_id=? and movement_id>? order by movement_id",stock,max);
            assertThat(ledger).hasSize(2);
            assertThat(number(ledger.get(0).get("quantity_after"))).isEqualTo(original+4);
            assertThat(number(ledger.get(1).get("quantity_before"))).as("Admin must observe the committed quantity, not its previously cached entity").isEqualTo(original+4);
            assertThat(number(ledger.get(1).get("quantity_change"))).isEqualTo(5);
            assertThat(quantity(stock)).isEqualTo(original+9);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(20,TimeUnit.SECONDS)).isTrue();resetFaults();guard.close();}
    }
}
