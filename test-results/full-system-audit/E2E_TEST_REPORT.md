# E2E TEST REPORT (Chrome 155 thật, app Spring Boot thật, SQL Server `oneshop_fixture_20261025_test`)

Không dùng tiến trình `localhost:8080`. Chrome qua CDP; mỗi luồng kiểm UI → HTTP → DB.

| Luồng (BrowserV3DatabaseIntegrationTest) | Kết quả | Xác minh DB |
|---|---|---|
| COD | PASS | CONFIRMED, 0 payment SUCCESS |
| COD_SPLIT (consent) | PASS | 2 đơn độc lập CONFIRMED |
| PICKUP (+ mã nhận hàng, không lộ cho Staff) | PASS | COMPLETED, 1 payment SUCCESS |
| ONLINE (Cart→Checkout→Payment→Staff prepare/pack/ship/complete) | PASS | COMPLETED, 1 payment SUCCESS |
| FAILED (qua endpoint thật bằng session+CSRF của khách) | PASS | CANCELLED, hoàn kho đúng 1 lần |
| REFUND | PASS | CANCELLED, payment REFUNDED |
| REVIEW (review → Staff reply → Admin ẩn bằng modal lý do) | PASS | PARTIALLY_COMPLETED, review HIDDEN + reply |

Thêm: QaAuditScreenBrowser (màn hình theo vai trò + thao tác nhỏ) PASS; QaFixUiBrowser ×2 PASS; **responsive 135 capture** (45 trang × 375/768/1440; Customer/Staff/Admin) PASS, 0 overflow ngang.
Bằng chứng: `evidence/browser-be07/<MODE>/`, `evidence/browser-responsive/`.

**Chưa phủ bằng browser thật:** Admin CRUD đầy đủ (chỉ form ảnh/danh mục/thương hiệu), Staff đổi tồn kho trên UI (chỉ HTTP/DB test), cổng thanh toán thật. Tablet/mobile chỉ ở mức điều hướng + 1 tương tác giỏ/checkout, chưa chạy cả luồng mua hàng ở mobile.
Script responsive gốc dùng ID đơn cố định của DB cũ → đã ánh xạ sang đơn fixture (`scripts/qa-audit/responsive-fixture.mjs`).
