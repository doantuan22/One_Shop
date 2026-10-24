# Ảnh màn hình OneShop (bộ chụp đầy đủ)

Sinh bởi `scripts/capture_full_ui.mjs` trên server đang chạy (`http://127.0.0.1:8095`), chỉ GET (trừ thao tác
thêm 1 món vào giỏ khi giỏ trống của khách hàng demo — được xóa lại ngay sau khi chụp).

## Role
- `guest/`     — chưa đăng nhập: trang chủ, đăng nhập (+ trạng thái lỗi, vừa đăng xuất), đăng ký, sản phẩm,
  chi tiết sản phẩm, hệ thống cửa hàng, ghi công hình ảnh, lỗi 404.
- `customer/`  — khách hàng: tài khoản, sản phẩm, giỏ hàng, đặt hàng (giao tận nơi + nhận tại cửa hàng),
  đơn của tôi, chi tiết đơn, trang thanh toán, lỗi 404.
- `staff/`     — nhân viên: tổng quan, hàng đợi phần giao (+ lọc, 2 tab, đơn tổng hợp), chi tiết, đánh giá,
  ton kho (+ lọc, trang điều chỉnh, modal mở), lịch sử tồn kho (3 dạng), lỗi 403.
- `admin/`     — quản trị: 12 trang chính, form thêm/sửa (sản phẩm, SKU theo chi nhánh, danh mục,
  thương hiệu, người dùng, chi nhánh), chi tiết đơn/thanh toán, đánh giá (+ modal lý do), lỗi 403.

## Tỉ lệ chuẩn từng thiết bị (viewport thật)
Một tỷ lệ duy nhất: desktop 1440×900

## Trang dài
Chụp thành nhiều phần `-p01, -p02, ...` theo từng lần cuộn bằng chiều cao viewport (không thẳng lại,
mỗi phần hiển thị chính xác phần đó + thanh chủ đề dính).

## Tên file
`<role>/<tự số>-<trang>/<tự số>-<trang>-<RỘTONGxCAO>-p<phan>.png`

Danh sách chi tiết từng file (URL, tiêu đề, tỷ lệ, scrollY, HTTP status) nằm trong `manifest.json`.
Đếm nhanh: `manifest.summary`.
