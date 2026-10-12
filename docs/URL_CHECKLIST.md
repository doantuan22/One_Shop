# Checklist màn hình và URL – OneShop
Vai trò	Email
ADMIN	admin@oneshop.vn
STAFF	staff.thuduc@oneshop.vn
STAFF	staff.govap@oneshop.vn
STAFF	staff.quan7@oneshop.vn
STAFF	staff.quan10@oneshop.vn
CUSTOMER	khachhang1@example.com
CUSTOMER	khachhang2@example.com
mk OneShop@123
> Được lập từ source trong ZIP dự án. Đường dẫn `{id}`, `{orderId}`, `{checkoutId}` là tham số động. URL ghi theo mapping trong controller, chưa phải kết quả kiểm thử chạy ứng dụng.
>
> Đánh dấu `x` ở cột đầu sau khi đã mở và kiểm thử màn hình tương ứng.

| Kiểm tra | Nhóm | Màn hình | File code / template | URL |
|---|---|---|---|---|
| done | Khách hàng | Trang chủ | `src/main/java/com/oneshop/controller/client/HomeController.java`<br>`src/main/resources/templates/home/index.html` | `GET /` |
| done | Khách hàng | Đăng nhập | `src/main/java/com/oneshop/controller/client/AuthController.java`<br>`src/main/resources/templates/auth/login.html` | `GET /login` |
| done | Khách hàng | Đăng ký | `src/main/java/com/oneshop/controller/client/AuthController.java`<br>`src/main/resources/templates/auth/register.html` | `GET /register` |
| done | Khách hàng | Danh sách sản phẩm | `src/main/java/com/oneshop/controller/client/ProductController.java`<br>`src/main/resources/templates/product/list.html` | `GET /products` |
| done | Khách hàng | Chi tiết sản phẩm | `src/main/java/com/oneshop/controller/client/ProductController.java`<br>`src/main/resources/templates/product/detail.html` | `GET /products/{id}` |
| done | Khách hàng | Danh sách chi nhánh | `src/main/java/com/oneshop/controller/client/StoreController.java`<br>`src/main/resources/templates/store/list.html` | `GET /stores` |
| done | Khách hàng | Giỏ hàng | `src/main/java/com/oneshop/controller/client/CartController.java`<br>`src/main/resources/templates/cart/index.html` | `GET /cart` |
| done | Khách hàng | Thanh toán | `src/main/java/com/oneshop/controller/client/CheckoutController.java`<br>`src/main/resources/templates/checkout/index.html` | `GET /checkout` |
| done | Khách hàng | Kết quả thanh toán/đặt hàng | `src/main/java/com/oneshop/controller/client/CheckoutController.java`<br>`src/main/resources/templates/checkout/result.html` | `GET /checkout/{checkoutId}` |
| ☐ | Khách hàng | Lịch sử đơn hàng | `src/main/java/com/oneshop/controller/client/CustomerOrderController.java`<br>`src/main/resources/templates/orders/index.html` | `GET /orders` |
| ☐ | Khách hàng | Chi tiết đơn hàng | `src/main/java/com/oneshop/controller/client/CustomerOrderController.java`<br>`src/main/resources/templates/orders/detail.html` | `GET /orders/{orderId}` |
| ☐ | Khách hàng | Thanh toán đơn hàng | `src/main/java/com/oneshop/controller/client/PaymentController.java`<br>`src/main/resources/templates/payment/index.html` | `GET /orders/{orderId}/payments` |
| done | Quản trị | Dashboard quản trị | `src/main/java/com/oneshop/controller/admin/AdminDashboardController.java`<br>`src/main/resources/templates/admin/dashboard.html` | `GET /admin` |
| ☐ | Quản trị | Quản lý sản phẩm | `src/main/java/com/oneshop/controller/admin/AdminProductController.java`<br>`src/main/resources/templates/admin/products.html` | `GET /admin/products` |
| ☐ | Quản trị | Thêm sản phẩm | `src/main/java/com/oneshop/controller/admin/AdminProductController.java`<br>`src/main/resources/templates/admin/product-form.html` | `GET /admin/products/new` |
| ☐ | Quản trị | Sửa sản phẩm | `src/main/java/com/oneshop/controller/admin/AdminProductController.java`<br>`src/main/resources/templates/admin/product-form.html` | `GET /admin/products/{id}/edit` |
| ☐ | Quản trị | Quản lý thương hiệu | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java`<br>`src/main/resources/templates/admin/brands.html (xác minh view helper)` | `GET /admin/brands` |
| ☐ | Quản trị | Sửa thương hiệu | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java`<br>`src/main/resources/templates/admin/brands.html (xác minh view helper)` | `GET /admin/brands/{id}/edit` |
| ☐ | Quản trị | Quản lý danh mục | `src/main/java/com/oneshop/controller/admin/AdminCategoryController.java`<br>`src/main/resources/templates/admin/categories.html (xác minh view helper)` | `GET /admin/categories` |
| ☐ | Quản trị | Sửa danh mục | `src/main/java/com/oneshop/controller/admin/AdminCategoryController.java`<br>`src/main/resources/templates/admin/categories.html (xác minh view helper)` | `GET /admin/categories/{id}/edit` |
| done | Quản trị | Quản lý chi nhánh | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java`<br>`src/main/resources/templates/admin/stores.html` | `GET /admin/stores` |
| done | Quản trị | Thêm chi nhánh | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java`<br>`src/main/resources/templates/admin/store-form.html` | `GET /admin/stores/new` |
| done | Quản trị | Sửa chi nhánh | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java`<br>`src/main/resources/templates/admin/store-form.html` | `GET /admin/stores/{id}/edit` |
| done | Quản trị | Sản phẩm theo chi nhánh / tồn kho | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java`<br>`src/main/resources/templates/admin/store-products.html` | `GET /admin/store-products` |
| done | Quản trị | Thêm sản phẩm vào chi nhánh | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java`<br>`src/main/resources/templates/admin/store-product-form.html` | `GET /admin/store-products/new` |
| done | Quản trị | Sửa sản phẩm theo chi nhánh | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java`<br>`src/main/resources/templates/admin/store-product-form.html` | `GET /admin/store-products/{id}/edit` |
| done | Quản trị | Kho / tồn kho (đường dẫn phụ) | `src/main/java/com/oneshop/controller/admin/AdminInventoryController.java`<br>`src/main/resources/templates/admin/store-products.html` | `GET /admin/inventory` |
| done | Quản trị | Quản lý đơn hàng | `src/main/java/com/oneshop/controller/admin/AdminOrderController.java`<br>`src/main/resources/templates/admin/orders.html` | `GET /admin/orders` |
| done | Quản trị | Chi tiết đơn hàng quản trị | `src/main/java/com/oneshop/controller/admin/AdminOrderController.java`<br>`src/main/resources/templates/admin/order-detail.html` | `GET /admin/orders/{id}` |
| done | Quản trị | Đánh giá sản phẩm | `src/main/java/com/oneshop/controller/admin/AdminReviewController.java`<br>`src/main/resources/templates/admin/reviews.html` | `GET /admin/reviews` |
| done | Quản trị | Phân công nhân viên | `src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java`<br>`src/main/resources/templates/admin/staff-assignments.html` | `GET /admin/staff-assignments` |
| done | Quản trị | Quản lý người dùng | `src/main/java/com/oneshop/controller/admin/AdminUserController.java`<br>`src/main/resources/templates/admin/users.html` | `GET /admin/users` |
| done | Quản trị | Thêm người dùng | `src/main/java/com/oneshop/controller/admin/AdminUserController.java`<br>`src/main/resources/templates/admin/user-form.html` | `GET /admin/users/new` |
| done | Quản trị | Sửa người dùng | `src/main/java/com/oneshop/controller/admin/AdminUserController.java`<br>`src/main/resources/templates/admin/user-form.html` | `GET /admin/users/{id}/edit` |
| done | Nhân viên | Dashboard nhân viên | `src/main/java/com/oneshop/controller/staff/StaffDashboardController.java`<br>`src/main/resources/templates/staff/dashboard.html` | `GET /staff hoặc /staff/` |
| done | Nhân viên | Dashboard nhân viên (alias) | `src/main/java/com/oneshop/controller/staff/StaffDashboardController.java`<br>`src/main/resources/templates/staff/dashboard.html` | `GET /staff/dashboard` |
| done | Nhân viên | Danh sách đơn hàng | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java`<br>`src/main/resources/templates/staff/orders/index.html` | `GET /staff/orders` |
| done | Nhân viên | Chi tiết đơn hàng nhân viên | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java`<br>`src/main/resources/templates/staff/orders/detail.html` | `GET /staff/orders/{orderId}` |
| done | Nhân viên | Đơn giao hàng | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java`<br>`src/main/resources/templates/staff/delivery/index.html` | `GET /staff/orders/delivery` |
| done | Nhân viên | Chi tiết giao hàng | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java`<br>`src/main/resources/templates/staff/delivery/detail.html` | `GET /staff/orders/delivery/{orderId}` |
| done | Nhân viên | Đơn nhận tại cửa hàng | `src/main/java/com/oneshop/controller/staff/StaffPickupController.java`<br>`src/main/resources/templates/staff/pickup/index.html` | `GET /staff/orders/pickup` |
| done | Nhân viên | Chi tiết nhận tại cửa hàng | `src/main/java/com/oneshop/controller/staff/StaffPickupController.java`<br>`src/main/resources/templates/staff/pickup/detail.html` | `GET /staff/orders/pickup/{orderId}` |
| done | Nhân viên | Hàng chờ nhận tại cửa hàng | `src/main/java/com/oneshop/controller/staff/StaffPickupQueueController.java`<br>`src/main/resources/templates/staff/pickup/queue.html` | `GET /staff/pickup` |
| done | Nhân viên | Quản lý tồn kho | `src/main/java/com/oneshop/controller/staff/StaffStockController.java`<br>`src/main/resources/templates/staff/stock/index.html` | `GET /staff/stock` |
| done | Nhân viên | Lịch sử biến động kho | `src/main/java/com/oneshop/controller/staff/StaffStockController.java`<br>`src/main/resources/templates/staff/stock/index.html` | `GET /staff/inventory-history` |
| done | Nhân viên | Điều chỉnh tồn kho | `src/main/java/com/oneshop/controller/staff/StaffStockController.java`<br>`src/main/resources/templates/staff/stock/adjust.html` | `GET /staff/stock/{id}/adjust` |
| done | Nhân viên | Chi tiết lịch sử kho | `src/main/java/com/oneshop/controller/staff/StaffStockController.java`<br>`src/main/resources/templates/staff/stock/history.html` | `GET /staff/inventory-history/{id}` |
| ☐ | Hệ thống/API | Health check | `src/main/java/com/oneshop/controller/api/HealthController.java`<br>`Không có template (API/health response)` | `GET /health` |
| ☐ | Hệ thống/API | API đăng nhập | `src/main/java/com/oneshop/controller/api/AuthApiController.java`<br>`Không có template (JSON API)` | `POST /api/auth/login` |
| ☐ | Hệ thống/API | API đăng ký | `src/main/java/com/oneshop/controller/api/AuthApiController.java`<br>`Không có template (JSON API)` | `POST /api/auth/register` |

## Ghi chú

- Các route `POST` dùng để gửi form/thực hiện thao tác (thêm, sửa, xóa, chọn chi nhánh, cập nhật trạng thái đơn hàng...) không được liệt kê như màn hình riêng vì không phải trang giao diện `GET`.
- Các URL có tham số động cần kiểm tra bằng ID hợp lệ trong dữ liệu.
- **Cần xác minh thêm:** trang thương hiệu và danh mục dùng helper `page(...)` trong controller; template được đánh dấu “xác minh view helper” cần đối chiếu phần triển khai helper trước khi coi là đã khớp tuyệt đối.
- File layout/fragment dùng chung (header, navbar, footer, layouts) không phải URL độc lập nên không liệt kê thành màn hình riêng.
