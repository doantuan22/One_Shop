# Final audit summary — phạm vi chưa hoàn tất exhaustive

**Đã chạy backend, SQL Server và Chrome thật; đã chứng minh 8 bug. Chưa đạt điều kiện tuyên bố hoàn tất toàn bộ phạm vi exhaustive. Không có thay đổi production code.**

## Số liệu thực tế

| Metric | Result |
|---|---|
| Source files structurally inventoried | 518 |
| Controller route declarations | 115 |
| Runtime mapping records / unique verb-path | 118 / 117 |
| Database tables / repositories | 23 / 23 |
| Enum/action/state declarations | 34 |
| Heuristic branch lines / UI candidate lines | 1430 / 1113 |
| Semantic functions / all flows / all branches | Chưa đủ semantic reconciliation để có denominator chính xác |
| Primary Surefire records | 2225 |
| PASS / FAIL / ERROR / SKIPPED | 2140 / 72 / 10 / 3 |
| BLOCKED interpretation | 10 fixture errors + 3 live Cloudinary skipped; không cộng hai lần vào raw totals |
| Guard probes / matching expected status | 307 / 307 |
| Latest Chrome screen checkpoints | {'PASS': 59, 'FAIL': 3} |
| Cross-module Chrome journeys | 7 modes PASS, verified SQL; included in baseline, not added twice |
| Generated allocation cases | 3 seeds × 1,000 cases; 3 JUnit invocations, unit-level not E2E |
| Line coverage | {'covered': 5642, 'total': 6206, 'percent': 90.91} |
| Branch coverage | {'covered': 2329, 'total': 3128, 'percent': 74.46} |
| Not-run semantic cases | UNKNOWN, không tự suy đoán từ source marker counts |

## Bug đã xác nhận

| Severity | Count | Bugs |
|---|---|---|
| CRITICAL | 1 | QA-001: profile khôi phục account ACTIVE / role vừa bị Admin revoke |
| HIGH | 1 | QA-004: rollback metadata nhưng ảnh cũ đã bị xóa tại storage boundary |
| MEDIUM | 5 | QA-002 primary image race; QA-003 orphan upload; QA-005 page overflow; QA-006 date overflow; QA-008 category image 404 |
| LOW | 1 | QA-007 double-escaped catalog browser title |

Số FAIL không bằng số bug: một bug có nhiều input/lần lặp; baseline còn các assertion/fixture lệch policy. Chi tiết tái hiện, before/after SQL, root cause và remediation trong BUG_REPORT.md.

## Mô hình và khả năng vận hành

Runtime là V3 thật: giá Product, cart Product, central ONLINE Order nhiều Fulfillments, consent COD split. Không tự triển khai/chuyển đổi nghiệp vụ. Staff hiện có tối đa một assignment ACTIVE theo SQL và service; 11 scenario baseline cũ mâu thuẫn policy này (10 ERROR, một FAIL). Các khác biệt UI text/finder chưa bị đánh thành runtime business bug khi thiếu spec xác nhận.

Các hành trình checkout/payment/fulfillment/review chính đã chạy với SQL checks, nhưng không đủ để kết luận an toàn vận hành. QA-001 trực tiếp phá bảo đảm thu hồi truy cập; nên ưu tiên chặn trước khi đưa hệ thống vào sử dụng với tài khoản thật. Ảnh/rollback và reader boundary vẫn cần khắc phục. Không có kết luận an toàn cho payment/refund thật vì dự án chỉ có simulation.

## Ưu tiên sửa

1. QA-001: profile chỉ patch cột được phép, dùng lock/version phù hợp với Admin, kiểm tra revoke race.
2. QA-004 / QA-003: lifecycle external asset gắn commit/rollback và retry compensation.
3. QA-002: serialize primary image selection + DB uniqueness.
4. QA-005 / QA-006: validate page offset/date theo giới hạn kỹ thuật, trả 4xx hợp lý.
5. QA-008 / QA-007: sửa asset reference có xác nhận và title escaping.
6. Chốt tài liệu hiện hành, sửa fixture/assertion cũ theo policy đã phê duyệt; không bỏ constraint để che test lỗi.

## Phần chưa hoàn tất / chưa đủ bằng chứng

- Toàn bộ 1.430 source decision candidates chưa được map semantic từng true/false/AND/OR sang execution. JaCoCo đo bytecode branch, không chứng minh đầy đủ business decision coverage.
- 1.113 UI candidate lines đã inventory nhưng chưa tất cả có thao tác browser riêng; page render không phải mọi button/modal/AJAX đã test.
- Mọi failure point, transport loss sau commit/retry, mọi stale CRUD và mọi cặp concurrency theo prompt chưa có full execution mapping. Các race đã test không được suy rộng cho mọi module.
- Live Cloudinary: có request đọc asset từ browser và phát hiện 404; upload/delete/rollback provider thật chưa test vì không có tenant test được xác định. Ba live cases SKIPPED được công khai.
- Scheduler engine đã test trực tiếp, nhưng scheduler cadence thực tế giữ OFF để tránh background writes ngoài fixture.
- Thiếu các tài liệu V3/runbook được README tham chiếu. Không khẳng định đầy đủ mọi business DEC khi thiếu tài liệu gốc.

## Bằng chứng và tái chạy

`TEST_EXECUTION_REPORT.md`, `FLOW_TEST_MATRIX.md`, `SYSTEM_INVENTORY.md`, `COVERAGE_REPORT.md`; JSON/XML/CSV/screenshots và manifest SHA-256 trong `evidence/`. Raw Maven/Surefire/HTML coverage trong `target/`. Database audit được giữ lại, không drop; fixture phục hồi nội dung, identity counters có thể tăng.

Runner: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/qa-audit/run.ps1 -Phase baseline|audit|reproduce|coverage|package` (chọn một Phase mỗi lần, không chạy hai writer suites trên cùng DB). `database.ps1 -Action provision` chỉ dành cho tên DB mới chưa tồn tại; script từ chối database có sẵn.
