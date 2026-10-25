# Kết quả kiểm chứng tự động

| Kiểm tra | Kết quả | Chi tiết |
|---|---|---|
| Mọi đoạn mã trích khớp nguyên văn với mã nguồn (md và docx) | ĐẠT | 96 đoạn kiểm tra; lỗi: [] |
| Mọi tên lớp được nhắc tới trong mô tả/giải thích tồn tại trong mã nguồn | ĐẠT | không tìm thấy: [] |
| Mọi cặp Lớp.phương_thức được nhắc tới tồn tại trong lớp đó | ĐẠT | không khớp: [] |
| Ảnh gốc và ảnh đánh số tồn tại cho mọi màn hình | ĐẠT | 52 màn hình; thiếu: [] |
| Số dòng bảng diễn giải = số chú thích trên ảnh, STT liên tục từ 1 (từng màn hình) | ĐẠT | 52 bảng giao diện cho 52 ảnh; lệch: [] |
| Mỗi chức năng có sơ đồ svg, png và mã nguồn json | ĐẠT | 40 chức năng; thiếu [] |
| Mỗi chức năng có đủ 4 phần A, B, C, D và ít nhất một đoạn mã | ĐẠT | 40 chức năng |
| Mỗi sơ đồ có ít nhất một điểm rẽ nhánh và luồng lỗi | ĐẠT | thiếu: [] |
| Tệp docx là gói zip hợp lệ và có thể mở bằng python-docx | ĐẠT | 3413 đoạn văn, 52 bảng, 93 ảnh nhúng |
| Ba chương dùng Heading 1 (mục lục tự động) | ĐẠT | ['Chương 1. KIẾN TRÚC ỨNG DỤNG', 'Chương 2. SƠ ĐỒ HOẠT ĐỘNG CÁC CHỨC NĂNG', 'Chương 3. GIAO DIỆN HỆ THỐNG'] |
| Phông chữ: nội dung, tiêu đề, chú thích là Times New Roman; đoạn mã là Consolas (monospace) | ĐẠT | {'Normal': 'Times New Roman', 'Heading 1': 'Times New Roman', 'Heading 2': 'Times New Roman', 'Heading 3': 'Times New Roman', 'Caption': 'Times New Roman', 'CodeBlock': 'Consolas'} |
| Cỡ chữ nội dung 13 pt, căn đều hai bên, giãn dòng 1,4 | ĐẠT | 13.0 pt, alignment=JUSTIFY (3), line_spacing=1.4 |
| Khổ A4 (21 x 29,7 cm) | ĐẠT | 21.00 x 29.70 cm |
| Không có thông tin bí mật (JWT secret, API key, mật khẩu) trong báo cáo | ĐẠT | [] |