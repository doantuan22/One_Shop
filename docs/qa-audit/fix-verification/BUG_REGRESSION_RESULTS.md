# Bug regression results

**Environment:** SQL Server thật trên database test riêng; embedded HTTP server; Chrome headless qua CDP. Storage cho QA-003/004 là test double, không phải Cloudinary live. Chi tiết đầu vào, output và timestamp được giữ trong JSON evidence.

| Scope | Phase/evidence | Kết quả | Kết luận |
|---|---|---:|---|
| Profile selective-update unit/controller | `evidence/qa001/results.json` | 12/12 PASS | Regression assertion hoạt động. |
| QA-001/002 concurrency và race | `evidence/qa002/results.json` | 39/39 PASS | Race HTTP/SQL cho profile và race chọn primary được giữ; SQL unique index được xác minh. |
| QA-003/004 lifecycle/rollback | `evidence/qa003-004-final/results.json` | 42/42 PASS | Commit/rollback, retry, shared reference và metadata faults qua SQL thật + storage double. |
| QA-005 pagination | `evidence/qa005-rerun/results.json` | 667/667 PASS | HTTP probes có/không keyword, routes cũ và `/admin/stores`. |
| QA-006 date boundaries | `evidence/date-final2/results.json` | 424/424 PASS | Ngày 0001, thường, 9999; end-of-day inclusive, range đảo, Orders/Payments và attention. |
| QA-007/008 browser | `evidence/qa007-008-rerun/results.json` | 2/2 PASS | UI forms, title/text/XSS, category imagery/fallback/network. |
| Focused aggregate cũ | `evidence/focused-rerun/results.json` | 721 PASS / 1 FAIL | Lỗi duy nhất do seed assertion chạy trên DB fixture cũ; sau đó test được điều chỉnh để phân biệt DB canonical seeded và DB schema-only. Không xóa kết quả cũ. |
| Full suite trước cập nhật screen expectation | `evidence/full-suite/results.json` | 429 PASS / 1 FAIL | Chỉ screen test dùng kỳ vọng `&amp;` cũ; screen assertion đã được sửa theo plain-text title. |
| Full suite cuối | `evidence/full-suite-final/results.json` + `target/qa-fix/full-suite-final.log` | 2,517 invocations; 8 failures, 10 errors | JSON aggregator ghi 430/430 PASS nhưng Maven log báo BUILD FAILURE; dùng Maven/Surefire làm nguồn verdict. Xem `FULL_REGRESSION_REPORT.md`. |

## Phân loại lỗi ngoài regression

- Mười test legacy multi-assignment xung đột với constraint một ACTIVE Store assignment/Staff (`UX_ssa_one_active_store_per_user`). Giữ nguyên constraint và test; không tính là sửa QA hay làm lỏng authorization. Tám assertion failures còn lại liên quan kỳ vọng cũ về image-delete timing/mock, legacy finder, text/status UI; xem report full-suite.
- Không có xác minh Cloudinary live. Cleanup behavior chỉ được kiểm tra với storage double; thanh toán ngân hàng thật không nằm trong test.
