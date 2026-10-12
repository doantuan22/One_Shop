# Bộ seed lớn cho môi trường phát triển

> **Chỉ dùng cho môi trường phát triển cục bộ.** Bộ này KHÔNG dùng cho bản demo/deploy cuối cùng
> (Roadmap Giai đoạn 13 dùng bộ nhỏ `04_seed_data.sql` với 3–5 chi nhánh). Trước demo công khai: dọn ảnh seed
> trên Cloudinary (`--cleanup`) và dựng lại bằng dữ liệu nền (`rebuild_dev_db.ps1 -BaseDataOnly`).

Mục đích: có dữ liệu đủ lớn để thử phân trang, lọc, nhiều chi nhánh và luồng đơn hàng. `04_seed_data.sql` được giữ
nguyên làm nền; bộ lớn chạy sau nó trên DB dựng mới, dùng ID **từ 1000 trở lên** và không đụng ID dưới 1000
(ngoại lệ có chủ đích: gán ảnh Cloudinary cho 3 danh mục, 3 thương hiệu và 7 sản phẩm cũ của 04).

## Trạng thái hiện tại (đọc trước)

| Hạng mục | Trạng thái |
|---|---|
| Bộ sinh, `04_seed_data.sql` (gộp), `05_verify.sql`, script dựng lại | Xong; đã chạy thật 2 lần liên tiếp trên DB tạm `oneshop_scratch` (kết quả giống nhau, `All large-seed checks passed.`) |
| Nạp vào DB phát triển `oneshop` | **Đã nạp** (bộ lớn + ảnh Cloudinary), `05_verify.sql` qua với kiểm tra ảnh bật; bản sao lưu trước khi nạp ở `backup/` |
| Ảnh (228 khóa) | **Đã tải và xử lý đủ**: 13 logo thật (Wikimedia Commons) + 9 ảnh chữ tự tạo, 45 ảnh stock Pexels, 3 ảnh danh mục cũ; đúng giới hạn kích thước. **Đã upload Cloudinary** (228/228, `image_manifest.csv`) |
| DB test riêng `oneshop_test` | Có; `mvn test` đã chạy trên đó |

## Một bộ SQL duy nhất

Bộ seed cũ (nhỏ) và bộ lớn đã được **gộp thành một bộ database duy nhất** trong `database/`:

| File | Nội dung |
|---|---|
| `01_create_database.sql` … `03_create_indexes.sql` | Tạo database, 19 bảng, index |
| `04_seed_data.sql` | **Toàn bộ dữ liệu**: dữ liệu nền (ID < 1000: 5 chi nhánh, 7 sản phẩm, 5 đơn…) + khối `BEGIN LARGE SEED … END LARGE SEED` (ID ≥ 1000, do `LargeSeedGenerator` sinh, kèm URL ảnh Cloudinary) |
| `05_verify.sql` | Kiểm tra: phần 1 (schema, dữ liệu nền) + phần 2 (số lượng, quy tắc 1–9, ảnh Cloudinary) |
| `00_run_all.sql` | Chạy 01 → 05 (SQLCMD mode) |
| `rebuild_dev_db.ps1` | Dựng lại DB cục bộ có sao lưu và kiểm tra đích |

Không còn `seed_large/01..04.sql`, `00_run_small.sql`, file cài đặt riêng. Thư mục `database/seed_large/` chỉ còn
`images/` (kế hoạch, nguồn, manifest ảnh) và `SEED_STATS.md`.

## Dựng lại DB phát triển

Tắt ứng dụng trước. Script chỉ chạy trên máy cục bộ (`localhost`, `127.0.0.1`, `(local)`, `.`, `.\instance`) và chỉ khi
biến `REBUILD_CONFIRM` bằng đúng tên DB.

```powershell
$env:REBUILD_CONFIRM = "oneshop"
powershell -ExecutionPolicy Bypass -File databaseebuild_dev_db.ps1
#    chỉ xem đích, không đụng gì: thêm -CheckOnly
#    xóa asset Cloudinary cũ theo đúng danh sách đã xuất: thêm -DeleteOldCloudinaryAssets (mặc định TẮT, chỉ in số lượng)
```

Trình tự script: **S1** kiểm tra đích và in máy chủ/tên DB → **S2** `BACKUP DATABASE ... TO DISK` vào `backup/`
(+ `RESTORE VERIFYONLY`; thất bại thì dừng, không xóa) → **S3** xuất mọi `public_id` khác NULL
(`product_images`, `brands`, `categories`) ra `backup/old_cloudinary_public_ids.txt` → **S4**
`SET SINGLE_USER WITH ROLLBACK IMMEDIATE` + `DROP DATABASE` (lỗi thì trả lại `MULTI_USER`) → `01` … `04` → `05_verify.sql`.
`02_create_tables.sql` đã gồm `user_name`, `reviews.order_id/reply`, ảnh danh mục nên không cần chạy migration.
Thư mục `backup/` nằm trong `.gitignore`.

Chạy trên máy khác (một lệnh, từ thư mục `database/`; thay `-E` bằng `-U <user> -P <pass>` nếu dùng SQL login):

```
sqlcmd -S localhost -E -C -f 65001 -b -I -i 00_run_all.sql
```

Máy đích cần SQL Server có collation `Vietnamese_100_CI_AI`, `sqlcmd` và Internet (ảnh là URL Cloudinary công khai).
Chạy sẽ **xóa và tạo lại** database `oneshop`.

### Sinh lại khối seed lớn

```powershell
java src/test/java/com/oneshop/seed/LargeSeedGenerator.java     # thay khối giữa hai marker trong 04_seed_data.sql
```
Bộ sinh xác định (hạt giống cố định), tự kiểm tra quy tắc 1–9 trước khi ghi, và chạy lại không làm đổi file.
Thời gian dùng `DATEADD(MINUTE, -n, SYSDATETIME())` nên dữ liệu luôn nằm trong 120 ngày gần nhất tính theo ngày nạp.
Sau khi nạp, identity được reseed theo `MAX(id)`.

### Sao lưu và khôi phục

```
sqlcmd -S localhost -E -C -Q "ALTER DATABASE [oneshop] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; RESTORE DATABASE [oneshop] FROM DISK = N'D:\One_Shopackup\oneshop_<yyyyMMdd_HHmmss>.bak' WITH REPLACE; ALTER DATABASE [oneshop] SET MULTI_USER;"
```

## DB riêng cho test (Phần T)

Các test `*DatabaseIntegrationTest`, `*HttpDatabaseIntegrationTest`, `Phase9/10/11/12*` kết nối SQL Server thật bằng
profile `dev` (đọc `.env`) và kiểm tra giá trị phụ thuộc bộ nhỏ (ID, email, giá, tồn kho, số đơn). Vì DB phát triển là bộ
lớn, các test này chạy trên DB riêng **`oneshop_test`** (bộ nhỏ 01–04): `pom.xml` đặt `DB_NAME=${oneshop.test.db}`
(mặc định `oneshop_test`) cho JVM của surefire, ghi đè `DB_NAME` trong `.env`.

```powershell
$env:REBUILD_CONFIRM = "oneshop_test"
powershell -ExecutionPolicy Bypass -File database\rebuild_dev_db.ps1 -Database oneshop_test -BaseDataOnly
mvn test                                  # chạy trên oneshop_test
mvn test -Doneshop.test.db=khac           # DB test khác
```

| Test | Phụ thuộc gì | Cách xử lý |
|---|---|---|
| `DatabaseFoundationIntegrationTest`, `AuthDatabaseIntegrationTest`, `CartDatabaseIntegrationTest`, `CartHttpDatabaseIntegrationTest`, `CatalogDatabaseIntegrationTest`, `CatalogHttpDatabaseIntegrationTest`, `CheckoutDatabaseIntegrationTest`, `CheckoutHttpDatabaseIntegrationTest`, `CheckoutConcurrencyDatabaseIntegrationTest` | SQL Server thật (`@ActiveProfiles("dev")`); ID/email/giá/tồn của 04 (khachhang1/2, staff.*, Serum #3, store 1–5…) | Chạy trên `oneshop_test` (bộ nhỏ). Assertion giữ nguyên |
| `DeliveryDatabaseIntegrationTest`, `PaymentDatabaseIntegrationTest`, `PickupDatabaseIntegrationTest`, `OrderStateDatabaseIntegrationTest`, `ReviewDatabaseIntegrationTest` | SQL Server thật; đơn #1–#5, tồn, trạng thái chuyển | Như trên |
| `StaffOperations*`, `StaffFulfillmentOperations*`, `StaffInventory*`, `StaffStoreScope*DatabaseIntegrationTest` | SQL Server thật; phân công staff–chi nhánh của 04, số đơn mỗi chi nhánh, số trang | Như trên |
| `Phase9IntegrationWebDatabaseTest`, `Phase10IntegrationWebDatabaseTest`, `Phase11AdminDatabaseIntegrationTest`, `Phase12HistoryHardeningDatabaseIntegrationTest` | SQL Server thật; luồng đầu-cuối trên dữ liệu 04, số lượng bảng | Như trên |
| `Phase12CloudinaryLiveVerification` | Cloudinary thật (`CLOUDINARY_LIVE_TEST=true`) | Không đổi; bỏ qua mặc định |
| `*LiveSmoke` (Delivery/Payment/Pickup/Phase9) | `main` thủ công, đọc `DB_NAME` từ `.env`/môi trường | Không đổi; trỏ vào DB tùy ý bằng `DB_NAME` |
| `*WebIntegrationTest`, `LayoutsIntegrationTest`, `AccessControl*`, `service.*Test`, `JwtServiceTest`… | Không dùng DB (mock) | Không đổi |
| `SeedCategoryImagesIntegrityTest` | Tệp SQL + ảnh tĩnh trong repo | Giữ ý nghĩa cho 04; thêm kiểm tra bộ lớn không trỏ ảnh tĩnh và nguồn tĩnh vẫn tồn tại |

Không có test nào bị xóa hoặc nới lỏng assertion. `05_verify.sql` (kiểm tra bộ nhỏ) chạy trên `oneshop_test`;
phần 2 của cùng file kiểm tra bộ lớn và được bỏ qua với DB test.

## Tài khoản thử nghiệm

Mật khẩu chung của mọi tài khoản (cùng chuỗi băm với 04): **`OneShop@123`** — chỉ dùng cho môi trường phát triển.

| Email | Vai trò | Chi nhánh |
|---|---|---|
| `admin@oneshop.vn`, `admin.phu@seed.oneshop.test` | ADMIN | toàn chuỗi |
| `staff.thuduc@` / `staff.govap@` / `staff.quan7@` / `staff.quan10@oneshop.vn` (04) | STAFF | OS-THUDUC / OS-GOVAP / OS-QUAN7 / OS-QUAN10 |
| `staff.nguyen.thi.hanh@`, `staff.tran.van.phuc@seed.oneshop.test` | STAFF | OS-CAUGIAY (Hà Nội) |
| `staff.le.thi.oanh@`, `staff.pham.quang.vinh@seed.oneshop.test` | STAFF | OS-DONGDA (Hà Nội, chỉ nhận tại cửa hàng) |
| `staff.hoang.thi.lua@`, `staff.dang.van.son@seed.oneshop.test` | STAFF | OS-SONTRA (Đà Nẵng) |
| `staff.vu.thi.thanh@seed.oneshop.test` | STAFF | OS-THUDUC **và** OS-GOVAP |
| `staff.bui.minh.dat@seed.oneshop.test` | STAFF | OS-QUAN7 **và** OS-CAUGIAY |
| `staff.ngo.thi.hoa@seed.oneshop.test` | STAFF | OS-QUAN10 |
| `staff.duong.van.lam@seed.oneshop.test` | STAFF | OS-HAICHAU (chi nhánh INACTIVE) |
| `staff.ly.thi.kieu@seed.oneshop.test` | STAFF | OS-NINHKIEU (chi nhánh INACTIVE) |
| `staff.phan.van.tri@seed.oneshop.test` | STAFF | phân công OS-QUAN7 đã **thu hồi** (INACTIVE) |
| `staff.vo.thi.nhan@seed.oneshop.test` | STAFF | **chưa phân công** |
| `kh01.thi-mai@` … `kh31.lan-huong@seed.oneshop.test` (`seed_kh01`…`seed_kh31`) | CUSTOMER | — (`kh01` có 4 địa chỉ) |
| `kh31.lan-huong@seed.oneshop.test` | CUSTOMER | tài khoản **bị khóa** (INACTIVE) |
| `khachhang1@example.com`, `khachhang2@example.com` (04) | CUSTOMER | — |

Chi nhánh: 5 của 04 (4 ACTIVE + Hải Châu INACTIVE) + 4 mới: OS-CAUGIAY, OS-DONGDA (không giao hàng), OS-SONTRA,
OS-NINHKIEU (INACTIVE, không nhận tại cửa hàng).

## Số liệu thực tế (sinh ra, có kiểm tra)

Xem `database/seed_large/SEED_STATS.md` (tự sinh). Tóm tắt so với yêu cầu tối thiểu:

| Hạng mục | Yêu cầu | Thực tế (gồm cả 04) |
|---|---|---|
| Thương hiệu | ≥ 20 | 22 |
| Danh mục | 8 | 8 |
| Sản phẩm | ≥ 87 | 99 (6 không ACTIVE, 3 trong số đó có lịch sử đơn) |
| Chi nhánh | 9, ≥ 2 INACTIVE | 9, 2 INACTIVE |
| StoreProduct | ≥ 350 | 479 |
| Khách / nhân viên | ≥ 30 / ≥ 12 | 34 / 17 (+ 2 admin) |
| Địa chỉ | ≥ 40 | 50 |
| Giỏ nhiều chi nhánh | ≥ 8 | 10 giỏ của bộ lớn (12 giỏ gồm 04) |
| Checkout sessions / orders | ≥ 90 / ≥ 150 | 99 / 165 (49 session có 2–3 đơn; ở bộ lớn mỗi session nhiều đơn đều khác chi nhánh và khác fulfillment/payment) |
| Phân bố trạng thái | COMPLETED ~55%, CANCELLED ~12%, PENDING ~5%, mỗi trạng thái còn lại ≥ 8 đơn ở ≥ 3 chi nhánh | COMPLETED 89 (54%), CANCELLED 20 (12%), PENDING_PAYMENT 8 (5%), CONFIRMED 9, PREPARING 10, PACKED 9, SHIPPING 10, READY_FOR_PICKUP 10 (mỗi trạng thái ở 3–5 chi nhánh) |
| Đơn nhiều nhất ở một chi nhánh | ≥ 60 | OS-THUDUC 65 |
| Khoảng thời gian | 120 ngày | đơn cũ nhất 119 ngày trước ngày nạp |
| Đánh giá | ≥ 50, ≥ 6 ẩn, ≥ 8 có phản hồi | 62 (7 ẩn, 10 có phản hồi) |
| STOCK_ADJUST | ≥ 30 | 50 (49 của bộ lớn) |
| Tồn kho (bộ lớn) | ~10% = 0, ~15% từ 1–5 | 17% = 0 (gồm sản phẩm ngừng bán), 15.5% từ 1–5 |
| Hết hàng ở mọi chi nhánh | ≥ 3 | 9 (3 có chủ đích + các sản phẩm ngừng bán) |
| Sản phẩm chỉ ở 1 chi nhánh | ≥ 5 | 6 |

## Quy ước

- SKU: `<THƯƠNG-HIỆU>-<DÒNG>-<BIẾN-THỂ/DUNG-TÍCH>` in hoa, ≤ 50 ký tự (vd. `ROMAND-JLT-03`, `CETA-CLEANSER-250`).
  Mỗi màu/cỡ là một Product riêng.
- Giá giữa các chi nhánh chênh 0–8%, làm tròn 1.000đ. Tồn đầu tính ngược từ tồn cuối và chuỗi movement.
- `checkout_sessions.status`: tất cả đơn hủy → `CANCELLED`; tất cả đơn xong/hủy → `COMPLETED`; một phần → `PARTIAL`;
  chưa đơn nào xong → `CREATED`.
- Đơn hủy chỉ đi từ `PENDING_PAYMENT` (ONLINE: thanh toán thất bại hoặc khách hủy) hoặc `CONFIRMED` (COD/PAY_AT_STORE,
  khách hủy), đúng `OrderTransitionPolicy`. COD/PAY_AT_STORE chỉ `PAID` khi `COMPLETED`.
- Mã nhận hàng 8 ký tự (bảng chữ của `PickupCodeService`), duy nhất trong từng chi nhánh.

## Ca biên gài có chủ đích

| Ca biên | Dữ liệu | Test case |
|---|---|---|
| Tên sản phẩm rất dài (> 200 ký tự) | `NEU-SET-LONGNAME-2026` | TC-01 (hiển thị/cắt chuỗi) |
| Tên có `&` và dấu nháy | `NIVEA-SUN-MOIST-50`, các sản phẩm `L'Oréal …`, `Victoria's Secret …`, `Bath & Body Works …` | TC-02 (tìm kiếm, escape) |
| Mô tả có thẻ HTML dạng chữ | `SENKA-UV-ESSENCE-80` (`<b>`, `<script>`) | TC-01/TC-03 (kiểm tra escape) |
| Hết hàng ở mọi chi nhánh | `GARNIER-MASK-GREENTEA-28`, `NIVEA-LIPBALM-SOFTROSE-4`, `BBW-MIST-AQUA-236` | TC-01, TC-07 |
| Sản phẩm chỉ ở 1 chi nhánh | `S1004-AIRFIT-50`, `3CE-MULTIEYE-PALETTE`, `3CE-BRUSH-BLUSH`, `VS-MIST-LOVESPELL-250`, `DOVE-BODYWASH-REFILL-900` | TC-02, TC-03 |
| Sản phẩm INACTIVE/HIDDEN có lịch sử đơn | `CETA-CLEANSER-125`, `VS-MIST-PURESEDUCTION-250` (INACTIVE), `ROMAND-ZVT-02` (HIDDEN) | TC-18 (lịch sử vẫn xem được), TC-16 |
| INACTIVE/HIDDEN chưa có lịch sử | `PANTENE-COND-REPAIR-300`, `SENKA-MASK-AQUA-3` | TC-01 |
| Chi nhánh INACTIVE có đơn lịch sử | OS-HAICHAU (04 + bộ lớn), OS-NINHKIEU | TC-18 |
| Đơn đổi giá sau khi mua | 29 dòng đơn có `unit_price` khác giá hiện tại | TC-14 |
| Checkout 2–3 đơn, khác chi nhánh & payment | 48 session bộ lớn | TC-06, TC-15 |
| Giỏ từ 2–3 chi nhánh | 10 giỏ (khách `kh13`…`kh22`) | TC-05 |
| ONLINE FAILED → hủy → hoàn tồn | 9 đơn (8 bộ lớn + 1 của 04; `payments.FAILED`, `CANCEL_ORDER`) | TC-12 |
| Điều chỉnh tồn bởi đúng nhân viên chi nhánh | 49 `STOCK_ADJUST` có ghi chú | TC-13, TC-11 |
| Nhân viên 2 chi nhánh / phân công thu hồi / chưa phân công | xem bảng tài khoản | TC-11 |
| Phản hồi và đánh giá bị ẩn | 10 reply, 7 HIDDEN | Review (Phase 12) |

## Ảnh (Cloudinary)

Quy ước `public_id`: `oneshop/seed/products/<sku-slug>-<n>`, `oneshop/seed/brands/<slug>`,
`oneshop/seed/categories/<slug>` (thư mục gốc `oneshop` của ứng dụng + cấp `seed`). Mỗi sản phẩm/danh mục/thương hiệu
một asset riêng (cùng tệp nguồn có thể tải nhiều lần với `public_id` khác nhau); `overwrite=true`, `invalidate=true`.
Kế hoạch: `database/seed_large/images/image_plan.csv` (228 khóa); nguồn: `image_sources.csv`; kết quả upload:
`image_manifest.csv` (SQL seed đọc từ đây). Ảnh gốc ở thư mục tạm ngoài repo, **không commit**.

```powershell
$W = "$env:TEMP\oneshop-seed-images"
java src/test/java/com/oneshop/seed/SeedImages.java --fetch-logos --work $W   # logo (Wikimedia Commons) + ảnh chữ
java src/test/java/com/oneshop/seed/SeedImages.java --process     --work $W   # <=1200px, <=300KB (logo <=600px, <=150KB), kiểm tra chữ ký tệp
java src/test/java/com/oneshop/seed/SeedImages.java --missing     --work $W   # liệt kê ảnh stock còn thiếu
java src/test/java/com/oneshop/seed/SeedImages.java --upload --dry-run --work $W
$env:CLOUDINARY_SEED_UPLOAD = "true"                                           # cùng CLOUDINARY_* trong .env
java src/test/java/com/oneshop/seed/SeedImages.java --upload --work $W         # ghi image_manifest.csv
java src/test/java/com/oneshop/seed/SeedImages.java --cleanup --dry-run        # xóa asset theo prefix oneshop/seed/
java src/test/java/com/oneshop/seed/SeedImages.java --delete-ids backup\old_cloudinary_public_ids.txt --dry-run
```

Quy tắc chữ ký tệp (JPEG/PNG/WebP, từ chối SVG) trùng `CloudinaryServiceImpl.validateFileSignature`
(`SeedImageSignatureParityTest` kiểm tra tương đương). Công cụ không bao giờ sinh URL giả hoặc hotlink; khóa chưa
có ảnh được báo là thiếu. Không tự upload nếu thiếu `CLOUDINARY_SEED_UPLOAD=true`.

### Ảnh stock

45 ảnh stock (40 minh họa theo loại sản phẩm + 5 danh mục mới) lấy từ Pexels qua CDN theo ID ảnh (trang web Unsplash/Pexels chặn
tải tự động, Pixabay cần khóa API); nguồn, tác giả và giấy phép từng ảnh ở `image_sources.csv` và `docs/SEED_IMAGE_CREDITS.md`.
Thay ảnh: bỏ `<khóa>.jpg` mới vào thư mục `stock`, cập nhật dòng `stock:<khóa>` trong `image_sources.csv`, chạy lại `--process`
và `--upload`.

### Cảnh báo

- Ảnh sản phẩm là ảnh **minh họa chung theo loại** sản phẩm, không phải ảnh thật của đúng mặt hàng; nhiều sản phẩm dùng chung tệp nguồn (mỗi sản phẩm vẫn một asset Cloudinary riêng).
- Logo thương hiệu thật chỉ dùng cho **thử nghiệm nội bộ** (`use=internal-test-only`); Cetaphil (CC BY-SA 4.0) và Dove
  (CC BY-SA 3.0) cần ghi công nếu phát hành. 9 thương hiệu không tìm được nguồn tin cậy dùng ảnh chữ tự tạo.
- URL Cloudinary là **công khai**: ai biết URL đều xem được.

## Dọn dữ liệu và ảnh

- Dữ liệu bộ lớn: dựng lại DB bằng `rebuild_dev_db.ps1 -BaseDataOnly` (chỉ giữ dữ liệu nền).
- Ảnh seed trên Cloudinary: `SeedImages.java --cleanup` (chỉ prefix `oneshop/seed/`).
- Asset cũ trước khi dựng lại: `backup/old_cloudinary_public_ids.txt` + `-DeleteOldCloudinaryAssets` (chỉ xóa đúng danh sách).
- Tệp ảnh tĩnh `static/images/site/categories/category-{skincare,makeup,haircare}.webp` **không còn được bộ lớn dùng**
  (đã chuyển lên Cloudinary) nhưng phần dữ liệu nền của `04_seed_data.sql` vẫn tham chiếu nên giữ trong repo.

## Kiểm thử

- `LargeSeedGeneratorTest`: cùng đầu vào → cùng đầu ra; quy tắc 1–9 và số lượng tối thiểu; bắt được lỗi cố ý (tồn
  kho âm, tổng đơn lệch, lịch sử sai chuỗi, mã nhận hàng trùng, đơn hủy không hoàn kho, đánh giá không có đơn, thời
  điểm tương lai).
- `RebuildScriptGuardTest`: script từ chối máy chủ không cục bộ, thiếu/sai `REBUILD_CONFIRM`, DB hệ thống.
- `SeedImageSignatureParityTest`: quy tắc chữ ký tệp trùng `CloudinaryServiceImpl`.
- `LargeSeedWebIntegrationTest` (`@Tag("seed-large")`, chỉ chạy khi `SEED_LARGE_TEST=true`, DB `-Dseed.large.db`, mặc
  định `oneshop`): duyệt `/products` nhiều trang, `/staff/orders` của nhân viên Thủ Đức nhiều hơn một trang, mọi ảnh seed
  là URL https Cloudinary.

Tên sản phẩm/dòng sản phẩm là dữ liệu thử nghiệm theo các dòng phổ biến, không bảo đảm khớp danh mục chính thức của
từng hãng.
