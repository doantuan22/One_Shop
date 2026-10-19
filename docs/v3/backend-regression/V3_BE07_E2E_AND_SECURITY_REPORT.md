> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../../database/README.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-07 — E2E browser và security

Bảy flow chạy trên Google Chrome headless thật, với ba browser context cô lập cho Customer, Staff và Admin. Automation thao tác DOM/form/CSRF thật; không gọi API mutation thay cho UI.

| Flow | Kiểm chứng chính |
| --- | --- |
| COD | Customer đặt COD, trạng thái/fulfillment hiển thị đúng |
| COD_SPLIT | Consent tạo các Order độc lập theo Store |
| PICKUP | Pickup code và hoàn tất bởi Staff đúng nguồn |
| ONLINE | Refresh địa chỉ giữ dữ liệu người nhận; mock payment xác nhận server-side rồi Staff hoàn tất |
| FAILED | Payment failed hủy/hoàn tồn, không hồi sinh Order |
| REFUND | Paid ONLINE cancel tạo simulated refund một lần |
| REVIEW | Customer review item đã hoàn tất; Staff nguồn reply; Admin ẩn review |

Kết quả, metadata Chrome, order/fulfillment IDs và screenshot nằm tại [evidence/browser](evidence/browser). Password không xuất hiện trong screenshot; `00-login-filled.png` đã được mask bởi UI password input.

## Security/authorization đã kiểm chứng

- Browser không thể gửi `payment_status`, amount hoặc direct V2 success/failure để xác nhận thanh toán.
- Server đối chiếu Order id, Payment id, amount, phương thức và gateway ledger trước khi đổi trạng thái.
- CSRF, ownership Customer, scope Store Staff và role Admin được kiểm tra ở backend, kèm test HTTP/database.
- Staff khác Store bị 403/404 khi sửa fulfillment/reply review; Admin không tạo/sửa reply, chỉ moderation.
- Mock payment endpoint bị tắt trong cấu hình production; `ONLINE=true` chỉ ở test/demo context.

Đây là mock payment/refund, không phải tích hợp ngân hàng thật. Timeout expiry được kiểm thử xác định bằng thời gian server/SQL thay vì chờ 15 phút trên browser.
