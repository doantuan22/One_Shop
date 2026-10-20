> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-07 — Release readiness và bàn giao

## Trạng thái

**READY cho nghiệm thu current-V3 trên môi trường test/demo.** Full regression xanh, E2E browser hoàn tất, integrity database PASS và dữ liệu sau test trùng snapshot trước test.

**Chưa triển khai vận hành:** `oneshop` chưa chạy Review uniqueness migration và không có deploy/backup/restore nào được thực hiện trong BE-07. Cần phê duyệt vận hành riêng trước khi áp dụng migration vào database đó.

## Handover vận hành

1. Backup database vận hành và kiểm thử restore.
2. Chạy preflight duplicate Review; dừng nếu phát hiện duplicate thay vì sửa/xóa tự động.
3. Chạy `20261014_be06_review_uniqueness.sql` với tên database đích được chọn rõ ràng.
4. Chạy `05_verify.sql`, snapshot checksum và smoke flow ONLINE/PICKUP/Review.
5. Khi rollback, restore backup đã kiểm thử. Không downgrade key Review theo cách có thể mất dữ liệu.

Production giữ Mock Payment Gateway disabled. Các property ONLINE, timeout worker và fixture seed chỉ được bật trong test/demo đúng scope.

## Compatibility và ranh giới

| Hành vi cũ | Chính sách V3 hiện tại |
| --- | --- |
| Payload checkout nhóm V2 | Bị từ chối; dùng checkout/allocation V3 |
| Generic Order status mutation | Chặn khi Order có Fulfillment |
| Cart gắn StoreProduct | Compatibility read tồn tại khi cần, nhưng cart authority là Product trung tâm |
| Review unique user/SKU | Legacy không có nguồn còn được giữ; Review có nguồn unique theo OrderItem |
| Staff/Admin reply | Staff ACTIVE đúng Store trả lời; Admin moderation |
| Browser payment success/failure | Không được tin; chỉ gateway proof server-side hợp lệ |

## Tài liệu và chứng cứ bàn giao

- Báo cáo triển khai: `V3_BE07_IMPLEMENTATION_REPORT.md`.
- Kết quả test: `V3_BE07_FULL_REGRESSION_RESULTS.md` và `target/be07-evidence/full-final.log`.
- Security/browser: `V3_BE07_E2E_AND_SECURITY_REPORT.md` và `evidence/browser/`.
- Database/migration: `V3_BE07_DATABASE_MIGRATION_VERIFICATION.md`.
- Nguồn ảnh demo: `V3_BE07_DEMO_IMAGE_SOURCES.csv`.

Các hình demo là stock illustration có provenance, không khẳng định là ảnh sản phẩm thương mại thực. Tích hợp Cloudinary ở test không upload ra ngoài vì credentials bị blank trong context test.
