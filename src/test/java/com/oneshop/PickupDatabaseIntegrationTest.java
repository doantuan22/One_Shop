package com.oneshop;

import com.oneshop.dto.request.*;
import com.oneshop.dto.response.PickupAction;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production services, SQL Server and Tomcat. Every committed fixture is restored and compared row-for-row. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PickupDatabaseIntegrationTest {
    private static final String CUSTOMER = "khachhang1@example.com";
    private static final String STAFF = "staff.thuduc@oneshop.vn";
    private static final String ROOT = "/staff/orders/pickup";
    private static final List<String> TABLES = List.of("orders", "order_items", "payments", "order_status_history",
            "inventory_movements", "store_products", "checkout_sessions", "cart_items", "carts");
    @Autowired private PickupFulfillmentService pickup;
    @Autowired private PaymentService payments;
    @Autowired private CustomerOrderService customerOrders;
    @MockitoSpyBean private com.oneshop.repository.OrderRepository orderRepository;
    @Autowired private CartService cart;
    @Autowired private CheckoutService checkout;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private PaymentRepository paymentRepository;
    @MockitoSpyBean private OrderStatusHistoryRepository histories;
    @MockitoSpyBean private OrderService stateMachine;
    @LocalServerPort private int port;
    private SeedGuard guard;
    private Map<String, List<Map<String, Object>>> before;
    private Map<String, List<Map<String, Object>>> scopeBefore;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForList("select * from dbo." + table + " order by 1"));
        return result;
    }

    @BeforeEach
    void remember() { before = snapshot(); scopeBefore = scopeSnapshot(); guard = new SeedGuard(jdbc); guard.remember(); }

    @AfterEach
    void restore() {
        reset(paymentRepository, histories, stateMachine, orderRepository);
        guard.restore();
        assertThat(snapshot()).as("Exact cleanup of every touched row including timestamps").isEqualTo(before);
        assertThat(scopeSnapshot()).as("Assignment, Store and account fixture changes also restored").isEqualTo(scopeBefore);
    }

    private Map<String, List<Map<String, Object>>> scopeSnapshot() {
        return Map.of("assignments", jdbc.queryForList("select * from dbo.staff_store_assignments order by 1"),
                "stores", jdbc.queryForList("select * from dbo.stores order by 1"),
                "users", jdbc.queryForList("select user_id,role_id,email,status,created_at,updated_at from dbo.users order by 1"));
    }

    private long staffId() {
        return jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, STAFF);
    }

    private long create(long storeId, FulfillmentType type, PaymentMethod method, boolean multipleItems) {
        List<Long> lines = new ArrayList<>();
        for (String sku : multipleItems ? List.of("COCOON-SERUM-30", "BBIA-CHEEK-08")
                : List.of(storeId == 1 ? "COCOON-SERUM-30" : "INNI-TONER-200")) {
            long stock = jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p "
                    + "on p.product_id=sp.product_id where sp.store_id=? and p.sku=?", Long.class, storeId, sku);
            cart.addItem(CUSTOMER, new AddCartItemRequest(stock, 1));
            lines.add(jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id "
                    + "join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, CUSTOMER, stock));
        }
        var group = new StoreGroupCheckoutRequest(storeId, type, method, "Người nhận kiểm thử", "0912345678",
                type == FulfillmentType.DELIVERY ? "1 Đường kiểm thử" : null);
        return checkout.placeOrder(CUSTOMER, new CheckoutRequest(lines, List.of(group))).orders().get(0).orderId();
    }

    private long payAtStore() { return create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, false); }
    private long online(boolean pay) {
        long id = create(1, FulfillmentType.STORE_PICKUP, PaymentMethod.ONLINE, false);
        if(pay) { var p=payments.createOnlinePaymentAttempt(CUSTOMER,id); payments.markOnlinePaymentSuccess(CUSTOMER,id,p.paymentId()); }
        return id;
    }
    private String code(long id) { return (String)order(id).get("pickup_code"); }
    private void ready(long id) { pickup.startPreparingPickup(STAFF,id); pickup.markReadyForPickup(STAFF,id); }
    private void invoke(String staff,long id,PickupAction action) {
        switch(action) {
            case PREPARE -> pickup.startPreparingPickup(staff,id);
            case READY -> pickup.markReadyForPickup(staff,id);
            case COMPLETE -> pickup.completePickup(staff,id,code(id));
        }
    }

    private Map<String, Object> order(long id) { return jdbc.queryForMap("select * from dbo.orders where order_id=?", id); }
    private List<Map<String, Object>> receipts(long id) { return jdbc.queryForList("select * from dbo.payments where order_id=? order by payment_id", id); }
    private List<Map<String, Object>> history(long id) { return jdbc.queryForList("select * from dbo.order_status_history where order_id=? order by history_id", id); }

    private void state(long id, String status, String payment) {
        assertThat(order(id)).containsEntry("order_status", status).containsEntry("payment_status", payment);
    }

    private void audit(long id, int offset) {
        var entries = history(id);
        assertThat(entries).hasSize(offset + 3);
        int index = offset;
        for (var action : PickupAction.values()) {
            assertThat(entries.get(index++)).containsEntry("old_status", action.getExpectedStatus().name())
                    .containsEntry("new_status", action.getTargetStatus().name()).containsEntry("changed_by_user_id", staffId())
                    .containsEntry("note", action.getLabel());
            assertThat(entries.get(index - 1).get("changed_at")).isNotNull();
        }
    }

    @ParameterizedTest
    @EnumSource(value=PaymentMethod.class,names={"PAY_AT_STORE","ONLINE"})
    void tc10AndOnlineLifecyclePreserveInventoryAndSnapshots(PaymentMethod method) {
        long id=method==PaymentMethod.ONLINE ? online(true) : create(1,FulfillmentType.STORE_PICKUP,method,true);
        var initial=snapshot(); var receipt=receipts(id); String generated=null; Object readyTime=null;
        assertThat(customerOrders.getOrder(CUSTOMER,id).order().pickupCode()).isNull();
        for(var action:PickupAction.values()) {
            invoke(STAFF,id,action);
            state(id,action.getTargetStatus().name(),method==PaymentMethod.ONLINE || action==PickupAction.COMPLETE ? "PAID":"UNPAID");
            assertThat(snapshot().get("store_products")).isEqualTo(initial.get("store_products"));
            assertThat(snapshot().get("inventory_movements")).isEqualTo(initial.get("inventory_movements"));
            assertThat(snapshot().get("order_items")).isEqualTo(initial.get("order_items"));
            assertThat(snapshot().get("checkout_sessions")).isEqualTo(initial.get("checkout_sessions"));
            assertThat(order(id).get("picked_up_at")).satisfies(t -> { if(action==PickupAction.COMPLETE) assertThat(t).isNotNull(); else assertThat(t).isNull(); });
            if(action==PickupAction.READY) {
                generated=code(id); readyTime=order(id).get("ready_at"); assertThat(generated).matches("[A-HJ-NP-Z2-9]{8}"); assertThat(readyTime).isNotNull();
                assertThat(customerOrders.getOrder(CUSTOMER,id).order().pickupCode()).isEqualTo(generated);
                assertThat(pickup.getPickupOrder(STAFF,id).order().pickupCode()).isNull();
            }
            if(action==PickupAction.PREPARE) { assertThat(code(id)).isNull(); assertThat(order(id).get("ready_at")).isNull(); }
            if(method==PaymentMethod.ONLINE) assertThat(receipts(id)).isEqualTo(receipt);
            else assertThat(receipts(id)).hasSize(action==PickupAction.COMPLETE?1:0);
            var after=snapshot(); assertThatThrownBy(()->invoke(STAFF,id,action)).isInstanceOf(BadRequestException.class); assertThat(snapshot()).isEqualTo(after);
        }
        assertThat(code(id)).isEqualTo(generated); assertThat(order(id).get("ready_at")).isEqualTo(readyTime);
        assertThat(customerOrders.getOrder(CUSTOMER,id).order().pickupCode()).isEqualTo(generated);
        audit(id,method==PaymentMethod.ONLINE?2:1);
        if(method==PaymentMethod.PAY_AT_STORE) {
            var p=receipts(id).get(0); assertThat(p).containsEntry("method","PAY_AT_STORE").containsEntry("status","SUCCESS").containsEntry("amount",order(id).get("total_amount"));
            assertThat(p.get("paid_at")).isNotNull(); assertThat(p.get("transaction_code").toString()).matches("PAY-[0-9a-f-]{36}");
        }
        assertThat(jdbc.queryForList("select name from sys.check_constraints where parent_object_id=OBJECT_ID('dbo.orders') and is_disabled=1")).isEmpty();
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={" ","WRONG","ABCDEFGH9","ABCD2340","ABCD234!","ZZZZ9999"})
    void wrongAndMalformedCodeDoNotMutateAnyRows(String supplied) {
        long id=payAtStore(); ready(id); var original=snapshot();
        assertThatThrownBy(()->pickup.completePickup(STAFF,id,supplied)).isInstanceOf(BadRequestException.class).hasMessage("Mã nhận hàng không hợp lệ.");
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest @EnumSource(PickupAction.class)
    void pendingOnlineWrongFlowScopeAndOwnershipAreProtected(PickupAction action) {
        long pending=online(false); long delivery=create(1,FulfillmentType.DELIVERY,PaymentMethod.COD,false);
        long other=create(2,FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,false); var original=snapshot();
        assertThatThrownBy(()->invoke(STAFF,pending,action)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->invoke(STAFF,delivery,action)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->invoke(STAFF,other,action)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->pickup.getPickupOrder(STAFF,other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->customerOrders.getOrder("khachhang2@example.com",pending)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest @CsvSource({"ONLINE,PREPARE","ONLINE,READY","ONLINE,COMPLETE","PAY_AT_STORE,PREPARE","PAY_AT_STORE,READY","PAY_AT_STORE,COMPLETE"})
    void inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod method,PickupAction action) {
        long id=method==PaymentMethod.ONLINE?online(true):payAtStore();
        if(action!=PickupAction.PREPARE) pickup.startPreparingPickup(STAFF,id);
        if(action==PickupAction.COMPLETE) pickup.markReadyForPickup(STAFF,id);
        jdbc.update("update dbo.orders set payment_status=? where order_id=?",method==PaymentMethod.ONLINE?"UNPAID":"PAID",id);
        var original=snapshot(); assertThatThrownBy(()->invoke(STAFF,id,action)).isInstanceOf(BadRequestException.class); assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest @EnumSource(PickupAction.class)
    void successfulOfflineReceiptCannotBeCollectedOrRepairedAtAnyStage(PickupAction action) {
        long id=payAtStore(); if(action!=PickupAction.PREPARE) pickup.startPreparingPickup(STAFF,id);
        if(action==PickupAction.COMPLETE) pickup.markReadyForPickup(STAFF,id);
        jdbc.update("insert into dbo.payments(order_id,method,amount,status,transaction_code,paid_at) select order_id,'PAY_AT_STORE',total_amount,'SUCCESS','PAY-fixture',SYSDATETIME() from dbo.orders where order_id=?",id);
        var original=snapshot(); assertThatThrownBy(()->invoke(STAFF,id,action)).isInstanceOf(BadRequestException.class); assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest @CsvSource({"CONFIRMED,READY","CONFIRMED,COMPLETE","PREPARING,PREPARE","PREPARING,COMPLETE","READY_FOR_PICKUP,PREPARE","READY_FOR_PICKUP,READY"})
    void skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus stage,PickupAction action) {
        long id=payAtStore(); if(stage!=OrderStatus.CONFIRMED) pickup.startPreparingPickup(STAFF,id);
        if(stage==OrderStatus.READY_FOR_PICKUP) pickup.markReadyForPickup(STAFF,id);
        var original=snapshot(); assertThatThrownBy(()->invoke(STAFF,id,action)).isInstanceOf(BadRequestException.class); assertThat(snapshot()).isEqualTo(original);
    }

    @Test void cancelledOnlinePickupIsTerminal() {
        long id=online(false); var p=payments.createOnlinePaymentAttempt(CUSTOMER,id); payments.markOnlinePaymentFailed(CUSTOMER,id,p.paymentId());
        var original=snapshot(); for(var action:PickupAction.values()) assertThatThrownBy(()->invoke(STAFF,id,action)).isInstanceOf(BadRequestException.class);
        assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest @ValueSource(strings={"BEFORE_TRANSITION","ORDER_FLUSH","HISTORY_SAVE","AFTER_HISTORY_FLUSH"})
    void readyFieldsAndHistoryRollBackAtEveryFailurePoint(String point) {
        long id=payAtStore(); pickup.startPreparingPickup(STAFF,id); var original=snapshot();
        if(point.equals("BEFORE_TRANSITION")) doAnswer(call->{
            entityManager.flush(); assertThat(entityManager.createNativeQuery("select pickup_code from dbo.orders where order_id="+id).getSingleResult()).isNotNull();
            assertThat(entityManager.createNativeQuery("select ready_at from dbo.orders where order_id="+id).getSingleResult()).isNotNull();
            assertThat(entityManager.createNativeQuery("select order_status from dbo.orders where order_id="+id).getSingleResult()).isEqualTo("PREPARING");
            throw new IllegalStateException("Injected before READY transition");
        }).when(stateMachine).transition(eq(id),eq(OrderStatus.PREPARING),eq(OrderStatus.READY_FOR_PICKUP),any(),anyString());
        if(point.equals("ORDER_FLUSH")) doAnswer(call->{ entityManager.flush(); assertDbState(id,"READY_FOR_PICKUP","UNPAID");
            throw new IllegalStateException("Injected at Order flush"); }).when(orderRepository).saveAndFlush(any(Order.class));
        if(point.contains("HISTORY")) doAnswer(call->{
            assertDbState(id,"READY_FOR_PICKUP","UNPAID");
            if(point.equals("AFTER_HISTORY_FLUSH")) {entityManager.persist(call.getArgument(0));entityManager.flush();}
            throw new IllegalStateException("Injected READY history failure");
        }).when(histories).save(any(OrderStatusHistory.class));
        assertThatThrownBy(()->pickup.markReadyForPickup(STAFF,id)).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(original); assertThat(code(id)).isNull(); assertThat(order(id).get("ready_at")).isNull();
    }

    @ParameterizedTest @ValueSource(strings={"PAYMENT_SAVE","AFTER_PAYMENT_FLUSH","AFTER_PAID","AFTER_PICKED_UP","BEFORE_TRANSITION","HISTORY_SAVE","AFTER_HISTORY_FLUSH"})
    void payAtStoreCompletionRollsBackEveryWrite(String point) {
        long id=payAtStore(); ready(id); var original=snapshot();
        if(point.equals("PAYMENT_SAVE")) doThrow(new IllegalStateException("Injected Payment save")).when(paymentRepository).saveAndFlush(any(Payment.class));
        if(point.equals("AFTER_PAYMENT_FLUSH")) doAnswer(call->{entityManager.persist(call.getArgument(0));entityManager.flush();assertDbState(id,"READY_FOR_PICKUP","PAID");
            throw new IllegalStateException("Injected after Payment flush");}).when(paymentRepository).saveAndFlush(any(Payment.class));
        if(point.equals("AFTER_PAID")) doAnswer(call->{
            Payment pending=call.getArgument(0); assertThat(pending.getOrder().getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
            entityManager.flush(); assertDbState(id,"READY_FOR_PICKUP","PAID");
            throw new IllegalStateException("Injected after PAID before Payment insert");}).when(paymentRepository).saveAndFlush(any(Payment.class));
        if(point.equals("AFTER_PICKED_UP")||point.equals("BEFORE_TRANSITION")) doAnswer(call->{
            entityManager.flush(); assertDbState(id,"READY_FOR_PICKUP","PAID");
            assertThat(entityManager.createNativeQuery("select picked_up_at from dbo.orders where order_id="+id).getSingleResult()).isNotNull();
            throw new IllegalStateException("Injected before completion transition");
        }).when(stateMachine).transition(eq(id),eq(OrderStatus.READY_FOR_PICKUP),eq(OrderStatus.COMPLETED),any(),anyString());
        if(point.contains("HISTORY")) doAnswer(call->{assertDbState(id,"COMPLETED","PAID");
            if(point.equals("AFTER_HISTORY_FLUSH")) {entityManager.persist(call.getArgument(0));entityManager.flush();}
            throw new IllegalStateException("Injected completion history failure");}).when(histories).save(any(OrderStatusHistory.class));
        assertThatThrownBy(()->pickup.completePickup(STAFF,id,code(id))).isInstanceOf(IllegalStateException.class); assertThat(snapshot()).isEqualTo(original);
    }

    @ParameterizedTest @ValueSource(strings={"BEFORE_TRANSITION","HISTORY_SAVE","AFTER_HISTORY_FLUSH"})
    void onlineCompletionRollsBackTimeStatusAndHistoryWithoutTouchingReceipt(String point) {
        long id=online(true); ready(id); var original=snapshot();
        if(point.equals("BEFORE_TRANSITION")) doAnswer(call->{entityManager.flush();assertDbState(id,"READY_FOR_PICKUP","PAID");
            assertThat(entityManager.createNativeQuery("select picked_up_at from dbo.orders where order_id="+id).getSingleResult()).isNotNull();
            throw new IllegalStateException("Injected after picked_up_at");}).when(stateMachine).transition(eq(id),eq(OrderStatus.READY_FOR_PICKUP),eq(OrderStatus.COMPLETED),any(),anyString());
        else doAnswer(call->{assertDbState(id,"COMPLETED","PAID");if(point.equals("AFTER_HISTORY_FLUSH")){entityManager.persist(call.getArgument(0));entityManager.flush();}
            throw new IllegalStateException("Injected history failure");}).when(histories).save(any(OrderStatusHistory.class));
        assertThatThrownBy(()->pickup.completePickup(STAFF,id,code(id))).isInstanceOf(IllegalStateException.class);assertThat(snapshot()).isEqualTo(original);
    }
    private void assertDbState(long id,String state,String paid) {
        assertThat(entityManager.createNativeQuery("select order_status from dbo.orders where order_id="+id).getSingleResult()).isEqualTo(state);
        assertThat(entityManager.createNativeQuery("select payment_status from dbo.orders where order_id="+id).getSingleResult()).isEqualTo(paid);
    }

    @ParameterizedTest @CsvSource({"PAY_AT_STORE,READY,1","PAY_AT_STORE,READY,2","PAY_AT_STORE,READY,3","PAY_AT_STORE,COMPLETE,1","PAY_AT_STORE,COMPLETE,2","PAY_AT_STORE,COMPLETE,3","ONLINE,COMPLETE,1","ONLINE,COMPLETE,2","ONLINE,COMPLETE,3"})
    void sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod method,PickupAction action,int round) throws Exception {
        long id=method==PaymentMethod.ONLINE?online(true):payAtStore(); pickup.startPreparingPickup(STAFF,id);
        if(action==PickupAction.COMPLETE) pickup.markReadyForPickup(STAFF,id);
        var receipt=receipts(id); String originalCode=code(id); Object originalTime=order(id).get("ready_at"); int initial=history(id).size();
        var results=race(()->invoke(STAFF,id,action),()->invoke(STAFF,id,action));
        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);assertThat(results.stream().filter(BadRequestException.class::isInstance).count()).isEqualTo(1);
        assertThat(history(id)).hasSize(initial+1); assertThat(code(id)).matches("[A-HJ-NP-Z2-9]{8}"); assertThat(order(id).get("ready_at")).isNotNull();
        state(id,action.getTargetStatus().name(),action==PickupAction.COMPLETE?"PAID":"UNPAID");
        if(action==PickupAction.COMPLETE) {assertThat(code(id)).isEqualTo(originalCode);assertThat(order(id).get("ready_at")).isEqualTo(originalTime); assertThat(order(id).get("picked_up_at")).isNotNull();}
        if(method==PaymentMethod.ONLINE)assertThat(receipts(id)).isEqualTo(receipt);else assertThat(receipts(id)).hasSize(action==PickupAction.COMPLETE?1:0);
        var completed=snapshot();assertThatThrownBy(()->invoke(STAFF,id,action)).isInstanceOf(BadRequestException.class);assertThat(snapshot()).isEqualTo(completed);
    }

    @Test void codeVisibilityOwnerAndReadOnlyListsAreEnforced() {
        long own=payAtStore(); long other=create(2,FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,false);
        long delivery=create(1,FulfillmentType.DELIVERY,PaymentMethod.COD,false);
        ready(own);var original=snapshot();
        assertThat(pickup.getPickupOrders(STAFF).stream().map(o->o.orderId()).toList()).contains(own).doesNotContain(other,delivery);
        assertThat(customerOrders.getOrders(CUSTOMER).stream().map(o->o.orderId()).toList()).contains(own,other,delivery);
        assertThat(customerOrders.getOrders(CUSTOMER)).allSatisfy(o->assertThat(o.pickupCode()).isNull());
        assertThatThrownBy(()->customerOrders.getOrder("khachhang2@example.com",own)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(customerOrders.getOrder(CUSTOMER,own).order().pickupCode()).isEqualTo(code(own));
        assertThat(snapshot()).isEqualTo(original);
        long staff=staffId();jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status) values(?,2,'ACTIVE')",staff);
        try {assertThat(pickup.getPickupOrders(STAFF).stream().map(o->o.orderId()).toList()).contains(own,other).doesNotContain(delivery);}
        finally {jdbc.update("delete from dbo.staff_store_assignments where user_id=? and store_id=2",staff);}
    }

    @ParameterizedTest @ValueSource(strings={"ASSIGNMENT","STORE","USER"})
    void inactiveScopeRejectsReadAndEveryAction(String inactive) {
        long id=payAtStore();ready(id);long staff=staffId();
        String table=inactive.equals("ASSIGNMENT")?"staff_store_assignments":inactive.equals("STORE")?"stores":"users";
        String where=inactive.equals("ASSIGNMENT")?"user_id="+staff+" and store_id=1":inactive.equals("STORE")?"store_id=1":"user_id="+staff;
        String previous=jdbc.queryForObject("select status from dbo."+table+" where "+where,String.class);
        try {jdbc.update("update dbo."+table+" set status='INACTIVE' where "+where);var original=snapshot();
            assertThatThrownBy(()->pickup.getPickupOrders(STAFF)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->pickup.getPickupOrder(STAFF,id)).isInstanceOf(AccessDeniedException.class);
            for(var a:PickupAction.values())assertThatThrownBy(()->invoke(STAFF,id,a)).isInstanceOf(AccessDeniedException.class);
            assertThat(snapshot()).isEqualTo(original);
            if(inactive.equals("STORE"))assertThat(customerOrders.getOrder(CUSTOMER,id).order().pickupCode()).isEqualTo(code(id));
        }finally{jdbc.update("update dbo."+table+" set status=? where "+where,previous);}
    }

    @Test void purchaseSnapshotsRemainAfterCatalogueChange() {
        long id=payAtStore(); var item=jdbc.queryForMap("select * from dbo.order_items where order_id=?",id);
        long stock=((Number)item.get("store_product_id")).longValue();long product=jdbc.queryForObject("select product_id from dbo.store_products where store_product_id=?",Long.class,stock);
        String name=jdbc.queryForObject("select name from dbo.products where product_id=?",String.class,product);Object amount=order(id).get("total_amount");
        try {jdbc.update("update dbo.products set name='Changed pickup fixture' where product_id=?",product);jdbc.update("update dbo.store_products set price=1 where store_product_id=?",stock);
            for(var detail:List.of(pickup.getPickupOrder(STAFF,id),customerOrders.getOrder(CUSTOMER,id))) {
                assertThat(detail.items()).singleElement().satisfies(i->{assertThat(i.productName()).isEqualTo(item.get("product_name"));assertThat(i.unitPrice()).isEqualTo(item.get("unit_price"));});
            }
            ready(id);pickup.completePickup(STAFF,id,code(id));assertThat(receipts(id).get(0)).containsEntry("amount",amount);
            assertThat(jdbc.queryForMap("select * from dbo.order_items where order_id=?",id)).isEqualTo(item);
        }finally{jdbc.update("update dbo.products set name=? where product_id=?",name,product);}
    }

    @Test void mixedCheckoutPickupDoesNotMutateNeighborOrdersOrPayments() {
        List<Long> lines=new ArrayList<>();for(long store:List.of(1L,2L,4L)) {
            String sku=store==1?"COCOON-SERUM-30":store==2?"INNI-TONER-200":"INNI-CLEANS-120";
            long sp=jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id where sp.store_id=? and p.sku=?",Long.class,store,sku);
            cart.addItem(CUSTOMER,new AddCartItemRequest(sp,1));lines.add(jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?",Long.class,CUSTOMER,sp));
        }
        var result=checkout.placeOrder(CUSTOMER,new CheckoutRequest(lines,List.of(
                new StoreGroupCheckoutRequest(1L,FulfillmentType.DELIVERY,PaymentMethod.COD,"Fixture","0912345678","1 test"),
                new StoreGroupCheckoutRequest(2L,FulfillmentType.STORE_PICKUP,PaymentMethod.ONLINE,"Fixture","0912345678",null),
                new StoreGroupCheckoutRequest(4L,FulfillmentType.STORE_PICKUP,PaymentMethod.PAY_AT_STORE,"Fixture","0912345678",null))));
        long id=result.orders().stream().filter(o->o.storeId()==4L).findFirst().orElseThrow().orderId();
        var neighbors=jdbc.queryForList("select * from dbo.orders where checkout_id=? and order_id<>? order by 1",result.checkoutId(),id);
        var neighborReceipts=jdbc.queryForList("select p.* from dbo.payments p join dbo.orders o on o.order_id=p.order_id where o.checkout_id=? and o.order_id<>?",result.checkoutId(),id);
        var initial=snapshot();String staff="staff.quan10@oneshop.vn";
        for(var action:PickupAction.values()){invoke(staff,id,action);
            assertThat(jdbc.queryForList("select * from dbo.orders where checkout_id=? and order_id<>? order by 1",result.checkoutId(),id)).isEqualTo(neighbors);
            assertThat(jdbc.queryForList("select p.* from dbo.payments p join dbo.orders o on o.order_id=p.order_id where o.checkout_id=? and o.order_id<>?",result.checkoutId(),id)).isEqualTo(neighborReceipts);
            assertThat(snapshot().get("checkout_sessions")).isEqualTo(initial.get("checkout_sessions"));assertThat(snapshot().get("store_products")).isEqualTo(initial.get("store_products"));assertThat(snapshot().get("inventory_movements")).isEqualTo(initial.get("inventory_movements"));
        }
    }
    @Test void payAtStoreHelperRequiresExistingTransaction() {
        assertThatThrownBy(()->payments.recordPayAtStoreCollected(new Order())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    @Test void existingSeedCodeConventionWorksWithoutRewritingHistoricalCode() {
        long id=online(true); ready(id);
        String oldCode=jdbc.queryForObject("select pickup_code from dbo.orders where order_id=2",String.class);
        jdbc.update("update dbo.orders set pickup_code=? where order_id=?",oldCode,id);var receipt=receipts(id);Object time=order(id).get("ready_at");
        pickup.completePickup(STAFF,id,oldCode.toLowerCase(Locale.ROOT));
        state(id,"COMPLETED","PAID");assertThat(code(id)).isEqualTo(oldCode);assertThat(order(id).get("ready_at")).isEqualTo(time);assertThat(receipts(id)).isEqualTo(receipt);
    }

    @ParameterizedTest @EnumSource(value=PaymentMethod.class,names={"ONLINE","PAY_AT_STORE"})
    void realHttpPickupCustomerCodeWrongCodeAndForgedFields(PaymentMethod method) throws Exception {
        long id=method==PaymentMethod.ONLINE?online(true):payAtStore();String staff=login(STAFF),customer=login(CUSTOMER),other=login("khachhang2@example.com");
        var list=request(ROOT+"?storeId=999",null,"Cookie",staff);assertThat(list.statusCode()).isEqualTo(200);assertThat(list.body()).contains("data-layout=\"staff\"","data-order-id=\""+id+"\"");
        for(var action:PickupAction.values()) {
            var page=request(ROOT+"/"+id,null,"Cookie",staff);assertThat(page.statusCode()).isEqualTo(200);assertThat(page.body()).contains(ROOT+"/"+id+"/"+action.getPath(),"name=\"_csrf\"");
            if(action==PickupAction.COMPLETE) {
                var client=request("/orders/"+id,null,"Cookie",customer);assertThat(client.statusCode()).isEqualTo(200);assertThat(client.body()).contains("id=\"pickup-code\"",code(id));assertThat(client.body()).doesNotContain("name=\"pickupCode\"",ROOT+"/"+id+"/complete");
                assertThat(request("/orders/"+id+"?userId=1",null,"Cookie",other).statusCode()).isEqualTo(404);
                assertThat(page.body()).doesNotContain(code(id));var original=snapshot();
                var wrong=postAs(staff,ROOT+"/"+id+"/complete","pickupCode=WRONG999");assertThat(wrong.statusCode()).isEqualTo(400);assertThat(wrong.body()).doesNotContain(code(id),"WRONG999");assertThat(snapshot()).isEqualTo(original);
            } else assertThat(request("/orders/"+id,null,"Cookie",customer).body()).doesNotContain("id=\"pickup-code\"");
            String body="amount=1&paymentStatus=PAID&targetStatus=CANCELLED&readyAt=2000-01-01&pickedUpAt=2000-01-01&storeId=2&transactionCode=HACK";
            if(action==PickupAction.COMPLETE) body+="&pickupCode="+code(id);
            assertThat(postAs(staff,ROOT+"/"+id+"/"+action.getPath(),body).statusCode()).isEqualTo(302);
        }
        state(id,"COMPLETED","PAID");audit(id,method==PaymentMethod.ONLINE?2:1);assertThat(order(id).get("picked_up_at")).isNotNull();
        assertThat(postAs(customer,"/orders/"+id+"/complete","pickupCode="+code(id)).statusCode()).isEqualTo(404);
    }
    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timeout");
                try { action.run(); return null; } catch (Throwable failure) { return failure; }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Throwable> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private HttpResponse<String> request(String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) request.GET(); else request.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) request.headers(headers);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}"));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(token.find()).isTrue(); return "ONESHOP_TOKEN=" + token.group(1);
    }

    private HttpResponse<String> postAs(String cookie, String path, String body) throws Exception {
        var page = request("/login", null);
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return request(path, body + "&_csrf=" + csrf.group(1), "Cookie", cookie + "; " + tokenCookie);
    }
}
