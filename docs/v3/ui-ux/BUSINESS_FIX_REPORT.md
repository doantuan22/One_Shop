# Báo cáo khắc phục vấn đề nghiệp vụ còn tồn đọng — OneShop V3 (20/10/2026)

Phạm vi: chỉ 3 hạng mục của `QA_FIX_REPORT.md` (worker, "Cần xử lý" của Dashboard Admin, danh sách Payment Admin). Không làm UI/UX 49 màn hình, không đổi nghiệp vụ V3 đã chốt. Mọi thao tác ghi dữ liệu chỉ trên các DB test dựng mới (đã xóa); `oneshop` và `oneshop_test` không bị đụng.

## 1. Vấn đề ban đầu → nguyên nhân gốc → quyết định
### P0 — Payment Timeout Worker
- **Vấn đề:** ở đợt trước tôi đặt `timeout-worker-enabled` mặc định `true`. Worker *ghi* dữ liệu (hủy đơn ONLINE quá 15 phút, hoàn tồn); mở app trên DB có sẵn đơn quá hạn (vd. đơn #8 của seed trong `oneshop`) sẽ tự hủy đơn đó.
- **Nguyên nhân gốc:** mặc định bật chung cho mọi môi trường.
- **Quyết định áp dụng:** worker **tắt mặc định ở mọi profile**, chỉ bật khi cấu hình chủ động `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=true`. Khi tắt, đơn ONLINE quá hạn hiện ở Admin → Thanh toán → "Cần xử lý" để Admin "Đối soát lại" từng đơn (cùng quy tắc, cùng khóa Order, idempotent) — đã kiểm chứng. Ghi log rõ trạng thái worker khi khởi động. Khi bật, logic hạn/hoàn tồn một lần/race/idempotency **không đổi** (đã có BE05-09..49 và tôi chạy lại E2E với worker bật).
- **Ghi chú quan trọng phát hiện khi kiểm tra:** `pom.xml` (surefire) ép `oneshop.payment.timeout-worker-enabled=false` bằng system property cho JVM test ⇒ test không thể phát hiện mặc định sai; vì vậy test mới đọc trực tiếp `application*.properties`.

### P1 — Dashboard Admin "Cần xử lý"
- **Vấn đề:** cột/KPI "Cần xử lý" đếm 4 trạng thái Order *đang được Staff xử lý bình thường* (CONFIRMED, PROCESSING, SHIPPING, PARTIALLY_COMPLETED) và bỏ sót thứ Admin thực sự phải làm; chú thích còn sai.
- **Định nghĩa áp dụng (suy ra từ DEC-17/19, §10.5 của đặc tả, đã thống nhất với danh sách Payment của Admin):** "Cần Admin xử lý" = Order `NEEDS_ATTENTION` (mọi phương thức) **hoặc** Order ONLINE vẫn `PENDING_PAYMENT` sau hạn thanh toán **hoặc** khoản đã thu có hoàn tiền mô phỏng `SIMULATION_FAILED/PENDING_SIMULATION`. Đơn Staff đang xử lý bình thường không còn tính.
- **Số liệu:** KPI toàn chuỗi đếm mỗi Order đúng một lần; bảng chi nhánh đếm Order có phần giao tại chi nhánh đó (một Order nhiều Store hiện ở từng Store và được ghi chú rõ). Thêm KPI "Chờ đối soát" (thông tin, không phải việc của Admin). *Dự phòng cần xác nhận:* tôi coi định nghĩa trên là đủ rõ theo đặc tả nên đã triển khai; nếu chủ dự án muốn khác (vd. không tính "quá hạn nhưng còn trong cửa sổ đối soát"), chỉ cần đổi một hằng JPQL `ATTENTION`.

### P1 — Danh sách Payment Admin
- **Vấn đề:** danh sách chỉ có nhóm "cần xử lý"; Payment `UNKNOWN` trước hạn không thấy được, không có lọc/tìm kiếm, nhãn trạng thái thô.
- **Nguyên nhân gốc:** truy vấn `findPaymentCasesForAdmin` chỉ trả tập "cần can thiệp"; không có phân loại dùng chung.
- **Sửa:** một phân loại duy nhất `PaymentCaseCategory` (Cần xử lý / **Chờ đối soát** / Đang chờ thanh toán / Đã thanh toán / Đã hoàn tiền (mô phỏng) / Thất bại – đã hủy), loại trừ nhau, dùng chung cho **danh sách, chi tiết và Dashboard**; tab lọc có số đếm, tìm theo mã đơn / tên / email (ký tự `%`, `_` là ký tự thường). UNKNOWN còn trong hạn → "Chờ đối soát", không phải việc của Admin; chỉ thành "Cần xử lý" khi quá hạn hoặc `NEEDS_ATTENTION`. Mọi thao tác đọc **không** đổi trạng thái (có test).

## 2. File thay đổi
- Cấu hình: `application.properties`, `config/PaymentProperties.java`, `config/PaymentWorkerStatusLogger.java` (mới), `.env.example`, `README.md`.
- Phân loại/Dashboard/Payment: `service/payment/PaymentCaseCategory.java` (mới), `repository/OrderRepository.java` (truy vấn phân loại, đếm), `service/payment/AdminPaymentService.java`, `service/AdminOperationsService.java`, `controller/admin/AdminPaymentController.java`, `dto/response/AdminPaymentCaseResponse.java`, `AdminChainSummaryResponse.java`, `AdminStoreOverviewResponse.java` (`processingOrders`→`attentionOrders`).
- Giao diện (chỉ phần cần thiết): `admin/dashboard.html`, `admin/payments/index.html`, `admin/payments/detail.html`, `fragments/components.html` (badge), `fragments/admin.html` (nhãn menu).
- Lỗi phát hiện khi làm: tên phương thức `param()` của enum bị Thymeleaf chặn (`param` là biến đặc biệt) → đổi thành `key()`.

## 3. Test
- **Mới (16):** `AdminAttentionAndPaymentCasesDatabaseIntegrationTest` (11: trong hạn, quá hạn, NEEDS_ATTENTION, trạng thái cuối, hoàn tiền mở, việc bình thường của Staff, bộ lọc chia hoàn toàn + số đếm khớp danh sách, Dashboard khớp console và SQL độc lập, tìm kiếm, đọc không ghi, phân quyền); `PaymentWorkerDefaultsTest` (5: mặc định tắt ở mọi profile, bật chủ động, bean không tạo khi tắt, prod đóng ONLINE).
- **Test sửa có lý do:** `Phase11AdminDatabaseIntegrationTest` (định nghĩa `attentionOrders` mới, SQL độc lập), `LayoutsIntegrationTest` (constructor + nhãn). Không bỏ/yếu hóa assertion nào.
- **Toàn bộ suite trên DB test mới: 1610 test — 1610 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED** (gồm integration, concurrency và E2E trình duyệt). (Một lần chạy trước bị tôi ngắt vì sửa mã giữa chừng, không tính.)

## 4. HTTP/E2E thực tế (DB test riêng, ứng dụng chạy thật)
- Mặc định (worker tắt): **99/99** — chờ 75 giây không có ghi nền (đơn #8 quá hạn không bị hủy, tồn không đổi); console/lọc/tìm kiếm/phân quyền; UNKNOWN trong hạn → "Chờ đối soát", không vào "Cần xử lý", Dashboard +1 đối soát/0 attention; quá 16 phút → "Cần xử lý" (+2); Admin đối soát lại đơn im lặng quá hạn → hủy, hoàn tồn đúng một lần, lặp lại idempotent; UNKNOWN sau cửa sổ → NEEDS_ATTENTION → Admin "không thu" → hủy + hoàn tồn một lần; paid/refunded/failed đúng nhóm; COD NEEDS_ATTENTION tính ở Dashboard nhưng không nằm ở console Payment; trạng thái Staff xử lý (PROCESSING, SHIPPING, PARTIALLY_COMPLETED) không tính; số Dashboard khớp console.
- ONLINE end-to-end với worker **bật chủ động**: **93/93** (1 Order/1 Payment/N Fulfillment, FAILED cuối, UNKNOWN, hủy + hoàn tiền mô phỏng một lần, worker hủy đơn quá hạn).
- Hồi quy 4 role: khách vãng lai/xác thực 69/70, khách hàng 93/94, Staff+Admin 216/218 (gồm giá 0 bị từ chối), tồn kho/đua/pickup 33/33. Các mục chưa đạt đều là sai sót đã biết của script (`/health` cần `Accept` JSON; regex số lượng giỏ; kiểm tra sản phẩm ngừng bán dùng dữ liệu sai), đã xác minh riêng ở báo cáo trước.

## 5. Chưa xử lý / cần chủ dự án quyết định
1. Định nghĩa "Cần Admin xử lý" ở mục 1 (đặc biệt việc tính đơn ONLINE quá hạn nhưng còn trong cửa sổ đối soát 15 phút).
2. Worker tắt mặc định + ONLINE mở trong dev: đơn ONLINE chưa trả giữ tồn đến khi Admin đối soát hoặc worker được bật. Production đã đóng ONLINE; nếu muốn mở ONLINE trong demo, nên bật worker chủ động.
3. Đơn `NEEDS_ATTENTION` do Staff báo giao thất bại (COD/pickup) được tính vào "Cần Admin xử lý" nhưng hệ thống chưa có thao tác Admin xử lý loại ngoại lệ này (OPEN-10 của đặc tả vẫn mở).
