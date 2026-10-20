# Báo cáo triển khai UI/UX OneShop V3 (20/10/2026)

Căn cứ: `OneShop_V3_Audit_UIUX_49_ManHinh.md` (định hướng UI/UX) + đặc tả V3 và README (nguồn chuẩn logic). Giữ nguyên stack, màu cam san hô, logo.

## Mâu thuẫn Audit ↔ V3 và cách xử lý
- **ONLINE ở checkout**: Audit muốn mở; README/`ONESHOP_CHECKOUT_ONLINE_ENABLED=false` là chính sách release → **không bật**. Chỉ làm rõ thông báo "chưa mở" kèm phương án thay thế.
- **"Mua ngay"**: chưa có luồng backend → không thêm (ngoài phạm vi V3).
- **Lịch sử Order #1 sai thứ tự**: truy vấn đã sắp theo `changed_at` tăng dần; đây là dữ liệu seed, không sửa dữ liệu.
- **Filter trạng thái đơn của khách, "quay về ý định mua sau login"**: cần thêm backend → hoãn.

## Màn hình đã chỉnh
**P0**
- Header client: bỏ "Chi nhánh đang chọn / Chọn chi nhánh"; thay bằng dải thương hiệu + link tra cứu cửa hàng.
- Home (01): bỏ ngữ cảnh Store; sửa khoảng trắng (nguyên nhân: card `data-reveal` ẩn đến khi cuộn → thêm fallback 2,5s); card sản phẩm thêm giỏ bằng `productId`.
- Catalog + Product Detail (02, 03, 07, 08): luôn là catalog chung (controller không còn đọc Store cookie), một giá, "Còn hàng toàn hệ thống", chip bộ lọc đang áp dụng + số kết quả; "Chi nhánh đang bán / Chọn chi nhánh này" → panel tùy chọn "Kiểm tra tại cửa hàng" (không giá, không số lượng, không chọn Store).
- Danh sách cửa hàng (04): bỏ nút "Chọn chi nhánh này", chỉ tra cứu.
- Admin StoreProduct (22–24): bỏ ô nhập giá; hiển thị "Giá online chung (chỉ đọc)"; **backend bỏ qua `price` bị gửi giả** (`StoreProductRequest`, `StoreProductServiceImpl`, `StoreProductMapper`).
- Checkout (02): khối đồng ý tách COD nhiều Store chuyển sát nút xác nhận, nêu số lần thu tiền; thông báo ONLINE chưa mở rõ hơn.
- Order detail/list (Customer, Admin): một Order với 3 lớp trạng thái tách riêng, phần giao theo chi nhánh, nhãn tiếng Việt, ẩn nút "Đơn cùng chi nhánh" khi đơn nhiều Store.

**P1**
- Admin Dashboard: KPI toàn chuỗi (Orders tính một lần, phần giao đang xử lý, đơn cần chú ý, SKU hết hàng) + bảng một dòng/Chi nhánh (thêm `getChainSummary`).
- Admin Products: cột giá online chung; Product form giải thích giá chung. Admin Orders/Payments/Reviews: bảng tóm tắt, nhãn VN, nhãn "mô phỏng" cho hoàn tiền/cổng.
- Staff: bảng phần giao gọn cột + nút việc tiếp theo, stepper trạng thái, xác nhận khi đánh dấu thất bại, lọc trạng thái tiếng Việt; Stock: huy hiệu Hết hàng/Sắp hết; Điều chỉnh tồn: xem trước tồn trước → sau + chênh lệch + xác nhận; Lịch sử tồn: nhãn loại, badge +/-; Reviews: trạng thái "Cần phản hồi/Đã phản hồi", form trả lời thu vào mục mở rộng.
- Cart: đếm/tổng tiền các dòng đã chọn, nút đặt hàng vô hiệu khi chưa chọn.
**P2**: skip-link, `:focus-visible`, cỡ chữ bảng ≥14px, tap target 44px trên mobile, màu chữ phụ đạt AA (`os-muted-text`); giải thích "khu vực" ≠ vùng phục vụ và tác động tắt Store (form Chi nhánh).

## Kiểm thử
- Toàn bộ suite (`oneshop_test`): 1590 test; lần đầu 22 lỗi, đều do test mã hóa hành vi V2 hoặc `confirm()` làm kẹt E2E (đã bỏ confirm cho thao tác thường ngày). Đã cập nhật ~20 test sang V3 và chạy lại các lớp liên quan: Catalog*, Cart*, StaffInventory*, BrowserV3 (7 luồng ONLINE/COD/PICKUP/REVIEW) đều PASS; các lớp *Web*/Layouts PASS.
- Chụp thực tế Home/Detail (1440px) xác nhận hết khoảng trắng và bỏ ngữ cảnh Store. **Lần chạy full suite cuối sau chỉnh `os-muted-text` chưa lặp lại** (chỉ đổi class CSS/văn bản).
- Chưa kiểm tra thủ công 768/390px cho mọi màn (mới chụp Home 390); chưa đo contrast tự động.

## Tồn đọng
1. Cookie/Interceptor Store và `/stores/select|clear`, `getStoreCatalog` vẫn còn (vô hiệu với UI, giữ để không phá test dịch vụ) → nên dọn ở đợt riêng.
2. `AdminOperationsService.PROCESSING` chỉ đếm CONFIRMED/PROCESSING/SHIPPING/PARTIALLY_COMPLETED (test khóa), bỏ PREPARING/PACKED/READY_FOR_PICKUP → cần chủ dự án xác nhận định nghĩa "cần xử lý"; dashboard hiện mô tả đúng định nghĩa này.
3. Chưa làm: filter trạng thái đơn khách, quay lại ý định mua sau login, ảnh seed theo SKU, accordion Fulfillment, các form Category/Brand/User (P2) ngoài chuẩn hóa chung.
4. Điểm V3 chưa chốt (mục 7 Audit) giữ nguyên, UI không công bố cam kết giao hàng/phí/thời gian.
