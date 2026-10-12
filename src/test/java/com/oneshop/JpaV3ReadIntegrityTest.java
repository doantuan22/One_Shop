package com.oneshop;

import com.oneshop.entity.*;
import com.oneshop.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase V3-BE-01 Read-Only JPA Integrity Test.
 * Validates entity mapping, relationships, and seed data across all 22 tables in OneShop V3 schema.
 * Runs with Hibernate 'ddl-auto=validate' on profile 'dev' against real SQL Server database.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("databaseConfigured")
class JpaV3ReadIntegrityTest {

    static boolean databaseConfigured() {
        String username = System.getenv("DB_USERNAME");
        return (username != null && !username.isBlank()) || Files.exists(Path.of(".env"));
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ListableBeanFactory beanFactory;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderFulfillmentRepository orderFulfillmentRepository;

    @Autowired
    private OrderFulfillmentStatusHistoryRepository orderFulfillmentStatusHistoryRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewReplyRepository reviewReplyRepository;

    @Test
    @DisplayName("V3 Metamodel and Repositories count must be exactly 22")
    @Transactional(readOnly = true)
    void all22EntitiesAndRepositoriesExist() {
        assertThat(entityManager.getMetamodel().getEntities())
                .as("All 22 V3 tables must be registered in JPA metamodel")
                .hasSize(22);

        Map<String, JpaRepository> repositories = beanFactory.getBeansOfType(JpaRepository.class);
        assertThat(repositories)
                .as("All 22 tables must have corresponding Spring Data JPA repositories")
                .hasSize(22);

        repositories.forEach((name, repo) -> {
            List<?> all = repo.findAll();
            assertThat(all).as("Repository '%s' findAll() should return non-null list", name).isNotNull();
        });
    }

    @Test
    @DisplayName("Product entity maps price and originalPrice correctly from V3 catalog")
    @Transactional(readOnly = true)
    void productPriceMappingIntegrity() {
        List<Product> products = productRepository.findAll();
        assertThat(products).isNotEmpty();

        assertThat(products).allSatisfy(p -> {
            assertThat(p.getId()).isNotNull();
            assertThat(p.getPrice()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
            if (p.getOriginalPrice() != null) {
                assertThat(p.getOriginalPrice()).isGreaterThanOrEqualTo(p.getPrice());
            }
        });
    }

    @Test
    @DisplayName("StoreProduct maps quantity, reservedQuantity and available stock correctly")
    @Transactional(readOnly = true)
    void storeProductStockMappingIntegrity() {
        List<StoreProduct> storeProducts = storeProductRepository.findAll();
        assertThat(storeProducts).isNotEmpty();

        assertThat(storeProducts).allSatisfy(sp -> {
            assertThat(sp.getId()).isNotNull();
            assertThat(sp.getStore()).isNotNull();
            assertThat(sp.getProduct()).isNotNull();
            assertThat(sp.getQuantity()).isGreaterThanOrEqualTo(0);
            assertThat(sp.getReservedQuantity()).isGreaterThanOrEqualTo(0);
            assertThat(sp.getReservedQuantity()).isLessThanOrEqualTo(sp.getQuantity());
            assertThat(sp.getAvailableQuantity()).isEqualTo(sp.getQuantity() - sp.getReservedQuantity());
        });
    }

    @Test
    @DisplayName("CartItem maps Product FK as primary reference and StoreProduct as legacy")
    @Transactional(readOnly = true)
    void cartItemMappingIntegrity() {
        List<CartItem> cartItems = cartItemRepository.findAll();
        assertThat(cartItems).isNotEmpty();

        assertThat(cartItems).allSatisfy(ci -> {
            assertThat(ci.getId()).isNotNull();
            assertThat(ci.getCart()).isNotNull();
            assertThat(ci.getProduct()).isNotNull();
            assertThat(ci.getQuantity()).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("Order and OrderFulfillment 1--N relationship integrity")
    @Transactional(readOnly = true)
    void orderAndFulfillmentIntegrity() {
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).isNotEmpty();

        List<OrderFulfillment> fulfillments = orderFulfillmentRepository.findAll();
        assertThat(fulfillments).isNotEmpty();

        assertThat(orders).allSatisfy(o -> {
            assertThat(o.getId()).isNotNull();
            assertThat(o.getShippingFee()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(o.getTotalAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(o.getOrderStatus()).isNotNull();
        });

        assertThat(fulfillments).allSatisfy(f -> {
            assertThat(f.getId()).isNotNull();
            assertThat(f.getOrder()).isNotNull();
            assertThat(f.getStore()).isNotNull();
            assertThat(f.getStatus()).isNotNull();
            assertThat(f.getFulfillmentType()).isNotNull();
            assertThat(f.getAssignedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("OrderItem references OrderFulfillment and StoreProduct")
    @Transactional(readOnly = true)
    void orderItemFulfillmentIntegrity() {
        List<OrderItem> orderItems = orderItemRepository.findAll();
        assertThat(orderItems).isNotEmpty();

        assertThat(orderItems).allSatisfy(oi -> {
            assertThat(oi.getId()).isNotNull();
            assertThat(oi.getOrder()).isNotNull();
            assertThat(oi.getFulfillment()).isNotNull();
            assertThat(oi.getStoreProduct()).isNotNull();
            assertThat(oi.getUnitPrice()).isNotNull();
            assertThat(oi.getQuantity()).isGreaterThan(0);
            assertThat(oi.getSubtotal()).isEqualByComparingTo(oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
        });
    }

    @Test
    @DisplayName("Payment refund simulation columns integrity")
    @Transactional(readOnly = true)
    void paymentRefundMappingIntegrity() {
        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).isNotEmpty();

        assertThat(payments).allSatisfy(p -> {
            assertThat(p.getId()).isNotNull();
            assertThat(p.getOrder()).isNotNull();
            assertThat(p.getRefundStatus()).isNotNull();
            if (p.getRefundStatus() != PaymentRefundStatus.NONE) {
                assertThat(p.getRefundAmount()).isNotNull();
            }
        });
    }

    @Test
    @DisplayName("Review links to OrderItem and ReviewReply 1--1 staff response")
    @Transactional(readOnly = true)
    void reviewAndReplyIntegrity() {
        List<Review> reviews = reviewRepository.findAll();
        assertThat(reviews).isNotEmpty();

        List<ReviewReply> replies = reviewReplyRepository.findAll();
        assertThat(replies).isNotEmpty();

        assertThat(replies).allSatisfy(r -> {
            assertThat(r.getId()).isNotNull();
            assertThat(r.getReview()).isNotNull();
            assertThat(r.getStaff()).isNotNull();
            assertThat(r.getContent()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Fulfillment status history audit trail integrity")
    @Transactional(readOnly = true)
    void fulfillmentStatusHistoryIntegrity() {
        List<OrderFulfillmentStatusHistory> histories = orderFulfillmentStatusHistoryRepository.findAll();
        assertThat(histories).isNotEmpty();

        assertThat(histories).allSatisfy(h -> {
            assertThat(h.getId()).isNotNull();
            assertThat(h.getFulfillment()).isNotNull();
            assertThat(h.getNewStatus()).isNotNull();
            assertThat(h.getChangedAt()).isNotNull();
        });
    }
}

