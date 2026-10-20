> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-07 — Xác minh database và migration

## Database đã kiểm chứng

`oneshop_test` chạy 22 bảng V3. `database/05_verify.sql` được chạy read-only, đổi context rõ ràng sang `oneshop_test`, và PASS 12/12: danh sách bảng, PK, required constraints/index, cột V3, seed/stock, Fulfillment, source Store, money math, inventory math, pickup/refund, Review/Reply và trusted constraints.

`DatabaseV3RegressionIntegrationTest` còn kiểm tra DDL validation, FK/CHECK enabled + trusted, DBCC không có vi phạm, orphan/movement math và migration Review chạy lặp lại.

| Database | SHA-256 snapshot trước | SHA-256 snapshot sau | Kết quả |
| --- | --- | --- | --- |
| `oneshop` | `8C360B2C86F66D66488A6132D2E3A643014C836A66A45F79A0442D6CE13659C8` | cùng giá trị | Không bị test/migration sửa |
| `oneshop_test` | `42ABD83DA25B8138A2BA0F805C7000E009F1DAE9A823E29BDA7FC1EC49D7555F` | cùng giá trị | Cleanup giữ nguyên nội dung 22 bảng |

Snapshot bao gồm row count/checksum từng bảng, index, CHECK và FK. Không coi identity counter tăng là dữ liệu nghiệp vụ thay đổi.

## BE-06 Review uniqueness

`database/migrations/20261014_be06_review_uniqueness.sql` đã được thực thi và chạy lại an toàn trên `oneshop_test`. Nó không update/delete/relink Review hay Reply, kiểm tra duplicate trước khi đổi key, rồi tạo:

- `UX_reviews_order_item`: unique filtered với `order_item_id IS NOT NULL`.
- `UX_reviews_legacy_user_product`: unique filtered với `order_item_id IS NULL`.

`oneshop` vẫn giữ nguyên và **chưa được chạy migration**. Runbook vận hành: backup đã kiểm tra restore → preflight duplicate → chạy migration với database đích chỉ rõ → chạy verify/snapshot. Rollback bằng restore backup; không tự khôi phục `UNIQUE(user_id, product_id)` nếu đã phát sinh Review hợp lệ cho hai OrderItem cùng SKU.

V3-T30 backfill từ V2 không được thực hiện, theo xác nhận phạm vi của chủ dự án. Không có backup/dataset V2 để chứng minh chuyển đổi 19→22 bảng.
