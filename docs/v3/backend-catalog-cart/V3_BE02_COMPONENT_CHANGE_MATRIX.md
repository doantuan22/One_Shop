# ONESHOP V3 — COMPONENT CHANGE MATRIX
## Phase V3-BE-02: Core Catalog & Central Cart Refactoring

**Phase:** V3-BE-02  
**Date:** October 2026  
**Scope:** Backend DTOs, Mappers, Repositories, Services, Controllers, Templates & Test Suites  

---

## 1. DTO Components

| Component / File | Type of Change | Changes & Rationale | Backward Compatibility |
|---|---|---|---|
| `ProductResponse.java` | Modified | Added `BigDecimal price`, `BigDecimal originalPrice`. | Preserved original 7-arg constructor delegating with default prices. |
| `ProductCardResponse.java` | Modified | Updated factory `from(CatalogItemResponse)`: sets `price = product.price()`, `fromPrice = false`, and verifies `inStock` across stores. | Unchanged record signature; existing callers receive unified price. |
| `CatalogItemResponse.java` | Modified | `lowestPrice()` returns `product.price()`; `samePriceEverywhere()` returns `true`. | Methods remain available so callers expecting price range get unified price. |
| `ProductDetailResponse.java` | Modified | Added helper methods `inStock()` and `inStockStoreCount()` aggregating active stores. | All existing fields retained; helpers simplify Thymeleaf template logic. |
| `ProductRequest.java` | Modified | Added `BigDecimal price` (default `ZERO`) and `BigDecimal originalPrice` with getters/setters. | Admin product creation/updates can set SKU prices directly. |
| `AdminProductResponse.java` | Modified | Added `BigDecimal price`, `BigDecimal originalPrice`. | Preserved original 9-arg constructor defaulting prices to `ZERO`. |
| `AddCartItemRequest.java` | Modified | Extended record with `(Long productId, Long storeProductId, Integer quantity)`. Added factory `forProduct(Long productId, int quantity)`. | Retained 2-arg constructor `(Long storeProductId, Integer quantity)` as legacy bridge. |
| `CartItemResponse.java` | Modified | Added `BigDecimal originalPrice` to record. | Retained 10-arg constructor setting `originalPrice = null`. |
| `CartResponse.java` | Modified | Canonical constructor `(items, itemCount, total, groups)`. Provided accessor `items()`. | Retained 3-arg constructor `(groups, itemCount, total)` which flattens groups into `items`. |

---

## 2. Mapper Components

| Component / File | Type of Change | Changes & Rationale |
|---|---|---|
| `ProductMapper.java` | Modified | Maps `product.getPrice()` and `product.getOriginalPrice()` into `ProductResponse`. |
| `CatalogMapper.java` | Modified | Maps `product.getPrice()` and `product.getOriginalPrice()` into `ProductResponse` when building catalog cards. |
| `StoreProductMapper.java` | Modified | `toAvailability(...)` uses `product.getPrice()`; computes `inStock` as `(sp.quantity - sp.reservedQuantity) > 0` on active stores. |

---

## 3. Repository Components

| Component / File | Type of Change | Changes & Rationale |
|---|---|---|
| `ProductRepository.java` | Modified | Updated `findSellableInStockIds` query to join active store products and stores with `(sp.quantity - sp.reservedQuantity) > 0`. |
| `StoreProductRepository.java` | Modified | Updated `findSellableByProductIds` to join `product` and filter `sp.status = ACTIVE` and `s.status = ACTIVE`. Updated `findInStockIdsInStore` to use `(quantity - reservedQuantity) > 0`. |
| `CartItemRepository.java` | Modified | Expanded `@EntityGraph(attributePaths = {"product", "product.category", "product.brand", "storeProduct", "storeProduct.store"})` to eliminate N+1 queries during central cart loading. Added `findByCartIdAndProductId`. |

---

## 4. Service Components

| Component / File | Type of Change | Changes & Rationale |
|---|---|---|
| `CartServiceImpl.java` | Refactored | Injected `ProductRepository`. In `addItem` / `doAddItem`: processes `productId` directly, falls back to `storeProductId` bridge, checks chain-wide net sellable stock `(quantity - reservedQuantity)`, sets `CartItem.setProduct(product)`, sums quantities for duplicate SKU adds. In `updateItemQuantity`: validates chain stock. In `toResponse`: creates flat `items` list with `product.getPrice()`, calculates status, calculates total, builds legacy `groups`. |
| `ProductServiceImpl.java` | Modified | Persists `product.setPrice()` and `product.setOriginalPrice()` in `createProduct` and `updateProduct`. |

---

## 5. Web Controller Components

| Component / File | Type of Change | Changes & Rationale |
|---|---|---|
| `CartController.java` | Modified | Validates presence of either `productId` or `storeProductId` in `AddCartItemRequest`. Redirects with clear feedback on error or success. |

---

## 6. Thymeleaf Templates

| Template / Path | Changes Implemented |
|---|---|
| `src/main/resources/templates/product/list.html` | Removed "Từ X đ" label. Renders unified `product.price` with strikethrough `product.originalPrice`. |
| `src/main/resources/templates/product/detail.html` | Hero price box is always displayed prominently without requiring store selection. "Thêm vào giỏ hàng" enabled by `detail.inStock()` and posts `productId`. Branch inventory check is retained below for in-store pickup awareness. |
| `src/main/resources/templates/cart/index.html` | Replaced store-segmented cart headers with a single centralized product table iterating over `cart.items`. Universal select-all checkbox. Consolidated order summary computing subtotal and total from `Product.price`. |

---

## 7. Testing Components

| Test Class / Path | Status | Role & Verification Scope |
|---|---|---|
| `CentralCartIntegrationTest.java` | **NEW** (15/15 PASS) | Integration test suite against real SQL Server covering scenarios BE02-01 to BE02-15. |
| `CartServiceV3Test.java` | **NEW** (7/7 PASS) | Unit tests verifying `CartServiceImpl` logic (add, merge, stock checks, response mapping). |
| `ProductServiceV3Test.java` | **NEW** (6/6 PASS) | Unit tests verifying `ProductServiceImpl` price propagation and validation. |
| `CartServiceRetryTest.java` | Modified (4/4 PASS) | Updated mock associations (`product` on `storeProduct`) to match V3 entity model. |
| `CartWebIntegrationTest.java` | Modified (5/5 PASS) | Updated HTML assertions from 3 store groups to 1 centralized cart section. |
| `CartDatabaseIntegrationTest.java` | Disabled (`@Disabled`) | Legacy Phase 7 V2 test suite based on obsolete store-specific pricing and cart splitting. Superseded by `CentralCartIntegrationTest`. |
| `CartHttpDatabaseIntegrationTest.java` | Disabled (`@Disabled`) | Legacy Phase 7 V2 test suite based on obsolete seed data assumptions. Superseded by `CentralCartIntegrationTest`. |

