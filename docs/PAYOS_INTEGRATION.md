# Tích hợp thanh toán payOS (Hosted Checkout)

Tài liệu vận hành cho `ONESHOP_PAYMENT_PROVIDER=PAYOS`. Mặc định hệ thống vẫn dùng `MOCK` (mô phỏng, không có tiền thật).
Nguồn đã đối chiếu: payos.vn/docs (API, webhook, kiểm tra chữ ký, môi trường test) và SDK chính thức `vn.payos:payos-java:2.0.1`
(bản mới nhất trên Maven Central lúc triển khai, bytecode Java 8, chạy được trên Java 21 / Spring Boot 3.5).

## 1. Luồng thanh toán

```
Khách bấm "Thanh toán qua payOS"            POST /orders/{id}/payments/attempts
  └─ giao dịch ngắn (khóa Order): kiểm tra đơn còn hạn, lấy số tiền từ DB, tạo/giữ dòng payments (orderCode duy nhất)
  └─ gọi payOS tạo link (KHÔNG giữ khóa DB)  →  lưu paymentLinkId + checkoutUrl (giao dịch ngắn, kiểm tra lại trạng thái)
  └─ 302 → checkoutUrl (trang payOS)
Khách chuyển khoản / quét QR trên trang payOS
payOS ── POST /api/payments/payos/webhook ──►  xác minh chữ ký → ghi bằng chứng → engine đối soát quyết định
payOS ── 302 ──► /orders/{id}/payments/payos/return | /cancel   (CHỈ hiển thị + kích hoạt truy vấn payOS)
```

* **Chỉ backend quyết định.** `returnUrl`/`cancelUrl` bỏ qua mọi query (`status=PAID`, `cancel=false`, `code=00`...): chúng chỉ làm
  backend hỏi lại payOS (`GET /v2/payment-requests/{orderCode}`) rồi cho engine đối soát quyết định.
* **Đơn chỉ CONFIRMED khi** payOS báo `PAID` với `amountPaid` **đúng bằng** tổng đơn và có thời điểm ngân hàng ghi nhận **trước hạn**
  (15 phút kể từ lúc đặt, không gia hạn). Thiếu/thừa tiền (`UNDERPAID`, `amountPaid` ≠ amount), `PROCESSING` → kết quả
  *chưa rõ*: chờ đối soát, hết cửa sổ đối soát thì `NEEDS_ATTENTION`; không bao giờ tự coi là thành công/thất bại.
* **Thời điểm tiền** là `transactionDateTime` của ngân hàng (giờ VN → đổi sang múi giờ máy chủ), không phải lúc webhook đến.
  Webhook đến muộn nhưng ngân hàng ghi nhận trước hạn ⇒ vẫn xác nhận; ngân hàng ghi nhận sau hạn ⇒ hủy đơn + hoàn tồn 1 lần + yêu cầu hoàn tiền.
* **Hủy link ≠ hoàn tiền ≠ bằng chứng chưa thu tiền.** Khi đơn bị hủy, link payOS được hủy *sau khi commit* (best effort). Tiền vẫn
  về sau đó sẽ được phát hiện (webhook/truy vấn), ghi nhận 1 lần và tạo yêu cầu hoàn tiền; đơn đã hủy **không hồi sinh**.
* **Tạo link idempotent.** Bấm nhiều lần / retry ⇒ dùng lại cùng link, không gọi payOS. `orderCode` được lưu vào DB **trước** khi
  gọi payOS; nếu payOS đã tạo link nhưng app lỗi trước khi lưu, lần sau gửi lại đúng `orderCode` đó → payOS từ chối trùng → backend
  tra `GET` theo `orderCode` và nhận lại link cũ (không tạo yêu cầu thứ hai). Link hết hạn đúng hạn thanh toán của đơn (`expiredAt`).
* **payOS không trả lời** (timeout/5xx) ⇒ kết quả *chưa rõ*: không hủy đơn dựa trên bằng chứng cũ. Timeout scan, trang đơn hàng,
  nút hủy của khách và "Kiểm tra lại" của admin đều hỏi payOS trước, không giữ khóa DB trong lúc gọi HTTP.
* `orderCode` = (giây từ 2023-11-14) × 100000 + 5 chữ số ngẫu nhiên, duy nhất theo chỉ mục DB, < 2.5×10¹⁴ (giới hạn payOS 2⁵³−1).
* Số tiền là số nguyên đồng, đổi từ `BigDecimal` bằng `longValueExact` (số lẻ bị từ chối, không làm tròn). Cấu hình
  `PAYOS_MIN_AMOUNT`/`PAYOS_MAX_AMOUNT` (mặc định 2.000 / 500.000.000). **Tài liệu payOS không công bố hạn mức** — hai số này là
  giả định thận trọng của OneShop; payOS/ngân hàng vẫn áp hạn mức riêng.

## 2. Cấu hình (biến môi trường / `.env`, không commit)

| Biến | Ý nghĩa |
|---|---|
| `ONESHOP_PAYMENT_PROVIDER` | `MOCK` (mặc định) hoặc `PAYOS`. `PAYOS` chặn toàn bộ mô phỏng, không fallback sang "thành công giả". |
| `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | Khóa kênh thanh toán trên dashboard payOS. |
| `ONESHOP_PUBLIC_BASE_URL` | Origin https công khai, không có đường dẫn (vd `https://shop.example.com`). Dùng cho returnUrl/cancelUrl. |
| `ONESHOP_CHECKOUT_ONLINE_ENABLED` | Công tắc mở ONLINE (prod mặc định `false`). Phải `true` **và** payOS cấu hình hợp lệ thì khách mới chọn được ONLINE. |
| `PAYOS_MIN_AMOUNT`, `PAYOS_MAX_AMOUNT`, `PAYOS_TIMEOUT_MS`, `PAYOS_MAX_RETRIES` | Tùy chọn (xem `.env.example`). |
| `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED` | Giữ `false` cho tới khi đã kiểm chứng thanh toán thật đầu tiên. Worker khi bật sẽ hỏi payOS trước khi hủy. |

Thiếu khóa/URL ⇒ ONLINE đóng ở checkout, tạo link bị từ chối (`GATEWAY_DISABLED`), log ERROR lúc khởi động (không in khóa).
Khóa không xuất hiện trong frontend, log hay Git; body gửi payOS chỉ có chữ ký HMAC (SDK ký bằng checksum key).

## 3. Địa chỉ

| | URL |
|---|---|
| Webhook | `POST {ONESHOP_PUBLIC_BASE_URL}/api/payments/payos/webhook` — công khai, không đăng nhập, miễn CSRF (chỉ endpoint này) |
| returnUrl | `{ONESHOP_PUBLIC_BASE_URL}/orders/{orderId}/payments/payos/return` (cần đăng nhập đúng chủ đơn) |
| cancelUrl | `{ONESHOP_PUBLIC_BASE_URL}/orders/{orderId}/payments/payos/cancel` |

Webhook: SDK xác minh chữ ký HMAC-SHA256 trước khi đọc dữ liệu; sai chữ ký → 400. Bản tin mẫu của payOS (orderCode 123...) có chữ ký
hợp lệ nhưng không khớp đơn nào → trả 2xx, **không** làm thanh toán đơn nào. Lỗi xử lý nội bộ → 500 để payOS gửi lại. Chống trùng bền
vững bằng `payment_provider_events` (unique `provider + event_key`); lệch thứ tự/lặp/đến sau khi hủy/sau khi restart đều an toàn vì
trạng thái nằm trong DB và mọi quyết định chạy dưới khóa Order.

### Đăng ký / xác nhận webhook sau khi endpoint chạy
Cách 1 — dashboard payOS → Kênh thanh toán → *Webhook URL* → dán URL ở trên → Lưu (payOS gửi bản tin mẫu, endpoint phải trả 2xx).
Cách 2 — gọi API (khóa lấy từ biến môi trường, không dán vào lịch sử lệnh/chat):
```powershell
$h = @{ 'x-client-id' = $env:PAYOS_CLIENT_ID; 'x-api-key' = $env:PAYOS_API_KEY; 'Content-Type' = 'application/json' }
Invoke-RestMethod -Method Post -Uri 'https://api-merchant.payos.vn/confirm-webhook' -Headers $h `
  -Body (@{ webhookUrl = "$env:ONESHOP_PUBLIC_BASE_URL/api/payments/payos/webhook" } | ConvertTo-Json)
```
Ứng dụng phải đang chạy với `ONESHOP_PAYMENT_PROVIDER=PAYOS` và có thể truy cập công khai (HTTPS hợp lệ) khi gọi.

## 4. Migration

Script: `database/migrations/V009_payos_payments.sql` (rollback: `rollback/R009_payos_payments.sql`). **Không** được ứng dụng tự chạy;
`V3SchemaCompatibilityVerifier` từ chối khởi động nếu thiếu (thông báo nêu rõ V009). Cài mới dùng `database/schema/01_schema.sql`
(đã gồm V009, 31 bảng).

1. Dừng ứng dụng, sao lưu database (`BACKUP DATABASE ...`).
2. `sqlcmd -S <server> -d <database> -b -f 65001 -i database/migrations/V009_payos_payments.sql` (chạy lại nhiều lần vẫn an toàn).
3. Kiểm tra: `SELECT provider, method, COUNT(*) FROM dbo.payments GROUP BY provider, method;` — các dòng ONLINE cũ có `provider='MOCK'`,
   COD/PAY_AT_STORE có `NULL`; số dòng `payments`/`payment_refunds` không đổi.
4. Khởi động ứng dụng.

V009 chỉ thêm cột/bảng/ràng buộc: không xóa hay đổi trạng thái/số tiền/thời gian của dòng cũ; hoàn tiền mô phỏng cũ giữ nguyên
(`SIMULATED_REFUNDED`...) và tách biệt khỏi hoàn tiền thật (`MANUAL_REQUESTED`, `REFUNDED`). Đã kiểm chứng trên SQL Server 2025: schema
cài mới ≡ schema sau migration (so sánh cột/ràng buộc/chỉ mục), chạy lặp lại, rollback.

## 5. Hoàn tiền (thủ công, có bằng chứng)

* OneShop **không** gọi payout/chi tiền. Không dùng `DefaultRefundSimulator` cho tiền thật (nó ném lỗi nếu gặp payment payOS).
* Khi cần hoàn (khách hủy đơn đã trả, tiền về sau hạn / sau khi hủy, nhận thừa/thiếu/trùng, hàng không giao được, trả hàng) hệ
  thống tạo yêu cầu `MANUAL_REQUESTED` trong sổ `payment_refunds` (đúng số tiền, lý do, người yêu cầu theo nghiệp vụ hiện có). Đơn vẫn
  `payment_status=PAID` cho đến khi hoàn xong.
* Quản trị viên chuyển khoản hoàn tiền từ ngân hàng, rồi vào **Admin → Thanh toán → chi tiết đơn → "Hoàn tiền thật cần xử lý"** nhập
  *mã giao dịch ngân hàng thực tế* (+ ghi chú). Chỉ lúc đó dòng thành `REFUNDED` (lưu mã, người xử lý, thời điểm; mã giao dịch là duy nhất
  nên không dùng cho hai khoản hoàn). Khi toàn bộ số tiền đã hoàn, payment và đơn thành `REFUNDED`.
* Khoản nhận **không khớp** (thiếu/thừa, link thứ hai) trên đơn đã hủy/đã thanh toán được ghi nhận thành yêu cầu hoàn đúng số tiền payOS đã nhận.

## 6. Kiểm thử thủ công với số tiền nhỏ (payOS không có sandbox)

Đã kiểm tra lại: payOS ghi rõ *"không cung cấp môi trường test (sandbox/staging) riêng biệt"*; thử nghiệm chạy trên production với số tiền nhỏ.
Tôi **không** thực hiện giao dịch thật. Bạn thực hiện, trên một sản phẩm giá nhỏ (≥ `PAYOS_MIN_AMOUNT`, ví dụ 2.000–5.000 ₫; nếu không có
sản phẩm rẻ, tạm đặt giá một sản phẩm thử trong DB test/staging):

1. Chạy ứng dụng (https công khai/ngrok-like) với các biến ở mục 2, đăng ký webhook (mục 3), đảm bảo `ONESHOP_CHECKOUT_ONLINE_ENABLED=true`.
2. **Thanh toán đủ**: đặt hàng ONLINE → "Thanh toán qua payOS" → chuyển khoản đúng số tiền. Mong đợi: đơn `CONFIRMED/PAID`, bảng
   `payments` có `status=SUCCESS`, `provider_status=PAID`, `paid_at` = giờ ngân hàng, 1 dòng `payment_provider_events`.
3. **Bấm nhiều lần**: bấm lại nút thanh toán trước khi trả → cùng một link, `payments` chỉ 1 dòng PENDING.
4. **Hủy giữa chừng**: mở link rồi bấm Hủy trên trang payOS → quay về trang thanh toán, đơn vẫn chờ thanh toán; thanh toán lại được.
5. **Webhook bị bỏ lỡ**: tạm đổi webhook URL sai, thanh toán, rồi mở lại trang thanh toán đơn → được xác nhận nhờ truy vấn payOS.
6. **Trả sau hạn**: tạo đơn, chờ quá 15 phút, trả → đơn hủy, tồn kho hoàn 1 lần, xuất hiện "Hoàn tiền thật cần xử lý"; chuyển khoản hoàn,
   nhập mã giao dịch → `REFUNDED`.
7. **Hủy đơn đã trả** (khi còn `CONFIRMED`): khách hủy → yêu cầu hoàn tiền thật `MANUAL_REQUESTED`.
8. **Thiếu tiền** (nếu payOS cho phép chuyển thiếu): đơn không xác nhận, hiện "đang đối soát".
9. Kiểm tra log không có khóa; thử gửi `POST` giả vào webhook (không chữ ký) → 400, không đổi đơn nào.

## 7. Giới hạn / giả định cần biết

* Khi nhận lại link đã tồn tại (khôi phục sau sự cố) payOS không trả `checkoutUrl` ở API tra cứu; OneShop dựng
  `https://pay.payos.vn/web/{paymentLinkId}` (`oneshop.payos.checkout-base-url`) — **chưa kiểm chứng với tài khoản thật**; đường dẫn chính
  (tạo mới) dùng đúng `checkoutUrl` payOS trả về.
* Hạn mức số tiền như mục 1; mã trạng thái chi tiết của payOS (`PROCESSING`, `UNDERPAID`, `EXPIRED`, `FAILED`) lấy từ SDK 2.0.1.
* Hoàn tiền một phần theo từng phần giao dùng lại logic OPEN-10.1 (hàng/phí giao/trả hàng) nhưng ở dạng yêu cầu thủ công.
* Chưa có payout tự động; nếu làm sau cần cấu hình và phạm vi riêng (khóa payout riêng của payOS).
