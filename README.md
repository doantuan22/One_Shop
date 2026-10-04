# OneShop

**Xây dựng website bán mỹ phẩm OneShop theo mô hình chuỗi cửa hàng.**

Giai đoạn hiện tại là **bộ khung (skeleton)** của dự án: cấu hình công nghệ, kiến trúc phân lớp, xác thực JWT,
layout SiteMesh, tích hợp Cloudinary và cấu hình triển khai Render. Nghiệp vụ OneShop (đơn hàng, kho theo chi nhánh,
khuyến mãi, ...) sẽ được bổ sung sau khi có ERD chính thức.

## 1. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ / Build | Java 21 (LTS), Maven (kèm Maven Wrapper) |
| Framework | Spring Boot 3.5.x, Spring MVC |
| View | Thymeleaf, Bootstrap 5.3 (đặt sẵn trong `static/vendor`), HTML5, CSS, JavaScript |
| Layout | SiteMesh 3 (Decorator) |
| Dữ liệu | Spring Data JPA (Hibernate), **Microsoft SQL Server** |
| Bảo mật | Spring Security 6, JWT (jjwt), BCrypt |
| Ảnh | Cloudinary |
| Triển khai | Docker, Render; SQL Server đặt trên cloud |

## 2. Kiến trúc

```text
Browser -> Thymeleaf + Bootstrap -> Controller -> Service -> Repository (Spring Data JPA) -> SQL Server
```

* Controller **không** truy cập Repository trực tiếp; luôn đi qua Service.
* `controller/web`: trang Thymeleaf. `controller/api`: REST/JSON (`/api/**`, `/health`).
* **SiteMesh**: mọi trang HTML được render bình thường, sau đó SiteMesh gói vào layout
  `templates/layouts/main.html` (header, navbar, nội dung, footer). `DecoratorController` (`/decorators/main`) là cầu nối
  giữa SiteMesh và Thymeleaf; đường dẫn này không thể gọi trực tiếp từ bên ngoài. Trang con chỉ cần viết
  `<title>` và `<body>`, không cần khai báo layout.
* **JWT**:
  * Luồng web (Thymeleaf): `POST /login` cấp JWT và lưu trong cookie **HttpOnly**, `SameSite=Lax`
    (`Secure` khi chạy HTTPS). Form được bảo vệ CSRF.
  * Luồng API: `POST /api/auth/login` trả `accessToken`; gửi lại bằng header `Authorization: Bearer <token>`.
  * `JwtAuthenticationFilter` chấp nhận cả hai nguồn; session là `STATELESS`.
* **Phân quyền**: `ROLE_ADMIN` (`/admin/**`), `ROLE_STAFF` hoặc `ROLE_ADMIN` (`/staff/**`), các đường dẫn còn lại
  ngoài danh sách công khai yêu cầu đăng nhập.
* **Ảnh**: `CloudinaryService` upload/xóa ảnh; database chỉ lưu `imageUrl` và `imagePublicId`, không lưu dữ liệu ảnh.

## 3. Yêu cầu môi trường

* JDK 21 (hoặc 17)
* Git
* Một SQL Server truy cập được (cloud hoặc local) và tài khoản Cloudinary
* Không cần cài Maven: dùng `./mvnw` (Windows: `mvnw.cmd`)

## 4. Cấu hình

Ứng dụng đọc cấu hình từ **biến môi trường**; không có thông tin bí mật nào nằm trong source code.
Khi chạy development, có thể tạo file `.env` ở thư mục gốc (đã được `.gitignore`):

```bash
cp .env.example .env    # rồi điền giá trị thật
```

Profile `dev` tự nạp `.env` (nếu có). Trên Render, đặt biến trong dashboard.

### Database (SQL Server)

```text
jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=${DB_NAME};encrypt=true;trustServerCertificate=false
```

* Kết nối luôn mã hóa (`encrypt=true`), phù hợp SQL Server cloud.
* SQL Server local dùng chứng chỉ tự ký: đặt `DB_TRUST_SERVER_CERTIFICATE=true` (chỉ cho development).
* Schema do `database/*.sql` (Phase 2, 19 bảng) quản lý; chạy các script đó trước khi khởi động app.
  `dev`: `ddl-auto=validate` (khởi động thất bại nếu entity lệch schema). `prod`: `ddl-auto=none`.
  Hibernate không bao giờ tự tạo/sửa schema.
* Chuỗi tiếng Việt được lưu bằng `NVARCHAR` (`hibernate.use_nationalized_character_data=true`); cột thời gian là
  `DATETIME2` theo giờ máy chủ SQL (`SYSDATETIME()`), ánh xạ bằng `LocalDateTime`.
* Test đọc dữ liệu seed thật (`DatabaseFoundationIntegrationTest`) tự chạy khi có `.env` hoặc biến `DB_USERNAME`,
  nếu không sẽ bị bỏ qua.
* Ứng dụng vẫn khởi động được khi chưa kết nối được database; chỉ các trang cần dữ liệu mới báo lỗi.

### JWT

* `JWT_SECRET`: chuỗi bí mật **tối thiểu 32 ký tự**, ví dụ `openssl rand -base64 48`.
  Thiếu hoặc quá ngắn thì ứng dụng từ chối khởi động (có chủ đích).
* `JWT_EXPIRATION_MINUTES` (mặc định 60), `JWT_COOKIE_SECURE` (`false` ở dev, `true` ở prod).

### Cloudinary

Đặt `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Nếu chưa đặt, ứng dụng vẫn chạy
nhưng `CloudinaryService` báo lỗi rõ ràng khi được gọi.

## 5. Chạy và build

```bash
# chạy development (profile dev)
./mvnw spring-boot:run

# chạy test
./mvnw test

# build JAR (target/oneshop.jar)
./mvnw clean package
```

Mở http://localhost:8080. Các route mẫu: `/`, `/login`, `/register`, `/products`, `/cart`, `/health`.

Test không cần SQL Server hay secret thật (profile `test` dùng secret giả và mock các bean truy cập DB).

## 6. Triển khai lên Render

1. Đẩy source lên GitHub.
2. Trên Render chọn **New > Blueprint** và trỏ tới repository (dùng `render.yaml`), hoặc tạo **Web Service** kiểu
   Docker với `Dockerfile` ở thư mục gốc.
3. Đặt các biến môi trường trong bảng bên dưới (các biến bí mật để `sync: false`, nhập tay trên dashboard).
4. Health check path: `/health` (không cần đăng nhập, không phụ thuộc database).

`Dockerfile` build nhiều giai đoạn (Maven build, sau đó chạy JRE), chạy bằng user không phải root, đọc cổng từ `PORT`
(mặc định 8080) và dùng profile `prod`. SQL Server nằm ngoài Render, ứng dụng chỉ kết nối qua biến môi trường.
Nếu nhà cung cấp SQL Server có tường lửa IP, cần cho phép địa chỉ outbound của Render.

## 7. Biến môi trường

| Biến | Bắt buộc | Mô tả |
|---|---|---|
| `DB_HOST` | Có | Host SQL Server |
| `DB_PORT` | Không (1433) | Cổng |
| `DB_NAME` | Có | Tên database |
| `DB_USERNAME` | Có | Tài khoản |
| `DB_PASSWORD` | Có | Mật khẩu |
| `JWT_SECRET` | Có | Khóa ký JWT, tối thiểu 32 ký tự |
| `CLOUDINARY_CLOUD_NAME` | Khi dùng ảnh | Cloud name |
| `CLOUDINARY_API_KEY` | Khi dùng ảnh | API key |
| `CLOUDINARY_API_SECRET` | Khi dùng ảnh | API secret |
| `PORT` | Không (8080) | Cổng HTTP (Render tự đặt) |
| `SPRING_PROFILES_ACTIVE` | Không (`dev`) | `dev` hoặc `prod` (Docker mặc định `prod`) |
| `DB_TRUST_SERVER_CERTIFICATE` | Không (`false`) | `true` chỉ cho SQL Server local chứng chỉ tự ký |
| `JPA_DDL_AUTO` | Không (`none` ở prod) | `none`, `validate`, `update` |
| `JWT_EXPIRATION_MINUTES` | Không (60) | Thời hạn token |
| `JWT_COOKIE_SECURE` | Không | Cờ `Secure` của cookie JWT |
| `DB_POOL_SIZE`, `DB_CONNECTION_TIMEOUT_MS` | Không | Tinh chỉnh connection pool |

## 8. Cấu trúc thư mục

```text
oneshop/
├── pom.xml, mvnw, mvnw.cmd, .mvn/
├── Dockerfile, render.yaml, .env.example
└── src/
    ├── main/
    │   ├── java/com/oneshop/
    │   │   ├── OneShopApplication.java
    │   │   ├── config/        Security, SiteMesh, Cloudinary, Web, JPA, properties
    │   │   ├── controller/
    │   │   │   ├── client/    Trang Thymeleaf của khách (home, products, cart, login/register)
    │   │   │   ├── staff/     Khu vực /staff/** (khung, Phase 10)
    │   │   │   ├── admin/     Khu vực /admin/** (khung, Phase 6/11)
    │   │   │   ├── web/       DecoratorController (cầu nối SiteMesh - Thymeleaf)
    │   │   │   └── api/       REST: /api/auth/**, /health
    │   │   ├── service/       Interface; impl/ chứa cài đặt
    │   │   ├── repository/    Spring Data JPA
    │   │   ├── entity/        19 entity theo schema V2 + enum trạng thái
    │   │   ├── dto/           request/, response/
    │   │   ├── security/      jwt/ (JwtService, filter, cookie), service/ (UserDetailsService)
    │   │   ├── exception/     GlobalExceptionHandler, WebExceptionHandler, ...
    │   │   ├── mapper/, util/
    │   └── resources/
    │       ├── templates/     layouts/, fragments/, home/, auth/, product/, cart/,
    │       │                  order/, user/, staff/, admin/ (đang trống), error.html
    │       ├── static/        css/, js/, images/, vendor/bootstrap/
    │       └── application.properties, application-dev.properties, application-prod.properties
    └── test/                  Test tích hợp (Tomcat thật), JWT, Cloudinary
```
