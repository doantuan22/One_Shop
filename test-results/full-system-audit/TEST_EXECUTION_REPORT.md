# TEST EXECUTION REPORT

| Lần chạy | DB | Tests | Fail | Error | Ghi chú |
|---|---|---:|---:|---:|---|
| Baseline audit | oneshop_v3_verify_test (schema cũ) | 1799 | 1 | 977 | context lỗi `missing column discount_amount` |
| run1 | DB mới | – | – | – | COMPILATION ERROR: `target/classes` lỗi thời; `mvn clean` bị chặn vì `oneshop.jar` bị tiến trình khác khóa → chỉ xóa `target/classes`, `target/test-classes`, không dừng tiến trình đó |
| run3 | DB mới, schema đủ, chưa seed | 2593 | 91 | 1592 | thiếu dữ liệu seed |
| run4 | + seed demo (`02_seed.sql`) | 2593 | 71 | 54 | test viết cho seed kịch bản |
| run5 | + seed kịch bản (nạp sai encoding) | 2593 | 36 | 15 | mojibake do thiếu `-f 65001` |
| run6 | seed kịch bản đúng encoding | 2593 | 20 | 13 | còn 33 |
| run10 | sau sửa test lỗi thời | 2593 | 3 | 12 | 1 lỗi gián đoạn (xem dưới) |
| run14 | `oneshop_fixture_20261025_test` | 2595 | 2 | 12 | 11 test đa phân công chưa sửa |
| run17 | sau sửa đa phân công | 2596 | 2 | 4 | lộ lỗi rò `SET NOCOUNT ON` |
| **run20 (cuối)** | `oneshop_fixture_20261025_test` | **2596** | **1** | **2** | `evidence/fix-run20-final-fixture.log` |

Kết quả cuối: PASS 2593, FAIL 1, ERROR 2, SKIPPED 0. Không `@Disabled`, không xóa test; mọi thay đổi assertion được liệt kê kèm lý do trong FIX_REPORT.md.

## Còn lại (3, BLOCKED)
RealProductSeedIntegrityTest `everyProductHasEvidence`, `databaseMatchesTheSourcedManifest` (thiếu `database/seed-data/products.json`) và `everyProductImageIsOurOwnCreditedAsset` (cùng nguyên nhân; viết cho seed demo).

## Chạy riêng / chạy cùng suite / lặp lại
Suite đầy đủ chạy nhiều lần trên cùng DB (run6, run10, run14) với kết quả ổn định; số đơn trong DB sau suite luôn là 8 (fixture tự khôi phục). **Một lần gián đoạn:** `QaFixProfileDatabaseIntegrationTest.validProfilePatchIgnoresForgedSensitiveFields` trả 403 ở run10; chạy riêng, chạy cặp với lớp liền trước, và ở run14 đều PASS → nhiều khả năng do rò `SET NOCOUNT ON` (FIX-5), không còn thấy ở run20 (1 lần). Chưa thử thứ tự test ngẫu nhiên.

## Chênh lệch số lượng test (1799 vs 2592/2595)
Cùng 117 lớp test được Maven phát hiện ở cả hai lần chạy. Ở lần chạy cũ, lớp bị lỗi context chỉ tính 1 ERROR cho cả lớp (ví dụ `QaAuditRouteDatabaseIntegrationTest`: 1 ERROR so với 141 test thật). Không có test nào bị xóa; run14 có thêm 2 test mới (PaymentProductionRuntimeConfig).
