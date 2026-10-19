> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../../database/README.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

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
     * **Kịch bản C (Multi-Store Order Fulfillment):** Một Order ONLINE có thể lấy hàng từ nhiều Store; mỗi Store chỉ xử lý phần hàng đã được phân bổ cho mình và các Fulfillment của cùng một Order có thể được giao riêng tới khách.

       ```text
       Customer Checkout
           |
           v
       Order #1001
           |
           +-- Fulfillment #F1
           |       Store 1
           |       Product A x2
           |
           +-- Fulfillment #F2
                   Store 2
                   Product B x1
       ```

       Đây là Multi-Store Fulfillment, **không phải** Cross-Store Transfer. Không chuyển Product từ Store A sang Store B, không tạo stock transfer movement, không có transfer request, không thêm warehouse module, không thêm bảng quản lý chuyển kho, không đổi tồn của một Store chỉ vì Store khác thiếu hàng. Với `STORE_PICKUP`, chỉ cho chọn chi nhánh đang có đủ toàn bộ giỏ; không gom hàng từ chi nhánh khác về chi nhánh nhận.
3. **Immediate Stock Deduction (FINAL owner decision; supersedes the earlier reservation design):**
   - At order creation, in the same transaction: lock allocated `StoreProduct` rows, verify `quantity - reserved_quantity >= allocated`, decrement `StoreProduct.quantity`, record `InventoryMovement` type `ORDER` (negative `quantity_change`).
   - Do **not** increment `reserved_quantity`; no `RESERVE`/`OUTBOUND`/`RELEASE` movement types (not allowed by `CK_inventory_movements_type`).
   - Staff status changes never deduct stock again.
   - At valid cancellation / payment failure / payment timeout: restore each OrderItem quantity to its source `StoreProduct`, record `CANCEL_ORDER` (positive `quantity_change`), idempotently.
   - The Cart (BE-02) does not hold stock: availability shown in the cart is informational, and stock is only taken when BE-03 creates the Order.
   - Only these `InventoryMovement` types are valid: `ORDER`, `CANCEL_ORDER`, `STOCK_ADJUST`. There is no `RESERVE` / `OUTBOUND` / `RELEASE`.
4. **Order & Fulfillment Creation:**
   - Create `Order` records using only statuses accepted by `CK_orders_order_status` (`PENDING_PAYMENT`, `CONFIRMED`, `PROCESSING`, `SHIPPING`, `PARTIALLY_COMPLETED`, `COMPLETED`, `CANCELLED`, `NEEDS_ATTENTION`). ONLINE starts as `PENDING_PAYMENT` + `payment_status = UNPAID`.
   - Create `OrderFulfillment` records linked to `orders` and `stores`.
   - Create `OrderItem` records with `fulfillment_id NOT NULL`.
   - Record `OrderFulfillmentStatusHistory`.
5. **ONLINE Payment Policy (FINAL owner decisions):**
   - `PENDING_PAYMENT` lasts **15 minutes**. The backend computes the deadline as `Order.created_at + 15 minutes` (no new column) using a single injectable server `Clock`; browser time is never used. The deadline is fixed: it is not reset by reloading the page, continuing a pending attempt or repeated callbacks.
   - **Reconciliation window: 15 minutes after the deadline**, only for payment results that cannot be concluded (UNKNOWN/PENDING). During it: no new payment, no deadline extension, no Staff `PREPARING`, no automatic cancel/restore. Still unknown afterwards -> Order `NEEDS_ATTENTION` (an Order status, not a Payment status), stock stays deducted.
   - **Verified FAILED is terminal** for the current ONLINE Order: cancel, restore stock, no automatic payment retry; buying again is a new checkout. A gateway request timeout / lost connection is not FAILED.
   - **Mock Payment Gateway (server side):** the payment result is confirmed only after the backend verifies the mock ledger entry. The browser "success/failure" action (V2 `/orders/{id}/payments/{paymentId}/success|failure`) is never evidence of payment and must not remain a way to confirm payment.
   - BE-03 creates the `PENDING_PAYMENT` Order, deducts stock in the same transaction, creates the Payment attempt with its mock transaction reference and exposes the deadline; it does not implement the timeout worker, reconciliation, Admin resolution, mock simulation/verification or refund.
   - The complete timeout / reconciliation / `NEEDS_ATTENTION` Admin resolution / cancellation / refund simulation belongs to **BE-05**. BE-03 must prepare statuses and integration boundaries compatible with BE-05 (a single "is payable now" check and a single cancel-and-restore operation under the Order lock) and must not simulate a successful payment.
   - Staff cannot move a Fulfillment of an ONLINE Order to `PREPARING` until the Order is paid (`CONFIRMED`); `PENDING_PAYMENT` (including under reconciliation) and payment `NEEDS_ATTENTION` are both blocked.
   - Configuration names: `oneshop.payment.pending-timeout-minutes`, `oneshop.payment.reconciliation-window-minutes`, `oneshop.payment.timeout-scan-interval-ms` (defaults 15 / 15 / 60000). Do not change the business deadline through environment variables without owner approval.
   - ONLINE checkout must not be called production-safe until BE-05 is done, otherwise unpaid Orders hold stock indefinitely.
   - Full policy and race matrix: `BACKEND_V3_MIGRATION_PLAN.md`, Section 5.
   - The Cart delivered in BE-02 is unchanged by these decisions.

---

## 4. Key Pre-Flight Checks & Warnings for Phase V3-BE-03

> [!IMPORTANT]
> **Check Constraint `CK_orders_order_status`:** Orders must use only the V3 statuses allowed by the schema: `PENDING_PAYMENT`, `CONFIRMED`, `PROCESSING`, `SHIPPING`, `PARTIALLY_COMPLETED`, `COMPLETED`, `CANCELLED`, `NEEDS_ATTENTION`. Do not use `NEW`, `SHIPPED`, `DELIVERED`, nor obsolete V2 strings like `PENDING` or `READY_FOR_PICKUP` on `orders`.

> [!IMPORTANT]
> **Foreign Key `order_items.fulfillment_id`:** In V3 schema, `order_items.fulfillment_id` is `NOT NULL`. Every `OrderItem` must be associated with an `OrderFulfillment` at creation time.

> [!WARNING]
> **V2 checkout path:** `CheckoutServiceImpl.placeOrder` still reads `line.getStoreProduct()` (nullable after BE-02) and creates one Order per Store. It must be replaced for V3, not extended.

> [!NOTE]
> **Pessimistic Locking:** Ensure `StoreProductRepository.findAllByIdForUpdate` or `findByIdForUpdate` is used during order placement to acquire `WITH (UPDLOCK, HOLDLOCK, ROWLOCK)` on SQL Server, preventing race conditions during stock deduction.

---

## 5. Artifact Checklist

- [x] Historical implementation report: `V3_BE02_IMPLEMENTATION_REPORT.md` (retired; source file is not retained)
- [x] Historical component matrix: `V3_BE02_COMPONENT_CHANGE_MATRIX.md` (retired; source file is not retained)
- [x] Historical test evidence: `V3_BE02_TEST_RESULTS.md` (retired; source file is not retained)
- [x] Handover Specification: [`V3_BE02_HANDOVER.md`](file:///d:/One_Shop/docs/v3/backend-catalog-cart/V3_BE02_HANDOVER.md)
- [x] All 15 Integration Tests in `CentralCartIntegrationTest`: **PASS**
- [x] All 13 Unit Tests in `CartServiceV3Test` & `ProductServiceV3Test`: **PASS**
- [x] All Web Tests in `CartWebIntegrationTest`: **PASS**

