package com.oneshop;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.ProductCardResponse;
import com.oneshop.dto.response.ProductDetailResponse;
import com.oneshop.dto.response.ProductResponse;
import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Cart;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.Product;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.Store;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreProductService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase V3-BE-02 Integration Tests: Central Cart & Core Catalog Refactoring.
 * Covers all 15 test scenarios (BE02-01 to BE02-15) against SQL Server.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class CentralCartIntegrationTest {

    private static final String ALICE = "khachhang1@example.com";
    private static final String BOB = "khachhang2@example.com";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StoreProductService storeProductService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    private Product serum;
    private Product toner;
    private Store thuDuc;
    private Store goVap;

    @BeforeEach
    void setUp() {
        cartRepository.findByUserEmail(ALICE).ifPresent(c -> {
            cartItemRepository.deleteAll(cartItemRepository.findByCartId(c.getId()));
        });
        cartRepository.findByUserEmail(BOB).ifPresent(c -> {
            cartItemRepository.deleteAll(cartItemRepository.findByCartId(c.getId()));
        });
        sync();

        List<Product> availableProducts = productRepository.findAll().stream()
                .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .filter(p -> {
                    List<StoreProduct> sellable = storeProductRepository.findSellableByProductIds(List.of(p.getId()));
                    int totalStock = sellable.stream().mapToInt(sp -> Math.max(0, sp.getQuantity() - sp.getReservedQuantity())).sum();
                    return totalStock >= 5;
                })
                .toList();

        serum = availableProducts.get(0);
        toner = availableProducts.get(1);

        List<Store> activeStores = storeRepository.findByStatusOrderByProvinceCityAscAreaAscNameAsc(ActiveStatus.ACTIVE);
        thuDuc = activeStores.get(0);
        goVap = activeStores.size() > 1 ? activeStores.get(1) : activeStores.get(0);
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------
    // BE02-01: Catalog whole-chain browsing uses Product.price
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-01: Whole-chain catalog items use Product.price as single selling price")
    void be02_01_wholeChainCatalogUsesProductPrice() {
        Page<CatalogItemResponse> catalog = storeProductService.getChainCatalog(ProductSearchCriteria.none(), PageRequest.of(0, 10));
        assertThat(catalog.getContent()).isNotEmpty();

        for (CatalogItemResponse item : catalog.getContent()) {
            assertThat(item.product().price()).isNotNull();
            assertThat(item.lowestPrice()).isEqualByComparingTo(item.product().price());
            assertThat(item.samePriceEverywhere()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // BE02-02: ProductCard has fromPrice = false, price = Product.price
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-02: ProductCard sets fromPrice = false and price = Product.price")
    void be02_02_productCardHasUnifiedPriceAndNoFromPrice() {
        Page<CatalogItemResponse> catalog = storeProductService.getChainCatalog(ProductSearchCriteria.none(), PageRequest.of(0, 5));
        CatalogItemResponse firstItem = catalog.getContent().get(0);

        ProductCardResponse card = ProductCardResponse.from(firstItem);
        assertThat(card.price()).isEqualByComparingTo(firstItem.product().price());
        assertThat(card.fromPrice()).isFalse();
    }

    // ------------------------------------------------------------------
    // BE02-03: View product detail without store context shows Product.price
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-03: Product detail without store selection displays Product.price")
    void be02_03_productDetailWithoutStoreShowsProductPrice() {
        ProductDetailResponse detail = storeProductService.getProductDetail(serum.getId(), null);

        assertThat(detail.product().price()).isNotNull();
        assertThat(detail.product().price()).isEqualByComparingTo(serum.getPrice());
        assertThat(detail.atSelectedStore()).isNull();
        assertThat(detail.otherStores()).isNotEmpty();
    }

    // ------------------------------------------------------------------
    // BE02-04: ProductDetail availability computed across all active stores
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-04: Product detail availability computed across chain active stores")
    void be02_04_productDetailAvailabilityAcrossChain() {
        ProductDetailResponse detail = storeProductService.getProductDetail(serum.getId(), null);

        boolean expectedInStock = detail.otherStores().stream().anyMatch(StoreAvailabilityResponse::inStock);
        assertThat(detail.inStock()).isEqualTo(expectedInStock);
        assertThat(detail.inStockStoreCount()).isEqualTo((int) detail.otherStores().stream().filter(StoreAvailabilityResponse::inStock).count());
    }

    // ------------------------------------------------------------------
    // BE02-05: Add to cart by productId directly
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-05: Customer adds item to cart directly by productId")
    void be02_05_addToCartByProductIdDirectly() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1));
        sync();

        CartResponse cart = cartService.getCart(ALICE);
        assertThat(cart.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(serum.getId());
            assertThat(item.quantity()).isEqualTo(1);
            assertThat(item.unitPrice()).isEqualByComparingTo(serum.getPrice());
        });
    }

    // ------------------------------------------------------------------
    // BE02-06: Add to cart by storeProductId maps to product and works
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-06: Legacy add by storeProductId resolves product and succeeds")
    void be02_06_addToCartByStoreProductIdLegacyBridge() {
        StoreProduct sp = storeProductRepository.findByProductIdAndStatus(serum.getId(), ActiveStatus.ACTIVE).stream()
                .filter(s -> (s.getQuantity() - s.getReservedQuantity()) > 0)
                .findFirst().orElseThrow();

        cartService.addItem(ALICE, new AddCartItemRequest(sp.getId(), 1));
        sync();

        CartResponse cart = cartService.getCart(ALICE);
        assertThat(cart.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(serum.getId());
            assertThat(item.quantity()).isEqualTo(1);
        });
    }

    // ------------------------------------------------------------------
    // BE02-07: CartItem row in DB has product_id NOT NULL
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-07: Saved CartItem entity has product_id NOT NULL in database")
    void be02_07_cartItemHasNonNullProductId() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 2));
        sync();

        Cart userCart = cartRepository.findByUserEmail(ALICE).orElseThrow();
        List<CartItem> items = cartItemRepository.findByCartIdOrderByIdAsc(userCart.getId());

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getProduct()).isNotNull();
            assertThat(item.getProduct().getId()).isEqualTo(serum.getId());
        });
    }

    // ------------------------------------------------------------------
    // BE02-08: Adding same product twice sums quantity into single CartItem
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-08: Adding same product twice merges into single line with summed quantity")
    void be02_08_addingSameProductTwiceSumsQuantity() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1));
        sync();
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 2));
        sync();

        Cart userCart = cartRepository.findByUserEmail(ALICE).orElseThrow();
        List<CartItem> items = cartItemRepository.findByCartIdOrderByIdAsc(userCart.getId());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // BE02-09: CartResponse returns flat items list
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-09: CartResponse provides flat items list without requiring store grouping")
    void be02_09_cartResponseReturnsFlatItemsList() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1));
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(toner.getId(), 1));
        sync();

        CartResponse cart = cartService.getCart(ALICE);
        assertThat(cart.itemCount()).isEqualTo(2);
        assertThat(cart.items()).hasSize(2);
        assertThat(cart.items()).extracting(CartItemResponse::productId)
                .containsExactlyInAnyOrder(serum.getId(), toner.getId());
    }

    // ------------------------------------------------------------------
    // BE02-10: Cart total calculated using Product.price * quantity
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-10: Cart subtotals and total calculated from Product.price")
    void be02_10_cartTotalCalculatedFromProductPrice() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 2));
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(toner.getId(), 3));
        sync();

        CartResponse cart = cartService.getCart(ALICE);

        BigDecimal expectedSerumSubtotal = serum.getPrice().multiply(BigDecimal.valueOf(2));
        BigDecimal expectedTonerSubtotal = toner.getPrice().multiply(BigDecimal.valueOf(3));
        BigDecimal expectedTotal = expectedSerumSubtotal.add(expectedTonerSubtotal);

        CartItemResponse itemSerum = cart.items().stream()
                .filter(i -> i.productId().equals(serum.getId())).findFirst().orElseThrow();
        CartItemResponse itemToner = cart.items().stream()
                .filter(i -> i.productId().equals(toner.getId())).findFirst().orElseThrow();

        assertThat(itemSerum.subtotal()).isEqualByComparingTo(expectedSerumSubtotal);
        assertThat(itemToner.subtotal()).isEqualByComparingTo(expectedTonerSubtotal);
        assertThat(cart.total()).isEqualByComparingTo(expectedTotal);
    }

    // ------------------------------------------------------------------
    // BE02-11: Chain stock check uses (quantity - reservedQuantity)
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-11: Stock availability computation respects reserved_quantity")
    void be02_11_stockCheckUsesQuantityMinusReserved() {
        int totalAvailable = storeProductRepository.findSellableByProductIds(List.of(serum.getId())).stream()
                .mapToInt(sp -> Math.max(0, sp.getQuantity() - sp.getReservedQuantity()))
                .sum();

        // Adding totalAvailable should succeed
        if (totalAvailable > 0) {
            cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), totalAvailable));
            sync();
            assertThat(cartService.getCart(ALICE).items().get(0).quantity()).isEqualTo(totalAvailable);

            // Adding 1 more should fail
            assertThatThrownBy(() -> cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không đủ hàng");
        }
    }

    // ------------------------------------------------------------------
    // BE02-12: CartItem status is OUT_OF_STOCK when chain stock is 0
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-12: CartItem status is OUT_OF_STOCK when total chain available stock is 0")
    void be02_12_cartItemStatusOutOfStock() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1));
        sync();

        // Simulate zero stock across all stores
        List<StoreProduct> sps = storeProductRepository.findByProductIdAndStatus(serum.getId(), ActiveStatus.ACTIVE);
        for (StoreProduct sp : sps) {
            sp.setQuantity(0);
            sp.setReservedQuantity(0);
            storeProductRepository.save(sp);
        }
        sync();

        CartResponse cart = cartService.getCart(ALICE);
        assertThat(cart.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(CartItemStatus.OUT_OF_STOCK);
            assertThat(item.selectable()).isFalse();
        });
    }

    // ------------------------------------------------------------------
    // BE02-13: CartItem status is INSUFFICIENT_STOCK when quantity > chain stock
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-13: CartItem status is INSUFFICIENT_STOCK when quantity exceeds available stock")
    void be02_13_cartItemStatusInsufficientStock() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1));
        sync();

        // Reduce stock in DB to 0 or less than in cart, but still > 0 across chain
        List<StoreProduct> sps = storeProductRepository.findByProductIdAndStatus(serum.getId(), ActiveStatus.ACTIVE);
        for (int i = 0; i < sps.size(); i++) {
            StoreProduct sp = sps.get(i);
            sp.setQuantity(i == 0 ? 1 : 0);
            sp.setReservedQuantity(0);
            storeProductRepository.save(sp);
        }
        // Cart item quantity updated to 5 > 1
        Cart userCart = cartRepository.findByUserEmail(ALICE).orElseThrow();
        CartItem line = cartItemRepository.findByCartIdOrderByIdAsc(userCart.getId()).get(0);
        line.setQuantity(5);
        cartItemRepository.save(line);
        sync();

        CartResponse cart = cartService.getCart(ALICE);
        assertThat(cart.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);
            assertThat(item.selectable()).isFalse();
        });
    }

    // ------------------------------------------------------------------
    // BE02-14: Update quantity works within chain available stock
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-14: updateItemQuantity succeeds within chain stock and rejects when exceeding")
    void be02_14_updateQuantityValidatesChainStock() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1));
        sync();

        Cart userCart = cartRepository.findByUserEmail(ALICE).orElseThrow();
        Long cartItemId = cartItemRepository.findByCartIdOrderByIdAsc(userCart.getId()).get(0).getId();

        cartService.updateItemQuantity(ALICE, cartItemId, 2);
        sync();

        assertThat(cartService.getCart(ALICE).items().get(0).quantity()).isEqualTo(2);

        // Attempt to update to an excessive quantity
        assertThatThrownBy(() -> cartService.updateItemQuantity(ALICE, cartItemId, 99999))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không đủ hàng");
    }

    // ------------------------------------------------------------------
    // BE02-15: Selection for checkout validates selectable items
    // ------------------------------------------------------------------
    @Test
    @DisplayName("BE02-15: getSelection validates items without requiring store-grouping constraints")
    void be02_15_selectionValidatesWithoutStoreGrouping() {
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(serum.getId(), 1));
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(toner.getId(), 1));
        sync();

        Cart userCart = cartRepository.findByUserEmail(ALICE).orElseThrow();
        List<Long> itemIds = cartItemRepository.findByCartIdOrderByIdAsc(userCart.getId()).stream()
                .map(CartItem::getId).toList();

        CartResponse selection = cartService.getSelection(ALICE, itemIds);
        assertThat(selection.itemCount()).isEqualTo(2);
        assertThat(selection.hasSelectableItems()).isTrue();
    }
}
