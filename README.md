# OneShop V3

Spring Boot monolith bán mỹ phẩm theo mô hình chuỗi cửa hàng: Java 21, Maven, Thymeleaf, Bootstrap, SiteMesh, Spring Security/JWT, JPA, SQL Server và Cloudinary.

V3 dùng một giá online trên `Product`, giỏ hàng theo Product và tồn kho theo `StoreProduct`. ONLINE tạo một Order với nhiều Fulfillment; COD nhiều Store cần consent để tạo các Order độc lập. DELIVERY tính 30.000 VNĐ/Order; STORE_PICKUP miễn phí và cần một Store đủ hàng. Trừ tồn khi đặt hàng, hoàn tồn một lần khi hủy hợp lệ. Review gắn với OrderItem đã nhận; Staff đúng Store nguồn mới được trả lời; Admin chỉ kiểm duyệt. Mỗi nhân viên chỉ làm việc tại **một** chi nhánh (đổi chỗ bằng "Chuyển chi nhánh").

Dữ liệu demo (`database/seed/20_demo_data.sql`): 40 SKU thật, 12 thương hiệu, 10 danh mục, 40 ảnh Cloudinary có ghi công CC BY-SA 3.0 (xem `/image-credits`, `src/main/resources/data/image-credits.json`), 5 chi nhánh, 151 StoreProduct và 12 tài khoản demo. Chi nhánh, tài khoản và tồn kho là dữ liệu mô phỏng và được gắn nhãn như vậy. **Chỉ dùng cho demo/dev, không bao giờ nạp vào production.**

## Cài đặt và chạy local

Cần JDK **21**, SQL Server, `sqlcmd`, và wrapper `mvnw.cmd` (Windows) / `./mvnw` (Linux).

1. Copy `.env.example` thành `.env` (bị Git ignore), điền `DB_*`, `JWT_SECRET` (≥ 32 byte; `openssl rand -base64 48`), `CLOUDINARY_*`. Không commit credentials.
2. Tạo database **mới** bằng script (xem [database/README.md](database/README.md)):
   ```powershell
   $env:SQLCMDPASSWORD = '<mật khẩu SQL>'
   .\scripts\db\New-PasswordHash.ps1            # in BCrypt hash của mật khẩu demo bạn chọn (cần .\mvnw.cmd -DskipTests package)
   .\scripts\db\Initialize-OneShopDatabase.ps1 -Server localhost -User sa -Database oneshop -Mode Demo -SeedPasswordHash '<hash>' -TrustServerCertificate
   ```
   Database đã tồn tại thì **không** chạy lại schema/seed; nâng cấp bằng `database/migrations/V00x` sau khi backup.
3. `./mvnw spring-boot:run` (profile mặc định `dev`). `dev` và `prod` đều `ddl-auto=validate`; Hibernate không quản lý schema và ứng dụng từ chối khởi động nếu schema quá cũ.

Chỉ đặt `DB_TRUST_SERVER_CERTIFICATE=true` cho SQL Server local dùng chứng chỉ tự ký. JWT cookie là HttpOnly, SameSite=Lax (`Secure` ở `prod`); role/status được đọc lại từ database theo user ID trong token. CSRF bảo vệ form; Bearer API dùng token riêng.

## Kiểm thử

Full suite cần SQL Server, Node.js và Chrome (thiếu browser làm test báo lỗi, không skip). Dùng database dùng-một-lần tên `oneshop*_test` với fixture kịch bản:

```powershell
.\scripts\db\Initialize-OneShopDatabase.ps1 -Server localhost -User sa -Database oneshop_local_test -Mode Test -TrustServerCertificate
.\mvnw.cmd -B "-Doneshop.test.db=oneshop_local_test" test
```

`TestDatabaseSafetyInitializer` từ chối mọi datasource không tên `oneshop*_test` trước khi tạo bean. Không chạy hai suite song song trên cùng database; fixture tự khôi phục nội dung sau mỗi test. Evidence sinh ra nằm trong `target/` hoặc `test-results/` (đều bị ignore). Riêng `RealProductSeedIntegrityTest` viết cho dữ liệu demo và cần `database/seed-data/products.json` (chưa có trong repo — xem `docs/audit/PRE_AWS_DEPLOY_AUDIT.md`).

## Cấu hình vận hành

| Biến | Mặc định / mục đích |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev`. **Production phải đặt `prod`** (Dockerfile và `deploy/` đã đặt) |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | localhost, 1433, oneshop |
| `DB_USERNAME`, `DB_PASSWORD` | Credentials ngoài repository |
| `DB_TRUST_SERVER_CERTIFICATE` | false |
| `DB_JDBC_OPTIONS` | Nối vào JDBC URL, ví dụ `;trustStore=...;trustStorePassword=...` cho CA của AWS RDS |
| `DB_POOL_SIZE`, `DB_MAX_LIFETIME_MS`, `DB_KEEPALIVE_MS` | 5, 1500000, 300000 |
| `JWT_SECRET` | Bắt buộc, ≥ 32 byte UTF-8 |
| `JWT_COOKIE_SECURE` | false ở dev, true ở prod |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Cần khi upload ảnh |
| `MAIL_*` | SMTP gửi mã quên mật khẩu; để trống `MAIL_HOST` thì tắt |
| `PORT` | 8080 |
| `ONESHOP_CHECKOUT_ONLINE_ENABLED` | true ở dev; profile `prod` đặt **false** vì không có cổng thanh toán thật |
| `ONESHOP_PAYMENT_MOCK_GATEWAY_ENABLED` | true ở dev, false ở prod |
| `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED` | **false** (chỉ bật chủ động): worker ghi dữ liệu (hủy đơn ONLINE quá 15 phút, hoàn tồn). Khi tắt, đơn quá hạn hiện ở Admin → Thanh toán → "Cần xử lý" |

Không có biến để Hibernate tạo/sửa schema. ONLINE dùng cổng thanh toán MÔ PHỎNG phía server; production giữ ONLINE đóng. Triển khai AWS: [docs/AWS_DEPLOYMENT.md](docs/AWS_DEPLOYMENT.md).

## Đường dẫn và quyền

| Khu vực | Quyền |
| --- | --- |
| `/`, `/products/**`, `/stores/**`, `/login`, `/register`, `/forgot-password`, `/health` | Public |
| `/cart/**`, `/checkout/**`, `/orders/**`, `/reviews/**` | CUSTOMER, dữ liệu của chính tài khoản |
| `/staff/**` | STAFF + assignment ACTIVE; mọi dữ liệu theo Store được phân công |
| `/admin/**` | ADMIN |
| `/api/auth/login`, `/api/auth/register` | JSON auth; registration luôn CUSTOMER |

SiteMesh dùng ba decorator Client/Staff/Admin; `/decorators/**` không gọi trực tiếp. Staff thao tác Fulfillment, không sửa trạng thái tổng Order. Payment/refund là mô phỏng, không tích hợp ngân hàng thật.

## Tài liệu

- [database/README.md](database/README.md) — schema, seed, migration
- [docs/AWS_DEPLOYMENT.md](docs/AWS_DEPLOYMENT.md) — triển khai AWS
- [docs/audit/PRE_AWS_DEPLOY_AUDIT.md](docs/audit/PRE_AWS_DEPLOY_AUDIT.md) — báo cáo kiểm toán trước khi deploy; `docs/audit/` còn có báo cáo kiểm thử đợt trước
- [docs/report/](docs/report/) — báo cáo kiến trúc/chức năng/giao diện (lưu trữ, do công cụ trong `docs/report/tools` sinh ra)
