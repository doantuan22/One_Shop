# Truy vết chức năng – mã nguồn – sơ đồ – giao diện

Mỗi dòng nối một chức năng với các đoạn mã được trích trong báo cáo (đường dẫn tính từ src/main/java/com/oneshop/, kèm số dòng), tệp sơ đồ hoạt động và ảnh giao diện chụp từ website chạy thật.


## Customer

### KH-01. Đăng ký tài khoản

- Controller: AuthController (GET/POST /register), AuthApiController (POST /api/auth/register)
- Service: AuthServiceImpl.register
- Dữ liệu: User, Role; UserRepository, RoleRepository
- Giao diện (template): templates/auth/register.html
- Sơ đồ: `diagrams/customer/KH-01.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/AuthServiceImpl.java` dòng 70–87: Kiểm tra dữ liệu đăng ký
  - `src/main/java/com/oneshop/service/impl/AuthServiceImpl.java` dòng 88–106: Tạo tài khoản CUSTOMER
- Ảnh giao diện: `screenshots/customer/C05_annotated.png` (/register, Giao diện đăng ký tài khoản)

### KH-02. Đăng nhập và đăng xuất

- Controller: AuthController (GET/POST /login), AuthApiController (POST /api/auth/login), cấu hình logout trong SecurityConfig
- Service: AuthServiceImpl.login, JwtService, JwtAuthenticationFilter, CustomUserDetailsService
- Dữ liệu: User, Role; UserRepository
- Giao diện (template): templates/auth/login.html
- Sơ đồ: `diagrams/customer/KH-02.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/AuthController.java` dòng 37–51: Đăng nhập: AuthController
  - `src/main/java/com/oneshop/service/impl/AuthServiceImpl.java` dòng 54–66: Xác thực và sinh JWT
  - `src/main/java/com/oneshop/security/jwt/JwtAuthenticationFilter.java` dòng 43–64: Xác thực mỗi request bằng JWT
- Ảnh giao diện: `screenshots/customer/C04_annotated.png` (/login, Giao diện đăng nhập)

### KH-03. Quên mật khẩu bằng mã OTP

- Controller: PasswordResetController (/forgot-password, /forgot-password/verify, /forgot-password/reset)
- Service: PasswordResetService, OtpMailSender
- Dữ liệu: PasswordResetOtp, User; PasswordResetOtpRepository
- Giao diện (template): templates/auth/forgot-password.html, verify-otp.html, reset-password.html
- Sơ đồ: `diagrams/customer/KH-03.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/PasswordResetService.java` dòng 75–98: Bước 1: gửi mã OTP
  - `src/main/java/com/oneshop/service/PasswordResetService.java` dòng 108–139: Bước 2: xác minh mã OTP
  - `src/main/java/com/oneshop/service/PasswordResetService.java` dòng 148–169: Bước 3: đặt mật khẩu mới
- Ảnh giao diện: `screenshots/customer/C06_annotated.png` (/forgot-password, Giao diện quên mật khẩu (bước gửi mã OTP))

### KH-04. Xem danh sách, tìm kiếm và lọc sản phẩm

- Controller: ProductController (GET /products, /products/suggest)
- Service: ProductServiceImpl.searchProducts, StoreProductServiceImpl.getChainCatalog / getStoreCatalog
- Dữ liệu: Product, ProductImage, StoreProduct, Category, Brand; ProductRepository, StoreProductRepository
- Giao diện (template): templates/product/list.html
- Sơ đồ: `diagrams/customer/KH-04.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/ProductController.java` dòng 52–69: ProductController.list
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 78–91: Tìm kiếm sản phẩm hiển thị
- Ảnh giao diện: `screenshots/customer/C07_annotated.png` (/products, Giao diện danh sách sản phẩm, tìm kiếm và bộ lọc), `screenshots/customer/C01_annotated.png` (/, Giao diện trang chủ (phần đầu trang)), `screenshots/customer/C02_annotated.png` (/, Giao diện trang chủ (sản phẩm nổi bật và thương hiệu)), `screenshots/customer/C03_annotated.png` (/, Giao diện trang chủ (hệ thống cửa hàng và chân trang))

### KH-05. Xem chi tiết sản phẩm và tình trạng còn hàng theo chi nhánh

- Controller: ProductController.detail (GET /products/{id})
- Service: StoreProductServiceImpl.getProductDetail, ProductServiceImpl.getProduct, ReviewServiceImpl.getProductReviews
- Dữ liệu: Product, ProductImage, StoreProduct, Review; StoreProductRepository, ReviewRepository
- Giao diện (template): templates/product/detail.html
- Sơ đồ: `diagrams/customer/KH-05.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/ProductController.java` dòng 71–78: ProductController.detail
  - `src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java` dòng 152–165: Chi tiết sản phẩm và tồn theo chi nhánh
- Ảnh giao diện: `screenshots/customer/C08_annotated.png` (/products/1, Giao diện chi tiết sản phẩm)

### KH-06. Tra cứu và chọn chi nhánh

- Controller: StoreController (GET /stores, POST /stores/select, /stores/clear)
- Service: StoreServiceImpl.findStores / requireSelectableStore, SelectedStoreCookie
- Dữ liệu: Store; StoreRepository
- Giao diện (template): templates/store/list.html
- Sơ đồ: `diagrams/customer/KH-06.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/StoreController.java` dòng 42–51: Chọn chi nhánh
  - `src/main/java/com/oneshop/service/impl/StoreServiceImpl.java` dòng 50–58: Tra cứu chi nhánh theo tỉnh/thành, khu vực
- Ảnh giao diện: `screenshots/customer/C09_annotated.png` (/stores, Giao diện tra cứu hệ thống cửa hàng)

### KH-07. Thêm sản phẩm vào giỏ hàng

- Controller: CartController.add (POST /cart/items)
- Service: CartServiceImpl.addItem / doAddItem
- Dữ liệu: Cart, CartItem, Product, StoreProduct; CartRepository, CartItemRepository
- Giao diện (template): Nút 'Thêm vào giỏ' trong templates/product/list.html, detail.html; templates/cart/index.html
- Sơ đồ: `diagrams/customer/KH-07.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/CartController.java` dòng 43–55: CartController.add
  - `src/main/java/com/oneshop/service/impl/CartServiceImpl.java` dòng 164–198: Khóa giỏ, kiểm tra tồn, lưu dòng hàng
- Ảnh giao diện: `screenshots/customer/C08_annotated.png` (/products/1, Giao diện chi tiết sản phẩm), `screenshots/customer/C10_annotated.png` (/cart, Giao diện giỏ hàng)

### KH-08. Cập nhật số lượng và xóa sản phẩm trong giỏ hàng

- Controller: CartController.update, remove (POST /cart/items/{id}, /cart/items/{id}/delete)
- Service: CartServiceImpl.updateItemQuantity, removeItem
- Dữ liệu: CartItem; CartItemRepository
- Giao diện (template): templates/cart/index.html
- Sơ đồ: `diagrams/customer/KH-08.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/CartServiceImpl.java` dòng 214–248: Cập nhật và xóa dòng hàng
- Ảnh giao diện: `screenshots/customer/C10_annotated.png` (/cart, Giao diện giỏ hàng)

### KH-09. Xem trước đơn hàng và áp mã khuyến mãi

- Controller: CheckoutController.form (GET /checkout)
- Service: CheckoutServiceImpl.preview, StoreAllocationServiceImpl.preview, PromotionService
- Dữ liệu: Cart, CartItem, StoreProduct, Promotion, CustomerAddress; CheckoutProperties
- Giao diện (template): templates/checkout/index.html
- Sơ đồ: `diagrams/customer/KH-09.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java` dòng 203–236: CheckoutServiceImpl.preview: chọn nhánh xử lý
  - `src/main/java/com/oneshop/service/promotion/PromotionService.java` dòng 158–178: PromotionService.problem: điều kiện mã khuyến mãi
- Ảnh giao diện: `screenshots/customer/C11_annotated.png` (/checkout, Giao diện xác nhận đặt hàng)

### KH-10. Đặt hàng (phân bổ nguồn hàng, tạo Order và Fulfillment)

- Controller: CheckoutController.placeOrder (POST /checkout), result (GET /checkout/{id})
- Service: CheckoutServiceImpl.placeOrder / createOrder, StoreAllocationServiceImpl.allocateForUpdate, AllocationEngine, InventoryServiceImpl.deductForOrder, PromotionService
- Dữ liệu: CheckoutSession, Order, OrderFulfillment, OrderItem, OrderStatusHistory, OrderFulfillmentStatusHistory, InventoryMovement, StoreProduct, Payment
- Giao diện (template): templates/checkout/index.html, checkout/result.html
- Sơ đồ: `diagrams/customer/KH-10.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java` dòng 312–345: Khóa giỏ, kiểm tra SKU và phân bổ
  - `src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java` dòng 405–424: Tạo phiên thanh toán và các Order
  - `src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java` dòng 461–484: Tạo Fulfillment, OrderItem và trừ tồn
  - `src/main/java/com/oneshop/service/allocation/AllocationEngine.java` dòng 52–66: AllocationEngine: ưu tiên một chi nhánh đủ giỏ
- Ảnh giao diện: `screenshots/customer/C11_annotated.png` (/checkout, Giao diện xác nhận đặt hàng), `screenshots/customer/C12_annotated.png` (/checkout/1, Giao diện kết quả đặt hàng)

### KH-11. Thanh toán trực tuyến qua cổng mô phỏng

- Controller: PaymentController (GET /orders/{id}/payments, POST .../attempts, .../simulate)
- Service: PaymentServiceImpl.createOnlinePaymentAttempt, PaymentReconciliationService.simulate / reconcileLocked, MockPaymentGateway, MockLedger
- Dữ liệu: Payment, Order, OrderStatusHistory; PaymentRepository
- Giao diện (template): templates/payment/index.html
- Sơ đồ: `diagrams/customer/KH-11.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/PaymentController.java` dòng 46–53: PaymentController: bắt đầu thanh toán
  - `src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java` dòng 97–129: PaymentReconciliationService.simulate
  - `src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java` dòng 167–194: reconcileLocked: bảng quyết định
- Ảnh giao diện: `screenshots/customer/C18_annotated.png` (/orders/13/payments, Giao diện thanh toán đơn hàng trực tuyến)
- Ghi chú về ảnh giao diện: Nút 'Thanh toán' chỉ hiện với đơn ONLINE đang chờ thanh toán; ảnh chụp là trang của một đơn đã thanh toán.

### KH-12. Theo dõi đơn hàng

- Controller: CustomerOrderController (GET /orders, /orders/{id})
- Service: CustomerOrderServiceImpl.getOrders / getOrder, OrderViewService, OrderTransitionPolicy
- Dữ liệu: Order, OrderItem, OrderFulfillment, OrderStatusHistory, Payment
- Giao diện (template): templates/orders/index.html, orders/detail.html
- Sơ đồ: `diagrams/customer/KH-12.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/CustomerOrderController.java` dòng 51–80: CustomerOrderController.detail
  - `src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java` dòng 69–74: Lấy đơn của chính khách
- Ảnh giao diện: `screenshots/customer/C13_annotated.png` (/orders, Giao diện lịch sử đơn hàng), `screenshots/customer/C14_annotated.png` (/orders/13, Giao diện chi tiết đơn hàng nhiều chi nhánh (phần đầu)), `screenshots/customer/C16_annotated.png` (/orders/1, Giao diện chi tiết đơn hàng đã hoàn tất (trả hàng))

### KH-13. Hủy đơn hàng

- Controller: CustomerOrderController.cancel (POST /orders/{id}/cancel)
- Service: CustomerOrderServiceImpl.cancelOrder, CustomerOrderCancellationService.cancel, OrderCancellationService.cancelAndRestore, RefundService.simulate, InventoryServiceImpl.restoreForCancelledOrder
- Dữ liệu: Order, OrderFulfillment, InventoryMovement, Payment, PaymentRefund
- Giao diện (template): Nút 'Hủy đơn' trong templates/orders/detail.html
- Sơ đồ: `diagrams/customer/KH-13.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/payment/CustomerOrderCancellationService.java` dòng 56–88: CustomerOrderCancellationService.cancel (1)
  - `src/main/java/com/oneshop/service/payment/CustomerOrderCancellationService.java` dòng 89–115: CustomerOrderCancellationService.cancel (2)
  - `src/main/java/com/oneshop/service/payment/OrderCancellationService.java` dòng 65–86: OrderCancellationService.cancelAndRestore
- Ảnh giao diện: `screenshots/customer/C14_annotated.png` (/orders/13, Giao diện chi tiết đơn hàng nhiều chi nhánh (phần đầu)), `screenshots/customer/C16_annotated.png` (/orders/1, Giao diện chi tiết đơn hàng đã hoàn tất (trả hàng))
- Ghi chú về ảnh giao diện: Nút Hủy đơn chỉ xuất hiện với đơn còn hủy được; các đơn dữ liệu mẫu của khách thử nghiệm đều đã giao hoặc đã hủy nên nút này không có trên ảnh chụp.

### KH-14. Yêu cầu trả hàng sau khi nhận

- Controller: CustomerOrderReturnController (GET/POST /orders/{id}/return)
- Service: CustomerOrderReturnService.request / plan
- Dữ liệu: OrderReturn, OrderReturnItem, OrderReturnImage; ImageTransactionCoordinator (Cloudinary)
- Giao diện (template): templates/orders/return-form.html (liên kết từ orders/detail.html)
- Sơ đồ: `diagrams/customer/KH-14.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/returns/CustomerOrderReturnService.java` dòng 128–148: Ghi nhận yêu cầu trả hàng
  - `src/main/java/com/oneshop/service/returns/CustomerOrderReturnService.java` dòng 181–206: Điều kiện trả hàng (1)
  - `src/main/java/com/oneshop/service/returns/CustomerOrderReturnService.java` dòng 207–226: Điều kiện trả hàng (2)
- Ảnh giao diện: `screenshots/customer/C16_annotated.png` (/orders/1, Giao diện chi tiết đơn hàng đã hoàn tất (trả hàng)), `screenshots/customer/C17_annotated.png` (/orders/1/return, Giao diện yêu cầu trả hàng)

### KH-15. Đánh giá sản phẩm đã nhận

- Controller: ReviewController.submit (POST /reviews)
- Service: ReviewServiceImpl.submit, ReviewSource
- Dữ liệu: Review, OrderItem, Order; ReviewRepository
- Giao diện (template): Biểu mẫu đánh giá trong templates/orders/detail.html và product/detail.html
- Sơ đồ: `diagrams/customer/KH-15.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java` dòng 62–97: ReviewServiceImpl.submit
- Ảnh giao diện: `screenshots/customer/C15_annotated.png` (/orders/13, Giao diện chi tiết đơn hàng (đánh giá sản phẩm))

### KH-16. Cập nhật thông tin tài khoản

- Controller: AccountController (GET/POST /account)
- Service: AccountProfileService.update
- Dữ liệu: User; UserRepository.updateProfileFields
- Giao diện (template): templates/account/profile.html
- Sơ đồ: `diagrams/customer/KH-16.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/AccountController.java` dòng 58–72: AccountController.updateProfile
  - `src/main/java/com/oneshop/service/AccountProfileService.java` dòng 57–64: AccountProfileService.update
- Ảnh giao diện: `screenshots/customer/C19_annotated.png` (/account, Giao diện thông tin tài khoản)

### KH-17. Đổi mật khẩu

- Controller: AccountController.changePassword (POST /account/password)
- Service: AccountProfileService.changePassword
- Dữ liệu: User; UserRepository.updatePasswordHash
- Giao diện (template): templates/account/profile.html (khối Đổi mật khẩu)
- Sơ đồ: `diagrams/customer/KH-17.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/client/AccountController.java` dòng 75–96: AccountController.changePassword
  - `src/main/java/com/oneshop/service/AccountProfileService.java` dòng 36–53: AccountProfileService.changePassword
- Ảnh giao diện: `screenshots/customer/C19_annotated.png` (/account, Giao diện thông tin tài khoản)


## Admin

### QT-01. Quản lý tài khoản người dùng

- Controller: AdminUserController (/admin/users, /admin/users/new, /admin/users/{id}/edit)
- Service: AdminUserService.searchUsers / create / update
- Dữ liệu: User, Role, StaffStoreAssignment
- Giao diện (template): templates/admin/users.html, admin/user-form.html
- Sơ đồ: `diagrams/admin/QT-01.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/AdminUserService.java` dòng 87–105: AdminUserService.create
  - `src/main/java/com/oneshop/service/AdminUserService.java` dòng 108–134: AdminUserService.update
- Ảnh giao diện: `screenshots/admin/A02_annotated.png` (/admin/users, Giao diện quản lý người dùng), `screenshots/admin/A03_annotated.png` (/admin/users/new, Giao diện tạo tài khoản người dùng)

### QT-02. Phân công và chuyển chi nhánh cho nhân viên

- Controller: AdminStaffAssignmentController (/admin/staff-assignments, /transfer, /{id}/status)
- Service: AdminStaffAssignmentService.assign / setStatus / transfer
- Dữ liệu: StaffStoreAssignment, User, Store
- Giao diện (template): templates/admin/staff-assignments.html
- Sơ đồ: `diagrams/admin/QT-02.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/AdminStaffAssignmentService.java` dòng 63–80: AdminStaffAssignmentService.assign
  - `src/main/java/com/oneshop/service/AdminStaffAssignmentService.java` dòng 105–132: AdminStaffAssignmentService.transfer
  - `src/main/java/com/oneshop/service/AdminStaffAssignmentService.java` dòng 135–144: Một nhân viên chỉ làm việc tại một chi nhánh
- Ảnh giao diện: `screenshots/admin/A04_annotated.png` (/admin/staff-assignments, Giao diện phân công nhân viên cho chi nhánh)

### QT-03. Quản lý chi nhánh

- Controller: AdminStoreController (/admin/stores, /new, /{id}/edit)
- Service: StoreServiceImpl.searchStores / createStore / updateStore
- Dữ liệu: Store; StoreRepository
- Giao diện (template): templates/admin/stores.html, admin/store-form.html
- Sơ đồ: `diagrams/admin/QT-03.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/StoreServiceImpl.java` dòng 133–166: StoreServiceImpl.createStore, updateStore
- Ảnh giao diện: `screenshots/admin/A05_annotated.png` (/admin/stores, Giao diện quản lý chi nhánh), `screenshots/admin/A06_annotated.png` (/admin/stores/new, Giao diện thêm chi nhánh)

### QT-04. Quản lý danh mục và thương hiệu

- Controller: AdminCategoryController (/admin/categories), AdminBrandController (/admin/brands)
- Service: ProductServiceImpl.createCategory / updateCategory / createBrand / updateBrand / upload*
- Dữ liệu: Category, Brand; CategoryRepository, BrandRepository; Cloudinary
- Giao diện (template): templates/admin/categories.html, admin/brands.html
- Sơ đồ: `diagrams/admin/QT-04.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 163–177: Tạo danh mục
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 187–204: Sửa danh mục và thay ảnh
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 252–272: Tạo và sửa thương hiệu
- Ảnh giao diện: `screenshots/admin/A07_annotated.png` (/admin/categories, Giao diện quản lý danh mục), `screenshots/admin/A08_annotated.png` (/admin/brands, Giao diện quản lý thương hiệu)

### QT-05. Quản lý sản phẩm và giá bán

- Controller: AdminProductController (/admin/products, /new, /{id}/edit)
- Service: ProductServiceImpl.searchAllProducts / createProduct / updateProduct
- Dữ liệu: Product, Category, Brand; ProductRepository
- Giao diện (template): templates/admin/products.html, admin/product-form.html
- Sơ đồ: `diagrams/admin/QT-05.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 352–372: Tạo và cập nhật sản phẩm
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 374–398: Kiểm tra giá, danh mục, thương hiệu
- Ảnh giao diện: `screenshots/admin/A09_annotated.png` (/admin/products, Giao diện quản lý sản phẩm), `screenshots/admin/A10_annotated.png` (/admin/products/1/edit, Giao diện sửa sản phẩm và quản lý hình ảnh)

### QT-06. Quản lý hình ảnh sản phẩm

- Controller: AdminProductController (POST /admin/products/{id}/images, .../primary, .../delete)
- Service: ProductServiceImpl.addProductImage / setPrimaryProductImage / deleteProductImage, ImageTransactionCoordinator, CloudinaryServiceImpl
- Dữ liệu: ProductImage; bảng image_cleanup_tasks (ImageCleanupService); Cloudinary
- Giao diện (template): Khối ảnh trong templates/admin/product-form.html
- Sơ đồ: `diagrams/admin/QT-06.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 409–434: Thêm ảnh và đặt ảnh chính
  - `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java` dòng 438–451: Xóa ảnh
- Ảnh giao diện: `screenshots/admin/A10_annotated.png` (/admin/products/1/edit, Giao diện sửa sản phẩm và quản lý hình ảnh)

### QT-07. Quản lý tồn kho toàn chuỗi (gắn sản phẩm vào chi nhánh)

- Controller: AdminStoreProductController (/admin/store-products), AdminInventoryController (GET /admin/inventory)
- Service: StoreProductServiceImpl.createStoreProduct / updateStoreProduct / searchInventory, InventoryServiceImpl.adjustStock
- Dữ liệu: StoreProduct, InventoryMovement
- Giao diện (template): templates/admin/store-products.html, store-product-form.html, inventory.html
- Sơ đồ: `diagrams/admin/QT-07.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java` dòng 246–283: Gắn sản phẩm vào chi nhánh, cập nhật
  - `src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java` dòng 48–75: InventoryServiceImpl.adjustStock
- Ảnh giao diện: `screenshots/admin/A11_annotated.png` (/admin/store-products, Giao diện sản phẩm theo chi nhánh), `screenshots/admin/A12_annotated.png` (/admin/store-products/new, Giao diện gắn sản phẩm vào chi nhánh), `screenshots/admin/A13_annotated.png` (/admin/inventory, Giao diện tồn kho toàn chuỗi)

### QT-08. Quản lý đơn hàng toàn chuỗi

- Controller: AdminOrderController (GET /admin/orders, /admin/orders/{id})
- Service: AdminOperationsService.getOrders / getOrder / getOrdersNeedingAttention
- Dữ liệu: Order, OrderFulfillment, Payment, OrderReturn
- Giao diện (template): templates/admin/orders.html, admin/order-detail.html
- Sơ đồ: `diagrams/admin/QT-08.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/admin/AdminOrderController.java` dòng 32–67: AdminOrderController
  - `src/main/java/com/oneshop/service/AdminOperationsService.java` dòng 41–57: AdminOperationsService.getOrders, getOrder
- Ảnh giao diện: `screenshots/admin/A14_annotated.png` (/admin/orders, Giao diện quản lý đơn hàng), `screenshots/admin/A15_annotated.png` (/admin/orders/13, Giao diện chi tiết đơn hàng của quản trị viên)

### QT-09. Xử lý ngoại lệ giao hàng thất bại

- Controller: AdminFulfillmentExceptionController (POST /admin/orders/{orderId}/fulfillments/{id}/redeliver, /undeliverable)
- Service: AdminFulfillmentExceptionService.redeliver / markUndeliverable, InventoryServiceImpl.restoreForUndeliverableFulfillment, PartialRefundService
- Dữ liệu: Order, OrderFulfillment, OrderFulfillmentStatusHistory, InventoryMovement, PaymentRefund
- Giao diện (template): Khối ngoại lệ trong templates/admin/order-detail.html
- Sơ đồ: `diagrams/admin/QT-09.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java` dòng 222–253: Quyết định giao lại
  - `src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java` dòng 258–294: Xác nhận không giao được (1)
  - `src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java` dòng 295–317: Xác nhận không giao được (2)
- Ảnh giao diện: `screenshots/admin/A15_annotated.png` (/admin/orders/13, Giao diện chi tiết đơn hàng của quản trị viên)
- Ghi chú về ảnh giao diện: Khối xử lý ngoại lệ trong chi tiết đơn chỉ hiện khi đơn NEEDS_ATTENTION; dữ liệu mẫu không có đơn như vậy nên giao diện này không được chụp.

### QT-10. Quản lý thanh toán ONLINE: đối soát, chốt cổng, hoàn tiền

- Controller: AdminPaymentController (/admin/payments, /{orderId}, .../recheck, .../gateway, .../refund)
- Service: AdminPaymentService.getCases / recheck / settleGateway / retryRefund, PaymentReconciliationService, RefundService
- Dữ liệu: Order, Payment, PaymentRefund
- Giao diện (template): templates/admin/payments/index.html, payments/detail.html
- Sơ đồ: `diagrams/admin/QT-10.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/payment/AdminPaymentService.java` dòng 216–253: Kiểm tra lại và chốt cổng
  - `src/main/java/com/oneshop/service/payment/AdminPaymentService.java` dòng 256–281: Thử lại hoàn tiền
- Ảnh giao diện: `screenshots/admin/A16_annotated.png` (/admin/payments, Giao diện quản lý thanh toán trực tuyến), `screenshots/admin/A17_annotated.png` (/admin/payments/8, Giao diện chi tiết thanh toán của một đơn)

### QT-11. Duyệt hoặc từ chối yêu cầu trả hàng

- Controller: AdminOrderReturnController (/admin/returns, /{id}, /{id}/approve, /{id}/reject)
- Service: AdminOrderReturnService.approve / reject, PartialRefundService.refundReturn
- Dữ liệu: OrderReturn, OrderReturnItem, PaymentRefund, Payment
- Giao diện (template): templates/admin/returns/index.html, returns/detail.html
- Sơ đồ: `diagrams/admin/QT-11.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/returns/AdminOrderReturnService.java` dòng 87–117: AdminOrderReturnService.approve, reject
  - `src/main/java/com/oneshop/service/payment/PartialRefundService.java` dòng 139–168: PartialRefundService.refundReturn
- Ảnh giao diện: `screenshots/admin/A18_annotated.png` (/admin/returns, Giao diện yêu cầu trả hàng của quản trị viên)
- Ghi chú về ảnh giao diện: Danh sách yêu cầu trả hàng rỗng trong dữ liệu mẫu; chi tiết yêu cầu (nút Duyệt, Từ chối) không được chụp.

### QT-12. Quản lý khuyến mãi

- Controller: AdminPromotionController (/admin/promotions, /new, /{id}/edit, /{id}/delete)
- Service: AdminPromotionService.create / update / delete
- Dữ liệu: Promotion, PromotionUsage
- Giao diện (template): templates/admin/promotions/index.html, promotions/form.html
- Sơ đồ: `diagrams/admin/QT-12.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/promotion/AdminPromotionService.java` dòng 77–107: Tạo, sửa, xóa mã
  - `src/main/java/com/oneshop/service/promotion/AdminPromotionService.java` dòng 111–139: Kiểm tra giá trị và điều kiện
- Ảnh giao diện: `screenshots/admin/A19_annotated.png` (/admin/promotions, Giao diện quản lý khuyến mãi), `screenshots/admin/A20_annotated.png` (/admin/promotions/new, Giao diện thêm mã khuyến mãi)

### QT-13. Kiểm duyệt đánh giá

- Controller: AdminReviewController (/admin/reviews, /{id}/status, /{id}/reply bị từ chối)
- Service: AdminReviewService.getReviews / stats / setStatus
- Dữ liệu: Review, ReviewReply; ReviewRepository
- Giao diện (template): templates/admin/reviews.html
- Sơ đồ: `diagrams/admin/QT-13.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/admin/AdminReviewController.java` dòng 44–53: AdminReviewController
  - `src/main/java/com/oneshop/service/AdminReviewService.java` dòng 88–100: AdminReviewService.setStatus, stats
- Ảnh giao diện: `screenshots/admin/A21_annotated.png` (/admin/reviews, Giao diện kiểm duyệt đánh giá)

### QT-14. Xem dashboard và thống kê chuỗi

- Controller: AdminDashboardController (GET /admin)
- Service: AdminOperationsService.getChainSummary / getOverview
- Dữ liệu: Order, OrderFulfillment, StoreProduct, StaffStoreAssignment
- Giao diện (template): templates/admin/dashboard.html
- Sơ đồ: `diagrams/admin/QT-14.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/admin/AdminDashboardController.java` dòng 25–32: AdminDashboardController
  - `src/main/java/com/oneshop/service/AdminOperationsService.java` dòng 36–40: AdminOperationsService.getChainSummary, getOverview
  - `src/main/java/com/oneshop/service/AdminOperationsService.java` dòng 97–107: getOverview theo chi nhánh
- Ảnh giao diện: `screenshots/admin/A01_annotated.png` (/admin, Giao diện dashboard quản trị)


## Staff

### NV-01. Đăng nhập khu vực nhân viên và xác định phạm vi chi nhánh

- Controller: Các controller /staff/** (StaffDashboardController, StaffFulfillmentController, StaffStockController, ...)
- Service: StaffStoreScopeInterceptor, StaffStoreScopeService, StoreServiceImpl.getAssignedStores
- Dữ liệu: StaffStoreAssignment, Store; StaffStoreAssignmentRepository
- Giao diện (template): templates/layouts/staff.html, staff/dashboard.html
- Sơ đồ: `diagrams/staff/NV-01.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/config/SecurityConfig.java` dòng 107–111: Phân quyền URL khu vực nhân viên
  - `src/main/java/com/oneshop/security/StaffStoreScopeInterceptor.java` dòng 41–54: StaffStoreScopeInterceptor.preHandle
  - `src/main/java/com/oneshop/service/StaffStoreScopeService.java` dòng 38–60: StaffStoreScopeService: phạm vi và kiểm tra chi nhánh
  - `src/main/java/com/oneshop/service/impl/StoreServiceImpl.java` dòng 176–183: Chi nhánh ACTIVE của nhân viên
- Ảnh giao diện: `screenshots/staff/S01_annotated.png` (/staff, Giao diện dashboard nhân viên)

### NV-02. Xem dashboard chi nhánh được phân công

- Controller: StaffDashboardController (GET /staff, /staff/dashboard)
- Service: StaffOperationsService.getDashboard, StaffFulfillmentService.getRecent
- Dữ liệu: OrderFulfillment, StoreProduct
- Giao diện (template): templates/staff/dashboard.html
- Sơ đồ: `diagrams/staff/NV-02.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/staff/StaffDashboardController.java` dòng 23–28: StaffDashboardController
  - `src/main/java/com/oneshop/service/StaffOperationsService.java` dòng 48–79: StaffOperationsService.getDashboard
- Ảnh giao diện: `screenshots/staff/S01_annotated.png` (/staff, Giao diện dashboard nhân viên)

### NV-03. Xem hàng đợi phần giao hàng của chi nhánh

- Controller: StaffFulfillmentController (GET /staff/fulfillments, /delivery, /pickup; /staff/orders chuyển hướng)
- Service: StaffFulfillmentService.getQueue / toItem
- Dữ liệu: OrderFulfillment, Order, OrderItem; OrderFulfillmentRepository
- Giao diện (template): templates/staff/fulfillments/index.html, delivery/fragments.html
- Sơ đồ: `diagrams/staff/NV-03.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java` dòng 41–55: StaffFulfillmentController.queue
  - `src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java` dòng 116–150: StaffFulfillmentService.getQueue
  - `src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java` dòng 428–440: Thao tác hợp lệ của từng phần giao
- Ảnh giao diện: `screenshots/staff/S02_annotated.png` (/staff/fulfillments, Giao diện hàng đợi phần giao hàng), `screenshots/staff/S03_annotated.png` (/staff/fulfillments/pickup, Giao diện hàng đợi nhận tại cửa hàng)

### NV-04. Xử lý phần giao tận nơi (chuẩn bị, đóng gói, giao, hoàn tất, thất bại)

- Controller: StaffFulfillmentController.act (POST /staff/fulfillments/{id}/{prepare|pack|ship|complete-delivery|fail-delivery|handover})
- Service: StaffFulfillmentService.perform, FulfillmentAction, OrderStatusAggregator, PaymentServiceImpl.recordOfflineCollected
- Dữ liệu: OrderFulfillment, OrderFulfillmentStatusHistory, Order, OrderStatusHistory, Payment
- Giao diện (template): templates/staff/fulfillments/detail.html
- Sơ đồ: `diagrams/staff/NV-04.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java` dòng 14–26: Các thao tác hợp lệ của nhân viên
  - `src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java` dòng 216–239: StaffFulfillmentService.perform (1)
  - `src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java` dòng 240–264: StaffFulfillmentService.perform (2)
  - `src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java` dòng 284–301: StaffFulfillmentService.perform (3)
  - `src/main/java/com/oneshop/service/fulfillment/OrderStatusAggregator.java` dòng 50–75: OrderStatusAggregator.derive
- Ảnh giao diện: `screenshots/staff/S04_annotated.png` (/staff/fulfillments/5, Giao diện chi tiết phần giao đang giao (giao tận nơi))

### NV-05. Xử lý nhận hàng tại cửa hàng và xác minh mã nhận hàng

- Controller: StaffFulfillmentController.act (POST .../ready-for-pickup, .../complete-pickup, .../fail-pickup)
- Service: StaffFulfillmentService.perform, PickupCodeService, PickupAttemptLimiter, PaymentServiceImpl.recordOfflineCollected
- Dữ liệu: OrderFulfillment (pickup_code, ready_at, picked_up_at), Payment
- Giao diện (template): templates/staff/fulfillments/detail.html, staff/fulfillments/index.html (hàng đợi pickup)
- Sơ đồ: `diagrams/staff/NV-05.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java` dòng 251–283: Sẵn sàng nhận và xác minh mã
  - `src/main/java/com/oneshop/service/fulfillment/PickupAttemptLimiter.java` dòng 39–63: PickupAttemptLimiter
- Ảnh giao diện: `screenshots/staff/S05_annotated.png` (/staff/fulfillments/6, Giao diện chi tiết phần giao nhận tại cửa hàng (xác minh mã)), `screenshots/staff/S03_annotated.png` (/staff/fulfillments/pickup, Giao diện hàng đợi nhận tại cửa hàng)

### NV-06. Xem tồn kho chi nhánh và điều chỉnh tồn

- Controller: StaffStockController (GET /staff/stock, /staff/stock/{id}/adjust; POST /staff/stock/{id}/adjust)
- Service: StaffInventoryService.getStock / adjust, InventoryServiceImpl.adjustStock
- Dữ liệu: StoreProduct, InventoryMovement
- Giao diện (template): templates/staff/stock/index.html, stock/adjust.html
- Sơ đồ: `diagrams/staff/NV-06.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/StaffInventoryService.java` dòng 187–197: StaffInventoryService.adjust
  - `src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java` dòng 48–75: InventoryServiceImpl.adjustStock
  - `src/main/java/com/oneshop/controller/staff/StaffStockController.java` dòng 80–90: StaffStockController.adjust
- Ảnh giao diện: `screenshots/staff/S06_annotated.png` (/staff/stock, Giao diện tồn kho chi nhánh), `screenshots/staff/S07_annotated.png` (/staff/stock/1/adjust, Giao diện điều chỉnh tồn kho)

### NV-07. Xem lịch sử biến động kho

- Controller: StaffStockController (GET /staff/inventory-history, /staff/inventory-history/{id})
- Service: StaffInventoryService.getMovementLog / getHistory, StaffStoreScopeService.requireStoreProduct
- Dữ liệu: InventoryMovement, StoreProduct
- Giao diện (template): templates/staff/stock/inventory-history.html, stock/history.html
- Sơ đồ: `diagrams/staff/NV-07.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/StaffInventoryService.java` dòng 120–142: StaffInventoryService.getMovementLog
  - `src/main/java/com/oneshop/service/StaffInventoryService.java` dòng 174–184: StaffInventoryService.getHistory
  - `src/main/java/com/oneshop/service/StaffStoreScopeService.java` dòng 70–75: StaffStoreScopeService.requireStoreProduct
- Ảnh giao diện: `screenshots/staff/S08_annotated.png` (/staff/inventory-history, Giao diện nhật ký biến động tồn), `screenshots/staff/S09_annotated.png` (/staff/inventory-history/1, Giao diện lịch sử biến động tồn của một sản phẩm)

### NV-08. Xử lý hàng khách trả (nhập lại kho hoặc xử lý)

- Controller: StaffOrderReturnController (GET /staff/returns; POST /staff/returns/items/{id}/restock, /dispose)
- Service: StaffOrderReturnService.handle, InventoryServiceImpl.restoreReturnedItem
- Dữ liệu: OrderReturnItem, OrderReturn, StoreProduct, InventoryMovement
- Giao diện (template): templates/staff/returns/index.html
- Sơ đồ: `diagrams/staff/NV-08.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/returns/StaffOrderReturnService.java` dòng 78–103: StaffOrderReturnService.handle (1)
  - `src/main/java/com/oneshop/service/returns/StaffOrderReturnService.java` dòng 105–123: StaffOrderReturnService.handle (2)
- Ảnh giao diện: `screenshots/staff/S10_annotated.png` (/staff/returns, Giao diện hàng khách trả)
- Ghi chú về ảnh giao diện: Danh sách hàng khách trả rỗng trong dữ liệu mẫu.

### NV-09. Phản hồi đánh giá của khách

- Controller: StaffReviewController (GET /staff/reviews, /staff/reviews/{id}; POST /staff/reviews/{id}/reply)
- Service: ReviewServiceImpl.getStaffReviews / reply, ReviewReplyService.reply / requireScope
- Dữ liệu: Review, ReviewReply, OrderItem, OrderFulfillment
- Giao diện (template): templates/staff/reviews/index.html, reviews/detail.html
- Sơ đồ: `diagrams/staff/NV-09.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)
- Đoạn mã trích:
  - `src/main/java/com/oneshop/service/review/ReviewReplyService.java` dòng 31–55: ReviewReplyService
  - `src/main/java/com/oneshop/controller/staff/StaffReviewController.java` dòng 29–39: StaffReviewController.reply
- Ảnh giao diện: `screenshots/staff/S11_annotated.png` (/staff/reviews, Giao diện đánh giá của chi nhánh), `screenshots/staff/S12_annotated.png` (/staff/reviews/1, Giao diện chi tiết đánh giá và phản hồi)


## Danh sách ảnh giao diện

| Mã ảnh | Đường dẫn | Tiêu đề | Số thành phần đánh số | Ảnh gốc |
|---|---|---|---|---|
| A01 | /admin | Giao diện dashboard quản trị | 7 | `screenshots/admin/A01.png` |
| A02 | /admin/users | Giao diện quản lý người dùng | 8 | `screenshots/admin/A02.png` |
| A03 | /admin/users/new | Giao diện tạo tài khoản người dùng | 8 | `screenshots/admin/A03.png` |
| A04 | /admin/staff-assignments | Giao diện phân công nhân viên cho chi nhánh | 8 | `screenshots/admin/A04.png` |
| A05 | /admin/stores | Giao diện quản lý chi nhánh | 8 | `screenshots/admin/A05.png` |
| A06 | /admin/stores/new | Giao diện thêm chi nhánh | 10 | `screenshots/admin/A06.png` |
| A07 | /admin/categories | Giao diện quản lý danh mục | 4 | `screenshots/admin/A07.png` |
| A08 | /admin/brands | Giao diện quản lý thương hiệu | 4 | `screenshots/admin/A08.png` |
| A09 | /admin/products | Giao diện quản lý sản phẩm | 9 | `screenshots/admin/A09.png` |
| A10 | /admin/products/1/edit | Giao diện sửa sản phẩm và quản lý hình ảnh | 8 | `screenshots/admin/A10.png` |
| A11 | /admin/store-products | Giao diện sản phẩm theo chi nhánh | 7 | `screenshots/admin/A11.png` |
| A12 | /admin/store-products/new | Giao diện gắn sản phẩm vào chi nhánh | 6 | `screenshots/admin/A12.png` |
| A13 | /admin/inventory | Giao diện tồn kho toàn chuỗi | 6 | `screenshots/admin/A13.png` |
| A14 | /admin/orders | Giao diện quản lý đơn hàng | 6 | `screenshots/admin/A14.png` |
| A15 | /admin/orders/13 | Giao diện chi tiết đơn hàng của quản trị viên | 8 | `screenshots/admin/A15.png` |
| A16 | /admin/payments?view=all | Giao diện quản lý thanh toán trực tuyến | 5 | `screenshots/admin/A16.png` |
| A17 | /admin/payments/8 | Giao diện chi tiết thanh toán của một đơn | 5 | `screenshots/admin/A17.png` |
| A18 | /admin/returns | Giao diện yêu cầu trả hàng của quản trị viên | 3 | `screenshots/admin/A18.png` |
| A19 | /admin/promotions | Giao diện quản lý khuyến mãi | 7 | `screenshots/admin/A19.png` |
| A20 | /admin/promotions/new | Giao diện thêm mã khuyến mãi | 10 | `screenshots/admin/A20.png` |
| A21 | /admin/reviews | Giao diện kiểm duyệt đánh giá | 6 | `screenshots/admin/A21.png` |
| C01 | / | Giao diện trang chủ (phần đầu trang) | 8 | `screenshots/customer/C01.png` |
| C02 | / | Giao diện trang chủ (sản phẩm nổi bật và thương hiệu) | 5 | `screenshots/customer/C02.png` |
| C03 | / | Giao diện trang chủ (hệ thống cửa hàng và chân trang) | 4 | `screenshots/customer/C03.png` |
| C04 | /login | Giao diện đăng nhập | 6 | `screenshots/customer/C04.png` |
| C05 | /register | Giao diện đăng ký tài khoản | 7 | `screenshots/customer/C05.png` |
| C06 | /forgot-password | Giao diện quên mật khẩu (bước gửi mã OTP) | 3 | `screenshots/customer/C06.png` |
| C07 | /products | Giao diện danh sách sản phẩm, tìm kiếm và bộ lọc | 8 | `screenshots/customer/C07.png` |
| C08 | /products/1 | Giao diện chi tiết sản phẩm | 8 | `screenshots/customer/C08.png` |
| C09 | /stores | Giao diện tra cứu hệ thống cửa hàng | 5 | `screenshots/customer/C09.png` |
| C10 | /cart | Giao diện giỏ hàng | 7 | `screenshots/customer/C10.png` |
| C11 | /checkout?cartItemIds=30 | Giao diện xác nhận đặt hàng | 12 | `screenshots/customer/C11.png` |
| C12 | /checkout/1 | Giao diện kết quả đặt hàng | 5 | `screenshots/customer/C12.png` |
| C13 | /orders | Giao diện lịch sử đơn hàng | 5 | `screenshots/customer/C13.png` |
| C14 | /orders/13 | Giao diện chi tiết đơn hàng nhiều chi nhánh (phần đầu) | 7 | `screenshots/customer/C14.png` |
| C15 | /orders/13 | Giao diện chi tiết đơn hàng (đánh giá sản phẩm) | 4 | `screenshots/customer/C15.png` |
| C16 | /orders/1 | Giao diện chi tiết đơn hàng đã hoàn tất (trả hàng) | 4 | `screenshots/customer/C16.png` |
| C17 | /orders/1/return | Giao diện yêu cầu trả hàng | 5 | `screenshots/customer/C17.png` |
| C18 | /orders/13/payments | Giao diện thanh toán đơn hàng trực tuyến | 5 | `screenshots/customer/C18.png` |
| C19 | /account | Giao diện thông tin tài khoản | 6 | `screenshots/customer/C19.png` |
| S01 | /staff | Giao diện dashboard nhân viên | 6 | `screenshots/staff/S01.png` |
| S02 | /staff/fulfillments?open=false | Giao diện hàng đợi phần giao hàng | 8 | `screenshots/staff/S02.png` |
| S03 | /staff/fulfillments/pickup | Giao diện hàng đợi nhận tại cửa hàng | 4 | `screenshots/staff/S03.png` |
| S04 | /staff/fulfillments/5 | Giao diện chi tiết phần giao đang giao (giao tận nơi) | 7 | `screenshots/staff/S04.png` |
| S05 | /staff/fulfillments/6 | Giao diện chi tiết phần giao nhận tại cửa hàng (xác minh mã) | 6 | `screenshots/staff/S05.png` |
| S06 | /staff/stock | Giao diện tồn kho chi nhánh | 9 | `screenshots/staff/S06.png` |
| S07 | /staff/stock/1/adjust | Giao diện điều chỉnh tồn kho | 5 | `screenshots/staff/S07.png` |
| S08 | /staff/inventory-history | Giao diện nhật ký biến động tồn | 6 | `screenshots/staff/S08.png` |
| S09 | /staff/inventory-history/1 | Giao diện lịch sử biến động tồn của một sản phẩm | 4 | `screenshots/staff/S09.png` |
| S10 | /staff/returns | Giao diện hàng khách trả | 2 | `screenshots/staff/S10.png` |
| S11 | /staff/reviews | Giao diện đánh giá của chi nhánh | 4 | `screenshots/staff/S11.png` |
| S12 | /staff/reviews/1 | Giao diện chi tiết đánh giá và phản hồi | 3 | `screenshots/staff/S12.png` |