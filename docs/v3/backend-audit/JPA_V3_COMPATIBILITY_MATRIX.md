> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# ONESHOP V3 — JPA COMPATIBILITY MATRIX
**Phase:** V3-BE-01: Backend Audit & JPA Compatibility  
**Target Schema:** Microsoft SQL Server (22 Tables, Centralized Commerce Model)  
**Hibernate DDL Validation:** `spring.jpa.hibernate.ddl-auto=validate` (PASSED)  

---

## 1. Overview & Verification Summary

This document provides a field-by-field, constraint-by-constraint mapping matrix between the **22 SQL Server V3 tables** and the Spring Data JPA entity layer. Following the Phase V3-BE-01 updates, all 22 tables are fully mapped, all relationships and cascades are correctly declared, and Hibernate successfully validates the schema against the live SQL Server instance without errors or warnings.

---

## 2. Master 22-Table JPA Mapping Matrix

| # | SQL Server V3 Table | JPA Entity Class | Primary Key | Foreign Keys (FKs) | Unique & Check Constraints | Hibernate Status |
|---|---------------------|------------------|-------------|--------------------|----------------------------|------------------|
| 1 | `roles` | [`Role`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Role.java) | `role_id` (IDENTITY) | None | `UQ_roles_name`, `CK_roles_name` | VALIDATED |
| 2 | `users` | [`User`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/User.java) | `user_id` (IDENTITY) | `role_id` -> `roles` | `UQ_users_email`, `CK_users_status` | VALIDATED |
| 3 | `customer_addresses` | [`CustomerAddress`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/CustomerAddress.java) | `address_id` (IDENTITY) | `user_id` -> `users` | None | VALIDATED |
| 4 | `stores` | [`Store`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Store.java) | `store_id` (IDENTITY) | None | `UQ_stores_code`, `CK_stores_status` | VALIDATED |
| 5 | `staff_store_assignments` | [`StaffStoreAssignment`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/StaffStoreAssignment.java) | `assignment_id` (IDENTITY) | `user_id` -> `users`, `store_id` -> `stores` | `UQ_ssa_user_store`, `CK_ssa_status` | VALIDATED |
| 6 | `categories` | [`Category`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Category.java) | `category_id` (IDENTITY) | None | `UQ_categories_name`, `CK_categories_status` | VALIDATED |
| 7 | `brands` | [`Brand`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Brand.java) | `brand_id` (IDENTITY) | None | `UQ_brands_name`, `CK_brands_status` | VALIDATED |
| 8 | `products` | [`Product`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Product.java) | `product_id` (IDENTITY) | `category_id` -> `categories`, `brand_id` -> `brands` | `UQ_products_sku`, `CK_products_status`, `CK_products_price`, `CK_products_original_price` | VALIDATED |
| 9 | `product_images` | [`ProductImage`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/ProductImage.java) | `image_id` (IDENTITY) | `product_id` -> `products` | None | VALIDATED |
| 10 | `store_products` | [`StoreProduct`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/StoreProduct.java) | `store_product_id` (IDENTITY) | `store_id` -> `stores`, `product_id` -> `products` | `UQ_store_products_store_product`, `CK_store_products_quantity`, `CK_store_products_reserved`, `CK_store_products_status` | VALIDATED |
| 11 | `carts` | [`Cart`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Cart.java) | `cart_id` (IDENTITY) | `user_id` -> `users` | `UQ_carts_user` | VALIDATED |
| 12 | `cart_items` | [`CartItem`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/CartItem.java) | `cart_item_id` (IDENTITY) | `cart_id` -> `carts`, `product_id` -> `products`, `store_product_id` -> `store_products` (nullable) | `UQ_cart_items_cart_product`, `CK_cart_items_quantity` | VALIDATED |
| 13 | `checkout_sessions` | [`CheckoutSession`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/CheckoutSession.java) | `checkout_id` (IDENTITY) | `user_id` -> `users` | `CK_checkout_sessions_total`, `CK_checkout_sessions_status` | VALIDATED |
| 14 | `orders` | [`Order`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Order.java) | `order_id` (IDENTITY) | `checkout_id` -> `checkout_sessions`, `user_id` -> `users`, `store_id` -> `stores` (nullable) | `CK_orders_fulfillment_type`, `CK_orders_payment_method`, `CK_orders_payment_status`, `CK_orders_order_status`, `CK_orders_total`, `CK_orders_shipping_fee` | VALIDATED |
| 15 | `order_fulfillments` | [`OrderFulfillment`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/OrderFulfillment.java) | `fulfillment_id` (IDENTITY) | `order_id` -> `orders`, `store_id` -> `stores` | `UQ_order_fulfillments_order_store`, `CK_order_fulfillments_type`, `CK_order_fulfillments_status` | VALIDATED |
| 16 | `order_fulfillment_status_history` | [`OrderFulfillmentStatusHistory`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/OrderFulfillmentStatusHistory.java) | `history_id` (IDENTITY) | `fulfillment_id` -> `order_fulfillments`, `changed_by_user_id` -> `users` (nullable) | `CK_ofsh_old`, `CK_ofsh_new` | VALIDATED |
| 17 | `order_items` | [`OrderItem`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/OrderItem.java) | `order_item_id` (IDENTITY) | `order_id` -> `orders`, `fulfillment_id` -> `order_fulfillments`, `store_product_id` -> `store_products` | `CK_order_items_quantity`, `CK_order_items_unit_price`, `CK_order_items_subtotal` | VALIDATED |
| 18 | `payments` | [`Payment`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Payment.java) | `payment_id` (IDENTITY) | `order_id` -> `orders` | `CK_payments_method`, `CK_payments_status`, `CK_payments_amount`, `CK_payments_paid_at`, `CK_payments_refund_status` | VALIDATED |
| 19 | `inventory_movements` | [`InventoryMovement`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/InventoryMovement.java) | `movement_id` (IDENTITY) | `store_product_id` -> `store_products`, `reference_order_id` -> `orders`, `staff_id` -> `users` | `CK_inventory_movements_type`, `CK_inventory_movements_math`, `CK_inventory_movements_by_type` | VALIDATED |
| 20 | `order_status_history` | [`OrderStatusHistory`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/OrderStatusHistory.java) | `history_id` (IDENTITY) | `order_id` -> `orders`, `changed_by_user_id` -> `users` | `CK_order_status_history_old`, `CK_order_status_history_new` | VALIDATED |
| 21 | `reviews` | [`Review`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/Review.java) | `review_id` (IDENTITY) | `user_id` -> `users`, `product_id` -> `products`, `order_item_id` -> `order_items` (nullable), `order_id` -> `orders` (legacy), `replied_by` -> `users` (legacy) | `UQ_reviews_user_product`, `CK_reviews_rating`, `CK_reviews_status`, `CK_reviews_reply` | VALIDATED |
| 22 | `review_replies` | [`ReviewReply`](file:///d:/One_Shop/src/main/java/com/oneshop/entity/ReviewReply.java) | `reply_id` (IDENTITY) | `review_id` -> `reviews`, `staff_user_id` -> `users` | `UQ_review_replies_review` | VALIDATED |

---

## 3. Detailed Structural Changes by Entity in Phase V3-BE-01

### 3.1. Entity: `Product` (`dbo.products`)
* **SQL Columns Added in V3:**
  - `price DECIMAL(18,2) NOT NULL DEFAULT 0`
  - `original_price DECIMAL(18,2) NULL`
* **JPA Mappings Implemented:**
  ```java
  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal price = BigDecimal.ZERO;

  @Column(name = "original_price", precision = 18, scale = 2)
  private BigDecimal originalPrice;
  ```
* **Rationale:** In V3 Centralized Commerce (V3-DEC-02), the master selling price is unified across the chain and anchored on `Product`, rather than varying per store branch.

---

### 3.2. Entity: `StoreProduct` (`dbo.store_products`)
* **SQL Columns Modified in V3:**
  - `price DECIMAL(18,2) NULL` (made nullable for legacy backward compatibility)
  - `reserved_quantity INT NOT NULL DEFAULT 0` (added)
* **JPA Mappings Implemented:**
  ```java
  @Column(precision = 18, scale = 2)
  private BigDecimal price;

  @Column(name = "reserved_quantity", nullable = false)
  private int reservedQuantity = 0;

  public int getAvailableQuantity() {
      return Math.max(0, quantity - reservedQuantity);
  }
  ```
* **Rationale:** In V3 (V3-DEC-03), `StoreProduct` represents physical store inventory. Stock reservation during order placement uses `reserved_quantity` to avoid double-selling before physical order packing.

---

### 3.3. Entity: `CartItem` (`dbo.cart_items`)
* **SQL Columns Modified in V3:**
  - `product_id BIGINT NOT NULL` (FK -> `products`, added)
  - `store_product_id BIGINT NULL` (made nullable for legacy backward compatibility)
  - Unique Constraint updated from `(cart_id, store_product_id)` to `(cart_id, product_id)`.
* **JPA Mappings Implemented:**
  ```java
  @Table(name = "cart_items",
          uniqueConstraints = @UniqueConstraint(name = "UQ_cart_items_cart_product",
                  columnNames = {"cart_id", "product_id"}))
  public class CartItem extends TimestampedEntity {
      @ManyToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "product_id", nullable = false)
      private Product product;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "store_product_id")
      private StoreProduct storeProduct;
      ...
  }
  ```
* **Backward Compatibility Hook:**
  ```java
  public void setStoreProduct(StoreProduct storeProduct) {
      this.storeProduct = storeProduct;
      if (storeProduct != null && this.product == null) {
          this.product = storeProduct.getProduct();
      }
  }
  ```
* **Rationale:** In V3 Centralized Cart (V3-DEC-04), customers add products directly to their basket without choosing a branch upfront. The cart line references `Product`.

---

### 3.4. Entity: `Order` (`dbo.orders`)
* **SQL Columns Modified in V3:**
  - `store_id BIGINT NULL` (made nullable; online multi-store orders have no single store)
  - `shipping_fee DECIMAL(18,2) NOT NULL DEFAULT 0` (added)
* **JPA Mappings Implemented:**
  ```java
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id")
  private Store store;

  @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2)
  private BigDecimal shippingFee = BigDecimal.ZERO;

  @OneToMany(mappedBy = "order")
  private List<OrderFulfillment> fulfillments = new ArrayList<>();
  ```
* **Rationale:** An online order in V3 may be fulfilled by 1 or more stores. `store_id` is null when fulfillment is distributed across multiple stores, and populated for `STORE_PICKUP` or single-store local fulfillment.

---

### 3.5. Entity: `OrderFulfillment` (`dbo.order_fulfillments`) — NEW
* **JPA Definition:**
  ```java
  @Entity
  @Table(name = "order_fulfillments",
          uniqueConstraints = @UniqueConstraint(name = "UQ_order_fulfillments_order_store",
                  columnNames = {"order_id", "store_id"}))
  public class OrderFulfillment extends TimestampedEntity {
      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      @Column(name = "fulfillment_id")
      private Long id;

      @ManyToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "order_id", nullable = false)
      private Order order;

      @ManyToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "store_id", nullable = false)
      private Store store;

      @Enumerated(EnumType.STRING)
      @Column(name = "fulfillment_type", nullable = false, length = 20)
      private FulfillmentType fulfillmentType = FulfillmentType.DELIVERY;

      @Enumerated(EnumType.STRING)
      @Column(name = "status", nullable = false, length = 30)
      private OrderFulfillmentStatus status = OrderFulfillmentStatus.CONFIRMED;

      @Column(name = "tracking_code", length = 50)
      private String trackingCode;

      @Column(name = "pickup_code", length = 20)
      private String pickupCode;

      @Column(name = "assigned_at", nullable = false)
      private LocalDateTime assignedAt = LocalDateTime.now();
      ...
  }
  ```
* **Rationale:** Represents the dispatch package allocated to an individual store branch. Branch staff operations target this entity.

---

### 3.6. Entity: `OrderFulfillmentStatusHistory` (`dbo.order_fulfillment_status_history`) — NEW
* **JPA Definition:**
  ```java
  @Entity
  @Table(name = "order_fulfillment_status_history")
  @EntityListeners(AuditingEntityListener.class)
  public class OrderFulfillmentStatusHistory {
      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      @Column(name = "history_id")
      private Long id;

      @ManyToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "fulfillment_id", nullable = false)
      private OrderFulfillment fulfillment;

      @Enumerated(EnumType.STRING)
      @Column(name = "old_status", length = 30)
      private OrderFulfillmentStatus oldStatus;

      @Enumerated(EnumType.STRING)
      @Column(name = "new_status", nullable = false, length = 30)
      private OrderFulfillmentStatus newStatus;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "changed_by_user_id")
      private User changedBy;

      @Column(length = 500)
      private String note;

      @CreatedDate
      @Column(name = "changed_at", nullable = false, updatable = false)
      private LocalDateTime changedAt;
  }
  ```
* **Rationale:** Provides an immutable audit trail for every status transition of a branch fulfillment package.

---

### 3.7. Entity: `OrderItem` (`dbo.order_items`)
* **SQL Columns Added in V3:**
  - `fulfillment_id BIGINT NOT NULL` (FK -> `order_fulfillments`)
* **JPA Mappings Implemented:**
  ```java
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fulfillment_id", nullable = false)
  private OrderFulfillment fulfillment;
  ```
* **Rationale:** Each order item line belongs to a specific fulfillment package so the assigned store knows exactly what SKU and quantity to pack.

---

### 3.8. Entity: `Payment` (`dbo.payments`)
* **SQL Columns Added in V3:**
  - `refund_status NVARCHAR(30) NOT NULL DEFAULT 'NONE'`
  - `refund_amount DECIMAL(18,2) NULL`
  - `refund_requested_at DATETIME2(0) NULL`
  - `refunded_at DATETIME2(0) NULL`
  - `refund_reason NVARCHAR(500) NULL`
* **JPA Mappings Implemented:**
  ```java
  @Enumerated(EnumType.STRING)
  @Column(name = "refund_status", nullable = false, length = 30)
  private PaymentRefundStatus refundStatus = PaymentRefundStatus.NONE;

  @Column(name = "refund_amount", precision = 18, scale = 2)
  private BigDecimal refundAmount;

  @Column(name = "refund_requested_at")
  private LocalDateTime refundRequestedAt;

  @Column(name = "refunded_at")
  private LocalDateTime refundedAt;

  @Column(name = "refund_reason", length = 500)
  private String refundReason;
  ```
* **Rationale:** Enables simulated refund workflows for cancelled online orders or out-of-stock items in V3.

---

### 3.9. Entity: `Review` (`dbo.reviews`) & `ReviewReply` (`dbo.review_replies`)
* **SQL Columns Modified in `reviews`:**
  - `order_item_id BIGINT NULL` (FK -> `order_items`)
* **JPA Mappings in `Review.java`:**
  ```java
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_item_id")
  private OrderItem orderItem;

  @OneToOne(mappedBy = "review", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private ReviewReply reviewReply;
  ```
* **JPA Mappings in `ReviewReply.java` (NEW):**
  ```java
  @Entity
  @Table(name = "review_replies",
          uniqueConstraints = @UniqueConstraint(name = "UQ_review_replies_review", columnNames = "review_id"))
  public class ReviewReply extends TimestampedEntity {
      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      @Column(name = "reply_id")
      private Long id;

      @OneToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "review_id", nullable = false, unique = true)
      private Review review;

      @ManyToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "staff_user_id", nullable = false)
      private User staff;

      @Column(nullable = false, length = 1000)
      private String content;
  }
  ```
* **Rationale:** Replaces embedded text column replies with a normalized 1--1 table structure tracking the staff responder. Ties customer review eligibility directly to a purchased `order_item_id`.

---

## 4. Verification Checkpoint

1. **Compilation Check:** `mvn compile` and `mvn test-compile` succeeded with 0 errors.
2. **Schema Validation:** Executed Spring Boot integration test with `spring.jpa.hibernate.ddl-auto=validate`. All 22 entities validated successfully against Microsoft SQL Server without mismatch.
3. **Data Integrity Test:** Read operations via `findAll()` and specific criteria queries against all 22 tables confirmed successful object hydration and enum mapping across all 304 seed records.

