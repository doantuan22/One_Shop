# OneShop — Full System Audit

Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.

**AUDIT COMPLETED. Kết luận: NOT READY theo phạm vi Roadmap V2 chưa được điều chỉnh.** Các luồng P0 về mua hàng, payment mô phỏng, fulfillment, inventory và phân quyền hoạt động trong môi trường đã kiểm chứng. Không phát hiện CRITICAL/HIGH trong các scenario đã chạy. Ba finding MEDIUM vẫn mở; audit không sửa lỗi.

| Severity | Confirmed findings |
| --- | ---: |
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 3 |
| LOW | 0 |

| ID | Finding | Ý nghĩa trước Phase 13 |
| --- | --- | --- |
| FSA-01 | Customer không có luồng đánh giá sau COMPLETED; ReviewService/Impl là skeleton | Thiếu chức năng tác nhân Customer trong Roadmap §5/§8.1, P1. Không phải lỗi Admin review hoặc lỗi stock. Cần hoàn thiện hoặc ghi rõ quyết định hoãn P1; không tuyên bố full roadmap complete |
| FSA-02 | Mật khẩu Unicode 30 ký tự / 90 byte vượt BCrypt, đăng ký trả HTTP 500 | Input hợp lệ theo DTO được đưa tới encoder không được guard. Cần validation có thông báo đúng trước public demo signup |
| FSA-03 | Dockerfile/render.yaml/README chọn Docker trái giới hạn Roadmap | Điều kiện scope cho Phase13 “không thêm Docker chỉ để deploy” chưa đạt. Cần chốt cấu hình tuân thủ roadmap hoặc phê duyệt thay đổi phạm vi; audit không tự đổi platform |

Chi tiết tái hiện, dòng source, root cause, mức tác động và hướng sửa tối thiểu: [06_Issue_Register.md](06_Issue_Register.md). NOT READY là kết luận về completeness/scope hiện tại, **không** là tuyên bố đã tìm thấy lỗi nghiêm trọng trong giao dịch.

Kiểm chứng trên Windows, Java 21, Maven 3.9.16, Spring Boot 3.5.16, SQL Server `localhost:1433/oneshop`, Standard Developer Edition 17.0.1000.7. Server audit chỉ bind `127.0.0.1:8787`, profile dev; Chrome headless 155 qua CDP `127.0.0.1:9224`. Hai tiến trình do audit tạo đã dừng.

Các mutation dùng test fixture/SeedGuard hiện hữu hoặc fixture mới mang marker `AUD261009FSA`, ba Store mới và hai User mới. Không dùng stock/cart/order hiện hữu cho hành trình ghi dữ liệu. Cleanup chỉ nhắm fixture; [db-comparison.json](evidence/db-comparison.json) xác nhận 19/19 bảng giữ nguyên số hàng và hash toàn bộ nội dung, bao gồm IDs/timestamps. IDENTITY có thể tăng do fixture; không reset counter. [source-comparison.json](evidence/source-comparison.json): 353 file tracked nguyên trạng, Git chỉ có thư mục audit mới.

## Regression và acceptance

Lệnh thực chạy: `mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' clean package`.

**927 TOTAL, 926 PASS, 0 FAIL, 0 ERROR, 1 SKIP; BUILD SUCCESS**, 44 suites. 33 test Phase12 hardening PASS, 0 SKIP. Baseline 928 có thêm `Phase12CloudinaryLiveVerification`, ngoài Surefire default patterns. Audit không chạy class opt-in này vì user cấm live upload/delete khi chưa cấp phép. Chênh lệch không do sửa/bỏ test. Một SKIP cũ là nhánh Cloudinary *chưa configured* khi environment hiện tại đã configured.

TC-01–TC-16 và TC-18: **17 PASS** từ regression SQL/HTTP vừa chạy, bổ sung UI journey. TC-17: **NOT VERIFIED cho việc live rerun hiện tại**; adapter mock + SQL PASS, có historical Product live PASS, Brand live riêng chưa xác minh. Không gộp test lịch sử vào tổng test hiện tại. [Ma trận đầy đủ](05_Integration_Test_Coverage.md).

## Phạm vi và nguồn chuẩn

Đối chiếu Roadmap V2 đủ BR-01–18, flows §6, schema §7, service/UI §8–9, P0/P1/P2 §11, 13 giai đoạn, TC-01–18 và scope prohibitions. Đã đọc [Roadmap](../../OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang%20%281%29.md), [13 giai đoạn](../../OneShop_PhanChiaGiaiDoan_ThucHien_TheoRoadmapV2.md), README, database README/01–05 SQL, các phase reports hiện hữu và test/source liên quan. Không chạy 00_run_all/schema/seed.

Có 12 reports: Phase9.1–9.5, Phase10.1–10.5, Phase11, Phase12, cùng audit Phase11/12. **Không tìm thấy báo cáo riêng Phase1–8**; các giai đoạn này đối chiếu bằng README/schema/source/tests vừa chạy, không suy đoán nội dung tài liệu thiếu. Inventory liệt kê chính xác file report.

Các giới hạn: chưa chạy app trên Render/SQL Server cloud/HTTPS prod; không browser-test live upload/brand replace; chưa benchmark tải lớn, execution plans, mọi lịch deadlock, image primary concurrency, full accessibility bằng assistive technology. Không security certification/dependency CVE scan. Các hạng mục này ghi NOT VERIFIED, không được chuyển thành finding suy đoán hoặc PASS từ baseline.

| Evidence | Nội dung / mức kiểm chứng |
| --- | --- |
| [inventory.json](evidence/inventory.json), [inventory.md](evidence/inventory.md) | 97 action mapping annotations (98 verb/path pairs do `/staff` có alias `/staff/`), 19 entity/table, 54 templates/fragments/layouts, 38 service Java files, 52 test/support files. Các số này không đồng nghĩa mọi file là một màn hình hoặc suite chạy |
| [regression-summary.json](evidence/regression-summary.json) | 44 suites / 927 test executions, từng method và PASS/FAIL/ERROR/SKIP từ Surefire XML vừa chạy |
| [build-summary.json](evidence/build-summary.json) | Lệnh thật, BUILD SUCCESS, 15:42:46 +07:00, 02:03 phút, artifact/hash |
| [database-inspection.json](evidence/database-inspection.json), [database-summary.json](evidence/database-summary.json), [mapping-comparison.json](evidence/mapping-comparison.json) | SELECT actual schema/constraints/indexes; đối chiếu 141 JPA fields (kể cả inherited) với141 SQL columns, không drift về type/nullability/length/decimal precision-scale |
| [browser-survey.json](evidence/browser-survey.json), [browser-network.json](evidence/browser-network.json) | 46 route/ngữ cảnh × 3 viewport = 138 DOM surveys, actual HTTP/network và trạng thái decorator/form |
| [browser-journey.json](evidence/browser-journey.json) | 74 records, Customer/Staff/Admin đi qua UI production và SQL thật; ghi rõ fixture IDs, kết quả mỗi bước |
| [browser-additional.json](evidence/browser-additional.json), [browser-additional-network.json](evidence/browser-additional-network.json) | 19 records: race Admin/Staff, JWT cookie flags đã bỏ giá trị, user disable, CSRF/ownership, XSS, filter forms, validation, mobile toggle |
| [fixture-db.json](evidence/fixture-db.json), [journey-db-checks.json](evidence/journey-db-checks.json) | DB sau UI mutations, trước cleanup; 14 assertions nghiệp vụ đều true, không có password/hash/JWT |
| [screenshots/](evidence/screenshots/) | 137 screenshots thực tế; không phải mockup |
| [historical-cloudinary.json](evidence/historical-cloudinary.json) | Trích log Phase12 cũ: live Product upload/provider/CDN/delete 1 PASS, 15:11:16 +07:00; **không rerun** |
| [secret-log-scan.json](evidence/secret-log-scan.json) | Không thấy giá trị DB_PASSWORD/JWT_SECRET/Cloudinary key/secret đang cấu hình trong audit logs; exact matching ≥8 ký tự, không chứng minh không có mọi loại secret |

## Trả lời tám câu hỏi của yêu cầu

1. **Mức hoàn thiện:** BR-01–18 và P0 core được xác minh; chưa full Roadmap vì Customer review P1 thiếu và deploy strategy trái giới hạn Docker.
2. **Xuyên suốt:** catalog → cart ba Store → checkout ba Order → ONLINE demo/COD/PAY_AT_STORE → delivery/pickup → COMPLETED chạy qua Chrome; cancellation/repeat cũng đúng. Nhánh review sau completed bị đứt.
3. **Frontend/backend:** các form core, filter, CSRF, snapshot và state hiển thị đồng bộ với SQL. Signup Unicode lệch validation và thiếu review UI/backend. Không thấy overflow toàn trang trong 138 khảo sát viewport.
4. **Database:** 19 bảng, 26 FK/40 CHECK đều enabled/trusted; các query integrity không có vi phạm. Atomicity/race/rollback tests PASS và UI fixture ledger đúng. Không khẳng định an toàn cho mọi tải/môi trường khác.
5. **CRITICAL/HIGH:** không finding confirmed ở hai mức này trong phạm vi kiểm chứng.
6. **Thiếu/chưa xác minh:** FSA-01–03; TC17 current live và Brand live; cloud/prod deployment, scale/performance, accessibility nâng cao chưa xác minh.
7. **Trước Phase13:** xử lý signup FSA-02; đóng quyết định phạm vi FSA-03; hoàn thiện review hoặc ghi nhận hoãn P1 FSA-01. Sau remediation chạy lại coverage liên quan/full regression, giữ dữ liệu hiện có. Live/cloud kiểm chứng khi được phép ở môi trường demo.
8. **Go/No-Go:** **NOT READY** theo roadmap hiện tại. Không deploy hoặc triển khai Phase13 trong audit. AUDIT COMPLETED ≠ SYSTEM PASSED.
