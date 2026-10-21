# Báo cáo kiểm toán và sửa UI/UX OneShop V3

**Cập nhật:** 21/10/2026 (ICT)  
**Phạm vi:** giao diện khách hàng, nhân viên, quản trị; giá V3; checkout; khả năng truy cập; responsive; kiểm chứng build/test.  
**Trạng thái:** **PASS** — full suite, browser E2E và responsive đã xanh trên các database kiểm thử dựng sạch. Production `oneshop` không bị ghi, migrate hay seed.

## Kết quả cuối

### Kiểm thử tự động

- Full `clean verify` trên `oneshop_v3_final_20261020_test` (dựng mới từ `database/01_schema.sql` + `02_seed.sql`): **95 suite, 1.667 test, 0 failure, 0 error, 0 skipped**, `BUILD SUCCESS`, 5:24.
- Trong đó: 41 suite unit/service (726 test), 12 suite web MVC (113 test), 41 suite database integration trên SQL Server test DB (824 test), 1 suite template contract (4 test).
- Log: `verify_run_finaldb2_20261020_232842.log`; Surefire: `target/surefire-reports` (95 file XML khớp tổng 1.667 test).
- Flake `StaffFulfillmentWebIntegrationTest.friendlyErrorsWithoutLeaks` đã được xử lý ở gốc: token CSRF ngẫu nhiên có thể chứa chuỗi `SQL`, nên kiểm tra rò rỉ nay chỉ quét nội dung người dùng thấy trong `<main>`; toàn bộ marker `SECRET`, `PRIVATE`, `ABCD2345`, `SQL` vẫn được kiểm tra, không xóa test hay nới lỏng assertion nghiệp vụ.

### Browser E2E

- Chrome thật, thao tác form thật trên `oneshop_v3_e2e_20261020_test`; mock gateway bật, timeout worker tắt.
- **6 nhóm kịch bản PASS**, 21 ảnh bằng chứng:
  - ONLINE đa Store: Order #9, 845.000 ₫, 1 Order / 2 fulfillment (#13 Store 1, #14 Store 2), thanh toán mock thành công.
  - Cô lập Store: Staff Store 1 và Store 2 đều nhận **403** khi mở fulfillment của Store còn lại.
  - Giao một phần: #13 `COMPLETED`, #14 `CANCELLED`; Admin xác nhận không giao được, hoàn mô phỏng **445.000 ₫**, giữ **400.000 ₫** (370.000 ₫ hàng đã giao + 30.000 ₫ phí giao).
  - Khách thấy đúng số đã trả/đã hoàn/giữ lại; hủy Order chưa thanh toán #10 và hoàn kho.
  - Pickup: Order #11 / fulfillment #16, mã **L25PWEQA**, bàn giao và hoàn tất ở cả Staff lẫn khách.
  - COD tách đơn: Order #12 (Store 1, 400.000 ₫) và #13 (Store 2, 475.000 ₫), mỗi Order phí giao 30.000 ₫, đều `CONFIRMED`.
- Kết quả: `screenshots/e2e-final-20261020/e2e-result.json` — `"result": "PASS"`.
- Console chỉ có 2 lỗi tài nguyên 403 đúng chủ đích của kiểm tra cô lập Store; không có exception JavaScript.

### Responsive

- **135 capture** trên **45 route** × 375/768/1440 px: guest 7 route/21 ảnh, customer 7/21, Staff Store 1 10/30, Admin 21/63.
- Tất cả trả HTTP 200, có `<main>`, không phát sinh cuộn ngang thực tế (`scrollX = 0`).
- Kết quả: `screenshots/responsive-final-20261020/responsive-result.json` — `captureCount: 135`, `failures: 0`, `overflowCount: 0`.
- Khách hàng có thêm thao tác thật thêm sản phẩm vào giỏ và mở form checkout; không gửi đơn, không đổi tồn kho/thanh toán.

### An toàn dữ liệu

- `oneshop` chỉ được đọc; không chạy migration, seed, reset hay lệnh ghi.
- Phiên JDBC của ứng dụng chỉ trỏ tới `oneshop_v3_e2e_20261020_test` (xác nhận bằng `sys.dm_exec_sessions`).
- `oneshop_v3_final_20261020_test` và `oneshop_v3_e2e_20261020_test` đều là database dùng một lần, tạo mới từ schema/seed chuẩn.
- `oneshop_test` legacy không được dùng làm nguồn nghiệm thu vì dữ liệu không khớp seed/schema hiện hành; không sửa schema của database này.

## Đã sửa và xác minh trong các vòng UI/UX

- Checkout ONLINE nêu rõ **thanh toán mô phỏng, không thu tiền thật** ngay tại lựa chọn phương thức và trên trang kết quả.
- Đồng bộ fixture catalog V3: giá bán lấy từ `Product` (giá online dùng chung), không dùng giá tồn dư `StoreProduct`.
- Bổ sung `CheckoutTemplateContractTest` để ngăn việc bỏ cảnh báo giao dịch mô phỏng.
- Giữ và hoàn thiện các thay đổi UI/UX đang có: trạng thái/tương phản/focus, thao tác checkout bằng bàn phím, phản hồi khi gửi đơn, bảng fulfillment Staff responsive với nút hành động luôn hiển thị.
- Sửa flake CSRF trong `StaffFulfillmentWebIntegrationTest` mà không giảm độ phủ kiểm tra rò rỉ.

## File chính của lượt cuối

- `src/test/java/com/oneshop/StaffFulfillmentWebIntegrationTest.java` — giới hạn phạm vi kiểm tra rò rỉ vào `<main>`.
- `scripts/lib/cdp.mjs` — helper Chrome DevTools Protocol.
- `scripts/e2e_final_verification.mjs` — kịch bản E2E thao tác thật.
- `scripts/responsive_final_verification.mjs` — ma trận responsive 45 route × 3 kích thước.
- `screenshots/e2e-final-20261020/` — 21 ảnh + `e2e-result.json`.
- `screenshots/responsive-final-20261020/` — 135 ảnh + `responsive-result.json`.
- `docs/ONESHOP_V3_FINAL_VERIFICATION.md` — báo cáo xác minh cuối đầy đủ.

Các thay đổi UI/UX đang có trong working tree từ các vòng trước được giữ nguyên; không reset, không ghi đè, không đổi công nghệ.

## Nhật ký xác minh

| Kiểm tra | Kết quả |
|---|---|
| Targeted regression: `CheckoutTemplateContractTest`, `ProductServiceV3Test`, `WebRoutesIntegrationTest`, `CatalogWebIntegrationTest`, `HomePageWebIntegrationTest` | **50/50 pass** |
| Full `clean verify` trên database dựng sạch | **PASS** — 1.667 test, 0 failure/error/skipped, BUILD SUCCESS, 5:24 |
| Browser E2E Chrome thật | **PASS** — 6 nhóm kịch bản, 21 ảnh, order/payment/refund/stock đúng |
| Responsive 375/768/1440 | **PASS** — 135 capture, 0 lỗi HTTP, 0 cuộn ngang |
| Production schema/data | Không ghi/migrate/seed; ứng dụng chỉ kết nối database E2E |

## Tồn đọng P2

1. **Thanh toán mock:** đúng phạm vi V3, chưa có cổng thanh toán thật.
2. **`oneshop_test` legacy:** dữ liệu không khớp seed/schema hiện hành; nghiệm thu dùng database dựng sạch. Không sửa schema database này.
3. **Full-bleed trang chủ:** dải `100vw` có thể rộng hơn vùng nội dung ~8 px khi có scrollbar cổ điển, nhưng `body { overflow-x: clip }` giữ `scrollX = 0`, không có thanh cuộn ngang và không cản thao tác.
4. **Ngoài phạm vi:** chưa kiểm thử tải/đa người dùng thực, email/SMS thật, hoặc screen-reader thủ công toàn trình.

**Kết luận:** Không còn P0/P1 mở. OneShop V3 **READY** cho nghiệm thu chức năng trên môi trường kiểm thử; các giới hạn còn lại đều là P2 đã biết và không chặn phát hành theo phạm vi V3.
