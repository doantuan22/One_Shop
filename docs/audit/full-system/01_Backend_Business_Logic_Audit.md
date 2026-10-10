# Backend & Business Logic Audit

Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.

## Kiến trúc và dữ liệu

Controller client/staff/admin/api nhận Principal/path/query/DTO, BindingResult và tạo ViewModel; scan không có repository injection hoặc ghi OrderStatus/quantity vào entity từ Controller. AdminStoreProductController.setPrice/setQuantity phục vụ form, không là inventory writer. Form editing ghim Store/Product hiện hữu, service thực hiện business validation. API Auth trả AuthResponse/UserResponse; không trả User entity/passwordHash. Security filter/interceptor chạy trước controller, DTO mapping trong transaction khi open-in-view=false.

97 action annotations được inventory kèm file/dòng/method, 19 repositories/19 mapped entities. 38 service Java files gồm interface, implementation và concrete service; không suy ra 38 module. Core writers là CheckoutServiceImpl, OrderServiceImpl/OrderTransitionPolicy, PaymentServiceImpl, InventoryServiceImpl. OrderViewService chỉ snapshot/timeline reader, transaction MANDATORY.

New Admin services có @PreAuthorize ADMIN; CRUD Catalog/Store Phase6 được bảo vệ bởi SecurityConfig route ADMIN. Không khẳng định mọi phương thức trong ProductService có method-level annotation. Customer read dùng trusted Principal email + ownership repository, cancel có thêm method authorization; Staff dịch vụ lấy scope từ SecurityContext/ACTIVE DB assignments.

## Ma trận BR-01–18

| BR | Quy tắc Roadmap | Verification hiện tại | Status |
| --- | --- | --- | --- |
| BR-01 | Một Product là một SKU bán được cụ thể và có thể xuất hiện tại nhiều Store. | TC01 SQL + catalog-three-stores: Product503 tại Stores47/48/49 | PASS |
| BR-02 | StoreProduct là nguồn sự thật về price, quantity và status của SKU tại một Store. | TC08/13/14; UI stock/price và ledger 819–821 | PASS |
| BR-03 | Client có thể duyệt toàn chuỗi hoặc chọn selectedStoreId để chuyển sang ngữ cảnh một chi nhánh. | TC02/03; selected-store-A/B/C | PASS |
| BR-04 | Khi không chọn Store, kết quả cho biết SKU đang có ở những Store nào; khi đã chọn Store, chỉ trả dữ liệu StoreProduct của Store đó. | TC01/02/03 và selected Store detail/price | PASS |
| BR-05 | CartItem luôn tham chiếu StoreProduct; do đó một SKU ở hai Store là hai lựa chọn mua khác nhau. | TC04; cùng SKU thành 3 CartItem nguồn StoreProduct khác nhau | PASS |
| BR-06 | Cart có thể chứa CartItem từ nhiều Store và UI phải nhóm rõ theo Store. | TC05; cart-three-groups groupCount=3 | PASS |
| BR-07 | Một checkout có thể tạo nhiều Order; một Order chỉ thuộc đúng một Store. | TC06; checkout6281 → Orders9809/9810/9811 | PASS |
| BR-08 | Mỗi Store group tại checkout có fulfillment_type và payment_method riêng. | TC15; A DELIVERY/COD, B PICKUP/ONLINE, C PICKUP/PAY_AT_STORE | PASS |
| BR-09 | Checkout luôn tải lại price/quantity từ DB, lock/kiểm tra tồn và trừ kho trong transaction. | TC07/08 concurrency & tampering SQL/HTTP tests | PASS |
| BR-10 | Staff không duyệt việc có cho tạo Order hay không; hệ thống tự xác nhận đơn hợp lệ. Staff chỉ xử lý fulfillment. | Order tạo CONFIRMED tự động trước Staff prepare; ONLINE sau payment success | PASS |
| BR-11 | STORE_PICKUP sử dụng READY_FOR_PICKUP và pickup_code. | TC10; pickup B/C ready → mã Customer → Staff complete | PASS |
| BR-12 | Hủy Order hợp lệ phải hoàn tồn đúng StoreProduct và ghi nhận biến động kho. | TC12; Order9812 hoàn 819:11→12 một lần | PASS |
| BR-13 | Mọi thay đổi trạng thái Order được kiểm tra transition ở Service và ghi OrderStatusHistory. | TC16; 16 history fixture entries, timeline/status checks | PASS |
| BR-14 | Staff chỉ truy cập Store được phân công; điều kiện Store phải được kiểm tra ở backend. | TC11; revoke assignment397 cùng JWT →404, reactivate→200 | PASS |
| BR-15 | Client chỉ thấy Còn hàng/Hết hàng; số lượng tồn chính xác chỉ dành cho Staff/Admin. | DTO/HTTP tests không exact quantity; Staff/Admin stock fields và UI survey | PASS |
| BR-16 | Không tự chuyển CartItem sang Store khác khi người dùng đổi selectedStoreId. | TC03 SQL/cart tests; selected A→B→C vẫn giữ CartItems tại nguồn cũ | PASS |
| BR-17 | Không xóa cứng dữ liệu đã có lịch sử giao dịch; dùng status INACTIVE/HIDDEN. | TC18; Store47/Product503/StoreProduct819 INACTIVE vẫn còn Order/history | PASS |
| BR-18 | Payment gắn với Order, không gắn trực tiếp CheckoutSession, vì một checkout có thể sinh nhiều Order và mỗi Order có phương thức thanh toán khác nhau. | TC15; Payments của ba Order độc lập, checkout chỉ group | PASS |

Các PASS trên là quy tắc lõi được current regression/dynamic evidence kiểm chứng; chúng không bao hàm Customer review, Cloudinary live hoặc mọi chức năng P1. Xem [TC table](05_Integration_Test_Coverage.md) để truy vết exact method.

## Actor/function coverage

| Actor / module | Implementation & actual verification | Kết quả |
| --- | --- | --- |
| Guest auth/catalog/finder | AuthController/AuthService, Product/Store services; survey home/login/register/catalog/detail/StoreFinder; catalog HTTP/SQL/filter/selected context tests | Core PASS; Unicode registration FAIL FSA-02 |
| Customer cart/selection | CartService; repeated add 819 gives quantity2, thêm820/821 thành ba groups; UI checkbox group chọn item thật, backend own cart tests | PASS |
| Customer checkout/payment | CheckoutService/PaymentService; checkout6281 có total474000, Orders độc lập 222000/121000/131000; ONLINE B thanh toán trước Staff, offline thu lúc completion | PASS |
| Customer order/cancel | CustomerOrderService/OrderTransitionPolicy, trước PREPARING và UNPAID; Order9812 CONFIRMED→CANCELLED, duplicate không ghi lại, completed cancel400 | PASS |
| Customer review | ReviewService interface và Impl rỗng; completed order và product không có review action, valid CSRF POST hai candidate paths404 | **FAIL FSA-01**; chưa có route khác trong inventory |
| Staff multi-Store | Scope service/resolver đọc User/Role/assignment/Store ACTIVE hiện tại; fixture Staff270 có ba assignments; revoke397 chặn Order9809 với JWT cũ, reactivate phục hồi | PASS |
| Staff fulfillment/pickup | Production Delivery/Pickup services; DELIVERY prepare/pack/ship/complete, PICKUP prepare/ready/code/complete qua form UI. Repeat completion được reject, không duplicate payment/history | PASS |
| Staff inventory | StaffInventoryService + writer InventoryService; physical count8→12 actor270; history đúng `/staff/inventory-history/{id}` | PASS |
| Admin Store/assignment/catalog | Tạo Store/category/brand/SKU/StoreProduct/User/assignments qua UI; metadata và SQL kiểm lại; tái sử dụng CRUD Phase6 | PASS cho scope hiện hữu |
| Admin inventory/orders/overview | AdminOperationsService + reused StoreProduct query; store filter qua GET form UI, current tests stable pagination/counts, overview so với independent SQL | PASS |
| Admin User/Role/Review | Current Phase11 SQL/HTTP tests role revocation, password/role validation, review ACTIVE/HIDDEN chỉ visibility; UI list/forms/User disable-reactivate | PASS; không thay thế Customer review |
| Product image/Brand logo | ProductService/CloudinaryService, actual form có CSRF; mock-adapter SQL regression PASS, historical Product live evidence; current live không rerun | Live **NOT VERIFIED** hiện tại |

## Transaction/state/history

Checkout lock cart, lock StoreProduct PK tăng dần, reload price/availability, tạo session/orders/items/deduct/movements/initial history rồi xóa chỉ selected items trong cùng transaction. Initial order creation có entry old_status=NULL; subsequent transitions dùng policy/common history writer. ONLINE PENDING_PAYMENT→CONFIRMED chỉ sau success; failure restore target order. Không Staff approval gate trước checkout.

Cancel lock Order PK → close pending payment attempts → lock stocks tăng dần → restore đúng OrderItem → CANCEL_ORDER → CANCELLED/history. Nội bộ MANDATORY, outer customer cancellation REQUIRED. CANCELLED lặp là no-op; PAID/PREPARING trở đi bị reject. Rule do user xác nhận trước Phase12, không refund. Legacy offline pending receipts được phân biệt; không suy ra mọi PENDING payment đều là ONLINE.

State/history và SQL constraints không cho quantity âm, fulfillment/payment pair trái rule; SQL tests đã inject exception **sau real flush** rồi kiểm rollback exact rows/timestamps. [Concurrency table](03_Database_Integrity_Performance_Audit.md). Không Redis/queue/distributed lock, WMS hoặc audit table mới.

## Code quality / mismatch

ReviewServiceImpl skeleton là chức năng chưa triển khai thực tế, không coi là đã DONE vì có entity/service tên đúng (FSA-01). Auth Unicode encoder exception không được chuyển thành field validation (FSA-02). README deployment Docker lệch roadmap (FSA-03). Không sửa workaround/tests để hợp thức hóa các kết quả. Older repository read helpers còn hiện diện; chưa phát hiện endpoint dùng chúng để bỏ ownership/scope. Không tạo finding “dead code” chỉ từ tên hoặc import.

Cloudinary compensation không phải transaction phân tán: upload lỗi persist có deleteQuietly, brand replace cleanup old asset best effort. Sau database commit failure/provider delete failure vẫn có cửa sổ không atomic giữa provider và SQL; current tests kiểm các failure cases hiện hữu, crash-recovery mọi cửa sổ **NOT VERIFIED**. Không gọi đây là lỗi đã tái hiện.

Inventory chi tiết endpoint/service/entity/template: [inventory.md](evidence/inventory.md); exact test outcomes: [regression-summary.json](evidence/regression-summary.json).
