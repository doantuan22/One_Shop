# OPEN-10.1 — Hoàn tiền một phần mô phỏng & kết thúc Fulfillment

Ngày: 20/10/2026 · Phạm vi: đơn ONLINE nhiều Fulfillment, một số phần đã giao, một số phần không giao được.
Mọi khoản hoàn là **mô phỏng** (không có cổng thanh toán thật, không có tiền thật chuyển đi).

## 1. Kết luận

| Hạng mục | Kết quả |
| --- | --- |
| Toàn bộ test (`./mvnw test`, DB test độc lập) | **1.653 test, 0 failure, 0 error, 0 skipped** (baseline 1.635 + 18 mới) |
| Test mới | `PartialRefundDatabaseIntegrationTest` (14: số tiền, ràng buộc DB, UNCONFIRMED/RETURNED/LOST, mô phỏng lỗi + thử lại, view, 2 test đồng thời) · `PartialRefundHttpDatabaseIntegrationTest` (4: trang Admin, UNCONFIRMED→LOST qua HTTP, trang khách, phân quyền + trường giả mạo) |
| Test cũ đã cập nhật (không xóa, không bỏ qua, không nới lỏng) | `FulfillmentExceptionDatabaseIntegrationTest` (các ca OPEN10-01/05/06/07/10/11/12 phản ánh quy tắc mới), 3 test đếm bảng 22→23, `SeedGuardV3`, `AbstractIntegrationTest` |
| E2E HTTP trên app chạy thật + DB cô lập | **50/50 PASS** (`qa8.mjs`: 4 vai trò, A–E) |
| Migration trên DB dựng từ schema cũ (22 bảng) | Chạy lần 1 tạo bảng + backfill 1 dòng; chạy lại không thêm; rollback an toàn; bị chặn khi đã có hoàn một phần; chặn sai tên DB |

Không đụng `oneshop` và `oneshop_test`. Mọi test ghi dữ liệu chạy trên DB tạm `oneshop_o101_*_test`, đã xóa sau khi xong.

## 2. Quy tắc nghiệp vụ đã triển khai

1. **Không có phần nào được giao** (mọi phần còn lại đã `CANCELLED`): hoàn **toàn bộ tiền hàng** của các phần + **phí giao 30.000đ** (một lần, đi kèm phần quyết định *sau cùng*). Tổng hoàn = số đã thu → Payment `REFUNDED`, Order `CANCELLED`/`REFUNDED`.
2. **Đã có phần giao thành công / còn phần đang giao**: chỉ hoàn **giá trị hàng của phần không giao được**; **giữ lại một phí giao**. Payment vẫn `PAID`.
3. **Không hoàn quá số đã thu**: `goods + shipping ≤ amount − Σ(sổ cái, trừ dòng SIMULATION_FAILED)`; vi phạm → từ chối, rollback toàn bộ quyết định.
4. **Không tạo Payment/Order mới.** Mọi dòng hoàn gắn vào Payment `SUCCESS` sẵn có.
5. **Phần không giao được** → Fulfillment `CANCELLED`, `cancel_reason` = `Không giao được: <lý do cụ thể>`, ghi chú lịch sử `[ADMIN_UNDELIVERABLE][GOODS:<trạng thái>]`.
6. **Tình trạng hàng**: `RETURNED` → hoàn tồn đúng một lần; `LOST` → không hoàn tồn; `UNCONFIRMED` → **chỉ ghi nhận** (lịch sử + người xử lý), phần vẫn `FAILED`, đơn vẫn `NEEDS_ATTENTION`, **chưa hủy/hoàn tồn/hoàn tiền**; Admin có thể ghi lại nhiều lần rồi chốt `RETURNED`/`LOST` hoặc giao lại sau.
7. **Khóa đơn khi còn FAILED**: đơn giữ `NEEDS_ATTENTION` cho tới khi **mọi** phần FAILED có quyết định; nhiều phần FAILED được hỗ trợ (quyết định từng phần, tiền hàng hoàn theo từng quyết định, phí giao theo quyết định cuối). Khi hết FAILED, trạng thái đơn được tính lại từ các phần (`OrderStatusAggregator` + `OrderStatusWriter.writeResolved`) và Staff làm việc tiếp.
8. **COD / thanh toán tại quầy**: không có dòng sổ cái (chưa thu gì cho phần chưa giao).

### Biểu diễn "đơn đóng một phần" (chọn phương án ít tác động nhất)

Không thêm trạng thái Order mới. Khi mọi phần còn lại là `COMPLETED` và phần còn lại `CANCELLED` do không giao được, đơn là `COMPLETED` (giữ máy trạng thái, dashboard, báo cáo, Review hiện có). Phân biệt với đơn còn việc dở bằng:
- `PARTIALLY_COMPLETED` = **còn** phần chưa kết thúc (việc dở);
- `COMPLETED` + dấu hiệu dẫn xuất `closedWithUndelivered` (có Fulfillment `CANCELLED` mang tiền tố `Không giao được:` và đơn hoàn tất) → UI hiển thị **"Hoàn tất một phần: có phần không giao được"** (Admin + khách).

## 3. Thay đổi dữ liệu / migration

Bảng mới `dbo.payment_refunds` (sổ cái hoàn tiền mô phỏng): `refund_id, payment_id, order_id, fulfillment_id (NULL), component (GOODS|SHIPPING|WHOLE_ORDER), amount>0, status (PENDING_SIMULATION|SIMULATED_REFUNDED|SIMULATION_FAILED), reason, requested_by_user_id, requested_at, refunded_at`.
Ràng buộc: GOODS ⇔ có `fulfillment_id`; `SIMULATED_REFUNDED` ⇒ có `refunded_at`; chỉ mục unique có lọc chặn hoàn trùng: `UX_payment_refunds_goods(fulfillment_id)`, `UX_payment_refunds_shipping(order_id)`, `UX_payment_refunds_whole(payment_id)`.

- Cài mới: `01_schema.sql` (23 bảng), `02_seed.sql` (backfill dòng `WHOLE_ORDER` cho hoàn tiền seed của payment 7 = 458.000đ), `03_verify.sql` (23 bảng + 3 chỉ mục).
- Nâng cấp DB hiện hữu: `database/migrations/20261020_open101_payment_refunds.sql` (idempotent, không sửa/xóa dòng cũ, backfill mỗi `payments.refund_*` thành 1 dòng `WHOLE_ORDER`, kiểm số lượng payments/orders trước-sau) và `..._rollback.sql` (chỉ xóa bảng khi chỉ còn dòng backfill; có hoàn một phần → `THROW 50712`, không đổi gì).
- `V3SchemaCompatibilityVerifier` yêu cầu 3 chỉ mục sổ cái: **ứng dụng từ chối khởi động trên DB chưa áp migration**.
- **Chưa chạy migration vào `oneshop` / `oneshop_test`** (theo ràng buộc). Cần phê duyệt + backup riêng trước khi áp.

## 4. Thay đổi mã

- Mới: `PaymentRefund`, `PaymentRefundComponent`, `PaymentRefundRepository`, `PartialRefundService`, `OrderRefundViewService`, `OrderRefundSummaryResponse`.
- Viết lại: `AdminFulfillmentExceptionService` (UNCONFIRMED chỉ ghi nhận, hoàn một phần qua `PartialRefundService`, tính lại trạng thái đơn, hỗ trợ nhiều FAILED).
- Sửa: `RefundService` (ghi sổ cái cho hoàn cả đơn; từ chối hoàn cả đơn nếu đã có hoàn một phần), `AdminPaymentService` (phân loại/thử lại theo sổ cái), `OrderRepository` (điều kiện "hoàn tiền chưa xong" gồm sổ cái), `OrderStatusWriter`, controllers (`AdminFulfillmentExceptionController`, `AdminOrderController`, `AdminPaymentController`, `CustomerOrderController`), `V3SchemaCompatibilityVerifier`.
- Nhất quán Payment console / chi tiết / dashboard: một dòng hoàn `SIMULATION_FAILED` hoặc `PENDING_SIMULATION` đưa đơn vào "Cần Admin xử lý"; thử lại dùng đúng dòng đó (không tạo dòng thứ hai).

## 5. Giao diện

- **Admin** (`admin/order-detail.html`, `admin/payments/detail.html`): Đã thu / Đã hoàn (mô phỏng) / Còn giữ lại; tình trạng hàng; bản xem trước số tiền sẽ hoàn (hàng + phí giao hay giữ phí); cảnh báo "đang chờ xác minh"; bảng sổ cái (thời gian, nội dung, số tiền, trạng thái, lý do, **người xử lý**); lịch sử quyết định.
- **Khách** (`orders/detail.html` + fragment `refundSection`): mục "Hoàn tiền (mô phỏng)" kèm câu nói rõ *không có khoản tiền thật nào được chuyển*; đã thanh toán / đã hoàn / số tiền giữ lại; lý do từng phần không giao được; **không** hiện tên Admin/Staff; chỉ đơn của chính khách (đơn khác → 404). Lưu ý: mục "Hoàn tiền" không hiện tên người xử lý, nhưng danh sách "Lịch sử trạng thái đơn hàng" có sẵn từ trước vẫn hiển thị tên người thao tác và ghi chú kỹ thuật; xem FINAL_BUSINESS_ACCEPTANCE_REPORT.md mục 7.

## 6. Bảo đảm an toàn & đồng thời

- Thứ tự khóa: Order → Fulfillment (id tăng dần) → StoreProduct (id tăng dần). Idempotency bằng chuyển trạng thái dưới khóa + chỉ mục unique của sổ cái.
- Kiểm chứng: cùng quyết định gửi 2 lần đồng thời → đúng 1 dòng sổ cái, 1 lần hoàn tồn; 2 phần cuối quyết định đồng thời → tổng hoàn = đã thu, phí giao đúng 1 lần (cả test Java lẫn E2E HTTP).
- Ràng buộc DB tự chặn hoàn trùng GOODS/SHIPPING/WHOLE_ORDER và dòng sai dạng.
- Phân quyền: chỉ ADMIN quyết định; Staff/Customer/guest bị 403/redirect; thiếu CSRF → 403 (kể cả có Bearer); trường giả mạo (`amount`, `component`, `orderStatus`, `paymentStatus`, `fulfillmentId`…) bị bỏ qua — chỉ quy tắc cố định chạy.

## 7. Hạn chế và việc còn lại

1. **Chưa áp migration vào `oneshop`**: ứng dụng không khởi động trên `oneshop` cho tới khi áp (cần phê duyệt + backup).
2. **Khách hủy đơn sau khi đã hoàn tồn một phần** bị chặn bởi `hasRestorationForLockedOrder` (an toàn, nhưng chưa hỗ trợ). Chưa có yêu cầu nghiệp vụ.
3. Nhiều phần FAILED chỉ xảy ra qua luồng Staff khi báo cáo đồng thời; vì BE04-19 chặn Staff khi đơn `NEEDS_ATTENTION`, test dựng trạng thái này bằng SQL trên DB test.
4. "Hoàn tất một phần" là dấu hiệu dẫn xuất trên `COMPLETED`, không phải trạng thái lưu (quyết định để tránh đổi máy trạng thái). Nếu cần báo cáo/lọc riêng, có thể bổ sung cột sau.
5. Danh sách "Cần xử lý" chưa phân trang riêng; số lượng nhỏ trong dữ liệu hiện tại.
6. Tài liệu lịch sử cũ vẫn ghi "22 bảng" (chỉ phản ánh thời điểm viết); README gốc và `../seed/DATABASE_SETUP.md` đã cập nhật.

## 8. Ghi chú vận hành lúc kiểm thử

Khi dọn dẹp, lệnh xóa DB theo mẫu `oneshop%_test` (trừ `oneshop_test`) đã xóa luôn các DB tạm của các đợt audit trước còn sót lại (`oneshop_v3_audit_test`, `oneshop_v3_audit_cleanup_test`, `oneshop_v3_deep_audit_*`, `oneshop_v3_final_*`, `oneshop_v3_rollback_negative_*`, `oneshop_v3_uiux_screens_*`). Đây đều là DB kiểm thử độc lập theo yêu cầu "xong thì xóa", nhưng việc xóa không được hỏi lại riêng. `oneshop` và `oneshop_test` còn nguyên, không bị thay đổi.
