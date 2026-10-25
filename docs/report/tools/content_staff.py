# -*- coding: utf-8 -*-
"""Staff functions (see content_customer.py for the structure)."""

N, S = "Nhân viên", "Hệ thống"
LANES = [N, S]
DB = {"db": True}

FUNCS = [
dict(
 id="NV-01", title="Đăng nhập khu vực nhân viên và xác định phạm vi chi nhánh", actor="Nhân viên",
 desc=("Nhân viên đăng nhập bằng chức năng chung (mục KH-02) và được chuyển đến /staff. Mọi URL /staff/** và /api/staff/** chỉ dành cho vai trò STAFF. Phạm vi chi nhánh không lấy từ tham số, "
       "header hay nội dung JWT: StaffStoreScopeInterceptor đọc từ CSDL, ở mỗi request, danh sách chi nhánh mà nhân viên có phân công ACTIVE. Nhân viên chưa được phân công chỉ vào được trang "
       "/staff (hiển thị thông báo); các URL khác trả 403. Khi ghi dữ liệu, Service còn kiểm tra lại chi nhánh thật của dòng dữ liệu đã khóa."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Đăng nhập, mở một trang /staff/**"),
  ("dec",S,"Tài khoản có vai trò STAFF? (SecurityConfig)","có","không",["Chuyển /login hoặc trả 403"]),
  ("act",S,"Interceptor lấy phân công ACTIVE theo email của người đăng nhập",DB),
  ("act",S,"Ghi danh sách chi nhánh vào thuộc tính request assignedStores"),
  ("dec",S,"Có chi nhánh ACTIVE được phân công?","có","không",["Chỉ cho vào /staff (thông báo), URL khác trả 403"]),
  ("act",S,"Controller và Service chỉ truy vấn trong các chi nhánh đã phân công",DB),
  ("act",S,"Khi ghi: requireAssignedStore trên chi nhánh của dòng đã khóa"),
  ("act",S,"Hiển thị dữ liệu thuộc chi nhánh của nhân viên"),("end",N)],
 codes=[("Phân quyền URL khu vực nhân viên","config/SecurityConfig.java",107,111),
        ("StaffStoreScopeInterceptor.preHandle","security/StaffStoreScopeInterceptor.java",41,54),
        ("StaffStoreScopeService: phạm vi và kiểm tra chi nhánh","service/StaffStoreScopeService.java",38,60),
        ("Chi nhánh ACTIVE của nhân viên","service/impl/StoreServiceImpl.java",176,183)],
 explain=("SecurityConfig yêu cầu hasRole('STAFF') cho /staff/**. WebConfig đăng ký StaffStoreScopeInterceptor cho các đường dẫn này; preHandle gọi StoreService.getAssignedStores (truy vấn "
          "findActiveStoresByStaffEmail theo tên người dùng đã xác thực) và trả 403 khi danh sách rỗng, trừ trang /staff. StaffStoreScopeService.getAssignedStoreIds là bộ lọc duy nhất được "
          "truyền xuống các Repository; requireAssignedStore được gọi lại sau khi khóa dòng dữ liệu để chặn cả yêu cầu giả mạo id. Vì phạm vi được đọc lại ở mỗi request, việc đổi hoặc thu hồi phân công "
          "(mục QT-02) có hiệu lực ngay."),
 trace=dict(controller="Các controller /staff/** (StaffDashboardController, StaffFulfillmentController, StaffStockController, ...)", service="StaffStoreScopeInterceptor, StaffStoreScopeService, StoreServiceImpl.getAssignedStores",
            ui="templates/layouts/staff.html, staff/dashboard.html", data="StaffStoreAssignment, Store; StaffStoreAssignmentRepository"),
 status="Hoạt động",
),
dict(
 id="NV-02", title="Xem dashboard chi nhánh được phân công", actor="Nhân viên",
 desc=("Trang /staff hiển thị cho từng chi nhánh được phân công: số phần giao đang mở, số phần chờ khách nhận, số phần đang giao, số phần bị chặn bởi thanh toán, số phần thất bại, số mặt hàng "
       "sắp hết (1–5) và hết hàng, cùng 5 phần giao gần nhất. Đơn mà nhiều chi nhánh cùng cung cấp được tính một lần ở mỗi chi nhánh cho phần của chi nhánh đó."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Mở /staff"),
  ("dec",S,"Có chi nhánh ACTIVE được phân công?","có","không",["Hiển thị thông báo chưa được phân công"]),
  ("act",S,"Đếm phần giao theo chi nhánh và trạng thái",DB),
  ("act",S,"Tách: đang mở, chờ nhận, đang giao, bị chặn thanh toán, thất bại"),
  ("act",S,"Đếm mặt hàng sắp hết và hết hàng của từng chi nhánh",DB),
  ("act",S,"Lấy 5 phần giao đang mở gần nhất",DB),
  ("act",S,"Hiển thị staff/dashboard"),("end",N)],
 codes=[("StaffDashboardController","controller/staff/StaffDashboardController.java",23,28),
        ("StaffOperationsService.getDashboard","service/StaffOperationsService.java",48,79)],
 explain=("StaffDashboardController.landing chỉ gọi getDashboard khi thuộc tính assignedStores (do interceptor đặt) không rỗng. getDashboard lấy phạm vi từ StaffStoreScopeService, nhóm số "
          "phần giao theo chi nhánh và trạng thái bằng OrderFulfillmentRepository.countByStoreAndStatus rồi trừ phần bị chặn khỏi phần việc cần làm, đếm tồn thấp bằng LOW_STOCK_THRESHOLD và "
          "lấy các phần giao gần nhất qua StaffFulfillmentService.getRecent. Các con số được tính từ Fulfillment, không từ trạng thái tổng của Order."),
 trace=dict(controller="StaffDashboardController (GET /staff, /staff/dashboard)", service="StaffOperationsService.getDashboard, StaffFulfillmentService.getRecent",
            ui="templates/staff/dashboard.html", data="OrderFulfillment, StoreProduct"),
 status="Hoạt động",
),
dict(
 id="NV-03", title="Xem hàng đợi phần giao hàng của chi nhánh", actor="Nhân viên",
 desc=("Đơn vị công việc của nhân viên là một phần giao (OrderFulfillment), không phải cả đơn. Trang /staff/fulfillments (kèm /delivery và /pickup) liệt kê các phần giao thuộc chi nhánh "
       "được phân công, mặc định chỉ các phần đang mở, có thể lọc theo chi nhánh, trạng thái, loại, mã đơn, khoảng ngày và từ khóa khách hàng. Với mỗi phần giao, hệ thống tính các thao tác hợp lệ; "
       "nếu đơn đang bị chặn (chờ thanh toán, đã hủy, cần quản trị viên xử lý, thanh toán chưa xác nhận) thì thao tác bị ẩn và hiển thị lý do."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Mở /staff/fulfillments (hoặc /delivery, /pickup), chọn bộ lọc"),
  ("dec",S,"Chi nhánh được lọc thuộc phạm vi phân công?","có","không",["403 Không được phân công tại chi nhánh này"]),
  ("act",S,"Xác định trạng thái (mặc định: đang mở), loại, khoảng ngày"),
  ("act",S,"searchForStaff trên các chi nhánh được phân công, phân trang 20",DB),
  ("act",S,"Nạp dòng hàng của các phần giao trong trang",DB),
  ("act",S,"Tính thao tác hợp lệ; phần bị chặn hiện lý do thay cho thao tác"),
  ("act",S,"Hiển thị staff/fulfillments/index"),("end",N)],
 codes=[("StaffFulfillmentController.queue","controller/staff/StaffFulfillmentController.java",41,55),
        ("StaffFulfillmentService.getQueue","service/fulfillment/StaffFulfillmentService.java",116,150),
        ("Thao tác hợp lệ của từng phần giao","service/fulfillment/StaffFulfillmentService.java",428,440)],
 explain=("getQueue lấy danh sách chi nhánh đã phân công, từ chối storeId ngoài phạm vi bằng AccessDeniedException và chỉ truyền các id hợp lệ vào OrderFulfillmentRepository.searchForStaff cùng bộ lọc "
          "trạng thái, loại, khoảng ngày và từ khóa. toItem lọc FulfillmentAction theo loại phần giao và trạng thái hiện tại; blockedCode quyết định việc thao tác bị ẩn khi đơn đang chờ thanh toán, "
          "đã hủy hoặc cần xử lý. Đường dẫn cũ /staff/orders của V2 chỉ hiển thị cùng hàng đợi hoặc chuyển hướng đọc."),
 trace=dict(controller="StaffFulfillmentController (GET /staff/fulfillments, /delivery, /pickup; /staff/orders chuyển hướng)", service="StaffFulfillmentService.getQueue / toItem",
            ui="templates/staff/fulfillments/index.html, delivery/fragments.html", data="OrderFulfillment, Order, OrderItem; OrderFulfillmentRepository"),
 status="Hoạt động",
),
dict(
 id="NV-04", title="Xử lý phần giao tận nơi (chuẩn bị, đóng gói, giao, hoàn tất, thất bại)", actor="Nhân viên",
 desc=("Nhân viên thay đổi trạng thái của một phần giao tận nơi theo trình tự CONFIRMED → PREPARING → PACKED → SHIPPING → COMPLETED hoặc FAILED. Mỗi thao tác chỉ áp dụng cho phần giao của chi nhánh mình, "
       "chỉ khi đơn không bị chặn bởi thanh toán, và chỉ từ đúng trạng thái trước đó. 'Bàn giao vận chuyển' có thể kèm mã vận đơn; 'Xác nhận giao thành công' của đơn COD ghi nhận thu tiền; "
       "'Giao hàng không thành công' bắt buộc có lý do 5–500 ký tự. Nhân viên không sửa trạng thái Order: OrderStatusAggregator tự tính lại trạng thái đơn trong cùng giao dịch, và không thao tác nào chạm vào tồn kho."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Mở /staff/fulfillments/{id}, chọn một thao tác"),
  ("act",S,"Xác định chi nhánh của phần giao",DB),
  ("dec",S,"Chi nhánh thuộc phạm vi được phân công?","có","không",["403 Không được phân công xử lý phần giao này"]),
  ("act",S,"Khóa Order rồi khóa Fulfillment; kiểm tra lại chi nhánh trên dòng đã khóa",DB),
  ("dec",S,"Đơn không bị hủy, chờ thanh toán, cần xử lý; thanh toán đã xác nhận?","có","không",["409 Bị chặn bởi đơn hàng hoặc thanh toán"]),
  ("dec",S,"Thao tác hợp loại và trạng thái hiện tại đúng điểm xuất phát?","đúng","không",["409 Trạng thái đã thay đổi, tải lại trang"]),
  ("dec",S,"Dữ liệu kèm theo hợp lệ (mã vận đơn, lý do)?","hợp lệ","sai",["Báo lỗi mã vận đơn hoặc lý do"]),
  ("act",S,"Giao thành công đơn COD: ghi nhận thu tiền, đơn PAID",DB),
  ("act",S,"Ghi trạng thái mới và lịch sử của phần giao",DB),
  ("act",S,"OrderStatusAggregator.recompute: tính lại trạng thái đơn",DB),
  ("act",S,"Chuyển /staff/fulfillments/{id}"),("end",N)],
 codes=[("Các thao tác hợp lệ của nhân viên","service/fulfillment/FulfillmentAction.java",14,26),
        ("StaffFulfillmentService.perform (1)","service/fulfillment/StaffFulfillmentService.java",216,239),
        ("StaffFulfillmentService.perform (2)","service/fulfillment/StaffFulfillmentService.java",240,264),
        ("StaffFulfillmentService.perform (3)","service/fulfillment/StaffFulfillmentService.java",284,301),
        ("OrderStatusAggregator.derive","service/fulfillment/OrderStatusAggregator.java",50,75)],
 explain=("FulfillmentAction là bảng chuyển trạng thái duy nhất: mỗi hằng số nêu loại phần giao, trạng thái nguồn và đích. perform xác định phạm vi trước khi khóa (requireInScope), khóa Order rồi "
          "Fulfillment (cùng thứ tự với luồng hủy và thanh toán), kiểm tra lại chi nhánh trên dòng đã khóa, kiểm tra cổng thanh toán requireWorkable, khớp hành động với trạng thái, rồi áp tác dụng phụ trong "
          "switch (ví dụ recordOfflineCollected cho COMPLETE_DELIVERY của COD). Lịch sử OrderFulfillmentStatusHistory được ghi và aggregator.recompute suy ra trạng thái Order từ các phần giao: FAILED cho NEEDS_ATTENTION, "
          "tất cả hoàn tất cho COMPLETED, một phần hoàn tất cho PARTIALLY_COMPLETED, có phần đang giao cho SHIPPING. Mọi lỗi ném ngoại lệ trước khi ghi nên giao dịch được hoàn tác."),
 trace=dict(controller="StaffFulfillmentController.act (POST /staff/fulfillments/{id}/{prepare|pack|ship|complete-delivery|fail-delivery|handover})", service="StaffFulfillmentService.perform, FulfillmentAction, OrderStatusAggregator, PaymentServiceImpl.recordOfflineCollected",
            ui="templates/staff/fulfillments/detail.html", data="OrderFulfillment, OrderFulfillmentStatusHistory, Order, OrderStatusHistory, Payment"),
 status="Hoạt động",
),
dict(
 id="NV-05", title="Xử lý nhận hàng tại cửa hàng và xác minh mã nhận hàng", actor="Nhân viên",
 desc=("Với phần giao STORE_PICKUP, sau khi chuẩn bị xong nhân viên chọn 'Sẵn sàng cho khách nhận': hệ thống sinh mã nhận hàng duy nhất trong chi nhánh và ghi thời điểm sẵn sàng; khách xem mã trong chi tiết đơn của mình. "
       "Khi khách đến, nhân viên nhập mã để 'Xác nhận khách đã nhận hàng'. Mã sai không ghi gì và được đếm: sai 5 lần thì mã bị khóa 15 phút. Với đơn thanh toán tại cửa hàng, thao tác này ghi nhận thu tiền. "
       "Nếu khách không đến, nhân viên báo 'Khách không nhận hàng' kèm lý do và phần giao FAILED chờ quản trị viên xử lý."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Chuẩn bị xong, bấm 'Sẵn sàng cho khách nhận'"),
  ("act",S,"Kiểm tra phạm vi, khóa đơn và phần giao, cổng thanh toán (như NV-04)",DB),
  ("dec",S,"Phần giao PREPARING và chưa có mã nhận hàng?","có","không",["409 Thông tin nhận hàng không nhất quán"]),
  ("act",S,"Sinh mã nhận hàng duy nhất trong chi nhánh; READY_FOR_PICKUP",DB),
  ("act",N,"Khách đến, đọc mã; nhân viên nhập mã (complete-pickup)"),
  ("dec",S,"Mã chưa bị khóa (dưới 5 lần sai trong 15 phút)?","chưa khóa","đang khóa",["409 Sai quá nhiều lần, thử lại sau 15 phút"]),
  ("dec",S,"Mã nhập khớp mã nhận hàng?","khớp","sai",["Đếm lần sai, báo lỗi; chưa ghi gì"]),
  ("act",S,"Đơn PAY_AT_STORE: ghi nhận thu tiền, đơn PAID",DB),
  ("act",S,"Ghi pickedUpAt, completedAt; COMPLETED; xóa bộ đếm sai",DB),
  ("act",S,"Tính lại trạng thái đơn từ các phần giao",DB),("end",N)],
 codes=[("Sẵn sàng nhận và xác minh mã","service/fulfillment/StaffFulfillmentService.java",251,283),
        ("PickupAttemptLimiter","service/fulfillment/PickupAttemptLimiter.java",39,63)],
 explain=("Trong perform, MARK_READY chỉ chạy khi phần giao chưa có pickupCode hoặc readyAt, sinh mã bằng newPickupCode (thử lại tối đa 8 lần để mã duy nhất trong chi nhánh). COMPLETE_PICKUP gọi "
          "limiter.requireNotLocked, so mã bằng PickupCodeService.verify và ghi recordFailure khi sai; vì lỗi làm giao dịch hoàn tác, bộ đếm được giữ trong bộ nhớ (PickupAttemptLimiter) để vẫn đếm được. "
          "Khi mã đúng, đơn PAY_AT_STORE gọi payments.recordOfflineCollected, rồi đặt pickedUpAt, completedAt và xóa bộ đếm. Mã nhận hàng bị ẩn khỏi ghi chú và lịch sử bằng redactPickupCode khi nhân viên xem chi tiết."),
 trace=dict(controller="StaffFulfillmentController.act (POST .../ready-for-pickup, .../complete-pickup, .../fail-pickup)", service="StaffFulfillmentService.perform, PickupCodeService, PickupAttemptLimiter, PaymentServiceImpl.recordOfflineCollected",
            ui="templates/staff/fulfillments/detail.html, staff/fulfillments/index.html (hàng đợi pickup)", data="OrderFulfillment (pickup_code, ready_at, picked_up_at), Payment"),
 status="Hoạt động",
),
dict(
 id="NV-06", title="Xem tồn kho chi nhánh và điều chỉnh tồn", actor="Nhân viên",
 desc=("Trang /staff/stock liệt kê tồn kho của các chi nhánh được phân công, có tìm kiếm theo SKU hoặc tên, lọc mức tồn (hết, sắp hết, đủ), sắp xếp và các thẻ thống kê. Nhân viên điều chỉnh số lượng "
       "của một mặt hàng kèm ghi chú; số lượng mới không được âm hoặc thấp hơn số đang được giữ. Chi nhánh được xác định từ dòng StoreProduct đã khóa chứ không từ giá trị gửi lên; mỗi lần đổi ghi một "
       "InventoryMovement loại STOCK_ADJUST với người thực hiện. Điều chỉnh về đúng số hiện tại không ghi gì."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Mở /staff/stock, tìm và lọc; chọn Điều chỉnh một mặt hàng"),
  ("act",S,"getStock và stockStats trong các chi nhánh được phân công",DB),
  ("act",N,"Nhập số lượng mới và ghi chú (POST /staff/stock/{id}/adjust)"),
  ("dec",S,"Biểu mẫu StockAdjustRequest hợp lệ?","có","không",["400: hiển thị lại form (vẫn trong phạm vi)"]),
  ("act",S,"Khóa StoreProduct; lấy chi nhánh thật của dòng đã khóa",DB),
  ("dec",S,"Chi nhánh thuộc phạm vi được phân công?","có","không",["403 Không được phân công điều chỉnh tồn"]),
  ("dec",S,"Số lượng mới ≥ 0 và ≥ số đang giữ?","đạt","sai",["Báo tồn kho không hợp lệ"]),
  ("dec",S,"Số lượng mới khác số hiện tại?","khác","bằng nhau",["Không ghi gì; ?success=unchanged"]),
  ("act",S,"Cập nhật quantity; ghi InventoryMovement STOCK_ADJUST",DB),
  ("act",S,"Chuyển /staff/inventory-history/{id}?success=adjusted"),("end",N)],
 codes=[("StaffInventoryService.adjust","service/StaffInventoryService.java",187,197),
        ("InventoryServiceImpl.adjustStock","service/impl/InventoryServiceImpl.java",48,75),
        ("StaffStockController.adjust","controller/staff/StaffStockController.java",80,90)],
 explain=("StaffInventoryService.adjust trước hết resolve phạm vi (getAssignedStoreIds), khóa StoreProduct bằng findByIdForUpdate và gọi requireAssignedStore trên chi nhánh thật của dòng đã khóa; "
          "chỉ sau đó mới gọi InventoryService.adjustStock, nơi kiểm tra số âm và reservedQuantity rồi ghi InventoryMovement. Giá trị trả về cho biết có thay đổi hay không để controller chọn "
          "?success=adjusted hoặc unchanged. Lỗi AccessDeniedException và DataAccessException được StaffStockController chuyển thành trang lỗi 403 hoặc 409."),
 trace=dict(controller="StaffStockController (GET /staff/stock, /staff/stock/{id}/adjust; POST /staff/stock/{id}/adjust)", service="StaffInventoryService.getStock / adjust, InventoryServiceImpl.adjustStock",
            ui="templates/staff/stock/index.html, stock/adjust.html", data="StoreProduct, InventoryMovement"),
 status="Hoạt động",
),
dict(
 id="NV-07", title="Xem lịch sử biến động kho", actor="Nhân viên",
 desc=("Nhân viên xem nhật ký biến động kho của các chi nhánh được phân công (mới nhất trước), lọc theo từ khóa (SKU, sản phẩm, chi nhánh, ghi chú hoặc mã đơn), loại biến động, nhân viên ghi nhận và khoảng ngày; "
       "hoặc xem lịch sử của từng mặt hàng. Các dòng do hệ thống tạo (đặt hàng, hủy đơn) không có người ghi nhận và hiển thị là 'Hệ thống'."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Mở /staff/inventory-history, chọn bộ lọc"),
  ("dec",S,"Khoảng ngày hợp lệ?","hợp lệ","sai",["Từ chối: khoảng ngày không hợp lệ"]),
  ("act",S,"getMovementLog: nhật ký các chi nhánh được phân công, mới nhất trước",DB),
  ("act",N,"Chọn một mặt hàng (/staff/inventory-history/{id})"),
  ("dec",S,"Mặt hàng thuộc chi nhánh được phân công?","có","không",["Từ chối: không thuộc phạm vi chi nhánh"]),
  ("act",S,"Đọc lịch sử biến động của StoreProduct (phân trang 20)",DB),
  ("act",S,"Hiển thị staff/stock/history"),("end",N)],
 codes=[("StaffInventoryService.getMovementLog","service/StaffInventoryService.java",120,142),
        ("StaffInventoryService.getHistory","service/StaffInventoryService.java",174,184),
        ("StaffStoreScopeService.requireStoreProduct","service/StaffStoreScopeService.java",70,75)],
 explain=("getMovementLog luôn lọc theo getAssignedStoreIds nên không đọc được nhật ký chi nhánh khác; từ khóa số được hiểu là mã đơn, từ khóa chữ được so khớp trong bộ nhớ trên SKU, tên, chi nhánh và ghi chú. "
          "getHistory gọi getStoreProduct, tức StaffStoreScopeService.requireStoreProduct, để từ chối mặt hàng ngoài phạm vi trước khi đọc bất kỳ lịch sử nào, rồi trả các InventoryMovement của chính mặt hàng đó."),
 trace=dict(controller="StaffStockController (GET /staff/inventory-history, /staff/inventory-history/{id})", service="StaffInventoryService.getMovementLog / getHistory, StaffStoreScopeService.requireStoreProduct",
            ui="templates/staff/stock/inventory-history.html, stock/history.html", data="InventoryMovement, StoreProduct"),
 status="Hoạt động",
),
dict(
 id="NV-08", title="Xử lý hàng khách trả (nhập lại kho hoặc xử lý)", actor="Nhân viên",
 desc=("Sau khi quản trị viên duyệt yêu cầu trả hàng, các dòng hàng xuất hiện tại /staff/returns cho nhân viên của chi nhánh đã giao từng dòng. Với mỗi dòng nhân viên chọn một lần: nhập lại kho (tồn của đúng "
       "StoreProduct tăng, ghi InventoryMovement loại RETURN_ORDER) hoặc xử lý ngoài hệ thống (không đổi tồn). Nhân viên không đụng đến tiền vì hoàn tiền đã ghi khi quản trị viên duyệt. Khi dòng cuối cùng "
       "được xử lý, yêu cầu chuyển sang COMPLETED."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Mở /staff/returns; xem dòng hàng đã duyệt của chi nhánh"),
  ("act",S,"Liệt kê OrderReturnItem của các chi nhánh được phân công",DB),
  ("act",N,"Chọn Nhập lại kho hoặc Xử lý, nhập ghi chú"),
  ("dec",S,"Lựa chọn hợp lệ và ghi chú ≤ 400 ký tự?","có","không",["Báo lỗi action hoặc note"]),
  ("dec",S,"Chi nhánh của dòng hàng thuộc phạm vi phân công?","có","không",["403 Không được phân công"]),
  ("act",S,"Khóa đơn rồi khóa dòng hàng trả",DB),
  ("dec",S,"Dòng còn PENDING và yêu cầu đã APPROVED?","đúng","không",["409 Đã xử lý hoặc chưa được duyệt"]),
  ("act",S,"Ghi disposition, người xử lý, thời điểm",DB),
  ("dec",S,"Nhập lại kho (RESTOCKED)?","xử lý ngoài","nhập kho",["restoreReturnedItem: cộng tồn, ghi RETURN_ORDER","Hết dòng PENDING thì yêu cầu COMPLETED"]),
  ("act",S,"Hết dòng PENDING thì yêu cầu COMPLETED; không đổi tồn, tiền",DB),("end",N)],
 codes=[("StaffOrderReturnService.handle (1)","service/returns/StaffOrderReturnService.java",78,103),
        ("StaffOrderReturnService.handle (2)","service/returns/StaffOrderReturnService.java",105,123)],
 explain=("handle kiểm tra lựa chọn và ghi chú, xác thực quyền trên chi nhánh của dòng trước khi khóa (peek và requireAssignedStore), khóa đơn rồi dòng hàng và kiểm tra lại chi nhánh trên dòng đã khóa. "
          "Dòng đã xử lý trả FulfillmentConflictException; yêu cầu chưa APPROVED bị từ chối. RESTOCKED gọi InventoryService.restoreReturnedItem (kiểm tra chi nhánh, cộng tồn bằng Math.addExact, ghi RETURN_ORDER), "
          "còn DISPOSED chỉ ghi lựa chọn. Khi countByOrderReturnIdAndDisposition(PENDING) bằng 0, yêu cầu được đặt COMPLETED."),
 trace=dict(controller="StaffOrderReturnController (GET /staff/returns; POST /staff/returns/items/{id}/restock, /dispose)", service="StaffOrderReturnService.handle, InventoryServiceImpl.restoreReturnedItem",
            ui="templates/staff/returns/index.html", data="OrderReturnItem, OrderReturn, StoreProduct, InventoryMovement"),
 status="Hoạt động",
),
dict(
 id="NV-09", title="Phản hồi đánh giá của khách", actor="Nhân viên",
 desc=("Nhân viên xem các đánh giá có nguồn hàng thuộc chi nhánh được phân công và trả lời. Chỉ nhân viên của chi nhánh đã giao dòng hàng được đánh giá mới có quyền phản hồi; quản trị viên chỉ kiểm duyệt. "
       "Mỗi đánh giá có một phản hồi, gửi lại sẽ cập nhật nội dung phản hồi hiện có."),
 lanes=LANES,
 steps=[("start",N),("act",N,"Mở /staff/reviews, chọn một đánh giá, nhập phản hồi"),
  ("act",S,"getStaffReviews: đánh giá ACTIVE có nguồn hàng thuộc chi nhánh được phân công",DB),
  ("act",S,"Khóa dòng Review theo id",DB),
  ("dec",S,"Đánh giá ACTIVE, nguồn hàng đã nhận, chi nhánh nguồn thuộc phạm vi?","có","không",["403 Không được phân công xử lý đánh giá"]),
  ("dec",S,"Nội dung phản hồi hợp lệ (ReviewContent.reply)?","có","không",["Chuyển ?replyError=invalid"]),
  ("act",S,"Tạo hoặc cập nhật ReviewReply (một phản hồi cho mỗi đánh giá)",DB),
  ("act",S,"Chuyển /staff/reviews?replied"),("end",N)],
 codes=[("ReviewReplyService","service/review/ReviewReplyService.java",31,55),
        ("StaffReviewController.reply","controller/staff/StaffReviewController.java",29,39)],
 explain=("ReviewReplyService.reply xác minh người gọi là nhân viên ACTIVE, khóa Review bằng findByIdForUpdate rồi gọi requireScope: đánh giá phải ACTIVE, có nguồn hàng xác thực (ReviewSource.verified), "
          "phần giao đã nhận và chi nhánh của phần giao phải thuộc phạm vi phân công (StaffStoreScopeService.requireAssignedStore). Sau đó ReviewContent.reply kiểm tra nội dung và ReviewReply được tạo hoặc cập nhật. "
          "StaffReviewController chuyển BadRequestException thành ?replyError=invalid."),
 trace=dict(controller="StaffReviewController (GET /staff/reviews, /staff/reviews/{id}; POST /staff/reviews/{id}/reply)", service="ReviewServiceImpl.getStaffReviews / reply, ReviewReplyService.reply / requireScope",
            ui="templates/staff/reviews/index.html, reviews/detail.html", data="Review, ReviewReply, OrderItem, OrderFulfillment"),
 status="Hoạt động",
),
]
