# ONESHOP V3 — HANDOVER DOCUMENT: PHASE V3-BE-02 TO V3-BE-03
## Core Catalog & Central Cart -> Checkout, Order Allocation & Fulfillment

**Phase Completed:** V3-BE-02 (Catalog & Central Cart Refactoring)  
**Next Phase:** V3-BE-03 (Checkout, Order Allocation & Fulfillment Engine)  
**Date:** October 2026  
**Status:** READY FOR HANDOVER  

---

## 1. Handover Objectives & Baseline Established

Phase **V3-BE-02** establishes a stable upstream boundary for Phase **V3-BE-03**. The catalog browsing and central cart experience now fully conform to the **OneShop V3 (Phương án B)** business architecture:

1. **Catalog Single Source of Truth:**
   - Online selling price is always `Product.price`.
   - `StoreProduct.price` is no longer used for customer pricing calculations.
   - Catalog browsing and detail pages do not require or force store branch selection.

2. **Centralized Cart State:**
   - Cart lines (`CartItem`) are stored with `product_id NOT NULL`.
   - Cart items represent SKUs across the whole chain.
   - Multiple adds of the same SKU are combined into a single cart line with cumulative quantity.
   - `CartResponse.items()` provides a flat list of `CartItemResponse` objects with accurate line statuses (`AVAILABLE`, `INSUFFICIENT_STOCK`, `OUT_OF_STOCK`, `UNAVAILABLE`) based on chain-wide active inventory `(quantity - reservedQuantity)`.

3. **Database & Schema Integrity:**
   - 22/22 tables validated with `spring.jpa.hibernate.ddl-auto=validate`.
   - Zero DDL alterations or database resets performed during BE-02.

---

## 2. Key Contracts Provided for Phase V3-BE-03

Phase V3-BE-03 can directly rely on the following contracts and services:

### 2.1. Cart Selection API
```java
CartResponse CartService.getSelection(String customerEmail, Collection<Long> cartItemIds);
```
- **Behavior:** Validates that all requested `cartItemIds` belong to the authenticated customer, and checks that every selected line is currently `selectable() == true` (`AVAILABLE` status with sufficient chain-wide stock).
- **Return Value:** Returns a `CartResponse` containing:
  - `items()`: Flat list of selected `CartItemResponse`.
  - `itemCount()`: Total count of selected items.
  - `total()`: Accurate monetary total calculated as `sum(product.price * quantity)`.

### 2.2. Cart Item Structure
Each `CartItemResponse` provides:
- `cartItemId()`: Unique ID of the cart line.
- `productId()`: ID of the underlying product (`Product`).
- `sku()`: SKU code of the product.
- `productName()`: Product display name.
- `unitPrice()`: `Product.price` (unified online price).
- `quantity()`: Selected quantity.
- `subtotal()`: `unitPrice * quantity`.
- `status()`: Availability status enum (`CartItemStatus`).
- `selectable()`: Boolean indicating whether this item can proceed to checkout.

### 2.3. Aggregate Chain Inventory
```java
List<StoreProduct> StoreProductRepository.findSellableByProductIds(Collection<Long> productIds);
```
- Returns all `StoreProduct` records for the given products that belong to `ACTIVE` stores and have `ACTIVE` status.
- Each `StoreProduct` provides `getQuantity()` and `getReservedQuantity()`.
- Effective available quantity per store = `Math.max(0, sp.getQuantity() - sp.getReservedQuantity())`.

---

## 3. Scope & Responsibilities of Phase V3-BE-03

When implementing Phase V3-BE-03, the engineer should implement:

1. **Checkout Flow (`CheckoutService`):**
   - Create checkout session from `cart.getSelection()`.
   - Support shipping methods:
     * **Home Delivery (Giao hàng tận nơi):** Customer provides shipping address.
     * **Store Pickup (Nhận tại cửa hàng):** Customer selects a branch where all selected items have sufficient available stock (`inStockStoreCount`).
2. **Order Allocation Engine (`OrderAllocationService`):**
   - Implement the smart allocation algorithm defined in `OneShop_V3_DacTaNghiepVu_MaTranChuyenDoiCSDL.md`:
     * **Kịch bản A (Single-Store):** 1 chi nhánh có đủ toàn bộ giỏ hàng -> Phân bổ 1 `OrderFulfillment` duy nhất tại chi nhánh đó.
     * **Kịch bản B (Multi-Store Coordinate):** Không có chi nhánh nào đủ toàn bộ -> Phân bổ theo số lượng chi nhánh tối thiểu (ưu tiên chi nhánh có nhiều món nhất). Mỗi chi nhánh nhận 1 `OrderFulfillment`.
     * **Kịch bản C (Cross-Store Transfer / Store Pickup):** Đối với đơn nhận tại cửa hàng, gom hàng về chi nhánh nhận hoặc chỉ cho phép chọn chi nhánh có đủ tồn kho.
3. **Inventory Reservation (`InventoryReservationService`):**
   - At order creation: increment `StoreProduct.reserved_quantity += allocatedQuantity`.
   - Record `InventoryMovement` with type `RESERVE`.
   - At fulfillment packing/shipping: decrement `quantity -= allocatedQuantity` and decrement `reserved_quantity -= allocatedQuantity`, record `OUTBOUND`.
   - At cancellation: decrement `reserved_quantity -= allocatedQuantity`, record `RELEASE`.
4. **Order & Fulfillment Creation:**
   - Create `Order` record with V3 statuses: `NEW`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`.
   - Create `OrderFulfillment` records linked to `orders` and `stores`.
   - Create `OrderItem` records with `fulfillment_id NOT NULL`.
   - Record `OrderFulfillmentStatusHistory`.

---

## 4. Key Pre-Flight Checks & Warnings for Phase V3-BE-03

> [!IMPORTANT]
> **Check Constraint `CK_orders_order_status`:** Ensure all created orders use valid V3 statuses (`NEW`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`). Do not use obsolete V2 status strings like `PENDING` or `READY_FOR_PICKUP` on `orders`.

> [!IMPORTANT]
> **Foreign Key `order_items.fulfillment_id`:** In V3 schema, `order_items.fulfillment_id` is `NOT NULL`. Every `OrderItem` must be associated with an `OrderFulfillment` at creation time.

> [!NOTE]
> **Pessimistic Locking:** Ensure `StoreProductRepository.findAllByIdForUpdate` or `findByIdForUpdate` is used during order placement to acquire `WITH (UPDLOCK, HOLDLOCK, ROWLOCK)` on SQL Server, preventing race conditions during inventory reservation.

---

## 5. Artifact Checklist

- [x] Implementation Report: [`V3_BE02_IMPLEMENTATION_REPORT.md`](file:///d:/One_Shop/docs/v3/backend-catalog-cart/V3_BE02_IMPLEMENTATION_REPORT.md)
- [x] Component Change Matrix: [`V3_BE02_COMPONENT_CHANGE_MATRIX.md`](file:///d:/One_Shop/docs/v3/backend-catalog-cart/V3_BE02_COMPONENT_CHANGE_MATRIX.md)
- [x] Test Results & Evidence: [`V3_BE02_TEST_RESULTS.md`](file:///d:/One_Shop/docs/v3/backend-catalog-cart/V3_BE02_TEST_RESULTS.md)
- [x] Handover Specification: [`V3_BE02_HANDOVER.md`](file:///d:/One_Shop/docs/v3/backend-catalog-cart/V3_BE02_HANDOVER.md)
- [x] All 15 Integration Tests in `CentralCartIntegrationTest`: **PASS**
- [x] All 13 Unit Tests in `CartServiceV3Test` & `ProductServiceV3Test`: **PASS**
- [x] All Web Tests in `CartWebIntegrationTest`: **PASS**

