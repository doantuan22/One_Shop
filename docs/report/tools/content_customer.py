# -*- coding: utf-8 -*-
"""Customer (Guest/Customer) functions: description, activity diagram, code excerpts, explanation, traceability.
Code excerpts are (caption, file relative to src/main/java/com/oneshop/, first line, last line); the text is read
verbatim from the repository by the build script."""

K, S = "Khách hàng", "Hệ thống"
LANES = [K, S]
DB = {"db": True}

FUNCS = [
dict(
 id="KH-01", title="Đăng ký tài khoản", actor="Khách vãng lai",
 desc=("Chức năng cho phép khách vãng lai tạo tài khoản khách hàng để mua sắm. Người dùng nhập email, tên đăng nhập, họ tên, "
       "số điện thoại (tùy chọn) và mật khẩu tại trang /register; không cần đăng nhập trước. Hệ thống kiểm tra dữ liệu bằng Bean "
       "Validation, kiểm tra lại mẫu tên đăng nhập và giới hạn 72 byte của BCrypt, kiểm tra trùng email/tên đăng nhập, mã hóa mật "
       "khẩu và luôn gán vai trò CUSTOMER. Kết quả là một bản ghi trong bảng users và trình duyệt được chuyển đến /login?registered."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Nhập email, tên đăng nhập, họ tên, mật khẩu"),
  ("dec",S,"Dữ liệu qua Bean Validation?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("dec",S,"Tên đăng nhập đúng mẫu, mật khẩu ≤ 72 byte?","đúng","sai",["Báo lỗi userName hoặc password"]),
  ("act",S,"Kiểm tra email, tên đăng nhập đã tồn tại",DB),
  ("dec",S,"Email và tên đăng nhập chưa dùng?","chưa dùng","đã dùng",["Báo lỗi email hoặc userName"]),
  ("act",S,"Mã hóa BCrypt, gán vai trò CUSTOMER"),
  ("act",S,"Lưu bản ghi users (saveAndFlush)",DB),
  ("dec",S,"Vi phạm ràng buộc UNIQUE do đăng ký đồng thời?","không","có",["Báo lỗi email hoặc tên đã dùng"]),
  ("act",S,"Chuyển hướng /login?registered"),("end",K)],
 codes=[("Kiểm tra dữ liệu đăng ký","service/impl/AuthServiceImpl.java",70,87),
        ("Tạo tài khoản CUSTOMER","service/impl/AuthServiceImpl.java",88,106)],
 explain=("Phương thức register của AuthServiceImpl chạy trong một giao dịch. Đoạn đầu chuẩn hóa email, kiểm tra tên đăng nhập bằng "
          "USER_NAME_PATTERN và giới hạn 72 byte, rồi dùng UserRepository.existsByEmail / existsByUserNameIgnoreCase để loại trùng; "
          "mỗi lỗi ném BadRequestException gắn với một trường. Đoạn sau lấy Role CUSTOMER từ RoleRepository, mã hóa mật khẩu bằng "
          "PasswordEncoder và lưu User; nếu hai yêu cầu đồng thời cùng qua bước kiểm tra, ràng buộc UNIQUE của CSDL gây "
          "DataIntegrityViolationException và được đổi thành thông báo lỗi. Controller AuthController bắt BadRequestException để "
          "gắn lỗi vào đúng ô nhập, khớp với các nhánh lỗi của sơ đồ."),
 trace=dict(controller="AuthController (GET/POST /register), AuthApiController (POST /api/auth/register)", service="AuthServiceImpl.register",
            ui="templates/auth/register.html", data="User, Role; UserRepository, RoleRepository"),
 status="Hoạt động",
),
dict(
 id="KH-02", title="Đăng nhập và đăng xuất", actor="Khách hàng, nhân viên, quản trị viên",
 desc=("Chức năng xác thực dùng chung cho cả ba vai trò. Người dùng nhập email hoặc tên đăng nhập cùng mật khẩu tại /login. Hệ thống "
       "xác thực qua AuthenticationManager, sinh JWT và ghi vào cookie HttpOnly ONESHOP_TOKEN; sau đó chuyển hướng theo vai trò "
       "(ADMIN → /admin, STAFF → /staff, còn lại → /). Ở mỗi request sau, JwtAuthenticationFilter đọc lại người dùng và vai trò từ CSDL "
       "theo user_id trong token. Đăng xuất (POST /logout) xóa cookie và quay về /login?logout."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Nhập email hoặc tên đăng nhập và mật khẩu"),
  ("dec",S,"Form đăng nhập hợp lệ?","có","không",["Hiển thị lại form"]),
  ("act",S,"Tìm user theo email (có '@') hoặc user_name",DB),
  ("dec",S,"Tồn tại, tài khoản ACTIVE, mật khẩu BCrypt khớp?","đúng","sai",["Báo 'Sai tài khoản hoặc mật khẩu'"]),
  ("act",S,"Sinh JWT, ghi cookie HttpOnly ONESHOP_TOKEN"),
  ("act",S,"Chuyển hướng: ADMIN → /admin, STAFF → /staff, CUSTOMER → /"),
  ("act",K,"Gửi các request tiếp theo kèm cookie"),
  ("act",S,"Filter nạp lại user và vai trò từ CSDL theo user_id",DB),
  ("act",K,"Chọn Đăng xuất (POST /logout)"),
  ("act",S,"Xóa cookie JWT, chuyển /login?logout"),("end",K)],
 codes=[("Đăng nhập: AuthController","controller/client/AuthController.java",37,51),
        ("Xác thực và sinh JWT","service/impl/AuthServiceImpl.java",54,66),
        ("Xác thực mỗi request bằng JWT","security/jwt/JwtAuthenticationFilter.java",43,64)],
 explain=("AuthController.login kiểm tra form, gọi AuthService.login rồi ghi token vào cookie; mọi AuthenticationException đều "
          "cho cùng một thông báo để không lộ tài khoản có tồn tại hay không. AuthServiceImpl.login chuyển việc kiểm tra cho "
          "AuthenticationManager (CustomUserDetailsService chọn tìm theo email hay user_name tùy dấu '@') và dùng JwtService để tạo token. "
          "Trong JwtAuthenticationFilter, token lấy từ cookie hoặc header Bearer; người dùng được nạp lại bằng loadUserById nên khóa tài "
          "khoản hoặc đổi vai trò có hiệu lực ngay, không phụ thuộc nội dung token. Phân quyền URL do SecurityConfig đảm nhiệm."),
 trace=dict(controller="AuthController (GET/POST /login), AuthApiController (POST /api/auth/login), cấu hình logout trong SecurityConfig", service="AuthServiceImpl.login, JwtService, JwtAuthenticationFilter, CustomUserDetailsService",
            ui="templates/auth/login.html", data="User, Role; UserRepository"),
 status="Hoạt động",
),
dict(
 id="KH-03", title="Quên mật khẩu bằng mã OTP", actor="Khách hàng (chưa đăng nhập)",
 desc=("Chức năng giúp người dùng đặt lại mật khẩu khi quên, gồm ba bước: gửi mã OTP 6 chữ số tới email, nhập mã để chứng minh quyền "
       "sở hữu, rồi đặt mật khẩu mới. Email phải thuộc một tài khoản đang ACTIVE. Mã chỉ có hiệu lực 10 phút, chỉ được gửi lại sau 60 "
       "giây, tối đa 5 lần mỗi giờ và sai tối đa 5 lần. Khi nhập đúng, hệ thống cấp một vé ký HMAC; chỉ vé này mới cho phép đổi mật khẩu. "
       "Việc gửi thư cần cấu hình SMTP; nếu chưa có, hệ thống báo chưa gửi được và không lưu mã."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Nhập email tại /forgot-password"),
  ("dec",S,"Email thuộc tài khoản ACTIVE?","có","không",["Báo lỗi trường email"]),
  ("dec",S,"Đã quá 60 giây và dưới 5 mã/giờ?","đạt","chưa",["Báo đợi hoặc quá số lần gửi"]),
  ("act",S,"Hủy mã cũ, lưu OTP đã băm (hạn 10 phút)",DB),
  ("dec",S,"Gửi email SMTP thành công?","có","không",["Báo lỗi, hoàn tác (không lưu mã)"]),
  ("act",K,"Nhập mã OTP 6 chữ số"),
  ("dec",S,"Mã còn hạn, đúng chủ và đúng giá trị?","đúng","sai",["Tăng số lần sai hoặc hủy mã, báo lỗi"]),
  ("act",S,"Cấp vé ký HMAC-SHA256 gắn với mã và email"),
  ("act",K,"Nhập mật khẩu mới và xác nhận"),
  ("dec",S,"Vé hợp lệ và hai mật khẩu khớp?","đạt","không",["Báo lỗi ticket hoặc confirmPassword"]),
  ("act",S,"Đánh dấu mã đã dùng, cập nhật password_hash",DB),("end",K)],
 codes=[("Bước 1: gửi mã OTP","service/PasswordResetService.java",75,98),
        ("Bước 2: xác minh mã OTP","service/PasswordResetService.java",108,139),
        ("Bước 3: đặt mật khẩu mới","service/PasswordResetService.java",148,169)],
 explain=("requestOtp kiểm tra tài khoản, thời gian chờ và hạn mức gửi, hủy các mã còn mở, lưu mã đã băm BCrypt vào PasswordResetOtp "
          "rồi gọi OtpMailSender; nếu gửi thất bại, BadRequestException làm giao dịch hoàn tác. verifyOtp (noRollbackFor để vẫn ghi số lần "
          "sai) kiểm tra mã thuộc đúng tài khoản và email, còn hạn, chưa sai quá 5 lần, so khớp bằng PasswordEncoder rồi trả về vé. "
          "resetPassword đọc và kiểm tra chữ ký vé, đối chiếu hai mật khẩu, 'tiêu thụ' mã đúng một lần và cập nhật mật khẩu bằng "
          "UserRepository.updatePasswordHash. Các nhánh lỗi trong sơ đồ tương ứng với các BadRequestException trong ba đoạn mã."),
 trace=dict(controller="PasswordResetController (/forgot-password, /forgot-password/verify, /forgot-password/reset)", service="PasswordResetService, OtpMailSender",
            ui="templates/auth/forgot-password.html, verify-otp.html, reset-password.html", data="PasswordResetOtp, User; PasswordResetOtpRepository"),
 status="Hoạt động (gửi thư cần cấu hình MAIL_HOST; không có SMTP thì chức năng báo chưa gửi được)",
),
dict(
 id="KH-04", title="Xem danh sách, tìm kiếm và lọc sản phẩm", actor="Khách vãng lai, khách hàng",
 desc=("Trang /products hiển thị sản phẩm đang bán, hỗ trợ tìm theo từ khóa (tên hoặc SKU), lọc theo danh mục và thương hiệu, phân trang "
       "12 sản phẩm. Nếu khách đã chọn một chi nhánh để xem (lưu trong cookie), danh mục được lấy từ hàng bán được của chi nhánh đó; nếu "
       "chưa chọn, danh mục tính trên toàn chuỗi kèm danh sách chi nhánh còn hàng. Mọi sản phẩm dùng một giá online duy nhất. "
       "Chức năng không yêu cầu đăng nhập."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Mở /products, nhập từ khóa, chọn danh mục, thương hiệu"),
  ("act",S,"SelectedStoreInterceptor đọc chi nhánh đã chọn từ cookie"),
  ("dec",S,"Tham số phân trang hợp lệ?","hợp lệ","sai",["Từ chối yêu cầu (lỗi tham số phân trang)"]),
  ("dec",S,"Đã chọn chi nhánh để xem?","chưa chọn","đã chọn",["getStoreCatalog: hàng bán được của chi nhánh","Truy vấn sản phẩm ACTIVE và tồn của chi nhánh"]),
  ("act",S,"getChainCatalog: tìm sản phẩm ACTIVE theo từ khóa, danh mục, thương hiệu",DB),
  ("act",S,"Nạp ảnh chính và các chi nhánh còn hàng của cả trang",DB),
  ("act",S,"Dựng trang product/list; tải danh mục, thương hiệu để lọc"),
  ("act",K,"Xem kết quả, đổi bộ lọc hoặc trang"),("end",K)],
 codes=[("ProductController.list","controller/client/ProductController.java",52,69),
        ("Tìm kiếm sản phẩm hiển thị","service/impl/ProductServiceImpl.java",78,91)],
 explain=("ProductController.list tạo ProductSearchCriteria từ q, categoryId, brandId và chọn getStoreCatalog khi request có thuộc tính "
          "chi nhánh đã chọn, ngược lại getChainCatalog. ProductServiceImpl.searchProducts kiểm tra tham số phân trang bằng Paging.validate, "
          "gọi ProductRepository.searchVisible (chỉ sản phẩm hiển thị) hoặc lọc theo nhiều từ khóa bằng TextSearch, rồi gắn ảnh chính qua "
          "getPrimaryImageUrls. Dữ liệu trả về là các record ProductResponse, không lộ entity ra giao diện."),
 trace=dict(controller="ProductController (GET /products, /products/suggest)", service="ProductServiceImpl.searchProducts, StoreProductServiceImpl.getChainCatalog / getStoreCatalog",
            ui="templates/product/list.html", data="Product, ProductImage, StoreProduct, Category, Brand; ProductRepository, StoreProductRepository"),
 status="Hoạt động",
),
dict(
 id="KH-05", title="Xem chi tiết sản phẩm và tình trạng còn hàng theo chi nhánh", actor="Khách vãng lai, khách hàng",
 desc=("Trang /products/{id} cho xem mô tả, giá, bộ ảnh, tình trạng còn hàng tại từng chi nhánh và các đánh giá của khách. Sản phẩm không "
       "hiển thị (ngừng bán hoặc danh mục/thương hiệu bị ẩn) trả về 404. Khi khách đã đăng nhập, trang còn liệt kê các dòng hàng "
       "đã nhận có thể đánh giá. Chức năng này đồng thời là cách khách tra cứu tình trạng còn hàng theo từng chi nhánh."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Mở /products/{id}"),
  ("act",S,"Tìm sản phẩm hiển thị (findVisibleById)",DB),
  ("dec",S,"Sản phẩm tồn tại và đang hiển thị?","có","không",["Trả 404 Không tìm thấy sản phẩm"]),
  ("act",S,"Nạp ảnh sản phẩm và tồn theo từng chi nhánh bán được",DB),
  ("act",S,"Nạp đánh giá ACTIVE, số lượng và điểm trung bình",DB),
  ("act",S,"Nếu đã đăng nhập: nạp dòng hàng đã nhận có thể đánh giá",DB),
  ("act",S,"Dựng trang product/detail"),("end",K)],
 codes=[("ProductController.detail","controller/client/ProductController.java",71,78),
        ("Chi tiết sản phẩm và tồn theo chi nhánh","service/impl/StoreProductServiceImpl.java",152,165)],
 explain=("ProductController.detail gọi StoreProductService.getProductDetail, ReviewService.getProductReviews và getProductReviewItems "
          "(rỗng nếu chưa đăng nhập). getProductDetail dùng ProductService.getProduct để ném ResourceNotFoundException khi sản phẩm không "
          "hiển thị (ứng với nhánh 404), sau đó lấy danh sách StoreProduct bán được của sản phẩm và chuyển thành "
          "StoreAvailabilityResponse. Trang chi tiết không phụ thuộc chi nhánh đã chọn nên tham số selectedStoreId được truyền null."),
 trace=dict(controller="ProductController.detail (GET /products/{id})", service="StoreProductServiceImpl.getProductDetail, ProductServiceImpl.getProduct, ReviewServiceImpl.getProductReviews",
            ui="templates/product/detail.html", data="Product, ProductImage, StoreProduct, Review; StoreProductRepository, ReviewRepository"),
 status="Hoạt động",
),
dict(
 id="KH-06", title="Tra cứu và chọn chi nhánh", actor="Khách vãng lai, khách hàng",
 desc=("Trang /stores liệt kê chi nhánh của chuỗi, lọc theo tỉnh/thành và khu vực, hiển thị địa chỉ, giờ mở cửa, khả năng giao tận nơi hoặc "
       "nhận tại cửa hàng. Khách có thể chọn một chi nhánh để duyệt sản phẩm theo tồn kho của chi nhánh đó; lựa chọn được lưu trong cookie "
       "và có thể bỏ chọn để quay về toàn chuỗi. Việc chọn chi nhánh này chỉ ảnh hưởng đến cách xem danh mục, không quyết định nguồn hàng "
       "khi đặt đơn."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Mở /stores, chọn tỉnh/thành và khu vực"),
  ("act",S,"findStores: lọc chi nhánh theo tỉnh/thành, khu vực",DB),
  ("act",S,"Hiển thị danh sách chi nhánh, giờ mở cửa, hỗ trợ giao/nhận"),
  ("act",K,"Bấm chọn một chi nhánh (POST /stores/select)"),
  ("dec",S,"Chi nhánh tồn tại và ACTIVE?","có","không",["Chuyển /stores?error=unavailable"]),
  ("act",S,"Ghi cookie chi nhánh đã chọn"),
  ("act",S,"Chuyển về returnTo nếu là đường dẫn nội bộ, mặc định /products"),("end",K)],
 codes=[("Chọn chi nhánh","controller/client/StoreController.java",42,51),
        ("Tra cứu chi nhánh theo tỉnh/thành, khu vực","service/impl/StoreServiceImpl.java",50,58)],
 explain=("StoreController.select chỉ ghi cookie sau khi StoreService.requireSelectableStore xác nhận chi nhánh tồn tại và ACTIVE; nếu không, "
          "nó chuyển về /stores?error=unavailable. Hàm localPath (cùng controller) chỉ chấp nhận đường dẫn bắt đầu bằng '/' của chính website "
          "để tránh chuyển hướng mở sang miền khác. StoreServiceImpl.findStores lọc trong bộ nhớ theo tỉnh/thành và khu vực (chuỗi chỉ có vài "
          "chi nhánh) và trả StoreResponse."),
 trace=dict(controller="StoreController (GET /stores, POST /stores/select, /stores/clear)", service="StoreServiceImpl.findStores / requireSelectableStore, SelectedStoreCookie",
            ui="templates/store/list.html", data="Store; StoreRepository"),
 status="Hoạt động",
),
dict(
 id="KH-07", title="Thêm sản phẩm vào giỏ hàng", actor="Khách hàng",
 desc=("Khách đã đăng nhập thêm sản phẩm vào giỏ hàng từ trang danh sách hoặc chi tiết. Giỏ hàng V3 chứa các dòng theo sản phẩm (SKU), "
       "không gắn cố định chi nhánh; tồn kho được kiểm tra trên tổng tồn bán được toàn chuỗi. Hệ thống khóa giỏ của khách trong giao dịch, "
       "cộng dồn nếu sản phẩm đã có và từ chối khi số lượng sau cộng vượt tồn. Giá không bao giờ được gửi từ trình duyệt."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Bấm 'Thêm vào giỏ' (productId, quantity)"),
  ("dec",S,"Dữ liệu hợp lệ, có productId hoặc storeProductId?","có","không",["Trả 400, hiển thị giỏ kèm lỗi"]),
  ("act",S,"Tìm sản phẩm hiển thị (findVisibleById)",DB),
  ("dec",S,"Sản phẩm đang bán và giá > 0?","có","không",["Báo sản phẩm không còn bán"]),
  ("act",S,"Khóa giỏ của khách hoặc tạo giỏ mới; tìm dòng hiện có",DB),
  ("act",S,"Tính tồn bán được toàn chuỗi (tổng StoreProduct)",DB),
  ("dec",S,"Tồn > 0 và số lượng sau cộng ≤ tồn?","đủ","thiếu",["Báo hết hàng hoặc không đủ hàng"]),
  ("act",S,"Tạo hoặc cộng dồn CartItem (saveAndFlush)",DB),
  ("act",S,"Chuyển hướng /cart?success=added"),("end",K)],
 codes=[("CartController.add","controller/client/CartController.java",43,55),
        ("Khóa giỏ, kiểm tra tồn, lưu dòng hàng","service/impl/CartServiceImpl.java",164,198)],
 explain=("CartController.add bắt BadRequestException và hiển thị lại giỏ với mã 400. Trong doAddItem, giỏ được khóa bằng findByUserIdForUpdate "
          "(hoặc tạo mới), số lượng sau khi cộng so với getChainAvailableStock; vượt tồn thì ném BadRequestException. Dòng CartItem được tạo mới "
          "hoặc cập nhật số lượng rồi saveAndFlush. Nếu hai yêu cầu song song cùng tạo giỏ hoặc dòng, addItem bắt "
          "DataIntegrityViolationException và chạy lại một lần để cộng vào bản ghi vừa được commit."),
 trace=dict(controller="CartController.add (POST /cart/items)", service="CartServiceImpl.addItem / doAddItem",
            ui="Nút 'Thêm vào giỏ' trong templates/product/list.html, detail.html; templates/cart/index.html", data="Cart, CartItem, Product, StoreProduct; CartRepository, CartItemRepository"),
 status="Hoạt động",
),
dict(
 id="KH-08", title="Cập nhật số lượng và xóa sản phẩm trong giỏ hàng", actor="Khách hàng",
 desc=("Tại trang /cart, khách đổi số lượng hoặc xóa một dòng hàng. Chỉ dòng thuộc giỏ của chính khách mới được thao tác (không có tham số "
       "nào nêu tên người dùng hay giỏ). Khi cập nhật, hệ thống kiểm tra số lượng tối thiểu 1, sản phẩm còn bán và số lượng không vượt tổng "
       "tồn toàn chuỗi. Khi xóa, dòng hàng bị loại khỏi giỏ."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Đổi số lượng hoặc bấm xóa một dòng hàng"),
  ("dec",S,"Thao tác là cập nhật số lượng?","cập nhật","xóa",["Khóa giỏ, tìm dòng hàng của khách, xóa CartItem","Chuyển /cart?success=removed"]),
  ("dec",S,"Số lượng ≥ 1?","có","không",["Báo số lượng tối thiểu là 1"]),
  ("act",S,"Khóa giỏ, tìm dòng hàng thuộc giỏ của khách",DB),
  ("dec",S,"Tìm thấy dòng hàng trong giỏ của khách?","có","không",["404 Không tìm thấy trong giỏ"]),
  ("dec",S,"Sản phẩm còn hiển thị và giá > 0?","có","không",["Báo không còn bán, gợi ý xóa"]),
  ("dec",S,"Số lượng ≤ tồn toàn chuỗi?","đủ","thiếu",["Báo hết hàng hoặc không đủ"]),
  ("act",S,"Cập nhật quantity của CartItem",DB),
  ("act",S,"Chuyển hướng /cart?success=updated"),("end",K)],
 codes=[("Cập nhật và xóa dòng hàng","service/impl/CartServiceImpl.java",214,248)],
 explain=("updateItemQuantity gọi requirePositive, ownItem (tìm dòng bằng giỏ đã khóa của khách, nếu không thấy ném ResourceNotFoundException), "
          "kiểm tra sản phẩm còn hiển thị rồi so sánh số lượng với getChainAvailableStock trước khi gán quantity. removeItem dùng chính ownItem "
          "để chỉ xóa dòng của khách hiện tại. CartController.update/remove chuyển kết quả thành các redirect /cart?success=updated|removed, "
          "còn lỗi nghiệp vụ được hiển thị lại trên trang giỏ."),
 trace=dict(controller="CartController.update, remove (POST /cart/items/{id}, /cart/items/{id}/delete)", service="CartServiceImpl.updateItemQuantity, removeItem",
            ui="templates/cart/index.html", data="CartItem; CartItemRepository"),
 status="Hoạt động",
),
dict(
 id="KH-09", title="Xem trước đơn hàng và áp mã khuyến mãi", actor="Khách hàng",
 desc=("Từ giỏ hàng, khách chọn các dòng muốn mua và mở trang thanh toán /checkout. Hệ thống tính trước kế hoạch đặt hàng mà không ghi dữ liệu: "
       "cách nhận (giao tận nơi hoặc nhận tại cửa hàng), phương thức thanh toán hợp lệ, nguồn hàng được phân bổ cho từng chi nhánh, số đơn sẽ "
       "tạo, phí giao hàng 30.000 VNĐ mỗi đơn (nhận tại cửa hàng miễn phí) và giảm giá nếu khách nhập hoặc chọn mã khuyến mãi. Kết quả gồm "
       "một chữ ký kế hoạch dùng để phát hiện thay đổi khi khách xác nhận."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Chọn dòng hàng trong giỏ, mở /checkout"),
  ("dec",S,"Các dòng chọn thuộc giỏ và còn mua được?","có","không",["Quay lại trang giỏ kèm lý do"]),
  ("act",S,"Nạp người nhận, địa chỉ gần nhất; mặc định giao hàng + COD",DB),
  ("dec",S,"Cách nhận, địa chỉ và chi nhánh nhận hợp lệ?","hợp lệ","không",["Hiển thị 'problem' trên trang thanh toán"]),
  ("act",S,"Phân bổ thử nguồn hàng theo tồn các chi nhánh",DB),
  ("dec",S,"Phân bổ thành công?","có","không",["Hiển thị lý do thiếu hàng"]),
  ("act",S,"Liệt kê mã khuyến mãi dùng được; kiểm tra mã khách nhập",DB),
  ("act",S,"Tính tổng = tiền hàng − giảm + phí giao × số đơn; tạo chữ ký kế hoạch"),
  ("act",S,"Hiển thị trang checkout/index"),("end",K)],
 codes=[("CheckoutServiceImpl.preview: chọn nhánh xử lý","service/impl/CheckoutServiceImpl.java",203,236),
        ("PromotionService.problem: điều kiện mã khuyến mãi","service/promotion/PromotionService.java",158,178)],
 explain=("Phương thức preview là giao dịch chỉ đọc. Nó lần lượt loại các trường hợp giao hàng chưa mở, địa chỉ sai hoặc chưa chọn chi nhánh nhận, "
          "rồi gọi StoreAllocationService.preview; nếu thất bại, failureMessage tạo lý do hiển thị. Khi thành công, nó tính số đơn (COD giao từ "
          "nhiều chi nhánh sẽ tách thành nhiều đơn độc lập), lấy các mã khả dụng và kiểm tra mã nhập bằng PromotionService.check, "
          "tính tổng tiền và gắn contextSignature vào kế hoạch. PromotionService.problem quyết định mã có dùng được hay không "
          "(trạng thái, thời gian, số lượt toàn hệ thống và của từng khách, mức tiền hàng tối thiểu); lý do lỗi của mã không chặn việc xem đơn."),
 trace=dict(controller="CheckoutController.form (GET /checkout)", service="CheckoutServiceImpl.preview, StoreAllocationServiceImpl.preview, PromotionService",
            ui="templates/checkout/index.html", data="Cart, CartItem, StoreProduct, Promotion, CustomerAddress; CheckoutProperties"),
 status="Hoạt động",
),
dict(
 id="KH-10", title="Đặt hàng (phân bổ nguồn hàng, tạo Order và Fulfillment)", actor="Khách hàng",
 desc=("Khi khách xác nhận, hệ thống tạo đơn trong một giao dịch duy nhất theo mô hình V3. Cart khóa theo khách, các StoreProduct ứng viên bị khóa "
       "theo id tăng dần rồi AllocationEngine phân bổ: ưu tiên một chi nhánh đủ toàn bộ giỏ; nếu không có thì ghép nhiều chi nhánh. Thanh toán ONLINE "
       "tạo một Order có nhiều OrderFulfillment (trạng thái PENDING_PAYMENT và một Payment chờ); COD giao từ nhiều chi nhánh chỉ được tạo khi khách "
       "đồng ý tách thành các Order độc lập; nhận tại cửa hàng cần một chi nhánh đủ hàng. Tồn kho bị trừ ngay khi tạo đơn và có một "
       "InventoryMovement loại ORDER. Chỉ các dòng đã đặt mới bị xóa khỏi giỏ."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Xác nhận đặt hàng (POST /checkout)"),
  ("dec",S,"Chọn ≥ 1 dòng; cách nhận, thanh toán, người nhận, địa chỉ hợp lệ?","có","không",["Báo lỗi trên trang thanh toán"]),
  ("dec",S,"ONLINE và giao hàng đang được mở?","mở","đóng",["Báo luồng chưa mở (409)"]),
  ("act",S,"Khóa giỏ của khách, lấy các dòng đã chọn",DB),
  ("dec",S,"Đủ dòng; sản phẩm còn bán, giá > 0?","có","không",["Báo giỏ đã thay đổi hoặc ngừng bán"]),
  ("act",S,"Khóa StoreProduct (id tăng dần) và phân bổ AllocationEngine",DB),
  ("dec",S,"Phân bổ thành công?","có","không",["409 Không phân bổ được"]),
  ("dec",S,"COD tách nhiều chi nhánh đã có đồng ý (nếu cần)?","đạt","thiếu",["409 Cần đồng ý tách đơn"]),
  ("act",S,"Tính tiền hàng từng đơn; khóa và áp mã khuyến mãi",DB),
  ("dec",S,"Tổng tiền và chữ ký kế hoạch còn đúng?","đúng","đổi",["409 Giá hoặc nguồn hàng đã thay đổi"]),
  ("act",S,"Tạo CheckoutSession; ghi lượt dùng mã",DB),
  ("act",S,"Tạo Order, OrderFulfillment, OrderItem; trừ tồn (ORDER)",DB),
  ("act",S,"Ghi lịch sử; ONLINE: tạo Payment PENDING; xóa dòng khỏi giỏ",DB),
  ("act",S,"Chuyển /checkout/{id}?success=placed"),("end",K)],
 codes=[("Khóa giỏ, kiểm tra SKU và phân bổ","service/impl/CheckoutServiceImpl.java",312,345),
        ("Tạo phiên thanh toán và các Order","service/impl/CheckoutServiceImpl.java",405,424),
        ("Tạo Fulfillment, OrderItem và trừ tồn","service/impl/CheckoutServiceImpl.java",461,484),
        ("AllocationEngine: ưu tiên một chi nhánh đủ giỏ","service/allocation/AllocationEngine.java",52,66)],
 explain=("placeOrder kiểm tra lựa chọn rồi khóa Cart; các sản phẩm phải còn ACTIVE với giá lớn hơn 0 và giá luôn lấy từ Product, không từ yêu cầu. "
          "allocateForUpdate khóa StoreProduct theo thứ tự id để tránh deadlock và trả AllocationResult; AllocationEngine.allocate thử "
          "'Strategy 1' (một chi nhánh đủ toàn bộ giỏ, chọn chi nhánh có tổng tồn lớn nhất) trước khi chuyển sang ghép nhiều chi nhánh. Sau khi kiểm tra "
          "đồng ý tách đơn, tổng tiền và chữ ký, createOrder tạo Order, OrderFulfillment cho từng chi nhánh, OrderItem gắn StoreProduct và gọi "
          "InventoryService.deductForOrder; Order ONLINE ở PENDING_PAYMENT, các loại khác ở CONFIRMED. Mọi lỗi nghiệp vụ ném CheckoutConflictException "
          "hoặc BadRequestException khiến toàn bộ giao dịch hoàn tác, đúng với các nhánh lỗi trong sơ đồ."),
 trace=dict(controller="CheckoutController.placeOrder (POST /checkout), result (GET /checkout/{id})", service="CheckoutServiceImpl.placeOrder / createOrder, StoreAllocationServiceImpl.allocateForUpdate, AllocationEngine, InventoryServiceImpl.deductForOrder, PromotionService",
            ui="templates/checkout/index.html, checkout/result.html", data="CheckoutSession, Order, OrderFulfillment, OrderItem, OrderStatusHistory, OrderFulfillmentStatusHistory, InventoryMovement, StoreProduct, Payment"),
 status="Hoạt động (ONLINE dùng cổng thanh toán mô phỏng; profile prod đóng ONLINE)",
),
dict(
 id="KH-11", title="Thanh toán trực tuyến qua cổng mô phỏng", actor="Khách hàng",
 desc=("Với đơn ONLINE ở trạng thái PENDING_PAYMENT, khách có 15 phút để thanh toán tại /orders/{id}/payments. Hệ thống không có cổng thật: nút "
       "'Thanh toán' kích hoạt cổng mô phỏng ở phía máy chủ; trình duyệt chỉ chọn kịch bản, còn trạng thái, số tiền và thời điểm do máy chủ ghi. "
       "Kết quả được xác minh bằng sổ cái của cổng: thu thành công trước hạn thì đơn thành CONFIRMED và PAID; thu sau hạn thì hủy đơn, hoàn tồn và "
       "hoàn tiền mô phỏng; thất bại hoặc quá hạn thì hủy đơn và hoàn tồn; kết quả không xác định thì chờ đối soát."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Mở /orders/{id}/payments, bấm Thanh toán"),
  ("dec",S,"Đơn ONLINE, đang chờ thanh toán, còn hạn 15 phút?","có","không",["Báo không còn chờ thanh toán hoặc quá hạn"]),
  ("act",S,"Khóa đơn; lấy hoặc tạo Payment PENDING",DB),
  ("dec",S,"Cổng mô phỏng đang bật?","bật","tắt",["Báo cổng thanh toán không bật"]),
  ("act",S,"Cổng mô phỏng ghi nhận kết quả vào sổ cái",DB),
  ("act",S,"reconcileLocked: xác minh kết quả với sổ cái",DB),
  ("dec",S,"Kết quả SUCCESS và thu trước hạn?","đúng","khác",["Quá hạn/FAILED: hủy, hoàn tồn; UNKNOWN: chờ đối soát"]),
  ("act",S,"Payment SUCCESS; đơn PAID và CONFIRMED; ghi lịch sử",DB),
  ("act",S,"Chuyển /orders/{id}/payments?outcome=CONFIRMED"),("end",K)],
 codes=[("PaymentController: bắt đầu thanh toán","controller/client/PaymentController.java",46,53),
        ("PaymentReconciliationService.simulate","service/payment/PaymentReconciliationService.java",97,129),
        ("reconcileLocked: bảng quyết định","service/payment/PaymentReconciliationService.java",167,194)],
 explain=("PaymentController.start tạo (hoặc lấy) lần thanh toán đang chờ rồi gọi PaymentReconciliationService.simulate với kịch bản SUCCESS. "
          "simulate khóa đơn của chính khách, kiểm tra trạng thái sổ cái (SETTLED trả ALREADY_PAID, đơn đã hủy chỉ ghi nhận khoản thu muộn rồi hoàn tiền) "
          "và để cổng capture, decline hoặc markInDoubt. reconcileLocked áp bảng quyết định: SUCCESS trước hạn gọi confirm (PAID, CONFIRMED); SUCCESS sau hạn "
          "gọi cancelForLatePayment; FAILED gọi OrderCancellationService.cancelAndRestore; NOT_PAID chờ hoặc hủy khi hết hạn; UNKNOWN chờ, vào cửa sổ đối soát "
          "hoặc chuyển NEEDS_ATTENTION. Các nhánh này được gom thành nhánh 'khác' của sơ đồ để giữ sơ đồ dễ đọc."),
 trace=dict(controller="PaymentController (GET /orders/{id}/payments, POST .../attempts, .../simulate)", service="PaymentServiceImpl.createOnlinePaymentAttempt, PaymentReconciliationService.simulate / reconcileLocked, MockPaymentGateway, MockLedger",
            ui="templates/payment/index.html", data="Payment, Order, OrderStatusHistory; PaymentRepository"),
 status="Hoạt động (cổng mô phỏng, không kết nối ngân hàng thật)",
),
dict(
 id="KH-12", title="Theo dõi đơn hàng", actor="Khách hàng",
 desc=("Khách xem danh sách đơn của mình tại /orders và chi tiết một đơn tại /orders/{id}: sản phẩm, tổng tiền, trạng thái đơn và thanh toán, các phần giao "
       "theo từng chi nhánh với trạng thái riêng, mã vận đơn hoặc mã nhận hàng, lịch sử đơn, khoản hoàn tiền, dòng hàng có thể đánh giá và khả năng trả hàng. "
       "Chỉ đơn của chính khách mới xem được. Khi đơn ONLINE đang chờ thanh toán đã quá 15 phút, việc mở trang kích hoạt đối soát: hủy đơn và hoàn tồn."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Mở /orders, chọn một đơn (/orders/{id})"),
  ("act",S,"Lấy đơn theo id và email của khách",DB),
  ("dec",S,"Đơn thuộc về khách hiện tại?","có","không",["404 Không tìm thấy đơn hàng"]),
  ("act",S,"ONLINE chờ thanh toán đã quá hạn: đối soát, hủy đơn, hoàn tồn",DB),
  ("act",S,"Nạp sản phẩm, phần giao theo chi nhánh, lịch sử trạng thái",DB),
  ("act",S,"Nạp khoản hoàn tiền, dòng hàng có thể đánh giá, yêu cầu và điều kiện trả hàng",DB),
  ("act",S,"Tính canCancel theo chính sách; hiển thị orders/detail"),("end",K)],
 codes=[("CustomerOrderController.detail","controller/client/CustomerOrderController.java",51,80),
        ("Lấy đơn của chính khách","service/impl/CustomerOrderServiceImpl.java",69,74)],
 explain=("CustomerOrderController.detail gọi CustomerOrderService.getOrder, vốn dùng findByIdAndUserEmail nên đơn của người khác cho kết quả "
          "ResourceNotFoundException (404). Với đơn ONLINE đang PENDING_PAYMENT, controller kiểm tra hạn thanh toán và gọi "
          "PaymentReconciliationService.reconcile trước khi đọc lại đơn. Sau đó nó nạp thêm thông tin hoàn tiền, các dòng hàng có thể đánh giá, yêu cầu trả hàng "
          "và cờ canCancel từ OrderTransitionPolicy. Mã đơn hiển thị cho khách là số thứ tự riêng của khách, còn đường dẫn vẫn dùng id thật."),
 trace=dict(controller="CustomerOrderController (GET /orders, /orders/{id})", service="CustomerOrderServiceImpl.getOrders / getOrder, OrderViewService, OrderTransitionPolicy",
            ui="templates/orders/index.html, orders/detail.html", data="Order, OrderItem, OrderFulfillment, OrderStatusHistory, Payment"),
 status="Hoạt động",
),
dict(
 id="KH-13", title="Hủy đơn hàng", actor="Khách hàng",
 desc=("Khách chỉ được hủy khi cửa hàng chưa bắt đầu chuẩn bị. Đơn ONLINE chờ thanh toán được hủy (trừ khi kết quả cổng đang chờ đối soát); đơn ở trạng thái "
       "CONFIRMED mà mọi phần giao còn CONFIRMED được hủy: tồn kho hoàn lại đúng StoreProduct nguồn một lần, các phần giao thành CANCELLED, và đơn ONLINE đã thanh toán "
       "được hoàn tiền toàn phần (mô phỏng). Đơn đang xử lý, đang giao hoặc chờ quản trị viên xử lý bị từ chối. Yêu cầu lặp lại không gây tác dụng phụ."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Bấm Hủy đơn (POST /orders/{id}/cancel)"),
  ("act",S,"Khóa đơn theo khóa chính; kiểm tra thuộc khách",DB),
  ("dec",S,"Đơn tồn tại và thuộc khách?","có","không",["404 Không tìm thấy đơn hàng"]),
  ("dec",S,"Đơn chưa ở trạng thái CANCELLED?","chưa","đã hủy",["Trả ALREADY_CANCELLED, không đổi gì"]),
  ("act",S,"ONLINE: đối soát kết quả cổng trước khi hủy"),
  ("dec",S,"Đang PENDING_PAYMENT?","không","có",["Từ chối nếu cổng UNKNOWN; còn lại hủy, hoàn tồn","Kết quả CANCELLED"]),
  ("dec",S,"Trạng thái CONFIRMED (mọi phần còn CONFIRMED)?","đúng","không",["Từ chối: đã chuẩn bị hoặc chờ quản trị viên"]),
  ("dec",S,"Thanh toán hợp lệ: ONLINE đã PAID, hoặc COD/tại quầy chưa thu?","đúng","không",["Từ chối: trạng thái thanh toán không hợp lệ"]),
  ("act",S,"cancelAndRestore: hủy các phần giao, hoàn tồn CANCEL_ORDER, đóng thanh toán",DB),
  ("act",S,"ONLINE đã PAID: mô phỏng hoàn tiền toàn phần (REFUNDED)",DB),
  ("act",S,"Chuyển /orders/{id}?cancelled"),("end",K)],
 codes=[("CustomerOrderCancellationService.cancel (1)","service/payment/CustomerOrderCancellationService.java",56,88),
        ("CustomerOrderCancellationService.cancel (2)","service/payment/CustomerOrderCancellationService.java",89,115),
        ("OrderCancellationService.cancelAndRestore","service/payment/OrderCancellationService.java",65,86)],
 explain=("CustomerOrderServiceImpl.cancelOrder ủy quyền cho CustomerOrderCancellationService.cancel. Phương thức này khóa Order bằng khóa chính rồi kiểm tra chủ sở hữu "
          "trên dòng đã khóa; với ONLINE nó gọi reconcileLocked để áp dụng kết quả thanh toán đang chờ trước (thanh toán thắng hủy). Nhánh PENDING_PAYMENT hủy và "
          "hoàn tồn nếu kết quả không phải UNKNOWN; nhánh CONFIRMED yêu cầu mọi phần giao còn CONFIRMED, ONLINE phải PAID và được hoàn tiền mô phỏng qua RefundService. "
          "OrderCancellationService.cancelAndRestore khóa các Fulfillment, từ chối nếu có phần đã chuẩn bị và kiểm tra tồn chưa từng được hoàn "
          "trước khi gọi InventoryService.restoreForCancelledOrder."),
 trace=dict(controller="CustomerOrderController.cancel (POST /orders/{id}/cancel)", service="CustomerOrderServiceImpl.cancelOrder, CustomerOrderCancellationService.cancel, OrderCancellationService.cancelAndRestore, RefundService.simulate, InventoryServiceImpl.restoreForCancelledOrder",
            ui="Nút 'Hủy đơn' trong templates/orders/detail.html", data="Order, OrderFulfillment, InventoryMovement, Payment, PaymentRefund"),
 status="Hoạt động",
),
dict(
 id="KH-14", title="Yêu cầu trả hàng sau khi nhận", actor="Khách hàng",
 desc=("Khi đơn đã COMPLETED, khách có 3 ngày kể từ lúc phần giao cuối cùng hoàn tất để yêu cầu trả toàn bộ hàng đã nhận, kèm lý do 5–500 ký tự và 1–3 ảnh. "
       "Hệ thống kiểm tra điều kiện dưới khóa đơn, sao chép các dòng hàng của những phần đã COMPLETED, tải ảnh lên Cloudinary và lưu yêu cầu ở trạng thái "
       "REQUESTED chờ quản trị viên duyệt. Số tiền hoàn là giá trị hàng sau khuyến mãi, phí giao hàng không hoàn. Trạng thái đơn và phần giao không thay đổi."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Mở /orders/{id}/return, nhập lý do, đính 1–3 ảnh"),
  ("dec",S,"Lý do 5–500 ký tự và có 1–3 ảnh?","có","không",["Báo lỗi reason hoặc images"]),
  ("act",S,"Khóa đơn; kiểm tra thuộc khách",DB),
  ("dec",S,"Chưa có yêu cầu, đơn COMPLETED, mọi phần đã xong?","có","không",["Từ chối: đã có yêu cầu hoặc đơn chưa hoàn tất"]),
  ("dec",S,"Còn trong 3 ngày kể từ khi nhận hàng?","còn hạn","quá hạn",["Từ chối: đã quá hạn trả hàng"]),
  ("dec",S,"Có dòng đã nhận, tiền hàng > 0 và còn đủ để hoàn?","có","không",["Từ chối: không có khoản để hoàn"]),
  ("act",S,"Lưu OrderReturn (REQUESTED) và các OrderReturnItem",DB),
  ("act",S,"Tải ảnh lên Cloudinary, lưu OrderReturnImage",DB),
  ("act",S,"Chờ quản trị viên duyệt; đơn không đổi trạng thái"),("end",K)],
 codes=[("Ghi nhận yêu cầu trả hàng","service/returns/CustomerOrderReturnService.java",128,148),
        ("Điều kiện trả hàng (1)","service/returns/CustomerOrderReturnService.java",181,206),
        ("Điều kiện trả hàng (2)","service/returns/CustomerOrderReturnService.java",207,226)],
 explain=("request kiểm tra lý do và ảnh bằng cleanReason và photos, khóa đơn của khách rồi tính plan(order). plan lần lượt từ chối khi đã có yêu cầu, đơn chưa COMPLETED, "
          "còn phần giao chưa xong hoặc đã quá RETURN_WINDOW (3 ngày); sau đó lấy các OrderItem thuộc phần đã COMPLETED, tính tiền hàng bằng getNetSubtotal và so với số tiền "
          "còn có thể hoàn của khoản thanh toán thành công. Khi đủ điều kiện, yêu cầu và các dòng hàng được lưu, ảnh được tải qua ImageTransactionCoordinator. Mọi lý do từ chối "
          "là BadRequestException với thông báo riêng, khớp các nhánh của sơ đồ."),
 trace=dict(controller="CustomerOrderReturnController (GET/POST /orders/{id}/return)", service="CustomerOrderReturnService.request / plan",
            ui="templates/orders/return-form.html (liên kết từ orders/detail.html)", data="OrderReturn, OrderReturnItem, OrderReturnImage; ImageTransactionCoordinator (Cloudinary)"),
 status="Hoạt động",
),
dict(
 id="KH-15", title="Đánh giá sản phẩm đã nhận", actor="Khách hàng",
 desc=("Khách chỉ đánh giá được dòng hàng đã nhận và thanh toán hợp lệ, mỗi dòng hàng một lần, với số sao từ 1 đến 5 và nhận xét tùy chọn. Đánh giá gắn với OrderItem "
       "nên truy được chi nhánh nguồn để nhân viên đúng chi nhánh phản hồi. Hệ thống khóa đơn để các lần gửi trùng được tuần tự hóa, lưu Review ở trạng thái ACTIVE "
       "và chuyển về trang đơn hoặc trang sản phẩm."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Chọn dòng hàng đã nhận, số sao, nhận xét, Gửi đánh giá"),
  ("dec",S,"Có dòng hàng, số sao 1–5, nhận xét hợp lệ?","có","không",["Chuyển ?reviewError=invalid"]),
  ("act",S,"Tìm khóa dòng hàng thuộc khách; khóa đơn",DB),
  ("dec",S,"Dòng hàng thuộc khách và đơn khớp?","có","không",["404 Không tìm thấy dòng hàng"]),
  ("dec",S,"Phần giao đã nhận và thanh toán hợp lệ?","có","không",["Báo chỉ đánh giá hàng đã nhận"]),
  ("dec",S,"Dòng hàng chưa được đánh giá?","chưa","rồi",["Báo đã đánh giá dòng hàng này"]),
  ("act",S,"Lưu Review ACTIVE (gắn sản phẩm, dòng hàng, đơn)",DB),
  ("act",S,"Chuyển ?reviewed#reviews"),("end",K)],
 codes=[("ReviewServiceImpl.submit","service/impl/ReviewServiceImpl.java",62,97)],
 explain=("submit kiểm tra orderItemId, số sao và nhận xét (ReviewContent.comment), tìm khóa dòng hàng thuộc đúng khách rồi khóa Order trước khi đọc dữ liệu giao nhận, "
          "đúng thứ tự khóa của các luồng khác. ReviewSource.received xác nhận phần giao đã COMPLETED và thanh toán hợp lệ; reviews.existsByOrderItemId chặn đánh giá trùng. "
          "Review được lưu bằng saveAndFlush; ràng buộc duy nhất của CSDL bắt trường hợp hai yêu cầu đồng thời. ReviewController chuyển BadRequestException thành tham số "
          "reviewError trên URL để giao diện hiển thị thông báo."),
 trace=dict(controller="ReviewController.submit (POST /reviews)", service="ReviewServiceImpl.submit, ReviewSource",
            ui="Biểu mẫu đánh giá trong templates/orders/detail.html và product/detail.html", data="Review, OrderItem, Order; ReviewRepository"),
 status="Hoạt động",
),
dict(
 id="KH-16", title="Cập nhật thông tin tài khoản", actor="Khách hàng",
 desc=("Tại /account, người dùng đã đăng nhập sửa họ tên và số điện thoại. Email và tên đăng nhập là định danh bất biến, chỉ hiển thị. Việc cập nhật dựa hoàn toàn "
       "vào Principal hiện tại và thực hiện bằng một câu lệnh UPDATE chỉ áp dụng cho tài khoản ACTIVE nên tài khoản bị khóa không thể ghi."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Sửa họ tên, số điện thoại, bấm Lưu (POST /account)"),
  ("dec",S,"Dữ liệu hợp lệ (UpdateAccountRequest)?","có","không",["Hiển thị lại form kèm lỗi"]),
  ("act",S,"UPDATE users: full_name, phone, updated_at theo email và status ACTIVE",DB),
  ("dec",S,"Đúng một dòng được cập nhật?","có","không",["403 Tài khoản không còn hoạt động"]),
  ("act",S,"Chuyển hướng /account?success"),("end",K)],
 codes=[("AccountController.updateProfile","controller/client/AccountController.java",58,72),
        ("AccountProfileService.update","service/AccountProfileService.java",57,64)],
 explain=("AccountController.updateProfile kiểm tra BindingResult rồi gọi AccountProfileService.update với Principal.getName(). Phương thức này có @PreAuthorize bảo đảm email "
          "tham số trùng người đang đăng nhập và gọi UserRepository.updateProfileFields; kết quả khác 1 nghĩa là tài khoản không còn ACTIVE nên ném AccessDeniedException. "
          "Nhờ vậy không thể sửa hồ sơ của người khác hay khôi phục thuộc tính bảo mật cũ."),
 trace=dict(controller="AccountController (GET/POST /account)", service="AccountProfileService.update",
            ui="templates/account/profile.html", data="User; UserRepository.updateProfileFields"),
 status="Hoạt động",
),
dict(
 id="KH-17", title="Đổi mật khẩu", actor="Khách hàng (và mọi tài khoản đã đăng nhập)",
 desc=("Người dùng đổi mật khẩu tại /account bằng cách nhập mật khẩu hiện tại, mật khẩu mới và xác nhận. Hệ thống yêu cầu mật khẩu hiện tại đúng, hai mật khẩu mới khớp "
       "và mật khẩu mới khác mật khẩu cũ. Khi có lỗi, không lưu gì và biểu mẫu không nhận lại giá trị mật khẩu đã nhập."),
 lanes=LANES,
 steps=[("start",K),("act",K,"Nhập mật khẩu hiện tại, mật khẩu mới, xác nhận"),
  ("dec",S,"Biểu mẫu hợp lệ (độ dài, không trống)?","có","không",["Hiển thị lỗi, xóa trắng các ô mật khẩu"]),
  ("act",S,"Tải user ACTIVE theo email",DB),
  ("dec",S,"Mật khẩu hiện tại khớp BCrypt?","khớp","sai",["Báo lỗi currentPassword"]),
  ("dec",S,"Mật khẩu mới trùng ô xác nhận?","khớp","không",["Báo lỗi confirmPassword"]),
  ("dec",S,"Mật khẩu mới khác mật khẩu hiện tại?","khác","trùng",["Báo lỗi newPassword"]),
  ("act",S,"UPDATE password_hash (BCrypt)",DB),
  ("act",S,"Chuyển hướng /account?passwordChanged"),("end",K)],
 codes=[("AccountController.changePassword","controller/client/AccountController.java",75,96),
        ("AccountProfileService.changePassword","service/AccountProfileService.java",36,53)],
 explain=("changePassword của AccountProfileService tải user đang ACTIVE, so khớp mật khẩu hiện tại bằng PasswordEncoder.matches, đối chiếu xác nhận và yêu cầu mật khẩu mới khác mật khẩu cũ; "
          "mỗi lỗi là BadRequestException có tên trường. AccountController gắn lỗi vào đúng trường bằng bindingResult.rejectValue, "
          "xóa các giá trị mật khẩu khỏi đối tượng form và hiển thị lại trang. Khi thành công, updatePasswordHash ghi mật khẩu đã băm."),
 trace=dict(controller="AccountController.changePassword (POST /account/password)", service="AccountProfileService.changePassword",
            ui="templates/account/profile.html (khối Đổi mật khẩu)", data="User; UserRepository.updatePasswordHash"),
 status="Hoạt động",
),
]
