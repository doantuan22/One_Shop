# Tăng cường bảo mật: chống dò mật khẩu và security header

## 1. Giới hạn đăng nhập sai (brute-force)

Áp dụng cho `POST /login` và `POST /api/auth/login`, ở hai lớp.

**Trong ứng dụng** (`LoginAttemptLimiter`, bộ nhớ của JVM, không dùng database):

| Giới hạn | Mặc định | Cách đếm |
|---|---|---|
| Theo tài khoản (email/tên đăng nhập, không phân biệt hoa thường) | 5 lần / 15 phút | Đếm **trước** khi kiểm tra mật khẩu, nên nhiều request song song không vượt được giới hạn. Đăng nhập đúng thì xóa bộ đếm. |
| Theo IP | 30 lần **sai** / 15 phút | Chỉ đếm lần sai, để nhiều khách chung một IP (NAT) không tự khóa nhau. |

Khi vượt giới hạn, mọi lần đăng nhập (kể cả đúng mật khẩu) bị từ chối **15 phút**: trang web trả HTTP 429 kèm thông báo "Đăng nhập sai quá nhiều lần...", API trả 429 JSON kèm header `Retry-After`. Tài khoản không tồn tại bị đếm giống tài khoản có thật nên không lộ thông tin.

Cấu hình (biến môi trường, đều có mặc định): `ONESHOP_LOGIN_THROTTLE_ENABLED`, `ONESHOP_LOGIN_MAX_ATTEMPTS`, `ONESHOP_LOGIN_IP_MAX_FAILURES`, `ONESHOP_LOGIN_WINDOW_MINUTES`, `ONESHOP_LOGIN_LOCK_MINUTES`.

IP khách lấy từ `X-Forwarded-For` **chỉ khi** kết nối đến từ proxy của mình (loopback/mạng nội bộ) và dùng phần tử do proxy thêm vào cuối; client không giả mạo được.

Giới hạn của cách làm này:
- Trạng thái nằm trong bộ nhớ: restart app thì reset, chạy nhiều instance thì mỗi instance đếm riêng.
- Kẻ tấn công có thể cố ý đăng nhập sai vào một tài khoản để khóa chủ tài khoản 15 phút (đánh đổi quen thuộc của khóa theo tài khoản). Nếu cần chặn, chuyển bộ đếm sang database/Redis.

**Ở nginx**: `limit_req` 20 request/phút/IP (burst 10) cho các `POST` tới `/login`, `/register`, `/forgot-password`, `/api/auth/login`, `/api/auth/register`. Vượt thì trả 429.

> `deploy/aws/stack.yml` chỉ chạy khi **tạo** EC2. Với server đang chạy, cập nhật tay file `/etc/nginx/conf.d/oneshop.conf` theo `deploy/nginx-oneshop.conf` (khối `map` + `limit_req_zone` đặt ngoài `server {}`, hai dòng `limit_req` đặt trong `server` HTTPS), rồi `sudo nginx -t && sudo systemctl reload nginx`. Có thể sửa script `/usr/local/sbin/enable-oneshop-https` tương tự để lần chạy sau không mất cấu hình.

## 2. Security header

Gửi từ `SecurityConfig` cho mọi response:

| Header | Giá trị / tác dụng |
|---|---|
| Content-Security-Policy | `default-src 'self'`; script/style cho `'self'`, `'unsafe-inline'` (các trang còn script/style inline) và `https://cdn.jsdelivr.net`; ảnh `'self' data: https:` (ảnh Cloudinary); `object-src 'none'`; `base-uri 'self'`; `frame-ancestors 'none'`; `form-action 'self' https://pay.payos.vn` (thanh toán là form POST rồi redirect sang payOS, trình duyệt kiểm tra `form-action` cả khi redirect) |
| Referrer-Policy | `strict-origin-when-cross-origin` |
| Permissions-Policy | tắt camera, microphone, geolocation, payment, usb |
| X-Content-Type-Options, X-Frame-Options (DENY), HSTS | mặc định của Spring Security (HSTS chỉ khi request là HTTPS) |

CSP chưa chặn được inline script (vì còn `onclick`, `<script>` inline). Muốn siết hơn cần chuyển chúng sang file `.js` rồi bỏ `'unsafe-inline'`.

Nếu thêm nguồn ngoài mới (CDN, font, iframe, cổng thanh toán khác), phải thêm vào `CONTENT_SECURITY_POLICY` trong `SecurityConfig`, nếu không trình duyệt sẽ chặn. Lỗi CSP hiện ở Console của trình duyệt (`Refused to load...`).

## 3. Kiểm thử

`LoginAttemptLimiterTest`, `ClientIpTest`, `SecurityHardeningWebIntegrationTest` (429 + `Retry-After` qua HTTP thật, trang đăng nhập web, header). Chưa kiểm tra trên trình duyệt thật: sau khi deploy hãy mở các trang chính, trang thanh toán (chuyển sang payOS) và trang quản trị, xem Console có lỗi CSP không.
