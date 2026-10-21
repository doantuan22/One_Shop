# Danh Sách Tài Khoản Test & Đọc Hiểu Cơ Sở Dữ Liệu OneShop V3

Tài liệu này tổng hợp kiến trúc CSDL và toàn bộ danh sách tài khoản thử nghiệm (Seed Data) của hệ thống thương mại điện tử chuỗi cửa hàng mỹ phẩm **OneShop V3**.

---

## 1. Tổng Quan Về Cơ Sở Dữ Liệu OneShop V3

- **Hệ quản trị CSDL:** Microsoft SQL Server (Collation: `Vietnamese_100_CI_AI`).
- **Mô hình kinh doanh:** Chuỗi bán lẻ mỹ phẩm đa chi nhánh (Omnichannel):
  - **Product:** Danh mục sản phẩm đồng nhất với một mức giá niêm yết online toàn chuỗi.
  - **Store & StoreProduct:** Quản lý tồn kho theo từng chi nhánh vật lý (`OS-THUDUC`, `OS-GOVAP`, `OS-QUAN7`, `OS-QUAN10`, `OS-HAICHAU`, `OS-HOANKIEM`).
  - **Fulfillment:** Đơn hàng giao tận nơi (DELIVERY) có thể tách thành nhiều kiện hàng (Fulfillments) xuất từ nhiều cửa hàng khác nhau; đơn nhận tại quầy (STORE_PICKUP) gom tại 1 chi nhánh có đủ tồn kho.
  - **Reviews & Phản hồi:** Đánh giá gắn liền với sản phẩm trong đơn đã giao thành công; nhân viên chỉ được trả lời đánh giá xuất phát từ chi nhánh của mình; Admin có quyền kiểm duyệt.

### Cơ chế đăng nhập & Xác thực
- **Đường dẫn đăng nhập web:** `/login`
- **API Endpoint:** `POST /api/auth/login` (JSON body: `{"identifier": "<username hoặc email>", "password": "<mật khẩu>"}`)
- **Định danh đăng nhập:** Người dùng có thể đăng nhập linh hoạt bằng **Username** hoặc **Email**.
- **Mật khẩu mặc định cho TẤT CẢ tài khoản test:**
  ```text
  OneShop@123
  ```
  *(Chuỗi mã hóa BCrypt lưu trong CSDL: `$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW`)*

---

## 2. Bảng Tra Cứu Nhanh Các Tài Khoản Test

| Vai trò (Role) | Username | Email | Mật khẩu | Họ và tên | Chi nhánh / Ghi chú |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ADMIN** | `admin` | `admin@oneshop.vn` | `OneShop@123` | Quản Trị Viên OneShop | Toàn hệ thống (`/admin/**`) |
| **STAFF** | `staff_thuduc` | `staff.thuduc@oneshop.vn` | `OneShop@123` | Nguyễn Văn An | Chi nhánh Thủ Đức (`OS-THUDUC`) |
| **STAFF** | `staff_govap` | `staff.govap@oneshop.vn` | `OneShop@123` | Trần Thị Bình | Chi nhánh Gò Vấp (`OS-GOVAP`) |
| **STAFF** | `staff_quan7` | `staff.quan7@oneshop.vn` | `OneShop@123` | Lê Minh Châu | Chi nhánh Quận 7 (`OS-QUAN7`) |
| **STAFF** | `staff_quan10` | `staff.quan10@oneshop.vn` | `OneShop@123` | Võ Hoài Dung | Chi nhánh Quận 10 *(Chỉ nhận tại quầy - Pickup Only)* |
| **STAFF** | `staff_hoankiem` | `staff.hoankiem@oneshop.vn` | `OneShop@123` | Hoàng Tuấn Kiệt | Chi nhánh Hoàn Kiếm *(Chỉ giao hàng - Delivery Only)* |
| **CUSTOMER** | `khachhang1` | `khachhang1@example.com` | `OneShop@123` | Phạm Thu Hà | Có sẵn giỏ hàng + Đơn hàng #1, #2 |
| **CUSTOMER** | `khachhang2` | `khachhang2@example.com` | `OneShop@123` | Đặng Quốc Khánh | Đơn hàng #3 (3 kiện), Đơn #4 (Pickup chờ nhận) |
| **CUSTOMER** | `khachhang3` | `khachhang3@example.com` | `OneShop@123` | Lê Hoàng Nam | Đơn hàng #5 (Pickup xong), Đơn #6 (COD) |
| **CUSTOMER** | `khachhang4` | `khachhang4@example.com` | `OneShop@123` | Trần Thị Mai | Đơn #7 (Hủy & hoàn tiền), Đơn #8 (Chờ thanh toán) |

---

## 3. Chi Tiết Từng Nhóm Tài Khoản Thử Nghiệm

### 3.1. Quản Trị Viên Hệ Thống (ADMIN - Role ID: 3)

- **Quyền hạn truy cập:** Khu vực `/admin/**`
  - Quản lý danh mục sản phẩm (`categories`), thương hiệu (`brands`), sản phẩm (`products`), cấu hình ảnh Cloudinary.
  - Quản lý mạng lưới chi nhánh cửa hàng (`stores`) và phân công nhân viên (`staff_store_assignments`).
  - Quản lý người dùng, khóa/mở tài khoản (`users`).
  - Xem báo cáo đơn hàng toàn chuỗi, đối soát cổng thanh toán Mock, xử lý các đơn thanh toán quá hạn (Payment Timeout).
  - Kiểm duyệt phản hồi và đánh giá sản phẩm.

- **Thông tin tài khoản:**
  - **Username:** `admin`
  - **Email:** `admin@oneshop.vn`
  - **Mật khẩu:** `OneShop@123`
  - **Số điện thoại:** `0900000001`
  - **Trạng thái:** `ACTIVE`

---

### 3.2. Nhân Viên Chi Nhánh (STAFF - Role ID: 2)

- **Quyền hạn truy cập:** Khu vực `/staff/**`
  - Xử lý điều phối kiện hàng gán về chi nhánh (`/staff/fulfillments/**`): Chuẩn bị hàng (`PREPARING`), Đóng gói (`PACKED`), Bàn giao vận chuyển (`SHIPPING`), Sẵn sàng nhận tại quầy (`READY_FOR_PICKUP`), Xác nhận khách nhận quầy (`COMPLETED`).
  - Quản lý kiểm kê tồn kho chi nhánh (`/staff/stock/**`) và tra cứu sổ cái biến động kho (`/staff/inventory-history/**`).
  - Trả lời đánh giá khách hàng đối với các sản phẩm do chi nhánh mình xuất kho (`/staff/reviews/**`).

- **Danh sách chi tiết theo cửa hàng:**

1. **Nhân viên OneShop Thủ Đức (Store ID: 1 - Code: `OS-THUDUC`)**
   - **Username:** `staff_thuduc`
   - **Email:** `staff.thuduc@oneshop.vn`
   - **Họ và tên:** Nguyễn Văn An
   - **Số điện thoại:** `0900000002`
   - **Cửa hàng:** 101 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM
   - **Đặc điểm:** Hỗ trợ cả Giao hàng (Delivery) và Nhận tại quầy (Pickup). Đang quản lý các kiện hàng thuộc Đơn #1, #2, #3, #4, #6.

2. **Nhân viên OneShop Gò Vấp (Store ID: 2 - Code: `OS-GOVAP`)**
   - **Username:** `staff_govap`
   - **Email:** `staff.govap@oneshop.vn`
   - **Họ và tên:** Trần Thị Bình
   - **Số điện thoại:** `0900000003`
   - **Cửa hàng:** 202 Quang Trung, P.10, Q. Gò Vấp, TP.HCM
   - **Đặc điểm:** Hỗ trợ cả Giao hàng và Nhận tại quầy. Đang xử lý các kiện hàng thuộc Đơn #2, #3, #5.

3. **Nhân viên OneShop Quận 7 (Store ID: 3 - Code: `OS-QUAN7`)**
   - **Username:** `staff_quan7`
   - **Email:** `staff.quan7@oneshop.vn`
   - **Họ và tên:** Lê Minh Châu
   - **Số điện thoại:** `0900000004`
   - **Cửa hàng:** 303 Nguyễn Thị Thập, P. Tân Phong, Quận 7, TP.HCM
   - **Đặc điểm:** Hỗ trợ cả Giao hàng và Nhận tại quầy. Đang xử lý kiện hàng thuộc Đơn #3.

4. **Nhân viên OneShop Quận 10 (Store ID: 4 - Code: `OS-QUAN10`)**
   - **Username:** `staff_quan10`
   - **Email:** `staff.quan10@oneshop.vn`
   - **Họ và tên:** Võ Hoài Dung
   - **Số điện thoại:** `0900000005`
   - **Cửa hàng:** 404 Ba Tháng Hai, P.12, Q.10, TP.HCM
   - **Đặc điểm:** **Chỉ nhận tại cửa hàng (Pickup Only)**, `delivery_enabled = 0`. Dùng để kiểm thử kịch bản hạn chế hình thức vận chuyển (Kịch bản H).

5. **Nhân viên OneShop Hoàn Kiếm (Store ID: 6 - Code: `OS-HOANKIEM`)**
   - **Username:** `staff_hoankiem`
   - **Email:** `staff.hoankiem@oneshop.vn`
   - **Họ và tên:** Hoàng Tuấn Kiệt
   - **Số điện thoại:** `0900000006`
   - **Cửa hàng:** 606 Tràng Tiền, Q. Hoàn Kiếm, Hà Nội
   - **Đặc điểm:** **Chỉ giao hàng tận nơi (Delivery Only)**, `pickup_enabled = 0`.

*(Ghi chú: Cửa hàng Store ID 5 `OS-HAICHAU` tại Đà Nẵng đang ở trạng thái `INACTIVE` để thử nghiệm kịch bản chi nhánh tạm ngưng kinh doanh, chưa gán nhân viên).*

---

### 3.3. Khách Hàng Thử Nghiệm (CUSTOMER - Role ID: 1)

- **Quyền hạn truy cập:** Khu vực mua sắm khách hàng
  - Xem sản phẩm, tìm kiếm, lọc theo thương hiệu/danh mục.
  - Quản lý giỏ hàng (`/cart/**`), đặt hàng (`/checkout/**`).
  - Theo dõi trạng thái đơn hàng cá nhân (`/orders/**`).
  - Viết đánh giá sản phẩm sau khi đơn hoàn tất (`/reviews/**`).
  - Quản lý sổ địa chỉ giao hàng (`/account/addresses` hoặc `customer_addresses`).

- **Dữ liệu có sẵn theo từng khách hàng:**

#### 1. Khách hàng 1: Phạm Thu Hà (User ID: 7)
- **Username:** `khachhang1`
- **Email:** `khachhang1@example.com`
- **Mật khẩu:** `OneShop@123`
- **Số điện thoại:** `0911111111`
- **Sổ địa chỉ:**
  - *Mặc định:* 12 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM
  - *Phụ:* 88 Nguyễn Đình Chiểu, P. Đa Kao, Quận 1, TP.HCM
- **Dữ liệu test có sẵn:**
  - **Giỏ hàng:** Có sẵn 2 món (Nước tẩy trang Bioderma Sensibio H2O + Kem dưỡng CeraVe Moisturising Cream 340g).
  - **Đơn hàng #1:** Giao hàng online 1 chi nhánh (Thủ Đức) -> Đã hoàn tất (`COMPLETED`).
  - **Đơn hàng #2:** Giao hàng online tách 2 chi nhánh (Thủ Đức & Gò Vấp) -> Trạng thái giao một phần (`PARTIALLY_COMPLETED`).
  - **Đánh giá:** Đã có 2 đánh giá sản phẩm có phản hồi từ nhân viên.

#### 2. Khách hàng 2: Đặng Quốc Khánh (User ID: 8)
- **Username:** `khachhang2`
- **Email:** `khachhang2@example.com`
- **Mật khẩu:** `OneShop@123`
- **Số điện thoại:** `0922222222`
- **Địa chỉ:** 45 Quang Trung, P.10, Q. Gò Vấp, TP.HCM
- **Dữ liệu test có sẵn:**
  - **Giỏ hàng:** Có sẵn 2 món (Son kem lì Maybelline Matte Ink 15 + Kem dưỡng Nivea Creme 150ml).
  - **Đơn hàng #3:** Đơn giao hàng tách **3 chi nhánh** (Thủ Đức, Gò Vấp, Quận 7) -> Trạng thái `PROCESSING` (các kiện đang Đóng gói, Chuẩn bị, Tiếp nhận).
  - **Đơn hàng #4:** Đơn nhận tại cửa hàng Thủ Đức (`STORE_PICKUP`) đã thanh toán online -> Trạng thái `READY_FOR_PICKUP` với **mã nhận hàng tại quầy: `PK8391`**.

#### 3. Khách hàng 3: Lê Hoàng Nam (User ID: 9)
- **Username:** `khachhang3`
- **Email:** `khachhang3@example.com`
- **Mật khẩu:** `OneShop@123`
- **Số điện thoại:** `0933333333`
- **Địa chỉ:** 303 Nguyễn Thị Thập, P. Tân Phong, Quận 7, TP.HCM
- **Dữ liệu test có sẵn:**
  - **Đơn hàng #5:** Đơn nhận tại cửa hàng Gò Vấp, thanh toán tiền mặt tại quầy (`PAY_AT_STORE`) -> Đã hoàn tất (`COMPLETED`, mã nhận `PK2746`).
  - **Đơn hàng #6:** Đơn giao hàng thanh toán khi nhận (`COD`) xuất từ chi nhánh Thủ Đức -> Trạng thái `CONFIRMED`.
  - **Đánh giá:** Đã có 1 đánh giá khen ngợi dịch vụ nhận tại quầy Gò Vấp.

#### 4. Khách hàng 4: Trần Thị Mai (User ID: 10)
- **Username:** `khachhang4`
- **Email:** `khachhang4@example.com`
- **Mật khẩu:** `OneShop@123`
- **Số điện thoại:** `0944444444`
- **Địa chỉ:** 15 Hàng Khay, P. Tràng Tiền, Q. Hoàn Kiếm, Hà Nội
- **Dữ liệu test có sẵn:**
  - **Đơn hàng #7:** Đơn hàng thanh toán online bị hủy trước khi chuẩn bị hàng -> Trạng thái `CANCELLED` & `SIMULATED_REFUNDED` (đã hoàn tiền mô phỏng 388.000đ và hoàn trả tồn kho).
  - **Đơn hàng #8:** Đơn hàng thanh toán online mới tạo -> Trạng thái chờ thanh toán (`PENDING_PAYMENT` / `UNPAID`). Thích hợp để test luồng cổng thanh toán Mock hoặc kiểm tra cơ chế worker hủy đơn quá hạn 15 phút.

---

## 4. Bảng Tra Cứu Dữ Liệu Đơn Hàng Mẫu (Orders & Fulfillments)

| Mã Đơn (#) | Khách hàng | Hình thức (Fulfillment) | Thanh toán | Tổng tiền | Trạng thái Đơn | Tình trạng Kiện hàng (Fulfillment) |
| :---: | :--- | :--- | :--- | :---: | :--- | :--- |
| **#1** | `khachhang1` | DELIVERY (Thủ Đức) | ONLINE (Paid) | 524.000đ | `COMPLETED` | Kiện #1: `COMPLETED` (Giao thành công) |
| **#2** | `khachhang1` | DELIVERY (Thủ Đức + Gò Vấp) | ONLINE (Paid) | 1.076.000đ | `PARTIALLY_COMPLETED` | Kiện #2 (Thủ Đức): `COMPLETED`<br>Kiện #3 (Gò Vấp): `SHIPPING` |
| **#3** | `khachhang2` | DELIVERY (Thủ Đức + Gò Vấp + Q7)| ONLINE (Paid) | 1.745.000đ | `PROCESSING` | Kiện #4 (Thủ Đức): `PACKED`<br>Kiện #5 (Gò Vấp): `PREPARING`<br>Kiện #6 (Quận 7): `CONFIRMED` |
| **#4** | `khachhang2` | STORE_PICKUP (Thủ Đức) | ONLINE (Paid) | 340.000đ | `PROCESSING` | Kiện #7: `READY_FOR_PICKUP`<br>*(Mã nhận: `PK8391`)* |
| **#5** | `khachhang3` | STORE_PICKUP (Gò Vấp) | PAY_AT_STORE (Paid) | 142.000đ | `COMPLETED` | Kiện #8: `COMPLETED`<br>*(Mã nhận: `PK2746`)* |
| **#6** | `khachhang3` | DELIVERY (Thủ Đức) | COD (Unpaid) | 306.000đ | `CONFIRMED` | Kiện #9: `CONFIRMED` |
| **#7** | `khachhang4` | DELIVERY (Thủ Đức + Gò Vấp) | ONLINE (Refunded) | 388.000đ | `CANCELLED` | Kiện #10, #11: `CANCELLED`<br>*(Đã hoàn tồn kho & hoàn tiền mô phỏng)* |
| **#8** | `khachhang4` | DELIVERY (Thủ Đức) | ONLINE (Unpaid) | 400.000đ | `PENDING_PAYMENT` | Kiện #12: `CONFIRMED`<br>*(Chờ thanh toán)* |

