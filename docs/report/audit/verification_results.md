# Kết quả kiểm chứng báo cáo OneShop

Ngày kiểm chứng: 25/10/2026. Sản phẩm: `OneShop_BaoCao_KienTruc_ChucNang_GiaoDien.docx` (225 trang A4, 3 chương, 40 sơ đồ hoạt động, 96 đoạn mã, 52 màn hình giao diện).

## 1. Kiểm tra tự động (`tools/verify.py`, chi tiết ở `verification_auto.md`)

| Hạng mục | Kết quả |
|---|---|
| 96 đoạn mã trích khớp nguyên văn với tệp nguồn (chỉ bỏ phần thụt lề chung) ở cả `.md` và `.docx` | ĐẠT |
| Mọi tên lớp (Service, Controller, Repository, Policy, ...) nêu trong mô tả, giải thích, bảng truy vết đều tồn tại trong `src/main/java` | ĐẠT |
| Mọi cặp `Lớp.phương_thức` nêu trong văn bản đều tồn tại trong lớp đó | ĐẠT |
| 52 ảnh gốc và 52 ảnh đánh số tồn tại; mỗi ảnh có đúng một bảng diễn giải, số dòng bảng bằng số chú thích, STT liên tục từ 1 | ĐẠT |
| 40 chức năng đều có sơ đồ `.svg`, `.png`, `.json` và có đủ phần A (mô tả), B (sơ đồ), C (mã), D (giải thích) | ĐẠT |
| Mỗi sơ đồ có điểm rẽ nhánh và luồng lỗi | ĐẠT |
| Tệp Word mở được bằng python-docx và Microsoft Word 16, 93 ảnh nhúng, 52 bảng, 3 Heading 1 | ĐẠT |
| Phông Times New Roman, nội dung 13 pt căn đều hai bên, giãn dòng 1,4; đoạn mã Consolas; khổ A4 | ĐẠT |
| Không có JWT secret, API key, mật khẩu trong báo cáo | ĐẠT |

## 2. Kiểm tra bằng Microsoft Word và hiển thị PDF

- Mở bằng Word (COM), cập nhật 1070 trường (mục lục, danh mục hình/bảng/đoạn mã, số thứ tự chú thích SEQ, tham chiếu chéo REF), lưu lại, xuất PDF 225 trang. Không còn thông báo lỗi trường (lỗi `Error! Bookmark not defined` ở lần dựng đầu do định danh SEQ có khoảng trắng đã được sửa).
- Dựng lại cả 225 trang PDF thành ảnh và phân tích tự động: không có trang tràn lề; có 2 trang gần trống (trang 37 và 53) do khối "B. Sơ đồ hoạt động" kèm sơ đồ dài được giữ liền một trang; các trang còn lại có nội dung.
- Xem trực quan các trang: bìa, mục lục, danh mục hình/bảng/đoạn mã, chương 1 (sơ đồ kiến trúc), sơ đồ hoạt động ngắn và dài nhất (đặt hàng), đoạn mã, trang giao diện kèm bảng (bảng chia trang thì dòng tiêu đề lặp lại), trang cuối.

## 3. Kiểm tra giao diện thật

- Website chạy bằng `mvnw spring-boot:run` (profile dev) với SQL Server cục bộ; đăng nhập thật bằng tài khoản `khachhang1`, `staff.thuduc`, `admin` của dữ liệu mẫu; ảnh chụp bằng Chrome điều khiển qua DevTools Protocol, không dùng ảnh cũ trong thư mục `screenshots/` trước đó hay ảnh dựng giả.
- Mỗi ảnh được đối chiếu bằng mắt sau khi đánh số (các thành phần đúng vị trí, số theo thứ tự từ trên xuống, từ trái sang). Các lỗi phát hiện và đã sửa: bộ chọn sai phần tử (cửa hàng tạm ngưng thay vì cửa hàng đang hoạt động), thanh "Thêm vào giỏ" cố định ở đáy che nội dung (ẩn khi chụp vì chỉ hiện khi cuộn), huy hiệu che chữ, thứ tự số không theo vị trí.
- Giỏ hàng và trang xác nhận đặt hàng được chụp với một sản phẩm tạm thêm vào giỏ của `khachhang1` rồi xóa lại (giỏ trống như ban đầu). Không có đơn hàng nào được tạo trong quá trình chụp (CSDL vẫn 15 đơn).

## 4. Giới hạn đã biết (không bịa số liệu để lấp)

| Nội dung | Lý do chưa có ảnh giao diện |
|---|---|
| Nút Hủy đơn của khách | Ba đơn của khách thử nghiệm đều đã giao hoặc hoàn tất một phần nên không hủy được |
| Nút Thanh toán trên trang thanh toán | Chỉ hiện với đơn ONLINE đang chờ thanh toán; dữ liệu mẫu chỉ có đơn đã thanh toán |
| Khối xử lý ngoại lệ giao hàng của quản trị viên | Chỉ hiện với đơn NEEDS_ATTENTION; dữ liệu mẫu không có đơn như vậy |
| Duyệt hoặc từ chối trả hàng, xử lý hàng trả của nhân viên | Dữ liệu mẫu chưa có yêu cầu trả hàng; hai trang danh sách được chụp ở trạng thái rỗng. Tạo yêu cầu thật cần tải ảnh lên Cloudinary và ghi CSDL nên không thực hiện chỉ để chụp |
| Quản lý địa chỉ giao hàng | Hệ thống không có chức năng này trên giao diện (xem `function_inventory.md`) |

Các chức năng trên vẫn có sơ đồ hoạt động và mã nguồn đầy đủ, vì sơ đồ và mã được lấy từ source; chỉ phần ảnh giao diện của các trạng thái đó chưa có.

## 5. Tác động lên dữ liệu khi chạy website

- Khi khởi động, tác vụ nền `PaymentTimeoutWorker` của ứng dụng (bật theo cấu hình mặc định, trái với README ghi là tắt) đã hủy và hoàn tồn 2 đơn ONLINE quá hạn thanh toán của dữ liệu mẫu: đơn #11 và #12 chuyển sang CANCELLED, payment_status FAILED. Đây là hành vi bình thường của ứng dụng, không do báo cáo gây ra; các số liệu hiển thị trong ảnh chụp phản ánh trạng thái sau khi tác vụ chạy.
- Ngoài ra không có thay đổi dữ liệu nghiệp vụ nào; không sửa mã nguồn, lược đồ hay giao diện của dự án. Ứng dụng và Chrome tự khởi động cho việc chụp đã được tắt sau khi hoàn tất.

## 6. Việc chưa thể thực hiện theo đúng gợi ý của đề bài

- PlantUML hoặc Mermaid không được dùng vì việc tải và chạy tệp thực thi bên ngoài bị từ chối trong môi trường này; thay vào đó sơ đồ hoạt động được vẽ bằng bộ vẽ tự viết (`tools/actdiag.py`) từ danh sách bước nằm trong `diagrams/**/*.json`. Hình xuất SVG và PNG; sơ đồ chỉ có hai làn bơi (tác nhân, hệ thống), thao tác CSDL biểu diễn bằng ô màu xám thay cho làn thứ ba để chữ đủ lớn khi in A4.
- Số thứ tự hình, bảng, đoạn mã dùng trường SEQ của Word (đặt lại theo chương) và tham chiếu chéo dùng trường REF, nên tự cập nhật khi chỉnh sửa trong Word.
