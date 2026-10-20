# Báo cáo khắc phục sau kiểm thử OneShop V3 (20/10/2026)

Chỉ dùng database test độc lập (đã xóa sau khi xong). Không đụng `oneshop`, `oneshop_test`.

## Phân loại vấn đề từ báo cáo kiểm thử
| # | Vấn đề | Loại | Xử lý |
|---|---|---|---|
| 1 | Sản phẩm ACTIVE lưu được giá `0 ₫`, hiện công khai nhưng không mua được | **Lỗi thực tế** | Đã sửa |
| 2 | Thanh toán ONLINE bị vô hiệu hóa ở checkout | **Chức năng chưa hoàn thiện (cờ cấu hình)** | Đã mở, kiểm chứng E2E |
| 3 | Khóa tạm mã nhận hàng sau nhiều lần sai (kể cả nhập đúng sau đó, 409) | Đúng thiết kế (`PickupAttemptLimiter`) | Không đổi |
| 4 | `/health` trả 406 với `Accept: text/html` | Đúng thiết kế (JSON-only) | Không đổi |
| 5 | Dashboard "Cần xử lý" chỉ đếm 4 trạng thái Order (test khóa) | Cần chủ dự án xác nhận | Không đổi (chưa xử lý) |
| 6 | 4 test cũ lỗi (V2) | Test lỗi thời do đổi hành vi, đã cập nhật ở đợt trước | Giữ |

## 1. Giá 0 ₫
- **Nguyên nhân gốc:** `ProductRequest.price` mặc định `0`, chỉ kiểm `>= 0`; `ProductServiceImpl.apply` cũng cho `0`. Giỏ hàng lại từ chối giá `<= 0` ⇒ sản phẩm hiển thị nhưng không mua được.
- **Sửa:** `ProductRequest.isActivePriceValid()` (validation ở form, hiển thị lỗi trong `admin/product-form.html`) và chốt chặn ở service `ProductServiceImpl.apply` (cả tạo và sửa). Sản phẩm không ACTIVE vẫn được lưu giá 0.
- **Kiểm chứng:** test mới `ProductRequestValidationTest` (3), `CatalogDatabaseIntegrationTest.anActiveProductWithPriceZeroIsRefusedByTheServiceToo`; HTTP: tạo/sửa ACTIVE giá 0 bị từ chối và giá cũ giữ nguyên; INACTIVE giá 0 được lưu.
- **Test đã sửa và lý do:** helper `productRequest` trong `CatalogDatabaseIntegrationTest` và POST trong `CatalogHttpDatabaseIntegrationTest.duplicateSkuIsShownOnTheProductForm` không gửi giá nên ngầm tạo sản phẩm ACTIVE giá 0 — dữ liệu test không hợp lệ theo quy tắc mới; đã thêm giá hợp lệ, không nới lỏng assertion.

## 2. ONLINE
- **Nguyên nhân gốc:** `oneshop.checkout.online-enabled` mặc định `false` (comment: "mở khi có BE-05"). BE-05 (hạn 15 phút, đối soát, hủy/hoàn tồn, hoàn tiền mô phỏng, mock gateway) **đã được cài đặt và có test** nhưng cờ không được bật; timeout worker cũng mặc định tắt nên nếu chỉ bật ONLINE thì đơn chưa trả giữ tồn mãi.
- **Sửa (`application.properties`, `application-prod.properties`, `CheckoutProperties`, `PaymentProperties`, `.env.example`, `README.md`):** `online-enabled` mặc định `true`; `timeout-worker-enabled` mặc định `true`; profile `prod` đặt ONLINE `false` vì không có cổng thật (không tích hợp cổng thật). Test profile tắt worker (`application-test.properties`) vì test gọi scan trực tiếp.
- **Không cần sửa logic nghiệp vụ:** luồng ONLINE đã đúng đặc tả; phần mới là cấu hình.
- **Test cập nhật:** `CheckoutV3PolicyGateDatabaseIntegrationTest` (từ "ONLINE đóng mặc định" → "mở mặc định + công tắc tắt vẫn từ chối"), `PaymentDeadlinePolicyTest` (mặc định true). Phạm vi bảo vệ "đóng" vẫn còn.
- **Kiểm chứng E2E qua HTTP (93 kiểm tra, mặc định, không cờ test):** 1 Order / 1 Payment / 2 Fulfillment từ 2 Store, phí giao 30.000 một lần, tồn trừ lúc đặt và không trừ lần hai khi trả, Staff không chuẩn bị được khi chưa trả, nút success/failure của trình duyệt không đổi trạng thái, SUCCESS → CONFIRMED/PAID, FAILED = hủy cuối + hoàn tồn một lần + không retry + thanh toán trễ chỉ hoàn tiền mô phỏng, UNKNOWN → Admin quyết định, hủy đơn đã trả → hoàn tiền mô phỏng đúng một lần, hủy khi PENDING_PAYMENT, timeout worker thật hủy đơn quá 15 phút và hoàn tồn, thanh toán trễ không xác nhận lại, ONLINE + nhận tại cửa hàng, hoàn tất → COMPLETED/PAID, bất biến DB (một SUCCESS/đơn, không âm tồn).

## Kiểm thử đã chạy
- Toàn bộ suite trên DB test mới dựng từ `01_schema.sql` + `02_seed.sql`: **1594 test, 0 Failures, 0 Errors, 0 Skipped** (gồm E2E trình duyệt). Lần chạy đầu sau khi đổi cấu hình có 5 lỗi (3 test giả định cũ về ONLINE/mặc định, 2 do helper không có giá); đã xác định nguyên nhân thật và cập nhật như trên rồi chạy lại toàn bộ.
- HTTP regression 4 role trên DB mới: khách vãng lai/auth 69/70, khách hàng 93/94, nhân viên+admin 216/218, tồn kho/đua/pickup 33/33, ONLINE 93/93. Các mục "lỗi" còn lại đều là sai sót của script kiểm tra, đã xác minh riêng: `/health` 406 (xem #4); regex đếm số lượng gộp dòng giỏ (giá trị thực là 5, xác nhận bằng script riêng); kiểm tra sản phẩm ngừng bán dùng `originalPrice` < `price` nên bị từ chối, kiểm tra lại với dữ liệu đúng: ẩn khỏi danh mục, chi tiết 404, không thêm được vào giỏ.

## Chưa xử lý / cần quyết định
1. Mặc định worker bật nghĩa là mở app trên một DB có sẵn đơn ONLINE `PENDING_PAYMENT` quá hạn (ví dụ đơn #8 của seed trong `oneshop`) sẽ hủy và hoàn tồn đơn đó ngay lần quét đầu (theo DEC-15). Muốn xem dữ liệu mà không ghi: `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=false`.
2. Production vẫn đóng ONLINE (không có cổng thật; ngoài phạm vi).
3. Định nghĩa "Cần xử lý" của dashboard Admin (mục 5) cần chủ dự án xác nhận.
4. Danh sách Admin thanh toán chỉ liệt kê ca cần xử lý sau hạn; ca UNKNOWN trước hạn vẫn mở được qua trang chi tiết nhưng chưa nằm trong danh sách (hành vi hiện có, chưa kết luận là lỗi).
