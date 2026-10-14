package com.oneshop;

import com.oneshop.exception.PaymentConflictException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.SimulatedResult;
import com.oneshop.service.payment.PaymentTimeoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static com.oneshop.FulfillmentFixtures.ALICE;
import static com.oneshop.FulfillmentFixtures.SERUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The production-like configuration of the mock gateway ({@code oneshop.payment.mock-gateway-enabled=false}): no
 * simulated result can be produced by anybody, while the deadline rules and the timeout keep working.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890",
        "oneshop.checkout.online-enabled=true",
        "oneshop.payment.mock-gateway-enabled=false"})
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class PaymentGatewayDisabledDatabaseIntegrationTest {

    @Autowired
    PaymentReconciliationService reconciliation;
    @Autowired
    PaymentTimeoutService timeouts;
    @Autowired
    AdminPaymentService adminPayments;
    @Autowired
    CartService cartService;
    @Autowired
    CheckoutService checkoutService;
    @Autowired
    CartRepository cartRepository;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    StoreProductRepository storeProductRepository;
    @Autowired
    JdbcTemplate jdbc;

    private SeedGuardV3 guard;
    private FulfillmentFixtures data;
    private boolean remembered;

    @BeforeEach
    void remember() {
        guard = new SeedGuardV3(jdbc);
        assumeTrue(guard.isTestDatabase(), "these tests commit: run with DB_NAME=oneshop_test");
        guard.remember();
        remembered = true;
        data = new FulfillmentFixtures(jdbc, cartService, checkoutService, cartRepository, cartItemRepository,
                productRepository, storeRepository, storeProductRepository);
    }

    @AfterEach
    void restore() {
        if (remembered) {
            guard.restore();
        }
    }

    @Test
    @DisplayName("BE05-58 with the gateway off nobody can settle a payment; the timeout still cancels")
    void be05_58_gatewayDisabled() {
        long order = data.onlinePickup("OS-THUDUC", SERUM, 2);
        long attempt = data.attemptId(order);

        for (SimulatedResult scenario : SimulatedResult.values()) {
            assertThatThrownBy(() -> reconciliation.simulate(ALICE, order, attempt, scenario))
                    .isInstanceOfSatisfying(PaymentConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(PaymentConflictException.GATEWAY_DISABLED));
        }
        assertThatThrownBy(() -> data.asRole("admin@oneshop.vn", "ROLE_ADMIN",
                () -> adminPayments.settleGateway(order, true))).isInstanceOf(PaymentConflictException.class);
        assertThat(data.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(data.payments(order).get(0).get("paid_at")).isNull();
        assertThat(paymentPageSimulation(order)).isFalse();

        data.ageOrder(order, 16);
        timeouts.scan();

        assertThat(data.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(data.cancelRestores(order)).isEqualTo(1);
    }

    private boolean paymentPageSimulation(long order) {
        return data.asRole(ALICE, "ROLE_CUSTOMER", () -> checkoutPaymentCanSimulate(order));
    }

    @Autowired
    com.oneshop.service.PaymentService paymentService;

    private boolean checkoutPaymentCanSimulate(long order) {
        return paymentService.getOrderPayments(ALICE, order).canSimulate();
    }
}
