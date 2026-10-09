# Phase 12 – Audit trước code

Audit ngày 09/10/2026. Baseline source `f9fd4fa` (Phase 11), workspace sạch. Đã chạy lại clean package trước thay đổi: 894 tests, 893 PASS, 0 FAIL, 0 ERROR, 1 Cloudinary conditional SKIP, BUILD SUCCESS (14:38:46 +07:00).

Đã đọc toàn bộ Phase 12 trong tài liệu phân chia giai đoạn và Roadmap V2 (business rules, mục 6.3–6.8, 7, 8.2, bảng TC-01–TC-18, rủi ro mục 14).

## Hiện trạng và quyết định

| Thành phần | Đã có / evidence hiện tại | Phần cần bổ sung |
| --- | --- | --- |
| OrderStatusHistory | Checkout tạo entry NULL → initial; OrderServiceImpl apply cập nhật status + history; payment/fulfillment delegate; OrderStateDatabaseIntegrationTest + Phase9/10 lifecycle/race/rollback | Không triển khai lại. Customer cancellation phải delegate cùng writer |
| InventoryMovement ORDER | Checkout snapshot, khóa StoreProducts theo ID tăng, trừ kho + movement trong transaction; CheckoutDatabaseIntegrationTest | Tái sử dụng |
| CANCEL_ORDER | Payment FAILED: khóa Order → StoreProducts tăng ID → restore snapshot từng item; status/history/payment atomic; PaymentDatabaseIntegrationTest có multi-item rollback, repeated failure và race | Tái sử dụng primitive restore, bổ sung reason chính xác cho customer cancellation |
| STOCK_ADJUST | InventoryService cùng write transaction/row lock; StaffInventoryDatabaseIntegrationTest có before/after, actor/note, rollback, concurrent adjustments | Tái sử dụng |
| Customer cancellation | CustomerOrderService và /orders chỉ read; graph hiện chỉ có PENDING_PAYMENT → CANCELLED do PAYMENT_FAILURE. Roadmap 6.7/BR-12 và TC-12 còn thiếu hủy chủ động | Bổ sung POST Customer cancel với ownership/ACTIVE CUSTOMER, ngưỡng hợp lệ, idempotency, payment attempt closure, atomic restore/status/history |
| Checkout concurrency | CheckoutConcurrencyDatabaseIntegrationTest: SKU cuối, stock chỉ đủ một khách, thứ tự SKU ngược, duplicate checkout, lỗi sau Store đầu rollback toàn bộ | Không viết lại các test đã có |
| Fulfillment/payment concurrency | OrderState/Payment/Delivery/Pickup/Phase9/Phase10 tests: double actions/results, stale state, receipt/history rollback, sibling independence | Tái sử dụng; thêm race customer cancel với cancel/payment/Staff prepare/adjustment |
| Price/snapshot | TC-08 và TC-14 service + production HTTP/SQL; total từ StoreProduct đang khóa, snapshot bất biến | Tái sử dụng; request cancel không nhận price/status/quantity/actor |
| Authorization/JWT/CSRF | SecurityConfig/JWT đọc DB mỗi request, HttpOnly cookie, Staff scope ACTIVE assignments; Phase10/11 HTTP/SQL tests | Tái sử dụng, kiểm endpoint cancel mới cho guest/roles/owner, Cookie/Bearer/encoded CSRF |
| Soft statuses / historical reads | CRUD chỉ status, không delete Product/Store/StoreProduct; Phase11 TC-18 production checkout/read | Tái sử dụng; cancel phải hoàn đúng stock cả khi catalog/Store đã INACTIVE |
| Cloudinary TC-17 | CatalogDatabaseIntegrationTest mock external adapter + SQL URL/public_id/no binary; CloudinaryServiceTest provider config/guard; một HTTP test nhánh unconfigured skip khi config tồn tại | Giữ evidence adapter/SQL; bổ sung opt-in live HTTP/provider/SQL verification bằng fixture riêng và cleanup |

## Business rule hủy đã chốt trước code

Người dùng xác nhận qua chat: **chỉ trước PREPARING, chưa thanh toán**. Cụ thể:

- DELIVERY COD / STORE_PICKUP PAY_AT_STORE: CONFIRMED + UNPAID.
- ONLINE: PENDING_PAYMENT + UNPAID; ONLINE đã PAID không hủy vì chưa có refund.
- PREPARING/PACKED/SHIPPING/READY_FOR_PICKUP/COMPLETED hoặc dữ liệu payment bất nhất bị chặn.
- Customer chỉ hủy Order của chính mình. Staff/Admin không có chức năng cancel mới.
- CANCELLED lặp lại không restore/movement/history lần nữa. Callback/payment success sau cancel phải bị chặn; pending attempt (kể cả legacy COD/PAY_AT_STORE của model/seed hiện tại) kết thúc cùng transaction, không ghi nhận paid_at hoặc receipt SUCCESS.
- Khóa Order trước, kiểm trạng thái DB hiện tại, giữ thứ tự StoreProduct tăng ID; cancel + restore + movements + history (+ closure pending attempts) cùng REQUIRED transaction.

Không đổi schema/model; không refund/return, gateway, Redis/queue, audit system mới, chức năng Staff/Admin hay Phase 13. Không sửa/xóa test để né failure. Những test hiện tại cho generic fulfillment/payment graph vẫn giữ nguyên: cancellation có guard/cause riêng trong policy/state machine.

## Coverage TC trước bổ sung

TC-01/02/03: CatalogDatabaseIntegrationTest + CatalogHttpDatabaseIntegrationTest.
TC-04/05: CartDatabaseIntegrationTest + CartHttpDatabaseIntegrationTest.
TC-06/08/14: CheckoutDatabaseIntegrationTest + CheckoutHttpDatabaseIntegrationTest.
TC-07: CheckoutConcurrencyDatabaseIntegrationTest.
TC-09/10/15/16: Delivery/Pickup/Payment/OrderStateDatabaseIntegrationTest + Phase9IntegrationWebDatabaseTest + Phase10IntegrationWebDatabaseTest.
TC-11/13: Staff scope/operations/inventory DB tests + Phase10 HTTP acceptance.
TC-12: payment-failure cancellation đã có; customer cancellation còn thiếu, cần production code + test mới.
TC-17 trước bổ sung: adapter/SQL coverage có; không phải live Cloudinary evidence.
TC-18: Phase11AdminDatabaseIntegrationTest + Catalog/Checkout tests.

Kết quả thực chạy và bảng evidence từng method được ghi trong `Phase12_HistoryHardening_Report.md` sau verification.

## Lỗi concurrency xác nhận trước khi sửa

`StoreProductServiceImpl.updateStoreProduct` đọc entity bằng query không khóa rồi mới gọi `InventoryService.adjustStock` khóa cùng ID. Entity đã nằm trong persistence context nên JPA không refresh quantity khi khóa sau đó. Test SQL Server `adminStockEditMustLockBeforeLoadingQuantityToPreserveConcurrentMovementLedger` dùng barrier trước SELECT FOR UPDATE: transaction khác commit 40 → 44; Admin ghi movement với before=40 thay vì 44. Kết quả tái hiện: 1 FAIL, 0 ERROR, 0 SKIP, log `target-phase12-stale-read.log`, 14:48:05 +07:00. Chỉnh tối thiểu: Admin update dùng `findByIdForUpdate` ngay lần load đầu; giữ nguyên inventory writer và metadata update.

Kết quả sau triển khai/verification được ghi riêng trong báo cáo: 34/34 test Phase12 PASS (33 history/hardening + 1 live HTTP Cloudinary), TC-01–TC-18 PASS; full 928 tests, 927 PASS, 1 SKIP cũ, BUILD SUCCESS lúc 15:11:16 +07:00. Seed có legacy pending offline hợp lệ; bổ sung test giữ khả năng hủy khi còn CONFIRMED + UNPAID, không sửa/reset dữ liệu hiện có.
