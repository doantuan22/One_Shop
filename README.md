# OneShop V3

Spring Boot monolith bán mỹ phẩm theo mô hình chuỗi cửa hàng: Java 21, Maven, Thymeleaf, Bootstrap, SiteMesh, Spring Security/JWT, JPA, SQL Server và Cloudinary.

V3 dùng một giá online trên `Product`, giỏ hàng theo Product và tồn kho theo `StoreProduct`. ONLINE tạo một Order với nhiều Fulfillment; COD nhiều Store cần consent để tạo các Order độc lập. DELIVERY tính 30.000 VNĐ/Order; STORE_PICKUP miễn phí và cần một Store đủ hàng. Trừ tồn khi đặt hàng, hoàn tồn một lần khi hủy hợp lệ. Review gắn với OrderItem đã nhận; Staff đúng Store nguồn mới được trả lời; Admin chỉ kiểm duyệt.


Dữ liệu demo đã kiểm tra ngày 25/10/2026 gồm **40 SKU thật, 12 thương hiệu, 10 danh mục, 40 ảnh Cloudinary có ghi công CC BY-SA 3.0**, cùng **5 chi nhánh, 151 StoreProduct và 12 tài khoản mô phỏng**. Profile opt-in tạo 14 đơn qua Service thật; browser checkout tạo thêm một đơn. Xem [kết quả thực tế](database/SEED_RESULT_REPORT.md), [khảo sát schema](database/SEED_DATABASE_SURVEY.md), [runbook seed/reset](database/SEED_RUNBOOK.md), [manifest](database/seed-data/products.json) và `/image-credits`. Quy mô SKU/tồn kho còn dưới mục tiêu định hướng 100–150/250–450; không bổ sung sản phẩm thiếu chứng cứ chỉ để đủ số lượng. Database `oneshop` chỉ reset sau preflight, backup, kiểm tra khôi phục và xác minh dữ liệu demo.

## Cài đặt và chạy local

Cần JDK **21**, SQL Server và Maven hoặc wrapper `mvnw.cmd` trên Windows (`./mvnw` trên Linux).

1. Copy `.env.example` thành `.env`, rồi điền `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (ít nhất 32 byte UTF-8). `.env` bị Git ignore; không commit credentials.
2. Database phát triển chính là `oneshop`. Với database đã tồn tại, chỉ dùng upgrade có backup và phê duyệt riêng; không chạy schema/seed để nâng cấp database hiện hữu.
3. Database mới dùng `database/01_schema.sql` để tạo schema, sau đó chạy `database/06_password_reset_otps.sql` để tạo bảng OTP mà entity JPA yêu cầu.
4. Nạp fixture bằng `scripts/seed/Invoke-OneShopDemoSeed.ps1 -Action Seed` trên database trống; xem [runbook seed/reset](database/SEED_RUNBOOK.md).
5. Chạy `mvn spring-boot:run` sau khi database tương thích. `dev` và `prod` đều dùng `ddl-auto=validate`; Hibernate không quản lý schema.

Nếu chạy `com.oneshop.OneShopApplication` trực tiếp từ IDE mà log báo không có active profile và datasource `url` chưa được cấu hình, runtime classpath có thể đang thiếu resource cấu hình. Chạy `mvn spring-boot:run` từ thư mục gốc repo, hoặc cấu hình IDE để đưa `src/main/resources` vào classpath. Lưu credentials trong `.env` đã bị Git ignore; không chép giá trị bí mật trực tiếp vào lệnh PowerShell.

```text
jdbc:sqlserver://${DB_HOST:localhost}:${DB_PORT:1433};databaseName=${DB_NAME:oneshop};encrypt=true;trustServerCertificate=${DB_TRUST_SERVER_CERTIFICATE:false}
```

Chỉ đặt `DB_TRUST_SERVER_CERTIFICATE=true` cho SQL Server local dùng chứng chỉ tự ký. `dev` cho phép JWT cookie trên HTTP local; `prod` dùng cookie Secure. JWT cookie là HttpOnly, SameSite=Lax; role/status được đọc lại từ database theo user ID trong token. CSRF bảo vệ form; Bearer API dùng token riêng. Hiện chưa có SMTP implementation.

## Kiểm thử tách biệt

Full suite cần SQL Server và fixture V3. Browser E2E cần Node.js có WebSocket và Chrome tại đường dẫn trong `BrowserV3DatabaseIntegrationTest`; thiếu browser làm suite báo lỗi, không skip để che môi trường.

```powershell
# Test DB (disposable, empty -> 01_schema.sql refuses an existing DB). Use -f 65001 so the UTF-8 scripts keep Vietnamese text:
#   sqlcmd -S <host>,<port> -U <user> -P <pw> -C -b -f 65001 -v DatabaseName=oneshop_v3_local_test -i database/01_schema.sql
#   sqlcmd ... -d oneshop_v3_local_test -i database/06_password_reset_otps.sql
#   sqlcmd ... -v DatabaseName=oneshop_v3_local_test -i database/test-fixtures/02_test_fixture_seed.sql
# The suite needs the scenario fixture above (6 Stores, 8 sample Orders). database/02_seed.sql is the real-product demo seed
# (for the operational DB via the seed wrapper); only RealProductSeedIntegrityTest is written for that one.
mvn -B '-Doneshop.test.db=oneshop_v3_local_test' test
mvn -B '-Doneshop.test.db=oneshop_v3_local_test' clean package
```

Maven mặc định chọn `oneshop_test`; ghi đè `DB_NAME` trong `.env` cho JVM test và tắt scheduler. `TestDatabaseSafetyInitializer` từ chối datasource không có tên `oneshop*_test` trước khi tạo bean, kể cả override URL/Hikari hoặc Hibernate create/update. Khi chạy integration test trực tiếp trong IDE bằng profile `dev`, đặt `-DDB_NAME=oneshop_v3_local_test`.

Không chạy song song hai suite ghi dữ liệu trên cùng database test. Fixture khôi phục nội dung sau mỗi test; identity counters vẫn có thể tăng. Không chạy test trên `oneshop`.

## Cấu hình vận hành

| Biến | Mặc định / mục đích |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | localhost, 1433, oneshop |
| `DB_USERNAME`, `DB_PASSWORD` | Credentials ngoài repository |
| `DB_TRUST_SERVER_CERTIFICATE` | false |
| `JWT_SECRET` | Bắt buộc, ít nhất 32 byte UTF-8 |
| `JWT_COOKIE_SECURE` | false ở dev, true ở prod |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Cần khi upload ảnh |
| `SPRING_PROFILES_ACTIVE` | dev; Docker dùng prod |
| `PORT` | 8080 |
| `ONESHOP_CHECKOUT_ONLINE_ENABLED` | **true** (dev); profile `prod` đặt **false** vì không có cổng thanh toán thật |
| `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED` | **false** (mặc định, chỉ bật chủ động): worker ghi dữ liệu (hủy đơn ONLINE quá 15 phút, hoàn tồn). Khi tắt, đơn ONLINE quá hạn hiện ở Admin → Thanh toán → "Cần xử lý" để Admin đối soát từng đơn |
| `ONESHOP_PAYMENT_MOCK_GATEWAY_ENABLED` | true ở dev, false ở prod; gateway từ chối mô phỏng khi profile prod active |

Không dùng `JPA_DDL_AUTO` để chạy schema. ONLINE dùng cổng thanh toán MÔ PHỎNG phía server (không có cổng thật); production giữ ONLINE đóng. Thay đổi production cần quy trình riêng.

## Đường dẫn và quyền

| Khu vực | Quyền |
| --- | --- |
| `/`, `/products/**`, `/stores/**`, `/login`, `/register`, `/health` | Public |
| `/cart/**`, `/checkout/**`, `/orders/**`, `/reviews/**` | CUSTOMER, dữ liệu của chính tài khoản |
| `/staff/fulfillments/**`, `/staff/stock/**`, `/staff/inventory-history/**`, `/staff/reviews/**` | STAFF + assignment ACTIVE đúng Store nguồn |
| `/admin/**` | ADMIN |
| `/api/auth/login`, `/api/auth/register` | JSON auth; registration luôn CUSTOMER |

SiteMesh dùng ba decorator Client/Staff/Admin; `/decorators/**` không gọi trực tiếp. Staff thao tác Fulfillment, không sửa trạng thái tổng Order. URL Staff V2 chỉ redirect đọc hoặc từ chối mutation. Payment/refund hiện là mô phỏng, không tích hợp ngân hàng thật.

## Tài liệu hiện hành

- [Đặc tả nghiệp vụ V3 và quyết định DEC](docs/OneShop_V3_DacTaNghiepVu_MaTranChuyenDoiCSDL.md)
- [Kế hoạch migration backend V3](docs/v3/backend-audit/BACKEND_V3_MIGRATION_PLAN.md)
- [Khảo sát database live và schema](database/SEED_DATABASE_SURVEY.md)
- [Runbook seed/reset/validation](database/SEED_RUNBOOK.md)
- [Nguồn và giá sản phẩm thật](database/seed-data/products.json), [ghi công hình ảnh](src/main/resources/data/image-credits.json)
- [Báo cáo deep audit, database runbook, kết quả test và ma trận cleanup](docs/v3/final-deep-audit/DEEP_AUDIT_REPORT.md)

BE-01 đến BE-07 được giữ trong `docs/v3/backend-*` làm bằng chứng lịch sử; `docs/v3/final-system-audit` là audit ngày 14/10. Đường dẫn SQL cũ trong các báo cáo này không phải runbook hiện hành.
