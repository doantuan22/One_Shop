# ONESHOP V3 — PHASE V3-BE-02 IMPLEMENTATION REPORT
## Core Catalog & Central Cart Refactoring

**Phase:** V3-BE-02  
**Date:** October 2026  
**Status:** COMPLETED  
**Execution Environment:** OpenJDK 21 | Spring Boot 3.5.x | Microsoft SQL Server 2022 (`localhost:1433`)  
**Hibernate DDL Policy:** `spring.jpa.hibernate.ddl-auto=validate`  

---

## 1. Executive Summary

Phase **V3-BE-02: Core Catalog & Central Cart Refactoring** has been successfully implemented and verified. This phase transitions the core retail buying experience of OneShop from **Roadmap V2 (Phương án A - phân tán theo chi nhánh)** to **OneShop V3 (Phương án B - bán hàng tập trung theo mô hình chuỗi bán lẻ hiện đại)**.

### Key Milestones Achieved:
1. **Single Unified Online Price:** `Product.price` is now the single, authoritative online selling price across the entire chain. Pricing is completely decoupled from individual stores or `StoreProduct.price`.
2. **Browsing Without Mandatory Store Context:** Customers browse categories, search items, and view product details without being required to pre-select a store branch. The hero price is always prominent and consistent.
3. **Product-Based Central Cart:** `CartItem` rows in the database primarily reference `product_id` (mandatory `NOT NULL` foreign key). Adding products to cart uses `productId` directly, while backward compatibility bridges legacy `storeProductId` requests seamlessly.
4. **Unified Flat Cart Display:** Customers view a single consolidated cart (`CartResponse.items()`), with SKU-level items, merged quantities, and total calculations based on `Product.price`. No artificial splitting or grouping by store is required to see items.
5. **Chain-Wide Active Availability:** Stock availability is computed across all active stores using net sellable stock (`quantity - reservedQuantity > 0`). Cart items accurately reflect `AVAILABLE`, `INSUFFICIENT_STOCK`, `OUT_OF_STOCK`, or `UNAVAILABLE`.
6. **Thymeleaf UI Harmonization:** Catalog listing (`product/list.html`), product detail (`product/detail.html`), and cart views (`cart/index.html`) have been synchronized with V3 business rules.
7. **Strict Safety Compliance:** Zero DDL alterations executed against SQL Server; `ddl-auto=validate` passed; no spillover into Phase V3-BE-03 (Checkout / Fulfillment).

---

## 2. Business Architecture Transformation: V2 vs V3

| Business Dimension | Roadmap V2 (Phương án A) | OneShop V3 (Phương án B) — Implemented |
|---|---|---|
| **Online Price Source** | `StoreProduct.price` (price varied per branch) | `Product.price` (chain-wide unified selling price) |
| **Catalog Browsing Price** | "Từ X đ" (range from lowest to highest branch price) | Exact fixed price from `Product.price` with strikethrough `originalPrice` |
| **Store Context Requirement** | Customer had to select branch to see exact price and buy | Browsing & buying works chain-wide without selecting a store |
| **Cart Entity Key** | `CartItem.store_product_id` | `CartItem.product_id` (`store_product_id` optional bridge) |
| **Cart Line Merging** | Same SKU at Store A and Store B = 2 separate lines | Same SKU added anywhere = 1 single merged line with summed quantity |
| **Cart Structure** | Segmented into `CartStoreGroupResponse` per store | Flat list of `CartItemResponse` (`CartResponse.items`) |
| **Availability Logic** | Local to selected branch | Chain-wide aggregate: sum of `(quantity - reservedQuantity)` across active stores |
| **Stock Display on Detail** | Restricted to selected branch | Hero buy button enabled if any store in chain has stock; store stock breakdown visible below |

---

## 3. Detailed Implementation by Layer

### 3.1. DTO Layer
- **`ProductResponse`:** Added `BigDecimal price` and `BigDecimal originalPrice`. Retained 7-arg constructor for backward compatibility.
- **`ProductCardResponse`:** `ProductCardResponse.from(CatalogItemResponse)` sets `price = product.price()`, `fromPrice = false`, and checks chain-wide stock.
- **`CatalogItemResponse`:** `lowestPrice()` returns `product.price()`; `samePriceEverywhere()` always returns `true`.
- **`ProductDetailResponse`:** Added helper methods `inStock()` and `inStockStoreCount()` to check aggregate chain availability.
- **`ProductRequest` & `AdminProductResponse`:** Added unified `price` and `originalPrice` fields with zero-default handling.
- **`AddCartItemRequest`:** Added canonical record constructor with `productId` and `storeProductId`, plus factory helper `AddCartItemRequest.forProduct(Long productId, int quantity)`.
- **`CartItemResponse`:** Added `BigDecimal originalPrice` and maintained backward-compatible constructor.
- **`CartResponse`:** Canonical constructor `(items, itemCount, total, groups)`. Flat items are primary; groups preserved for backward compatibility.

### 3.2. Mapper Layer
- **`ProductMapper`:** Directly maps `product.getPrice()` and `product.getOriginalPrice()` into `ProductResponse`.
- **`CatalogMapper`:** Passes `product.getPrice()` and `product.getOriginalPrice()` when constructing `ProductResponse`.
- **`StoreProductMapper`:** `toAvailability()` uses `product.getPrice()` and checks sellable stock condition: `(quantity - reservedQuantity) > 0` on active stores.

### 3.3. Repository Layer
- **`ProductRepository`:**
  * Updated `findSellableInStockIds` to check `(sp.quantity - sp.reservedQuantity) > 0` on active store products and stores.
- **`StoreProductRepository`:**
  * Updated `findSellableByProductIds` to join `product` and filter `sp.status = ACTIVE` and `s.status = ACTIVE`.
  * Updated `findInStockIdsInStore` to enforce `(quantity - reservedQuantity) > 0`.
- **`CartItemRepository`:**
  * Updated `@EntityGraph` paths to eagerly fetch `{"product", "product.category", "product.brand", "storeProduct", "storeProduct.store"}`.
  * Provided `findByCartIdAndProductId` for central cart item lookup.

### 3.4. Service Layer (`CartServiceImpl` & `ProductServiceImpl`)
- **`CartServiceImpl`:**
  * Injected `ProductRepository` via `@Autowired` constructor.
  * In `addItem` / `doAddItem`:
    - Handles `productId` directly.
    - If legacy `storeProductId` is provided, resolves underlying `product`.
    - Checks aggregate chain stock: `sum(quantity - reservedQuantity)` across active stores.
    - Links `CartItem.setProduct(product)`.
    - Merges quantity if line already exists for customer + product.
  * In `updateItemQuantity`:
    - Validates target quantity against chain-wide available stock.
  * In `toResponse`:
    - Generates flat `List<CartItemResponse>` based on `product.getPrice()`.
    - Calculates statuses: `AVAILABLE`, `INSUFFICIENT_STOCK`, `OUT_OF_STOCK`, `UNAVAILABLE`.
    - Computes cart total from `product.getPrice() * quantity`.
    - Also populates `groups` for legacy UI compatibility if requested.
- **`ProductServiceImpl`:**
  * Saves `product.setPrice()` and `product.setOriginalPrice()` in `createProduct` and `updateProduct`.

### 3.5. Web Controller Layer (`CartController`)
- Updated `add(AddCartItemRequest, ...)`:
  * Validates that either `productId` or `storeProductId` is supplied.
  * Handles flash attributes and redirect smoothly.

### 3.6. Thymeleaf Templates
- **`product/list.html`:** Displays single unified price (`product.price`) with strikethrough `product.originalPrice`. Removed "Từ X đ".
- **`product/detail.html`:**
  * Prominent hero price box always visible without store selection.
  * "Thêm vào giỏ hàng" enabled by `detail.inStock()`, submitting `productId`.
  * Store inventory availability finder retained below for customer transparency.
- **`cart/index.html`:**
  * Centralized cart view iterating over `cart.items`.
  * Universal select-all checkbox.
  * Single consolidated order summary panel computing totals directly from SKU prices.

---

## 4. Verification & Quality Assurance Summary

- **Unit Tests:** 17/17 PASS (`ProductServiceV3Test`: 6/6, `CartServiceV3Test`: 7/7, `CartServiceRetryTest`: 4/4).
- **Integration Tests:** 15/15 PASS in `CentralCartIntegrationTest` (BE02-01 to BE02-15 against real SQL Server).
- **Web Layer Integration Tests:** 5/5 PASS in `CartWebIntegrationTest`.
- **Foundation & JPA Read Integrity Tests:** 15/15 PASS in `DatabaseFoundationIntegrationTest` & `JpaV3ReadIntegrityTest`.
- **Hibernate Schema Validation:** `spring.jpa.hibernate.ddl-auto=validate` fully satisfied.

---

## 5. Conclusion

Phase V3-BE-02 has established a robust, unified catalog and central cart architecture. The foundation is fully validated, completely isolated from future phases, and ready for handover to **Phase V3-BE-03 (Checkout, Stock Allocation & Fulfillment)**.

