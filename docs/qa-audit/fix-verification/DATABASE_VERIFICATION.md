# Database verification

## Phạm vi và an toàn

Verification sử dụng SQL Server test riêng, không sửa database production. Full regression database là `oneshop_fix_full_20261024_test`; browser seed database là `oneshop_fix_seed_20261024_test`. Hibernate chạy validate. Không chạy Cloudinary writes; không gọi payment/refund provider thật.

## Schema và migrations

Full test DB được dựng từ canonical `database/01_schema.sql` + `database/02_seed.sql`, sau đó áp dụng các migration lịch sử review uniqueness/payment refunds cùng `database/04_image_cleanup_tasks.sql` và `database/05_product_primary_image_unique.sql`. Runner lưu log/inspection dưới `evidence/oneshop_fix_full_20261024_test/`. Schema/index của QA-002/003/004 được xác minh bởi regression integration tests; unique primary index ngăn nhiều primary mà không tự sửa dữ liệu trùng.

## Assertions bằng SQL thật

- QA-001: profile chỉ cập nhật hai trường được phép trong khi Admin status/role revocation được commit độc lập.
- QA-002: concurrent primary selection kết thúc với đúng một primary; unique index là hàng rào cuối ở database.
- QA-003/004: rollback/commit xác nhận SQL metadata nhất quán với storage double; cleanup task được lưu khi cần retry; shared references không bị xóa.
- QA-006: rows được đặt tại các biên `0001-01-01`, ngày thông thường và `9999-12-31`, gồm cuối ngày; Orders/Payments query trả đúng kết quả, không tạo upper bound năm 10000.
- QA-008: canonical seed kiểm tra trên DB fresh; category placeholder SVG tồn tại nội bộ và tải được ở browser. Database schema-only cũ không được dùng để khẳng định nội dung seed.

Evidence tổng hợp theo từng phase: `evidence/qa001/results.json`, `qa002/results.json`, `qa003-004-final/results.json`, `qa005-rerun/results.json`, `date-final2/results.json`, `qa007-008-rerun/results.json`. Đây là verification database test, không phải audit data production.
