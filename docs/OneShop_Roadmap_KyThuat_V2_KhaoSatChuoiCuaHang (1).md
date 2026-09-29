**ROADMAP KỸ THUẬT ĐỒ ÁN CUỐI KỲ**

**XÂY DỰNG WEBSITE BÁN MỸ PHẨM ONESHOP**

**THEO MÔ HÌNH CHUỖI CỬA HÀNG**

**Bản V2 - Tối ưu sau khảo sát hệ thống bán lẻ mỹ phẩm thực tế**

Ngày khảo sát: 29/09/2026

**CÔNG NGHỆ GIỮ NGUYÊN**

**Spring Boot + Thymeleaf + Bootstrap + JPA + SQL Server  
Decorator SiteMesh + JWT + Cloudinary**

Nguyên tắc: làm nổi bật “chuỗi cửa hàng”, tăng chiều sâu nghiệp vụ, nhưng không mở rộng thành hệ thống thương mại điện tử quy mô lớn.

# 1\. MỤC TIÊU CỦA BẢN ROADMAP V2

Bản V2 giữ nguyên toàn bộ phạm vi công nghệ của đề tài, nhưng chỉnh lại mô hình nghiệp vụ để “chuỗi cửa hàng” trở thành trục xuyên suốt của hệ thống thay vì chỉ là một bảng Store. Mỗi sản phẩm bán được phải được gắn với một chi nhánh thông qua StoreProduct; tồn kho và khả năng bán được kiểm soát theo chi nhánh; giỏ hàng được nhóm theo chi nhánh; mỗi Order thuộc đúng một Store; Staff chỉ xử lý dữ liệu của Store được phân công.

Roadmap được tối ưu dựa trên khảo sát website/hệ thống thật của Watsons Việt Nam, Guardian Việt Nam, Hasaki, Sephora và Ulta Beauty. Chỉ các chức năng quan sát được từ website/tài liệu chính thức mới được dùng làm căn cứ. Không suy đoán kiến trúc backend nội bộ của các hệ thống này.

## 1.1. Nguyên tắc giới hạn phạm vi

- Không đổi frontend sang React/Vue/Angular; không bổ sung microservice, Redis, Elasticsearch, Docker hoặc message queue.
- Không xây dựng ERP, quản lý nhà cung cấp, kho trung tâm, chuyển kho liên chi nhánh hoặc tối ưu logistics.
- Không xây dựng loyalty phức tạp, voucher engine, recommendation AI, chatbot, virtual try-on, subscription hoặc marketplace seller.
- Không tích hợp bản đồ/geolocation phức tạp; người dùng chủ động chọn khu vực và chi nhánh.
- Chỉ bổ sung dữ liệu/bảng có tác dụng trực tiếp cho mô hình chuỗi, tính toàn vẹn tồn kho hoặc khả năng theo dõi đơn.

# 2\. PHẠM VI CÔNG NGHỆ - GIỮ NGUYÊN 100%

| **Công nghệ** | **Vai trò trong OneShop V2** |
| --- | --- |
| Spring Boot | Ứng dụng backend dạng monolith; Controller - Service - Repository; transaction và nghiệp vụ. |
| Thymeleaf | Render giao diện phía server cho Client, Staff và Admin. |
| Bootstrap | Responsive UI, form, modal, table, card, navbar, sidebar. |
| JPA | ORM, mapping entity, truy vấn và locking cần thiết với SQL Server. |
| SQL Server | CSDL duy nhất; PK/FK/UNIQUE/CHECK/INDEX và transaction. |
| Decorator SiteMesh | Layout dùng chung riêng cho Client, Staff, Admin. |
| JWT | Xác thực/phân quyền; lưu cookie HttpOnly, backend kiểm tra role và Store. |
| Cloudinary | Lưu ảnh sản phẩm/thương hiệu; SQL Server chỉ lưu URL/public_id. |

# 3\. KHẢO SÁT HỆ THỐNG THỰC TẾ

Mục tiêu khảo sát không phải sao chép chức năng, mà tìm các pattern phù hợp với OneShop: chọn cửa hàng, xem khả dụng theo cửa hàng, nhận tại cửa hàng, giao hàng dựa trên điểm có hàng, quản lý thông tin chi nhánh và cách xử lý sản phẩm nhiều màu/kích thước.

| **Hệ thống thật** | **Điểm đã xác minh từ nguồn chính thức** | **Ý nghĩa với OneShop** |
| --- | --- | --- |
| Watsons Việt Nam | Trang sản phẩm có “Kiểm tra tình trạng còn hàng tại cửa hàng”, hỗ trợ giao tận nhà và Click & Collect. Trang cửa hàng có địa chỉ, giờ mở cửa và dịch vụ như Click & Collect. | Rất gần đề tài: một website thống nhất nhưng trải nghiệm mua phụ thuộc cửa hàng được chọn. |
| Guardian Việt Nam | Có hệ thống nhiều cửa hàng, bộ lọc Store Locator theo khu vực, hiển thị địa chỉ/giờ hoạt động; có giao siêu tốc 2h ở một số thành phố. | Khẳng định Store Finder và metadata chi nhánh là chức năng quan trọng của chuỗi. |
| Hasaki | NowFree 2H chỉ khả dụng khi địa chỉ thuộc khu vực hỗ trợ và sản phẩm có sẵn tại kho/điểm gần nhất xử lý giao nhanh. | Cho thấy fulfillment phải dựa trên khả dụng tồn kho; nhưng auto-routing được loại khỏi phạm vi OneShop. |
| Sephora | Cho chọn Store ngay từ sản phẩm để Buy Online & Pick Up; gửi thông báo khi sẵn sàng; pickup có thời hạn và cơ chế xác minh người nhận. | Nên có luồng READY_FOR_PICKUP và mã nhận hàng, nhưng không cần hệ thống thông báo ngoài website. |
| Ulta Beauty | Có Check in-store availability, Pickup theo Store, cart có thể phân biệt Pickup và Ship to Home; trang sản phẩm thể hiện SKU/color/size. | Củng cố việc tách availability/fulfillment theo điểm bán và xử lý SKU mỹ phẩm rõ ràng. |

## 3.1. Các pattern nên áp dụng

- Selected Store: người dùng có một chi nhánh đang chọn; sản phẩm, giá và trạng thái còn hàng ưu tiên hiển thị theo chi nhánh đó.
- Store availability: ngay tại trang chi tiết sản phẩm phải có khả năng xem sản phẩm còn/hết tại các chi nhánh.
- Store Locator: lọc chi nhánh theo tỉnh/thành, khu vực; hiển thị địa chỉ, điện thoại, giờ hoạt động và dịch vụ cơ bản.
- BOPIS/Store Pickup: đơn pickup được xử lý bởi Store sở hữu hàng; có READY_FOR_PICKUP và mã nhận hàng.
- Fulfillment theo điểm bán: mỗi Order gắn đúng một Store để Staff, tồn kho và lịch sử đơn không bị nhập nhằng.
- Không hiển thị số tồn chính xác cho khách; Client chỉ cần Còn hàng/Hết hàng, Staff/Admin mới thấy quantity.

## 3.2. Những gì KHÔNG nên bê nguyên vào đồ án

| **Chức năng ngoài thực tế** | **Quyết định** | **Lý do** |
| --- | --- | --- |
| Giao 2h tự động chọn cửa hàng/kho gần nhất | Không triển khai lõi | Cần định vị, vùng giao, routing và logic logistics; vượt phạm vi. |
| Curbside pickup | Không triển khai | Không tạo giá trị học thuật đáng kể so với Store Pickup thông thường. |
| Loyalty/điểm thưởng/voucher engine | Không triển khai | Tăng nhiều bảng và rule, không làm rõ mô hình chuỗi cốt lõi. |
| Virtual try-on/AI recommendation | Không triển khai | Ngoài công nghệ và mục tiêu môn học. |
| Warehouse/DC/ship-from-store tối ưu | Không triển khai | Biến đề tài thành OMS/logistics lớn. |
| Product Variant hierarchy đầy đủ | Không thêm bảng riêng | Để kiểm soát quy mô, OneShop coi mỗi SKU bán được (ví dụ từng màu son) là một Product độc lập; cách này vẫn phù hợp với dữ liệu mỹ phẩm và tồn kho theo Store. |
| Promotion theo từng channel/store | Không triển khai engine | Giữ StoreProduct.price; không xây promotion rules phức tạp. |

## 3.3. Quyết định quan trọng về SKU mỹ phẩm

Sephora/Ulta thể hiện nhiều màu/kích thước dưới cùng một trang sản phẩm, trong khi Watsons cũng có các trang/mã sản phẩm riêng cho từng màu cụ thể. Với đồ án OneShop, cách tối ưu là coi mỗi đơn vị có thể bán và quản lý tồn độc lập là một Product/SKU độc lập. Ví dụ “BBIA Downy Cheek #06” và “#08” là hai Product khác nhau. Nhờ đó không cần thêm ProductVariant và không phải sửa toàn bộ Cart, OrderItem, StoreProduct, ảnh và review.

Quy ước phạm vi:  
PRODUCT = một SKU bán được cụ thể  
STORE_PRODUCT = SKU đó tại một Store cụ thể  
\=> tồn kho vẫn chính xác theo màu/kích thước mà không phát sinh module Variant riêng

# 4\. MÔ HÌNH NGHIỆP VỤ ONESHOP V2

OneShop là website thương mại điện tử thống nhất của một chuỗi cửa hàng mỹ phẩm. Product là danh mục SKU dùng chung toàn chuỗi. StoreProduct xác định SKU nào đang bán tại Store nào, giá bao nhiêu, tồn kho bao nhiêu và trạng thái bán. Đây là điểm phân biệt căn bản giữa OneShop và website một cửa hàng.

ONE WEBSITE  
|  
+-- Store A -> StoreProduct / Stock / Orders / Staff  
+-- Store B -> StoreProduct / Stock / Orders / Staff  
+-- Store C -> StoreProduct / Stock / Orders / Staff  
<br/>Product là dữ liệu toàn chuỗi; StoreProduct và Order là dữ liệu gắn chi nhánh.

## 4.1. Các nguyên tắc nghiệp vụ cuối cùng

| **Mã** | **Nguyên tắc** |
| --- | --- |
| BR-01 | Một Product là một SKU bán được cụ thể và có thể xuất hiện tại nhiều Store. |
| BR-02 | StoreProduct là nguồn sự thật về price, quantity và status của SKU tại một Store. |
| BR-03 | Client có thể duyệt toàn chuỗi hoặc chọn selectedStoreId để chuyển sang ngữ cảnh một chi nhánh. |
| BR-04 | Khi không chọn Store, kết quả cho biết SKU đang có ở những Store nào; khi đã chọn Store, chỉ trả dữ liệu StoreProduct của Store đó. |
| BR-05 | CartItem luôn tham chiếu StoreProduct; do đó một SKU ở hai Store là hai lựa chọn mua khác nhau. |
| BR-06 | Cart có thể chứa CartItem từ nhiều Store và UI phải nhóm rõ theo Store. |
| BR-07 | Một checkout có thể tạo nhiều Order; một Order chỉ thuộc đúng một Store. |
| BR-08 | Mỗi Store group tại checkout có fulfillment_type và payment_method riêng. |
| BR-09 | Checkout luôn tải lại price/quantity từ DB, lock/kiểm tra tồn và trừ kho trong transaction. |
| BR-10 | Staff không duyệt việc có cho tạo Order hay không; hệ thống tự xác nhận đơn hợp lệ. Staff chỉ xử lý fulfillment. |
| BR-11 | STORE_PICKUP sử dụng READY_FOR_PICKUP và pickup_code. |
| BR-12 | Hủy Order hợp lệ phải hoàn tồn đúng StoreProduct và ghi nhận biến động kho. |
| BR-13 | Mọi thay đổi trạng thái Order được kiểm tra transition ở Service và ghi OrderStatusHistory. |
| BR-14 | Staff chỉ truy cập Store được phân công; điều kiện Store phải được kiểm tra ở backend. |
| BR-15 | Client chỉ thấy Còn hàng/Hết hàng; số lượng tồn chính xác chỉ dành cho Staff/Admin. |
| BR-16 | Không tự chuyển CartItem sang Store khác khi người dùng đổi selectedStoreId. |
| BR-17 | Không xóa cứng dữ liệu đã có lịch sử giao dịch; dùng status INACTIVE/HIDDEN. |
| BR-18 | Payment gắn với Order, không gắn trực tiếp CheckoutSession, vì một checkout có thể sinh nhiều Order và mỗi Order có phương thức thanh toán khác nhau. |

# 5\. TÁC NHÂN VÀ CHỨC NĂNG

| **Tác nhân** | **Chức năng trong V2** |
| --- | --- |
| Khách vãng lai | Xem trang chủ; tìm sản phẩm toàn chuỗi; lọc Category/Brand/Store; xem availability theo Store; xem danh sách/chi tiết chi nhánh; đăng ký/đăng nhập. |
| Khách hàng | Chọn Store; thêm StoreProduct vào cart; giỏ nhóm theo Store; chọn item cần checkout; chọn DELIVERY/STORE_PICKUP theo từng Store group; chọn phương thức thanh toán; theo dõi/hủy đơn; xem pickup_code; đánh giá sau COMPLETED. |
| Nhân viên cửa hàng | Xem Store được phân công; xem và xử lý Order của Store; PREPARING/PACKED/SHIPPING/READY_FOR_PICKUP/COMPLETED; xác minh mã pickup; xem/cập nhật tồn thực tế; xem lịch sử biến động kho. |
| Quản trị viên | Quản lý User/Role/Staff assignment; Store; Category; Brand; Product/SKU; Product Image; StoreProduct; tồn toàn chuỗi; Order toàn hệ thống; Review; xem tổng quan theo Store. |

## 5.1. Chức năng tự động của hệ thống

- Xác thực JWT, role và Store scope trước khi Controller/Service xử lý nghiệp vụ bảo vệ.
- Tìm Product + StoreProduct phù hợp với selectedStoreId và trạng thái ACTIVE.
- Nhóm CartItem theo Store để hiển thị và checkout.
- Tính lại giá/tổng tiền ở backend, không tin dữ liệu client.
- Lock/validate stock trước khi tạo Order; trừ/hoàn tồn và ghi InventoryMovement.
- Sinh pickup_code khi Order pickup chuyển READY_FOR_PICKUP.
- Kiểm tra transition trạng thái theo fulfillment type.
- Ghi OrderStatusHistory cho mọi lần đổi trạng thái.

# 6\. LUỒNG HOẠT ĐỘNG CHI TIẾT

## 6.1. Chọn chi nhánh và duyệt sản phẩm theo Store

1.  Khách mở danh sách chi nhánh, lọc theo tỉnh/thành và khu vực.
2.  Mỗi Store hiển thị tên, địa chỉ, điện thoại, giờ hoạt động, hỗ trợ DELIVERY/PICKUP và trạng thái.
3.  Khi khách chọn Store, hệ thống lưu selectedStoreId trong session/cookie.
4.  Danh sách sản phẩm chuyển sang truy vấn StoreProduct của Store đã chọn.
5.  Trang chi tiết hiển thị giá và Còn hàng/Hết hàng tại Store hiện tại, kèm nút “Xem chi nhánh khác”.
6.  Nếu đổi Store, CartItem cũ không bị đổi nguồn Store.

Không chọn Store -> tìm Product trên toàn chuỗi + danh sách Store đang bán  
Chọn Store 3 -> chỉ hiển thị StoreProduct(store_id = 3, ACTIVE)

## 6.2. Thêm giỏ hàng và nhóm theo chi nhánh

1.  Client gửi storeProductId + quantity.
2.  Backend kiểm tra StoreProduct ACTIVE và quantity đủ.
3.  Nếu Cart đã có cùng storeProductId thì cộng quantity; nếu chưa thì tạo CartItem.
4.  Khi render cart, group CartItem theo Store.
5.  Mỗi Store group có checkbox “Chọn cả chi nhánh”; từng item vẫn chọn riêng.
6.  Khi đổi quantity, backend kiểm tra tồn mới nhất.

GIỎ HÀNG  
\+ OneShop Thủ Đức  
\[x\] Serum A x2  
\[ \] Son #06 x1  
\+ OneShop Gò Vấp  
\[x\] Toner C x1  
\[x\] Son #08 x1

## 6.3. Checkout nhiều chi nhánh - thiết kế tối ưu

Để giữ đúng bản chất chuỗi nhưng không biến checkout thành hệ thống quá lớn, UI chia theo Store group. Mỗi group cho chọn cách nhận và thanh toán riêng. Backend vẫn xử lý trong cùng một CheckoutSession để lưu dấu “đây là một lần checkout”.

1.  Nhận danh sách cartItemId được chọn; tải lại toàn bộ CartItem/StoreProduct từ SQL Server.
2.  Group theo store_id.
3.  Với từng group: kiểm tra Store ACTIVE, StoreProduct ACTIVE và đủ tồn; khóa bản ghi tồn cần thiết.
4.  Tạo CheckoutSession.
5.  Tạo một Order cho mỗi Store group, gắn fulfillment_type/payment_method của group.
6.  Tạo OrderItem snapshot product_name, unit_price, quantity, subtotal.
7.  Trừ StoreProduct.quantity; ghi InventoryMovement type ORDER.
8.  Xóa CartItem đã checkout; giữ nguyên item không chọn.
9.  Nếu một bước thuộc transaction tạo đơn lỗi, rollback toàn bộ lần tạo đơn.

Checkout #100  
\+ Order #1001 -> Store A -> DELIVERY + COD  
\+ Order #1002 -> Store B -> STORE_PICKUP + ONLINE  
\+ Order #1003 -> Store C -> STORE_PICKUP + PAY_AT_STORE

## 6.4. Thanh toán - sửa điểm bất hợp lý của roadmap cũ

Payment chuyển sang gắn trực tiếp Order. Lý do: một CheckoutSession có thể tạo nhiều Order và mỗi Order có thể dùng COD, PAY_AT_STORE hoặc ONLINE khác nhau. CheckoutSession chỉ còn nhiệm vụ nhóm các Order phát sinh trong cùng thao tác checkout.

CHECKOUT_SESSION 1 ---- N ORDER 1 ---- N PAYMENT

- ONLINE: Order bắt đầu PENDING_PAYMENT; khi Payment SUCCESS -> CONFIRMED. Nếu Payment FAILED -> hủy Order theo rule và hoàn tồn.
- COD: Order được CONFIRMED, payment_status = UNPAID; hoàn tất giao hàng thì cập nhật PAID.
- PAY_AT_STORE: chỉ cho STORE_PICKUP; payment_status = UNPAID cho đến lúc khách nhận và thanh toán tại Store.
- Không triển khai payment gateway phức tạp nếu môn học không bắt buộc; PaymentService chỉ cần chuẩn hóa trạng thái và transaction_code nếu có.

## 6.5. DELIVERY

ONLINE: PENDING_PAYMENT -> CONFIRMED -> PREPARING -> PACKED -> SHIPPING -> COMPLETED  
COD: CONFIRMED -> PREPARING -> PACKED -> SHIPPING -> COMPLETED

Nhân viên của đúng Store nhận Order đã hợp lệ và chỉ thực hiện fulfillment. Việc xác nhận có đủ tồn để tạo đơn được hệ thống xử lý tự động trước đó.

## 6.6. STORE_PICKUP

ONLINE/PAY_AT_STORE: CONFIRMED -> PREPARING -> READY_FOR_PICKUP -> COMPLETED

1.  Store của Order chính là nơi khách nhận; không cho đổi Store sau khi Order đã tạo.
2.  Khi nhân viên chuyển READY_FOR_PICKUP, backend sinh pickup_code ngắn và ghi ready_at.
3.  Khách xem mã tại “Chi tiết đơn hàng”; không cần SMS/email ngoài hệ thống.
4.  Khi khách đến nhận, Staff xác minh pickup_code; nếu PAY_AT_STORE thì cập nhật thanh toán trước khi COMPLETED.
5.  Ghi picked_up_at và OrderStatusHistory.

## 6.7. Hủy đơn và hoàn kho

1.  Khách chỉ được hủy khi Order chưa vượt ngưỡng cho phép; Service kiểm tra trạng thái DB hiện tại.
2.  Order -> CANCELLED.
3.  Mỗi OrderItem cộng trả quantity vào đúng StoreProduct.
4.  Ghi InventoryMovement type CANCEL_ORDER với quantity_change dương.
5.  Đổi trạng thái, hoàn tồn và lịch sử phải cùng transaction.

## 6.8. Staff vận hành theo Store

1.  Sau đăng nhập xác định StaffStoreAssignment ACTIVE.
2.  Mọi query Order/StoreProduct của Staff luôn kèm store_id từ assignment.
3.  Staff không được nhập store_id tùy ý để mở dữ liệu Store khác.
4.  Cập nhật tồn thủ công phải ghi InventoryMovement type STOCK_ADJUST, quantity_before, quantity_after và staff_id.
5.  Dashboard Staff chỉ có thống kê nhỏ: đơn chờ xử lý, pickup chờ nhận, số SKU sắp hết/hết hàng. Không xây BI lớn.

# 7\. THIẾT KẾ CƠ SỞ DỮ LIỆU SQL SERVER V2

V2 giữ cấu trúc dữ liệu lõi của roadmap cũ, chỉ bổ sung hai bảng audit có giá trị trực tiếp cho chuỗi (inventory_movements, order_status_history), sửa Payment về Order và mở rộng Store/Order với vài trường cần thiết cho Store Finder và Pickup.

## 7.1. Sơ đồ quan hệ logic

ROLE 1--N USER  
USER 1--N CUSTOMER_ADDRESS  
USER 1--1 CART  
USER 1--N STAFF_STORE_ASSIGNMENT N--1 STORE  
CATEGORY 1--N PRODUCT N--1 BRAND  
PRODUCT 1--N PRODUCT_IMAGE  
PRODUCT 1--N STORE_PRODUCT N--1 STORE  
STORE_PRODUCT 1--N INVENTORY_MOVEMENT  
CART 1--N CART_ITEM N--1 STORE_PRODUCT  
USER 1--N CHECKOUT_SESSION 1--N ORDER N--1 STORE  
ORDER 1--N ORDER_ITEM  
ORDER 1--N PAYMENT  
ORDER 1--N ORDER_STATUS_HISTORY  
USER 1--N REVIEW N--1 PRODUCT

## 7.2. Danh sách bảng

| **Bảng** | **Mục đích** |
| --- | --- |
| roles | CUSTOMER/STAFF/ADMIN. |
| users | Tài khoản, role, trạng thái. |
| customer_addresses | Địa chỉ giao hàng. |
| stores | Chi nhánh, khu vực, giờ hoạt động, dịch vụ. |
| staff_store_assignments | Phân công Staff -> Store. |
| categories | Danh mục. |
| brands | Thương hiệu. |
| products | Một SKU bán được cụ thể; dữ liệu chung toàn chuỗi. |
| product_images | Ảnh Cloudinary. |
| store_products | Product tại Store: price, quantity, status. |
| inventory_movements | Lịch sử biến động tồn theo StoreProduct. |
| carts | Một cart/user. |
| cart_items | Item gắn StoreProduct. |
| checkout_sessions | Nhóm nhiều Order cùng một lần checkout. |
| orders | Mỗi Order đúng một Store, fulfillment/payment/status. |
| order_items | Snapshot SKU/giá/số lượng. |
| payments | Giao dịch/trạng thái thanh toán theo Order. |
| order_status_history | Lịch sử transition Order. |
| reviews | Đánh giá đã mua. |

## 7.3. Các bảng/trường cần chỉnh so với V1

| **Bảng** | **Trường trọng tâm V2** | **Ghi chú** |
| --- | --- | --- |
| stores | store_id, code, name, address, province_city, area, phone, opening_hours, delivery_enabled, pickup_enabled, status | Không cần tọa độ/map API; đủ cho filter và hiển thị Store Finder. |
| products | product_id, category_id, brand_id, sku UNIQUE, name, description, status, created_at, updated_at | Một Product = một SKU bán được; màu/size nằm trong name/description nếu có. |
| store_products | store_product_id, store_id, product_id, price, quantity, status, updated_at; UNIQUE(store_id,product_id) | Nguồn sự thật về availability/tồn/giá theo Store. |
| inventory_movements | movement_id, store_product_id, type, quantity_change, quantity_before, quantity_after, reference_order_id, staff_id, note, created_at | ORDER/CANCEL_ORDER/STOCK_ADJUST. Không xây module nhập kho lớn. |
| checkout_sessions | checkout_id, user_id, total_amount, status, created_at | Status thiên về CREATED/PARTIAL/COMPLETED/CANCELLED; không dùng làm nguồn thanh toán. |
| orders | order_id, checkout_id, user_id, store_id, fulfillment_type, payment_method, payment_status, order_status, receiver_\*, shipping_address, pickup_code, ready_at, picked_up_at, total_amount, timestamps | pickup_\* chỉ dùng STORE_PICKUP. |
| payments | payment_id, order_id, method, amount, status, transaction_code, paid_at, created_at | Sửa FK từ checkout_id -> order_id. |
| order_status_history | history_id, order_id, old_status, new_status, changed_by_user_id, note, changed_at | Audit đơn; hỗ trợ timeline Client/Staff/Admin. |

## 7.4. Ràng buộc và index

- UNIQUE users.email; UNIQUE products.sku; UNIQUE store_products(store_id, product_id); UNIQUE cart_items(cart_id, store_product_id).
- CHECK store_products.quantity >= 0; price >= 0; review.rating BETWEEN 1 AND 5.
- Index store_products(store_id, status, quantity) cho catalog theo Store.
- Index store_products(product_id, status) cho availability toàn chuỗi.
- Index orders(store_id, order_status, created_at) cho Staff.
- Index orders(user_id, created_at) cho lịch sử khách.
- Index inventory_movements(store_product_id, created_at).
- Index order_status_history(order_id, changed_at).
- Không xóa cứng Product/Store/StoreProduct đã có lịch sử giao dịch.

# 8\. KIẾN TRÚC PHẦN MỀM

Browser  
|  
JWT Authentication / Authorization  
|  
Controller (client / staff / admin)  
|  
Service &lt;----&gt; CloudinaryService  
|  
Repository (JPA)  
|  
SQL Server  
<br/>View: Thymeleaf + Bootstrap + SiteMesh

Không thêm lớp kiến trúc mới. Các thay đổi V2 nằm trong domain model và Service hiện có. Tất cả quy tắc Store scope, stock, payment/order transition phải đặt trong Service; Controller chỉ nhận request, validate cơ bản và chuẩn bị ViewModel/DTO.

## 8.1. Service đề xuất

| **Service** | **Trách nhiệm** |
| --- | --- |
| AuthService | Đăng ký/đăng nhập/JWT/role. |
| ProductService | Catalog, Category, Brand, tìm kiếm SKU. |
| StoreService | Store Finder, selectedStoreId, Store metadata. |
| StoreProductService | Availability, price, stock theo Store. |
| CartService | CartItem gắn StoreProduct, group theo Store. |
| CheckoutService | Load lại DB, group Store, lock stock, tạo Session/Orders/Items. |
| OrderService | State transition, cancel, history, Store scope, pickup code. |
| PaymentService | Payment theo Order; ONLINE/COD/PAY_AT_STORE. |
| InventoryService | Trừ/hoàn/điều chỉnh tồn + InventoryMovement. |
| CloudinaryService | Upload/xóa ảnh, URL/public_id. |
| ReviewService | Kiểm tra đã mua và review. |

## 8.2. Quy tắc JPA/transaction quan trọng

- CheckoutService method tạo đơn dùng @Transactional.
- Khi nhiều khách mua SKU cuối cùng, repository phải lock StoreProduct hoặc dùng chiến lược cập nhật đảm bảo không âm kho; ưu tiên @Lock(PESSIMISTIC_WRITE) cho phạm vi đồ án vì dễ giải thích và kiểm thử.
- Không nhận unit_price/total_amount từ client làm nguồn sự thật.
- Order cancel + restore inventory + movement + status history phải atomic.
- Staff query không chỉ lọc ở UI; Service/Repository bắt buộc ràng buộc store_id.

# 9\. THIẾT KẾ MÀN HÌNH LÀM NỔI BẬT CHUỖI CỬA HÀNG

| **Khu vực** | **Màn hình / điểm nhấn V2** |
| --- | --- |
| Client | Header có “Chi nhánh đang chọn”; Store Finder; Catalog toàn chuỗi/ theo Store; Product Detail có availability các Store; Cart group theo Store; Checkout theo Store group; Order Detail hiển thị Store + pickup code/timeline. |
| Staff | Dashboard tên Store rõ ràng; Orders của Store; Pickup queue; Stock StoreProduct; Inventory history; không có màn dữ liệu toàn chuỗi. |
| Admin | Stores; Staff assignment; Product/SKU; StoreProduct matrix/list; tồn toàn chuỗi theo Store; Orders toàn hệ thống có filter Store; Review/User cơ bản. |

## 9.1. Product Detail chuẩn cho mô hình chuỗi

\[Tên SKU\] \[Giá tại Store đang chọn\]  
Chi nhánh đang chọn: OneShop Thủ Đức  
Tình trạng: CÒN HÀNG  
\[Thêm vào giỏ\]  
<br/>Xem tại chi nhánh khác:  
\- OneShop Gò Vấp Còn hàng  
\- OneShop Quận 10 Hết hàng  
\- OneShop Quận 7 Còn hàng  
\[Đổi chi nhánh\]

Không cần hiển thị số lượng tồn cho khách. Cách này vừa giống pattern của chuỗi thật, vừa giảm khả năng lộ dữ liệu kho và làm UI dễ hiểu.

# 10\. NHỮNG THAY ĐỔI TỐI ƯU SO VỚI ROADMAP V1

| **Hạng mục** | **V1** | **V2 tối ưu** |
| --- | --- | --- |
| Bản chất Product | Product chung, chưa quy định rõ variant | Quy định Product = một SKU bán được; không thêm ProductVariant. |
| Store Finder | Tên/địa chỉ/khu vực | Bổ sung province_city, area, opening_hours, delivery/pickup flags. |
| Availability | Có StoreProduct nhưng UI chưa nhấn mạnh | Product Detail và catalog thể hiện Còn hàng/Hết hàng theo Store. |
| Payment | Payment -> CheckoutSession | Payment -> Order để hỗ trợ multi-Store/multi-method rõ ràng. |
| Pickup | Có READY_FOR_PICKUP | Bổ sung pickup_code, ready_at, picked_up_at. |
| Inventory audit | Staff chỉnh quantity trực tiếp | Bổ sung InventoryMovement nhẹ, không mở rộng thành WMS. |
| Order tracking | Chỉ order_status hiện tại | Bổ sung OrderStatusHistory để có timeline. |
| Fast delivery | Có thể hiểu như chức năng tương lai | Không triển khai auto nearest/2H; giữ DELIVERY chuẩn. |
| Khuyến mãi/loyalty | Có thể phát sinh khi tham khảo web thật | Chủ động loại khỏi scope để tập trung vào chuỗi. |

# 11\. PHẠM VI ƯU TIÊN P0/P1/P2

| **Mức** | **Nội dung** |
| --- | --- |
| P0 - Bắt buộc | Auth/JWT/role; Store/Staff assignment; Product/StoreProduct; selected Store; availability theo Store; Cart nhiều Store; Checkout -> nhiều Order; stock transaction; Payment theo Order; DELIVERY/STORE_PICKUP; Staff xử lý Order; Admin cơ bản; Cloudinary. |
| P1 - Nên hoàn thiện | Store Finder metadata; pickup_code; OrderStatusHistory; InventoryMovement; review; địa chỉ khách; responsive và validation đầy đủ. |
| P2 - Chỉ khi còn thời gian | Dashboard thống kê đơn giản, cảnh báo sắp hết hàng theo ngưỡng, polish UI. |
| OUT OF SCOPE | Promotion engine, loyalty, AI, recommendation, virtual try-on, warehouse, transfer stock, courier integration, geolocation/routing, curbside, subscription, marketplace seller. |

# 12\. ROADMAP TRIỂN KHAI KỸ THUẬT V2

| **Giai đoạn** | **Công việc** | **Điều kiện hoàn thành** |
| --- | --- | --- |
| 1\. Chuẩn hóa domain chuỗi | Chốt Product=SKU, StoreProduct, selected Store, one Order-one Store, payment per Order, state machine. | Tài liệu không còn mâu thuẫn. |
| 2\. SQL Server schema | Tạo/điều chỉnh 19 bảng; constraint/index; seed 3-5 Store + SKU ở nhiều Store. | DB phản ánh rõ dữ liệu toàn chuỗi và theo Store. |
| 3\. Spring Boot foundation | Entity/Repository/Service/Controller; SQL Server/JPA; DTO/validation. | App kết nối DB ổn định. |
| 4\. SiteMesh + UI nền | Decorator client/staff/admin; Bootstrap components; header selected Store. | 3 khu vực UI ổn định. |
| 5\. Auth + JWT | Register/login/cookie HttpOnly/role/route protection. | Role và Store scope đúng. |
| 6\. Catalog + Store chain | CRUD Category/Brand/Product/Store; Cloudinary; StoreProduct; Store Finder; availability. | Khách thấy rõ SKU ở Store nào. |
| 7\. Cart nhiều Store | Add/update/delete; group Store; chọn Store/item; stock validation. | Giỏ thể hiện chuỗi rõ ràng. |
| 8\. Checkout + Orders | Group store_id; CheckoutSession; one Order/store; OrderItem snapshot; lock/trừ kho. | Một checkout sinh đúng Orders theo Store. |
| 9\. Payment + fulfillment | Payment per Order; DELIVERY/STORE_PICKUP; state transitions; pickup code. | Luồng mua hoàn chỉnh. |
| 10\. Staff Store operations | Order queue; pickup queue; state update; stock; inventory movement. | Staff chỉ xử lý đúng Store. |
| 11\. Admin toàn chuỗi | StoreProduct, inventory overview, Orders filter Store, staff assignment, review/user. | Admin quản lý được mạng lưới. |
| 12\. History + hardening | OrderStatusHistory; cancel/restore; permission tests; concurrency tests. | Dữ liệu nhất quán và audit được. |
| 13\. Deploy/demo | Env variables; SQL Server cloud; Cloudinary; build/deploy; seed demo; test script. | Demo ổn định. |

# 13\. BỘ KIỂM THỬ BẮT BUỘC TRƯỚC DEMO

| **Mã** | **Tình huống** | **Kết quả mong đợi** |
| --- | --- | --- |
| TC-01 | Một SKU có tại 3 Store | Global view hiển thị đúng availability/giá theo Store. |
| TC-02 | Chọn Store A rồi tìm | Chỉ StoreProduct Store A. |
| TC-03 | Product Detail đổi Store | Giá/availability đổi đúng; cart cũ không tự đổi Store. |
| TC-04 | Thêm cùng StoreProduct 2 lần | Một CartItem, quantity tăng. |
| TC-05 | Cart từ 3 Store | UI chia đúng 3 group. |
| TC-06 | Checkout item từ 3 Store | Tạo 3 Order, mỗi Order đúng một Store. |
| TC-07 | Hai khách mua SKU cuối | Không âm kho; chỉ transaction hợp lệ được commit. |
| TC-08 | Giá client bị sửa | Backend dùng StoreProduct.price. |
| TC-09 | DELIVERY + COD | CONFIRMED -> PREPARING -> PACKED -> SHIPPING -> COMPLETED. |
| TC-10 | STORE_PICKUP + PAY_AT_STORE | CONFIRMED -> PREPARING -> READY_FOR_PICKUP -> COMPLETED; mã pickup đúng. |
| TC-11 | Staff A mở Order B | Backend từ chối. |
| TC-12 | Hủy Order hợp lệ | CANCELLED + hoàn đúng StoreProduct + movement. |
| TC-13 | Staff chỉnh tồn | quantity thay đổi và có InventoryMovement STOCK_ADJUST. |
| TC-14 | Đổi giá sau mua | OrderItem cũ giữ unit_price snapshot. |
| TC-15 | Payment khác nhau trong cùng checkout | Mỗi Order có Payment/status độc lập; CheckoutSession vẫn nhóm các Order. |
| TC-16 | Timeline trạng thái | OrderStatusHistory ghi đủ transition. |
| TC-17 | Upload ảnh | Cloudinary lưu file; SQL Server chỉ URL/public_id. |
| TC-18 | Store INACTIVE | Không cho tạo Order mới, dữ liệu lịch sử vẫn xem được. |

# 14\. RỦI RO KỸ THUẬT VÀ CÁCH KIỂM SOÁT

| **Rủi ro** | **Kiểm soát trong V2** |
| --- | --- |
| Overselling SKU cuối | Lock/validate StoreProduct trong @Transactional; CHECK quantity >= 0. |
| Lẫn dữ liệu giữa Store | Mọi Staff query/service bắt buộc store_id từ assignment. |
| Giá request bị sửa | Backend đọc price từ StoreProduct. |
| Checkout nhiều Store lỗi giữa chừng | Transaction tạo Session/Orders/stock/cart; lỗi thì rollback. |
| Payment một Store lỗi | Payment gắn Order; có thể cancel/restore Order đó mà không làm mơ hồ Payment của Store khác. |
| Staff chỉnh stock không truy vết | InventoryMovement ghi before/after/staff/note. |
| Order nhảy sai trạng thái | Service state machine + OrderStatusHistory. |
| Xóa dữ liệu làm gãy lịch sử | Soft status INACTIVE/HIDDEN. |
| JWT cookie bị lạm dụng | HttpOnly, SameSite phù hợp, secret qua env; request thay đổi dữ liệu phải có bảo vệ CSRF phù hợp. |
| Mở rộng scope theo web thật | Tuân thủ OUT OF SCOPE; chỉ lấy pattern chuỗi cần thiết. |

# 15\. KIẾN TRÚC NGHIỆP VỤ CUỐI CÙNG

Khách tìm SKU  
|  
Product + StoreProduct + Store  
|  
+-- Toàn chuỗi -> xem Store availability  
+-- Chọn Store -> catalog theo Store  
|  
Add to Cart(storeProductId)  
|  
Cart group theo Store  
|  
Checkout các item được chọn  
|  
Group BY store_id  
|  
+-- Order Store A -> Payment A -> Fulfillment A  
+-- Order Store B -> Payment B -> Fulfillment B  
+-- Order Store C -> Payment C -> Fulfillment C  
|  
Staff của từng Store xử lý Order của chính Store  
|  
COMPLETED

Kết luận thiết kế: OneShop V2 làm nổi bật chuỗi cửa hàng ở đúng những nơi quan trọng nhất - Store selection, availability, StoreProduct, cart grouping, one Order-one Store, Staff scope và inventory audit. Những pattern nâng cao của Watsons/Guardian/Hasaki/Sephora/Ulta chỉ được áp dụng khi chúng làm rõ mô hình chuỗi mà không kéo đồ án sang logistics, loyalty, marketplace hoặc AI.

# 16\. NGUỒN KHẢO SÁT HỆ THỐNG THỰC TẾ

Các nguồn dưới đây đều là trang chính thức, được dùng để xác minh chức năng quan sát được. Roadmap không suy đoán công nghệ hay kiến trúc backend của các website này.

| **Nguồn** | **URL** |
| --- | --- |
| Watsons Việt Nam - Cách mua hàng Online | https://www.watsons.vn/vi/huong-dan-mua-hang |
| Watsons Việt Nam - Đặt hàng & thanh toán | https://www.watsons.vn/vi/howtopay |
| Watsons Việt Nam - Ví dụ trang cửa hàng | https://www.watsons.vn/vi/store/wtcvn_S0018_VI |
| Watsons Việt Nam - Ví dụ product có kiểm tra tồn tại cửa hàng | https://www.watsons.vn/vi/ma-nyo-s%E1%BB%AFa-r%E1%BB%ADa-m%E1%BA%B7t-ma-nyo-pure-deep-cleansing-foam-120ml/p/BP_218704 |
| Guardian Việt Nam - Hệ thống cửa hàng | https://www.guardian.com.vn/he-thong-cua-hang/ |
| Guardian Việt Nam - Giới thiệu chuỗi | https://www.guardian.com.vn/guardian/gioi-thieu |
| Guardian Việt Nam - Lịch hoạt động/giao hàng 2026 | https://www.guardian.com.vn/blog/lich-hoat-dong-tet-nguyen-dan-2026.html |
| Hasaki - Vận chuyển 2H | https://hotro.hasaki.vn/van-chuyen-2h.html |
| Hasaki - Hướng dẫn đặt hàng 2H | https://hotro.hasaki.vn/huong-dan-dat-hang-2h.html |
| Hasaki - Hệ thống cửa hàng | https://hotro.hasaki.vn/he-thong-cua-hang.html |
| Sephora - Buy Online, Pick Up In Store | https://www.sephora.com/beauty/in-store-pickup |
| Sephora - In-Store Pickup FAQ | https://www.sephora.com/beauty/in-store-pick-up-faq |
| Ulta Beauty - Pickup | https://www.ulta.com/guestservices/ways-to-shop/pickup |
| Ulta Beauty - Guest Services / in-store availability | https://www.ulta.com/guestservices/all?param1=workflows-deep-dive |
| Ulta Beauty - In Store | https://www.ulta.com/guestservices/ways-to-shop/in-store |

# 17\. CHECKLIST CHỐT PHẠM VI TRƯỚC KHI CODE

- ☐ Có ít nhất 3 Store demo và mỗi Store có tập StoreProduct/tồn khác nhau.
- ☐ Một Product/SKU có thể xuất hiện ở nhiều Store; không copy Product thành dữ liệu riêng cho từng Store.
- ☐ Header Client luôn thể hiện chi nhánh đang chọn hoặc trạng thái “Toàn chuỗi”.
- ☐ Product Detail có availability theo Store.
- ☐ CartItem gắn storeProductId và cart group theo Store.
- ☐ Checkout tạo đúng một Order cho mỗi Store group.
- ☐ Payment gắn Order; không dùng Payment của CheckoutSession làm nguồn duy nhất.
- ☐ Staff chỉ thấy dữ liệu Store được phân công.
- ☐ Pickup có READY_FOR_PICKUP + pickup_code.
- ☐ Tồn kho không âm và mọi hủy hợp lệ hoàn đúng StoreProduct.
- ☐ Không thêm các module OUT OF SCOPE chỉ vì website tham khảo có chúng.
- ☐ Toàn bộ triển khai vẫn dùng đúng Spring Boot + Thymeleaf + Bootstrap + JPA + SQL Server + SiteMesh + JWT + Cloudinary.