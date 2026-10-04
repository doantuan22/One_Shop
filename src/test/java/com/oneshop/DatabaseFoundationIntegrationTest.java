package com.oneshop;

import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.CheckoutSession;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.StoreProduct;
import com.oneshop.repository.CheckoutSessionRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.RoleRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real SQL Server created by {@code database/*.sql} (Phase 2). Uses the {@code dev} profile, so
 * Hibernate runs with {@code ddl-auto=validate}: the context only starts if all 19 entities match the schema.
 *
 * <p>Skipped unless {@code DB_USERNAME} is set in the environment or a {@code .env} file exists, so the normal build
 * stays runnable without a database. Everything here is read-only.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("databaseConfigured")
class DatabaseFoundationIntegrationTest {

    static boolean databaseConfigured() {
        String username = System.getenv("DB_USERNAME");
        return (username != null && !username.isBlank()) || Files.exists(Path.of(".env"));
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ListableBeanFactory beanFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private StoreService storeService;

    @Autowired
    private ProductService productService;

    @Test
    @Transactional(readOnly = true)
    void all19TablesAreMappedAndEveryRepositoryCanReadItsSeedData() {
        assertThat(entityManager.getMetamodel().getEntities()).hasSize(19);

        Map<String, JpaRepository> repositories = beanFactory.getBeansOfType(JpaRepository.class);
        assertThat(repositories).hasSize(19);
        // findAll converts every column, including all enum columns, so a wrong mapping fails here
        repositories.forEach((name, repository) -> assertThat(repository.findAll()).as(name).isNotNull());
    }

    @Test
    @Transactional(readOnly = true)
    void seedDataIsReadThroughJpa() {
        assertThat(roleRepository.count()).isEqualTo(3);
        assertThat(storeRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(storeService.getActiveStores()).isNotEmpty()
                .allSatisfy(store -> assertThat(store.status()).isEqualTo(ActiveStatus.ACTIVE));
        assertThat(productService.getActiveProducts(PageRequest.of(0, 20)).getContent()).isNotEmpty()
                .allSatisfy(product -> {
                    assertThat(product.sku()).isNotBlank();
                    assertThat(product.categoryName()).isNotBlank();
                    assertThat(product.brandName()).isNotBlank();
                });
    }

    @Test
    @Transactional(readOnly = true)
    void oneSkuIsSoldAtSeveralStoresWithItsOwnPriceAndQuantity() {
        // TC-01 data: a SKU available at 3 Stores
        Map<Long, List<StoreProduct>> bySku = storeProductRepository.findAll().stream()
                .collect(Collectors.groupingBy(sp -> sp.getProduct().getId()));
        Long skuAtThreeStores = bySku.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 3)
                .map(Map.Entry::getKey).findFirst().orElseThrow();

        List<StoreProduct> rows = storeProductRepository.findByProductIdAndStatus(skuAtThreeStores, ActiveStatus.ACTIVE);
        assertThat(rows.stream().map(sp -> sp.getStore().getId()).distinct().count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Transactional(readOnly = true)
    void ordersCheckoutAndPaymentsAreMappedPerOrder() {
        // TC-15 data: one checkout whose Orders have different Stores / payment methods
        List<CheckoutSession> sessions = checkoutSessionRepository.findAll();
        assertThat(sessions).isNotEmpty();
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).isNotEmpty();
        Map<Long, Set<PaymentMethod>> methodsPerCheckout = orders.stream().collect(Collectors.groupingBy(
                o -> o.getCheckoutSession().getId(), Collectors.mapping(Order::getPaymentMethod, Collectors.toSet())));
        assertThat(methodsPerCheckout.values()).anyMatch(methods -> methods.size() > 1);
        assertThat(orders).allSatisfy(order -> assertThat(order.getOrderStatus()).isIn((Object[]) OrderStatus.values()));
    }

    @Test
    void storeProductRowsAreLockedWithPessimisticWrite() throws Exception {
        Long id = storeProductRepository.findAll().get(0).getId();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        AtomicReference<CompletableFuture<Void>> competitor = new AtomicReference<>();

        tx.executeWithoutResult(status -> {
            assertThat(storeProductRepository.findAllByIdForUpdate(List.of(id))).hasSize(1);

            // A second transaction, on another connection, must wait while we hold the row lock.
            competitor.set(CompletableFuture.runAsync(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(inner ->
                            assertThat(storeProductRepository.findByIdForUpdate(id)).isPresent())));
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            assertThat(competitor.get()).as("second transaction is blocked by PESSIMISTIC_WRITE").isNotDone();
        });

        // Once the first transaction has ended, the waiting one gets the row.
        competitor.get().get(10, TimeUnit.SECONDS);
    }
}
