# V3-BE-03 — ALLOCATION & TRANSACTION DESIGN

**Phase:** V3-BE-03 Centralized Checkout & Multi-Branch Order Allocation (final state after OPEN-01/02/03)  
Sources: `OneShop_V3_DacTaNghiepVu_MaTranChuyenDoiCSDL.md` (DEC-01..DEC-25, V3-BR-01..28, V3-T01..T61), `BACKEND_V3_MIGRATION_PLAN.md` (§1, §5), `V3_BE02_HANDOVER.md`, schema `database/02_create_tables.sql`.

---

## 1. Decisions and where they live in the code

| Topic | State | Code |
|---|---|---|
| One price per SKU (`Product.price`) | Decided | `unit_price = Product.price`; `StoreProduct.price` never read by checkout |
| One Store for the whole cart first, then several | Decided (DEC-05) | `AllocationEngine` Strategy 1, then greedy |
| **Same-SKU quantity split between Stores** | **Decided (DEC-23)** | `AllocationEngine` (`mustSplit`), one OrderItem per source |
| ONLINE = 1 Order, N Fulfillments, 1 payment obligation | Decided (DEC-06) | `CheckoutServiceImpl.createOrder` |
| COD one Store = 1 Order; COD several Stores only with consent → independent Orders | Decided (DEC-09/10) | `placeOrder` split branch + `COD_SPLIT_CONSENT_REQUIRED` |
| Pickup: chosen Store holds the whole cart | Decided (DEC-11) | `AllocationEngine.allocateAtStore` |
| **Delivery: every ACTIVE Store with `delivery_enabled`, whole of Vietnam, no GPS** | **Decided (DEC-24)** | `DeliveryServiceAreaPolicy`, candidate queries, `ShippingAddressValidator` |
| **Shipping fee 30.000 VND once per Order, pickup free** | **Decided (DEC-25)** | `ShippingFeeCalculator`, `CheckoutProperties.shippingFeePerOrder` |
| Immediate stock deduction, no reservation | Decided (DEC-16) | `InventoryService.deductForOrder` in the creating transaction |
| 15 minutes to pay from `created_at` | Decided (DEC-15) | `PaymentDeadlinePolicy` |
| No Cross-Store Transfer | Decided (DEC-22) | no transfer code path or movement type |
| ONLINE checkout before BE-05 | Gated by design | `oneshop.checkout.online-enabled=false` |

---

## 2. Allocation algorithm (`AllocationEngine`, pure and deterministic)

### 2.1 Input

- **Demand:** one `AllocationDemand(productId, quantity)` per cart line.
- **Offers:** one `StockOffer(storeProductId, storeId, productId, available)` per eligible StoreProduct, `available = quantity - reserved_quantity` (never negative).

### 2.2 Eligibility (decided before offers are built)

| Rule | Where |
|---|---|
| StoreProduct, Store, Product, Category, Brand all `ACTIVE` | `StoreProductRepository.findCheckoutCandidate*` and re-checked on the locked rows |
| DELIVERY: Store `ACTIVE` and `delivery_enabled` — nothing else (DEC-24) | `StoreAllocationServiceImpl.servesFulfillment` → `DeliveryServiceAreaPolicy.canServe` |
| STORE_PICKUP: Store `pickup_enabled` | same |
| Available quantity | `AllocationEngine` |

`stores.province_city/area` never excludes a Store. A Store in Ha Noi supplies a Ho Chi Minh address (BE03-F13). There is no latitude/longitude, distance or nearest-Store logic anywhere.

### 2.3 Strategy 1 — One-Store Full Cart

A Store qualifies if for every demanded SKU `available >= quantity`. Among qualifying Stores: largest total available quantity of the demanded SKUs, then smallest `store_id`. A split is never made when one Store holds everything.

### 2.4 Strategy 2/3 — greedy cover with same-SKU split

State: `missing` (SKU → units still needed) and `left` (Store → SKU → units still available).

Each round, every Store with `units > 0` is scored and the best is taken:

```text
for each missing line (p, rem):
    a = left[store][p]
    if a >= rem                          -> "full" line: full++, units += rem
    else if a > 0 and mustSplit(p, rem)  -> units += a         (partial, only when no Store can give rem alone)
score = (full desc, units desc, already-in-plan desc, total available of missing SKUs desc, store_id asc)
```

The picked Store gives, for each line: `rem` if it can; else `a` only if `mustSplit`; otherwise nothing for that line this round. `mustSplit(p, rem)` is true iff **no** Store still has `left[.][p] >= rem`.

Termination and failure: a round with no Store having `units > 0` means every missing line has nothing left anywhere → `OUT_OF_STOCK` for exactly those SKUs, nothing allocated. Every successful round removes units, so the loop ends.

Properties (asserted by the unit and generated-data tests):
1. For every SKU, allocated quantities add up to the requested quantity; every part is `> 0` and `<= available` of its StoreProduct.
2. No StoreProduct appears twice in one plan; no unit is allocated twice; a Store never gives more than is missing.
3. Success iff for every SKU the eligible Stores together have at least the requested quantity.
4. The same data (in any offer order) gives the same plan and the same fingerprint.
5. A line is **not** split when one Store can supply what is still missing of it.

Limits: greedy, so not guaranteed to use the minimum number of Stores; only stock is considered, never distance or cost.

### 2.5 Examples

| Demand | Stock | Plan |
|---|---|---|
| A ×5 | S1:3, S2:2 | S1 ×3 + S2 ×2 (two Fulfillments, two OrderItems) |
| A ×8 | S1:4, S2:3, S3:6 | S3 ×6 + S1 ×2 (S2 not used) |
| A ×5 | S1:2, S2:2, S3:2 | S1 ×2, S2 ×2, S3 ×1 |
| A ×5 + B ×1 | S1: A3,B1; S2: A2 | S1: A×3,B×1; S2: A×2 |
| A ×2 + B ×1 | S1: A1,B1; S2: A2 | S1: B×1; S2: A×2 (A is not split) |
| A ×5 | S1:2, S2:2 | refused `OUT_OF_STOCK`, nothing created |

### 2.6 Pickup

`allocateAtStore(storeId, ...)`: the chosen Store alone must hold every line **in full**; quantity is not split and nothing is borrowed from another Store (`INSUFFICIENT_AT_PICKUP_STORE`). The page lists only Stores that cover the cart.

### 2.7 Failure reasons

`NO_DEMAND`, `OUT_OF_STOCK`, `INSUFFICIENT_AT_PICKUP_STORE`, `NO_ELIGIBLE_STORE`. Messages never expose a stock figure.

---

## 3. Plan fingerprint

`AllocationResult.signature()` fingerprints "which StoreProduct supplies which quantity of which SKU". `CheckoutServiceImpl.contextSignature(...)` wraps it with everything the customer confirms:

```text
user id | fulfillment type | payment method | pickup Store (pickup) | normalized address (delivery)
| SKU×quantity of the cart | allocation fingerprint | shipping fee per Order | number of Orders | total
```

It is built by the same method for the preview (given to the page) and when placing the order (recomputed from the database). It is not a MAC and is not trusted from the browser: a mismatch can only cause a refusal. Order of checks at placement: split consent → expected total (`TOTAL_CHANGED`) → fingerprint (`PLAN_CHANGED`). Optional unless a COD split is needed (then mandatory, which makes the consent specific to the plan shown).

---

## 4. Transaction and locking (unchanged, re-verified)

```text
BEGIN
 1  validate request shape + address; closed flows fail here (ONLINE disabled, DELIVERY kill switch)
 2  lock the customer's Cart row
 3  load the chosen lines of THAT cart; any other id -> refuse everything
 4  read each Product: ACTIVE and price > 0; unit price = Product.price
 5  candidate StoreProduct ids of the demanded SKUs   (scalar ids, no entity loaded)
 6  lock those StoreProducts in ascending id and read quantity/status under the lock
 7  allocate on the locked rows; failure -> refuse, nothing written
 8  fee per Order; totals; compare expected total and fingerprint with what the customer confirmed
 9  create CheckoutSession, then per Order: Order, Fulfillment(s), OrderItem(s)
10  deduct StoreProduct.quantity + InventoryMovement(ORDER, negative, before/after, order) per source
11  OrderStatusHistory + OrderFulfillmentStatusHistory (initial)
12  ONLINE: create the single PENDING Payment (whole total, backend-made mock reference)
13  delete exactly the checked-out CartItems
COMMIT  (any exception: ROLLBACK of everything above)
```

All candidate StoreProducts of the SKUs are locked, not only the chosen ones, so the allocation that is verified is the one applied (no re-allocation loop). The cost is that checkouts of the same SKU serialize, which is what prevents overselling. Lock order: Cart, then StoreProducts ascending; consistent order reduces but cannot eliminate deadlocks (the controller reports a lock failure as "try again"). Three layers stop overselling: the engine, `deductForOrder` on the locked row, and the database CHECK constraints.

---

## 5. Orders, Fulfillments, items, money

| Object | Rule |
|---|---|
| `orders.store_id` | Legacy convenience: the Store only when the Order has exactly one Fulfillment, `NULL` otherwise. Never an authorization source. |
| `order_fulfillments` | One per supplying Store of the Order (`UNIQUE(order_id, store_id)`), initial `CONFIRMED`. |
| `order_items` | One per allocated source: `unit_price = Product.price`, `quantity`, `subtotal = unit_price × quantity`, FK to its Fulfillment and its StoreProduct (same Store). One SKU may therefore have several items in one Order. |
| `orders.shipping_fee` | `30.000` for DELIVERY, `0` for pickup; **once per Order**, never multiplied by the number of Fulfillments. |
| `orders.total_amount` | `Σ items.subtotal + shipping_fee`. |
| `checkout_sessions.total_amount` | `Σ` of its independent Orders (COD split: one fee per Order). Not a payment object. |
| Parent/child Orders | Do not exist. |

### 5.1 Worked amounts (unit price 100.000, SKU ×5 from S1 ×3 and S2 ×2)

| Method | Orders | Amounts |
|---|---|---|
| ONLINE (gated) | 1 Order, 2 Fulfillments | items 500.000 + fee 30.000 = **530.000** |
| COD, split consented | 2 Orders | S1: 300.000 + 30.000 = **330.000**; S2: 200.000 + 30.000 = **230.000**; session **560.000** |
| STORE_PICKUP (needs one Store with 5) | 1 Order | 500.000 + 0 = **500.000** |

### 5.2 Status at creation

| Method | `order_status` | `payment_status` | Payment row | Fulfillments |
|---|---|---|---|---|
| ONLINE | `PENDING_PAYMENT` | `UNPAID` | one `PENDING`, whole total, `MOCK-…` | `CONFIRMED`, payment-gated |
| COD | `CONFIRMED` | `UNPAID` | none | `CONFIRMED` |
| PAY_AT_STORE | `CONFIRMED` | `UNPAID` | none | `CONFIRMED` |

---

## 6. COD split

- A COD cart that no single Store can supply is not created directly; the preview reports `splitConsentRequired`, `orderCount`, and per Order its Store, lines, items, fee and amount to pay.
- With consent and the matching fingerprint, one independent Order per Store is created in one transaction (same session, own fee). Without consent or with a stale fingerprint, nothing is created.
- If the number of Orders, a source, a price or the fee changes before confirmation, the order is refused and the page shows the new plan.

## 7. Idempotency

No key storage (no table/column may be added). The cart row lock serializes one customer with themselves: the second request finds the lines gone and is refused, so there is no second Order and no second deduction; only the checked-out lines are removed. "Exactly-once response" is not claimed.

## 8. Worked examples on the test seed

(Stores: THUDUC=1, GOVAP=2, QUAN7=3, QUAN10=4 pickup-only, HAICHAU=5 inactive, HOANKIEM=6 delivery-only.)

| Cart | Result |
|---|---|
| `COCOON-SERUM-30` ×2 + `INNI-TONER-200` ×1, DELIVERY | 1 Fulfillment (THUDUC) |
| `COCOON-SERUM-30` + `COCOON-SHAM-310`, ONLINE | 1 Order, 2 Fulfillments (THUDUC, GOVAP), fee 30.000 once |
| same + `ANESSA-MILK-60`, COD consented | 3 Orders, 3 × 30.000 |
| `SENKA-WHIP-120` ×100, DELIVERY (delivery Stores hold 53+48+50+60) | split over two Stores (HOANKIEM 60 + THUDUC 40), not refused |
| `SENKA-WHIP-120` ×5 with THUDUC 3 + GOVAP 2 | S1 ×3 + S2 ×2 |
| `SENKA-WHIP-120` ×5, delivery Stores hold 2+2, QUAN10 holds 40 | DELIVERY refused (OUT_OF_STOCK); PICKUP at QUAN10 accepted |
| HCM address, only HOANKIEM (Ha Noi) has stock | HOANKIEM supplies it |

## 9. Not done here (by design)

Payment confirmation by the mock gateway, timeout worker, reconciliation, Admin resolution, customer-cancel races and refund simulation are BE-05; Staff Fulfillment operations and the aggregate Order status are BE-04.
