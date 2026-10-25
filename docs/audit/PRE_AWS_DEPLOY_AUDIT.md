# PRE-AWS DEPLOY AUDIT — OneShop

Ngày: 2026-10-25 · Nhánh: `cleanup/pre-aws-deploy` (tách từ `tuntun`, **chưa commit**: mọi thay đổi đang ở trạng thái staged để bạn review; không push, không merge, không deploy).

**Kết luận: READY WITH WARNINGS** (mục G).

---

## A. System Understanding

Kiến trúc đã đọc và xác minh từ mã nguồn (không dựa vào tài liệu cũ): Spring Boot 3.5.16 / Java 21 monolith, Thymeleaf + SiteMesh (3 decorator Client/Staff/Admin), Spring Security stateless với JWT trong cookie HttpOnly (hoặc Bearer cho `/api`), CSRF bằng cookie, JPA/Hibernate chỉ `validate`, SQL Server, Cloudinary cho ảnh. 595 file trong `src/`, 30 bảng, 29 entity/repository.

| Nhóm nghiệp vụ | Trạng thái thực tế (đã truy vết Controller → Service → Repository → bảng) |
|---|---|
| Đăng ký/đăng nhập/đăng xuất, quên mật khẩu bằng mã OTP qua e-mail (`password_reset_otps`) | Hoàn thiện. Role CUSTOMER/STAFF/ADMIN đọc lại từ DB theo user ID ở mỗi request |
| Chi nhánh, nhân viên | Hoàn thiện. **Mỗi nhân viên một chi nhánh ACTIVE** (unique index + service; dùng "Chuyển chi nhánh") |
| Danh mục, thương hiệu, sản phẩm (giá online duy nhất trên `Product`), tồn kho theo `StoreProduct` | Hoàn thiện; ảnh qua Cloudinary với journal dọn ảnh (`image_cleanup_tasks`), xóa file chỉ sau commit |
| Giỏ hàng theo Product, checkout, phân bổ kho (`service/allocation`), COD tách nhiều chi nhánh cần consent, ONLINE 1 Order nhiều Fulfillment, STORE_PICKUP | Hoàn thiện, có kiểm thử đồng thời không oversell |
| Thanh toán (cổng **mô phỏng** phía server), timeout 15 phút, đối soát, hoàn tiền mô phỏng, trả hàng sau giao, khuyến mãi | Hoàn thiện; worker timeout tắt mặc định, ONLINE đóng ở `prod` |
| Xử lý Fulfillment tại chi nhánh, mã nhận hàng, đánh giá + phản hồi của Staff, kiểm duyệt của Admin | Hoàn thiện |
| Mã chết còn dependency | Phần "đa phân công nhiều chi nhánh" trong `StaffStoreScopeService`/dashboard vẫn xử lý danh sách nhiều Store dù DB cấm (không ảnh hưởng, **không dọn** vì đụng nghiệp vụ) |
| Mâu thuẫn giữa các lần phát triển đã phát hiện | (1) `02_seed.sql` là seed demo nhưng bộ test cần seed kịch bản; (2) 11 test đa phân công xung đột quy tắc 1 Store/Staff; (3) chú thích "worker ON by default" sai; (4) thông báo lỗi schema trỏ tới file migration không tồn tại. Tất cả đã xử lý (xem `docs/audit/POST_FIX_FIX_REPORT.md` và dưới đây) |

Lỗi có sẵn ghi nhận, không sửa (ngoài phạm vi cleanup, an toàn thấp): `GET /favicon.ico` trả 404 (đường dẫn được khai public nhưng không có file) → một dòng lỗi console ở lần tải trang đầu; `/error` trực tiếp trả 500 (test hiện hành khẳng định như vậy).

## B. Database Cleanup

**Trước:** `database/01_schema.sql … 06_*.sql` + `03_validate_seed.sql` + `test-fixtures/` + một migration nằm ở `src/test/resources/db`; các migration BE-06/OPEN-10.1/returns/promotions bị xóa khỏi repo nhưng thông báo lỗi của ứng dụng vẫn trỏ tới chúng; `01_schema.sql` thiếu bảng `password_reset_otps` (phải chạy thêm script 06).

**Sau:**
```text
database/schema/01_schema.sql         fresh install, 30 bảng, ràng buộc + index inline, tự kiểm tra cấu trúc cuối script
database/seed/01_reference_data.sql   3 role (mọi môi trường, idempotent)
database/seed/02_bootstrap_admin.sql  admin đầu tiên từ BCrypt hash (production, idempotent)
database/seed/20_demo_data.sql        CHỈ demo (bọc transaction, từ chối DB có dữ liệu khác, chạy lại không tạo trùng)
database/seed/99_validate_demo_data.sql
database/migrations/V001…V008 + rollback/R001,R002   DB đã tồn tại
database/test/test_fixture_seed.sql   chỉ cho test tự động (*_test)
```

| Script cũ | Số phận |
|---|---|
| `01_schema.sql` | **Hợp nhất**: chuyển `schema/`, thêm `password_reset_otps` (không nối script) |
| `03_one_active_store_per_staff`, `04_image_cleanup_tasks`, `05_product_primary_image_unique`, `06_password_reset_otps` | **Giữ làm migration** `V005…V008` (đã nằm sẵn trong schema mới; chỉ cần cho DB cũ) |
| migration BE-06 / OPEN-10.1 / returns / promotions (đã xóa ở f7c3bb3) | **Khôi phục từ git** thành `V001…V004`, rollback thành `R001/R002`; bỏ giới hạn tên DB cứng `oneshop`/`oneshop*_test` (chặn deploy tên khác) |
| `02_seed.sql` | **Tách/viết lại**: roles → `01_reference_data`; phần demo → `20_demo_data` không cần "seed wrapper" đã mất; bỏ phụ thuộc transaction ngoài |
| `03_validate_seed.sql` | Giữ → `99_validate_demo_data.sql` |
| `test-fixtures/02_test_fixture_seed.sql` | Giữ → `database/test/` |
| `scripts/seed/*` (wrapper PowerShell, `generate_seed.py`), `database/seed-data/products.json`, `SEED_*.md` | **Không có trong repo** (README cũ tham chiếu; chưa từng được commit). Thay bằng `scripts/db/*` |

Tài liệu: `database/README.md`. Công cụ: `scripts/db/Initialize-OneShopDatabase.ps1`, `initialize-database.sh`, `New-PasswordHash.ps1`, `new-password-hash.sh` (+ `PasswordHashTool` trong jar, đọc mật khẩu từ biến môi trường/stdin, không in, không nhận qua tham số).

**Kết quả kiểm chứng (đã chạy thật trên SQL Server local):**

| Kiểm tra | Kết quả |
|---|---|
| Khởi tạo DB mới chế độ `demo` (sh) + validation | PASS (12 user, 40 sản phẩm, 151 StoreProduct, validation "PASS") |
| Chế độ `production` (sh và ps1): 0 sản phẩm, 1 admin, 3 role; chạy lại bootstrap → "đã có admin"; nạp demo lên DB đó → bị từ chối (51204) | PASS |
| Chế độ `test` (sh và ps1), rồi cả suite 2596 test chạy trên DB đó | PASS (trừ 3 test bị chặn, mục F) |
| Chạy lại `01_reference_data` và `20_demo_data` | PASS: không tạo trùng |
| Schema mới vs database `oneshop` hiện hành (480 đối tượng: cột, kiểu, default, index, FK, CHECK, collation) | **Giống hệt** |
| Schema mới vs DB dựng bằng đường cũ (01_schema + script 06) | Giống hệt |
| Chạy V001…V008 trên DB mới | PASS, schema không đổi (idempotent) |
| Hibernate `validate` + `V3SchemaCompatibilityVerifier` khi khởi động `prod` trên DB mới | PASS |
| Migration trên **bản sao có dữ liệu cũ thật** | NOT RUN: không có DB ở schema cũ; chỉ chứng minh được idempotence + tương đương schema. `oneshop` không bị chạm tới |
| Chuyển đổi V2 → V3 | Không còn trong repo (chỉ ở lịch sử git); ghi trong `database/README.md` |

## C. Seed Data

- Demo: 40 SKU thật (giá niêm yết, ảnh CC BY-SA đã có trên Cloudinary — **không tạo URL mới**), 12 thương hiệu, 10 danh mục, 5 chi nhánh (Thủ Đức, Gò Vấp, Quận 7 đủ dịch vụ; Quận 10 chỉ nhận tại cửa hàng; Bình Thạnh tạm ngưng), 151 StoreProduct có đủ hàng/ít hàng/hết hàng, 1 mã khuyến mãi `DEMO10`, địa chỉ khách mô phỏng.
- Tài khoản demo (đổi tên hiển thị thực tế, giữ e-mail/username vì profile `demo-seed` và guard `@example.com` dựa vào): `admin@example.com` (Nguyễn Hoàng Minh), `staff.thuduc|govap|quan7|quan10|binhthanh@example.com` (mỗi người một chi nhánh, Bình Thạnh INACTIVE), `khachhang1…6@example.com`.
- **Mật khẩu không nằm trong repo**: seed demo nhận `-v SeedPasswordHash` (BCrypt do chính `PasswordEncoder` của ứng dụng tạo). `note.md` (liệt kê mật khẩu + hash) đã xóa.
- Địa chỉ chi nhánh vẫn mang nhãn "mô phỏng": đây là guard an toàn của `DemoTransactionSeed` (chỉ chạy khi mọi chi nhánh là dữ liệu mô phỏng) nên giữ nguyên, không bịa địa chỉ thật.
- **Demo vs Production**: production dùng `schema` + `01_reference_data` + `02_bootstrap_admin` (chế độ `Production`) — không tài khoản demo, không đơn thử. Demo nạp thủ công bằng chế độ `Demo`; ứng dụng không tự seed ở bất kỳ profile nào.
- Còn sót: ID cố định (IDENTITY_INSERT) trong seed demo — chấp nhận vì chỉ nạp vào DB rỗng và script từ chối mọi DB có dữ liệu khác. Tài khoản fixture test dùng mật khẩu test cố định (`database/test/`, test Java, `scripts/lib/cdp.mjs`) chỉ hợp lệ trên DB `oneshop*_test`; `cdp.mjs` đọc `ONESHOP_DEMO_PASSWORD` nếu có.

## D. Repository Cleanup

Tracked: 2.545 → 901 file. 1.662 file bị xóa khỏi index:

| Đã xóa | Lý do / căn cứ |
|---|---|
| `docs/qa-audit/` (1.433 tệp) | Evidence do test sinh ra (ảnh chụp, JSON, log); test giờ ghi vào `target/qa-audit/` (`QaAuditEvidence`); đã thêm vào `.gitignore` |
| `test-results/` (209 tệp evidence) | Sinh lại được; 8 báo cáo markdown giữ ở `docs/audit/` |
| `note.md` | Chứa mật khẩu và hash tài khoản test |
| `run_verify.ps1`, `run_verify_v3.ps1`, `run_targeted.ps1` | Đường dẫn tuyệt đối `D:\One_Shop`, ghi log vào thư mục gốc, trỏ DB cũ; không file nào tham chiếu |
| 15 script `scripts/*.mjs` (capture/audit/verify một lần) | 0 tham chiếu từ code/test/README; vẫn còn trong lịch sử git |
| `database/test-fixtures` (thư mục rỗng sau khi chuyển), `src/test/resources/db` | Chuyển vị trí |

**Giữ:** toàn bộ `src/`; `scripts/lib/cdp.mjs`, `scripts/qa-audit/{fix-ui,screens}.mjs` (được test Java gọi), `responsive-fixture.mjs`, `smoke-pages.mjs`, `scripts/smoke.mjs`; `Dockerfile`, `.dockerignore`, `render.yaml` (hạ tầng hiện có); `docs/report/` (báo cáo kiến trúc/chức năng/giao diện + công cụ sinh, ~50 MB, lưu trữ — có thể chuyển sang repo khác trước khi lên `main` nếu muốn repo gọn hơn); `docs/audit/*` (báo cáo kiểm thử đợt trước, đường dẫn SQL cũ trong đó mang tính lịch sử).
Ngoài git (đã bị ignore, không đụng): `target/` (4,4 GB), `backups/*.bak` (139 MB), `.env`, `.env.seed.local`, `screenshots/`.
`.gitignore` thêm: `test-results/`, `docs/qa-audit/`, `screenshots/`, `backups/`, `target/qa-audit/`, `*.hprof`.

Dependency: rà `pom.xml` — mọi starter đều có mã sử dụng (mail → `OtpMailSender`, devtools bị loại khỏi jar, đã xác minh jar không chứa devtools). Không gỡ dependency nào. Không tìm thấy debug code/`System.out` cần dọn; không refactor nghiệp vụ.

Thêm mới: `src/main/java/.../tools/PasswordHashTool.java` (tiện ích CLI nhỏ, không bean/endpoint), `deploy/` (systemd, env mẫu, nginx), `docs/AWS_DEPLOYMENT.md`, `scripts/db/*`, `scripts/qa-audit/smoke-pages.mjs`, test `PaymentProductionRuntimeConfigDatabaseIntegrationTest`.

## E. Security & AWS Preparation

| Hạng mục | Kết quả |
|---|---|
| Secret trong repo | Quét toàn bộ file tracked + untracked-không-ignore (898 file text): **không** thấy giá trị thật của `DB_PASSWORD`, `JWT_SECRET`, `CLOUDINARY_API_KEY/SECRET`, `MAIL_*` từ `.env`; không có khóa AWS/private key/token; không đường dẫn máy cá nhân. Tìm thấy: mật khẩu fixture test (test-only, mục C) và cloud name công khai trong URL ảnh seed |
| Profile | `dev` (mặc định!), `prod`; **production phải đặt `SPRING_PROFILES_ACTIVE=prod`** (đã ghi README/AWS/systemd/Docker). `ddl-auto=validate` ở mọi profile; không tự seed; ONLINE + cổng mô phỏng + worker đều tắt ở `prod` (kiểm chứng bằng test chạy context `prod` thật) |
| Cấu hình đã chuẩn hóa | `.env` chỉ import một chỗ (trùng ở profile dev đã bỏ); thêm `DB_JDBC_OPTIONS` (trust store RDS), `DB_MAX_LIFETIME_MS`, `DB_KEEPALIVE_MS`; `server.error.include-*=never`; `.env.example` sửa (worker mặc định OFF, biến mới); comment "Render" → trung lập proxy/ALB |
| TLS DB | `encrypt=true`, `trustServerCertificate=false` mặc định; hướng dẫn trust store CA của RDS (⚠ chưa thử trên RDS) |
| JWT/cookie/CSRF | Đã đọc: secret ≥ 32 byte bắt buộc (ứng dụng từ chối khởi động nếu thiếu), cookie HttpOnly + SameSite=Lax + Secure (prod), CSRF cookie, role đọc lại từ DB mỗi request, `/admin|/staff` theo role, `/decorators/**` bị chặn, không actuator/debug công khai. Không đổi mô hình |
| Header phản hồi | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Cache-Control` no-store trên API (quan sát thực tế) |
| Build artifact | `oneshop.jar` ≈ 64 MB, chạy `java -jar` với profile `prod` trên DB mới: khởi động 15 s, `/health` 200 ✔ (build ở bản sao ngoài `target/` vì `target/oneshop.jar` đang bị tiến trình khác khóa) |
| Linux | Không đường dẫn tuyệt đối trong `src/main`; `.sh` có LF + bit thực thi; `Dockerfile` đã Linux-ready. Chưa chạy thử trên Linux thật (⚠) |
| Tài liệu | `docs/AWS_DEPLOYMENT.md` đủ 15 mục yêu cầu, ghi rõ chỗ đã/chưa kiểm chứng |

Việc còn phải làm khi deploy thật: xem `docs/AWS_DEPLOYMENT.md` §12 (tạo VPC/RDS/EC2/ALB/DNS, trust store RDS, user DB riêng cho app, SMTP thật, giám sát, đo tải).

## F. Test Results

| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| Biên dịch + đóng gói jar (bản sao build) | **PASS** | `mvnw package -DskipTests`, jar 67 MB |
| `mvnw clean` trong repo | **BLOCKED** | `target/oneshop.jar` bị tiến trình ngoài khóa (không dừng tiến trình đó) |
| Toàn bộ suite Maven (2596 test) trên DB tạo bằng `scripts/db` chế độ `test` | **FAIL (3)** — 2593 PASS | `test-results/full-system-audit/evidence/final-run-fixt4.log` (ignored, còn trên đĩa). 3 FAIL/ERROR đều là `RealProductSeedIntegrityTest` thiếu `database/seed-data/products.json` |
| Hibernate validate + schema verifier khi khởi động `prod` | **PASS** | log `app8097.log`, `/health` 200 |
| Schema mới = schema `oneshop` (480 đối tượng) | **PASS** | `diff` rỗng |
| Fresh init demo / production / test, idempotent, từ chối sai chế độ | **PASS** | mục B |
| Migration V001–V008 idempotent | **PASS** | mục B |
| Migration trên bản sao DB cũ có dữ liệu | **NOT RUN** | không có DB schema cũ |
| Smoke 33 trang × 4 vai trò (guest/customer/staff/admin) với bắt lỗi console + network + overflow, jar `prod` | **PASS** (1 cảnh báo `favicon.ico` 404) | `evidence/smoke-pages-demo/smoke-pages.json` |
| Chrome thật, jar `prod`, DB mới: COD, STORE_PICKUP, ONLINE (override demo), FAILED, REFUND | **PASS** (5/5) | `evidence/be07-demo-prod-profile/*` |
| Chrome thật qua suite Java (7 luồng BrowserV3 + màn hình theo vai trò + UI forms) | **PASS** | trong suite 2596 |
| Quét secret | **PASS** | mục E |
| Triển khai/kết nối RDS, ALB, TLS RDS, Linux/systemd | **NOT RUN / BLOCKED** | không có tài khoản AWS; không deploy theo yêu cầu |
| Hồi quy nghiệp vụ | **PASS** | Không có thay đổi hành vi: sửa đổi trong `src/main` chỉ là cấu hình, chú thích, thông báo lỗi trỏ file, health comment, `PasswordHashTool` |

## G. Final Readiness

**READY WITH WARNINGS.** Không có lỗi blocking nào đã biết trong mã chạy; schema, seed, cấu hình và tài liệu AWS đã sẵn sàng để review.

Cần xử lý / quyết định **trước khi merge `main`:**
1. `RealProductSeedIntegrityTest` (3 test) thất bại vì thiếu `database/seed-data/products.json` (README cũ tham chiếu nhưng chưa từng được commit). `./mvnw package` (không `-DskipTests`) và CI sẽ đỏ cho tới khi cung cấp manifest, hoặc chủ dự án quyết định bỏ/viết lại test. Tôi không tự tạo manifest.
2. Review cleanup: 1.662 file bị xóa khỏi index (chủ yếu evidence sinh ra). Quyết định giữ `docs/report/` (50 MB) trong `main` hay không. Khôi phục bất kỳ file nào bằng `git checkout 9a4322f -- <path>`.
3. Chạy lại suite trên một lần sạch (`clean`) sau khi tiến trình đang khóa `target/oneshop.jar` dừng.
4. Chọn sử dụng `render.yaml`/Docker hiện có hay chuyển hẳn sang AWS.

Cần xử lý **khi deploy AWS:** các mục ⚠ trong `docs/AWS_DEPLOYMENT.md` (TLS tới RDS, `CREATE DATABASE` collation trên RDS, backup/restore sang RDS, kích thước instance); **không** đưa DB `oneshop` hiện tại (có tài khoản demo mật khẩu đã biết) lên site công khai; chọn mật khẩu admin riêng và tạo user DB riêng cho ứng dụng; đặt `SPRING_PROFILES_ACTIVE=prod`; production giữ ONLINE đóng cho tới khi có cổng thanh toán thật.

Lỗi nhỏ có sẵn: `favicon.ico` 404; phần code đa phân công trong `StaffStoreScopeService` không thể kích hoạt (code chết do ràng buộc); test chạy tuần tự ~10 phút.
