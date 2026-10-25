# Công cụ tái tạo báo cáo

Toàn bộ nội dung báo cáo được sinh từ các tệp trong thư mục này; không có hình hay đoạn mã nào được dựng tay.

| Bước | Lệnh | Kết quả |
|---|---|---|
| 1. Nội dung chức năng | sửa `content_customer.py`, `content_admin.py`, `content_staff.py` | mô tả, sơ đồ (danh sách bước), đoạn mã (tệp, dòng đầu, dòng cuối), giải thích |
| 2. Sơ đồ hoạt động | `python build_diagrams.py` rồi `node render_svgs.mjs ../diagrams/*/*.svg` | `diagrams/<nhóm>/<mã>.svg/.png/.json`; kiểm tra khoảng dòng của đoạn mã |
| 3. Sơ đồ kiến trúc | `python arch.py` rồi `node render_svgs.mjs ../diagrams/architecture/architecture.svg` | `diagrams/architecture/architecture.svg/.png` |
| 4. Ảnh giao diện | chạy ứng dụng, Chrome `--remote-debugging-port=9222`, đặt `ONESHOP_APP_URL`, `ONESHOP_SEED_PASSWORD`, rồi `node capture.mjs [--only C01,A02]` | `screenshots/<nhóm>/<mã>.png`, `<mã>_annotated.png`, `manifest.json` |
| 5. Báo cáo | `python build_report.py` | `.docx` và `.md` |
| 6. Cập nhật trường Word, xuất PDF | `powershell -File word_update.ps1 -Docx ... -Pdf ... -Info ...` | mục lục, danh mục hình/bảng/đoạn mã, số thứ tự chú thích, tham chiếu chéo |
| 7. Kiểm chứng | `python build_audit.py`, `python verify.py`, `node pdfshot.mjs`, `python pageaudit.py` | `audit/*.md` |

Ghi chú:

- `actdiag.py` là bộ vẽ Activity Diagram (làn bơi) tự viết, chỉ phụ thuộc Pillow; `render_svgs.mjs` dùng Chrome để xuất PNG.
- `spec.mjs` khai báo từng màn hình và các thành phần được đánh số (bộ chọn CSS hoặc văn bản, tên thành phần, chức năng). Số thứ tự được sắp xếp tự động từ trên xuống dưới, từ trái sang phải.
- `probe*.mjs`, `survey.mjs` dùng để khảo sát DOM của trang đang chạy khi viết `spec.mjs`.
- Mật khẩu tài khoản thử nghiệm đọc từ biến môi trường `ONESHOP_SEED_PASSWORD` (tệp `.env.seed.local`, đã bị Git bỏ qua); không có mật khẩu nào được ghi vào báo cáo hay vào mã của thư mục này.
