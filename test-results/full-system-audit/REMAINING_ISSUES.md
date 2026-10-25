# REMAINING ISSUES

## (Đã giải quyết) 11 test đa phân công
Đã viết lại theo quy tắc 1 nhân viên – 1 chi nhánh, xem FIX_REPORT.md FIX-4.

## BLOCKED-1 — thiếu `database/seed-data/products.json`
- README và `RealProductSeedIntegrityTest` tham chiếu tệp này nhưng **chưa từng có trong git** (và `scripts/seed/` cũng không còn). Không tự tạo vì sẽ làm giả nguồn dữ liệu. 3 test bị ảnh hưởng (`everyProductHasEvidence`, `databaseMatchesTheSourcedManifest`, `everyProductImageIsOurOwnCreditedAsset`). Cần chủ dự án cung cấp manifest/generator.

## Khác
- QaFixProfile 403 gián đoạn: không tái hiện trong lần full suite duy nhất chạy sau FIX-5 (run20) (nguyên nhân nhiều khả năng là rò session, chưa chứng minh trực tiếp).
- `mvn clean` bị chặn bởi tiến trình ngoài khóa `target/oneshop.jar` (có thể là app live trên DB `oneshop`); không dừng nó.
- `docs/`, `scripts/qa-audit/`, `test-results/` đang untracked; test QaFixUi/QaAudit ghi evidence vào `docs/qa-audit/evidence`.
- Suite và seed demo dùng hai seed khác nhau, hiện tách bằng `database/test-fixtures/`; nên chốt cấu trúc này.
- Chưa có thứ tự test ngẫu nhiên, chưa có CI.
- Các thay đổi `application*.properties`, đổi email test sang `@example.com` đã có sẵn trong working tree từ trước (chưa commit); tôi không commit gì.
