# Báo cáo Phase D — Dọn repo và seed lại dữ liệu sản phẩm thật

Ngày: 20/10/2026 · Nhánh: `tuntun` · Trạng thái: **đã xong Phase A–C, DỪNG Ở PHASE D chờ xác nhận.**
CSDL `oneshop` **chưa bị thay đổi** và **chưa xóa ảnh Cloudinary nào** (chỉ tải thêm 27 ảnh mới).

> Viết cho: chủ dự án OneShop, người quyết định có chạy Phase E (reset `oneshop`, xóa ảnh Cloudinary cũ) hay không.

## 1. Kết luận ngắn

| Mục | Kết quả |
| --- | --- |
| Số sản phẩm thật | **27** (yêu cầu ban đầu 100–150; sau quyết định (a) chấp nhận 60–90 nếu xác minh đủ) — **thấp hơn cả hai mức**, lý do ở mục 3 |
| Độ xác thực | 27/27 có tên, thương hiệu, dung tích/màu, giá tham khảo thật, URL nguồn, ngày xác minh 20/10/2026; 27/27 có ảnh thật kèm tác giả + giấy phép |
| Khớp ảnh–sản phẩm | 5 sản phẩm hạng **A** (mã vạch EAN trong URL nhà bán khớp mã vạch ảnh), 22 hạng **B** (tên + dung tích/màu trên bao bì khớp nhà bán) |
| Dữ liệu mô phỏng | Kho từng cửa hàng, đơn hàng, thanh toán Mock, hoàn tiền, phiếu kho, 3 đánh giá demo — đều đánh dấu mô phỏng; **không có đánh giá khách hàng bịa** |
| `database/` | Chỉ còn `01_schema.sql` (23 bảng gồm `payment_refunds`) và `02_seed.sql` |
| Kiểm thử | Xem mục 7 |
| Cần bạn quyết | Mục 10 |

## 2. Việc đã làm

- **Hợp nhất schema**: `01_schema.sql` đã gộp migration BE-06 (DEC-26, chỉ mục duy nhất đánh giá) và OPEN-10.1 (`payment_refunds`); cuối script có khối tự kiểm tra cấu trúc (23 bảng, khóa, ràng buộc, chỉ mục lọc, ràng buộc trusted).
- **Seed mới** (`02_seed.sql`, sinh từ `products.json`): 27 sản phẩm, 8 thương hiệu (logo để trống, hiển thị tên chữ), 4 danh mục, 27 ảnh, 47 dòng tồn kho, 8 đơn hàng/13 dòng hàng/8 thanh toán Mock/1 hoàn tiền một phần + sổ cái, 16 phiếu kho, 3 đánh giá + 3 phản hồi demo (`[Dữ liệu mô phỏng]`). Có khối kiểm tra ngay trong transaction; lỗi thì rollback cả seed.
- **Trang ghi công ảnh** công khai `GET /image-credits` (nguồn, tác giả, giấy phép, thay đổi đã làm, từng sản phẩm), liên kết ở footer và dòng ghi công trên trang chi tiết sản phẩm. Dữ liệu ở `src/main/resources/data/image-credits.json`.
- **Tài liệu cũ**: migration cũ, `03_verify.sql` và README cũ chuyển sang `docs/v3/migration-history/`; README, `.env.example`, báo cáo V3 cập nhật đường dẫn/ghi chú.
- **Ảnh tĩnh cũ**: đã kiểm tra không còn tham chiếu rồi xóa 40 tệp `src/main/resources/static/images/products/`. Không đụng logo OneShop, banner, icon, ảnh UI gắn cứng.
- **Cloudinary**: tải 27 ảnh gốc mới vào `oneshop/v3/products/<sku>` (chỉ thêm, không xóa/ghi đè); URL hiển thị dùng `c_pad,w_800,h_800,b_white,f_auto,q_auto` để ảnh hộp cao không bị cắt, **không sửa nội dung sản phẩm trong ảnh và không đổi CSS**.
- 3 tham chiếu Cloudinary hỏng trong seed cũ không còn tồn tại vì seed cũ đã bị thay hoàn toàn.

## 3. Vì sao chỉ 27 sản phẩm

1. **Ảnh là nút thắt.** Chính sách (a) chỉ cho phép ảnh có giấy phép dùng được. Nguồn duy nhất đạt điều kiện là Open Beauty Facts (CC BY-SA 3.0); nó chủ yếu phủ các thương hiệu drugstore toàn cầu (CeraVe, Cetaphil, Bioderma, La Roche-Posay, Simple, Maybelline, Vaseline, Nivea). Các nhãn nội địa/Hàn (Cocoon, Innisfree, 3CE, Bbia, Anessa…) hầu như không có ảnh hợp lệ nên không đưa vào.
2. **Quy tắc khớp đúng bao bì.** Tôi loại ảnh khác phiên bản bao bì, khác dung tích hoặc khác tông màu dù cùng dòng sản phẩm. Phần lớn ứng viên "gần giống" bị loại theo quy tắc này.
3. **Giá phải có chứng cứ.** Hasaki/Cocolux hiển thị giá bằng JavaScript nên chỉ dùng được để xác nhận tên/màu/dung tích/EAN; Watsons/Guardian một số trang trả 403 cho truy cập tự động (không dùng cách vượt chặn). Giá lấy từ Medicare, Watsons, Guardian, Pharmacity, Long Châu… khi hiển thị được; ghi rõ từng URL trong [PRODUCT_SOURCES.md](PRODUCT_SOURCES.md).
4. Giá là **giá niêm yết/tham khảo** (không dùng giá khuyến mãi biến động); `original_price` để trống.

Tôi không bịa sản phẩm, ảnh hay giá để đủ số lượng. Có thể mở rộng nếu bạn chọn phương án ở mục 10 (quyết định 3).

## 4. Nguồn, giá, ảnh

- Bảng đầy đủ 27 SKU, giá, hạng khớp ảnh, URL nguồn và ngày xác minh: [PRODUCT_SOURCES.md](PRODUCT_SOURCES.md); dữ liệu máy đọc được: [products.json](products.json) (có test đối chiếu với DB).
- Tác giả, trang gốc, giấy phép từng ảnh: [IMAGE_CREDITS.md](IMAGE_CREDITS.md) và trang `/image-credits`.
- Lưu ý quyền: Open Beauty Facts cảnh báo ảnh có thể chứa nhãn hiệu/thiết kế của bên thứ ba; nhãn hiệu thuộc chủ sở hữu, OneShop là dự án học thuật. Nếu chủ quyền yêu cầu, gỡ ảnh theo hướng dẫn trên trang ghi công.
- Chất lượng ảnh: có ảnh độ phân giải thấp (ví dụ `CERAVE-FOAM-88` rộng khoảng 191 px) — vẫn đúng sản phẩm nhưng sẽ mờ khi phóng to. Tài khoản `thaialagata` là người đóng góp nhiều ảnh nhất.
- Lưu ý giao diện (ngoài phạm vi, để vòng UI/UX): thẻ sản phẩm dùng `object-fit: cover`; ảnh đã được pad trắng ở phía Cloudinary nên không bị cắt.

## 5. Dữ liệu đa dạng cho các màn hình

Thương hiệu: CeraVe, Cetaphil, Bioderma, La Roche-Posay, Simple, Maybelline, Vaseline, Nivea · 4 danh mục · giá từ 71.000đ đến 765.000đ · còn hàng, sắp hết (1–5), hết hàng toàn chuỗi, 1 sản phẩm ngừng bán (ẩn khỏi danh mục, trang chi tiết trả 404), 1 sản phẩm chưa bán ở cửa hàng nào, tồn kho ở nhiều cửa hàng · tài khoản và dữ liệu cho Customer/Staff/Admin được giữ cùng dạng để các bài test vai trò không đổi ý nghĩa.

Chỉ có **4 danh mục**; ba ảnh danh mục cũ (chống nắng, nước hoa, mặt nạ) không còn được tham chiếu — xem quyết định 5.

## 6. Tệp đã xóa / thay đổi lớn

- Xóa: `database/03_verify.sql`, `database/README.md`, `database/migrations/*` (4 tệp, đã lưu ở `docs/v3/migration-history/`), 40 ảnh `static/images/products/`.
- Mới: `docs/seed/*`, `docs/seed/evidence/*` (script HTTP/trình duyệt đã dùng để kiểm thử và 3 ảnh chụp màn hình), `docs/v3/migration-history/*`, `ImageCreditsService`, `ImageCreditsController`, `credits/image-credits.html`, `image-credits.json`, 2 lớp test mới (`RealProductSeedIntegrityTest`, `ImageCreditsPageIntegrationTest`).
- Test cũ phụ thuộc seed được cập nhật hằng số/SKU (ví dụ giá, tên, số tiền hoàn 388.000đ) **mà không làm yếu assertion**.

## 7. Kết quả kiểm thử

Tất cả chạy trên CSDL tạm dựng từ `database/01_schema.sql` + `02_seed.sql` (`oneshop*_test`, có `SeedGuardV3`), không chạm `oneshop` hay `oneshop_test`.

**Toàn bộ bộ test (`./mvnw test -Doneshop.test.db=<DB tạm>`): 1.660 test, 0 lỗi, 0 fail, 0 bỏ qua — BUILD SUCCESS**, chạy lại sau khi seed/ghi công ảnh đã chốt, trên CSDL dựng mới từ hai script cuối. Gồm 5 test đối chiếu seed với bằng chứng (`RealProductSeedIntegrityTest`) và 2 test trang ghi công (`ImageCreditsPageIntegrationTest`).

Kiểm thử HTTP/trình duyệt trên ứng dụng chạy thật (cổng 8094, worker tắt), script nằm ở [evidence/](evidence/):

| Script | Nội dung | Kết quả |
| --- | --- | --- |
| `http_smoke_real_seed.mjs` | danh mục, phân trang, 26 trang sản phẩm hiện + 1 ngừng bán 404, ảnh Cloudinary trả 200 `image/*`, tìm kiếm/lọc, trang ghi công, màn hình 3 vai trò, đơn 7 có sổ cái hoàn tiền | 27/27 |
| `browser_catalog_images.mjs` | Chrome headless: ảnh danh mục tải thật trên desktop và điện thoại | 10/10 |
| `http_e2e_open101.mjs` | luồng OPEN-10.1 end-to-end trên dữ liệu mới | 50/50 |
| `browser_e2e_open10.mjs` | luồng trình duyệt cho Admin/Staff/Customer | 14/14 |

Ảnh chụp: [catalog](evidence/screenshots/seed-catalog-desktop.png), [chi tiết sản phẩm](evidence/screenshots/seed-detail-sensibio-desktop.png), [ghi công ảnh](evidence/screenshots/seed-credits-desktop.png).

## 8. Trạng thái CSDL và dữ liệu sẽ mất khi reset (Phase E)

- `oneshop`: đã migration OPEN-10.1 (23 bảng), **chưa động tới trong Phase B–C**.
- `oneshop_test`: chưa migrate, không đụng.
- Bản sao lưu đã xác minh: `oneshop_pre_open101_20261020.bak` (trước migration) và `oneshop_pre_real_seed_reset_20261020.bak` (trạng thái hiện tại của `oneshop`).

Dữ liệu trong `oneshop` sẽ **mất** nếu reset: 8 đơn hàng, 13 dòng hàng, 12 fulfillment, 33 dòng lịch sử fulfillment, 23 dòng lịch sử trạng thái đơn, 8 thanh toán (gồm 1 hoàn tiền + 1 dòng sổ cái), 16 phiếu kho, 3 đánh giá + 3 phản hồi, 2 giỏ/4 dòng giỏ, 8 phiên checkout, 36 `store_products`, 40 sản phẩm, 40 ảnh sản phẩm, 22 thương hiệu, 8 danh mục. Người dùng, cửa hàng, nhân viên, địa chỉ được seed lại giống hệt (cùng email, mật khẩu demo).

## 9. Quy trình reset (chỉ chạy sau khi bạn duyệt) và kế hoạch phục hồi

1. Dừng ứng dụng và worker; xác nhận không còn kết nối ghi.
2. Sao lưu mới `oneshop` (tên có ngày giờ, không ghi đè bản cũ) và `RESTORE VERIFYONLY`.
3. Xóa đúng tên `oneshop` (không dùng wildcard) — chỉ khi bạn đã cho phép bằng văn bản.
4. Chạy `01_schema.sql` rồi `02_seed.sql` với `-v DatabaseName=oneshop`; script tự kiểm tra, lỗi thì dừng.
5. Kiểm tra đếm bảng/dữ liệu, bật ứng dụng với worker tắt, chạy smoke test, rồi mới bật worker.
6. **Phục hồi**: không tự động rollback. Nếu cần, khôi phục `oneshop_pre_real_seed_reset_20261020.bak` (hoặc bản sao lưu mới ở bước 2) vào tên CSDL mới để so sánh, rồi bạn quyết định thay thế.

Thủ tục này đã được diễn tập trên CSDL tạm `oneshop_reset_rehearsal_20261020_test`.

**Cloudinary** (chỉ xóa sau khi duyệt): 22 ứng viên — 21 logo thương hiệu cũ và 1 ảnh mồ côi `oneshop/products/qhjfx0suige5zmllnasd`; 131 ảnh của dự án khác và 33 ảnh cần giữ được đánh dấu KHÔNG ĐỘNG TỚI. Danh sách chi tiết: [CLOUDINARY_CLEANUP_MANIFEST.md](CLOUDINARY_CLEANUP_MANIFEST.md).

## 10. Cần bạn quyết định

1. **Có reset CSDL `oneshop` (Phase E) không?** Mất dữ liệu liệt kê ở mục 8; đã có sao lưu.
2. **Có xóa 22 ảnh Cloudinary cũ không?** (đồng ý nguyên danh sách hoặc loại bớt mục).
3. **Số lượng sản phẩm:** chấp nhận 27 sản phẩm đã xác minh chặt, hay muốn mở rộng bằng (a) cho phép thêm ứng viên "gần khớp" với giá đã xác minh (tôi sẽ liệt kê riêng để bạn duyệt từng món), (b) bạn cung cấp/cấp phép thêm ảnh thương hiệu, hoặc (c) hiển thị sản phẩm không ảnh với placeholder trung tính — tôi khuyến nghị **không** chọn (c).
4. Có chấp nhận cảnh báo quyền ảnh bên thứ ba của Open Beauty Facts (đã nêu công khai ở `/image-credits`) không?
5. Ba ảnh danh mục cũ (chống nắng, nước hoa, mặt nạ) trên Cloudinary hiện không còn được tham chiếu — giữ hay đưa vào danh sách xóa?
6. Hai ghi chú UI/UX chuyển sang vòng sau, không thuộc phạm vi này: thẻ `object-fit: cover`; lịch sử khách hàng hiển thị tên Admin / `[ADMIN_…]`.

Tôi sẽ không reset `oneshop` và không xóa ảnh Cloudinary cho tới khi bạn xác nhận riêng.

---

## 11. Phase E — đã thực hiện sau khi được xác nhận (20/10/2026)

Chủ dự án xác nhận **chỉ việc reset CSDL `oneshop`**. Việc xóa ảnh Cloudinary **chưa được duyệt nên chưa thực hiện**.

1. Không còn ứng dụng/kết nối nào dùng `oneshop` (0 phiên).
2. Sao lưu mới `oneshop_final_before_real_seed_20261020_1.bak` (thư mục Backup mặc định của SQL Server, `CHECKSUM`) và `RESTORE VERIFYONLY` hợp lệ. Các bản sao lưu cũ giữ nguyên.
3. `DROP DATABASE [oneshop]` đúng tên; `oneshop_test` không đụng.
4. Chạy `01_schema.sql` rồi `02_seed.sql`: cả hai báo thành công (khối kiểm tra trong script đều qua).
5. Kiểm tra: 23 bảng, 27 sản phẩm, 27 ảnh, 8 đơn hàng, 10 người dùng, 1 dòng hoàn tiền.
6. Khởi động ứng dụng với worker thanh toán tắt; smoke test chỉ đọc 27/27 đạt (danh mục, 26 trang sản phẩm + 1 ngừng bán 404, ảnh Cloudinary 200, ghi công, màn hình Customer/Staff/Admin, đơn 7 có sổ cái). Ứng dụng đã được tắt lại sau đó.

Phục hồi nếu cần: khôi phục bản sao lưu ở bước 2 vào tên CSDL mới rồi so sánh; không tự ghi đè.
Còn chờ duyệt: xóa 22 ảnh Cloudinary cũ (mục 9–10).
