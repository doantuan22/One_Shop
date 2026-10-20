# OPEN-10 — Admin xử lý giao hàng thất bại (20/10/2026)

Chỉ kiểm thử ghi dữ liệu trên DB test dựng mới (đã xóa). Không đụng `oneshop`, `oneshop_test`. Không đổi schema, không thêm enum/CHECK/bảng/cột.

## 1. Khoảng trống ban đầu
- Staff báo `FAIL_DELIVERY/FAIL_PICKUP` → Fulfillment `FAILED`, Order `NEEDS_ATTENTION`; aggregator "không bao giờ xóa NEEDS_ATTENTION" và Staff bị khóa cả đơn. **Không có thao tác nào để thoát**: đơn kẹt vĩnh viễn (spec §9.1, §10.2, OPEN-10 "cách khắc phục CẦN CHỐT").
- `OrderStatusWriter` chỉ cho `NEEDS_ATTENTION → CONFIRMED | CANCELLED` (dành cho thanh toán); `OrderCancellationService` từ chối hủy khi phần giao đã qua `CONFIRMED`; `RefundService` chỉ hoàn **toàn bộ** đơn.
- Dashboard "Cần Admin xử lý" đã đếm các đơn này nhưng Admin không có chỗ xử lý.

## 2. Khả năng tái sử dụng cấu trúc hiện có (kết luận: không cần schema mới)
| Nhu cầu | Tái sử dụng |
|---|---|
| Lý do thất bại / lý do xử lý | `order_fulfillments.note`, `cancel_reason`, `order_fulfillment_status_history.note`, `order_status_history.note` (≤500) + người thực hiện `changed_by_user_id` |
| Kết thúc phần giao không giao được | `order_fulfillments.status = CANCELLED` + `cancelled_at`, `cancel_reason` (CHECK đã có) |
| Hoàn tồn | `InventoryMovement` loại `CANCEL_ORDER` (không thêm loại) |
| Hoàn tiền mô phỏng | `RefundService` (toàn bộ, idempotent) |
| Idempotency | khóa Order → khóa Fulfillment; phần giao rời `FAILED` trong cùng transaction với mọi tác dụng phụ |

## 3. Quy tắc nghiệp vụ đã triển khai
Admin quyết định **trên từng Fulfillment `FAILED`**, bắt buộc có lý do (5–400 ký tự), ghi Admin + lý do vào lịch sử. Order tổng **không bị sửa tùy ý**: tính lại bằng đúng quy tắc aggregator.

**A — Giao lại** (`FAILED → PACKED` với DELIVERY; `FAILED → READY_FOR_PICKUP` với STORE_PICKUP, giữ nguyên mã nhận hàng)
- Admin phải tick xác nhận hàng đang ở chi nhánh, đủ điều kiện giao lại; Store phải ACTIVE và còn hỗ trợ giao hàng/nhận tại cửa hàng; thanh toán phải đúng (ONLINE `PAID`; COD/PAY_AT_STORE `UNPAID`); chưa có hoàn tồn nào của đơn.
- **Không** trừ/hoàn tồn, **không** tạo Order/Payment. Xóa mã vận đơn cũ của lần giao hỏng để Staff nhập lại. Order rời `NEEDS_ATTENTION` sang trạng thái suy ra (PROCESSING / PARTIALLY_COMPLETED…) khi **không còn** phần `FAILED`.

**B — Xác nhận không thể giao** (`FAILED → CANCELLED`) kèm tình trạng hàng:
- `RETURNED` (hàng đã về): hoàn đúng các dòng của **phần giao đó** về Store của nó (`CANCEL_ORDER`, một lần).
- `LOST` / `UNCONFIRMED`: **không đổi tồn** (không cộng chỉ vì giao thất bại).
- Chỉ mở khi **không còn phần nào đã giao hoặc sẽ giao** (mọi phần khác là `CANCELLED`/`FAILED`), xem mục 6. Khi mọi phần đều bị hủy: Order → `CANCELLED`; ONLINE `PAID` → hoàn tiền mô phỏng **toàn bộ, một lần** (`REFUNDED`); COD/PAY_AT_STORE chưa thu → không hoàn, `payment_status` giữ `UNPAID`. Còn phần FAILED khác → Order giữ `NEEDS_ATTENTION` (chỉ ghi audit) tới quyết định cuối.
- Phần `COMPLETED` không bao giờ bị đụng.

Trạng thái: `Fulfillment FAILED → PACKED | READY_FOR_PICKUP | CANCELLED`; `Order NEEDS_ATTENTION → PROCESSING | SHIPPING | PARTIALLY_COMPLETED | COMPLETED | CONFIRMED | CANCELLED` qua `OrderStatusWriter.writeResolved` (chỉ từ NEEDS_ATTENTION, chỉ các đích đó, chỉ service này gọi). Lặp lại hoặc đồng thời → `FAILURE_ALREADY_RESOLVED` (từ chối logic, không đổi gì).

## 4. File thay đổi
- Mới: `service/fulfillment/AdminFulfillmentExceptionService.java`, `controller/admin/AdminFulfillmentExceptionController.java`, `dto/response/FulfillmentExceptionResponse.java`.
- Sửa: `InventoryService` và `InventoryServiceImpl` (`restoreForUndeliverableFulfillment`: hoàn đúng dòng của một phần giao, dùng chung nền `restoreLines`), `OrderStatusWriter` (`writeResolved`, `abbreviate` public), `OrderRepository` (`searchAdminAttention`), `AdminOperationsService`, `AdminOrderController` (panel + lọc `?attention=true`), `SecurityConfig` (`/admin/orders/**` luôn cần CSRF, kể cả khi có Bearer), `admin/order-detail.html`, `admin/orders.html`, `admin/dashboard.html`, `admin/payments/detail.html` (gợi ý dẫn tới chi tiết đơn).
- Endpoint: `POST /admin/orders/{orderId}/fulfillments/{fulfillmentId}/redeliver` (`goodsAtStore`, `reason`) và `/undeliverable` (`goods=RETURNED|LOST|UNCONFIRMED`, `reason`). Không route nào nhận status, số lượng, số tiền hay thời gian; trường giả mạo bị bỏ qua.
- Đồng bộ: Dashboard "Cần Admin xử lý" và danh sách Payment dùng chung phân loại sẵn có; đơn rời `NEEDS_ATTENTION` thì tự rời đếm; đơn đã hoàn tiền mô phỏng hiện ở nhóm "Đã hoàn tiền (mô phỏng)".

## 5. Kiểm thử thực tế
- **Toàn bộ suite (DB test dựng mới): 1635 test, 1635 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED** (baseline 1610 + 25 mới; gồm E2E trình duyệt).
- Mới: `FulfillmentExceptionDatabaseIntegrationTest` (20: hiển thị cho Admin, giao lại DELIVERY/PICKUP, kiểm tra đầu vào/Store, không giao được với RETURNED/LOST/UNCONFIRMED, ONLINE đã thanh toán, PAY_AT_STORE, nhiều Fulfillment — một phần đã COMPLETED/đang chạy, hai phần FAILED, idempotency, đơn `NEEDS_ATTENTION` do thanh toán không bị xử lý ở đây, phân quyền, Dashboard/Payment, 3 test đua đồng thời); `FulfillmentExceptionHttpDatabaseIntegrationTest` (5: trang Admin, redirect, đầu vào sai, Staff/Customer/guest/không CSRF/Bearer không CSRF → 403, field giả mạo, 404).
- **HTTP E2E trên app chạy thật (DB riêng): 70/70** — COD giao lại rồi hoàn tất; COD không giao được (hai request đồng thời: đúng một request thành công, hoàn tồn +1 một lần); LOST/UNCONFIRMED không đổi tồn; ONLINE nhận tại cửa hàng không nhận → hoàn tiền mô phỏng một lần, replay/retry của Admin không hoàn lần hai; ONLINE hai Store (một phần đã giao): panel cảnh báo, ép POST "không thể giao" bị từ chối, giao lại → COMPLETED với một khoản thanh toán; Staff/Customer/khách khác/guest bị chặn; field giả mạo bị bỏ qua; không còn đơn kẹt; Dashboard về mốc ban đầu.

## 6. Quyết định cần chủ dự án xác nhận (không tự áp đặt quy tắc tài chính)
1. **Hoàn tiền một phần / điều chỉnh COD khi chỉ một phần giao thất bại** (còn phần khác đã giao hoặc đang giao): đặc tả §10.2 yêu cầu thiết kế riêng (`payment_refunds`, theo OrderItem). Chưa triển khai: phương án B bị **từ chối** trong trường hợp này (hiển thị lý do và số liệu đã giao / chưa giao / đã thu); chỉ có thể **giao lại**. Đơn ONLINE nhiều Store mà một Store giao thất bại vĩnh viễn hiện chưa có đường đóng riêng phần đó.
2. **Dùng `CANCELLED` của Fulfillment cho "không thể giao"** (spec §9.2: Order `CANCELLED` không dành cho thất bại riêng của một phần): chỉ dùng ở mức phần giao, Order chỉ thành `CANCELLED` khi **toàn bộ** phần đều không giao được. Xin xác nhận chấp nhận cách tái dụng này thay vì thêm trạng thái mới.
3. **Hoàn tiền toàn đơn gồm cả phí giao 30.000đ** khi không giao được gì.
4. **`UNCONFIRMED` vẫn cho đóng phần giao** (không đổi tồn); nếu hàng về sau, kiểm đếm bù bằng điều chỉnh tồn của Staff. Có thể siết thành "chờ xác nhận" nếu muốn.
5. **Khóa cả đơn khi một phần FAILED** (BE04-19) giữ nguyên: Store khác chưa thao tác tiếp được cho tới khi Admin quyết định.
6. Nhiều phần cùng `FAILED` không đạt được qua đường Staff (phần đầu đã khóa đơn) nên được **dựng bằng test**; service vẫn xử lý đúng (còn phần FAILED thì giữ `NEEDS_ATTENTION`; hoàn tiền/tồn đúng một lần).


> **Cập nhật OPEN-10.1 (20/10/2026):** quy tắc "UNCONFIRMED đóng ngoại lệ" và "không hoàn tiền khi đã có phần giao thành công" trong báo cáo này đã được thay thế; xem [OPEN10_1_PARTIAL_REFUND_REPORT.md](OPEN10_1_PARTIAL_REFUND_REPORT.md).
