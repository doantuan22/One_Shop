# Ghi chú lỗi

## Báo cáo từ tester

- Khi nhân viên đổi trạng thái / chuẩn bị hàng của đơn thì bị đẩy sang trang 500.
  - Trạng thái: **chưa tái hiện được** (xem mục "Đã kiểm tra" bên dưới).
  - Cần thêm: mã đơn, trạng thái đơn trước khi bấm, nút đã bấm, tài khoản nhân viên, đường dẫn trang 500, và log server tại thời điểm đó (tìm dòng `threw exception` hoặc `Unexpected error`).

## Lỗi đã tái hiện

### BUG-01: Upload file không phải ảnh nhưng khai báo `image/*` gây HTTP 500 (mức: cao) — ĐÃ SỬA

- **Đã sửa:** `CloudinaryServiceImpl.uploadImage` nay bắt cả `RuntimeException`. Lỗi nội dung ảnh ("Invalid image file", unsupported, empty file, file size too large) → `BadRequestException` ("Tệp tải lên không phải hình ảnh hợp lệ"), người dùng được chuyển về form với `error=image-invalid`. Lỗi khác của provider (sai khóa, quota...) → `ImageStorageException` (`error=image-storage`), thông báo không lộ chi tiết từ provider.
- **Kiểm thử:** `CloudinaryServiceTest` bổ sung 5 test (ảnh không hợp lệ, lỗi provider khác, IOException, upload thành công, lỗi khi xóa).

- **Tái hiện:**
  - `POST /admin/products/{id}/images` với file nội dung không phải ảnh, `Content-Type: image/png` → **500**.
  - `POST /admin/brands/{id}/logo` với cùng loại file → **500**.
  - Cùng file với `Content-Type: text/plain` → 302 kèm `error=image-invalid` (đúng).
- **Nguyên nhân:** `CloudinaryServiceImpl.uploadImage` (dòng ~43–52) chỉ bắt `IOException`. Khi Cloudinary từ chối nội dung ảnh, SDK ném `RuntimeException: Invalid image file`, lọt ra ngoài. Controller `AdminProductController.uploadImage` và `AdminBrandController.uploadLogo` chỉ bắt `BadRequestException` và `ImageStorageException`, nên thành 500.
- **Ghi chú:** request đã được gửi tới tài khoản Cloudinary thật trong `.env`. Cloudinary đã từ chối, không tạo ảnh.
- **Hướng sửa:** trong `uploadImage`, bắt `RuntimeException` ngoài `IOException`; lỗi "Invalid image file" đổi thành `BadRequestException`, lỗi khác đổi thành `ImageStorageException`.

## Lỗi nghi ngờ (đọc code, chưa tái hiện)

### CODE-01: Xóa ảnh trên Cloudinary có thể gây 500 (mức: trung bình) — ĐÃ SỬA

- **Đã sửa:** `CloudinaryServiceImpl.deleteImage` nay bắt `RuntimeException` và đổi thành `ImageStorageException`; các controller đã bắt loại này nên hiển thị `error=image-storage` thay vì 500. Có test `providerFailureOnDeleteBecomesImageStorageException`.

- `CloudinaryServiceImpl.deleteImage` gọi `cloudinary.uploader().destroy(...)` và chỉ bắt `IOException`. Khi Cloudinary báo lỗi API (sai khóa, bị giới hạn...), SDK ném `RuntimeException`.
- Các nơi gọi không bắt loại này: `ProductServiceImpl.deleteProductImage` (dòng ~345), `ProductServiceImpl.removeBrandLogo` (dòng ~236). Controller `deleteImage` và `removeLogo` chỉ bắt `ImageStorageException` → 500.
- Chưa tái hiện vì cần Cloudinary trả lỗi.
- Hướng sửa: giống BUG-01, bắt `RuntimeException` và đổi thành `ImageStorageException` trong `deleteImage`.

### UX-01: Upload vượt 5MB hiện trang lỗi mặc định của Spring (mức: thấp)

- Upload file trên 5MB trả HTTP 413 (không phải 500). Tuy nhiên `WebExceptionHandler` không xử lý `MaxUploadSizeExceededException`, nên người dùng thấy trang lỗi mặc định thay vì thông báo tiếng Việt như các lỗi khác.
- Hướng sửa: thêm handler cho `MaxUploadSizeExceededException` trả về `error-message` với thông báo "Ảnh tối đa 5MB".

## Đã kiểm tra, không phát hiện lỗi 500

Các kiểm tra thực hiện trên app local với database seed và bản sao database:

- Crawl GET các trang theo link, 4 vai trò (khách, khách hàng, staff, admin): không có 500.
- Gọi các route có id sai (0, -1, 999999, abc) và tham số query sai: không có 500.
- Duyệt tổ hợp id 0–25 trên 497 đường dẫn, 4 vai trò: không có 500.
- Chọn từng chi nhánh (kể cả INACTIVE) trên trang sản phẩm và trang chủ: không có 500.
- Submit form với dữ liệu hợp lệ (bản sao DB, khoảng 260 trang): không có 500.
- Đường đi đầy đủ của đơn giao hàng và nhận tại cửa hàng (COD, ONLINE, PAY_AT_STORE), gồm mọi thao tác sai trạng thái: không có 500, các thao tác sai trả 400.
- Mã nhận hàng sai/thiếu, nhân viên khác chi nhánh, đơn thanh toán online thất bại, nhân viên nhiều chi nhánh, nhân viên chưa phân công: không có 500.
- Trang chi tiết đơn (khách, admin, staff) trên 13 đơn ở nhiều trạng thái, 114 request: không có 500.
- Upload file rỗng và file không phải ảnh có `Content-Type: text/plain`: xử lý đúng.

## Đã sửa trong phiên này (tham khảo)

- FSA-02: mật khẩu Unicode vượt 72 byte gây 500 → nay trả 400 (kiểm tra UTF-8 byte ở DTO và service).
- FSA-01: bổ sung chức năng đánh giá sản phẩm của khách sau khi đơn hoàn tất (+ migration `migration_002`).
