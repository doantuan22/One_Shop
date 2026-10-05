package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.RegisterRequest;
import com.oneshop.dto.request.UpdateCartItemRequest;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CartStoreGroupResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Cart;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.CheckoutSessionRepository;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.AuthService;
import com.oneshop.service.CartService;
import com.oneshop.service.StoreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 7 on the real SQL Server with the seed data: the cart rules of CartService and the constraints of
 * {@code cart_items} (TC-04, TC-05).
 *
 * <p>Skipped without a configured database. Every test runs in a transaction that is rolled back.
 *
 * <p>Seed: khachhang1 has an empty cart; khachhang2 has a cart with three lines at three Stores.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class CartDatabaseIntegrationTest {

    private static final String ALICE = "khachhang1@example.com";   // empty cart
    private static final String BOB = "khachhang2@example.com";     // 3 lines at 3 Stores

    private static final String SERUM = "COCOON-SERUM-30";
    private static final String TONER = "INNI-TONER-200";
    private static final String CHEEK_08 = "BBIA-CHEEK-08";
    private static final String THU_DUC = "OS-THUDUC";
    private static final String GO_VAP = "OS-GOVAP";
    private static final String QUAN_7 = "OS-QUAN7";
    private static final String QUAN_10 = "OS-QUAN10";
    private static final String HAI_CHAU = "OS-HAICHAU";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CartService cartService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private AuthService authService;

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
    private InventoryMovementRepository movementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    // ------------------------------------------------------------------ helpers

    /** The StoreProduct of a SKU at a Store, whatever its status. */
    private StoreProduct storeProduct(String sku, String storeCode) {
        return storeProductRepository.findByStoreIdAndProductId(
                storeRepository.findByCode(storeCode).orElseThrow().getId(),
                productRepository.findBySku(sku).orElseThrow().getId()).orElseThrow();
    }

    private Long sp(String sku, String storeCode) {
        return storeProduct(sku, storeCode).getId();
    }

    private void add(String email, Long storeProductId, int quantity) {
        cartService.addItem(email, new AddCartItemRequest(storeProductId, quantity));
        sync();
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<CartItem> rowsOf(String email) {
        return cartRepository.findByUserEmail(email)
                .map(cart -> cartItemRepository.findByCartIdOrderByIdAsc(cart.getId())).orElseGet(List::of);
    }

    private static List<CartItemResponse> lines(CartResponse cart) {
        return cart.groups().stream().flatMap(group -> group.items().stream()).toList();
    }

    private CartItemResponse line(String email, Long storeProductId) {
        return lines(cartService.getCart(email)).stream()
                .filter(item -> item.storeProductId().equals(storeProductId)).findFirst().orElseThrow();
    }

    /** id of a cart line -> "storeProductId x quantity" */
    private Map<Long, String> snapshot(String email) {
        return rowsOf(email).stream().collect(Collectors.toMap(CartItem::getId,
                item -> item.getStoreProduct().getId() + "x" + item.getQuantity()));
    }

    // ================================================================= TC-04

    @Test
    void tc04_addingTheSameStoreProductTwiceGivesOneLineWithTheSummedQuantity() {
        Long serumAtThuDuc = sp(SERUM, THU_DUC);

        add(ALICE, serumAtThuDuc, 2);
        add(ALICE, serumAtThuDuc, 1);

        List<CartItem> rows = rowsOf(ALICE);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getQuantity()).isEqualTo(3);
        assertThat(rows.get(0).getStoreProduct().getId()).isEqualTo(serumAtThuDuc);

        CartResponse cart = cartService.getCart(ALICE);
        assertThat(cart.itemCount()).isEqualTo(1);
        assertThat(lines(cart)).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(3);
            assertThat(item.unitPrice()).isEqualByComparingTo("129000");
            assertThat(item.subtotal()).isEqualByComparingTo("387000");
        });
    }

    // ================================================================= TC-05

    @Test
    void tc05_cartWithLinesOfThreeStoresIsShownAsThreeGroups() {
        CartResponse cart = cartService.getCart(BOB);

        assertThat(cart.groups()).extracting(CartStoreGroupResponse::storeName)
                .containsExactly("OneShop Gò Vấp", "OneShop Quận 7", "OneShop Thủ Đức");
        assertThat(cart.itemCount()).isEqualTo(3);
        // every line sits in the group of the Store of its own StoreProduct, nowhere else
        for (CartStoreGroupResponse group : cart.groups()) {
            assertThat(group.items()).isNotEmpty().allSatisfy(item ->
                    assertThat(storeProductRepository.findById(item.storeProductId()).orElseThrow().getStore().getId())
                            .isEqualTo(group.storeId()));
        }
        assertThat(lines(cart)).extracting(CartItemResponse::cartItemId).doesNotHaveDuplicates();
        assertThat(lines(cart)).extracting(CartItemResponse::sku)
                .containsExactlyInAnyOrder(CHEEK_08, "BBIA-CHEEK-06", TONER);

        // group and cart totals are the sums of their lines, at the current StoreProduct prices
        CartStoreGroupResponse quan7 = cart.groups().get(1);
        assertThat(quan7.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualByComparingTo("378000");
        });
        assertThat(quan7.subtotal()).isEqualByComparingTo("378000");
        assertThat(cart.total()).isEqualByComparingTo(new BigDecimal("255000").add(new BigDecimal("195000"))
                .add(new BigDecimal("378000")));
    }

    @Test
    void tc05_aCustomerBuildsACartAcrossThreeStores() {
        add(ALICE, sp(SERUM, THU_DUC), 1);
        add(ALICE, sp(TONER, THU_DUC), 2);
        add(ALICE, sp(SERUM, GO_VAP), 1);
        add(ALICE, sp("INNI-CLEANS-120", QUAN_10), 1);

        CartResponse cart = cartService.getCart(ALICE);

        assertThat(cart.groups()).extracting(CartStoreGroupResponse::storeName)
                .containsExactly("OneShop Gò Vấp", "OneShop Quận 10", "OneShop Thủ Đức");
        assertThat(cart.groups().get(2).items()).extracting(CartItemResponse::sku).containsExactly(SERUM, TONER);
        assertThat(cart.groups().get(0).items()).extracting(CartItemResponse::sku).containsExactly(SERUM);
        assertThat(cart.itemCount()).isEqualTo(4);
    }

    // ================================================================= add

    @Test
    void sameSkuAtTwoStoresIsTwoIndependentLines() {
        Long atThuDuc = sp(SERUM, THU_DUC);
        Long atGoVap = sp(SERUM, GO_VAP);

        add(ALICE, atThuDuc, 2);
        add(ALICE, atGoVap, 1);
        add(ALICE, atThuDuc, 1);

        assertThat(rowsOf(ALICE)).hasSize(2);
        assertThat(line(ALICE, atThuDuc).quantity()).isEqualTo(3);
        assertThat(line(ALICE, atThuDuc).unitPrice()).isEqualByComparingTo("129000");
        assertThat(line(ALICE, atGoVap).quantity()).isEqualTo(1);
        assertThat(line(ALICE, atGoVap).unitPrice()).isEqualByComparingTo("135000");
        assertThat(cartService.getCart(ALICE).groups()).hasSize(2);
    }

    @Test
    void addingMoreThanTheStoreHasIsRefusedAndChangesNothing() {
        StoreProduct cheek = storeProduct(CHEEK_08, THU_DUC);     // seed stock 8
        int stock = cheek.getQuantity();

        assertThatThrownBy(() -> add(ALICE, cheek.getId(), stock + 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("không đủ hàng");
        assertThat(rowsOf(ALICE)).isEmpty();

        // exactly the stock is fine
        add(ALICE, cheek.getId(), stock);
        assertThat(line(ALICE, cheek.getId()).quantity()).isEqualTo(stock);
    }

    @Test
    void whatIsAlreadyInTheCartCountsTowardsTheStockCheck() {
        StoreProduct cheek = storeProduct(CHEEK_08, THU_DUC);     // seed stock 8
        add(ALICE, cheek.getId(), 5);

        assertThatThrownBy(() -> add(ALICE, cheek.getId(), 4))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("đã có trong giỏ");

        sync();
        assertThat(rowsOf(ALICE)).singleElement().extracting(CartItem::getQuantity).isEqualTo(5);
        add(ALICE, cheek.getId(), 3);
        assertThat(rowsOf(ALICE)).singleElement().extracting(CartItem::getQuantity).isEqualTo(8);
    }

    @Test
    void whatIsNotOnSaleCannotBeAdded() {
        // out of stock (ACTIVE, quantity 0)
        assertThatThrownBy(() -> add(ALICE, sp(SERUM, QUAN_7), 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("hết hàng");
        // StoreProduct INACTIVE
        assertThatThrownBy(() -> add(ALICE, sp(TONER, QUAN_10), 1)).isInstanceOf(BadRequestException.class);
        // Store INACTIVE
        assertThatThrownBy(() -> add(ALICE, sp(SERUM, HAI_CHAU), 1)).isInstanceOf(BadRequestException.class);
        // Product INACTIVE
        assertThatThrownBy(() -> add(ALICE, sp("COCOON-LIP-05", THU_DUC), 1)).isInstanceOf(BadRequestException.class);
        // unknown / missing StoreProduct
        assertThatThrownBy(() -> add(ALICE, -1L, 1)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> add(ALICE, null, 1)).isInstanceOf(BadRequestException.class);

        assertThat(rowsOf(ALICE)).isEmpty();
    }

    @Test
    void sellabilityFollowsTheCatalogConvention() {
        Long serumAtThuDuc = sp(SERUM, THU_DUC);

        // Product HIDDEN
        productRepository.findBySku(SERUM).orElseThrow().setStatus(ProductStatus.HIDDEN);
        sync();
        assertThatThrownBy(() -> add(ALICE, serumAtThuDuc, 1)).isInstanceOf(BadRequestException.class);
        productRepository.findBySku(SERUM).orElseThrow().setStatus(ProductStatus.ACTIVE);

        // Category HIDDEN
        productRepository.findBySku(SERUM).orElseThrow().getCategory().setStatus(VisibilityStatus.HIDDEN);
        sync();
        assertThatThrownBy(() -> add(ALICE, serumAtThuDuc, 1)).isInstanceOf(BadRequestException.class);
        productRepository.findBySku(SERUM).orElseThrow().getCategory().setStatus(VisibilityStatus.ACTIVE);

        // Brand HIDDEN
        productRepository.findBySku(SERUM).orElseThrow().getBrand().setStatus(VisibilityStatus.HIDDEN);
        sync();
        assertThatThrownBy(() -> add(ALICE, serumAtThuDuc, 1)).isInstanceOf(BadRequestException.class);
        productRepository.findBySku(SERUM).orElseThrow().getBrand().setStatus(VisibilityStatus.ACTIVE);
        sync();

        add(ALICE, serumAtThuDuc, 1);
        assertThat(rowsOf(ALICE)).hasSize(1);
    }

    @Test
    void quantityMustBeAtLeastOne() {
        Long serumAtThuDuc = sp(SERUM, THU_DUC);

        for (Integer quantity : new Integer[]{0, -1, null}) {
            assertThatThrownBy(() -> cartService.addItem(ALICE, new AddCartItemRequest(serumAtThuDuc, quantity)))
                    .isInstanceOf(BadRequestException.class);
        }
        assertThat(rowsOf(ALICE)).isEmpty();
    }

    @Test
    void theCartIsCreatedOnTheFirstAddAndThereIsOnlyEverOne() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Khách Mới");
        request.setEmail("phase7.new@example.com");
        request.setPassword("Phase7@Test");
        Long userId = authService.register(request).id();
        sync();
        long carts = cartRepository.count();

        // looking at the cart does not create one
        assertThat(cartService.getCart("phase7.new@example.com").isEmpty()).isTrue();
        assertThat(cartRepository.count()).isEqualTo(carts);

        add("phase7.new@example.com", sp(SERUM, THU_DUC), 1);
        add("phase7.new@example.com", sp(SERUM, GO_VAP), 1);

        assertThat(cartRepository.count()).isEqualTo(carts + 1);
        Cart cart = cartRepository.findByUserId(userId).orElseThrow();
        assertThat(cartItemRepository.countByCartId(cart.getId())).isEqualTo(2);

        // emptying the cart keeps the (empty) cart
        rowsOf("phase7.new@example.com").forEach(item ->
                cartService.removeItem("phase7.new@example.com", item.getId()));
        sync();
        assertThat(cartRepository.findByUserId(userId)).isPresent();
        assertThat(cartService.getCart("phase7.new@example.com").isEmpty()).isTrue();
    }

    // ================================================================= the cart never touches stock or orders

    @Test
    void cartOperationsNeverChangeStockOrCreateOrdersOrMovements() {
        StoreProduct serum = storeProduct(SERUM, THU_DUC);
        int stock = serum.getQuantity();
        long movements = movementRepository.count();
        long orders = orderRepository.count();
        long checkouts = checkoutSessionRepository.count();

        add(ALICE, serum.getId(), 5);
        Long lineId = rowsOf(ALICE).get(0).getId();
        cartService.updateItemQuantity(ALICE, lineId, 9);
        sync();
        cartService.getSelection(ALICE, List.of(lineId));
        cartService.removeItem(ALICE, lineId);
        sync();

        assertThat(storeProductRepository.findById(serum.getId()).orElseThrow().getQuantity()).isEqualTo(stock);
        assertThat(movementRepository.count()).isEqualTo(movements);
        assertThat(orderRepository.count()).isEqualTo(orders);
        assertThat(checkoutSessionRepository.count()).isEqualTo(checkouts);
    }

    // ================================================================= update quantity

    @Test
    void updateSetsTheQuantityAfterCheckingStock() {
        StoreProduct cheek = storeProduct(CHEEK_08, THU_DUC);     // seed stock 8
        add(ALICE, cheek.getId(), 2);
        Long lineId = rowsOf(ALICE).get(0).getId();

        cartService.updateItemQuantity(ALICE, lineId, 8);
        sync();
        assertThat(rowsOf(ALICE)).singleElement().extracting(CartItem::getQuantity).isEqualTo(8);

        assertThatThrownBy(() -> cartService.updateItemQuantity(ALICE, lineId, 9))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("không đủ hàng");
        for (int invalid : new int[]{0, -3}) {
            assertThatThrownBy(() -> cartService.updateItemQuantity(ALICE, lineId, invalid))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("tối thiểu là 1");
        }
        sync();
        assertThat(rowsOf(ALICE)).singleElement().extracting(CartItem::getQuantity).isEqualTo(8);

        cartService.updateItemQuantity(ALICE, lineId, 1);
        sync();
        assertThat(rowsOf(ALICE)).singleElement().extracting(CartItem::getQuantity).isEqualTo(1);
    }

    @Test
    void updateReadsTheLatestStockNotTheOneThePageWasRenderedWith() {
        StoreProduct cheek = storeProduct(CHEEK_08, THU_DUC);
        add(ALICE, cheek.getId(), 2);
        Long lineId = rowsOf(ALICE).get(0).getId();

        // meanwhile the Store sells down to 3
        storeProductRepository.findById(cheek.getId()).orElseThrow().setQuantity(3);
        sync();

        assertThatThrownBy(() -> cartService.updateItemQuantity(ALICE, lineId, 5))
                .isInstanceOf(BadRequestException.class);
        cartService.updateItemQuantity(ALICE, lineId, 3);
        sync();
        assertThat(rowsOf(ALICE)).singleElement().extracting(CartItem::getQuantity).isEqualTo(3);
        // the update reserved nothing
        assertThat(storeProductRepository.findById(cheek.getId()).orElseThrow().getQuantity()).isEqualTo(3);
    }

    // ================================================================= remove

    @Test
    void removeDeletesOnlyThatLine() {
        add(ALICE, sp(SERUM, THU_DUC), 1);
        add(ALICE, sp(SERUM, GO_VAP), 1);
        Long first = rowsOf(ALICE).get(0).getId();

        cartService.removeItem(ALICE, first);
        sync();

        assertThat(rowsOf(ALICE)).singleElement()
                .extracting(item -> item.getStoreProduct().getId()).isEqualTo(sp(SERUM, GO_VAP));
        assertThatThrownBy(() -> cartService.removeItem(ALICE, first)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================= ownership

    @Test
    void aCustomerCannotTouchAnotherCustomersLines() {
        Map<Long, String> bobBefore = snapshot(BOB);
        Long bobsLine = bobBefore.keySet().iterator().next();
        add(ALICE, sp(SERUM, THU_DUC), 1);

        assertThatThrownBy(() -> cartService.updateItemQuantity(ALICE, bobsLine, 1))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> cartService.removeItem(ALICE, bobsLine))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> cartService.getSelection(ALICE, List.of(bobsLine)))
                .isInstanceOf(BadRequestException.class);
        // mixing an own line with a foreign one is refused as a whole
        Long alicesLine = rowsOf(ALICE).get(0).getId();
        assertThatThrownBy(() -> cartService.getSelection(ALICE, List.of(alicesLine, bobsLine)))
                .isInstanceOf(BadRequestException.class);
        // an account without a cart, or an unknown one
        assertThatThrownBy(() -> cartService.removeItem("admin@oneshop.vn", bobsLine))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> cartService.updateItemQuantity("nobody@example.com", bobsLine, 1))
                .isInstanceOf(ResourceNotFoundException.class);

        sync();
        assertThat(snapshot(BOB)).isEqualTo(bobBefore);
        // and Alice's cart never shows Bob's lines
        assertThat(lines(cartService.getCart(ALICE))).extracting(CartItemResponse::cartItemId)
                .doesNotContainAnyElementsOf(bobBefore.keySet());
    }

    // ================================================================= selected Store (BR-16)

    @Test
    void changingTheSelectedStoreNeverChangesTheCart() {
        Map<Long, String> before = snapshot(BOB);

        for (String code : List.of(THU_DUC, GO_VAP, QUAN_7, QUAN_10)) {
            assertThat(storeService.findSelectableStore(storeRepository.findByCode(code).orElseThrow().getId())).isPresent();
            assertThat(cartService.getCart(BOB).groups()).hasSize(3);
        }
        sync();

        assertThat(snapshot(BOB)).isEqualTo(before);
    }

    // ================================================================= data changing under a cart line

    @Test
    void priceShownIsTheCurrentStoreProductPriceNotASnapshot() {
        StoreProduct serum = storeProduct(SERUM, THU_DUC);
        add(ALICE, serum.getId(), 2);
        assertThat(line(ALICE, serum.getId()).subtotal()).isEqualByComparingTo("258000");

        storeProductRepository.findById(serum.getId()).orElseThrow().setPrice(new BigDecimal("150000"));
        sync();

        CartItemResponse item = line(ALICE, serum.getId());
        assertThat(item.unitPrice()).isEqualByComparingTo("150000");
        assertThat(item.subtotal()).isEqualByComparingTo("300000");
        assertThat(cartService.getCart(ALICE).total()).isEqualByComparingTo("300000");
        // cart_items has no price column at all
        Number priceColumns = (Number) entityManager.createNativeQuery("""
                select count(*) from INFORMATION_SCHEMA.COLUMNS
                where TABLE_NAME = 'cart_items' and (COLUMN_NAME like '%price%' or COLUMN_NAME like '%total%'
                      or COLUMN_NAME like '%selected%')""").getSingleResult();
        assertThat(priceColumns.intValue()).isZero();
    }

    @Test
    void stockDroppingBelowTheCartQuantityIsFlaggedButTheLineIsNotChanged() {
        StoreProduct cheek = storeProduct(CHEEK_08, THU_DUC);
        add(ALICE, cheek.getId(), 6);
        add(ALICE, sp(SERUM, THU_DUC), 1);
        assertThat(line(ALICE, cheek.getId()).status()).isEqualTo(CartItemStatus.AVAILABLE);

        storeProductRepository.findById(cheek.getId()).orElseThrow().setQuantity(4);
        sync();

        CartItemResponse item = line(ALICE, cheek.getId());
        assertThat(item.status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);
        assertThat(item.selectable()).isFalse();
        assertThat(item.quantity()).as("not silently lowered").isEqualTo(6);
        assertThat(rowsOf(ALICE)).hasSize(2);
        // not counted in the totals, cannot be handed to checkout
        assertThat(cartService.getCart(ALICE).total()).isEqualByComparingTo("129000");
        assertThatThrownBy(() -> cartService.getSelection(ALICE, List.of(item.cartItemId())))
                .isInstanceOf(BadRequestException.class);

        storeProductRepository.findById(cheek.getId()).orElseThrow().setQuantity(0);
        sync();
        assertThat(line(ALICE, cheek.getId()).status()).isEqualTo(CartItemStatus.OUT_OF_STOCK);
        assertThat(line(ALICE, cheek.getId()).quantity()).isEqualTo(6);
    }

    @Test
    void lineWhoseStoreProductIsNoLongerOnSaleStaysButCanOnlyBeRemoved() {
        StoreProduct serum = storeProduct(SERUM, THU_DUC);
        add(ALICE, serum.getId(), 2);
        Long lineId = rowsOf(ALICE).get(0).getId();

        // StoreProduct switched off
        storeProductRepository.findById(serum.getId()).orElseThrow().setStatus(ActiveStatus.INACTIVE);
        sync();
        assertThat(line(ALICE, serum.getId()).status()).isEqualTo(CartItemStatus.UNAVAILABLE);
        assertThat(rowsOf(ALICE)).as("not removed automatically").hasSize(1);
        assertThatThrownBy(() -> cartService.updateItemQuantity(ALICE, lineId, 1))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("không còn bán");
        assertThatThrownBy(() -> cartService.getSelection(ALICE, List.of(lineId))).isInstanceOf(BadRequestException.class);
        storeProductRepository.findById(serum.getId()).orElseThrow().setStatus(ActiveStatus.ACTIVE);

        // Store closed: the group is marked, the line is not moved to another Store
        storeRepository.findByCode(THU_DUC).orElseThrow().setStatus(ActiveStatus.INACTIVE);
        sync();
        CartStoreGroupResponse group = cartService.getCart(ALICE).groups().get(0);
        assertThat(group.storeName()).isEqualTo("OneShop Thủ Đức");
        assertThat(group.storeActive()).isFalse();
        assertThat(group.hasSelectableItems()).isFalse();
        assertThat(group.items()).singleElement().extracting(CartItemResponse::storeProductId).isEqualTo(serum.getId());
        storeRepository.findByCode(THU_DUC).orElseThrow().setStatus(ActiveStatus.ACTIVE);

        // Product retired
        productRepository.findBySku(SERUM).orElseThrow().setStatus(ProductStatus.INACTIVE);
        sync();
        assertThat(line(ALICE, serum.getId()).status()).isEqualTo(CartItemStatus.UNAVAILABLE);

        // it can still be removed
        cartService.removeItem(ALICE, lineId);
        sync();
        assertThat(rowsOf(ALICE)).isEmpty();
    }

    // ================================================================= hand-over to checkout

    @Test
    void selectionReturnsExactlyTheChosenLinesGroupedByStore() {
        List<Long> bobsLines = cartService.getCart(BOB).cartItemIds();
        assertThat(bobsLines).hasSize(3);

        CartResponse two = cartService.getSelection(BOB, List.of(bobsLines.get(0), bobsLines.get(2), bobsLines.get(0)));
        assertThat(two.itemCount()).isEqualTo(2);
        assertThat(two.cartItemIds()).containsExactly(bobsLines.get(0), bobsLines.get(2));
        assertThat(two.groups()).hasSize(2);
        assertThat(two.total()).isEqualByComparingTo(two.groups().get(0).subtotal().add(two.groups().get(1).subtotal()));

        assertThat(cartService.getSelection(BOB, bobsLines).groups()).hasSize(3);

        assertThatThrownBy(() -> cartService.getSelection(BOB, List.of())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> cartService.getSelection(BOB, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> cartService.getSelection(BOB, List.of(-1L))).isInstanceOf(BadRequestException.class);
    }

    // ================================================================= what the client can and cannot send / see

    @Test
    void cartRequestsCarryNoUserPriceOrStoreAndCartViewsCarryNoStock() {
        assertThat(componentNames(AddCartItemRequest.class)).containsExactlyInAnyOrder("storeProductId", "quantity");
        assertThat(componentNames(UpdateCartItemRequest.class)).containsExactly("quantity");

        for (Class<?> view : List.of(CartItemResponse.class, CartStoreGroupResponse.class, CartResponse.class)) {
            assertThat(componentNames(view)).as(view.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().contains("stock") || name.toLowerCase().contains("available"));
        }
        // "quantity" in a cart line is the customer's own quantity, not the Store's
        assertThat(componentNames(CartItemResponse.class)).contains("quantity");
    }

    private static List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    // ================================================================= constraints of cart_items (database level)

    @Test
    void databaseRejectsASecondLineForTheSameStoreProductInOneCart() {
        add(ALICE, sp(SERUM, THU_DUC), 1);
        CartItem existing = rowsOf(ALICE).get(0);

        CartItem duplicate = new CartItem();
        duplicate.setCart(existing.getCart());
        duplicate.setStoreProduct(existing.getStoreProduct());
        duplicate.setQuantity(1);

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UQ_cart_items_cart_store_product");
    }

    @Test
    void databaseRejectsANonPositiveQuantity() {
        add(ALICE, sp(SERUM, THU_DUC), 1);
        CartItem item = rowsOf(ALICE).get(0);

        item.setQuantity(0);

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_cart_items_quantity");
    }
}
