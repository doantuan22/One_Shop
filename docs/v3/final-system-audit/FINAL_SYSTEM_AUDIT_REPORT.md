> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# OneShop V3 — Final System Audit

Ngày audit: 14-10-2026 (ICT). Baseline BE-07 đã được chạy lại trước thay đổi: **1.585 PASS, 0 failure, 0 error, 0 skipped**.

## Kết luận audit

Luồng V3 được kiểm tra xuyên Controller → Service → Repository → SQL Server → Thymeleaf:

- Checkout dùng `Product.price` để snapshot `OrderItem.unit_price`; giá `StoreProduct` không quyết định giá checkout.
- Cart authority là Product; checkout allocation đọc tồn `quantity - reserved_quantity`, ưu tiên một Store rồi split nhiều Store khi cần.
- ONLINE tạo một Order và nhiều Fulfillment; COD split tạo các Order độc lập khi có consent; pickup cần một Store đủ toàn giỏ.
- Staff V3 thao tác `OrderFulfillment` theo Store assignment `ACTIVE`; aggregate Order và inventory movement không bị sửa ở các bước chuẩn bị/giao.
- Payment mock được đối chiếu ở server; timeout, UNKNOWN, cancel, refund simulation và callback muộn được bao phủ bởi integration/concurrency tests.
- Review gắn OrderItem đã hoàn tất, unique theo item; Staff reply đúng source Store, Admin chỉ moderation.

Các service Order-level cũ vẫn có caller ở dashboard/test compatibility nhưng fail-closed khi Order có Fulfillment. Chúng không là đường xử lý Fulfillment V3. Cart `store_product_id` còn được giữ nullable vì entity/schema hiện tại vẫn dùng nó cho metadata display và dữ liệu lịch sử; không dùng để quyết định allocation hoặc giá checkout.

## Kết quả cuối

Database `oneshop_v3_audit_cleanup_test` được tạo mới hoàn toàn bằng `database/01_schema.sql` và `02_seed.sql`; `03_verify.sql` PASS 12/12. Full suite trên database đó: **1.558 PASS, 0 failure, 0 error, 0 skipped**.

`oneshop` và `oneshop_test` không bị sửa trong đợt audit này.
