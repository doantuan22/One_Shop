package com.oneshop.service;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Cart;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.Category;
import com.oneshop.entity.Brand;
import com.oneshop.entity.Product;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.Store;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceV3Test {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private StoreProductRepository storeProductRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductService productService;

    private CartService cartService;

    private User customer;
    private Cart cart;
    private Product productSerum;
    private Store storeThuDuc;
    private Store storeGoVap;
    private StoreProduct spThuDuc;
    private StoreProduct spGoVap;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        cartService = new CartServiceImpl(
                cartRepository,
                cartItemRepository,
                storeProductRepository,
                productRepository,
                userRepository,
                productService,
                tm
        );

        customer = new User();
        ReflectionTestUtils.setField(customer, "id", 100L);
        customer.setEmail("customer@oneshop.vn");

        cart = new Cart();
        ReflectionTestUtils.setField(cart, "id", 200L);
        cart.setUser(customer);

        Category cat = new Category();
        cat.setStatus(VisibilityStatus.ACTIVE);
        Brand brand = new Brand();
        brand.setStatus(VisibilityStatus.ACTIVE);

        productSerum = new Product();
        ReflectionTestUtils.setField(productSerum, "id", 1L);
        productSerum.setSku("COCOON-SERUM-30");
        productSerum.setName("Cocoon Serum 30ml");
        productSerum.setPrice(new BigDecimal("129000"));
        productSerum.setOriginalPrice(new BigDecimal("149000"));
        productSerum.setStatus(ProductStatus.ACTIVE);
        productSerum.setCategory(cat);
        productSerum.setBrand(brand);

        storeThuDuc = new Store();
        ReflectionTestUtils.setField(storeThuDuc, "id", 10L);
        storeThuDuc.setName("OneShop Thủ Đức");
        storeThuDuc.setStatus(ActiveStatus.ACTIVE);

        storeGoVap = new Store();
        ReflectionTestUtils.setField(storeGoVap, "id", 20L);
        storeGoVap.setName("OneShop Gò Vấp");
        storeGoVap.setStatus(ActiveStatus.ACTIVE);

        // Store 1: quantity 10, reserved 2 -> available 8
        spThuDuc = new StoreProduct();
        ReflectionTestUtils.setField(spThuDuc, "id", 101L);
        spThuDuc.setStore(storeThuDuc);
        spThuDuc.setProduct(productSerum);
        spThuDuc.setQuantity(10);
        ReflectionTestUtils.setField(spThuDuc, "reservedQuantity", 2);
        spThuDuc.setStatus(ActiveStatus.ACTIVE);

        // Store 2: quantity 5, reserved 0 -> available 5
        spGoVap = new StoreProduct();
        ReflectionTestUtils.setField(spGoVap, "id", 102L);
        spGoVap.setStore(storeGoVap);
        spGoVap.setProduct(productSerum);
        spGoVap.setQuantity(5);
        ReflectionTestUtils.setField(spGoVap, "reservedQuantity", 0);
        spGoVap.setStatus(ActiveStatus.ACTIVE);
    }

    @Test
    @DisplayName("addItem with productId checks chain-wide stock and saves CartItem with Product")
    void addItem_withProductId_successfullyAddsToCart() {
        when(productRepository.findVisibleById(1L)).thenReturn(Optional.of(productSerum));
        when(storeProductRepository.findSellableByProductIds(List.of(1L))).thenReturn(List.of(spThuDuc, spGoVap));
        when(userRepository.findByEmail("customer@oneshop.vn")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserIdForUpdate(100L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(200L, 1L)).thenReturn(Optional.empty());

        cartService.addItem("customer@oneshop.vn", AddCartItemRequest.forProduct(1L, 3));

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).saveAndFlush(captor.capture());
        CartItem saved = captor.getValue();
        assertThat(saved.getProduct()).isEqualTo(productSerum);
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getCart()).isEqualTo(cart);
    }

    @Test
    @DisplayName("addItem sums quantity when product already exists in central cart")
    void addItem_sumsQuantity_whenProductAlreadyInCart() {
        CartItem existing = new CartItem();
        ReflectionTestUtils.setField(existing, "id", 55L);
        existing.setCart(cart);
        existing.setProduct(productSerum);
        existing.setQuantity(2);

        when(productRepository.findVisibleById(1L)).thenReturn(Optional.of(productSerum));
        when(storeProductRepository.findSellableByProductIds(List.of(1L))).thenReturn(List.of(spThuDuc, spGoVap));
        when(userRepository.findByEmail("customer@oneshop.vn")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserIdForUpdate(100L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(200L, 1L)).thenReturn(Optional.of(existing));

        // existing 2 + new 4 = 6 <= chain stock 13
        cartService.addItem("customer@oneshop.vn", AddCartItemRequest.forProduct(1L, 4));

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("addItem rejects when quantity exceeds chain-wide available stock (quantity - reserved)")
    void addItem_rejectsWhenExceedsChainStock() {
        // Chain stock = (10 - 2) + (5 - 0) = 13
        when(productRepository.findVisibleById(1L)).thenReturn(Optional.of(productSerum));
        when(storeProductRepository.findSellableByProductIds(List.of(1L))).thenReturn(List.of(spThuDuc, spGoVap));
        when(userRepository.findByEmail("customer@oneshop.vn")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserIdForUpdate(100L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(200L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem("customer@oneshop.vn", AddCartItemRequest.forProduct(1L, 14)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không đủ hàng");
    }

    @Test
    @DisplayName("updateItemQuantity updates successfully within chain-wide stock")
    void updateItemQuantity_successWithinChainStock() {
        CartItem item = new CartItem();
        ReflectionTestUtils.setField(item, "id", 77L);
        item.setCart(cart);
        item.setProduct(productSerum);
        item.setQuantity(2);

        when(userRepository.findByEmail("customer@oneshop.vn")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserIdForUpdate(100L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(77L, 200L)).thenReturn(Optional.of(item));
        when(productRepository.findVisibleById(1L)).thenReturn(Optional.of(productSerum));
        when(storeProductRepository.findSellableByProductIds(List.of(1L))).thenReturn(List.of(spThuDuc, spGoVap));

        cartService.updateItemQuantity("customer@oneshop.vn", 77L, 10);

        assertThat(item.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("updateItemQuantity rejects when quantity exceeds chain-wide stock")
    void updateItemQuantity_rejectsWhenExceedsStock() {
        CartItem item = new CartItem();
        ReflectionTestUtils.setField(item, "id", 77L);
        item.setCart(cart);
        item.setProduct(productSerum);
        item.setQuantity(2);

        when(userRepository.findByEmail("customer@oneshop.vn")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserIdForUpdate(100L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(77L, 200L)).thenReturn(Optional.of(item));
        when(productRepository.findVisibleById(1L)).thenReturn(Optional.of(productSerum));
        when(storeProductRepository.findSellableByProductIds(List.of(1L))).thenReturn(List.of(spThuDuc, spGoVap));

        assertThatThrownBy(() -> cartService.updateItemQuantity("customer@oneshop.vn", 77L, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không đủ hàng");
    }

    @Test
    @DisplayName("getCart returns flat items list and calculates totals using Product.price")
    void getCart_returnsFlatItemsListAndCalculatesTotals() {
        CartItem item = new CartItem();
        ReflectionTestUtils.setField(item, "id", 11L);
        item.setCart(cart);
        item.setProduct(productSerum);
        item.setQuantity(2);

        when(cartRepository.findByUserEmail("customer@oneshop.vn")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(200L)).thenReturn(List.of(item));
        when(productService.getPrimaryImageUrls(List.of(1L))).thenReturn(Map.of(1L, "https://example.com/serum.jpg"));
        when(storeProductRepository.findSellableByProductIds(List.of(1L))).thenReturn(List.of(spThuDuc, spGoVap));

        CartResponse response = cartService.getCart("customer@oneshop.vn");

        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);

        CartItemResponse itemRes = response.items().get(0);
        assertThat(itemRes.cartItemId()).isEqualTo(11L);
        assertThat(itemRes.productId()).isEqualTo(1L);
        assertThat(itemRes.sku()).isEqualTo("COCOON-SERUM-30");
        assertThat(itemRes.unitPrice()).isEqualByComparingTo("129000");
        assertThat(itemRes.originalPrice()).isEqualByComparingTo("149000");
        assertThat(itemRes.subtotal()).isEqualByComparingTo("258000");
        assertThat(itemRes.status()).isEqualTo(CartItemStatus.AVAILABLE);

        assertThat(response.total()).isEqualByComparingTo("258000");
    }

    @Test
    @DisplayName("getCart sets OUT_OF_STOCK and INSUFFICIENT_STOCK correctly based on chain stock")
    void getCart_setsCorrectStatuses() {
        CartItem item1 = new CartItem();
        ReflectionTestUtils.setField(item1, "id", 11L);
        item1.setCart(cart);
        item1.setProduct(productSerum);
        item1.setQuantity(20); // exceeds chain stock 13 -> INSUFFICIENT_STOCK

        Product outOfStockProduct = new Product();
        ReflectionTestUtils.setField(outOfStockProduct, "id", 2L);
        outOfStockProduct.setSku("OUT-OF-STOCK");
        outOfStockProduct.setName("Sản phẩm hết hàng");
        outOfStockProduct.setPrice(new BigDecimal("50000"));
        outOfStockProduct.setStatus(ProductStatus.ACTIVE);
        outOfStockProduct.setCategory(productSerum.getCategory());
        outOfStockProduct.setBrand(productSerum.getBrand());

        CartItem item2 = new CartItem();
        ReflectionTestUtils.setField(item2, "id", 12L);
        item2.setCart(cart);
        item2.setProduct(outOfStockProduct);
        item2.setQuantity(1); // 0 stock in chain -> OUT_OF_STOCK

        when(cartRepository.findByUserEmail("customer@oneshop.vn")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(200L)).thenReturn(List.of(item1, item2));
        when(productService.getPrimaryImageUrls(List.of(1L, 2L))).thenReturn(Map.of());
        when(storeProductRepository.findSellableByProductIds(List.of(1L, 2L))).thenReturn(List.of(spThuDuc, spGoVap));

        CartResponse response = cartService.getCart("customer@oneshop.vn");

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);
        assertThat(response.items().get(1).status()).isEqualTo(CartItemStatus.OUT_OF_STOCK);
        // Non-available items are not added to total
        assertThat(response.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

