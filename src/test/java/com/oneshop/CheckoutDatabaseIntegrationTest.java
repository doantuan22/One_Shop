package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.StoreGroupCheckoutRequest;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.OrderItemResponse;
import com.oneshop.dto.response.OrderSummaryResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.CheckoutSession;
import com.oneshop.entity.CheckoutStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.InventoryMovement;
import com.oneshop.entity.InventoryMovementType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.StoreProduct;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.CheckoutSessionRepository;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 8 on the real SQL Server with the seed data: what CheckoutService creates and what it refuses
 * (TC-06, TC-08, TC-14). Concurrency (TC-07) and rollback in the middle of a checkout need real commits and are in
 * {@link CheckoutConcurrencyDatabaseIntegrationTest}.
 *
 * <p>Skipped without a configured database. Every test runs in a transaction that is rolled back.
 *
 * <p>Seed: khachhang2 (Bob) has a cart with three lines at three Stores; khachhang1 (Alice) has an empty cart.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class CheckoutDatabaseIntegrationTest {

    private static final String ALICE = "khachhang1@example.com";
    private static final String BOB = "khachhang2@example.com";

    private static final String SERUM = "COCOON-SERUM-30";
    private static final String TONER = "INNI-TONER-200";
    private static final String FOAM = "INNI-CLEANS-120";
    private static final String THU_DUC = "OS-THUDUC";
    private static final String GO_VAP = "OS-GOVAP";
    private static final String QUAN_7 = "OS-QUAN7";
    private static final String QUAN_10 = "OS-QUAN10";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    // ------------------------------------------------------------------ helpers

    private Long storeId(String code) {
        return storeRepository.findByCode(code).orElseThrow().getId();
    }

    private StoreProduct storeProduct(String sku, String storeCode) {
        return storeProductRepository.findByStoreIdAndProductId(storeId(storeCode),
                productRepository.findBySku(sku).orElseThrow().getId()).orElseThrow();
    }

    private Long sp(String sku, String storeCode) {
        return storeProduct(sku, storeCode).getId();
    }

    private int stock(Long storeProductId) {
        return storeProductRepository.findById(storeProductId).orElseThrow().getQuantity();
    }

    /** Adds to the cart and returns the id of the cart line. */
    private Long add(String email, Long storeProductId, int quantity) {
        cartService.addItem(email, new AddCartItemRequest(storeProductId, quantity));
        sync();
        return rowsOf(email).stream().filter(line -> line.getStoreProduct().getId().equals(storeProductId))
                .findFirst().orElseThrow().getId();
    }

    /** Writes pending changes and empties the persistence context, so the service reads everything from the database. */
    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<CartItem> rowsOf(String email) {
        return cartRepository.findByUserEmail(email)
                .map(cart -> cartItemRepository.findByCartIdOrderByIdAsc(cart.getId())).orElseGet(List::of);
    }

    private Map<Long, String> cartSnapshot(String email) {
        return rowsOf(email).stream().collect(Collectors.toMap(CartItem::getId,
                line -> line.getStoreProduct().getId() + "x" + line.getQuantity()));
    }

    private static StoreGroupCheckoutRequest delivery(Long storeId, PaymentMethod payment) {
        return new StoreGroupCheckoutRequest(storeId, FulfillmentType.DELIVERY, payment, "Người Nhận", "0912345678",
                "1 Đường Thử Nghiệm, TP.HCM");
    }

    private static StoreGroupCheckoutRequest pickup(Long storeId, PaymentMethod payment) {
        return new StoreGroupCheckoutRequest(storeId, FulfillmentType.STORE_PICKUP, payment, "Người Nhận", "0912345678", null);
    }

    private CheckoutResultResponse place(String email, List<Long> cartItemIds, StoreGroupCheckoutRequest... groups) {
        sync();
        CheckoutResultResponse result = checkoutService.placeOrder(email, new CheckoutRequest(cartItemIds, List.of(groups)));
        sync();
        return result;
    }

    private static OrderSummaryResponse orderAt(CheckoutResultResponse result, String storeName) {
        return result.orders().stream().filter(order -> order.storeName().equals(storeName)).findFirst().orElseThrow();
    }

    /** Row counts of everything a checkout writes, plus stock and carts: to assert "nothing happened". */
    private String worldState() {
        sync();
        return "sessions=" + checkoutSessionRepository.count() + " orders=" + orderRepository.count()
                + " items=" + orderItemRepository.count() + " movements=" + movementRepository.count()
                + " history=" + historyRepository.count() + " payments=" + paymentRepository.count()
                + " stock=" + storeProductRepository.findAll().stream().sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(storeProduct -> storeProduct.getId() + ":" + storeProduct.getQuantity()).collect(Collectors.joining(","))
                + " carts=" + cartSnapshot(ALICE) + cartSnapshot(BOB);
    }

    private void assertRefusedWithoutAnyEffect(String email, List<Long> cartItemIds, StoreGroupCheckoutRequest... groups) {
        String before = worldState();
        assertThatThrownBy(() -> checkoutService.placeOrder(email, new CheckoutRequest(cartItemIds, List.of(groups))))
                .isInstanceOf(BadRequestException.class);
        assertThat(worldState()).isEqualTo(before);
    }

    // ================================================================= checkout page

    @Test
    void preparingShowsTheChosenLinesByStoreAndSuggestsTheDefaultAddress() {
        List<Long> bobsLines = cartService.getCart(BOB).cartItemIds();

        CheckoutPreviewResponse preview = checkoutService.prepare(BOB, bobsLines);

        assertThat(preview.selection().groups()).hasSize(3);
        assertThat(preview.selection().total()).isEqualByComparingTo("828000");
        assertThat(preview.receiverName()).isEqualTo("Đặng Quốc Khánh");
        assertThat(preview.receiverPhone()).isEqualTo("0922222222");
        assertThat(preview.shippingAddress()).contains("Quang Trung");
        // nothing is created by looking at the page
        assertThatThrownBy(() -> checkoutService.prepare(ALICE, bobsLines)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> checkoutService.prepare(BOB, List.of())).isInstanceOf(BadRequestException.class);
    }

    // ================================================================= one Store

    @Test
    void checkoutOfOneStoreCreatesOneSessionOneOrderAndItsSnapshots() {
        Long serum = sp(SERUM, THU_DUC);
        Long toner = sp(TONER, THU_DUC);
        int serumStock = stock(serum);
        int tonerStock = stock(toner);
        Long lineSerum = add(ALICE, serum, 2);
        Long lineToner = add(ALICE, toner, 1);
        long sessions = checkoutSessionRepository.count();
        long orders = orderRepository.count();

        CheckoutResultResponse result = place(ALICE, List.of(lineSerum, lineToner), delivery(storeId(THU_DUC), PaymentMethod.COD));

        assertThat(checkoutSessionRepository.count()).isEqualTo(sessions + 1);
        assertThat(orderRepository.count()).isEqualTo(orders + 1);
        assertThat(result.status()).isEqualTo(CheckoutStatus.CREATED);
        assertThat(result.totalAmount()).isEqualByComparingTo("513000");          // 2 x 129000 + 255000
        OrderSummaryResponse order = result.orders().get(0);
        assertThat(order.storeName()).isEqualTo("OneShop Thủ Đức");
        assertThat(order.totalAmount()).isEqualByComparingTo("513000");
        assertThat(order.items()).extracting(OrderItemResponse::productName, OrderItemResponse::quantity)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Cocoon Serum Bí Đao 30ml", 2),
                        org.assertj.core.groups.Tuple.tuple("Innisfree Green Tea Balancing Toner 200ml", 1));
        assertThat(order.receiverName()).isEqualTo("Người Nhận");
        assertThat(order.shippingAddress()).isEqualTo("1 Đường Thử Nghiệm, TP.HCM");

        assertThat(stock(serum)).isEqualTo(serumStock - 2);
        assertThat(stock(toner)).isEqualTo(tonerStock - 1);
        assertThat(rowsOf(ALICE)).isEmpty();
        // the session belongs to the customer who checked out
        CheckoutSession session = checkoutSessionRepository.findById(result.checkoutId()).orElseThrow();
        assertThat(session.getUser().getEmail()).isEqualTo(ALICE);
        assertThat(orderRepository.findById(order.orderId()).orElseThrow().getUser().getEmail()).isEqualTo(ALICE);
    }

    // ================================================================= TC-06

    @Test
    void tc06_checkoutOfThreeStoresCreatesOneSessionAndOneOrderPerStore() {
        Map<Long, String> cart = cartSnapshot(BOB);
        long sessions = checkoutSessionRepository.count();
        long orders = orderRepository.count();
        long payments = paymentRepository.count();

        CheckoutResultResponse result = place(BOB, new ArrayList<>(cart.keySet()),
                delivery(storeId(THU_DUC), PaymentMethod.COD),
                pickup(storeId(GO_VAP), PaymentMethod.ONLINE),
                pickup(storeId(QUAN_7), PaymentMethod.PAY_AT_STORE));

        // one session grouping three Orders
        assertThat(checkoutSessionRepository.count()).isEqualTo(sessions + 1);
        assertThat(orderRepository.count()).isEqualTo(orders + 3);
        assertThat(result.orders()).extracting(OrderSummaryResponse::storeName)
                .containsExactlyInAnyOrder("OneShop Thủ Đức", "OneShop Gò Vấp", "OneShop Quận 7");
        List<Order> created = orderRepository.findByCheckoutSessionIdOrderByIdAsc(result.checkoutId());
        assertThat(created).hasSize(3).allSatisfy(order ->
                assertThat(order.getCheckoutSession().getId()).isEqualTo(result.checkoutId()));

        // every Order holds only items of its own Store
        for (Order order : created) {
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            assertThat(items).isNotEmpty().allSatisfy(item ->
                    assertThat(item.getStoreProduct().getStore().getId()).isEqualTo(order.getStore().getId()));
        }
        assertThat(orderAt(result, "OneShop Thủ Đức").items()).extracting(OrderItemResponse::productName)
                .containsExactly("Innisfree Green Tea Balancing Toner 200ml");
        assertThat(orderAt(result, "OneShop Gò Vấp").items()).extracting(OrderItemResponse::productName)
                .containsExactly("BBIA Downy Cheek #08");
        assertThat(orderAt(result, "OneShop Quận 7").items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("BBIA Downy Cheek #06");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.unitPrice()).isEqualByComparingTo("189000");
            assertThat(item.subtotal()).isEqualByComparingTo("378000");
        });

        // totals: Order = sum of its items, session = sum of its Orders
        assertThat(orderAt(result, "OneShop Thủ Đức").totalAmount()).isEqualByComparingTo("255000");
        assertThat(orderAt(result, "OneShop Gò Vấp").totalAmount()).isEqualByComparingTo("195000");
        assertThat(orderAt(result, "OneShop Quận 7").totalAmount()).isEqualByComparingTo("378000");
        assertThat(result.totalAmount()).isEqualByComparingTo("828000");

        // each Store group kept its own way of receiving and paying
        OrderSummaryResponse thuDuc = orderAt(result, "OneShop Thủ Đức");
        assertThat(thuDuc.fulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        assertThat(thuDuc.paymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(thuDuc.orderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(thuDuc.shippingAddress()).isNotBlank();
        OrderSummaryResponse goVap = orderAt(result, "OneShop Gò Vấp");
        assertThat(goVap.fulfillmentType()).isEqualTo(FulfillmentType.STORE_PICKUP);
        assertThat(goVap.paymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(goVap.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(goVap.shippingAddress()).isNull();
        OrderSummaryResponse quan7 = orderAt(result, "OneShop Quận 7");
        assertThat(quan7.fulfillmentType()).isEqualTo(FulfillmentType.STORE_PICKUP);
        assertThat(quan7.paymentMethod()).isEqualTo(PaymentMethod.PAY_AT_STORE);
        assertThat(quan7.orderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.orders()).allSatisfy(order ->
                assertThat(order.paymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID));

        // this phase stops at the Orders: no pickup data, no Payment rows
        assertThat(created).allSatisfy(order -> {
            assertThat(order.getPickupCode()).isNull();
            assertThat(order.getReadyAt()).isNull();
            assertThat(order.getPickedUpAt()).isNull();
        });
        assertThat(paymentRepository.count()).isEqualTo(payments);
        assertThat(rowsOf(BOB)).isEmpty();
    }

    @Test
    void everyOrderStartsItsTimelineWithACreationEntryWrittenByTheSystem() {
        CheckoutResultResponse result = place(BOB, cartService.getCart(BOB).cartItemIds(),
                delivery(storeId(THU_DUC), PaymentMethod.COD),
                pickup(storeId(GO_VAP), PaymentMethod.ONLINE),
                pickup(storeId(QUAN_7), PaymentMethod.PAY_AT_STORE));

        for (OrderSummaryResponse order : result.orders()) {
            List<OrderStatusHistory> timeline = historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.orderId());
            assertThat(timeline).singleElement().satisfies(entry -> {
                assertThat(entry.getOldStatus()).isNull();
                assertThat(entry.getNewStatus()).isEqualTo(order.orderStatus());
                assertThat(entry.getChangedBy()).as("created by the system, not approved by Staff").isNull();
            });
        }
    }

    // ================================================================= stock and inventory movements

    @Test
    void stockIsDeductedWithOneOrderMovementPerLine() {
        StoreProduct cheek = storeProduct("BBIA-CHEEK-08", THU_DUC);          // seed stock 8
        Long cheekId = cheek.getId();
        Long line = add(ALICE, cheekId, 3);
        long movements = movementRepository.count();

        CheckoutResultResponse result = place(ALICE, List.of(line), pickup(storeId(THU_DUC), PaymentMethod.PAY_AT_STORE));

        assertThat(stock(cheekId)).isEqualTo(5);
        assertThat(movementRepository.count()).isEqualTo(movements + 1);
        InventoryMovement movement = movementRepository.findByStoreProductIdOrderByCreatedAtDesc(cheekId, PageRequest.of(0, 20))
                .getContent().stream().max((a, b) -> a.getId().compareTo(b.getId())).orElseThrow();
        assertThat(movement.getType()).isEqualTo(InventoryMovementType.ORDER);
        assertThat(movement.getQuantityBefore()).isEqualTo(8);
        assertThat(movement.getQuantityChange()).isEqualTo(-3);
        assertThat(movement.getQuantityAfter()).isEqualTo(5);
        assertThat(movement.getReferenceOrder().getId()).isEqualTo(result.orders().get(0).orderId());
        assertThat(movement.getStaff()).isNull();
        assertThat(movement.getNote()).isEqualTo("Checkout #" + result.checkoutId() + " - Order #" + result.orders().get(0).orderId());
    }

    @Test
    void buyingTheWholeStockLeavesZeroNeverLess() {
        Long cheek = sp("BBIA-CHEEK-08", THU_DUC);                            // seed stock 8
        Long line = add(ALICE, cheek, 8);

        place(ALICE, List.of(line), pickup(storeId(THU_DUC), PaymentMethod.PAY_AT_STORE));

        assertThat(stock(cheek)).isZero();
        // and the next customer cannot buy what is gone
        assertThatThrownBy(() -> cartService.addItem(BOB, new AddCartItemRequest(cheek, 1)))
                .isInstanceOf(BadRequestException.class);
    }

    // ================================================================= the cart after checkout

    @Test
    void onlyTheCheckedOutLinesLeaveTheCart() {
        Long a = add(ALICE, sp(SERUM, THU_DUC), 1);
        Long b = add(ALICE, sp(TONER, THU_DUC), 1);
        Long c = add(ALICE, sp(SERUM, GO_VAP), 2);
        Long d = add(ALICE, sp(FOAM, QUAN_10), 1);

        CheckoutResultResponse result = place(ALICE, List.of(a, c),
                delivery(storeId(THU_DUC), PaymentMethod.ONLINE), delivery(storeId(GO_VAP), PaymentMethod.COD));

        assertThat(result.orders()).hasSize(2);
        assertThat(cartSnapshot(ALICE)).containsOnlyKeys(b, d);
        assertThat(cartSnapshot(ALICE).get(b)).isEqualTo(sp(TONER, THU_DUC) + "x1");
        assertThat(cartSnapshot(ALICE).get(d)).isEqualTo(sp(FOAM, QUAN_10) + "x1");
        // the cart itself stays, and Bob's cart was never involved
        assertThat(cartRepository.findByUserEmail(ALICE)).isPresent();
        assertThat(rowsOf(BOB)).hasSize(3);
    }

    @Test
    void duplicateCartItemIdsAreOrderedOnce() {
        Long serum = sp(SERUM, THU_DUC);
        int stockBefore = stock(serum);
        Long line = add(ALICE, serum, 2);

        CheckoutResultResponse result = place(ALICE, List.of(line, line, line), delivery(storeId(THU_DUC), PaymentMethod.COD));

        assertThat(result.orders()).singleElement().satisfies(order ->
                assertThat(order.items()).singleElement().extracting(OrderItemResponse::quantity).isEqualTo(2));
        assertThat(stock(serum)).isEqualTo(stockBefore - 2);
    }

    // ================================================================= TC-08 / TC-14: prices

    @Test
    void tc08_checkoutRequestCannotCarryAPriceTotalStatusOrOwner() {
        assertThat(componentNames(CheckoutRequest.class)).containsExactlyInAnyOrder("cartItemIds", "groups");
        assertThat(componentNames(StoreGroupCheckoutRequest.class)).containsExactlyInAnyOrder("storeId", "fulfillmentType",
                "paymentMethod", "receiverName", "receiverPhone", "shippingAddress");
    }

    private static List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void tc08_priceComesFromTheStoreProductAtTheMomentOfCheckoutNotFromTheCartPage() {
        Long serum = sp(SERUM, THU_DUC);
        Long line = add(ALICE, serum, 2);
        // the customer looked at the cart at 129.000; the price is changed before they confirm
        assertThat(cartService.getCart(ALICE).total()).isEqualByComparingTo("258000");
        storeProductRepository.findById(serum).orElseThrow().setPrice(new BigDecimal("140000"));

        CheckoutResultResponse result = place(ALICE, List.of(line), delivery(storeId(THU_DUC), PaymentMethod.COD));

        OrderItemResponse item = result.orders().get(0).items().get(0);
        assertThat(item.unitPrice()).isEqualByComparingTo("140000");
        assertThat(item.subtotal()).isEqualByComparingTo("280000");
        assertThat(result.totalAmount()).isEqualByComparingTo("280000");
    }

    @Test
    void tc14_orderItemsKeepTheirSnapshotWhenPriceAndProductNameChangeLater() {
        Long serum = sp(SERUM, THU_DUC);
        Long line = add(ALICE, serum, 2);
        CheckoutResultResponse placed = place(ALICE, List.of(line), delivery(storeId(THU_DUC), PaymentMethod.COD));

        // afterwards the Admin changes the price at that Store and renames the product
        storeProductRepository.findById(serum).orElseThrow().setPrice(new BigDecimal("159000"));
        productRepository.findBySku(SERUM).orElseThrow().setName("Cocoon Serum Bí Đao 30ml (bao bì mới)");
        sync();

        OrderItem stored = orderItemRepository.findByOrderId(placed.orders().get(0).orderId()).get(0);
        assertThat(stored.getUnitPrice()).isEqualByComparingTo("129000");
        assertThat(stored.getSubtotal()).isEqualByComparingTo("258000");
        assertThat(stored.getProductName()).isEqualTo("Cocoon Serum Bí Đao 30ml");
        // and the customer's view of the past checkout shows the snapshot, not the current catalog
        CheckoutResultResponse later = checkoutService.getCheckout(ALICE, placed.checkoutId());
        assertThat(later.orders().get(0).items().get(0).unitPrice()).isEqualByComparingTo("129000");
        assertThat(later.orders().get(0).items().get(0).productName()).isEqualTo("Cocoon Serum Bí Đao 30ml");
        assertThat(later.orders().get(0).totalAmount()).isEqualByComparingTo("258000");
        assertThat(later.totalAmount()).isEqualByComparingTo("258000");
        // while a new cart line already uses the new price
        add(ALICE, serum, 1);
        assertThat(cartService.getCart(ALICE).total()).isEqualByComparingTo("159000");
    }

    // ================================================================= ownership

    @Test
    void aCustomerCannotCheckOutAnotherCustomersLines() {
        List<Long> bobsLines = cartService.getCart(BOB).cartItemIds();
        Long own = add(ALICE, sp(SERUM, THU_DUC), 1);

        // only foreign lines
        assertRefusedWithoutAnyEffect(ALICE, bobsLines, delivery(storeId(THU_DUC), PaymentMethod.COD),
                pickup(storeId(GO_VAP), PaymentMethod.ONLINE), pickup(storeId(QUAN_7), PaymentMethod.PAY_AT_STORE));
        // an own line mixed with a foreign one: refused as a whole, the own line is not ordered either
        assertRefusedWithoutAnyEffect(ALICE, List.of(own, bobsLines.get(0)), delivery(storeId(THU_DUC), PaymentMethod.COD));
        // a line that does not exist
        assertRefusedWithoutAnyEffect(ALICE, List.of(own, 999_999_999L), delivery(storeId(THU_DUC), PaymentMethod.COD));
        assertRefusedWithoutAnyEffect(ALICE, List.of(), delivery(storeId(THU_DUC), PaymentMethod.COD));
        // an account without a cart
        assertRefusedWithoutAnyEffect("admin@oneshop.vn", bobsLines, delivery(storeId(THU_DUC), PaymentMethod.COD));
    }

    @Test
    void aCheckoutCanOnlyBeReadByItsOwner() {
        CheckoutResultResponse bobs = place(BOB, cartService.getCart(BOB).cartItemIds(),
                delivery(storeId(THU_DUC), PaymentMethod.COD), pickup(storeId(GO_VAP), PaymentMethod.ONLINE),
                pickup(storeId(QUAN_7), PaymentMethod.PAY_AT_STORE));

        assertThat(checkoutService.getCheckout(BOB, bobs.checkoutId()).orders()).hasSize(3);
        assertThatThrownBy(() -> checkoutService.getCheckout(ALICE, bobs.checkoutId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> checkoutService.getCheckout(BOB, -1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================= data that changed since the cart was filled

    @Test
    void checkoutIsRefusedWhenALineIsNoLongerOnSale() {
        Long serumAtThuDuc = sp(SERUM, THU_DUC);
        Long line = add(ALICE, serumAtThuDuc, 1);
        StoreGroupCheckoutRequest group = delivery(storeId(THU_DUC), PaymentMethod.COD);

        // StoreProduct switched off
        storeProductRepository.findById(serumAtThuDuc).orElseThrow().setStatus(ActiveStatus.INACTIVE);
        assertRefusedWithoutAnyEffect(ALICE, List.of(line), group);
        storeProductRepository.findById(serumAtThuDuc).orElseThrow().setStatus(ActiveStatus.ACTIVE);

        // Store closed
        storeRepository.findByCode(THU_DUC).orElseThrow().setStatus(ActiveStatus.INACTIVE);
        assertRefusedWithoutAnyEffect(ALICE, List.of(line), group);
        storeRepository.findByCode(THU_DUC).orElseThrow().setStatus(ActiveStatus.ACTIVE);

        // Product retired / hidden
        for (ProductStatus status : List.of(ProductStatus.INACTIVE, ProductStatus.HIDDEN)) {
            productRepository.findBySku(SERUM).orElseThrow().setStatus(status);
            assertRefusedWithoutAnyEffect(ALICE, List.of(line), group);
        }
        productRepository.findBySku(SERUM).orElseThrow().setStatus(ProductStatus.ACTIVE);

        // back on sale: it goes through
        assertThat(place(ALICE, List.of(line), group).orders()).hasSize(1);
    }

    @Test
    void checkoutIsRefusedWhenStockDroppedSinceTheLineWasAdded() {
        Long cheek = sp("BBIA-CHEEK-08", THU_DUC);                            // seed stock 8
        Long line = add(ALICE, cheek, 5);
        StoreGroupCheckoutRequest group = pickup(storeId(THU_DUC), PaymentMethod.PAY_AT_STORE);

        // the cart accepted 5; the Store has sold down to 4 since, then to 0
        storeProductRepository.findById(cheek).orElseThrow().setQuantity(4);
        assertRefusedWithoutAnyEffect(ALICE, List.of(line), group);
        storeProductRepository.findById(cheek).orElseThrow().setQuantity(0);
        assertRefusedWithoutAnyEffect(ALICE, List.of(line), group);

        assertThat(rowsOf(ALICE)).singleElement().extracting(CartItem::getQuantity).isEqualTo(5);
    }

    @Test
    void oneInvalidStoreGroupRefusesTheWholeCheckout() {
        Long a = add(ALICE, sp(SERUM, THU_DUC), 1);
        Long b = add(ALICE, sp("BBIA-CHEEK-08", GO_VAP), 2);
        Long c = add(ALICE, sp(FOAM, QUAN_10), 1);
        // Store B runs out after the cart was filled; A and C are fine
        storeProductRepository.findById(sp("BBIA-CHEEK-08", GO_VAP)).orElseThrow().setQuantity(1);

        assertRefusedWithoutAnyEffect(ALICE, List.of(a, b, c),
                delivery(storeId(THU_DUC), PaymentMethod.COD),
                pickup(storeId(GO_VAP), PaymentMethod.ONLINE),
                pickup(storeId(QUAN_10), PaymentMethod.PAY_AT_STORE));

        assertThat(cartSnapshot(ALICE)).containsOnlyKeys(a, b, c);
    }

    // ================================================================= choices per Store group

    @Test
    void fulfillmentAndPaymentCombinationsAreChecked() {
        Long line = add(ALICE, sp(SERUM, THU_DUC), 1);
        Long thuDuc = storeId(THU_DUC);

        // not allowed (Roadmap V2 6.4)
        assertRefusedWithoutAnyEffect(ALICE, List.of(line), delivery(thuDuc, PaymentMethod.PAY_AT_STORE));
        assertRefusedWithoutAnyEffect(ALICE, List.of(line), pickup(thuDuc, PaymentMethod.COD));
        // missing choices
        assertRefusedWithoutAnyEffect(ALICE, List.of(line),
                new StoreGroupCheckoutRequest(thuDuc, null, PaymentMethod.COD, "A", "0912345678", "x"));
        assertRefusedWithoutAnyEffect(ALICE, List.of(line),
                new StoreGroupCheckoutRequest(thuDuc, FulfillmentType.DELIVERY, null, "A", "0912345678", "x"));

        // allowed: all four
        for (StoreGroupCheckoutRequest allowed : List.of(delivery(thuDuc, PaymentMethod.COD), delivery(thuDuc, PaymentMethod.ONLINE),
                pickup(thuDuc, PaymentMethod.PAY_AT_STORE), pickup(thuDuc, PaymentMethod.ONLINE))) {
            Long again = add(ALICE, sp(SERUM, THU_DUC), 1);
            OrderSummaryResponse order = place(ALICE, List.of(again), allowed).orders().get(0);
            assertThat(order.fulfillmentType()).isEqualTo(allowed.fulfillmentType());
            assertThat(order.paymentMethod()).isEqualTo(allowed.paymentMethod());
            assertThat(order.orderStatus()).isEqualTo(allowed.paymentMethod() == PaymentMethod.ONLINE
                    ? OrderStatus.PENDING_PAYMENT : OrderStatus.CONFIRMED);
        }
    }

    @Test
    void deliveryNeedsAnAddressAndEveryOrderNeedsAReceiver() {
        Long line = add(ALICE, sp(SERUM, THU_DUC), 1);
        Long thuDuc = storeId(THU_DUC);

        for (String address : new String[]{null, "", "   ", "x".repeat(501)}) {
            assertRefusedWithoutAnyEffect(ALICE, List.of(line), new StoreGroupCheckoutRequest(thuDuc,
                    FulfillmentType.DELIVERY, PaymentMethod.COD, "Người Nhận", "0912345678", address));
        }
        for (String name : new String[]{null, " ", "x".repeat(151)}) {
            assertRefusedWithoutAnyEffect(ALICE, List.of(line), new StoreGroupCheckoutRequest(thuDuc,
                    FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, name, "0912345678", null));
        }
        for (String phone : new String[]{null, "", "abc", "123", "0912345678901234567890123"}) {
            assertRefusedWithoutAnyEffect(ALICE, List.of(line), new StoreGroupCheckoutRequest(thuDuc,
                    FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, "Người Nhận", phone, null));
        }

        // a pickup order is collected at its own Store: an address sent with it is not stored
        OrderSummaryResponse pickup = place(ALICE, List.of(line), new StoreGroupCheckoutRequest(thuDuc,
                FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, "  Người Nhận ", " 0912345678 ", "địa chỉ thừa")).orders().get(0);
        assertThat(pickup.shippingAddress()).isNull();
        assertThat(pickup.receiverName()).isEqualTo("Người Nhận");
        assertThat(pickup.receiverPhone()).isEqualTo("0912345678");
        assertThat(pickup.storeName()).isEqualTo("OneShop Thủ Đức");
    }

    @Test
    void aStoreOnlyOffersTheWaysOfReceivingItSupports() {
        // Quận 10 does not deliver (delivery_enabled = 0 in the seed data)
        Long line = add(ALICE, sp(FOAM, QUAN_10), 1);

        assertRefusedWithoutAnyEffect(ALICE, List.of(line), delivery(storeId(QUAN_10), PaymentMethod.COD));

        assertThat(place(ALICE, List.of(line), pickup(storeId(QUAN_10), PaymentMethod.PAY_AT_STORE)).orders()).hasSize(1);
    }

    @Test
    void storeOfAnOrderComesFromTheCartLinesNotFromTheRequest() {
        Long atThuDuc = add(ALICE, sp(SERUM, THU_DUC), 1);
        Long atGoVap = add(ALICE, sp(SERUM, GO_VAP), 1);
        Long thuDuc = storeId(THU_DUC);
        Long goVap = storeId(GO_VAP);

        // choices for a Store that has no chosen line, a missing Store, the same Store twice, no Store id
        assertRefusedWithoutAnyEffect(ALICE, List.of(atThuDuc), delivery(goVap, PaymentMethod.COD));
        assertRefusedWithoutAnyEffect(ALICE, List.of(atThuDuc, atGoVap), delivery(thuDuc, PaymentMethod.COD));
        assertRefusedWithoutAnyEffect(ALICE, List.of(atThuDuc), delivery(thuDuc, PaymentMethod.COD), delivery(goVap, PaymentMethod.COD));
        assertRefusedWithoutAnyEffect(ALICE, List.of(atThuDuc), delivery(thuDuc, PaymentMethod.COD), delivery(thuDuc, PaymentMethod.ONLINE));
        assertRefusedWithoutAnyEffect(ALICE, List.of(atThuDuc),
                new StoreGroupCheckoutRequest(null, FulfillmentType.DELIVERY, PaymentMethod.COD, "A", "0912345678", "x"));
        assertRefusedWithoutAnyEffect(ALICE, List.of(atThuDuc));
        assertRefusedWithoutAnyEffect(ALICE, List.of(atThuDuc), delivery(-1L, PaymentMethod.COD));

        // with matching choices each line lands in the Order of its own Store, at that Store's price
        CheckoutResultResponse result = place(ALICE, List.of(atThuDuc, atGoVap),
                pickup(goVap, PaymentMethod.ONLINE), delivery(thuDuc, PaymentMethod.COD));
        assertThat(orderAt(result, "OneShop Thủ Đức").items().get(0).unitPrice()).isEqualByComparingTo("129000");
        assertThat(orderAt(result, "OneShop Thủ Đức").fulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        assertThat(orderAt(result, "OneShop Gò Vấp").items().get(0).unitPrice()).isEqualByComparingTo("135000");
        assertThat(orderAt(result, "OneShop Gò Vấp").fulfillmentType()).isEqualTo(FulfillmentType.STORE_PICKUP);
    }

    // ================================================================= constraints of the database itself

    @Test
    void databaseRejectsAnOrderWithAForbiddenFulfillmentAndPaymentCombination() {
        CheckoutResultResponse placed = place(BOB, cartService.getCart(BOB).cartItemIds(),
                delivery(storeId(THU_DUC), PaymentMethod.COD), pickup(storeId(GO_VAP), PaymentMethod.ONLINE),
                pickup(storeId(QUAN_7), PaymentMethod.PAY_AT_STORE));
        Order delivery = orderRepository.findById(orderAt(placed, "OneShop Thủ Đức").orderId()).orElseThrow();

        delivery.setPaymentMethod(PaymentMethod.PAY_AT_STORE);

        assertThatThrownBy(() -> orderRepository.saveAndFlush(delivery))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_orders_fulfillment_payment");
    }

    @Test
    void databaseRejectsNegativeStock() {
        StoreProduct serum = storeProduct(SERUM, THU_DUC);

        serum.setQuantity(-1);

        assertThatThrownBy(() -> storeProductRepository.saveAndFlush(serum))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_store_products_quantity");
    }

    @Test
    void databaseRejectsAnOrderItemWhoseSubtotalIsNotPriceTimesQuantity() {
        Long line = add(ALICE, sp(SERUM, THU_DUC), 2);
        CheckoutResultResponse placed = place(ALICE, List.of(line), delivery(storeId(THU_DUC), PaymentMethod.COD));
        OrderItem item = orderItemRepository.findByOrderId(placed.orders().get(0).orderId()).get(0);

        item.setSubtotal(new BigDecimal("1"));

        assertThatThrownBy(() -> orderItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_order_items_subtotal");
    }

}
