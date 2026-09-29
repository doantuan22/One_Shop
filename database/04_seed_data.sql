/* =============================================================================
   OneShop - Phase 2 - File 04: seed du lieu demo (mo hinh chuoi)
   - 5 Store (4 ACTIVE + 1 INACTIVE de chuan bi TC-18); moi SKU xuat hien o nhieu Store
     voi price / quantity / status khac nhau (TC-01).
   - 1 checkout sinh 3 Order o 3 Store, moi Order co fulfillment + payment rieng (TC-06, TC-15).
   - Co Payment theo Order, InventoryMovement va OrderStatusHistory.
   Tai khoan demo: mat khau chung "OneShop@123" (BCrypt) - CHI DUNG DEMO.
   Script tu don du lieu cu roi nap lai nen chay lai duoc nhieu lan.
   Dung IDENTITY_INSERT voi id co dinh de cac tham chieu ben duoi on dinh.
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRANSACTION;

/* ---------- Don du lieu cu (con truoc, cha sau) ---------- */
DELETE FROM dbo.reviews;
DELETE FROM dbo.order_status_history;
DELETE FROM dbo.payments;
DELETE FROM dbo.order_items;
DELETE FROM dbo.inventory_movements;
DELETE FROM dbo.orders;
DELETE FROM dbo.checkout_sessions;
DELETE FROM dbo.cart_items;
DELETE FROM dbo.carts;
DELETE FROM dbo.store_products;
DELETE FROM dbo.product_images;
DELETE FROM dbo.products;
DELETE FROM dbo.brands;
DELETE FROM dbo.categories;
DELETE FROM dbo.staff_store_assignments;
DELETE FROM dbo.stores;
DELETE FROM dbo.customer_addresses;
DELETE FROM dbo.users;
DELETE FROM dbo.roles;

/* ---------- roles ---------- */
SET IDENTITY_INSERT dbo.roles ON;
INSERT INTO dbo.roles (role_id, name, description) VALUES
 (1, N'CUSTOMER', N'Khách hàng'),
 (2, N'STAFF',    N'Nhân viên cửa hàng'),
 (3, N'ADMIN',    N'Quản trị viên toàn chuỗi');
SET IDENTITY_INSERT dbo.roles OFF;

/* ---------- users (BCrypt cua "OneShop@123") ---------- */
SET IDENTITY_INSERT dbo.users ON;
INSERT INTO dbo.users (user_id, role_id, email, password_hash, full_name, phone, status) VALUES
 (1, 3, N'admin@oneshop.vn',          N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Quản Trị OneShop',      N'0900000001', N'ACTIVE'),
 (2, 2, N'staff.thuduc@oneshop.vn',   N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Nguyễn Văn An (Thủ Đức)', N'0900000002', N'ACTIVE'),
 (3, 2, N'staff.govap@oneshop.vn',    N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Trần Thị Bình (Gò Vấp)',  N'0900000003', N'ACTIVE'),
 (4, 2, N'staff.quan7@oneshop.vn',    N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Lê Minh Châu (Quận 7)',   N'0900000004', N'ACTIVE'),
 (5, 1, N'khachhang1@example.com',    N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Phạm Thu Hà',            N'0911111111', N'ACTIVE'),
 (6, 1, N'khachhang2@example.com',    N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Đặng Quốc Khánh',        N'0922222222', N'ACTIVE'),
 (7, 2, N'staff.quan10@oneshop.vn',   N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Võ Hoài Dung (Quận 10)',  N'0900000007', N'ACTIVE');
SET IDENTITY_INSERT dbo.users OFF;

/* ---------- customer_addresses ---------- */
INSERT INTO dbo.customer_addresses (user_id, receiver_name, receiver_phone, address_line, is_default) VALUES
 (5, N'Phạm Thu Hà',     N'0911111111', N'12 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM', 1),
 (6, N'Đặng Quốc Khánh', N'0922222222', N'45 Quang Trung, P.10, Q. Gò Vấp, TP.HCM',            1);

/* ---------- stores ---------- */
SET IDENTITY_INSERT dbo.stores ON;
INSERT INTO dbo.stores (store_id, code, name, address, province_city, area, phone, opening_hours, delivery_enabled, pickup_enabled, status) VALUES
 (1, N'OS-THUDUC', N'OneShop Thủ Đức', N'101 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức',  N'TP. Hồ Chí Minh', N'Thủ Đức', N'02811110001', N'08:00 - 22:00 hằng ngày', 1, 1, N'ACTIVE'),
 (2, N'OS-GOVAP',  N'OneShop Gò Vấp',  N'202 Quang Trung, P.10, Q. Gò Vấp',              N'TP. Hồ Chí Minh', N'Gò Vấp',  N'02811110002', N'08:00 - 22:00 hằng ngày', 1, 1, N'ACTIVE'),
 (3, N'OS-QUAN7',  N'OneShop Quận 7',  N'303 Nguyễn Thị Thập, P. Tân Phong, Q.7',        N'TP. Hồ Chí Minh', N'Quận 7',  N'02811110003', N'09:00 - 22:00 hằng ngày', 1, 1, N'ACTIVE'),
 (4, N'OS-QUAN10', N'OneShop Quận 10', N'404 Ba Tháng Hai, P.12, Q.10',                  N'TP. Hồ Chí Minh', N'Quận 10', N'02811110004', N'09:00 - 21:30 hằng ngày', 0, 1, N'ACTIVE'),
 (5, N'OS-HAICHAU',N'OneShop Hải Châu',N'505 Nguyễn Văn Linh, Q. Hải Châu',              N'Đà Nẵng',         N'Hải Châu',N'02361110005', N'08:30 - 21:30 hằng ngày', 1, 1, N'INACTIVE');
SET IDENTITY_INSERT dbo.stores OFF;

/* ---------- staff_store_assignments ---------- */
INSERT INTO dbo.staff_store_assignments (user_id, store_id, status) VALUES
 (2, 1, N'ACTIVE'),
 (3, 2, N'ACTIVE'),
 (4, 3, N'ACTIVE'),
 (7, 4, N'ACTIVE');

/* ---------- categories / brands ---------- */
SET IDENTITY_INSERT dbo.categories ON;
INSERT INTO dbo.categories (category_id, name, description, status) VALUES
 (1, N'Chăm sóc da',  N'Serum, toner, sữa rửa mặt',  N'ACTIVE'),
 (2, N'Trang điểm',   N'Má hồng, son, phấn',          N'ACTIVE'),
 (3, N'Chăm sóc tóc', N'Dầu gội, dầu xả',             N'ACTIVE');
SET IDENTITY_INSERT dbo.categories OFF;

SET IDENTITY_INSERT dbo.brands ON;
INSERT INTO dbo.brands (brand_id, name, description, status) VALUES
 (1, N'BBIA',      N'Mỹ phẩm trang điểm Hàn Quốc',  N'ACTIVE'),
 (2, N'Cocoon',    N'Mỹ phẩm thuần chay Việt Nam',  N'ACTIVE'),
 (3, N'Innisfree', N'Mỹ phẩm thiên nhiên Hàn Quốc', N'ACTIVE');
SET IDENTITY_INSERT dbo.brands OFF;

/* ---------- products: moi dong = MOT SKU (khong co variant) ---------- */
SET IDENTITY_INSERT dbo.products ON;
INSERT INTO dbo.products (product_id, category_id, brand_id, sku, name, description, status) VALUES
 (1, 2, 1, N'BBIA-CHEEK-06',   N'BBIA Downy Cheek #06',                    N'Má hồng dạng kem, tông #06.',                     N'ACTIVE'),
 (2, 2, 1, N'BBIA-CHEEK-08',   N'BBIA Downy Cheek #08',                    N'Má hồng dạng kem, tông #08.',                     N'ACTIVE'),
 (3, 1, 2, N'COCOON-SERUM-30', N'Cocoon Serum Bí Đao 30ml',                N'Serum bí đao hỗ trợ da dầu mụn.',                 N'ACTIVE'),
 (4, 1, 3, N'INNI-TONER-200',  N'Innisfree Green Tea Balancing Toner 200ml', N'Toner trà xanh cân bằng da.',                   N'ACTIVE'),
 (5, 3, 2, N'COCOON-SHAM-310', N'Cocoon Dầu Gội Bưởi 310ml',               N'Dầu gội tinh dầu bưởi.',                          N'ACTIVE'),
 (6, 1, 3, N'INNI-CLEANS-120', N'Innisfree Green Tea Cleansing Foam 120ml', N'Sữa rửa mặt tạo bọt trà xanh.',                  N'ACTIVE'),
 (7, 2, 2, N'COCOON-LIP-05',   N'Cocoon Son Dưỡng Dầu Dừa 5g (ngừng bán)', N'SKU đã ngừng kinh doanh, giữ lại để bảo toàn lịch sử.', N'INACTIVE');
SET IDENTITY_INSERT dbo.products OFF;

/* ---------- store_products: nguon su that price / quantity / status theo Store ----------
   quantity la ton HIEN TAI (da tinh cac don + movement seed ben duoi). */
SET IDENTITY_INSERT dbo.store_products ON;
INSERT INTO dbo.store_products (store_product_id, store_id, product_id, price, quantity, status) VALUES
 -- Serum Bi Dao (p3): 5 Store, gia/ton/trang thai khac nhau
 ( 1, 1, 3, 129000, 40, N'ACTIVE'),     -- Thu Duc: con hang
 ( 2, 2, 3, 135000, 15, N'ACTIVE'),     -- Go Vap: gia cao hon
 ( 3, 3, 3, 129000,  0, N'ACTIVE'),     -- Quan 7: het hang (ACTIVE, quantity = 0)
 ( 4, 4, 3, 139000, 25, N'ACTIVE'),     -- Quan 10
 ( 5, 5, 3, 129000, 10, N'INACTIVE'),   -- Hai Chau: Store INACTIVE, ngung ban
 -- BBIA Cheek #06 (p1)
 ( 6, 1, 1, 189000, 12, N'ACTIVE'),
 ( 7, 2, 1, 195000,  0, N'ACTIVE'),     -- het hang
 ( 8, 3, 1, 189000, 30, N'ACTIVE'),
 -- BBIA Cheek #08 (p2)
 ( 9, 1, 2, 189000,  8, N'ACTIVE'),
 (10, 2, 2, 195000, 20, N'ACTIVE'),
 -- Innisfree Toner (p4)
 (11, 1, 4, 255000, 25, N'ACTIVE'),
 (12, 2, 4, 249000, 18, N'ACTIVE'),
 (13, 3, 4, 259000,  9, N'ACTIVE'),
 (14, 4, 4, 255000,  0, N'INACTIVE'),   -- Quan 10 tam ngung ban SKU nay
 -- Cocoon Dau goi (p5)
 (15, 1, 5,  95000, 60, N'ACTIVE'),
 (16, 2, 5,  95000, 45, N'ACTIVE'),
 (17, 3, 5,  99000, 22, N'ACTIVE'),
 -- Innisfree Cleansing Foam (p6)
 (18, 1, 6, 165000, 33, N'ACTIVE'),
 (19, 3, 6, 169000, 14, N'ACTIVE'),
 (20, 4, 6, 165000,  7, N'ACTIVE'),
 -- SKU ngung ban (p7) - soft status, khong xoa cung
 (21, 1, 7,  45000,  0, N'INACTIVE');
SET IDENTITY_INSERT dbo.store_products OFF;

/* ---------- carts / cart_items: khach 2 co gio hang tu 3 Store ---------- */
SET IDENTITY_INSERT dbo.carts ON;
INSERT INTO dbo.carts (cart_id, user_id) VALUES (1, 6), (2, 5);
SET IDENTITY_INSERT dbo.carts OFF;

INSERT INTO dbo.cart_items (cart_id, store_product_id, quantity) VALUES
 (1, 11, 1),   -- Toner @ Thu Duc
 (1, 10, 1),   -- Cheek #08 @ Go Vap
 (1,  8, 2);   -- Cheek #06 @ Quan 7

/* ---------- checkout_sessions ---------- */
SET IDENTITY_INSERT dbo.checkout_sessions ON;
INSERT INTO dbo.checkout_sessions (checkout_id, user_id, total_amount, status, created_at) VALUES
 (1, 5, 1063000, N'CREATED',   DATEADD(HOUR, -30, SYSDATETIME())),   -- 447000 + 249000 + 367000
 (2, 6,  189000, N'CANCELLED', DATEADD(HOUR, -20, SYSDATETIME())),
 (3, 5,  129000, N'COMPLETED', DATEADD(DAY,  -10, SYSDATETIME()));
SET IDENTITY_INSERT dbo.checkout_sessions OFF;

/* ---------- orders: moi Order dung 1 Store ---------- */
SET IDENTITY_INSERT dbo.orders ON;
INSERT INTO dbo.orders
 (order_id, checkout_id, user_id, store_id, fulfillment_type, payment_method, payment_status, order_status,
  receiver_name, receiver_phone, shipping_address, pickup_code, ready_at, picked_up_at, total_amount, created_at, updated_at)
VALUES
 -- Checkout 1: 3 Order / 3 Store / 3 kieu fulfillment + payment khac nhau
 (1, 1, 5, 1, N'DELIVERY',     N'COD',          N'UNPAID', N'SHIPPING',
    N'Phạm Thu Hà', N'0911111111', N'12 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM', NULL, NULL, NULL,
    447000, DATEADD(HOUR, -30, SYSDATETIME()), DATEADD(HOUR, -26, SYSDATETIME())),
 (2, 1, 5, 2, N'STORE_PICKUP', N'ONLINE',       N'PAID',   N'READY_FOR_PICKUP',
    N'Phạm Thu Hà', N'0911111111', NULL, N'K7Q2M9', DATEADD(HOUR, -5, SYSDATETIME()), NULL,
    249000, DATEADD(HOUR, -30, SYSDATETIME()), DATEADD(HOUR, -5, SYSDATETIME())),
 (3, 1, 5, 3, N'STORE_PICKUP', N'PAY_AT_STORE', N'UNPAID', N'PREPARING',
    N'Phạm Thu Hà', N'0911111111', NULL, NULL, NULL, NULL,
    367000, DATEADD(HOUR, -30, SYSDATETIME()), DATEADD(HOUR, -28, SYSDATETIME())),
 -- Checkout 2: thanh toan ONLINE that bai -> huy + hoan ton
 (4, 2, 6, 1, N'DELIVERY',     N'ONLINE',       N'FAILED', N'CANCELLED',
    N'Đặng Quốc Khánh', N'0922222222', N'45 Quang Trung, P.10, Q. Gò Vấp, TP.HCM', NULL, NULL, NULL,
    189000, DATEADD(HOUR, -20, SYSDATETIME()), DATEADD(HOUR, -19, SYSDATETIME())),
 -- Checkout 3: Order lich su o Store INACTIVE (TC-18: lich su van xem duoc)
 (5, 3, 5, 5, N'STORE_PICKUP', N'PAY_AT_STORE', N'PAID',   N'COMPLETED',
    N'Phạm Thu Hà', N'0911111111', NULL, N'H3D8P1', DATEADD(DAY, -10, DATEADD(HOUR, 3, SYSDATETIME())), DATEADD(DAY, -9, SYSDATETIME()),
    129000, DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -9, SYSDATETIME()));
SET IDENTITY_INSERT dbo.orders OFF;

/* ---------- order_items: snapshot ten / gia ---------- */
INSERT INTO dbo.order_items (order_id, store_product_id, product_name, unit_price, quantity, subtotal) VALUES
 (1,  1, N'Cocoon Serum Bí Đao 30ml',                 129000, 2, 258000),
 (1,  6, N'BBIA Downy Cheek #06',                     189000, 1, 189000),
 (2, 12, N'Innisfree Green Tea Balancing Toner 200ml', 249000, 1, 249000),
 (3, 19, N'Innisfree Green Tea Cleansing Foam 120ml',  169000, 1, 169000),
 (3, 17, N'Cocoon Dầu Gội Bưởi 310ml',                  99000, 2, 198000),
 (4,  9, N'BBIA Downy Cheek #08',                     189000, 1, 189000),
 (5,  5, N'Cocoon Serum Bí Đao 30ml',                 129000, 1, 129000);

/* ---------- payments: gan Order, doc lap trong cung checkout ---------- */
INSERT INTO dbo.payments (order_id, method, amount, status, transaction_code, paid_at, created_at) VALUES
 (1, N'COD',          447000, N'PENDING', NULL,            NULL,                                 DATEADD(HOUR, -30, SYSDATETIME())),
 (2, N'ONLINE',       249000, N'SUCCESS', N'DEMO-TXN-0002', DATEADD(HOUR, -30, SYSDATETIME()),   DATEADD(HOUR, -30, SYSDATETIME())),
 (3, N'PAY_AT_STORE', 367000, N'PENDING', NULL,            NULL,                                 DATEADD(HOUR, -30, SYSDATETIME())),
 (4, N'ONLINE',       189000, N'FAILED',  N'DEMO-TXN-0004', NULL,                                DATEADD(HOUR, -20, SYSDATETIME())),
 (5, N'PAY_AT_STORE', 129000, N'SUCCESS', NULL,            DATEADD(DAY, -9, SYSDATETIME()),      DATEADD(DAY, -10, SYSDATETIME()));

/* ---------- inventory_movements ---------- */
INSERT INTO dbo.inventory_movements
 (store_product_id, type, quantity_change, quantity_before, quantity_after, reference_order_id, staff_id, note, created_at) VALUES
 ( 1, N'ORDER',        -2, 42, 40, 1, NULL, N'Checkout #1 - Order #1',                DATEADD(HOUR, -30, SYSDATETIME())),
 ( 6, N'ORDER',        -1, 13, 12, 1, NULL, N'Checkout #1 - Order #1',                DATEADD(HOUR, -30, SYSDATETIME())),
 (12, N'ORDER',        -1, 19, 18, 2, NULL, N'Checkout #1 - Order #2',                DATEADD(HOUR, -30, SYSDATETIME())),
 (19, N'ORDER',        -1, 15, 14, 3, NULL, N'Checkout #1 - Order #3',                DATEADD(HOUR, -30, SYSDATETIME())),
 (17, N'ORDER',        -2, 24, 22, 3, NULL, N'Checkout #1 - Order #3',                DATEADD(HOUR, -30, SYSDATETIME())),
 ( 9, N'ORDER',        -1,  8,  7, 4, NULL, N'Checkout #2 - Order #4',                DATEADD(HOUR, -20, SYSDATETIME())),
 ( 9, N'CANCEL_ORDER', +1,  7,  8, 4, NULL, N'Hủy do thanh toán ONLINE thất bại',     DATEADD(HOUR, -19, SYSDATETIME())),
 ( 5, N'ORDER',        -1, 11, 10, 5, NULL, N'Checkout #3 - Order #5',                DATEADD(DAY,  -10, SYSDATETIME())),
 (11, N'STOCK_ADJUST', +5, 20, 25, NULL, 2, N'Kiểm kê thực tế - nhập thêm 5',         DATEADD(HOUR, -48, SYSDATETIME()));

/* ---------- order_status_history ---------- */
INSERT INTO dbo.order_status_history (order_id, old_status, new_status, changed_by_user_id, note, changed_at) VALUES
 -- Order 1: DELIVERY + COD
 (1, NULL,              N'CONFIRMED',        NULL, N'Hệ thống tự xác nhận đơn hợp lệ', DATEADD(HOUR, -30, SYSDATETIME())),
 (1, N'CONFIRMED',      N'PREPARING',        2,    NULL,                               DATEADD(HOUR, -29, SYSDATETIME())),
 (1, N'PREPARING',      N'PACKED',           2,    NULL,                               DATEADD(HOUR, -28, SYSDATETIME())),
 (1, N'PACKED',         N'SHIPPING',         2,    NULL,                               DATEADD(HOUR, -26, SYSDATETIME())),
 -- Order 2: STORE_PICKUP + ONLINE
 (2, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn ONLINE',                  DATEADD(HOUR, -30, SYSDATETIME())),
 (2, N'PENDING_PAYMENT',N'CONFIRMED',        NULL, N'Payment SUCCESS',                 DATEADD(HOUR, -30, SYSDATETIME())),
 (2, N'CONFIRMED',      N'PREPARING',        3,    NULL,                               DATEADD(HOUR, -8,  SYSDATETIME())),
 (2, N'PREPARING',      N'READY_FOR_PICKUP', 3,    N'Sinh pickup_code K7Q2M9',         DATEADD(HOUR, -5,  SYSDATETIME())),
 -- Order 3: STORE_PICKUP + PAY_AT_STORE
 (3, NULL,              N'CONFIRMED',        NULL, N'Hệ thống tự xác nhận đơn hợp lệ', DATEADD(HOUR, -30, SYSDATETIME())),
 (3, N'CONFIRMED',      N'PREPARING',        4,    NULL,                               DATEADD(HOUR, -28, SYSDATETIME())),
 -- Order 4: ONLINE that bai -> huy
 (4, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn ONLINE',                  DATEADD(HOUR, -20, SYSDATETIME())),
 (4, N'PENDING_PAYMENT',N'CANCELLED',        NULL, N'Payment FAILED - hoàn tồn',       DATEADD(HOUR, -19, SYSDATETIME())),
 -- Order 5: Store INACTIVE, don lich su hoan tat
 (5, NULL,              N'CONFIRMED',        NULL, N'Hệ thống tự xác nhận đơn hợp lệ', DATEADD(DAY, -10, SYSDATETIME())),
 (5, N'CONFIRMED',      N'PREPARING',        1,    NULL,                               DATEADD(DAY, -10, DATEADD(HOUR, 2, SYSDATETIME()))),
 (5, N'PREPARING',      N'READY_FOR_PICKUP', 1,    N'Sinh pickup_code H3D8P1',         DATEADD(DAY, -10, DATEADD(HOUR, 3, SYSDATETIME()))),
 (5, N'READY_FOR_PICKUP',N'COMPLETED',       1,    N'Khách nhận và thanh toán tại Store', DATEADD(DAY, -9, SYSDATETIME()));

/* ---------- reviews (chi sau COMPLETED) ---------- */
INSERT INTO dbo.reviews (user_id, product_id, rating, comment, status) VALUES
 (5, 3, 5, N'Serum dịu nhẹ, dùng ổn.', N'ACTIVE');

COMMIT TRANSACTION;
GO

PRINT 'Seed data loaded.';
GO
