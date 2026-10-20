> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-07 — Báo cáo triển khai và nghiệm thu hồi quy

Ngày nghiệm thu: 14-10-2026 (ICT). Phạm vi là OneShop V3 đang có trong repo và SQL Server `oneshop_test`; database vận hành `oneshop` chỉ được đọc để đối chiếu.

## Kết quả

Lệnh `mvn -o -B test` kết thúc lúc 11:36 ICT với **1.585 test PASS, 0 failure, 0 error, 0 skipped**. Baseline đầu BE-07 có 1.575 test: 1.158 PASS, 49 failure, 326 error, 42 skipped. So sánh theo từng identity `classname.method`: 417 identity không PASS nay PASS, 0 regression, 0 identity bị bỏ, 10 identity mới.

Toàn bộ test chạy với SQL Server thật trên `oneshop_test`, không chạy test ghi dữ liệu vào `oneshop`. Snapshot nội dung 22 bảng, index, FK và CHECK trước/sau hoàn toàn giống nhau ở cả hai database. Giá trị identity có thể tăng trong lúc test nhưng dữ liệu nghiệp vụ đã được dọn sạch.

## Thay đổi hoàn tất trong BE-07

- Chặn primitive transition cũ trên Order đã có Fulfillment; giữ luồng V3 qua aggregate và history.
- Khoá và đọc Payment/Fulfillment theo Order để tránh deadlock giữa các Order độc lập; chạy các race checkout, payment và cancel trên SQL Server thật.
- Xác thực server-side Order, phương thức, số tiền và ownership trước khi Mock Payment Gateway xác nhận thu tiền.
- Sửa lọc dữ liệu Admin theo Store nguồn Fulfillment; Staff chỉ reply Review của Store nguồn khi assignment `ACTIVE`; Admin chỉ moderation.
- Bảo vệ mã pickup trong DTO Staff và sửa refresh địa chỉ checkout để giữ receiver name/phone.
- Port các test V2 còn bị tắt sang hợp đồng V3 hiện hành; không xoá identity test hay thêm skip để che lỗi.
- Bổ sung 7 flow browser Chrome thật và 3 kiểm tra database V3/BE-06 migration.
- Bổ sung 40 ảnh WebP demo cho dữ liệu seed hiện tại. Nguồn và SHA-256 được lưu tại [V3_BE07_DEMO_IMAGE_SOURCES.csv](V3_BE07_DEMO_IMAGE_SOURCES.csv); đây là ảnh minh hoạ stock, không phải ảnh packshot sản phẩm đã xác thực.

## Quyết định và giới hạn đã chốt

Migration `20261014_be06_review_uniqueness.sql` là migration bảo toàn dữ liệu đã được duyệt: thay `UNIQUE(user_id, product_id)` bằng unique filtered cho `order_item_id` hiện tại và cặp user/product của Review legacy không có nguồn. Vì Review cùng SKU ở các OrderItem khác nhau có thể hợp lệ, không thể quay về unique cũ sau khi dữ liệu đó xuất hiện nếu chưa có quyết định xử lý dữ liệu.

Nghiệm thu này là **V3 hiện tại**. Chủ dự án xác nhận V3-T30 backfill từ dataset/backup V2 gốc ngoài phạm vi vì repo không có snapshot V2. Không có tuyên bố đã chạy hay đã xác nhận chuyển đổi dữ liệu V2 sang V3.

## Chứng cứ

- `target/be07-evidence/full-final.log`: Maven full suite cuối.
- `target/be07-evidence/final-reports/`: 85 XML Surefire; `final-cases.json` và `final-comparison.json` là so sánh theo từng test.
- `target/be07-evidence/final-oneshop.txt`, `final-oneshop_test.txt`: snapshot database cuối; SHA-256 trùng với snapshot trước test.
- `target/be07-evidence/final-verify-oneshop_test.log`: 12/12 kiểm tra của `database/05_verify.sql` PASS trên `oneshop_test`.
- [evidence/browser](evidence/browser): `result.json` và screenshot của bảy flow browser cuối.

Chi tiết regression, E2E/security, database và release handover nằm trong năm tài liệu BE-07 còn lại trong thư mục này.
