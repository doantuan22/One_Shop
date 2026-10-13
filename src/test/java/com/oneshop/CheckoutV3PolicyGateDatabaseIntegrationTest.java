package com.oneshop;

import com.oneshop.config.CheckoutProperties;
import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The DEFAULT application configuration (no checkout property set) after the owner's decisions OPEN-01/02/03:
 * DELIVERY is open (national coverage, 30.000 VND per Order), pickup is free, and ONLINE stays CLOSED until BE-05.
 * The {@code UNDECIDED} constants remain a fail-closed switch, verified here by flipping the bean and restoring it.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class CheckoutV3PolicyGateDatabaseIntegrationTest {

    private static final String ALICE = "khachhang1@example.com";
    private static final String ADDRESS = "12 Đường Thử Nghiệm, Quận 1, TP. Hồ Chí Minh";

    @PersistenceContext
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartService cartService;
    @Autowired
    CartRepository cartRepository;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    CheckoutProperties checkoutProperties;

    @BeforeEach
    void onlyOnTheTestDatabase() {
        assumeTrue(new SeedGuardV3(jdbc).isTestDatabase(), "run with DB_NAME=oneshop_test");
    }

    private List<Long> cart(String sku, int quantity) {
        cartRepository.findByUserEmail(ALICE).ifPresent(c -> cartItemRepository.deleteAll(cartItemRepository.findByCartId(c.getId())));
        entityManager.flush();
        entityManager.clear();
        cartService.addItem(ALICE, AddCartItemRequest.forProduct(productRepository.findBySku(sku).orElseThrow().getId(), quantity));
        entityManager.flush();
        entityManager.clear();
        return cartRepository.findByUserEmail(ALICE).map(c -> cartItemRepository.findByCartIdOrderByIdAsc(c.getId()))
                .orElseThrow().stream().map(line -> line.getId()).toList();
    }

    private static PlaceOrderRequest request(List<Long> ids, FulfillmentType type, PaymentMethod method, Long store) {
        return new PlaceOrderRequest(ids, type, method, "Người Nhận", "0912345678",
                type == FulfillmentType.DELIVERY ? ADDRESS : null, store, false, null, null);
    }

    @Test
    @DisplayName("Default: DELIVERY + COD is open, the fee is 30.000 and the preview says so")
    void deliveryIsOpenByDefault() {
        List<Long> ids = cart("COCOON-SERUM-30", 1);

        CheckoutPreviewResponse preview = checkoutService.preview(ALICE, ids, FulfillmentType.DELIVERY, PaymentMethod.COD,
                null, ADDRESS);
        assertThat(preview.deliveryAvailable()).isTrue();
        assertThat(preview.deliveryBlockedReason()).isNull();
        assertThat(preview.shippingFeePerOrder()).isEqualByComparingTo("30000");
        assertThat(preview.plan()).isNotNull();

        CheckoutResultResponse result = checkoutService.placeOrder(ALICE,
                request(ids, FulfillmentType.DELIVERY, PaymentMethod.COD, null));
        assertThat(result.orders()).hasSize(1);
        assertThat(result.orders().get(0).shippingFee()).isEqualByComparingTo("30000");
        assertThat(result.orders().get(0).totalAmount()).isEqualByComparingTo(
                result.orders().get(0).items().get(0).subtotal().add(new BigDecimal("30000")));
    }

    @Test
    @DisplayName("Default: ONLINE is refused until BE-05 (timeout/reconciliation/refund) exists")
    void onlineIsClosedByDefault() {
        List<Long> ids = cart("COCOON-SERUM-30", 1);
        long ordersBefore = jdbc.queryForObject("select count(*) from dbo.orders", Long.class);

        assertThat(checkoutProperties.isOnlineEnabled()).isFalse();
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(ids, FulfillmentType.DELIVERY, PaymentMethod.ONLINE, null)))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_ONLINE_CHECKOUT_DISABLED));
        Long thuDuc = storeRepository.findByCode("OS-THUDUC").orElseThrow().getId();
        assertThatThrownBy(() -> checkoutService.placeOrder(ALICE,
                request(ids, FulfillmentType.STORE_PICKUP, PaymentMethod.ONLINE, thuDuc)))
                .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_ONLINE_CHECKOUT_DISABLED));

        assertThat(jdbc.queryForObject("select count(*) from dbo.orders", Long.class)).isEqualTo(ordersBefore);
    }

    @Test
    @DisplayName("Default: STORE_PICKUP + PAY_AT_STORE works and is free")
    void pickupPayAtStoreIsFree() {
        List<Long> ids = cart("COCOON-SERUM-30", 2);
        Long thuDuc = storeRepository.findByCode("OS-THUDUC").orElseThrow().getId();

        CheckoutResultResponse result = checkoutService.placeOrder(ALICE,
                request(ids, FulfillmentType.STORE_PICKUP, PaymentMethod.PAY_AT_STORE, thuDuc));

        assertThat(result.orders()).hasSize(1);
        assertThat(result.orders().get(0).orderStatus().name()).isEqualTo("CONFIRMED");
        assertThat(result.orders().get(0).fulfillments()).hasSize(1);
        assertThat(result.orders().get(0).shippingFee()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("The UNDECIDED constants are a fail-closed switch: DELIVERY is refused, pickup is not affected")
    void killSwitchClosesDelivery() {
        List<Long> ids = cart("COCOON-SERUM-30", 1);
        CheckoutProperties.DeliveryServiceAreaPolicy area = checkoutProperties.getDeliveryServiceAreaPolicy();
        CheckoutProperties.ShippingFeePolicy feePolicy = checkoutProperties.getShippingFeePolicy();
        long ordersBefore = jdbc.queryForObject("select count(*) from dbo.orders", Long.class);
        try {
            checkoutProperties.setDeliveryServiceAreaPolicy(CheckoutProperties.DeliveryServiceAreaPolicy.UNDECIDED);
            assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(ids, FulfillmentType.DELIVERY, PaymentMethod.COD, null)))
                    .isInstanceOfSatisfying(CheckoutConflictException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(CheckoutConflictException.CODE_DELIVERY_POLICY_PENDING));
            CheckoutPreviewResponse preview = checkoutService.preview(ALICE, ids, FulfillmentType.DELIVERY, null, null, null);
            assertThat(preview.deliveryAvailable()).isFalse();
            assertThat(preview.plan()).isNull();
            assertThat(preview.pickupStores()).isNotEmpty();

            checkoutProperties.setDeliveryServiceAreaPolicy(area);
            checkoutProperties.setShippingFeePolicy(CheckoutProperties.ShippingFeePolicy.UNDECIDED);
            assertThatThrownBy(() -> checkoutService.placeOrder(ALICE, request(ids, FulfillmentType.DELIVERY, PaymentMethod.COD, null)))
                    .isInstanceOf(CheckoutConflictException.class);
        } finally {
            checkoutProperties.setDeliveryServiceAreaPolicy(area);
            checkoutProperties.setShippingFeePolicy(feePolicy);
        }
        assertThat(jdbc.queryForObject("select count(*) from dbo.orders", Long.class)).isEqualTo(ordersBefore);
    }
}
