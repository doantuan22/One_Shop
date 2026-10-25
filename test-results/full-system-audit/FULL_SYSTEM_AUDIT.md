# OneShop — Full System Audit & Test Report

**Ngày kiểm thử:** 2026-10-25  
**Kết luận:** **PARTIALLY VERIFIED / NOT READY**  
**Evidence:** `test-results/full-system-audit/evidence/`

## 1. Tóm tắt điều hành

Đã khảo sát source, schema, cấu hình, test suite; chạy Maven suite trên DB có tên test; duyệt website đang chạy bằng Chrome thật ở ba viewport; sửa hai mặc định cấu hình rủi ro và chạy regression tập trung.

Kết quả chưa đủ điều kiện nghiệm thu toàn hệ thống. DB test không khớp entity/schema nên các integration context không khởi động; 1 test UI còn thất bại; DB vận hành `oneshop` được loại khỏi các thao tác ghi. Không có truy vấn SQL trực tiếp hoàn tất trong lượt này, không đo branch coverage, và chưa chạy full E2E nghiệp vụ.

| Chỉ số | Kết quả |
|---|---:|
| Feature records được inventory | 40 |
| Page/screen templates | 51 |
| Tổng HTML templates | 68 |
| Controller Java files | 40 |
| Handler mapping annotations | 132 (không đồng nghĩa unique URL) |
| Class-level `@RequestMapping` | 29 |
| Service Java files | 98 |
| SQL tables trong schema | 29 |
| Regex conditional-token scan | 1,105 (không phải số nhánh) |
| Branch coverage | Chưa đo |
| Full Maven suite mới nhất | 1,799 test: 821 PASS tính theo phép trừ, 1 FAIL, 977 ERROR, 0 skipped |
| Focused regression | 6 PASS |
| Browser public-page visits | 18 PASS (render smoke only) |
| Defect records | 4: 2 đã sửa, 1 blocker môi trường, 1 cần quyết định UI/spec |
| SQL query kiểm chứng trực tiếp | 0 |

Pass count của full suite được tính bằng tổng trừ failures/errors/skips. Focused regression được báo cáo riêng, không cộng vào kết quả full suite.

## 2. Phiên bản và căn cứ nghiệp vụ

- Repository triển khai mô hình **OneShop V3**: một giá online trên `Product`, CartItem theo Product, tồn theo `StoreProduct`; ONLINE tạo một Order có nhiều Fulfillment; COD nhiều Store cần consent tách Order; Staff xử lý Fulfillment theo Store scope.
- Căn cứ: `README.md`, controllers/services/entities, `database/01_schema.sql`, và `docs/report/audit/function_inventory.md` có sẵn.
- README liên kết Roadmap V2 và tài liệu DEC/V3-T01..30 nhưng các tài liệu đó **không có trong checkout**. Vì vậy không thể xác nhận đối chiếu TC-01..18/V3-T01..30 hoặc quyết định còn pending. Không dùng yêu cầu V2 để kết luận nghiệp vụ V3.
- `.env` chọn DB `oneshop`; không chạy mutation lên DB này. Website `http://localhost:8080` là tiến trình JAR đang chạy sẵn; không restart/reconfigure trong audit.

## 3. Inventory chức năng, màn hình và endpoint

### 3.1 Màn hình/template

51 page/screen templates (không phải tất cả đã mở trên browser):

`error.html`; account `profile`; admin `brands`, `categories`, `dashboard`, `inventory`, `order-detail`, `orders`, `product-form`, `products`, `reviews`, `staff-assignments`, `store-form`, `store-product-form`, `store-products`, `stores`, `user-form`, `users`, `payments/detail`, `payments/index`, `promotions/form`, `promotions/index`, `returns/detail`, `returns/index`; auth `forgot-password`, `login`, `register`, `reset-password`, `verify-otp`; `cart/index`; `checkout/index`, `checkout/result`; `credits/image-credits`; `home/index`; orders `detail`, `index`, `return-form`; `payment/index`; product `detail`, `list`; staff `dashboard`, `fulfillments/detail`, `fulfillments/index`, `returns/index`, `reviews/detail`, `reviews/index`, `stock/adjust`, `stock/history`, `stock/index`, `stock/inventory-history`; `store/list`.

68 templates tổng cộng còn bao gồm layouts, fragments, error partials. Chỉ 6 public route được mở browser trong phiên này; xem mục E2E.

### 3.2 Feature IDs và traceability

| IDs | Nhóm chức năng / thao tác | UI / route family | Service/domain và DB/test mapping |
|---|---|---|---|
| F-01..03 | Đăng ký, đăng nhập/đăng xuất, OTP quên mật khẩu | `/login`, `/register`, `/forgot-password/**`, `/api/auth/**` | Auth/JWT/PasswordReset; users, roles, OTP; Auth/JWT tests |
| F-04..06 | Trang chủ/catalog/search/detail, tra cứu/chọn Store | `/`, `/products/**`, `/stores/**`, `/image-credits` | Product/StoreProduct/Store; products, categories, brands, stores, store_products; Catalog tests |
| F-07..08 | Thêm/chọn/sửa/xóa CartItem | `/cart/**` | CartService; carts, cart_items; Cart tests |
| F-09..11 | Preview/checkout/allocation/promotion/payment | `/checkout/**`, `/orders/{id}/payments/**` | Checkout/Allocation/Payment; orders, order_items, fulfillments, payments, inventory_movements; Checkout/Payment tests |
| F-12..15 | Xem/hủy đơn, trả hàng, review | `/orders/**`, `/reviews` | Order/cancellation/refund/returns/review; order histories, returns, reviews/replies; corresponding tests |
| F-16..17 | Cập nhật thông tin và mật khẩu tài khoản | `/account/**` | AccountProfile; users; Account tests |
| F-18..24 | Admin users/assignments/stores/categories/brands/products/images/inventory | `/admin/users`, `/admin/staff-assignments`, `/admin/stores`, `/admin/categories`, `/admin/brands`, `/admin/products`, `/admin/store-products`, `/admin/inventory` | Admin/Product/Inventory/Cloudinary; corresponding tables; Phase11/Admin/Catalog tests |
| F-25..31 | Admin orders, exceptions, payment/refund, returns, promotion, moderation, dashboard | `/admin/orders/**`, `/admin/payments/**`, `/admin/returns/**`, `/admin/promotions/**`, `/admin/reviews/**`, `/admin` | Admin operations; orders, fulfillment, payment/refund/return/promotion/review histories; corresponding integration tests |
| F-32..40 | Staff scope/dashboard, fulfillment/pickup, stock/history, returns, review reply | `/staff/**` | Staff scope/fulfillment/inventory/returns/review; assignments, fulfillments, store_products, movements, reviews; Staff/Fulfillment/Review tests |

`docs/report/audit/function_inventory.md` chứa 40 dòng chức năng chi tiết `KH-01..17`, `QT-01..14`, `NV-01..09`, mapping controller/service/template và các giới hạn hiện tại.

### 3.3 Endpoint/controller inventory

132 method-level mapping annotations được tìm thấy. Số này gồm aliases, class prefixes và path constraints, không được coi là số unique HTTP URL. Runtime unique endpoint count chưa xác định.

| Khu vực | Controllers và route families |
|---|---|
| Client/public | `HomeController /`; `AuthController /login,/register`; `PasswordResetController /forgot-password/**`; `ProductController /products,/products/suggest,/products/{id}`; `StoreController /stores,/stores/select,/stores/clear`; `CartController /cart/**`; `CheckoutController /checkout/**`; `PaymentController /orders/{orderId}/payments/**`; `CustomerOrderController /orders/**`; `CustomerOrderReturnController /orders/{orderId}/return`; `ReviewController /reviews`; `AccountController /account/**`; `ImageCreditsController /image-credits` |
| API | `AuthApiController /api/auth/login,/api/auth/register`; `HealthController /health` |
| Staff | `StaffDashboardController /staff/**`; `StaffFulfillmentController /staff/fulfillments/**,/staff/orders/**,/staff/pickup`; `StaffStockController /staff/stock/**,/staff/inventory-history/**`; `StaffReviewController /staff/reviews/**`; `StaffOrderReturnController /staff/returns/**` |
| Admin | `AdminDashboardController /admin`; `AdminUserController /admin/users/**`; `AdminStaffAssignmentController /admin/staff-assignments/**`; `AdminStoreController /admin/stores/**`; `AdminCategoryController /admin/categories/**`; `AdminBrandController /admin/brands/**`; `AdminProductController /admin/products/**`; `AdminStoreProductController /admin/store-products/**`; `AdminInventoryController /admin/inventory`; `AdminOrderController /admin/orders/**`; `AdminFulfillmentExceptionController /admin/orders/{orderId}/fulfillments/{id}/redeliver,/undeliverable`; `AdminPaymentController /admin/payments/**`; `AdminOrderReturnController /admin/returns/**`; `AdminPromotionController /admin/promotions/**`; `AdminReviewController /admin/reviews/**`; `AdminImageCleanupController /admin/images/cleanup/retry` |
| Cross-cutting | `DecoratorController /decorators/**`; advice/exception handlers không thêm business routes |

### 3.4 Actions, rules, branches, states và tương tác cần test

Inventory thao tác: list/detail/search/suggest/filter/sort/pagination; auth/OTP; chọn/bỏ Store; cart add/update/remove/select; preview/checkout/promotion; payment attempt/simulate/success/failure/recheck/refund; order view/cancel/return; staff prepare/pack/ship/deliver/fail/ready/verify pickup/complete; stock adjust/history; review reply/moderate; admin CRUD/status/assignment transfer/image upload/primary/delete/promotion/order/payment/refund/return; account update/password; điều hướng, validation, modal/form, media và responsive.

Nhánh nghiệp vụ được xác định cần testcase riêng: role/status/ownership/IDOR; staff assignment ACTIVE và cross-store; số lượng biên/tồn toàn chuỗi; selection CartItem; phân bổ một/nhiều Store/không đủ hàng; Store/StoreProduct ngừng hoạt động; DELIVERY/PICKUP; ONLINE/COD/PAY_AT_STORE/split consent; stale price/stock/signature; retry/idempotency/concurrency/rollback; callback thanh toán lặp/sai amount; transition hợp lệ/không hợp lệ và aggregate Order; hủy khi PREPARING/đã trả tiền/hủy lặp; pickup code đúng/sai/Store khác; hoàn kho/refund đúng một lần; return/review eligibility và Store nguồn; upload invalid/oversize/DB failure/orphan cleanup; uniqueness/FK/history trong admin.

Tên trạng thái và transition policy được định nghĩa trong enum, `*TransitionPolicy`, `OrderStatusAggregator`, `FulfillmentAction`, và các Payment/Return/Review status. Regex scan 1.105 conditional tokens **không phải** số branch. Branch enumeration/coverage chưa được đo.

## 4. Test coverage matrix

PASS chỉ áp dụng cho assertion/evidence ghi rõ. Các test chưa chạy hoặc bị chặn không được mặc định PASS.

| Test ID | Module / Flow | Branch | Preconditions / data | Steps và expected | Actual / status | Evidence / defect |
|---|---|---|---|---|---|---|
| WEB-001 | Home / F-04 | render/load | App local; `GET /` | Mở bằng Chrome; mong 200/render | 200/render — PASS | `evidence/browser/browser-run.json`, `home-1440.png` |
| WEB-002 | Login / F-01 | render/load | `GET /login` | Kiểm tra form `#identifier` | 200/form hiện diện — PASS | JSON và `login-1440.png` |
| WEB-003 | Register / F-01 | render/load | `GET /register` | Kiểm tra form | 200/form hiện diện — PASS | JSON và `register-1440.png` |
| WEB-004 | Catalog / F-04 | render/load | `GET /products` | Mở trang catalog | 200/render — PASS | JSON và `catalog-1440.png` |
| WEB-005 | Store finder / F-06 | render/load | `GET /stores` | Mở trang Store | 200/render — PASS | JSON và `stores-1440.png` |
| WEB-006 | Credits / F-06 | render/load | `GET /image-credits` | Mở trang credits | 200/render — PASS | JSON và `credits-1440.png` |
| WEB-007 | Responsive public UI | 3 viewport | 6 route x 1440x900/768x900/390x844 | Điều hướng; kiểm tra status/errors/overflow | 18 loads; 0 lỗi, 0 failed responses, 0 overflow — PASS render smoke | JSON và 18 PNG |
| CFG-001 | Payment worker / F-11 | default/opt-in | Property config, không DB | Chạy `PaymentWorkerDefaultsTest` | 5/5 PASS | Surefire XML; BUG-001 |
| CFG-002 | Production payment / F-11 | mock/ONLINE/worker defaults | Prod properties, không DB | Assert default disabled | PASS; combined focused suite 6/6 | `PaymentProductionDefaultsTest`; BUG-002 |
| SUITE-001 | Full automated suite | DB context | DB test `oneshop_v3_verify_test` | Maven full test | 1,799 total; 1 failure; 977 errors; 0 skipped; 821 pass tính theo phép trừ. DB contexts vấp thiếu `order_items.discount_amount` — FAIL suite / DB tests BLOCKED | `evidence/full-suite-maven.log`; ENV-001 |
| SUITE-002 | Focused regression | config defaults | Không cần DB | Worker + production defaults | 6/6 PASS | Surefire reports; BUG-001/002 |
| SUITE-003 | Admin category | list copy | Mocked web test | Assertion đòi heading “Danh mục dùng chung toàn chuỗi” | Heading không có trong template; FAIL/NEEDS_DECISION | `CategoryAdminSecurityWebIntegrationTest`; BUG-004 |
| AUTH-001 | Authentication/security | role/JWT/CSRF/IDOR | Isolated SQL fixture | Thực hiện HTTP/browser mutation và xác minh DB | Web/security một phần đã chạy; DB scope/auth bị chặn; full browser login không chạy — BLOCKED | ENV-001 |
| CAT-001 | Catalog | filter/search/status | Isolated SQL fixture | Search/filter/product/status và so với DB | Chỉ render smoke; behavior chưa đủ — NOT_RUN |
| CART-001 | Cart | quantity/owner/repeat | Isolated SQL fixture | Add/update/remove/bounds/foreign IDs; query DB | Integration contexts bị chặn — BLOCKED |
| CHECKOUT-001 | Checkout/allocation | đủ/thiếu hàng, split, stale values, race | SQL fixture tối thiểu 3 Store | Preview, tamper, submit/concurrent, query tables | Chưa chạy do DB context — BLOCKED |
| ORDER-001 | Order/Fulfillment | state transitions/aggregation | Isolated DB | Chạy transition valid/invalid, đọc history | DB tests bị chặn — BLOCKED |
| PAY-001 | Payment/refund | methods/callback/idempotency | Isolated DB | HTTP + SQL assertions | DB tests bị chặn; config test PASS riêng — BLOCKED |
| INV-001 | Inventory/cancel | deduction/restore once/concurrency | Isolated DB | Cancel/retry/race; kiểm movement | DB tests bị chặn — BLOCKED |
| STAFF-001 | Staff operations | scope/assignment/cross-store | 2 Store, isolated DB | Store A thử truy cập Store B; đối chiếu DB | Scope integration bị chặn — BLOCKED |
| ADMIN-001 | Admin CRUD | validation/FK/history | Isolated DB và media | Test CRUD, duplicate, status, pagination | Chưa thực thi toàn bộ — NOT_RUN |
| MEDIA-001 | Cloudinary | upload/rollback/orphan | Test credentials/isolated DB | Upload các loại file và xác minh URL/public_id | Live integration chưa chạy — BLOCKED |
| DB-001 | SQL integrity | constraints/transactions | Matching isolated DB | Query PK/FK/check, totals, stock, histories | Không có direct SQL query; schema mismatch — BLOCKED |
| E2E-001 | Cross-module journeys 1–10 | guest/customer/staff/admin | Fixture DB và browser | Chạy 10 luồng prompt và verify SQL | Chưa chạy full E2E; BrowserV3 context không khởi động — BLOCKED |
| SPEC-001 | V2/V3 baseline | TC-01..18/V3-T01..30 | Tài liệu baseline | Reconcile từng requirement | Tài liệu không có — BLOCKED |

Coverage requirements/actions/endpoints/transitions chưa đầy đủ. Không có branch coverage metric. Chưa test hết nút, form, modal, filter, pagination, sort, thông báo hoặc error state.

## 5. Execution log và kết quả thực thi

1. Khảo sát README, source inventory, schema, app profiles, DB test safety initializer và audit artifacts. Xác định V3; baseline docs vắng mặt.
2. Xác nhận `.env` trỏ tới operational DB `oneshop`; không gửi mutation đến DB này.
3. Chạy `./mvnw.cmd -B test '-Doneshop.test.db=oneshop_v3_verify_test'` sau khi sửa cấu hình; kết quả mới nhất 1,799 tests, 1 FAIL, 977 ERROR, 0 skipped, build thất bại. Các DB context lỗi bởi `Schema-validation: missing column [discount_amount] in table [order_items]`.
4. Chạy `./mvnw.cmd -B test '-Dtest=PaymentWorkerDefaultsTest,PaymentProductionDefaultsTest'`; 6 tests, 0 fail/error/skip, BUILD SUCCESS.
5. Read-only HTTP: `/`, `/login`, `/register`, `/products`, `/stores`, `/health` trả 200; `/admin`, `/cart`, `/checkout` chuyển hướng yêu cầu auth; không submit mutation.
6. Chrome/CDP điều hướng 6 public route ở 3 viewport; 18 loads, không console error, failed response hoặc overflow. Screenshot/JSON nằm trong evidence.

`/health` trả `{"status":"UP"}` nhưng là liveness endpoint, không xác nhận database.

## 6. Defect report và trạng thái sửa

### BUG-001 — Timeout worker bật mặc định

- **Severity:** High — worker có thể tự hủy order/hoàn kho khi app khởi động.
- `application.properties` mặc định worker `true`, trái README và regression expectation an toàn.
- **Fix:** mặc định `false`; explicit environment opt-in còn hoạt động.
- **Retest:** `PaymentWorkerDefaultsTest` 5/5 PASS. Không kiểm thử scheduler ghi SQL thật.
- **Status:** Fixed; focused regression PASS.

### BUG-002 — Mock gateway và ONLINE checkout bật mặc định production

- **Severity:** High — mock payment có thể được xem là thanh toán thành công trong production dù không có gateway thật.
- `application-prod.properties` mặc định cả hai là `true`, trái README/comment.
- **Fix:** mock gateway và ONLINE mặc định `false`; có thể override rõ ràng qua env.
- **Regression:** thêm `PaymentProductionDefaultsTest`, assert production gateway/ONLINE/worker defaults; combined suite 6/6 PASS.
- **Status:** Fixed; chưa verify deployed overrides.

### ENV-001 / BUG-003 — Test DB schema cũ/không tương thích

- **Severity:** High blocker kiểm nghiệm release; đây là blocker môi trường, chưa quy kết lỗi production.
- `oneshop_v3_verify_test` thiếu `order_items.discount_amount`, trong khi `database/01_schema.sql` khai báo column này, `NOT NULL`, default 0 và check `0 <= discount_amount <= subtotal`.
- **Ảnh hưởng:** 977 suite errors; checkout/payment/order/inventory/review/staff/admin SQL integrations và browser E2E không chạy đến assertion nghiệp vụ.
- **Status:** BLOCKED. Không tự chạy ALTER/upgrade trên DB có sẵn. Cần tạo DB disposable `oneshop*_test` mới từ schema và fixture được duyệt, hoặc có migration/backup được phê duyệt.

### BUG-004 — Test category đòi heading UI không tồn tại

- **Severity:** Low / needs decision.
- Test kỳ vọng “Danh mục dùng chung toàn chuỗi”; `templates/admin/categories.html` chỉ có “Danh mục”. Không có spec V3 được duyệt trong checkout để quyết định label nào đúng.
- Test file là thay đổi đã có của người dùng; không sửa/nới assertion hoặc UI khi chưa có yêu cầu được xác nhận.
- **Status:** FAIL, NEEDS_DECISION.

Các 977 errors không đại diện cho 977 lỗi sản phẩm độc lập; phần lớn bắt nguồn schema validation. Không tuyên bố không có vulnerability/defect nào khác.

## 7. Database integrity

`.env` chọn `oneshop`, bị loại khỏi write test; không chạy SQL query trực tiếp. Test dùng tên `oneshop_v3_verify_test` và safety initializer chỉ cho phép `oneshop*_test`, Hibernate DDL giới hạn `none`/`validate`; context fail trước khi test DB nghiệp vụ chạy.

| Bất biến | Trạng thái | Bằng chứng/thiếu hụt |
|---|---|---|
| PK/FK/UNIQUE/CHECK/NOT NULL | BLOCKED | Schema validation fail; không có successful integrity query |
| Tồn không âm và allocation atomic | BLOCKED | Cần transaction/concurrency tests trên SQL Server |
| Không có OrderItem mồ côi; quan hệ Order/Fulfillment/Store đúng | BLOCKED | Fixture context không khởi động |
| Tổng tiền/shipping/promotion/snapshot giá đúng | BLOCKED | Chưa chạy checkout SQL thành công |
| Deduct/restore và InventoryMovement đúng một lần | BLOCKED | Không hoàn thành DB test an toàn |
| Payment/refund idempotency, không duplicate success | BLOCKED | Chưa chạy DB payment assertions |
| Staff scope và DB Store khác không đổi | BLOCKED | Scope test context fail |
| Status history đầy đủ, history được giữ | BLOCKED | Không có transaction transition để kiểm tra |
| Schema source có OrderItem discount column | PASS (source only) | `database/01_schema.sql`; không chứng minh schema test/live đang tương thích |

Không chạy ad-hoc DDL, truncate dữ liệu hoặc trỏ test sang `oneshop`. Bước tiếp theo an toàn là provision DB test hoàn toàn mới bằng schema hiện hành, seed fixture, xác minh không có dữ liệu phi-test rồi chạy lại.

## 8. Browser E2E và evidence

- App: app local đang chạy tại `http://localhost:8080`, không restart/reconfigure.
- Browser: Chrome headless thật, profile tách riêng, CDP qua `scripts/lib/cdp.mjs`.
- Routes read-only: `/`, `/login`, `/register`, `/products`, `/stores`, `/image-credits`.
- Viewports: 1440x900, 768x900, 390x844; **18 loads**, 0 lỗi console, 0 lỗi network/HTTP, 0 horizontal overflow.
- Evidence: `evidence/browser/browser-run.json` và 18 PNG route/viewport; run script `evidence/browser/audit-live.mjs`.
- Không login và không gửi cart/checkout/payment/CRUD/cancel/refund/stock/review mutation trên app đang cấu hình DB `oneshop`.
- Vì vậy 10 cross-module journeys được yêu cầu không được xem là E2E PASS; browser DB integration bị chặn bởi ENV-001.
- Đây là render smoke, không phải kiểm thử mọi UI action, accessibility hoặc authenticated role.

## 9. Regression report

| Area | Retest | Kết quả | Giới hạn |
|---|---|---|---|
| BUG-001 timeout-worker default | `PaymentWorkerDefaultsTest` | 5/5 PASS | Không chạy SQL scheduler/overdue write |
| BUG-002 production payment defaults | `PaymentProductionDefaultsTest` | Direct property assertions PASS; focused suite 6/6 PASS | Chưa kiểm tra deployed env overrides |
| Checkout/payment/inventory/fulfillment/review/admin SQL | Latest full Maven suite | BLOCKED | DB context thiếu column |
| Admin category heading | Latest full Maven suite | FAIL | Pending product/spec decision |
| Public route rendering | Chrome/CDP 18 visits | PASS render smoke | Không có authenticated/mutating interactions |

Full suite đã được chạy lại sau khi sửa config; không thể hoàn tất full SQL-backed regression do DB schema test chưa tương thích.

## 10. Những hành vi đã biết không có hoặc bị giới hạn

- Không có bank gateway thật; ONLINE dùng mô phỏng. Production defaults đã tắt.
- Không có SMTP implementation trừ khi được cấu hình ngoài.
- Không có CRUD địa chỉ trong account; checkout nhận địa chỉ trực tiếp.
- Customer không tự hoàn tất fulfillment; Staff thực hiện.
- Admin trả lời review bị chặn có chủ đích; Staff Store nguồn trả lời.
- Staff legacy Order endpoints redirect/read-only; Fulfillment là đơn vị vận hành.
- Đây là ghi nhận từ source, không phải yêu cầu V3 tự suy diễn.

## 11. Điều kiện còn thiếu để nghiệm thu

1. Cung cấp/provision isolated SQL Server test DB mới từ schema và fixture hiện hành; xác nhận an toàn, không dùng `oneshop`.
2. Chạy lại full DB integration/browser suite và query SQL sau các luồng quan trọng.
3. Quyết định BUG-004 theo yêu cầu UI được duyệt.
4. Bổ sung Roadmap V2 và DEC/V3-T01..30 để đối chiếu testcase baseline và pending decisions.
5. Chạy các UI actions, negative security cases, media integration, concurrency và đo branch coverage bằng instrumentation phù hợp.

Không tuyên bố 100% coverage, `FULLY VERIFIED`, hoặc không còn bug.
