**ROADMAP TRIỂN KHAI THEO GIAI ĐOẠN**

**XÂY DỰNG WEBSITE BÁN MỸ PHẨM ONESHOP  
THEO MÔ HÌNH CHUỖI CỬA HÀNG**

_Bản phân rã thực thi bám 100% OneShop Roadmap Kỹ thuật V2_

**CÔNG NGHỆ GIỮ NGUYÊN 100%**

• Spring Boot + Thymeleaf + Bootstrap + JPA + SQL Server + Decorator SiteMesh + JWT + Cloudinary.

• Ứng dụng backend dạng monolith; không đổi stack, không thêm kiến trúc/công nghệ ngoài Roadmap V2.

• Chuỗi cửa hàng là trục nghiệp vụ: Product/SKU dùng chung toàn chuỗi, StoreProduct theo Store, Cart nhóm theo Store, một Order thuộc một Store, Staff bị giới hạn theo Store.

**Tài liệu này chỉ phân chia và diễn giải cách thực hiện những nội dung đã có trong Roadmap V2; không bổ sung nghiệp vụ mới.**

# 1\. NGUYÊN TẮC SỬ DỤNG TÀI LIỆU

**NGUỒN DUY NHẤT**

• OneShop Roadmap Kỹ thuật V2 - Tối ưu sau khảo sát hệ thống bán lẻ mỹ phẩm thực tế.

• Mọi mục tiêu, nghiệp vụ, bảng dữ liệu, Service, UI, test case và ràng buộc trong tài liệu này phải truy vết được về Roadmap V2.

• Nếu một chức năng không có trong Roadmap V2 thì không được tự thêm vào kế hoạch thực hiện.

## 1.1. Các ràng buộc toàn cục

- Giữ nguyên 100% công nghệ: Spring Boot, Thymeleaf, Bootstrap, JPA, SQL Server, SiteMesh, JWT, Cloudinary.
- Không đổi frontend sang React/Vue/Angular; không bổ sung microservice, Redis, Elasticsearch, Docker hoặc message queue.
- Không xây ERP, quản lý nhà cung cấp, kho trung tâm, chuyển kho liên chi nhánh, tối ưu logistics.
- Không xây loyalty phức tạp, voucher engine, recommendation AI, chatbot, virtual try-on, subscription hoặc marketplace seller.
- Không tích hợp bản đồ/geolocation phức tạp; người dùng chủ động chọn khu vực và chi nhánh.
- Không thêm ProductVariant; một Product được quy ước là một SKU bán được cụ thể.
- Chỉ bổ sung dữ liệu/bảng có tác dụng trực tiếp cho mô hình chuỗi, toàn vẹn tồn kho hoặc theo dõi đơn như Roadmap V2 đã chốt.
- Không xóa cứng Product/Store/StoreProduct đã có lịch sử giao dịch; dùng status INACTIVE/HIDDEN.
- Client chỉ thấy Còn hàng/Hết hàng; quantity chính xác chỉ Staff/Admin được xem.
- Controller chỉ nhận request, validate cơ bản và chuẩn bị ViewModel/DTO; quy tắc Store scope, stock, payment/order transition đặt trong Service.

## 1.2. Trục nghiệp vụ bắt buộc phải xuyên suốt

|     |     |
| --- | --- |
| **Product** | Một SKU bán được cụ thể, là dữ liệu chung toàn chuỗi. |
| **StoreProduct** | Nguồn sự thật về SKU tại một Store: price, quantity, status. |
| **Selected Store** | Client có thể duyệt toàn chuỗi hoặc chọn một Store làm ngữ cảnh. |
| **Cart** | CartItem gắn StoreProduct và giao diện nhóm theo Store. |
| **Checkout** | Một checkout có thể tạo nhiều Order; một Order chỉ thuộc đúng một Store. |
| **Payment** | Gắn trực tiếp Order; từng Order có thể có phương thức thanh toán riêng. |
| **Staff** | Chỉ truy cập và xử lý dữ liệu của Store được phân công. |
| **Inventory audit** | Trừ/hoàn/điều chỉnh tồn phải có InventoryMovement theo Roadmap V2. |
| **Order audit** | Mọi transition trạng thái được Service kiểm tra và ghi OrderStatusHistory. |

## 1.3. Phạm vi ưu tiên theo Roadmap V2

|     |     |
| --- | --- |
| **Mức** | **Nội dung** |
| P0 - Bắt buộc | Auth/JWT/role; Store/Staff assignment; Product/StoreProduct; selected Store; availability theo Store; Cart nhiều Store; Checkout -> nhiều Order; stock transaction; Payment theo Order; DELIVERY/STORE_PICKUP; Staff xử lý Order; Admin cơ bản; Cloudinary. |
| P1 - Nên hoàn thiện | Store Finder metadata; pickup_code; OrderStatusHistory; InventoryMovement; review; địa chỉ khách; responsive và validation đầy đủ. |
| P2 - Chỉ khi còn thời gian | Dashboard thống kê đơn giản, cảnh báo sắp hết hàng theo ngưỡng, polish UI. |
| OUT OF SCOPE | Promotion engine, loyalty, AI, recommendation, virtual try-on, warehouse, transfer stock, courier integration, geolocation/routing, curbside, subscription, marketplace seller. |

# 2\. TỔNG QUAN 13 GIAI ĐOẠN THỰC HIỆN

Thứ tự dưới đây giữ đúng Roadmap triển khai kỹ thuật V2. Giai đoạn sau chỉ được triển khai khi đầu ra cốt lõi của giai đoạn trước đã đạt điều kiện hoàn thành.

|     |     |     |     |
| --- | --- | --- | --- |
| **GĐ** | **Tên giai đoạn** | **Trọng tâm** | **Kết quả cuối** |
| 1   | Chuẩn hóa domain chuỗi | Chốt Product=SKU, StoreProduct, selected Store, one Order-one Store, Payment per Order, state machine. | Tài liệu không còn mâu thuẫn. |
| 2   | SQL Server schema | Tạo/điều chỉnh 19 bảng, constraint/index, seed 3-5 Store + SKU ở nhiều Store. | DB phản ánh rõ dữ liệu toàn chuỗi và theo Store. |
| 3   | Spring Boot foundation | Entity/Repository/Service/Controller; SQL Server/JPA; DTO/validation. | App kết nối DB ổn định. |
| 4   | SiteMesh + UI nền | Decorator client/staff/admin; Bootstrap components; header selected Store. | 3 khu vực UI ổn định. |
| 5   | Auth + JWT | Register/login/cookie HttpOnly/role/route protection. | Role và Store scope đúng. |
| 6   | Catalog + Store chain | CRUD Category/Brand/Product/Store; Cloudinary; StoreProduct; Store Finder; availability. | Khách thấy rõ SKU ở Store nào. |
| 7   | Cart nhiều Store | Add/update/delete; group Store; chọn Store/item; stock validation. | Giỏ thể hiện chuỗi rõ ràng. |
| 8   | Checkout + Orders | Group store_id; CheckoutSession; one Order/store; OrderItem snapshot; lock/trừ kho. | Một checkout sinh đúng Orders theo Store. |
| 9   | Payment + fulfillment | Payment per Order; DELIVERY/STORE_PICKUP; state transitions; pickup code. | Luồng mua hoàn chỉnh. |
| 10  | Staff Store operations | Order queue; pickup queue; state update; stock; inventory movement. | Staff chỉ xử lý đúng Store. |
| 11  | Admin toàn chuỗi | StoreProduct, inventory overview, Orders filter Store, staff assignment, review/user. | Admin quản lý được mạng lưới. |
| 12  | History + hardening | OrderStatusHistory; cancel/restore; permission tests; concurrency tests. | Dữ liệu nhất quán và audit được. |
| 13  | Deploy/demo | Env variables; SQL Server cloud; Cloudinary; build/deploy; seed demo; test script. | Demo ổn định. |

# GIAI ĐOẠN 1 - CHUẨN HÓA DOMAIN CHUỖI

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 3.3, mục 4, BR-01 đến BR-18, checklist mục 17 và Giai đoạn 1 của mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Chốt một mô hình nghiệp vụ duy nhất để toàn bộ phần Database, Backend và UI cùng dùng; không còn diễn giải khác nhau về SKU, StoreProduct, Cart, Order, Payment, Store Pickup và Store scope.

## 1\. Làm gì

- Chốt Product = một SKU bán được cụ thể; màu/kích thước nếu có nằm trong Product cụ thể, không tạo ProductVariant.
- Chốt StoreProduct là nguồn sự thật về price, quantity, status của SKU tại một Store.
- Chốt hai ngữ cảnh duyệt: Toàn chuỗi và selectedStoreId.
- Chốt CartItem luôn tham chiếu StoreProduct và Cart có thể chứa item từ nhiều Store.
- Chốt một checkout có thể sinh nhiều Order nhưng một Order chỉ thuộc đúng một Store.
- Chốt mỗi Store group có fulfillment_type và payment_method riêng; Payment gắn Order.
- Chốt state flow DELIVERY, STORE_PICKUP, hủy đơn, READY_FOR_PICKUP, pickup_code.
- Chốt Staff chỉ xử lý Store được phân công và backend phải kiểm tra Store scope.
- Chốt nguyên tắc stock: kiểm tra/lock ở backend, không âm kho, hoàn tồn khi hủy hợp lệ, có InventoryMovement.
- Chốt OrderStatusHistory cho transition trạng thái.

## 2\. Làm như thế nào

1\. Đọc và đối chiếu BR-01 đến BR-18; dùng chính các rule này làm baseline cho code và kiểm thử.

2\. Đối chiếu từng actor (Guest/Customer/Staff/Admin) với chức năng V2 để không gán nhầm quyền.

3\. Đối chiếu sơ đồ quan hệ logic với luồng nghiệp vụ: Product -> StoreProduct -> CartItem -> CheckoutSession -> Order -> Payment/Fulfillment.

4\. Đối chiếu checklist mục 17; chỉ khi các mục lõi đã rõ mới chuyển sang thiết kế DB.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Tài liệu/domain | Chốt thuật ngữ, business rule, state flow và quan hệ dữ liệu; chưa mở rộng chức năng. |
| Database/Backend/UI | Chỉ xác định ranh giới và trách nhiệm để các giai đoạn sau triển khai thống nhất; chưa code nghiệp vụ hoàn chỉnh. |

## 4\. Đầu ra bắt buộc

- Một baseline domain gồm Product=SKU, StoreProduct, selected Store, Cart group theo Store, Checkout nhiều Order, Payment per Order, fulfillment và Staff Store scope.
- Bộ BR-01 đến BR-18 được dùng nguyên nghĩa làm tiêu chuẩn kiểm tra mọi thay đổi sau này.
- Checklist trước khi code được chốt theo đúng mục 17 của Roadmap V2.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Chuẩn hóa cách gọi và mô tả những đối tượng đã có trong roadmap.

• Làm rõ mối quan hệ toàn chuỗi và theo Store nhưng không tạo module mới.

• Chốt state flow đúng như roadmap.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không thêm ProductVariant.

• Không thêm loyalty, voucher engine, AI/recommendation, virtual try-on, marketplace seller.

• Không thêm warehouse, transfer stock, courier integration, geolocation/routing hoặc giao 2h tự động.

• Không đổi stack công nghệ hoặc kiến trúc monolith.

## 7\. Ràng buộc bắt buộc

- BR-01 đến BR-18 là ràng buộc nghiệp vụ cuối cùng.
- Mọi chức năng mới không có căn cứ trong Roadmap V2 phải bị loại khỏi kế hoạch.
- Chuỗi cửa hàng phải thể hiện ở StoreProduct, selected Store, cart grouping, one Order-one Store và Staff scope.

## 8\. Điều kiện hoàn thành / Definition of Done

- Tài liệu không còn mâu thuẫn về Product/SKU, Payment, Store scope và state machine.
- Có thể trả lời thống nhất: dữ liệu nào là toàn chuỗi, dữ liệu nào thuộc Store.
- Không còn nhu cầu thêm bảng/module ngoài phạm vi chỉ để mô phỏng hệ thống thương mại điện tử lớn.

# GIAI ĐOẠN 2 - SQL SERVER SCHEMA

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 7, mục 7.1-7.4, bảng danh sách 19 bảng, bảng trường trọng tâm V2, Giai đoạn 2 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• CSDL SQL Server phản ánh đúng mô hình chuỗi: Product/SKU dùng chung, StoreProduct theo Store, Order theo Store, Payment theo Order, audit tồn kho và lịch sử đơn; có dữ liệu demo đủ để kiểm thử chuỗi.

## 1\. Làm gì

- Tạo/điều chỉnh đúng 19 bảng: roles, users, customer_addresses, stores, staff_store_assignments, categories, brands, products, product_images, store_products, inventory_movements, carts, cart_items, checkout_sessions, orders, order_items, payments, order_status_history, reviews.
- Mở rộng stores với province_city, area, opening_hours, delivery_enabled, pickup_enabled, status theo Roadmap V2.
- Giữ products theo quy ước Product = một SKU, sku UNIQUE.
- Giữ store_products chứa store_id, product_id, price, quantity, status, updated_at; UNIQUE(store_id, product_id).
- Tạo inventory_movements với ORDER/CANCEL_ORDER/STOCK_ADJUST theo roadmap.
- Giữ checkout_sessions chỉ để nhóm các Order; không dùng làm nguồn thanh toán.
- Orders phải có store_id, fulfillment_type, payment_method, payment_status, order_status, thông tin DELIVERY, pickup_code/ready_at/picked_up_at cho STORE_PICKUP.
- Sửa payments để FK tới order_id.
- Tạo order_status_history để audit transition.

## 2\. Làm như thế nào

1\. Tạo schema theo sơ đồ quan hệ logic ở mục 7.1, bảo toàn PK/FK giữa các bảng.

2\. Tạo đúng các UNIQUE/CHECK/INDEX được roadmap liệt kê.

3\. Thiết kế quantity/price không cho âm theo CHECK tương ứng.

4\. Tạo index phục vụ catalog theo Store, availability toàn chuỗi, Staff orders, lịch sử khách, inventory movement và order history.

5\. Seed tối thiểu 3-5 Store và bố trí cùng SKU xuất hiện ở nhiều Store với giá/tồn/trạng thái phù hợp để demo chuỗi.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| SQL Server | Schema, PK/FK, UNIQUE, CHECK, INDEX, seed data. |
| JPA mapping chuẩn bị | Chưa triển khai nghiệp vụ; chỉ bảo đảm schema có thể ánh xạ ở giai đoạn 3. |

## 4\. Đầu ra bắt buộc

- Schema 19 bảng đúng danh sách Roadmap V2.
- Dữ liệu seed có ít nhất 3 Store và SKU xuất hiện ở nhiều Store.
- Constraint/index đúng mục 7.4.
- Quan hệ Payment -> Order và hai bảng audit đúng thiết kế V2.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Tạo đúng các bảng/trường đã được Roadmap V2 nêu.

• Tạo dữ liệu demo để thể hiện chuỗi.

• Dùng SQL Server là CSDL duy nhất.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không tạo ProductVariant.

• Không thêm bảng warehouse, supplier, transfer stock, promotion, loyalty, courier, map/geolocation.

• Không đổi Payment quay lại gắn CheckoutSession.

• Không lưu ảnh binary trong SQL Server.

## 7\. Ràng buộc bắt buộc

- UNIQUE users.email, products.sku, store_products(store_id,product_id), cart_items(cart_id,store_product_id).
- CHECK store_products.quantity >= 0; price >= 0; review.rating 1..5.
- Không xóa cứng dữ liệu có lịch sử giao dịch.
- Nguồn sự thật stock/price theo Store là store_products.

## 8\. Điều kiện hoàn thành / Definition of Done

- DB phản ánh rõ dữ liệu toàn chuỗi và dữ liệu gắn Store.
- Có thể truy vấn một SKU ở nhiều Store và nhận price/quantity/status riêng.
- Payment có thể độc lập theo từng Order của cùng CheckoutSession.
- Seed data đủ để tiếp tục Phase 3 và các test TC-01/TC-06 về sau.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-01 chuẩn bị dữ liệu: một SKU có tại 3 Store.
- TC-18 chuẩn bị dữ liệu: có thể đặt Store INACTIVE mà không phá lịch sử.

# GIAI ĐOẠN 3 - SPRING BOOT FOUNDATION

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 8, mục 8.1, mục 8.2 và Giai đoạn 3 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Ứng dụng Spring Boot monolith kết nối SQL Server ổn định, có Entity/Repository/Service/Controller/DTO-validation đúng domain V2 và sẵn sàng cho từng nghiệp vụ tiếp theo.

## 1\. Làm gì

- Ánh xạ Entity JPA cho các bảng trong schema V2.
- Tạo Repository JPA cho truy cập dữ liệu, bao gồm khả năng lock StoreProduct khi cần.
- Tạo các Service theo roadmap: AuthService, ProductService, StoreService, StoreProductService, CartService, CheckoutService, OrderService, PaymentService, InventoryService, CloudinaryService, ReviewService.
- Tổ chức Controller theo ba khu vực client/staff/admin.
- Chuẩn bị DTO/validation cho request/view model thay vì để Controller chứa nghiệp vụ.
- Cấu hình kết nối Spring Boot - JPA - SQL Server.

## 2\. Làm như thế nào

1\. Mapping Entity bám đúng PK/FK/quan hệ của Phase 2.

2\. Đặt nghiệp vụ vào Service; Controller chỉ nhận request, validate cơ bản và chuẩn bị ViewModel/DTO.

3\. Repository chịu trách nhiệm truy cập DB thông qua JPA.

4\. Chuẩn bị @Transactional cho các nghiệp vụ cần atomic ở các phase sau; chưa tự thêm cơ chế ngoài roadmap.

5\. Xác nhận app khởi động và truy cập SQL Server ổn định trước khi triển khai UI/nghiệp vụ.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Spring Boot - Entity | Mapping các bảng SQL Server. |
| Spring Boot - Repository | JPA truy vấn và locking cần thiết. |
| Spring Boot - Service | Khung các Service đúng danh sách Roadmap V2. |
| Spring Boot - Controller/DTO | Tách client/staff/admin; validation cơ bản. |
| Cấu hình datasource | Kết nối SQL Server. |

## 4\. Đầu ra bắt buộc

- Ứng dụng Spring Boot chạy và kết nối SQL Server.
- Entity/Repository phản ánh schema V2.
- Khung Service/Controller sẵn sàng nhưng chưa nhồi nghiệp vụ sai tầng.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Dùng kiến trúc Controller - Service - Repository trong một ứng dụng monolith.

• Dùng JPA cho ORM/truy vấn/locking.

• Tách logic nghiệp vụ về Service.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không thêm microservice, Redis, Elasticsearch, Docker, message queue.

• Không đặt Store scope, stock transition, payment/order transition chỉ ở Controller/UI.

• Không tạo lớp kiến trúc mới ngoài mô hình roadmap.

## 7\. Ràng buộc bắt buộc

- Controller không là nguồn sự thật cho nghiệp vụ.
- Service sẽ là nơi thực hiện các rule Store scope, stock, payment/order transition.
- JPA/SQL Server là đường truy cập dữ liệu duy nhất trong stack đã chốt.

## 8\. Điều kiện hoàn thành / Definition of Done

- App kết nối DB ổn định.
- Có thể load dữ liệu seed thông qua JPA.
- Cấu trúc sẵn sàng để Phase 4-12 đi vào đúng tầng mà không đổi kiến trúc.

# GIAI ĐOẠN 4 - SITEMESH + UI NỀN

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 công nghệ SiteMesh/Thymeleaf/Bootstrap, mục 9 thiết kế màn hình, bảng khu vực Client/Staff/Admin và Giai đoạn 4 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Ba khu vực Client, Staff, Admin có layout ổn định, dùng Thymeleaf + Bootstrap + SiteMesh và làm nổi bật ngữ cảnh Store ngay từ giao diện nền.

## 1\. Làm gì

- Tạo Decorator SiteMesh riêng cho Client, Staff, Admin.
- Tạo component Bootstrap nền: navbar, sidebar, form, table, card/modal phù hợp roadmap.
- Client header có vị trí hiển thị “Chi nhánh đang chọn” hoặc “Toàn chuỗi”.
- Staff layout phải hiển thị rõ Store đang được phân công.
- Admin layout chuẩn bị điều hướng cho quản lý toàn chuỗi.

## 2\. Làm như thế nào

1\. Dùng Thymeleaf render server-side cho cả ba khu vực.

2\. Dùng SiteMesh quản lý layout dùng chung thay vì tạo SPA.

3\. Dùng Bootstrap cho responsive UI và thành phần giao diện.

4\. Chỉ dựng nền/layout; nghiệp vụ dữ liệu sẽ nối ở các phase sau.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| templates Client | Layout mua sắm; header selected Store. |
| templates Staff | Dashboard/sidebar theo Store. |
| templates Admin | Sidebar/layout quản trị toàn chuỗi. |
| static CSS/JS | Bootstrap và phần giao diện cần thiết; không đổi framework frontend. |

## 4\. Đầu ra bắt buộc

- 3 layout riêng Client/Staff/Admin.
- Header Client có vùng selected Store.
- UI nền responsive để các phase catalog/cart/order dùng lại.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Tinh chỉnh UI bằng Bootstrap trong stack.

• Dùng SiteMesh để tái sử dụng header/footer/menu/sidebar.

• Làm nổi bật selected Store và Store scope trong giao diện.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không đổi sang React/Vue/Angular.

• Không tạo SPA hoặc frontend tách stack.

• Không thêm bản đồ/geolocation ở Store Finder.

• Không triển khai các màn OUT OF SCOPE.

## 7\. Ràng buộc bắt buộc

- Client, Staff, Admin dùng chung Spring Boot application nhưng layout/menu riêng.
- UI chỉ là lớp hiển thị; quyền và Store scope vẫn phải được kiểm tra backend ở các phase sau.

## 8\. Điều kiện hoàn thành / Definition of Done

- Ba khu vực UI render ổn định qua Thymeleaf + SiteMesh.
- Header selected Store sẵn sàng nhận dữ liệu ở Phase 6.
- Không có thay đổi stack hoặc cấu trúc vượt roadmap.

# GIAI ĐOẠN 5 - AUTH + JWT

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 bảng công nghệ JWT, chức năng tác nhân, mục 5.1 chức năng tự động, rủi ro JWT và Giai đoạn 5 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Đăng ký/đăng nhập và phân quyền CUSTOMER/STAFF/ADMIN hoạt động đúng; request bảo vệ được xác thực bằng JWT cookie HttpOnly; Staff scope có nền tảng đúng từ assignment.

## 1\. Làm gì

- Đăng ký và đăng nhập người dùng theo role.
- Tạo/xác minh JWT; lưu JWT trong cookie HttpOnly.
- Chặn route theo role cho Client/Staff/Admin.
- Sau đăng nhập Staff phải xác định StaffStoreAssignment ACTIVE trước khi xử lý dữ liệu Store.
- Bảo vệ request thay đổi dữ liệu bằng cơ chế CSRF phù hợp theo rủi ro roadmap.
- Đăng xuất bằng cách xóa/vô hiệu cookie phía trình duyệt theo thiết kế.

## 2\. Làm như thế nào

1\. AuthService xử lý đăng ký/đăng nhập/JWT/role.

2\. Lớp bảo mật thực hiện Authentication/Authorization trước Controller bảo vệ.

3\. Backend kiểm tra role và Store scope; không dựa vào việc ẩn menu.

4\. Secret phải đi qua biến môi trường ở phase deploy; không hard-code.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Spring Security/JWT layer trong Spring Boot | Xác thực và phân quyền. |
| AuthService | Đăng ký/đăng nhập/JWT/role. |
| StaffStoreAssignment | Xác định Store của Staff. |
| Thymeleaf/SiteMesh | Hiển thị menu tương ứng role nhưng không thay thế backend authorization. |

## 4\. Đầu ra bắt buộc

- Luồng đăng ký/đăng nhập/đăng xuất.
- JWT cookie HttpOnly.
- Route protection theo role.
- Nền tảng Store scope cho Staff.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Dùng JWT đúng stack đã chốt.

• Dùng cookie HttpOnly và SameSite phù hợp.

• Kiểm tra role/Store ở backend.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không lưu JWT ở localStorage theo thiết kế roadmap gốc/V2.

• Không hard-code JWT secret.

• Không chỉ ẩn menu UI để coi là đã phân quyền.

• Không cho Staff truyền store_id tùy ý để quyết định phạm vi quyền.

## 7\. Ràng buộc bắt buộc

- Backend là nguồn quyết định quyền.
- JWT cookie phải HttpOnly; SameSite phù hợp.
- Request thay đổi dữ liệu phải có bảo vệ CSRF phù hợp.
- Staff chỉ có ngữ cảnh Store khi assignment ACTIVE.

## 8\. Điều kiện hoàn thành / Definition of Done

- Customer/Staff/Admin truy cập đúng khu vực.
- Staff chưa có assignment hợp lệ không thể truy cập dữ liệu Store.
- Route bảo vệ không thể vượt quyền chỉ bằng URL trực tiếp.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- Chuẩn bị cho TC-11: Staff A mở Order B phải bị backend từ chối.

# GIAI ĐOẠN 6 - CATALOG + STORE CHAIN

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 6.1, BR-01 đến BR-04/BR-15/BR-16, mục 9 Product Detail, bảng khu vực UI và Giai đoạn 6 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Khách nhìn thấy rõ đây là website chuỗi: có thể duyệt toàn chuỗi, chọn Store, xem Store Finder, xem SKU đang bán ở Store nào và Còn hàng/Hết hàng theo Store; Admin quản lý được Product/Store/StoreProduct và ảnh.

## 1\. Làm gì

- CRUD Category, Brand, Product/SKU, Store ở phạm vi Admin.
- Upload/xóa ảnh qua Cloudinary; SQL Server lưu URL/public_id.
- CRUD StoreProduct để gắn SKU với Store, price, quantity, status.
- Tạo Store Finder theo province_city và area; hiển thị address, phone, opening_hours, delivery_enabled, pickup_enabled, status.
- Triển khai selectedStoreId lưu session/cookie.
- Catalog không chọn Store: tìm Product toàn chuỗi và cho biết Store nào đang bán.
- Catalog khi chọn Store: chỉ trả StoreProduct ACTIVE của Store đó.
- Product Detail hiển thị giá/trạng thái ở Store hiện tại và danh sách availability của Store khác.
- Client chỉ thấy Còn hàng/Hết hàng; Staff/Admin mới thấy quantity.

## 2\. Làm như thế nào

1\. StoreService xử lý Store Finder, selectedStoreId và metadata Store.

2\. ProductService xử lý catalog/category/brand/tìm kiếm SKU.

3\. StoreProductService xử lý availability, price, stock theo Store.

4\. Khi selectedStoreId null, dùng ngữ cảnh Toàn chuỗi; khi có ID, lọc theo Store tương ứng.

5\. Khi đổi selected Store, chỉ đổi ngữ cảnh duyệt; không chạm vào CartItem đã có.

6\. Product Detail cung cấp nút “Xem chi nhánh khác/Đổi chi nhánh” theo thiết kế roadmap.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Admin - Spring Boot/Thymeleaf | Category, Brand, Product/SKU, Store, StoreProduct, Product Image. |
| Client - Thymeleaf | Store Finder, catalog toàn chuỗi/theo Store, Product Detail availability. |
| StoreService/ProductService/StoreProductService | Nghiệp vụ tìm kiếm và ngữ cảnh Store. |
| CloudinaryService | Ảnh sản phẩm/thương hiệu; DB chỉ lưu URL/public_id. |

## 4\. Đầu ra bắt buộc

- Catalog toàn chuỗi và catalog theo Store.
- Store Finder không cần map.
- Product Detail hiển thị availability theo Store.
- Admin CRUD các dữ liệu catalog/Store cần thiết.
- Cloudinary hoạt động đúng.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Lọc theo Category/Brand/Store.

• Hiển thị metadata Store đã chốt.

• Hiển thị availability Còn hàng/Hết hàng cho Client.

• Dùng selectedStoreId trong session/cookie.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không hiển thị exact quantity cho Client.

• Không thêm tọa độ/map API/geolocation/routing.

• Không tự chọn Store gần nhất hoặc giao 2h.

• Không thêm ProductVariant.

• Không thêm promotion/loyalty engine.

## 7\. Ràng buộc bắt buộc

- Product là SKU; StoreProduct là nguồn price/quantity/status theo Store.
- Store/StoreProduct phải ACTIVE để bán.
- Đổi selected Store không chuyển CartItem cũ.
- Ảnh chỉ lưu Cloudinary URL/public_id trong SQL Server.

## 8\. Điều kiện hoàn thành / Definition of Done

- Khách thấy rõ SKU ở Store nào.
- Chọn Store A thì catalog chỉ phản ánh Store A.
- Product Detail đổi Store thì giá/availability đổi đúng.
- Upload ảnh thành công không lưu binary DB.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-01, TC-02, TC-03, TC-17, TC-18.

# GIAI ĐOẠN 7 - CART NHIỀU STORE

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 6.2, BR-05/BR-06/BR-16, CartService và Giai đoạn 7 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Giỏ hàng thể hiện rõ nhiều chi nhánh: CartItem gắn StoreProduct, được nhóm đúng Store, chọn cả Store hoặc từng item và luôn kiểm tra stock backend.

## 1\. Làm gì

- Thêm CartItem bằng storeProductId + quantity.
- Nếu đã có cùng storeProductId trong Cart thì cộng quantity, không tạo dòng trùng.
- Cập nhật quantity và xóa CartItem.
- Render Cart bằng cách group CartItem theo Store của StoreProduct.
- Mỗi group có checkbox “Chọn cả chi nhánh”, từng item có checkbox riêng.
- Backend kiểm tra StoreProduct ACTIVE và quantity đủ khi add/update.
- Giữ nguyên CartItem theo Store gốc khi selectedStoreId thay đổi.

## 2\. Làm như thế nào

1\. CartService nhận storeProductId, tải StoreProduct từ DB rồi validate status/quantity.

2\. Dùng UNIQUE(cart_id, store_product_id) làm ràng buộc chống dòng trùng.

3\. Khi hiển thị, group theo Store để tạo UI phân nhóm.

4\. Mọi thay đổi quantity phải kiểm tra tồn mới nhất ở backend.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| CartService | Add/update/delete, stock validation, group theo Store. |
| Repository/JPA | Cart, CartItem, StoreProduct. |
| Client Thymeleaf | Giỏ hàng nhiều Store với checkbox Store/item. |

## 4\. Đầu ra bắt buộc

- Add/update/delete CartItem.
- Cart nhóm Store rõ ràng.
- Chọn được toàn bộ group hoặc từng item.
- Không có quantity vượt stock ở thời điểm cập nhật cart.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Cho một Cart chứa item từ nhiều Store.

• Cho cùng một SKU ở hai Store tồn tại như hai lựa chọn StoreProduct khác nhau.

• Giữ CartItem cũ khi đổi selected Store.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không gắn CartItem chỉ với Product.

• Không tự chuyển item sang Store khác.

• Không coi Store như marketplace seller và thêm seller logic.

• Không tin quantity client mà không kiểm tra DB.

## 7\. Ràng buộc bắt buộc

- CartItem luôn tham chiếu StoreProduct.
- UNIQUE(cart_id, store_product_id).
- Backend kiểm tra StoreProduct ACTIVE và stock.
- UI phải thể hiện Store group rõ.

## 8\. Điều kiện hoàn thành / Definition of Done

- Thêm cùng StoreProduct hai lần chỉ có một CartItem và quantity tăng.
- Cart có item từ 3 Store hiển thị đúng 3 group.
- Giỏ sẵn sàng cung cấp danh sách item đã chọn cho Checkout.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-04, TC-05.

# GIAI ĐOẠN 8 - CHECKOUT + ORDERS

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 6.3, BR-07 đến BR-10, mục 8.2 JPA/transaction, InventoryMovement và Giai đoạn 8 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Một lần checkout có thể xử lý item từ nhiều Store, tạo đúng một Order cho mỗi Store group, snapshot dữ liệu chính xác, trừ tồn an toàn và rollback toàn bộ nếu transaction tạo đơn lỗi.

## 1\. Làm gì

- Nhận danh sách cartItemId được chọn; tải lại CartItem/StoreProduct từ SQL Server.
- Group item theo store_id.
- Kiểm tra Store ACTIVE, StoreProduct ACTIVE và đủ tồn cho từng group.
- Lock StoreProduct cần thiết để tránh overselling SKU cuối.
- Tạo CheckoutSession cho lần checkout.
- Tạo một Order cho mỗi Store group; gắn fulfillment_type và payment_method của group.
- Tạo OrderItem snapshot product_name, unit_price, quantity, subtotal.
- Trừ StoreProduct.quantity và ghi InventoryMovement type ORDER.
- Xóa CartItem đã checkout; giữ item không chọn.
- Nếu bất kỳ bước nào trong transaction tạo đơn lỗi thì rollback toàn bộ.

## 2\. Làm như thế nào

1\. CheckoutService dùng @Transactional.

2\. Repository ưu tiên @Lock(PESSIMISTIC_WRITE) cho StoreProduct như roadmap đề xuất để dễ giải thích/kiểm thử.

3\. Không nhận unit_price/total_amount từ client; đọc lại price từ StoreProduct và tính lại backend.

4\. Ghi quantity_before/quantity_after trong InventoryMovement theo dữ liệu thực tế.

5\. Chỉ sau khi tất cả group hợp lệ mới commit Session/Orders/OrderItems/stock/cart trong transaction.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| CheckoutService | Group Store, validate/lock stock, tạo Session/Orders/Items. |
| InventoryService | Trừ tồn và ghi InventoryMovement ORDER. |
| Repository/JPA | PESSIMISTIC_WRITE/transaction trên StoreProduct và các entity liên quan. |
| Client Checkout Thymeleaf | Hiển thị từng Store group và lấy lựa chọn fulfillment/payment cho từng group. |

## 4\. Đầu ra bắt buộc

- CheckoutSession liên kết các Order cùng lần checkout.
- Mỗi Store group sinh đúng một Order.
- OrderItem snapshot đúng dữ liệu mua.
- Stock trừ an toàn và có movement.
- Cart chỉ xóa item đã checkout.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Cho các Store group trong cùng checkout chọn fulfillment/payment riêng.

• Dùng transaction và locking trong JPA/SQL Server.

• Ghi audit movement cho việc trừ kho.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không tạo một Order chứa item của nhiều Store.

• Không tin giá/tổng tiền từ client.

• Không cho quantity âm hoặc commit nửa chừng khi một group lỗi.

• Không để Staff duyệt thủ công việc có được tạo Order hay không.

## 7\. Ràng buộc bắt buộc

- Một Order = một Store.
- Hệ thống tự xác nhận tính hợp lệ tồn kho trước khi tạo đơn; Staff chỉ fulfillment.
- CHECK quantity >= 0 vẫn phải đúng sau commit.
- Transaction tạo Session/Orders/stock/cart phải atomic.

## 8\. Điều kiện hoàn thành / Definition of Done

- Checkout item từ 3 Store tạo đúng 3 Order.
- Hai khách mua SKU cuối không làm âm kho; chỉ transaction hợp lệ commit.
- Giá client bị sửa không ảnh hưởng OrderItem.
- OrderItem cũ vẫn giữ snapshot nếu StoreProduct price thay đổi về sau.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-06, TC-07, TC-08, TC-14.

# GIAI ĐOẠN 9 - PAYMENT + FULFILLMENT

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 6.4-6.6, BR-08/BR-11/BR-18, PaymentService/OrderService và Giai đoạn 9 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Mỗi Order có payment và fulfillment độc lập đúng Store; DELIVERY và STORE_PICKUP đi qua đúng state flow; pickup có mã xác minh nhưng không kéo dự án sang hệ thống payment/logistics phức tạp.

## 1\. Làm gì

- Gắn Payment trực tiếp Order; cho phép nhiều lần ghi nhận payment nếu cần theo quan hệ 1-N đã thiết kế.
- ONLINE: PENDING_PAYMENT -> Payment SUCCESS -> CONFIRMED; FAILED thì hủy Order theo rule và hoàn tồn.
- COD: Order CONFIRMED, payment_status UNPAID; khi giao hoàn tất thì PAID.
- PAY_AT_STORE: chỉ dùng với STORE_PICKUP; UNPAID tới khi khách nhận/thanh toán tại Store.
- DELIVERY flow: ONLINE PENDING_PAYMENT -> CONFIRMED -> PREPARING -> PACKED -> SHIPPING -> COMPLETED; COD bắt đầu CONFIRMED.
- STORE_PICKUP flow: CONFIRMED -> PREPARING -> READY_FOR_PICKUP -> COMPLETED.
- Khi chuyển READY_FOR_PICKUP, sinh pickup_code ngắn và ghi ready_at.
- Khách xem pickup_code trong Order Detail; Staff xác minh mã khi nhận hàng; ghi picked_up_at.
- Ghi OrderStatusHistory cho transition trạng thái.

## 2\. Làm như thế nào

1\. PaymentService quản lý method/status/transaction_code theo Order.

2\. OrderService kiểm tra transition theo fulfillment_type; không cho nhảy trạng thái tùy ý.

3\. OrderService sinh pickup_code tại READY_FOR_PICKUP và ghi timestamps theo roadmap.

4\. Nếu PAY_AT_STORE, cập nhật payment trước khi COMPLETED.

5\. Không tích hợp gateway phức tạp nếu môn học không bắt buộc; chỉ chuẩn hóa trạng thái và transaction_code nếu có.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| PaymentService | ONLINE/COD/PAY_AT_STORE theo Order. |
| OrderService | State machine, pickup code, timestamps, history. |
| Client Order Detail | Store, trạng thái, pickup_code/timeline. |
| Staff Order/Pickup UI | Chuyển trạng thái và xác minh pickup_code. |

## 4\. Đầu ra bắt buộc

- Payment tách theo Order.
- Hai state flow DELIVERY/STORE_PICKUP hoạt động đúng.
- pickup_code/ready_at/picked_up_at đúng quy tắc.
- Payment/status từng Order độc lập trong cùng CheckoutSession.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Cho mỗi Order chọn payment method theo Store group.

• Cho Store Pickup có PAY_AT_STORE hoặc ONLINE.

• Cho DELIVERY có COD hoặc ONLINE.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không gắn Payment trở lại CheckoutSession.

• Không dùng PAY_AT_STORE cho DELIVERY.

• Không thêm SMS/email ngoài hệ thống cho pickup.

• Không xây payment gateway phức tạp nếu không bắt buộc.

• Không thêm curbside, courier integration, giao 2h/auto nearest.

## 7\. Ràng buộc bắt buộc

- Mọi transition phải qua Service state machine và ghi history.
- Store của Order pickup là nơi khách nhận; không đổi Store sau khi Order tạo.
- Payment FAILED phải đi theo rule hủy/hoàn tồn.
- Staff chỉ fulfillment Order hợp lệ của Store mình.

## 8\. Điều kiện hoàn thành / Definition of Done

- DELIVERY + COD chạy đúng chuỗi trạng thái.
- STORE_PICKUP + PAY_AT_STORE chạy đúng và xác minh pickup_code.
- Trong một checkout, các Order có payment method khác nhau vẫn độc lập, không mơ hồ.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-09, TC-10, TC-15.

# GIAI ĐOẠN 10 - STAFF STORE OPERATIONS

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 mục 6.8, tác nhân Staff, bảng UI Staff, BR-14/BR-15, InventoryService và Giai đoạn 10 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Nhân viên chỉ nhìn thấy và xử lý đúng Store được phân công: Order queue, pickup queue, trạng thái fulfillment, stock StoreProduct và lịch sử biến động tồn; không có quyền dữ liệu toàn chuỗi.

## 1\. Làm gì

- Sau đăng nhập xác định StaffStoreAssignment ACTIVE.
- Hiển thị Dashboard Staff có tên Store rõ ràng.
- Hiển thị danh sách/chi tiết Order thuộc Store của Staff.
- Cho Staff chuyển các trạng thái fulfillment hợp lệ: PREPARING/PACKED/SHIPPING/READY_FOR_PICKUP/COMPLETED.
- Tạo pickup queue cho Order STORE_PICKUP chờ nhận.
- Hiển thị StoreProduct stock của Store.
- Cho cập nhật tồn thực tế; bắt buộc ghi InventoryMovement STOCK_ADJUST với before/after/staff_id/note.
- Cho xem inventory history theo StoreProduct.
- Dashboard chỉ thống kê nhỏ: đơn chờ xử lý, pickup chờ nhận, SKU sắp hết/hết hàng.

## 2\. Làm như thế nào

1\. Mọi query Order/StoreProduct của Staff phải thêm store_id từ assignment ở Service/Repository.

2\. Không nhận store_id từ request làm nguồn quyết định quyền.

3\. OrderService xác thực Store scope trước khi chuyển trạng thái.

4\. InventoryService thực hiện chỉnh stock và movement trong nghiệp vụ phù hợp.

5\. UI Staff không có màn dữ liệu toàn chuỗi.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Staff Controller/Thymeleaf | Dashboard, Order queue/detail, Pickup queue, Stock, Inventory history. |
| OrderService | Store scope + state transition. |
| InventoryService | STOCK_ADJUST + InventoryMovement. |
| Repository | Query bắt buộc ràng buộc store_id từ assignment. |

## 4\. Đầu ra bắt buộc

- Order queue đúng Store.
- Pickup queue đúng Store.
- Stock + inventory movement đúng Store.
- Dashboard nhỏ theo đúng roadmap.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Staff cập nhật fulfillment của Order Store mình.

• Staff xem exact quantity của StoreProduct Store mình.

• Staff điều chỉnh stock nếu có movement đầy đủ.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không cho Staff xem Order/stock Store khác.

• Không cho nhập store_id tùy ý để vượt scope.

• Không xây BI lớn.

• Không xây module WMS/nhập kho/chuyển kho lớn.

## 7\. Ràng buộc bắt buộc

- Store scope phải được backend enforce.
- Mọi chỉnh stock thủ công có InventoryMovement STOCK_ADJUST.
- Order transition phải qua Service state machine.
- Exact quantity không public cho Client.

## 8\. Điều kiện hoàn thành / Definition of Done

- Staff A truy cập Order B bị từ chối backend.
- Staff chỉnh tồn tạo movement đầy đủ.
- Staff chỉ thấy dashboard và queue của Store mình.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-11, TC-13.

# GIAI ĐOẠN 11 - ADMIN TOÀN CHUỖI

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 tác nhân Admin, bảng UI Admin, P0/P1 và Giai đoạn 11 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Admin quản trị được toàn mạng lưới OneShop ở mức đồ án: Store, Staff assignment, Product/SKU, StoreProduct/tồn theo Store, Order filter Store, User/Review cơ bản; vẫn không mở rộng thành ERP/BI/logistics.

## 1\. Làm gì

- Quản lý Users/Roles ở phạm vi roadmap.
- Quản lý Staff và StaffStoreAssignment.
- Quản lý Stores và metadata Store Finder.
- Quản lý Category, Brand, Product/SKU, Product Image.
- Quản lý StoreProduct theo từng Store; xem tồn toàn chuỗi theo Store.
- Xem Orders toàn hệ thống và filter theo Store.
- Quản lý Review/User cơ bản.
- Xem tổng quan theo Store ở mức đơn giản.

## 2\. Làm như thế nào

1\. Admin Controller/Service dùng dữ liệu toàn chuỗi nhưng vẫn dựa trên cùng Product/StoreProduct/Order model.

2\. Dùng Store filter để làm rõ mạng lưới chi nhánh.

3\. Ảnh tiếp tục qua CloudinaryService.

4\. Không biến Admin thành ERP, WMS hoặc BI platform.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Admin Thymeleaf/SiteMesh | Stores, Staff assignment, Product/SKU, StoreProduct, Orders, Review/User. |
| Service tương ứng | Quản lý dữ liệu toàn chuỗi. |
| SQL Server/JPA | Nguồn dữ liệu duy nhất. |
| Cloudinary | Product/Brand image theo stack. |

## 4\. Đầu ra bắt buộc

- Admin quản lý được các điểm bán và SKU theo Store.
- Admin lọc được Order/tồn theo Store.
- Admin quản lý assignment và dữ liệu cơ bản toàn hệ thống.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Cho Admin xem exact stock toàn chuỗi theo Store.

• Cho Admin filter theo Store.

• Cho Admin soft-disable dữ liệu bằng status.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không xây supplier/procurement/ERP.

• Không xây kho trung tâm/chuyển kho.

• Không xây BI nâng cao.

• Không xây promotion/loyalty engine.

• Không thêm module ngoài danh sách Roadmap V2.

## 7\. Ràng buộc bắt buộc

- Không xóa cứng Product/Store/StoreProduct có lịch sử.
- Admin vẫn dùng đúng domain chain hiện có, không tạo data model song song.
- Cloudinary/SQL Server phải đúng vai trò đã chốt.

## 8\. Điều kiện hoàn thành / Definition of Done

- Admin quản trị được mạng lưới Store và StoreProduct.
- Filter Store cho Orders/stock hoạt động.
- Không có module mở rộng vượt scope.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-18: Store INACTIVE không nhận Order mới nhưng lịch sử vẫn xem được.
- TC-17: upload ảnh vẫn đúng Cloudinary/URL-public_id.

# GIAI ĐOẠN 12 - HISTORY + HARDENING

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 OrderStatusHistory/InventoryMovement, mục 7.4, mục 8.2, mục 13 test bắt buộc, mục 14 rủi ro và Giai đoạn 12 mục 12.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Dữ liệu nhất quán, có audit, không vượt quyền, không âm kho và các tình huống cạnh tranh/hủy/trạng thái đều được kiểm soát đúng business rule V2.

## 1\. Làm gì

- Hoàn thiện OrderStatusHistory cho mọi transition.
- Hoàn thiện luồng hủy Order hợp lệ: CANCELLED + hoàn đúng StoreProduct + InventoryMovement CANCEL_ORDER.
- Bảo đảm cancel + restore inventory + movement + status history atomic.
- Kiểm thử Store scope cho Staff ở backend.
- Kiểm thử concurrent checkout SKU cuối với locking.
- Kiểm thử client sửa giá/tổng tiền.
- Kiểm thử Order state machine không cho transition sai.
- Kiểm thử soft status INACTIVE/HIDDEN không làm gãy lịch sử.
- Kiểm tra JWT cookie/CSRF theo rủi ro roadmap.
- Chạy đầy đủ bộ TC-01 đến TC-18.

## 2\. Làm như thế nào

1\. Dùng @Transactional cho các luồng atomic đã roadmap yêu cầu.

2\. Dùng lock StoreProduct/chiến lược JPA đã chốt để không âm kho; ưu tiên PESSIMISTIC_WRITE.

3\. OrderService kiểm tra transition trước update và ghi history.

4\. InventoryService ghi movement cho ORDER/CANCEL_ORDER/STOCK_ADJUST.

5\. Permission test phải gọi endpoint/backend thật, không chỉ kiểm UI.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Service layer | Transaction, state machine, Store scope, validation. |
| Repository/JPA | Locking và query theo Store. |
| SQL Server | CHECK/UNIQUE/INDEX và dữ liệu audit. |
| Test layer/manual test | TC-01 đến TC-18 theo roadmap. |

## 4\. Đầu ra bắt buộc

- OrderStatusHistory đầy đủ.
- InventoryMovement đúng cho các loại đã chốt.
- Hủy/hoàn kho atomic.
- Bộ TC-01..TC-18 có kết quả mong đợi.
- Các rủi ro roadmap đã có kiểm soát.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Tăng validation/hardening đúng các rủi ro đã nêu.

• Kiểm thử concurrency/permission/state/price integrity.

• Bổ sung index/constraint chỉ đúng danh sách roadmap.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không thêm hệ thống audit chung ngoài hai audit table đã chốt.

• Không thêm queue/distributed lock/Redis.

• Không sửa domain chỉ để vượt test bằng shortcut.

• Không bỏ backend authorization và chỉ test UI.

## 7\. Ràng buộc bắt buộc

- Quantity không âm.
- Giá backend lấy StoreProduct.
- Staff query bắt buộc Store assignment.
- Checkout nhiều Store tạo đơn/stock/cart atomic; Order cancel restore atomic.
- Soft status bảo toàn lịch sử.

## 8\. Điều kiện hoàn thành / Definition of Done

- TC-01 đến TC-18 đều đạt.
- Không còn lỗi overselling, lẫn Store, sai price, sai transition hoặc mất audit trong phạm vi test roadmap.
- Dữ liệu nhất quán và audit được như điều kiện hoàn thành Giai đoạn 12.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- TC-01 đến TC-18 toàn bộ.

# GIAI ĐOẠN 13 - DEPLOY / DEMO

**Căn cứ trực tiếp trong Roadmap V2:** Roadmap V2 Giai đoạn 13 mục 12, checklist mục 17, công nghệ Cloudinary/SQL Server/JWT và bộ test mục 13.

**MỤC TIÊU CUỐI CÙNG CỦA GIAI ĐOẠN**

• Có phiên bản demo ổn định, dùng đúng stack, kết nối SQL Server cloud/Cloudinary, dữ liệu seed thể hiện rõ chuỗi và vượt bộ test bắt buộc trước khi trình diễn.

## 1\. Làm gì

- Đưa các cấu hình nhạy cảm qua environment variables: SQL Server, JWT secret, Cloudinary.
- Chuẩn bị SQL Server cloud theo stack đã chốt.
- Kiểm tra Cloudinary upload/URL-public_id trên môi trường deploy.
- Build/deploy ứng dụng Spring Boot monolith.
- Seed dữ liệu demo: tối thiểu 3 Store, SKU xuất hiện ở nhiều Store, Customer/Staff/Admin và các StoreProduct khác nhau.
- Chạy test script/bộ TC-01 đến TC-18 trên môi trường gần demo.
- Kiểm tra checklist chốt phạm vi: selected Store, availability, cart group, one Order/store, Payment per Order, Staff Store scope, pickup code, stock không âm, stack không đổi.

## 2\. Làm như thế nào

1\. Dùng environment variables cho credential/secret; không commit giá trị thật.

2\. Kiểm tra app kết nối SQL Server và Cloudinary sau deploy.

3\. Dùng seed demo để trình bày trực tiếp bản chất chuỗi: cùng SKU ở nhiều Store, cart nhiều Store, checkout nhiều Order, Staff từng Store.

4\. Chỉ sau khi TC-01..TC-18 đạt mới chốt bản demo.

## 3\. Làm ở đâu

|     |     |
| --- | --- |
| **Khu vực/thành phần** | **Nội dung thực hiện** |
| Môi trường deploy Spring Boot | Build/chạy ứng dụng monolith. |
| SQL Server cloud | CSDL duy nhất của bản demo. |
| Cloudinary | Ảnh Product/Brand. |
| Environment variables | DB credentials, JWT secret, Cloudinary config. |
| Dữ liệu demo | 3-5 Store + SKU nhiều Store + role/assignment phù hợp. |

## 4\. Đầu ra bắt buộc

- Bản deploy truy cập được.
- SQL Server/Cloudinary kết nối ổn định.
- Dữ liệu demo đủ thể hiện chuỗi.
- Bộ test bắt buộc đạt trước demo.

## 5\. Được làm

**PHẠM VI ĐƯỢC PHÉP**

• Deploy đúng ứng dụng monolith và stack roadmap.

• Dùng SQL Server cloud và Cloudinary như đã chốt.

• Dùng env variables cho secret.

## 6\. Không được làm

**PHẠM VI CẤM / KHÔNG THUỘC ROADMAP**

• Không thêm Docker chỉ để deploy.

• Không đổi DB sang MySQL/PostgreSQL.

• Không tách frontend sang framework khác.

• Không thêm module OUT OF SCOPE trước demo.

• Không hard-code secret/credential thật.

## 7\. Ràng buộc bắt buộc

- Toàn bộ triển khai vẫn là Spring Boot + Thymeleaf + Bootstrap + JPA + SQL Server + SiteMesh + JWT + Cloudinary.
- Demo phải thể hiện ít nhất 3 Store và khác biệt StoreProduct/tồn.
- Staff phải bị giới hạn theo Store.
- Payment phải theo Order, pickup có READY_FOR_PICKUP + pickup_code, tồn không âm.

## 8\. Điều kiện hoàn thành / Definition of Done

- Demo ổn định như điều kiện hoàn thành roadmap.
- Checklist mục 17 đạt đầy đủ.
- TC-01 đến TC-18 không phát sinh lỗi blocking.
- Không có công nghệ/module ngoài phạm vi.

## 9\. Kiểm thử / minh chứng gắn với roadmap

- Chạy lại TC-01 đến TC-18 trên dữ liệu/môi trường demo.

# 3\. MA TRẬN TRUY VẾT GIAI ĐOẠN -> ROADMAP V2

|     |     |     |     |
| --- | --- | --- | --- |
| **Giai đoạn** | **Roadmap V2 trọng tâm** | **Business rule/test liên quan** | **Cổng hoàn thành** |
| 1   | Mục 3.3, 4, 4.1, 17 | BR-01..BR-18 | Domain không mâu thuẫn. |
| 2   | Mục 7.1..7.4 | TC-01/TC-18 chuẩn bị dữ liệu | Schema 19 bảng đúng và seed đủ. |
| 3   | Mục 8, 8.1, 8.2 | Nền cho toàn bộ BR | App/JPA/SQL Server ổn định. |
| 4   | Mục 9 + bảng UI | selected Store/Store scope hiển thị | 3 layout ổn định. |
| 5   | JWT + tác nhân + rủi ro | BR-14, TC-11 | Role/Store scope đúng. |
| 6   | Mục 6.1 + Product Detail | BR-01..04,15,16; TC-01,02,03,17,18 | Khách thấy availability theo Store. |
| 7   | Mục 6.2 | BR-05,06,16; TC-04,05 | Cart group theo Store. |
| 8   | Mục 6.3 + 8.2 | BR-07..10; TC-06,07,08,14 | Orders/stock transaction đúng. |
| 9   | Mục 6.4..6.6 | BR-08,11,18; TC-09,10,15 | Payment/fulfillment hoàn chỉnh. |
| 10  | Mục 6.8 | BR-14,15; TC-11,13 | Staff chỉ đúng Store. |
| 11  | Admin UI/tác nhân | TC-17,18 | Admin quản lý mạng lưới. |
| 12  | Mục 7.4, 8.2, 13, 14 | TC-01..TC-18 | Nhất quán + audit. |
| 13  | Giai đoạn 13 + mục 17 | TC-01..TC-18 | Demo ổn định, đúng stack. |

# 4\. CHECKLIST CẤM LỆCH ROADMAP KHI AI CODING THỰC HIỆN

- **Không thay đổi bất kỳ công nghệ nào trong stack đã chốt.**
- Không thêm ProductVariant; Product = một SKU bán được cụ thể.
- Không thêm Promotion Engine, Loyalty, AI/Recommendation, Virtual Try-on, Subscription, Marketplace Seller.
- Không thêm Warehouse, Transfer Stock, Courier Integration, Geolocation/Routing, Curbside, giao 2h tự động.
- Không cho CartItem tham chiếu Product thay vì StoreProduct.
- Không tạo Order chứa item của nhiều Store.
- Không gắn Payment trực tiếp CheckoutSession.
- Không cho Staff tự chọn store_id để mở dữ liệu Store khác.
- Không tin unit_price/total_amount từ client.
- Không cho stock âm; checkout/hủy phải transaction đúng roadmap.
- Không hiển thị quantity chính xác cho Client.
- Không xóa cứng dữ liệu đã có lịch sử giao dịch.
- Không chuyển CartItem sang Store mới khi khách đổi selected Store.
- Không thêm SMS/email ngoài hệ thống cho pickup.
- Không xây BI/ERP/WMS/logistics ngoài phạm vi.
- Không được bỏ bất kỳ TC-01..TC-18 nào trước khi chốt demo.

**KẾT LUẬN THỰC THI**

• 13 giai đoạn trên là phân rã trực tiếp của Roadmap triển khai kỹ thuật V2; không phải một roadmap mới.

• Mọi quyết định kỹ thuật/hiệu chỉnh trong quá trình code phải ưu tiên giữ đúng Business Rules, Store scope, transaction, Payment per Order và OUT OF SCOPE đã chốt.

• Mục tiêu cuối cùng là bản demo thể hiện rõ OneShop là một website duy nhất cho một chuỗi cửa hàng, trong đó sản phẩm dùng chung toàn chuỗi nhưng availability/tồn/Order/Staff được quản lý theo Store.