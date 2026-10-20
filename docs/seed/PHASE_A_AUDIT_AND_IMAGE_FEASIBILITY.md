# Seed mỹ phẩm thật — Giai đoạn A: Audit và khảo sát khả thi ảnh

Ngày: 20/10/2026 · Trạng thái: **tạm dừng để xin quyết định** (chưa xóa/ghi gì vào `oneshop`, chưa upload/xóa gì trên Cloudinary, chưa sửa repository ngoài tài liệu này).

## 1. Audit repository / database

| Hạng mục | Hiện trạng |
| --- | --- |
| `database/` | `01_schema.sql` (23 bảng, đã gồm `payment_refunds`), `02_seed.sql`, `03_verify.sql`, `README.md`, `migrations/` (4 tệp: 2 BE-06, 2 OPEN-10.1). Tệp OPEN-10.1 **chưa được commit** (untracked) → không được xóa; phải lưu trữ (ví dụ `docs/v3/migration-history/`) trước khi bỏ khỏi `database/`. |
| Tham chiếu tới SQL cũ | `pom.xml` (chú thích), `V3SchemaCompatibilityVerifier` (thông báo trỏ tới tệp migration), `DatabaseV3RegressionIntegrationTest` **đọc** `database/migrations/20261014_be06_review_uniqueness.sql`, `README.md`, `DATABASE_SETUP.md`, `SeedGuardV3`. |
| Seed hiện tại | 22 brand, 8 category, 40 product, 36 store_products, 8 đơn / 13 order_items / 12 fulfillment / 8 payment / 1 refund / 16 inventory_movements / 3 review. Dữ liệu trong `oneshop` **chính là bộ fixture này** (chỉ khác giá trị thời gian). |
| Ràng buộc FK | `order_items → store_products → products`; `cart_items → products`; `reviews → products/order_items`. Vì vậy **không thể thay sản phẩm cũ mà không xử lý 8 đơn/13 dòng hàng/payment/refund/review đang tham chiếu** (xem mục 4 quyết định #2). |
| Phụ thuộc của test | 22/81 tệp test dùng SKU seed (`COCOON-SERUM-30` 56 lần, `COCOON-SHAM-310` 20, `INNI-TONER-200` 12, `SENKA-WHIP-120` 9, `BBIA-CHEEK-06`, `COCOON-LIP-05`, `INNI-CLEANS-120`, `ANESSA-MILK-60`…), id cố định, giá/tổng tiền đơn (447.000, 607.000, 458.000…), số bảng/dòng. Đổi sản phẩm = cập nhật các kỳ vọng số này (không nới lỏng assertion). |
| Thư mục lớn không thuộc Git | `backup/` (603 MB, đã ignore, chứa log Cloudinary 12–13/10), `target/` (3 GB, ignore), `screenshots/` (9 MB, chưa track). |

## 2. Audit ảnh

**Ảnh sản phẩm trong seed cũ không nằm trên Cloudinary.** 40 dòng `product_images` trỏ tới tệp tĩnh trong repo `src/main/resources/static/images/products/*.webp` (1,4 MB tổng cùng ảnh site), kèm `public_id` giả `oneshop/v3/products/*` **không tồn tại** trên Cloudinary. Vì vậy chưa có ảnh sản phẩm Cloudinary nào cần xóa theo seed cũ.

Cloudinary (cloud của dự án, đọc qua Admin API, chỉ đọc): 159 ảnh.

| Nhóm | Số lượng | Xử lý đề xuất |
| --- | --- | --- |
| `oneshop/seed/brands/*` (logo thương hiệu, seed cũ) | 21 | Nhóm 1: chỉ xóa **sau** khi ảnh thay thế hoạt động và được xác nhận; **giữ** logo của thương hiệu vẫn còn trong seed mới |
| `oneshop/seed/categories/*` | 6 | Không thuộc phạm vi xóa (giữ) |
| `oneshop/products/qhjfx0suige5zmllnasd` | 1 | Mồ côi (không bảng nào tham chiếu, tạo 12/10) — ứng viên xóa, cần xác nhận |
| `hotel-booking/*` (63), `samples/*` (53), ảnh ở gốc (15) | 131 | **Không thuộc OneShop — tuyệt đối không đụng** |

Phát hiện lỗi hiện có: DB tham chiếu **3 tài sản Cloudinary không tồn tại** → ảnh hỏng ngay bây giờ: logo `3ce`, ảnh category `cham-soc-da` và `cham-soc-toc`.
Ảnh tĩnh giao diện (`images/One.png`, `images/site/{hero,cta,categories}`) và logo OneShop: giữ nguyên.
`brands.logo_url`, `categories.image_url` đều NULL được và UI có fallback → seed mới không bắt buộc phải có logo/ảnh danh mục.

## 3. Khảo sát nguồn dữ liệu thật và quyền sử dụng ảnh

**Giá, tên, dung tích (dữ kiện) — khả thi.** `medicare.vn` (nhà thuốc/mỹ phẩm Việt Nam) cho xem danh mục theo thương hiệu với tên, dung tích, giá hiện tại, giá gạch ngang và URL từng sản phẩm; truy cập bình thường qua công cụ tra cứu. CeraVe 29, La Roche-Posay 28, Bioderma 24, Cetaphil 27, Neutrogena 9, Vichy 22, Eucerin 29, Garnier 32, Maybelline 80, Nivea 113, Pantene 27, Dove 45, Vaseline 49 sản phẩm. Giá biến động theo khuyến mãi (cùng một SKU có thể thấy 280.000 và 350.000 trên hai bản ghi) → phải ghi nguồn + ngày.
Không dùng được: **Watsons** (HTTP 403 chặn bot), **Guardian** (403); Hasaki render bằng JavaScript (trang thương hiệu trống). Không né các biện pháp chặn này.

**Ảnh — đây là điểm nghẽn.** Hình bao bì trên các website bán lẻ/hãng đều có bản quyền; đồ án học thuật không mặc nhiên được phép. Nguồn ảnh duy nhất tôi xác minh được là **Open Beauty Facts** (cơ sở dữ liệu mở): trang `world.openbeautyfacts.org/terms-of-use` và `/data` ghi *"Products images are available under the Creative Commons Attribution ShareAlike licence"*, kèm lưu ý ảnh *"có thể chứa thành phần đồ họa thuộc bản quyền hoặc quyền khác"* (nhãn hiệu, bao bì). Dùng được **kèm ghi công** (Open Beauty Facts contributors) và **chia sẻ tương tự** đối với ảnh đã chỉnh.

Độ phủ thực đo được (API Open Beauty Facts, 33 thương hiệu, ~1.640 sản phẩm có ảnh mặt trước):

| Nhóm thương hiệu | Kết quả |
| --- | --- |
| **Không có ảnh** (0 sản phẩm): **3CE, Bbia, Anessa, Senka, Romand, Dear Klairs** | Cocoon 1, SKIN1004 2, Innisfree 3, Some By Mi 1 → coi như **không đưa được vào seed với ảnh có giấy phép** |
| Có ảnh: CeraVe 52, La Roche-Posay 93, Bioderma 73, Cetaphil 48, Neutrogena 141, Vichy 48, Eucerin 57, L'Oréal 130, Maybelline 81, Garnier 165, Nivea 154, Dove 153, Pantene 97, Vaseline 105, Simple 33… | Ảnh do người dùng chụp: phần lớn là ảnh cầm tay, nền thảm/kệ, mặt sau hộp — **không phù hợp** hiển thị catalog |
| Ảnh **packshot sạch** (nền trắng, do nhà sản xuất tải lên, đã xem trực quan) | **208/1.640** (≈13%) → ứng viên thực tế; sau khi đối chiếu sản phẩm đang bán tại Việt Nam dự kiến còn **khoảng 60–90 SKU**, không phải 100–150 |

Rủi ro nhận diện: nhiều packshot sạch là **bao bì thị trường châu Âu** (nhãn tiếng Hà Lan/Đức/Pháp, đặc biệt Dove, Nivea, Garnier, Pantene), không đúng 100% bao bì bán tại Việt Nam. CeraVe, La Roche-Posay, Bioderma, Vichy, Simple, L'Oréal/Maybelline (packshot hãng) gần như giống bao bì quốc tế.

## 4. Quyết định cần chủ dự án (trước khi làm tiếp Giai đoạn B/C)

1. **Chính sách ảnh.** (a) *Khuyến nghị*: chỉ dùng ảnh CC BY-SA từ Open Beauty Facts + ghi công trong `docs/seed/IMAGE_CREDITS.md` (và, nếu muốn hiển thị trên website, cần một thay đổi UI nhỏ — thuộc giai đoạn UI/UX sau); chấp nhận ~60–90 SKU, không có Cocoon/3CE/Bbia/Anessa/Senka/Romand/Klairs. (b) Bạn cung cấp/được cấp phép ảnh cho các thương hiệu Việt–Hàn → tôi dùng cho các SKU đó. (c) Chấp nhận rủi ro bản quyền khi dùng ảnh nhà bán lẻ: **tôi không khuyến nghị và sẽ không làm nếu chưa có chỉ thị rõ ràng.**
2. **Cách thay dữ liệu `oneshop`** (Giai đoạn D, chưa làm): (A) *Reset toàn bộ dữ liệu demo*: dựng lại `oneshop` từ `01_schema.sql` + `02_seed.sql` mới → **mất** 8 đơn, 13 dòng hàng, 8 payment, 1 refund, 16 inventory_movements, 3 review + phản hồi, 4 giỏ hàng, 8 checkout session, mọi tài khoản/chi nhánh sẽ được seed lại tương đương; sau backup đã có (`oneshop_pre_open101_20261020.bak`) và backup mới. (B) Giữ lịch sử: giữ các sản phẩm cũ đang được đơn hàng tham chiếu dưới dạng `INACTIVE` và thêm sản phẩm mới → trái yêu cầu "xóa hoàn toàn seed cũ/không trùng". *Khuyến nghị A* vì toàn bộ dữ liệu hiện tại là fixture demo.
3. **Seed vs test.** Tôi dự kiến giữ nguyên cấu trúc fixture (6 Store, 10 tài khoản, 8 đơn mẫu đủ các trạng thái, tồn kho kịch bản A→H) và gán lại cho sản phẩm thật; tổng tiền đơn đổi theo giá thật → cập nhật kỳ vọng số trong test, không nới lỏng assertion.
4. **Cơ sở dữ liệu.** Test ghi dữ liệu chỉ chạy được trên DB tên `*_test` (cơ chế an toàn bắt buộc). "Chỉ một DB lâu dài" nghĩa là: DB tạm sinh ra cho mỗi lần chạy test sẽ được xóa ngay sau đó; **`oneshop_test` giữ nguyên cho tới khi bạn xác nhận riêng**.
5. **Logo thương hiệu.** Không có nguồn logo đã xác minh quyền → đề xuất `logo_url = NULL` cho thương hiệu mới (UI có fallback), giữ logo cũ của thương hiệu còn dùng, xóa phần còn lại sau xác nhận.

Chưa thực hiện: xóa/tạo dữ liệu, upload/xóa Cloudinary, sửa schema/seed/test.
