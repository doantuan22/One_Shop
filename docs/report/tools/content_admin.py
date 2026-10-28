# -*- coding: utf-8 -*-
"""Administrator functions (see content_customer.py for the structure)."""

A, S = "Quản trị viên", "Hệ thống"
LANES = [A, S]
DB = {"db": True}

FUNCS = [
dict(
 id="QT-01", title="Quản lý tài khoản người dùng", actor="Quản trị viên",
 desc=("Quản trị viên xem danh sách tài khoản (lọc theo vai trò, trạng thái, từ khóa), tạo tài khoản mới với vai trò CUSTOMER, STAFF hoặc ADMIN và sửa thông tin, vai trò, "
       "trạng thái của tài khoản đã có. Tài khoản không bị xóa: khóa tài khoản là đặt trạng thái INACTIVE. Email không đổi được sau khi tạo, mật khẩu không đổi qua "
       "biểu mẫu sửa, quản trị viên không thể tự khóa mình hay bỏ quyền ADMIN của chính mình, và khi một nhân viên bị đổi khỏi vai trò STAFF thì các phân công chi nhánh bị vô hiệu."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/users, lọc theo vai trò, trạng thái, từ khóa"),
  ("act",S,"searchUsers: lọc và phân trang 20 dòng",DB),
  ("act",A,"Chọn Thêm mới hoặc Sửa, gửi biểu mẫu"),
  ("dec",S,"Biểu mẫu AdminUserRequest hợp lệ?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("dec",S,"Thao tác tạo mới?","tạo mới","sửa",["Sửa: email giữ nguyên, không đổi mật khẩu, không tự khóa hay bỏ quyền ADMIN","Rời STAFF thì vô hiệu phân công; lưu User","Chuyển /admin/users?success"]),
  ("dec",S,"Email, tên đăng nhập chưa dùng; mật khẩu ≥ 8 ký tự, ≤ 72 byte?","đạt","không",["Báo lỗi email, userName hoặc password"]),
  ("act",S,"Mã hóa BCrypt, gán vai trò và trạng thái, lưu User",DB),
  ("act",S,"Chuyển /admin/users?success"),("end",A)],
 codes=[("AdminUserService.create","service/AdminUserService.java",87,105),
        ("AdminUserService.update","service/AdminUserService.java",108,134)],
 explain=("AdminUserService được bảo vệ bởi @PreAuthorize(\"hasRole('ADMIN')\"). create chuẩn hóa email, kiểm tra trùng email/tên đăng nhập, yêu cầu mật khẩu từ 8 ký tự và tối đa 72 byte "
          "rồi lưu User đã băm mật khẩu. update khóa dòng User bằng findByIdForUpdate, từ chối đổi email hoặc mật khẩu, không cho quản trị viên tự khóa hay bỏ quyền ADMIN, "
          "và khi vai trò rời STAFF thì đặt mọi StaffStoreAssignment về INACTIVE; việc quay lại STAFF không tự khôi phục phân công cũ. Lỗi được FormErrors gắn vào trường tương ứng của biểu mẫu."),
 trace=dict(controller="AdminUserController (/admin/users, /admin/users/new, /admin/users/{id}/edit)", service="AdminUserService.searchUsers / create / update",
            ui="templates/admin/users.html, admin/user-form.html", data="User, Role, StaffStoreAssignment"),
 status="Hoạt động",
),
dict(
 id="QT-02", title="Phân công và chuyển chi nhánh cho nhân viên", actor="Quản trị viên",
 desc=("Chức năng xác định chi nhánh mà mỗi nhân viên được phép xử lý. Quản trị viên gán nhân viên vào chi nhánh, tạm ngưng hoặc khôi phục phân công, hoặc chuyển nhân viên sang "
       "chi nhánh khác. Chỉ tài khoản STAFF đang ACTIVE và chi nhánh ACTIVE được bật phân công, và mỗi nhân viên chỉ làm việc tại một chi nhánh: muốn đổi nơi làm việc phải dùng "
       "'Chuyển chi nhánh'. Phân công ACTIVE chính là phạm vi dữ liệu mà backend áp dụng cho nhân viên đó."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/staff-assignments, chọn nhân viên và chi nhánh"),
  ("dec",S,"Thao tác là chuyển chi nhánh?","gán / đổi trạng thái","chuyển",["Yêu cầu STAFF ACTIVE, chi nhánh đích ACTIVE, đang làm việc nơi khác","Xóa phân công cũ, tạo phân công ACTIVE mới","Chuyển /admin/staff-assignments?success"]),
  ("act",S,"Khóa dòng User; tìm chi nhánh",DB),
  ("dec",S,"Tài khoản có vai trò STAFF?","có","không",["Báo chỉ STAFF được phân công"]),
  ("dec",S,"Khi bật: nhân viên và chi nhánh đều ACTIVE?","có","không",["Báo chỉ bật cho đối tượng đang hoạt động"]),
  ("dec",S,"Nhân viên chưa ACTIVE ở chi nhánh khác?","chưa","đã có",["Báo dùng 'Chuyển chi nhánh'"]),
  ("act",S,"Tạo hoặc cập nhật phân công (UNIQUE user và store)",DB),
  ("act",S,"Chuyển /admin/staff-assignments?success"),("end",A)],
 codes=[("AdminStaffAssignmentService.assign","service/AdminStaffAssignmentService.java",63,80),
        ("AdminStaffAssignmentService.transfer","service/AdminStaffAssignmentService.java",105,132),
        ("Một nhân viên chỉ làm việc tại một chi nhánh","service/AdminStaffAssignmentService.java",135,144)],
 explain=("assign khóa dòng User, kiểm tra vai trò STAFF, requireActive và requireNoOtherWorkplace rồi upsert cặp (user, store). transfer yêu cầu nhân viên đang có đúng "
          "phân công làm việc, xóa phân công cũ (không để lại dòng 'tạm ngưng' có thể khôi phục) và tạo dòng ACTIVE mới tại chi nhánh đích, trong cùng một giao dịch nên nhân viên "
          "không bao giờ làm việc ở hai nơi hay không ở đâu cả. requireNoOtherWorkplace tạo thông báo hướng dẫn dùng chức năng chuyển chi nhánh. Phạm vi này được StaffStoreScopeInterceptor "
          "đọc lại từ CSDL ở mỗi request của nhân viên (mục NV-01)."),
 trace=dict(controller="AdminStaffAssignmentController (/admin/staff-assignments, /transfer, /{id}/status)", service="AdminStaffAssignmentService.assign / setStatus / transfer",
            ui="templates/admin/staff-assignments.html", data="StaffStoreAssignment, User, Store"),
 status="Hoạt động",
),
dict(
 id="QT-03", title="Quản lý chi nhánh", actor="Quản trị viên",
 desc=("Quản trị viên xem, tìm kiếm, thêm và sửa chi nhánh: mã, tên, địa chỉ, tỉnh/thành, khu vực, số điện thoại, giờ mở cửa, khả năng giao tận nơi, nhận tại cửa hàng và trạng thái. "
       "Mã chi nhánh là duy nhất. Chi nhánh ngừng hoạt động bằng trạng thái INACTIVE, không bị xóa, nên lịch sử đơn hàng và tồn kho vẫn được giữ."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/stores; chọn Thêm mới hoặc Sửa chi nhánh"),
  ("act",A,"Nhập mã, tên, địa chỉ, khu vực, giờ mở, giao/nhận, trạng thái"),
  ("dec",S,"Biểu mẫu StoreRequest hợp lệ?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("act",S,"Kiểm tra mã chi nhánh đã tồn tại",DB),
  ("dec",S,"Mã chi nhánh chưa dùng (bởi chi nhánh khác)?","chưa","đã dùng",["Báo lỗi 'Mã chi nhánh đã tồn tại'"]),
  ("act",S,"Ghi Store với các thuộc tính đã chuẩn hóa",DB),
  ("act",S,"Chuyển hướng /admin/stores?success"),("end",A)],
 codes=[("StoreServiceImpl.createStore, updateStore","service/impl/StoreServiceImpl.java",133,166)],
 explain=("createStore và updateStore kiểm tra mã bằng existsByCode hoặc existsByCodeAndIdNot rồi gọi apply để gán các thuộc tính (cắt khoảng trắng, số điện thoại và giờ mở cửa "
          "có thể để trống, cờ giao/nhận, trạng thái). Lỗi trùng mã là BadRequestException gắn vào trường code. Danh sách chi nhánh đọc bằng searchStores với bộ lọc từ khóa, khu vực, trạng thái."),
 trace=dict(controller="AdminStoreController (/admin/stores, /new, /{id}/edit)", service="StoreServiceImpl.searchStores / createStore / updateStore",
            ui="templates/admin/stores.html, admin/store-form.html", data="Store; StoreRepository"),
 status="Hoạt động",
),
dict(
 id="QT-04", title="Quản lý danh mục và thương hiệu", actor="Quản trị viên",
 desc=("Quản trị viên thêm, sửa, ẩn danh mục và thương hiệu sản phẩm, kèm ảnh danh mục hoặc logo thương hiệu. Hai chức năng có cùng cấu trúc nên được trình bày chung: tên là duy nhất, "
       "trạng thái ACTIVE hoặc INACTIVE quyết định việc hiển thị với khách, ảnh được lưu trên Cloudinary và ảnh cũ chỉ bị xóa sau khi giao dịch thành công."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/categories hoặc /admin/brands; nhập tên, mô tả, trạng thái"),
  ("dec",S,"Biểu mẫu hợp lệ?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("act",S,"Kiểm tra tên đã tồn tại",DB),
  ("dec",S,"Tên chưa dùng (bởi bản ghi khác)?","chưa","đã dùng",["Báo lỗi tên danh mục hoặc thương hiệu đã tồn tại"]),
  ("act",S,"Lưu Category hoặc Brand (khi sửa: khóa dòng trước)",DB),
  ("act",S,"Nếu có tệp: tải lên Cloudinary, lưu URL và public_id",DB),
  ("act",S,"Xóa ảnh cũ trên Cloudinary sau commit"),
  ("act",S,"Chuyển hướng ?success=created hoặc updated"),("end",A)],
 codes=[("Tạo danh mục","service/impl/ProductServiceImpl.java",163,177),
        ("Sửa danh mục và thay ảnh","service/impl/ProductServiceImpl.java",187,204),
        ("Tạo và sửa thương hiệu","service/impl/ProductServiceImpl.java",252,272)],
 explain=("ProductServiceImpl kiểm tra tên bằng existsByName hoặc existsByNameAndIdNot, gán thuộc tính bằng apply rồi lưu. Khi có tệp, ImageTransactionCoordinator tải ảnh lên Cloudinary và "
          "trả về url cùng publicId; ảnh trước đó được xóa bằng deleteAfterCommit để không mất ảnh nếu giao dịch hoàn tác. Logo thương hiệu và ảnh danh mục có thêm các thao tác tải lên và gỡ ảnh riêng "
          "(uploadBrandLogo, removeBrandLogo, uploadCategoryImage, removeCategoryImage). Controller AdminCategoryController và AdminBrandController chuyển kết quả thành tham số success hoặc error trên URL."),
 trace=dict(controller="AdminCategoryController (/admin/categories), AdminBrandController (/admin/brands)", service="ProductServiceImpl.createCategory / updateCategory / createBrand / updateBrand / upload*",
            ui="templates/admin/categories.html, admin/brands.html", data="Category, Brand; CategoryRepository, BrandRepository; Cloudinary"),
 status="Hoạt động",
),
dict(
 id="QT-05", title="Quản lý sản phẩm và giá bán", actor="Quản trị viên",
 desc=("Quản trị viên tìm kiếm, thêm và sửa sản phẩm: SKU, tên, mô tả, danh mục, thương hiệu, giá bán online duy nhất, giá gốc và trạng thái. Theo mô hình V3, giá chỉ nằm trên Product và "
       "áp dụng cho mọi chi nhánh. Sản phẩm không bị xóa mà được ngừng bán bằng trạng thái; sản phẩm đang bán phải có giá lớn hơn 0. Sau khi tạo, hệ thống chuyển đến trang sửa để thêm ảnh."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/products, lọc; chọn Thêm mới hoặc Sửa"),
  ("act",A,"Nhập SKU, tên, giá online, giá gốc, danh mục, thương hiệu, trạng thái"),
  ("dec",S,"Biểu mẫu ProductRequest hợp lệ?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("act",S,"Kiểm tra SKU đã tồn tại",DB),
  ("dec",S,"SKU chưa dùng (bởi sản phẩm khác)?","chưa","đã dùng",["Báo lỗi 'Mã SKU đã tồn tại'"]),
  ("dec",S,"Giá ≥ 0, ACTIVE thì giá > 0, giá gốc ≥ giá bán?","đạt","sai",["Báo lỗi price hoặc originalPrice"]),
  ("dec",S,"Danh mục và thương hiệu tồn tại?","có","không",["Báo lỗi categoryId hoặc brandId"]),
  ("act",S,"Lưu Product (ngừng bán bằng trạng thái, không xóa)",DB),
  ("act",S,"Tạo mới: chuyển trang sửa để thêm ảnh; sửa: về danh sách"),("end",A)],
 codes=[("Tạo và cập nhật sản phẩm","service/impl/ProductServiceImpl.java",352,372),
        ("Kiểm tra giá, danh mục, thương hiệu","service/impl/ProductServiceImpl.java",374,398)],
 explain=("createProduct và updateProduct chuẩn hóa SKU, kiểm tra trùng bằng existsBySku hoặc existsBySkuAndIdNot rồi gọi apply. apply từ chối giá âm, giá có hơn hai chữ số lẻ hoặc vượt "
          "giới hạn, giá bằng 0 khi sản phẩm ACTIVE và giá gốc thấp hơn giá bán; sau đó nạp Category và Brand, báo lỗi theo trường nếu không tồn tại. AdminProductController.create dẫn tới "
          "/admin/products/{id}/edit?success=created, còn update quay về danh sách. Danh sách quản trị dùng searchAllProducts với bộ lọc từ khóa, danh mục, thương hiệu và trạng thái."),
 trace=dict(controller="AdminProductController (/admin/products, /new, /{id}/edit)", service="ProductServiceImpl.searchAllProducts / createProduct / updateProduct",
            ui="templates/admin/products.html, admin/product-form.html", data="Product, Category, Brand; ProductRepository"),
 status="Hoạt động",
),
dict(
 id="QT-06", title="Quản lý hình ảnh sản phẩm", actor="Quản trị viên",
 desc=("Trong trang sửa sản phẩm, quản trị viên tải ảnh lên, chọn ảnh chính hoặc xóa ảnh. Ảnh được lưu trên Cloudinary (tối đa 5MB, kiểm tra loại tệp), thông tin url và public_id nằm trong "
       "bảng product_images. Ảnh đầu tiên tự là ảnh chính; mỗi sản phẩm chỉ có một ảnh chính (chỉ mục duy nhất có lọc). Mọi thao tác ảnh khóa dòng Product nên tuần tự hóa kể cả khi có nhiều máy chủ."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở trang sửa sản phẩm; tải ảnh, chọn ảnh chính hoặc xóa ảnh"),
  ("act",S,"Khóa dòng Product (tuần tự hóa các thao tác ảnh)",DB),
  ("dec",S,"Thao tác tải ảnh lên?","đặt chính / xóa","tải lên",["Kiểm tra tệp (≤ 5MB, ảnh); tải lên Cloudinary","Lưu ProductImage; ảnh đầu tiên là ảnh chính","Chuyển ?success=image-uploaded"]),
  ("dec",S,"Thao tác đặt ảnh chính?","xóa ảnh","đặt chính",["Bỏ cờ chính của ảnh cũ, flush; đặt cờ cho ảnh chọn","Chuyển ?success=image-primary"]),
  ("act",S,"Xóa ProductImage; nếu là ảnh chính thì ảnh kế tiếp thành chính",DB),
  ("act",S,"Xóa ảnh trên Cloudinary sau commit"),
  ("act",S,"Chuyển ?success=image-deleted"),("end",A)],
 codes=[("Thêm ảnh và đặt ảnh chính","service/impl/ProductServiceImpl.java",409,434),
        ("Xóa ảnh","service/impl/ProductServiceImpl.java",438,451)],
 explain=("addProductImage khóa Product bằng productForUpdate, tải tệp qua ImageTransactionCoordinator, đặt cờ primary nếu sản phẩm chưa có ảnh chính và sortOrder tiếp theo. setPrimaryProductImage "
          "bỏ cờ của mọi ảnh, flush rồi mới đặt cờ cho ảnh chọn vì chỉ mục duy nhất của SQL Server được kiểm tra ngay. deleteProductImage xóa dòng, chuyển ảnh chính sang ảnh kế tiếp nếu cần và gọi "
          "deleteAfterCommit để xóa tệp trên Cloudinary sau khi commit. AdminProductController bắt BadRequestException và ImageStorageException để trả error=image-size, image-invalid hoặc image-storage."),
 trace=dict(controller="AdminProductController (POST /admin/products/{id}/images, .../primary, .../delete)", service="ProductServiceImpl.addProductImage / setPrimaryProductImage / deleteProductImage, ImageTransactionCoordinator, CloudinaryServiceImpl",
            ui="Khối ảnh trong templates/admin/product-form.html", data="ProductImage; bảng image_cleanup_tasks (ImageCleanupService); Cloudinary"),
 status="Hoạt động",
),
dict(
 id="QT-07", title="Quản lý tồn kho toàn chuỗi (gắn sản phẩm vào chi nhánh)", actor="Quản trị viên",
 desc=("Quản trị viên xem tồn kho của mọi chi nhánh (trang tồn kho với thống kê tổng, sắp hết, hết hàng) và quản lý các bản ghi StoreProduct: gắn một sản phẩm vào chi nhánh, "
       "đổi số lượng và trạng thái bán. Mỗi cặp (chi nhánh, sản phẩm) tồn tại một lần và không có giá riêng. Số lượng mở bán ban đầu và mọi lần điều chỉnh đều được ghi thành "
       "InventoryMovement loại STOCK_ADJUST; tồn không được âm hoặc thấp hơn số lượng đang được giữ."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/store-products hoặc /admin/inventory, lọc theo chi nhánh"),
  ("act",S,"searchStoreProducts hoặc searchInventory, kèm thống kê tồn",DB),
  ("act",A,"Chọn gắn sản phẩm vào chi nhánh hoặc sửa số lượng, trạng thái"),
  ("dec",S,"Biểu mẫu StoreProductRequest hợp lệ?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("dec",S,"Chi nhánh, sản phẩm tồn tại và cặp chưa được gắn?","đạt","không",["Báo lỗi storeId, productId hoặc đã gắn"]),
  ("act",S,"Tạo StoreProduct với quantity 0, không giá riêng",DB),
  ("dec",S,"Số lượng mới ≥ 0 và ≥ số lượng đang giữ?","đạt","sai",["Báo lỗi tồn kho không hợp lệ"]),
  ("act",S,"Cập nhật quantity và ghi InventoryMovement STOCK_ADJUST",DB),
  ("act",S,"Cập nhật trạng thái bán; chuyển ?success"),("end",A)],
 codes=[("Gắn sản phẩm vào chi nhánh, cập nhật","service/impl/StoreProductServiceImpl.java",246,283),
        ("InventoryServiceImpl.adjustStock","service/impl/InventoryServiceImpl.java",48,75)],
 explain=("createStoreProduct kiểm tra chi nhánh, sản phẩm và tính duy nhất của cặp (bắt cả DataIntegrityViolationException khi tạo đồng thời), tạo StoreProduct với quantity 0 rồi gọi "
          "InventoryService.adjustStock để ghi tồn ban đầu thành một InventoryMovement. updateStoreProduct khóa dòng trước khi nạp thực thể, gọi adjustStock và cập nhật trạng thái. adjustStock từ chối "
          "số âm hoặc thấp hơn reservedQuantity, bỏ qua khi số lượng không đổi và ghi quantityBefore, quantityChange, quantityAfter cùng người thực hiện."),
 trace=dict(controller="AdminStoreProductController (/admin/store-products), AdminInventoryController (GET /admin/inventory)", service="StoreProductServiceImpl.createStoreProduct / updateStoreProduct / searchInventory, InventoryServiceImpl.adjustStock",
            ui="templates/admin/store-products.html, store-product-form.html, inventory.html", data="StoreProduct, InventoryMovement"),
 status="Hoạt động",
),
dict(
 id="QT-08", title="Quản lý đơn hàng toàn chuỗi", actor="Quản trị viên",
 desc=("Quản trị viên xem toàn bộ đơn của chuỗi, lọc theo chi nhánh, trạng thái đơn, trạng thái thanh toán, hình thức nhận, khoảng ngày và từ khóa (mã đơn hoặc khách hàng), hoặc chỉ "
       "xem các đơn cần xử lý. Trang chi tiết đơn hiển thị khách hàng, các phần giao theo chi nhánh, ngoại lệ giao hàng, thông tin hoàn tiền và yêu cầu trả hàng. Quản trị viên chỉ xem "
       "và xử lý ngoại lệ ở đây; trạng thái đơn không bao giờ được sửa tay."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/orders, chọn bộ lọc"),
  ("dec",S,"Khoảng ngày hợp lệ (SqlDayRange)?","hợp lệ","sai",["Từ chối: khoảng ngày không hợp lệ"]),
  ("dec",S,"Chỉ xem đơn cần xử lý (attention)?","không","có",["Lấy đơn NEEDS_ATTENTION và chờ thanh toán quá hạn","Hiển thị danh sách đơn cần xử lý"]),
  ("act",S,"searchAdmin theo bộ lọc, phân trang 20; kèm số phần giao và chi nhánh",DB),
  ("act",A,"Chọn một đơn (/admin/orders/{id})"),
  ("dec",S,"Đơn tồn tại?","có","không",["404 Không tìm thấy đơn hàng"]),
  ("act",S,"Nạp chi tiết, ngoại lệ giao hàng, hoàn tiền, yêu cầu trả hàng",DB),
  ("act",S,"Hiển thị trang admin/order-detail"),("end",A)],
 codes=[("AdminOrderController","controller/admin/AdminOrderController.java",32,67),
        ("AdminOperationsService.getOrders, getOrder","service/AdminOperationsService.java",41,57)],
 explain=("AdminOrderController.list kiểm tra khoảng ngày bằng SqlDayRange.of trước khi tách nhánh 'attention'; AdminOperationsService.getOrders gọi OrderRepository.searchAdmin hoặc, khi từ khóa "
          "là nhiều từ, lọc theo tên và email khách bằng TextSearch. Mỗi dòng gồm tóm tắt đơn, số phần giao và tên các chi nhánh. detail gọi getOrder (ném ResourceNotFoundException khi không có đơn), "
          "AdminFulfillmentExceptionService.getException, OrderRefundViewService.forAdmin và AdminOrderReturnService.findByOrder để dựng trang."),
 trace=dict(controller="AdminOrderController (GET /admin/orders, /admin/orders/{id})", service="AdminOperationsService.getOrders / getOrder / getOrdersNeedingAttention",
            ui="templates/admin/orders.html, admin/order-detail.html", data="Order, OrderFulfillment, Payment, OrderReturn"),
 status="Hoạt động",
),
dict(
 id="QT-09", title="Xử lý ngoại lệ giao hàng thất bại", actor="Quản trị viên",
 desc=("Khi nhân viên báo một phần giao thất bại, phần giao ở trạng thái FAILED và đơn ở NEEDS_ATTENTION. Quản trị viên quyết định riêng cho từng phần giao, kèm lý do 5–400 ký tự: "
       "giao lại (FAILED → PACKED hoặc READY_FOR_PICKUP) hoặc xác nhận không giao được (FAILED → CANCELLED) với tình trạng hàng. Hàng đã về chi nhánh thì hoàn tồn đúng StoreProduct một lần; "
       "hàng thất lạc không đổi tồn; chưa xác nhận được thì chỉ ghi chú và ngoại lệ vẫn mở. Đơn ONLINE đã thanh toán được hoàn tiền mô phỏng theo giá trị hàng của phần đó. Trạng thái đơn "
       "được tổng hợp lại từ các phần giao."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở đơn NEEDS_ATTENTION; chọn giao lại hoặc không giao được, nhập lý do"),
  ("act",S,"Khóa đơn rồi khóa các phần giao (id tăng dần)",DB),
  ("dec",S,"Phần giao đang FAILED và đơn NEEDS_ATTENTION?","đúng","không",["409 Đã xử lý hoặc không phải ngoại lệ giao hàng"]),
  ("dec",S,"Quyết định là giao lại?","không giao được","giao lại",["Kiểm tra hàng ở chi nhánh, chi nhánh ACTIVE, thanh toán hợp lệ","FAILED → PACKED hoặc READY_FOR_PICKUP; ghi lịch sử","Tổng hợp lại trạng thái đơn"]),
  ("dec",S,"Tình trạng hàng đã xác định (không phải UNCONFIRMED)?","đã xác định","chưa",["Chỉ ghi chú; phần giao vẫn FAILED, đơn vẫn NEEDS_ATTENTION"]),
  ("dec",S,"Đủ điều kiện xác nhận không giao được?","đủ","chưa",["409 Cần xử lý thanh toán trước"]),
  ("act",S,"Hàng đã về: hoàn tồn đúng StoreProduct (một lần)",DB),
  ("act",S,"Phần giao → CANCELLED, ghi lịch sử và lý do",DB),
  ("act",S,"ONLINE đã PAID: hoàn tiền mô phỏng giá trị phần hàng",DB),
  ("act",S,"Tổng hợp lại trạng thái đơn từ các phần giao",DB),("end",A)],
 codes=[("Quyết định giao lại","service/fulfillment/AdminFulfillmentExceptionService.java",222,253),
        ("Xác nhận không giao được (1)","service/fulfillment/AdminFulfillmentExceptionService.java",258,294),
        ("Xác nhận không giao được (2)","service/fulfillment/AdminFulfillmentExceptionService.java",295,317)],
 explain=("redeliver và markUndeliverable đều chạy trong một giao dịch: khóa Order rồi các Fulfillment, requireFailed bảo đảm phần giao còn FAILED nên yêu cầu lặp lại không có tác dụng. "
          "redeliver chuyển phần giao sang PACKED hoặc READY_FOR_PICKUP và xóa mã vận đơn cũ; markUndeliverable với GoodsStatus.UNCONFIRMED chỉ ghi lịch sử, với RETURNED gọi "
          "restoreForUndeliverableFulfillment, rồi đặt CANCELLED và gọi refundUndeliverable khi đơn ONLINE đã PAID. Cuối cùng settle dùng OrderStatusAggregator.derive để tính lại trạng thái đơn; "
          "nếu còn phần FAILED khác, đơn vẫn NEEDS_ATTENTION. Nhân viên chỉ báo thất bại, không thể hủy hay hoàn tồn."),
 trace=dict(controller="AdminFulfillmentExceptionController (POST /admin/orders/{orderId}/fulfillments/{id}/redeliver, /undeliverable)", service="AdminFulfillmentExceptionService.redeliver / markUndeliverable, InventoryServiceImpl.restoreForUndeliverableFulfillment, PartialRefundService",
            ui="Khối ngoại lệ trong templates/admin/order-detail.html", data="Order, OrderFulfillment, OrderFulfillmentStatusHistory, InventoryMovement, PaymentRefund"),
 status="Hoạt động",
),
dict(
 id="QT-10", title="Quản lý thanh toán ONLINE: đối soát, chốt cổng, hoàn tiền", actor="Quản trị viên",
 desc=("Trang thanh toán của quản trị viên liệt kê các đơn ONLINE theo nhóm (cần xử lý, chờ thanh toán, đang đối soát, đã thu, đã hoàn, thất bại). Với từng đơn, quản trị viên có ba thao tác bị "
       "giới hạn bởi bằng chứng của sổ cái cổng: kiểm tra lại (đối soát), chốt kết quả của lần thanh toán chưa xác định (đã thu hoặc chưa thu, chỉ khi cổng mô phỏng bật) và thử lại hoàn tiền "
       "mô phỏng đã thất bại. Không có cách đặt trạng thái tùy ý, đánh dấu PAID khi chưa có bằng chứng, sửa tồn kho hay hồi sinh đơn đã hủy."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/payments, chọn nhóm cần xem"),
  ("act",S,"getCases và getCaseCounts theo nhóm, từ khóa, ngày",DB),
  ("act",A,"Mở chi tiết đơn (/admin/payments/{id}), chọn thao tác"),
  ("dec",S,"Đơn dùng thanh toán ONLINE?","có","không",["409 Đơn không dùng thanh toán trực tuyến"]),
  ("dec",S,"Thao tác là kiểm tra lại (recheck)?","khác","recheck",["reconcileLocked (ADMIN_RECHECK)","Ghi audit nếu trạng thái giữ nguyên"]),
  ("dec",S,"Thao tác là chốt kết quả cổng?","hoàn tiền","chốt cổng",["Cổng bật, đơn chưa hủy, có lần thanh toán chưa xác định","Ghi kết quả đã thu hoặc chưa thu vào sổ cái","reconcileLocked (ADMIN_GATEWAY)"]),
  ("dec",S,"Đơn đã CANCELLED (hoặc hoàn tiền từng phần lỗi)?","đúng","không",["409 Chỉ hoàn tiền cho đơn đã hủy"]),
  ("act",S,"RefundService.simulate: hoàn tiền toàn phần, ghi sổ hoàn tiền",DB),
  ("act",S,"Ghi audit ADMIN_REFUND_RETRY, báo kết quả"),("end",A)],
 codes=[("Kiểm tra lại và chốt cổng","service/payment/AdminPaymentService.java",216,253),
        ("Thử lại hoàn tiền","service/payment/AdminPaymentService.java",256,281)],
 explain=("Mỗi thao tác lấy quản trị viên từ SecurityContext (currentAdmin), khóa đơn bằng lockOnline (từ chối đơn không phải ONLINE) rồi gọi PaymentReconciliationService.reconcileLocked với tác nhân "
          "tương ứng; auditWhenUnchanged ghi lịch sử khi trạng thái đơn không đổi. settleGateway chỉ chạy khi cổng mô phỏng bật và có lần thanh toán IN_DOUBT. retryFailed của PartialRefundService "
          "xử lý hoàn tiền từng phần lỗi, còn đơn đã hủy được hoàn toàn phần qua RefundService.simulate; đơn chưa hủy bị từ chối bằng PaymentConflictException."),
 trace=dict(controller="AdminPaymentController (/admin/payments, /{orderId}, .../recheck, .../gateway, .../refund)", service="AdminPaymentService.getCases / recheck / settleGateway / retryRefund, PaymentReconciliationService, RefundService",
            ui="templates/admin/payments/index.html, payments/detail.html", data="Order, Payment, PaymentRefund"),
 status="Hoạt động (mô phỏng, không kết nối ngân hàng)",
),
dict(
 id="QT-11", title="Duyệt hoặc từ chối yêu cầu trả hàng", actor="Quản trị viên",
 desc=("Quản trị viên xem các yêu cầu trả hàng (lý do, ảnh, dòng hàng, số tiền hoàn) và quyết định một lần. Duyệt: trong một giao dịch dưới khóa đơn, hệ thống hoàn tiền mô phỏng giá trị hàng "
       "(giữ phí giao hàng) rồi đặt yêu cầu APPROVED; các dòng hàng sau đó xuất hiện trong danh sách xử lý của nhân viên chi nhánh đã giao. Từ chối: bắt buộc nhập lý do 5–400 ký tự và quyết định là cuối cùng. "
       "Nếu hoàn tiền mô phỏng thất bại, việc duyệt bị hoàn tác."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/returns/{id}, xem lý do, ảnh, dòng hàng"),
  ("act",A,"Chọn Duyệt hoặc Từ chối (kèm lý do)"),
  ("dec",S,"Thao tác là duyệt?","duyệt","từ chối",["Lý do từ chối 5–400 ký tự","Khóa đơn và yêu cầu, còn REQUESTED","Ghi REJECTED, người duyệt, ghi chú"]),
  ("act",S,"Khóa đơn rồi khóa yêu cầu trả hàng",DB),
  ("dec",S,"Yêu cầu còn ở trạng thái REQUESTED?","còn","đã xử lý",["409 Đã được xử lý trước đó"]),
  ("act",S,"refundReturn: ghi PaymentRefund (RETURN) và mô phỏng hoàn tiền",DB),
  ("dec",S,"Mô phỏng hoàn tiền thành công?","có","không",["Hoàn tác giao dịch; báo thử lại"]),
  ("act",S,"Đặt APPROVED, ghi người duyệt và thời điểm",DB),
  ("act",S,"Chuyển hướng về chi tiết yêu cầu"),("end",A)],
 codes=[("AdminOrderReturnService.approve, reject","service/returns/AdminOrderReturnService.java",87,117),
        ("PartialRefundService.refundReturn","service/payment/PartialRefundService.java",139,168)],
 explain=("approve và reject cùng gọi lockOrder rồi lockedRequest (khóa đơn trước, đúng thứ tự khóa toàn hệ thống) và requireRequested; yêu cầu đã xử lý trả FulfillmentConflictException. "
          "approve gọi PartialRefundService.refundReturn: kiểm tra có đúng một khoản thanh toán đã thu, số tiền không vượt phần còn hoàn được, ghi PaymentRefund và mô phỏng; "
          "nếu simulate trả false, ngoại lệ làm cả giao dịch phê duyệt hoàn tác. reject kiểm tra độ dài lý do và ghi trạng thái REJECTED cùng ghi chú. Đơn và phần giao không bị sửa."),
 trace=dict(controller="AdminOrderReturnController (/admin/returns, /{id}, /{id}/approve, /{id}/reject)", service="AdminOrderReturnService.approve / reject, PartialRefundService.refundReturn",
            ui="templates/admin/returns/index.html, returns/detail.html", data="OrderReturn, OrderReturnItem, PaymentRefund, Payment"),
 status="Hoạt động",
),
dict(
 id="QT-12", title="Quản lý khuyến mãi", actor="Quản trị viên",
 desc=("Quản trị viên tạo, sửa và xóa mã khuyến mãi (giảm theo phần trăm có mức trần hoặc theo số tiền), đặt tiền hàng tối thiểu, số lượt dùng tổng và mỗi khách, thời gian áp dụng và trạng thái. "
       "Mã gồm 3–30 ký tự A–Z, 0–9, '_' hoặc '-', duy nhất và không đổi sau khi tạo. Số lượt tối đa không được nhỏ hơn số lượt đã dùng; mã đã có người dùng không xóa được mà chỉ chuyển sang INACTIVE. "
       "Thay đổi chỉ ảnh hưởng đơn đặt sau đó."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/promotions; chọn Tạo, Sửa hoặc Xóa mã"),
  ("dec",S,"Thao tác là xóa?","tạo / sửa","xóa",["Khóa dòng mã khuyến mãi","Đã có lượt dùng thì báo lỗi, gợi ý Ngừng áp dụng; chưa thì xóa","Chuyển ?success"]),
  ("dec",S,"Biểu mẫu PromotionRequest hợp lệ?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("dec",S,"Mã 3–30 ký tự đúng mẫu và chưa tồn tại (khi tạo)?","đạt","không",["Báo lỗi trường code"]),
  ("dec",S,"Giá trị giảm, mức trần, hạn kết thúc, số lượt hợp lệ?","hợp lệ","sai",["Báo lỗi trường tương ứng"]),
  ("act",S,"Lưu Promotion (khi sửa: khóa dòng trước)",DB),
  ("act",S,"Chuyển hướng về danh sách"),("end",A)],
 codes=[("Tạo, sửa, xóa mã","service/promotion/AdminPromotionService.java",77,107),
        ("Kiểm tra giá trị và điều kiện","service/promotion/AdminPromotionService.java",111,139)],
 explain=("create chuẩn hóa mã, kiểm tra mẫu CODE và existsByCodeIgnoreCase rồi gọi apply. update khóa dòng bằng findByIdForUpdate (không xen kẽ với một lần thanh toán đang dùng mã) và truyền usedCount cho apply. "
          "delete từ chối khi đã có PromotionUsage hoặc usedCount lớn hơn 0. apply bảo đảm giảm phần trăm là số nguyên 1–100, số tiền là số đồng nguyên, mức trần chỉ dùng cho phần trăm, hạn kết thúc sau bắt đầu và "
          "usageLimit không nhỏ hơn số lượt đã dùng; mỗi lỗi ném BadRequestException gắn với trường tương ứng."),
 trace=dict(controller="AdminPromotionController (/admin/promotions, /new, /{id}/edit, /{id}/delete)", service="AdminPromotionService.create / update / delete",
            ui="templates/admin/promotions/index.html, promotions/form.html", data="Promotion, PromotionUsage"),
 status="Hoạt động",
),
dict(
 id="QT-13", title="Kiểm duyệt đánh giá", actor="Quản trị viên",
 desc=("Quản trị viên xem mọi đánh giá của khách, lọc theo từ khóa, trạng thái hiển thị và tình trạng đã được phản hồi, kèm các số liệu tổng, chưa phản hồi và đang ẩn. Vai trò của quản trị viên "
       "chỉ là kiểm duyệt: đặt đánh giá ở trạng thái hiển thị (ACTIVE) hoặc ẩn (HIDDEN). Việc trả lời đánh giá thuộc nhân viên của chi nhánh nguồn; URL phản hồi của quản trị viên được giữ lại nhưng luôn bị từ chối."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/reviews, lọc theo từ khóa, trạng thái, đã phản hồi"),
  ("act",S,"getReviews và stats (tổng, chưa phản hồi, đang ẩn)",DB),
  ("act",A,"Chọn Ẩn hoặc Hiện một đánh giá"),
  ("dec",S,"Đánh giá tồn tại?","có","không",["404 Không tìm thấy đánh giá"]),
  ("act",S,"Khóa dòng đánh giá, đặt status ACTIVE hoặc HIDDEN",DB),
  ("act",S,"Chuyển /admin/reviews?success"),("end",A)],
 codes=[("AdminReviewController","controller/admin/AdminReviewController.java",44,53),
        ("AdminReviewService.setStatus, stats","service/AdminReviewService.java",88,100)],
 explain=("AdminReviewController.status chỉ nhận id và VisibilityStatus rồi gọi AdminReviewService.setStatus; dịch vụ khóa dòng Review bằng findByIdForUpdate (ném ResourceNotFoundException nếu không có) "
          "và đặt trạng thái. Endpoint /{id}/reply luôn ném AccessDeniedException để các biểu mẫu hoặc đường dẫn cũ không thể cho quản trị viên trả lời. Bộ lọc dùng Specification theo trạng thái, "
          "tình trạng phản hồi và từ khóa trên khách hoặc sản phẩm."),
 trace=dict(controller="AdminReviewController (/admin/reviews, /{id}/status, /{id}/reply bị từ chối)", service="AdminReviewService.getReviews / stats / setStatus",
            ui="templates/admin/reviews.html", data="Review, ReviewReply; ReviewRepository"),
 status="Hoạt động (phản hồi bị chặn có chủ đích: thuộc về nhân viên)",
),
dict(
 id="QT-14", title="Xem dashboard và thống kê chuỗi", actor="Quản trị viên",
 desc=("Trang /admin cho quản trị viên cái nhìn tổng quan chuỗi: tổng số đơn, số phần giao đang mở, số đơn cần xử lý, số đơn đang đối soát thanh toán và số dòng SKU–chi nhánh hết hàng, cùng bảng theo từng chi nhánh (kể cả chi nhánh "
       "ngừng hoạt động): số đơn, đơn cần xử lý, đơn hoàn tất, đơn hủy, số mặt hàng, sắp hết, hết hàng và số nhân viên. Tất cả số liệu được tính trực tiếp từ dữ liệu hiện có, có thể thu hẹp theo một chi nhánh. Phần doanh thu được tách thành chức năng riêng (QT-15), trang tổng quan chỉ có nút Báo cáo doanh thu dẫn tới đó."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin, có thể chọn một chi nhánh (storeId)"),
  ("act",S,"getChainSummary: tổng đơn, phần giao mở, đơn cần xử lý, đang đối soát",DB),
  ("dec",S,"Có chọn một chi nhánh?","toàn chuỗi","một chi nhánh",["getStore: 404 nếu không tồn tại","Tính số liệu riêng của chi nhánh đó"]),
  ("act",S,"getOverview: số liệu từng chi nhánh (đơn, tồn, nhân viên)",DB),
  ("act",S,"Nạp danh sách chi nhánh cho bộ lọc"),
  ("act",S,"Hiển thị admin/dashboard"),("end",A)],
 codes=[("AdminDashboardController","controller/admin/AdminDashboardController.java",25,32),
        ("AdminOperationsService.getChainSummary, getOverview","service/AdminOperationsService.java",36,40),
        ("getOverview theo chi nhánh","service/AdminOperationsService.java",97,107)],
 explain=("AdminDashboardController.dashboard gọi getChainSummary, getOverview(storeId) và StoreService.getAllStores. getChainSummary đếm đơn, phần giao ở các trạng thái mở và các đơn cần chú ý theo "
          "hạn thanh toán của PaymentDeadlinePolicy. getOverview chọn toàn bộ chi nhánh hoặc một chi nhánh (getStore ném ResourceNotFoundException nếu sai id) rồi tính các bộ đếm đơn, tồn thấp (1–5), hết hàng "
          "và số nhân viên hiệu lực. Dashboard không có biểu đồ; số liệu hiển thị dạng thẻ và bảng."),
 trace=dict(controller="AdminDashboardController (GET /admin)", service="AdminOperationsService.getChainSummary / getOverview",
            ui="templates/admin/dashboard.html", data="Order, OrderFulfillment, StoreProduct, StaffStoreAssignment"),
 status="Hoạt động",
),
dict(
 id="QT-15", title="Xem báo cáo doanh thu", actor="Quản trị viên",
 desc=("Trang /admin/revenue cho quản trị viên xem doanh thu của toàn chuỗi trong một khoảng ngày (mặc định từ ngày đầu tháng đến hôm nay) và nhóm kết quả theo ngày, tuần, tháng, chi nhánh, danh mục, thương hiệu, "
       "phương thức thanh toán hoặc hình thức nhận hàng. Doanh thu chỉ tính các đơn đã thu tiền (trạng thái thanh toán PAID), là tiền hàng đã trừ giảm giá và không gồm phí vận chuyển; đơn chưa thanh toán, thất bại, "
       "đã hủy hoặc đã hoàn tiền không được tính. Trang hiển thị ba chỉ số (doanh thu, số đơn đã thu tiền, trung bình mỗi đơn) và bảng số đơn, doanh thu, tỷ trọng của từng nhóm. Trang tổng quan /admin có nút Báo cáo doanh thu dẫn tới đây."),
 lanes=LANES,
 steps=[("start",A),("act",A,"Mở /admin/revenue, chọn từ ngày, đến ngày và nhóm theo"),
  ("dec",S,"Khoảng ngày hợp lệ (đến ngày không trước từ ngày)?","hợp lệ","không",["400 Ngày kết thúc không được trước ngày bắt đầu"]),
  ("act",S,"Lấy các đơn đã thu tiền trong khoảng (tiền hàng, không gồm phí ship)",DB),
  ("act",S,"Gom theo nhóm: từ đơn (thời gian, thanh toán, nhận hàng) hoặc từ dòng hàng (chi nhánh, danh mục, thương hiệu)",DB),
  ("act",S,"Tính tổng, trung bình mỗi đơn và tỷ trọng từng nhóm"),
  ("act",S,"Hiển thị admin/revenue"),("end",A)],
 codes=[("AdminRevenueController","controller/admin/AdminRevenueController.java",29,47),
        ("AdminRevenueService.report","service/AdminRevenueService.java",71,96),
        ("Truy vấn doanh thu trong OrderRepository","repository/OrderRepository.java",179,186)],
 explain=("AdminRevenueController.report đặt mặc định từ ngày đầu tháng đến hôm nay (theo đồng hồ máy chủ), đọc nhóm theo và rơi về nhóm theo ngày nếu giá trị lạ, rồi gọi AdminRevenueService.report. Dịch vụ kiểm tra khoảng ngày bằng SqlDayRange, "
          "lấy các đơn có trạng thái thanh toán PAID bằng collectedOrders (tổng tiền trừ phí vận chuyển) và cộng tổng. Khi nhóm theo thời gian, phương thức thanh toán hoặc hình thức nhận hàng, dịch vụ gom các đơn này trong bộ nhớ; "
          "khi nhóm theo chi nhánh, danh mục hoặc thương hiệu, doanh thu được cộng từ dòng hàng (giá nhân số lượng trừ giảm giá của dòng) nên một đơn nhiều chi nhánh được chia đúng theo phần hàng của từng chi nhánh và tổng các nhóm bằng tổng doanh thu. "
          "Tỷ trọng là doanh thu nhóm chia tổng, làm tròn một chữ số thập phân. Dịch vụ được bảo vệ bởi @PreAuthorize(\"hasRole('ADMIN')\") và chỉ đọc dữ liệu."),
 trace=dict(controller="AdminRevenueController (GET /admin/revenue)", service="AdminRevenueService.report",
            ui="templates/admin/revenue.html", data="Order, OrderItem, OrderFulfillment, Product; OrderRepository"),
 status="Hoạt động",
),
]
