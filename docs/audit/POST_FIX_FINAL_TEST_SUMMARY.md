# FINAL TEST SUMMARY — OneShop V3 (cập nhật sau khi sửa test đa phân công)

**Kết luận: PARTIALLY VERIFIED.** 2.593/2.596 test PASS. 3 test còn lại BLOCKED vì thiếu tệp `database/seed-data/products.json` (chưa từng có trong git); không có lỗi sản phẩm đã biết nào chưa xử lý.

| Metric | Before | After |
|---|---:|---:|
| Total tests | 1799 | **2596** |
| PASS | 821 | **2593** |
| FAIL | 1 | **1** |
| ERROR | 977 | **2** |
| SKIPPED | 0 | **0** |
| Browser E2E PASS | Chưa hoàn thành | **7/7 luồng BrowserV3 + 2 QaFixUi + QaAuditScreen + 135/135 responsive capture** |
| SQL integrity | BLOCKED | **PASS** (DATABASE_INTEGRITY_REPORT.md) |

Lệnh: `./mvnw.cmd -B test -Doneshop.test.db=oneshop_fixture_20261025_test` → `Tests run: 2596, Failures: 1, Errors: 2, Skipped: 0` (`evidence/fix-run20-final-fixture.log`).

- 11 test đa phân công đã viết lại theo quy tắc "1 nhân viên – 1 chi nhánh" (chuyển chi nhánh thay vì hai phân công ACTIVE), thêm test Admin `/transfer` thật. Chi tiết trong FIX_REPORT.md.
- Phát hiện và sửa thêm lỗi cô lập test: `DatabaseV3RegressionIntegrationTest` chạy migration làm rò `SET NOCOUNT ON` sang connection pool, khiến test sau đó (Delivery, Phase10…) và khả năng cao cả lần 403 gián đoạn của `QaFixProfile` lỗi tùy thứ tự. Sau sửa, lần chạy full run20 không còn tái hiện (mới 1 lần, cần chạy lặp thêm để chắc chắn).
- Còn lại: 3 test `RealProductSeedIntegrityTest` BLOCKED (REMAINING_ISSUES.md).
- Chênh lệch số test (1799 vs 2596): cùng 117 lớp; lớp lỗi context trước đây chỉ tính 1 ERROR cho cả lớp. Không test nào bị xóa.
