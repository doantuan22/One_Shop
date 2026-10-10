# Security Audit

Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.

## Authentication

JwtService dùng signing key từ JWT_SECRET (tối thiểu32 UTF8 bytes), JJWT verifyWith + signed claims + expiration; JwtServiceTest 5 cases và AccessControl/Auth suites vừa chạy PASS. JwtAuthenticationFilter reload UserDetails từ DB mỗi request, role từ current UserDetails thay vì tin JWT role claim/Store client; invalid/expired/disabled token để anonymous. Không log token/secret trong filter, chỉ exception class.

Chrome cookie actual local dev: **ONESHOP_TOKEN HttpOnly=true, SameSite=Lax, Path=/, Secure=false**, phù hợp localhost HTTP/dev. XSRF-TOKEN không HttpOnly vì CSRF repository cần browser-readable token, không phải authentication cookie. Prod source default JWT_COOKIE_SECURE=true; actual HTTPS cookie/proxy behavior **NOT VERIFIED**, không báo Secure prod PASS từ dev.

Ordinary signup/login/logout qua UI PASS. User status INACTIVE làm JWT Staff đang dùng mất quyền; active lại cùng JWT có quyền theo DB. Phase11 current role/status tests kiểm role đổi, assignments bị vô hiệu khi rời STAFF và không tự bật lại. Logout xóa cookie browser; JWT stateless không có global token denylist, không khẳng định đã thu hồi mọi copy của token sau logout.

Unicode signup FSA-02 trả500, không tạo User; stack trace hiện trong local server log nhưng response web generic500 không có exception/DB secret. Đây là input-validation defect, chưa thấy bypass hoặc dữ liệu hỏng.

## Authorization, isolation, IDOR

| Request / actor | Current evidence | Kết quả |
| --- | --- | --- |
| Guest tới Admin/Staff/Orders | Browser fetch follow redirect cuối `/login`200; AccessControl protected route tests | Bị yêu cầu đăng nhập; **không** coi final200 là truy cập Admin |
| Customer tới Admin/Staff | Survey/probes403; current parameterized AccessControl tests | PASS |
| Staff tới Admin/Customer orders routes |403 ở protected role route | PASS |
| Admin tới Staff/Customer routes |403; Admin có /admin/orders chain-wide | PASS; không tự inherit STAFF role |
| Customer foreign Order | Peer customer tới fixture Order9809 trả404; owned order200 | PASS |
| Staff foreign Store/forged selector | Phase10 tc11 variants endpoint thật; forced query/header/cookie/body không mở scope | PASS |
| Revoke assignment với JWT cũ | Assignment397 ACTIVE→INACTIVE; same JWT Order9809 chuyển200→404; reactivate200 | PASS |
| User inactive với JWT cũ | Chrome additional user disable/reactivate; current Phase11/12 tests | PASS |
| Admin mutation bởi Customer | Actual POST /admin/users với validCSRF403, không tạo user | PASS |
| Direct Admin service invocation | Phase11.newAdminServicesEnforceRoleEvenWithoutHttp | PASS với Admin services mới; không gán kết quả này cho mọi legacy catalog helper |

Store scope lấy từ ACTIVE assignment/User STAFF/Store trong DB tại backend; scoped repositories có store IDs từ resolver. Admin filter Store không áp assignment nhưng vẫn ROLE_ADMIN. SelectedStore là browsing context, không là permission. INACTIVE Store không nhận checkout mới, Customer/Admin vẫn đọc history; Staff active-store scope không mở lại Store đã inactive.

## CSRF và request tampering

SecurityConfig cookie CSRF vẫn bật cho web mutations; stateless session strategy giữ token đúng cho nhiều tab. Bearer API exemptions chỉ request mang Authorization Bearer và không phải protected Order/Staff order/stock/fulfillment form paths. Payment/cancel/Staff forms yêu cầu CSRF cả Bearer và percent-encoded parsed path. Current Phase12/StaffInventory/Phase11 tests kiểm các đường này PASS.

Browser form survey/journey dùng hidden `_csrf` đúng rendered token. Missing token POST cancel403; valid token pass normal flow. Cookie CSRF raw token với mặc định XOR handler không được dùng thay hidden token trong negative-review recheck; audit driver đã phân biệt probe ban đầu403 và current valid CSRF → 404.

Checkout DTO không cho client xác định unitPrice/total/status/owner; production service reload locked StoreProduct price/quantity. Current TC08 test sửa payload vẫn dùng giá DB, TC14 snapshot không bị repricing. Admin StoreProduct identity Store/Product ghim theo existing row; forged identity không chuyển stock/Order sang Store khác. Cancel chỉ nhận OrderId/principal, body quantity/store/price/actor không là nguồn dữ liệu. Stock quantity negative/missing/note invalid reject backend, no partial mutation.

## XSS, files, exceptions và secret configuration

Stored XSS probe chỉ dùng fixture Category description `<script>window.__auditXss=1</script>`. Admin render giữ literal value, scriptExecuted=false; Admin review current `reviewManagementOnlyChangesVisibilityAndEscapesContent` cũng PASS. Thymeleaf text/field escaping và queries tham số được đọc; không báo chứng nhận chống mọi stored/reflected XSS/SQL injection. Không chạy destructive/security attack ngoài localhost.

CloudinaryService check empty/contentType `image/`, resource_type=image; multipart server giới hạn5MB file/6MB request; URL/publicId DTO persisted. Client-supplied MIME không là kiểm bytes độc lập; live spoofed-MIME/provider validation, SVG security, all file variants **NOT VERIFIED** trong audit vì không được phép provider mutations. Current mocked adapter/service validation tests PASS, không suy diễn thành exploit confirmed hoặc live secure PASS.

JWT/SQL/Cloudinary credentials lấy từ environment; `.env` ignored, `.env.example` không có giá trị thật. Secret-sensitive values exact scan trong audit logs không có match. Không in/log giá trị token, password, signing secret trong evidence JSON; browser metadata giữ cookie flags, không values. Một số Spring/Tomcat DEBUG logs trong runtime dev không được coi là production config; tracked prod root/oneshop INFO, SQL logging false, errors generic. Chưa kiểm Git history toàn bộ, entropy secrets, dependency CVEs, production logging/headers/CSP/rate limit và target Render config.

FSA-02 là finding security-adjacent validation MEDIUM. Không confirmed privilege escalation, IDOR cross-Store, JWT forge bypass, leak secret hoặc CSRF bypass trong các probes/tests. [Issue register](06_Issue_Register.md) chứa finding đã chứng minh, không nâng giới hạn kiểm chứng thành finding.
