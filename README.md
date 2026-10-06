# OneShop

**Xây dựng website bán mỹ phẩm OneShop theo mô hình chuỗi cửa hàng.**

Giai đoạn hiện tại: **Phase 8 – Checkout + Orders** (Roadmap V2, mục 12). Các phase đã xong:

* **Phase 8 – Checkout + Orders**: từ các dòng giỏ đã chọn tạo một CheckoutSession và mỗi chi nhánh một Order, lưu
  snapshot OrderItem, trừ tồn kho có khóa và ghi biến động kho, tất cả trong một transaction.

* **Phase 7 – Cart nhiều Store**: thêm / sửa số lượng / xóa trong giỏ, giỏ nhóm theo chi nhánh, chọn cả chi nhánh
  hoặc từng sản phẩm để chuyển sang bước đặt hàng.
* **Phase 6 – Catalog + Store chain**: Admin quản lý Danh mục, Thương hiệu, Sản phẩm/SKU, Chi nhánh, Sản phẩm theo
  chi nhánh và ảnh (Cloudinary); Client có Hệ thống cửa hàng, chọn chi nhánh, catalog toàn chuỗi / theo chi nhánh và
  trang chi tiết sản phẩm với giá, tình trạng hàng theo từng chi nhánh.

* **Phase 3 – Spring Boot foundation**: 19 Entity JPA khớp schema `database/*.sql` (Phase 2), 19 Repository (kèm
  `PESSIMISTIC_WRITE` cho `StoreProduct`), khung 11 Service, Controller tách `client/staff/admin`, DTO + validation.
* **Phase 4 – SiteMesh + UI nền**: 3 layout riêng cho Client, Staff, Admin và bộ component Bootstrap dùng lại.
* **Phase 5 – Auth + JWT**: đăng ký/đăng nhập/đăng xuất, JWT trong cookie HttpOnly, phân quyền CUSTOMER/STAFF/ADMIN ở
  backend, Store scope của Staff theo `StaffStoreAssignment` ACTIVE, CSRF.

Vòng đời thanh toán và xử lý đơn (Phase 9 trở đi) chưa được triển khai.

### Đặt hàng (Phase 8)

* `GET /checkout?cartItemIds=…` hiện trang đặt hàng: mỗi chi nhánh một khối, tự chọn cách nhận (giao hàng / nhận tại
  cửa hàng) và thanh toán (COD, tại cửa hàng, trực tuyến) riêng. `POST /checkout` đặt hàng; `GET /checkout/{id}` xem
  kết quả. Chỉ `CUSTOMER`.
* Request chỉ mang `cartItemIds` và lựa chọn của từng chi nhánh. Chủ đơn lấy từ người đăng nhập; chi nhánh của đơn
  lấy từ StoreProduct của các dòng giỏ; giá, thành tiền, trạng thái do máy chủ tính.
* Trong **một transaction**: khóa giỏ của khách, khóa các StoreProduct (`PESSIMISTIC_WRITE`, theo id tăng dần), đọc lại
  giá / tồn / trạng thái, kiểm tra mọi dòng và mọi chi nhánh, rồi tạo CheckoutSession, Order, OrderItem (snapshot tên,
  đơn giá, số lượng, thành tiền), trừ tồn kèm `InventoryMovement` loại `ORDER`, xóa đúng các dòng giỏ đã đặt. Một chỗ
  không hợp lệ thì không có gì được tạo.
* Trạng thái đầu của Order: thanh toán `ONLINE` → `PENDING_PAYMENT`; `COD` và `PAY_AT_STORE` → `CONFIRMED`; tất cả
  `UNPAID`. Chưa tạo dòng `payments`, chưa có mã nhận hàng: đó là việc của Phase 9.
* Giao hàng chỉ đi với COD hoặc trực tuyến; nhận tại cửa hàng chỉ đi với thanh toán tại cửa hàng hoặc trực tuyến; chi
  nhánh không bật giao hàng / nhận tại cửa hàng thì không chọn được cách đó.

### Giỏ hàng nhiều chi nhánh (Phase 7)

* Mỗi dòng giỏ gắn với một **StoreProduct** (SKU tại một chi nhánh), không gắn với Product. Cùng một SKU ở hai chi
  nhánh là hai dòng riêng; thêm lại đúng StoreProduct đã có thì cộng số lượng vào dòng cũ.
* Giỏ thuộc về khách đang đăng nhập (lấy từ JWT); không request nào nhận `userId`, giá hay thành tiền. Id dòng giỏ
  không thuộc giỏ của mình được coi như không tồn tại (404).
* Giỏ **chỉ kiểm tra** tồn kho mỗi lần thêm / sửa, không giữ hàng, không trừ kho, không ghi `InventoryMovement`. Bước
  đặt hàng (Phase 8) sẽ kiểm tra lại và mới trừ kho.
* Trang `/cart` nhóm theo chi nhánh, giá là giá hiện tại của StoreProduct (không lưu trong giỏ), không hiển thị số tồn.
  Dòng không còn mua được (hết hàng, thiếu hàng, ngừng bán) vẫn nằm trong giỏ, được đánh dấu và không chọn được.
* Checkbox "Chọn cả chi nhánh" / từng dòng chỉ là trạng thái trên trang; bấm tiếp tục sẽ gửi danh sách `cartItemIds`
  tới `GET /cart/selection`, nơi máy chủ kiểm tra lại và hiển thị các dòng đã chọn theo chi nhánh. Chưa tạo đơn.
* Đổi "Chi nhánh đang chọn" không làm thay đổi giỏ hàng.

### Catalog và mô hình chuỗi (Phase 6)

* **Product = một SKU** dùng chung toàn chuỗi, không có giá hay tồn kho. **StoreProduct** (SKU tại một chi nhánh) là
  nguồn duy nhất của giá, tồn kho, trạng thái bán. Một SKU được coi là *đang bán* tại một chi nhánh khi StoreProduct
  `ACTIVE`, Store `ACTIVE`, Product `ACTIVE` và Danh mục/Thương hiệu của nó `ACTIVE`.
* **Chi nhánh đang chọn** lưu trong cookie `ONESHOP_STORE` (HttpOnly, SameSite=Lax; ứng dụng không dùng session).
  `SelectedStoreInterceptor` kiểm tra id với database ở mỗi request; id không tồn tại, không phải số hoặc của chi nhánh
  đã ngừng hoạt động thì cookie bị xóa và khách quay về "Toàn chuỗi". Đổi chi nhánh chỉ đổi ngữ cảnh duyệt, không động
  tới giỏ hàng. Chọn bằng `POST /stores/select` (có CSRF), bỏ chọn bằng `POST /stores/clear`.
* **Catalog** (`/products`): chưa chọn chi nhánh thì mỗi SKU hiện một lần kèm các chi nhánh đang bán; đã chọn thì chỉ
  hiện StoreProduct của chi nhánh đó với giá của chính chi nhánh đó. Tìm theo tên/SKU, lọc Danh mục, Thương hiệu.
* **Khách chỉ thấy Còn hàng / Hết hàng**; số tồn chính xác chỉ có ở trang Admin.
* **Admin** (`/admin/categories`, `/admin/brands`, `/admin/products`, `/admin/stores`, `/admin/store-products`): thêm,
  sửa, đổi trạng thái; không có xóa cứng. Mỗi lần đổi tồn kho đều ghi một `InventoryMovement` loại `STOCK_ADJUST`.
* **Ảnh**: tải lên từ trang sửa Sản phẩm / Thương hiệu, lưu trên Cloudinary; database chỉ giữ URL và `public_id`.
  Cần đặt `CLOUDINARY_*`, nếu không trang sẽ báo lỗi và không lưu gì.

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
* **SiteMesh**: mọi trang HTML được render bình thường, sau đó SiteMesh gói vào layout của khu vực (cấu hình ở
  `SiteMeshConfig`):

  | URL | Layout | Nội dung |
  |---|---|---|
  | `/staff`, `/staff/**` | `layouts/staff.html` | Top bar + sidebar; khu vực **Chi nhánh được phân công** |
  | `/admin`, `/admin/**` | `layouts/admin.html` | Top bar + sidebar quản trị, phạm vi **Toàn chuỗi** |
  | còn lại | `layouts/client.html` | Header (**Chi nhánh đang chọn** / **Toàn chuỗi**), navbar, footer |

  `DecoratorController` (`/decorators/client|staff|admin`) là cầu nối giữa SiteMesh và Thymeleaf; các đường dẫn này
  không thể gọi trực tiếp từ bên ngoài. Trang con chỉ cần viết `<title>` và `<body>`, không cần khai báo layout.
  Sidebar của Staff/Admin thu thành menu off-canvas trên màn hình nhỏ.
* **Ngữ cảnh Store trong layout** chỉ là lớp hiển thị. Layout Client đọc model attribute tùy chọn `selectedStore`
  (`StoreResponse`, vắng mặt = "Toàn chuỗi"; Phase 6 cấp dữ liệu). Layout Staff đọc `assignedStores`
  (`List<StoreResponse>`, rỗng = "Chưa được phân công") do `StaffStoreScopeInterceptor` đặt ở mỗi request.
* **Component UI** trong `templates/fragments/components.html` (dùng bằng `th:replace`): `pageHeader`, `navItem`, `card`,
  `statCard`, `dataTable` + `emptyRow`, `emptyState`, `formField`, `formSelect`, `modal`. Ví dụ dùng đủ bộ nằm ở
  `templates/admin/dashboard.html`; `staff/dashboard.html` là trang mẫu của Staff.
* **JWT**:
  * Luồng web (Thymeleaf): `POST /login` cấp JWT và lưu trong cookie **HttpOnly**, `SameSite=Lax`
    (`Secure` khi chạy HTTPS). Form được bảo vệ CSRF.
  * Luồng API: `POST /api/auth/login` trả `accessToken`; gửi lại bằng header `Authorization: Bearer <token>`.
  * `JwtAuthenticationFilter` chấp nhận cả hai nguồn; session là `STATELESS`.
* **Phân quyền** (quyết định ở backend trong `SecurityConfig`, không dựa vào việc ẩn menu):

  | Đường dẫn | Ai được vào |
  |---|---|
  | `/`, `/login`, `/register`, `/products/**`, `/stores/**`, `/health` | Mọi người |
  | `/cart/**` | `CUSTOMER` |
  | `/staff/**`, `/api/staff/**` | `STAFF` (và phải có Store scope, xem dưới) |
  | `/admin/**`, `/api/admin/**` | `ADMIN` |
  | còn lại | Đã đăng nhập |

  Chưa đăng nhập: trang web chuyển về `/login`, API trả 401. Sai role: 403. Role được đọc lại từ database ở mỗi
  request (không tin role trong token), nên tài khoản bị khóa hoặc đổi role mất quyền ngay.
* **Store scope của Staff (BR-14)**: `StaffStoreScopeInterceptor` chạy trước mọi controller của `/staff/**` và
  `/api/staff/**`, lấy danh sách Store từ `staff_store_assignments` (assignment `ACTIVE`, Store `ACTIVE`, tài khoản
  `STAFF` `ACTIVE`) theo email của người đang đăng nhập và đặt vào request attribute `assignedStores`. Store không bao
  giờ lấy từ tham số, header hay token. Staff chưa có assignment hợp lệ chỉ vào được trang `/staff` (hiện thông báo),
  mọi URL Staff khác trả 403. Service xử lý dữ liệu của một Store cụ thể gọi `StoreService.requireAssignedStore`.
* **CSRF**: bật cho mọi request thay đổi dữ liệu đi bằng cookie (form Thymeleaf tự chèn `_csrf`; đăng xuất là
  `POST /logout`). Chỉ bỏ qua cho `/api/auth/**` và request mang `Authorization: Bearer` (trình duyệt không tự gửi được).
  Token không bị đổi ở mỗi request, nên form trên trang mở từ trước (tab khác, nút Back) vẫn gửi được.
* **Đăng ký** luôn tạo tài khoản `CUSTOMER`; tài khoản Staff/Admin không tạo được qua form. Sau đăng nhập, mỗi role
  được chuyển về khu vực của mình (`/`, `/staff`, `/admin`).
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

Mở http://localhost:8080. Các route mẫu: `/`, `/login`, `/register`, `/products`, `/products/{id}`, `/stores`, `/cart` (Customer), `/health`, `/staff` (Staff), `/admin` (Admin).

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
    │   │   │   ├── staff/     Khu vực /staff/** (trang mẫu Phase 4; nghiệp vụ Phase 10)
    │   │   │   ├── admin/     Khu vực /admin/** (trang mẫu Phase 4; nghiệp vụ Phase 6/11)
    │   │   │   ├── web/       DecoratorController (cầu nối SiteMesh - Thymeleaf)
    │   │   │   └── api/       REST: /api/auth/**, /health
    │   │   ├── service/       Interface; impl/ chứa cài đặt
    │   │   ├── repository/    Spring Data JPA
    │   │   ├── entity/        19 entity theo schema V2 + enum trạng thái
    │   │   ├── dto/           request/, response/
    │   │   ├── security/      jwt/ (JwtService, filter, cookie), service/ (UserDetailsService),
    │   │   │                  StaffStoreScopeInterceptor (Store scope của Staff)
    │   │   ├── exception/     GlobalExceptionHandler, WebExceptionHandler, ...
    │   │   ├── mapper/
    │   └── resources/
    │       ├── templates/     layouts/ (client, staff, admin), fragments/ (header, navbar, footer, staff, admin,
    │       │                  assets, components), home/, auth/, product/, cart/, staff/, admin/,
    │       │                  order/, user/ (đang trống), error.html
    │       ├── static/        css/, js/, images/, vendor/bootstrap/
    │       └── application.properties, application-dev.properties, application-prod.properties
    └── test/                  Test tích hợp (Tomcat thật), JWT, Cloudinary
```
