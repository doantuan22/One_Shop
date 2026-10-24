# Bug fix summary — OneShop V3

**Ngày xác minh:** 24/10/2026. **Runtime:** V3. **Database test:** `oneshop_fix_full_20261024_test` (SQL Server). Không chạy thao tác ghi vào production. Thay đổi chưa commit.

| Bug | Kết quả hiện tại | Implementation / bằng chứng chính |
|---|---|---|
| QA-001 — profile ghi đè trạng thái/quyền Admin | **FIXED; focused VERIFIED** | Cập nhật chọn lọc `full_name`/`phone` trên account đang ACTIVE, không merge entity cũ. Regression ở `evidence/qa001/results.json` (12/12); race HTTP/SQL nằm trong `evidence/qa002/results.json` (39/39). |
| QA-002 — nhiều ảnh primary | **FIXED; focused VERIFIED** | Khóa Product cho mọi mutation ảnh, tuần tự hóa flush và filtered unique index; migration fail-closed không tự chọn/xóa dữ liệu trùng. SQL Server concurrency/regression trong `evidence/qa002/results.json` (39/39). |
| QA-003 — upload mồ côi sau rollback | **FIXED; storage-double + SQL verified** | Compensation khi rollback, cleanup journal và retry Admin có chủ đích. `evidence/qa003-004-final/results.json` (42/42). Cloudinary live chưa xác minh; khoảng crash trước khi ghi callback vẫn cần reconciliation. |
| QA-004 — xóa asset cũ trước commit | **FIXED; storage-double + SQL verified** | Xóa asset cũ sau commit; bảo vệ tham chiếu dùng chung; cleanup lỗi ghi journal/retry. Cùng evidence `qa003-004-final` (42/42). |
| QA-005 — page offset gây 500 | **FIXED; HTTP/SQL Server verified** | Paging policy dùng chung kiểm tra offset/size ở controller và service, kể cả nhánh search; negative page giữ chuẩn hiện hữu. `evidence/qa005-rerun/results.json` (667/667). |
| QA-006 — ngày 9999 gây 500 | **FIXED; HTTP/SQL Server verified** | Day range inclusive an toàn với SQL `datetime2`, validate range trước các nhánh Orders/Payments. `evidence/date-final2/results.json` (424/424). |
| QA-007 — title escape hai lần | **FIXED; Chrome verified** | SiteMesh title được decode entity một lần, layout tiếp tục `th:text` escape output. `evidence/qa007-008-rerun/results.json` (2/2), browser checks plain text, tiếng Việt/ký tự `&`, literal script không thực thi. |
| QA-008 — ảnh category seed hỏng | **FIXED; Chrome/local resource verified** | Seed canonical dùng placeholder SVG nội bộ, fallback lỗi ảnh không lặp request. Cùng browser evidence (2/2), naturalWidth/network assertions. Không đoán URL hoặc gọi Cloudinary live. |

## Giới hạn trạng thái

Các “VERIFIED” trên là kết quả focused regression tương ứng; full suite không sạch. `evidence/full-suite-final/results.json` ghi 430/430 PASS nhưng Maven/Surefire log `target/qa-fix/full-suite-final.log` báo 2,517 invocations, 8 failures, 10 errors và BUILD FAILURE; aggregator bỏ sót invocation lỗi nên không dùng làm verdict. Phân loại trong `FULL_REGRESSION_REPORT.md`. Không đổi policy một ACTIVE Store/Staff và không tuyên bố Cloudinary live behavior đã kiểm thử.
