/* =============================================================================
   OneShop V3 - canonical V3 seed data
   Kien truc: Centralized Commerce - Distributed Inventory & Fulfillment
   Dac ta: docs/OneShop_V3_DacTaNghiepVu_MaTranChuyenDoiCSDL.md
   Bao gom:
   - 22 Brands & 8 Categories giu nguyen logo/anh Cloudinary da xac minh
   - 40 SKU san pham phong phu, gia ban online (products.price) thong nhat
   - 6 Stores (4 ACTIVE da khu vuc, 1 Pickup Only, 1 Delivery Only, 1 INACTIVE)
   - Ton kho da chi nhanh bao phu day du 8 kich ban nghiep vu (A -> H)
   - 8 Don hang mau voi da dang hinh thuc (Online 1-Store, Online 2-Store, Online 3-Store,
     Store Pickup, COD, Cancelled voi Simulated Refund, Pending Payment)
   - OrderFulfillments & OrderFulfillmentStatusHistory chi tiet
   - Reviews gan voi order_item_id va ReviewReplies tu nhan vien dung chi nhanh
   - InventoryMovements luu vet toan hoc chinh xac 100%
   - Carts & CartItems tham chieu truc tiep product_id
   This seed is intentionally a known fixture for the V3 regression suite. It
   has linked users, Store assignments, availability scenarios, Orders,
   Fulfillments, Payments, Reviews and Replies. It contains no external upload
   operation and is safe only on a database freshly made by 01_schema.sql.
   ========================================================================== */
USE [$(DatabaseName)];
GO
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

IF (SELECT COUNT(*) FROM dbo.roles) <> 0
    THROW 51011, 'Seed refuses a non-empty database. Create a new database for a fresh install.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

/* ============================================================================
   1. roles
   ========================================================================== */
SET IDENTITY_INSERT dbo.roles ON;
INSERT INTO dbo.roles (role_id, name, description) VALUES
 (1, N'CUSTOMER', N'Khách hàng mua sắm'),
 (2, N'STAFF',    N'Nhân viên chi nhánh cửa hàng'),
 (3, N'ADMIN',    N'Quản trị viên hệ thống toàn chuỗi');
SET IDENTITY_INSERT dbo.roles OFF;

/* ============================================================================
   2. users (Mat khau mac dinh cho tat ca tai khoan: OneShop@123)
   ========================================================================== */
SET IDENTITY_INSERT dbo.users ON;
INSERT INTO dbo.users (user_id, role_id, email, user_name, password_hash, full_name, phone, status) VALUES
 (1, 3, N'admin@oneshop.vn',         N'admin',          N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Quản Trị Viên OneShop',       N'0900000001', N'ACTIVE'),
 (2, 2, N'staff.thuduc@oneshop.vn',   N'staff_thuduc',   N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Nguyễn Văn An (Thủ Đức)',    N'0900000002', N'ACTIVE'),
 (3, 2, N'staff.govap@oneshop.vn',    N'staff_govap',    N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Trần Thị Bình (Gò Vấp)',     N'0900000003', N'ACTIVE'),
 (4, 2, N'staff.quan7@oneshop.vn',    N'staff_quan7',    N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Lê Minh Châu (Quận 7)',      N'0900000004', N'ACTIVE'),
 (5, 2, N'staff.quan10@oneshop.vn',   N'staff_quan10',   N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Võ Hoài Dung (Quận 10)',     N'0900000005', N'ACTIVE'),
 (6, 2, N'staff.hoankiem@oneshop.vn', N'staff_hoankiem', N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Hoàng Tuấn Kiệt (Hoàn Kiếm)', N'0900000006', N'ACTIVE'),
 (7, 1, N'khachhang1@example.com',   N'khachhang1',     N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Phạm Thu Hà',               N'0911111111', N'ACTIVE'),
 (8, 1, N'khachhang2@example.com',   N'khachhang2',     N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Đặng Quốc Khánh',           N'0922222222', N'ACTIVE'),
 (9, 1, N'khachhang3@example.com',   N'khachhang3',     N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Lê Hoàng Nam',              N'0933333333', N'ACTIVE'),
 (10,1, N'khachhang4@example.com',   N'khachhang4',     N'$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW', N'Trần Thị Mai',              N'0944444444', N'ACTIVE');
SET IDENTITY_INSERT dbo.users OFF;

/* ============================================================================
   3. customer_addresses
   ========================================================================== */
SET IDENTITY_INSERT dbo.customer_addresses ON;
INSERT INTO dbo.customer_addresses (address_id, user_id, receiver_name, receiver_phone, address_line, is_default) VALUES
 (1, 7, N'Phạm Thu Hà',     N'0911111111', N'12 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM', 1),
 (2, 7, N'Phạm Thu Hà',     N'0911111111', N'88 Nguyễn Đình Chiểu, P. Đa Kao, Quận 1, TP.HCM',     0),
 (3, 8, N'Đặng Quốc Khánh', N'0922222222', N'45 Quang Trung, P.10, Q. Gò Vấp, TP.HCM',            1),
 (4, 9, N'Lê Hoàng Nam',    N'0933333333', N'303 Nguyễn Thị Thập, P. Tân Phong, Quận 7, TP.HCM',   1),
 (5, 10,N'Trần Thị Mai',    N'0944444444', N'15 Hàng Khay, P. Tràng Tiền, Q. Hoàn Kiếm, Hà Nội',  1);
SET IDENTITY_INSERT dbo.customer_addresses OFF;

/* ============================================================================
   4. stores
   ========================================================================== */
SET IDENTITY_INSERT dbo.stores ON;
INSERT INTO dbo.stores (store_id, code, name, address, province_city, area, phone, opening_hours, delivery_enabled, pickup_enabled, status) VALUES
 (1, N'OS-THUDUC',  N'OneShop Thủ Đức',   N'101 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức',  N'TP. Hồ Chí Minh', N'Thủ Đức',   N'02811110001', N'08:00 - 22:00 hằng ngày', 1, 1, N'ACTIVE'),
 (2, N'OS-GOVAP',   N'OneShop Gò Vấp',    N'202 Quang Trung, P.10, Q. Gò Vấp',              N'TP. Hồ Chí Minh', N'Gò Vấp',    N'02811110002', N'08:00 - 22:00 hằng ngày', 1, 1, N'ACTIVE'),
 (3, N'OS-QUAN7',   N'OneShop Quận 7',    N'303 Nguyễn Thị Thập, P. Tân Phong, Q.7',        N'TP. Hồ Chí Minh', N'Quận 7',    N'02811110003', N'09:00 - 22:00 hằng ngày', 1, 1, N'ACTIVE'),
 (4, N'OS-QUAN10',  N'OneShop Quận 10',   N'404 Ba Tháng Hai, P.12, Q.10',                  N'TP. Hồ Chí Minh', N'Quận 10',   N'02811110004', N'09:00 - 21:30 hằng ngày', 0, 1, N'ACTIVE'),  -- Pickup only (Kich ban H)
 (5, N'OS-HAICHAU', N'OneShop Hải Châu',  N'505 Nguyễn Văn Linh, Q. Hải Châu',              N'Đà Nẵng',         N'Hải Châu',  N'02361110005', N'08:30 - 21:30 hằng ngày', 1, 1, N'INACTIVE'),-- Tam ngung (Kich ban G)
 (6, N'OS-HOANKIEM',N'OneShop Hoàn Kiếm', N'606 Tràng Tiền, Q. Hoàn Kiếm',                  N'Hà Nội',          N'Hoàn Kiếm', N'02411110006', N'08:30 - 22:00 hằng ngày', 1, 0, N'ACTIVE');  -- Delivery only (Kich ban H)
SET IDENTITY_INSERT dbo.stores OFF;

/* ============================================================================
   5. staff_store_assignments
   ========================================================================== */
SET IDENTITY_INSERT dbo.staff_store_assignments ON;
INSERT INTO dbo.staff_store_assignments (assignment_id, user_id, store_id, status) VALUES
 (1, 2, 1, N'ACTIVE'),
 (2, 3, 2, N'ACTIVE'),
 (3, 4, 3, N'ACTIVE'),
 (4, 5, 4, N'ACTIVE'),
 (5, 6, 6, N'ACTIVE');
SET IDENTITY_INSERT dbo.staff_store_assignments OFF;

/* ============================================================================
   6. categories (Anh da xac minh 100% tren Cloudinary CDN)
   ========================================================================== */
SET IDENTITY_INSERT dbo.categories ON;
INSERT INTO dbo.categories (category_id, name, description, image_url, image_public_id, status) VALUES
 (1, N'Chăm sóc da',         N'Serum, toner, kem dưỡng, làm sạch da',            N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803746/oneshop/seed/categories/cham-soc-da.webp',        N'oneshop/seed/categories/cham-soc-da',        N'ACTIVE'),
 (2, N'Trang điểm',          N'Son, phấn, má hồng, trang điểm mắt môi',          N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803748/oneshop/seed/categories/trang-diem.webp',         N'oneshop/seed/categories/trang-diem',         N'ACTIVE'),
 (3, N'Chăm sóc tóc',        N'Dầu gội, dầu xả, dưỡng tóc phục hồi',             N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803749/oneshop/seed/categories/cham-soc-toc.webp',       N'oneshop/seed/categories/cham-soc-toc',       N'ACTIVE'),
 (4, N'Chăm sóc cơ thể',     N'Sữa tắm, dưỡng thể, tẩy tế bào chết body',        N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803739/oneshop/seed/categories/cham-soc-co-the.jpg',     N'oneshop/seed/categories/cham-soc-co-the',    N'ACTIVE'),
 (5, N'Chống nắng',          N'Kem chống nắng phổ rộng, chống tia UV',           N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803741/oneshop/seed/categories/chong-nang.jpg',          N'oneshop/seed/categories/chong-nang',         N'ACTIVE'),
 (6, N'Nước hoa',            N'Nước hoa nữ, xịt thơm toàn thân body mist',       N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803742/oneshop/seed/categories/nuoc-hoa.jpg',            N'oneshop/seed/categories/nuoc-hoa',           N'ACTIVE'),
 (7, N'Mặt nạ',              N'Mặt nạ giấy, mặt nạ đất sét, cấp ẩm sâu',        N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803744/oneshop/seed/categories/mat-na.jpg',              N'oneshop/seed/categories/mat-na',             N'ACTIVE'),
 (8, N'Tẩy trang & làm sạch',N'Nước tẩy trang, dầu tẩy trang, bông tẩy trang',   N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803745/oneshop/seed/categories/tay-trang-va-lam-sach.jpg',N'oneshop/seed/categories/tay-trang-va-lam-sach',N'ACTIVE');
SET IDENTITY_INSERT dbo.categories OFF;

/* ============================================================================
   7. brands (Logo da xac minh 100% tren Cloudinary CDN)
   ========================================================================== */
SET IDENTITY_INSERT dbo.brands ON;
INSERT INTO dbo.brands (brand_id, name, description, logo_url, logo_public_id, status) VALUES
 (1,  N'BBIA',               N'Mỹ phẩm trang điểm Hàn Quốc',                  N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803762/oneshop/seed/brands/bbia.png',               N'oneshop/seed/brands/bbia',               N'ACTIVE'),
 (2,  N'Cocoon',             N'Mỹ phẩm thuần chay 100% Việt Nam',             N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803763/oneshop/seed/brands/cocoon.png',             N'oneshop/seed/brands/cocoon',             N'ACTIVE'),
 (3,  N'Innisfree',          N'Mỹ phẩm thiên nhiên đảo Jeju Hàn Quốc',        N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803764/oneshop/seed/brands/innisfree.png',          N'oneshop/seed/brands/innisfree',          N'ACTIVE'),
 (4,  N'L''Oréal Paris',     N'Thương hiệu mỹ phẩm cao cấp Pháp',             N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803750/oneshop/seed/brands/loreal-paris.png',      N'oneshop/seed/brands/loreal-paris',      N'ACTIVE'),
 (5,  N'Maybelline New York',N'Mỹ phẩm trang điểm đại chúng New York',        N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803751/oneshop/seed/brands/maybelline-new-york.png', N'oneshop/seed/brands/maybelline-new-york', N'ACTIVE'),
 (6,  N'Garnier',            N'Chăm sóc da và tóc chiết xuất thiên nhiên',    N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803751/oneshop/seed/brands/garnier.png',            N'oneshop/seed/brands/garnier',            N'ACTIVE'),
 (7,  N'Neutrogena',         N'Dược mỹ phẩm chăm sóc da khuyên dùng',         N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803752/oneshop/seed/brands/neutrogena.png',         N'oneshop/seed/brands/neutrogena',         N'ACTIVE'),
 (8,  N'La Roche-Posay',     N'Dược mỹ phẩm hàng đầu cho da nhạy cảm Pháp',   N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803753/oneshop/seed/brands/la-roche-posay.png',     N'oneshop/seed/brands/la-roche-posay',     N'ACTIVE'),
 (9,  N'Cetaphil',           N'Làm sạch và dưỡng ẩm dịu lành làn da',         N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803753/oneshop/seed/brands/cetaphil.png',           N'oneshop/seed/brands/cetaphil',           N'ACTIVE'),
 (10, N'CeraVe',             N'Dưỡng ẩm phục hồi với 3 Ceramide thiết yếu',   N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803754/oneshop/seed/brands/cerave.png',             N'oneshop/seed/brands/cerave',             N'ACTIVE'),
 (11, N'Senka',              N'Chăm sóc da làm sạch bọt tơ tằm Nhật Bản',     N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803754/oneshop/seed/brands/senka.png',              N'oneshop/seed/brands/senka',              N'ACTIVE'),
 (12, N'Dear Klairs',        N'Dưỡng ẩm dịu nhẹ cho da nhạy cảm Hàn Quốc',    N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803755/oneshop/seed/brands/dear-klairs.png',        N'oneshop/seed/brands/dear-klairs',        N'ACTIVE'),
 (13, N'Some By Mi',         N'Giải pháp 30 ngày cho làn da sạch mụn',        N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803755/oneshop/seed/brands/some-by-mi.png',        N'oneshop/seed/brands/some-by-mi',        N'ACTIVE'),
 (14, N'SKIN1004',           N'Chiết xuất rau má vùng Madagascar tinh khiết', N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803756/oneshop/seed/brands/skin1004.png',           N'oneshop/seed/brands/skin1004',           N'ACTIVE'),
 (15, N'Anessa',             N'Chống nắng số 1 Nhật Bản 21 năm liên tiếp',    N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803757/oneshop/seed/brands/anessa.png',             N'oneshop/seed/brands/anessa',             N'ACTIVE'),
 (16, N'Nivea',              N'Chăm sóc cơ thể và làn da gia đình',           N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803758/oneshop/seed/brands/nivea.png',              N'oneshop/seed/brands/nivea',              N'ACTIVE'),
 (17, N'Dove',               N'Dưỡng ẩm sâu cho da và mái tóc',               N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803758/oneshop/seed/brands/dove.png',               N'oneshop/seed/brands/dove',               N'ACTIVE'),
 (18, N'Pantene',            N'Phục hồi hư tổn mái tóc chắc khỏe Pro-V',      N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803759/oneshop/seed/brands/pantene.png',            N'oneshop/seed/brands/pantene',            N'ACTIVE'),
 (19, N'Romand',             N'Son tint bóng và trang điểm xu hướng trẻ',     N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803759/oneshop/seed/brands/romand.png',             N'oneshop/seed/brands/romand',             N'ACTIVE'),
 (20, N'3CE',                N'Thời trang mỹ phẩm phong cách Stylenanda',      N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803760/oneshop/seed/brands/3ce.png',                N'oneshop/seed/brands/3ce',                N'ACTIVE'),
 (21, N'Bath & Body Works',  N'Xịt thơm toàn thân và dưỡng thể hương nước hoa',N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803761/oneshop/seed/brands/bath-and-body-works.png',N'oneshop/seed/brands/bath-and-body-works',N'ACTIVE'),
 (22, N'Victoria''s Secret', N'Nước hoa và xịt thơm quyến rũ gợi cảm',       N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803761/oneshop/seed/brands/victorias-secret.png',   N'oneshop/seed/brands/victorias-secret',   N'ACTIVE');
SET IDENTITY_INSERT dbo.brands OFF;

/* ============================================================================
   8. products (40 SKU - gia ban online products.price thong nhat toan chuoi)
   ========================================================================== */
SET IDENTITY_INSERT dbo.products ON;
INSERT INTO dbo.products (product_id, category_id, brand_id, sku, name, description, price, original_price, status) VALUES
 (1,  2, 1,  N'BBIA-CHEEK-06',       N'Má hồng dạng kem BBIA Downy Cheek #06',                    N'Má hồng kem mịn màng, tông màu #06 tự nhiên tươi sáng.',          189000, 210000, N'ACTIVE'),
 (2,  2, 1,  N'BBIA-CHEEK-08',       N'Má hồng dạng kem BBIA Downy Cheek #08',                    N'Má hồng dạng kem tông #08 trầm ấm rạng rỡ.',                       189000, 210000, N'ACTIVE'),
 (3,  1, 2,  N'COCOON-SERUM-30',     N'Serum Bí Đao Cocoon Giảm Dầu Mụn 30ml',                   N'Serum bí đao thuần chay kháng khuẩn, hỗ trợ phục hồi da mụn.',     129000, 145000, N'ACTIVE'),
 (4,  1, 3,  N'INNI-TONER-200',      N'Nước hoa hồng trà xanh Innisfree Green Tea 200ml',         N'Toner cân bằng độ ẩm chiết xuất mầm trà xanh tươi Jeju.',          249000, 280000, N'ACTIVE'),
 (5,  3, 2,  N'COCOON-SHAM-310',     N'Dầu gội bưởi giảm rụng tóc Cocoon 310ml',                 N'Dầu gội tinh dầu vỏ bưởi tự nhiên, ngăn rụng tóc hiệu quả.',        95000, 110000, N'ACTIVE'),
 (6,  1, 3,  N'INNI-CLEANS-120',     N'Sữa rửa mặt trà xanh Innisfree Green Tea Foam 120ml',      N'Sữa rửa mặt tạo bọt mịn làm sạch sâu bã nhờn.',                    165000, 185000, N'ACTIVE'),
 (7,  1, 4,  N'LOREAL-SERUM-30',     N'Serum sáng mờ thâm L''Oréal Glycolic-Bright 30ml',         N'Serum chứa Glycolic Acid 1% dưỡng sáng mịn da đều màu.',           329000, 399000, N'ACTIVE'),
 (8,  2, 5,  N'MAYB-MASCARA-10',     N'Mascara uốn mi cong dày Maybelline Hyper Curl',           N'Mascara chống trôi nước đến 18 giờ cho hàng mi cong vút.',         139000, 169000, N'ACTIVE'),
 (9,  8, 6,  N'GARNIER-MICELLAR-400',N'Nước tẩy trang dịu nhẹ Garnier Micellar Cleansing Water 400ml', N'Nước tẩy trang công nghệ Micelles hút sạch bụi mịn và dầu thừa.',159000, 189000, N'ACTIVE'),
 (10, 1, 7,  N'NEUTRO-HYDRO-50',     N'Gel dưỡng ẩm Neutrogena Hydro Boost Water Gel 50g',        N'Kem dưỡng ẩm cấp nước Hyaluronic Acid siêu nhẹ thoáng da.',        299000, 350000, N'ACTIVE'),
 (11, 1, 8,  N'LRP-EFFACLAR-40',     N'Kem giảm mụn ngừa thâm La Roche-Posay Effaclar Duo+ 40ml',N'Kem dưỡng giảm mụn viêm và ngăn thâm sẹo sau mụn.',               395000, 450000, N'ACTIVE'),
 (12, 5, 8,  N'LRP-ANTHELIOS-50',    N'Kem chống nắng kiềm dầu La Roche-Posay Anthelios 50ml',    N'Chống nắng quang phổ rộng bảo vệ da tối ưu khỏi tia UVA/UVB.',     445000, 495000, N'ACTIVE'),
 (13, 1, 9,  N'CETAPHIL-CLEANS-500', N'Sữa rửa mặt dịu lành Cetaphil Gentle Cleanser 500ml',    N'Công thức làm sạch khoa học bảo vệ màng ẩm tự nhiên của da.',      315000, 360000, N'ACTIVE'),
 (14, 1, 10, N'CERAVE-FOAM-236',     N'Sữa rửa mặt cho da dầu CeraVe Foaming Cleanser 236ml',     N'Sữa rửa mặt tạo bọt chứa 3 Ceramide và Niacinamide.',              279000, 320000, N'ACTIVE'),
 (15, 1, 10, N'CERAVE-MOIST-52',     N'Sữa dưỡng ẩm ban đêm CeraVe Facial Moisturising Lotion 52ml', N'Sữa dưỡng ẩm thẩm thấu nhanh phục hồi hàng rào da.',         349000, 390000, N'ACTIVE'),
 (16, 1, 11, N'SENKA-WHIP-120',      N'Sữa rửa mặt bọt tơ tằm Senka Perfect Whip 120g',           N'Lớp bọt siêu dày làm sạch sâu bụi bẩn lỗ chân lông.',               89000, 105000, N'ACTIVE'),
 (17, 1, 12, N'KLAIRS-SUPPLE-180',   N'Nước hoa hồng không mùi Dear Klairs Supple Toner 180ml',   N'Toner cấp ẩm tức thì, làm dịu da nhạy cảm dễ kích ứng.',           285000, 330000, N'ACTIVE'),
 (18, 1, 12, N'KLAIRS-VITC-35',      N'Tinh chất Vitamin C dưỡng sáng Dear Klairs Freshly Drop 35ml', N'Vitamin C tươi nồng độ 5% mờ thâm sáng da an toàn.',          335000, 385000, N'ACTIVE'),
 (19, 1, 13, N'SOMEBYMI-AHA-150',    N'Nước cân bằng giảm mụn Some By Mi AHA-BHA-PHA 150ml',      N'Toner chứa tràm trà giúp làm sạch da và tẩy tế bào chết nhẹ.',     245000, 290000, N'ACTIVE'),
 (20, 1, 13, N'SOMEBYMI-SERUM-50',   N'Tinh chất trị mụn Some By Mi Miracle Serum 50ml',          N'Serum 2 lớp dầu và nước kháng khuẩn làm dịu nốt mụn.',             295000, 350000, N'ACTIVE'),
 (21, 1, 14, N'SKIN1004-AMPOULE-100',N'Tinh chất rau má phục hồi SKIN1004 Centella Ampoule 100ml',N'Chiết xuất 100% rau má Madagascar tinh khiết làm dịu da.',        349000, 420000, N'ACTIVE'),
 (22, 5, 14, N'SKIN1004-SUN-50',     N'Kem chống nắng cấp ẩm SKIN1004 Hyalu-Cica Sun Serum 50ml', N'Chống nắng dạng serum mỏng nhẹ như nước, cấp ẩm cả ngày.',       289000, 330000, N'ACTIVE'),
 (23, 5, 15, N'ANESSA-MILK-60',      N'Sữa chống nắng kiềm dầu Anessa Perfect UV Milk 60ml',      N'Sữa chống nắng chống trôi nước mạnh mẽ với công nghệ Auto Booster.',495000, 560000, N'ACTIVE'),
 (24, 5, 15, N'ANESSA-GEL-90',       N'Gel chống nắng dưỡng ẩm Anessa Perfect UV Gel 90g',        N'Dạng gel tươi mát dưỡng ẩm chuyên sâu suốt ngày dài.',             455000, 510000, N'ACTIVE'),
 (25, 4, 16, N'NIVEA-BODY-350',      N'Sữa dưỡng thể sáng da Nivea Extra White Radiant 350ml',    N'Dưỡng thể chứa vitamin C tinh khiết giúp da sáng mịn rạng ngời.',  139000, 165000, N'ACTIVE'),
 (26, 4, 17, N'DOVE-BODYWASH-500',   N'Sữa tắm dưỡng ẩm chuyên sâu Dove Deep Moisture 500g',      N'Công thức dưỡng ẩm tức thì cho làn da mềm mịn sau một lần tắm.',   125000, 149000, N'ACTIVE'),
 (27, 3, 18, N'PANTENE-SHAMP-650',   N'Dầu gội phục hồi hư tổn Pantene Pro-V 650ml',              N'Bổ sung dưỡng chất Pro-V nuôi dưỡng tóc từ sâu bên trong.',        159000, 189000, N'ACTIVE'),
 (28, 3, 18, N'PANTENE-COND-300',    N'Kem xả 3 phút diệu kỳ Pantene 3 Minute Miracle 300ml',     N'Phục hồi hư tổn tóc chỉ trong 3 phút mỗi ngày.',                   119000, 139000, N'ACTIVE'),
 (29, 2, 19, N'ROMAND-JUCY-06',      N'Son tint bóng Romand Juicy Lasting Tint #06 Figfig',       N'Sắc hồng quả sung ngọt ngào, tạo hiệu ứng môi căng mọng.',         159000, 185000, N'ACTIVE'),
 (30, 2, 19, N'ROMAND-JUCY-07',      N'Son tint bóng Romand Juicy Lasting Tint #07 Jujube',       N'Màu đỏ táo tàu thanh lịch, tôn màu da châu Á.',                    159000, 185000, N'ACTIVE'),
 (31, 2, 20, N'3CE-VELVET-DAFF',     N'Son kem lì 3CE Velvet Lip Tint #Daffodil',                 N'Màu đỏ đất quyến rũ, chất son mịn như nhung không khô môi.',      285000, 330000, N'ACTIVE'),
 (32, 2, 20, N'3CE-VELVET-TAUPE',    N'Son kem lì 3CE Velvet Lip Tint #Taupe',                    N'Màu đỏ nâu cổ điển sang trọng, thời thượng.',                      285000, 330000, N'ACTIVE'),
 (33, 6, 21, N'BBW-MIST-236',        N'Xịt thơm toàn thân Bath & Body Works Japanese Cherry 236ml', N'Hương hoa anh đào Nhật Bản ngọt ngào, lưu hương bền lâu.',     295000, 350000, N'ACTIVE'),
 (34, 4, 21, N'BBW-LOTION-236',      N'Sữa dưỡng thể Bath & Body Works Gingham 236ml',            N'Dưỡng ẩm bơ hạt mỡ hương hoa lan Nam Phi tươi mát.',               285000, 330000, N'ACTIVE'),
 (35, 6, 22, N'VS-MIST-PURE-250',    N'Xịt thơm Victoria''s Secret Pure Seduction 250ml',         N'Hương mận đỏ và hoa lan Nam Phi mê đắm quyến rũ.',                 345000, 410000, N'ACTIVE'),
 (36, 6, 22, N'VS-MIST-VELVET-250',  N'Xịt thơm Victoria''s Secret Velvet Petals 250ml',          N'Hương hạnh nhân ngào ngạt kết hợp hoa cỏ tinh tế.',                345000, 410000, N'ACTIVE'),
 (37, 7, 2,  N'COCOON-MASK-100',     N'Mặt nạ hoa cúc làm dịu da Cocoon 100ml',                   N'Cánh hoa cúc tươi nguyên bản kháng viêm dưỡng ẩm dịu da.',         115000, 135000, N'ACTIVE'),
 (38, 7, 3,  N'INNI-MASK-TEA-20',    N'Mặt nạ giấy dưỡng ẩm Innisfree My Real Squeeze Trà Xanh',  N'Mặt nạ giấy chiết xuất trà xanh cấp nước thanh lọc da.',            25000,  30000, N'ACTIVE'),
 (39, 8, 4,  N'LOREAL-MICELLAR-400', N'Nước tẩy trang sạch sâu L''Oréal Micellar 3-in-1 400ml',    N'Làm sạch sâu cặn trang điểm và bụi mịn không gây khô rát.',        189000, 220000, N'ACTIVE'),
 (40, 2, 2,  N'COCOON-LIP-05',       N'Son dưỡng dừa Bến Tre Cocoon 5g (ngừng kinh doanh)',       N'SKU đã ngừng kinh doanh, lưu trữ nhằm mục đích bảo toàn lịch sử.',  45000,  55000, N'INACTIVE');
SET IDENTITY_INSERT dbo.products OFF;

/* ============================================================================
   9. product_images (URL placeholder an toan, khong dung lai public_id da xoa o Phase 1)
   ========================================================================== */
SET IDENTITY_INSERT dbo.product_images ON;
INSERT INTO dbo.product_images (image_id, product_id, image_url, public_id, is_primary, sort_order) VALUES
 (1,  1,  N'/images/products/bbia-cheek-06.webp',       N'oneshop/v3/products/bbia-cheek-06',       1, 0),
 (2,  2,  N'/images/products/bbia-cheek-08.webp',       N'oneshop/v3/products/bbia-cheek-08',       1, 0),
 (3,  3,  N'/images/products/cocoon-serum-30.webp',     N'oneshop/v3/products/cocoon-serum-30',     1, 0),
 (4,  4,  N'/images/products/inni-toner-200.webp',      N'oneshop/v3/products/inni-toner-200',      1, 0),
 (5,  5,  N'/images/products/cocoon-sham-310.webp',     N'oneshop/v3/products/cocoon-sham-310',     1, 0),
 (6,  6,  N'/images/products/inni-cleans-120.webp',     N'oneshop/v3/products/inni-cleans-120',     1, 0),
 (7,  7,  N'/images/products/loreal-serum-30.webp',     N'oneshop/v3/products/loreal-serum-30',     1, 0),
 (8,  8,  N'/images/products/mayb-mascara-10.webp',     N'oneshop/v3/products/mayb-mascara-10',     1, 0),
 (9,  9,  N'/images/products/garnier-micellar-400.webp',N'oneshop/v3/products/garnier-micellar-400',1, 0),
 (10, 10, N'/images/products/neutro-hydro-50.webp',     N'oneshop/v3/products/neutro-hydro-50',     1, 0),
 (11, 11, N'/images/products/lrp-effaclar-40.webp',     N'oneshop/v3/products/lrp-effaclar-40',     1, 0),
 (12, 12, N'/images/products/lrp-anthelios-50.webp',    N'oneshop/v3/products/lrp-anthelios-50',    1, 0),
 (13, 13, N'/images/products/cetaphil-cleans-500.webp', N'oneshop/v3/products/cetaphil-cleans-500', 1, 0),
 (14, 14, N'/images/products/cerave-foam-236.webp',     N'oneshop/v3/products/cerave-foam-236',     1, 0),
 (15, 15, N'/images/products/cerave-moist-52.webp',     N'oneshop/v3/products/cerave-moist-52',     1, 0),
 (16, 16, N'/images/products/senka-whip-120.webp',      N'oneshop/v3/products/senka-whip-120',      1, 0),
 (17, 17, N'/images/products/klairs-supple-180.webp',   N'oneshop/v3/products/klairs-supple-180',   1, 0),
 (18, 18, N'/images/products/klairs-vitc-35.webp',      N'oneshop/v3/products/klairs-vitc-35',      1, 0),
 (19, 19, N'/images/products/somebymi-aha-150.webp',    N'oneshop/v3/products/somebymi-aha-150',    1, 0),
 (20, 20, N'/images/products/somebymi-serum-50.webp',   N'oneshop/v3/products/somebymi-serum-50',   1, 0),
 (21, 21, N'/images/products/skin1004-ampoule-100.webp',N'oneshop/v3/products/skin1004-ampoule-100',1, 0),
 (22, 22, N'/images/products/skin1004-sun-50.webp',     N'oneshop/v3/products/skin1004-sun-50',     1, 0),
 (23, 23, N'/images/products/anessa-milk-60.webp',      N'oneshop/v3/products/anessa-milk-60',      1, 0),
 (24, 24, N'/images/products/anessa-gel-90.webp',       N'oneshop/v3/products/anessa-gel-90',       1, 0),
 (25, 25, N'/images/products/nivea-body-350.webp',      N'oneshop/v3/products/nivea-body-350',      1, 0),
 (26, 26, N'/images/products/dove-bodywash-500.webp',   N'oneshop/v3/products/dove-bodywash-500',   1, 0),
 (27, 27, N'/images/products/pantene-shamp-650.webp',   N'oneshop/v3/products/pantene-shamp-650',   1, 0),
 (28, 28, N'/images/products/pantene-cond-300.webp',    N'oneshop/v3/products/pantene-cond-300',    1, 0),
 (29, 29, N'/images/products/romand-jucy-06.webp',      N'oneshop/v3/products/romand-jucy-06',      1, 0),
 (30, 30, N'/images/products/romand-jucy-07.webp',      N'oneshop/v3/products/romand-jucy-07',      1, 0),
 (31, 31, N'/images/products/3ce-velvet-daff.webp',     N'oneshop/v3/products/3ce-velvet-daff',     1, 0),
 (32, 32, N'/images/products/3ce-velvet-taupe.webp',    N'oneshop/v3/products/3ce-velvet-taupe',    1, 0),
 (33, 33, N'/images/products/bbw-mist-236.webp',        N'oneshop/v3/products/bbw-mist-236',        1, 0),
 (34, 34, N'/images/products/bbw-lotion-236.webp',      N'oneshop/v3/products/bbw-lotion-236',      1, 0),
 (35, 35, N'/images/products/vs-mist-pure-250.webp',    N'oneshop/v3/products/vs-mist-pure-250',    1, 0),
 (36, 36, N'/images/products/vs-mist-velvet-250.webp',  N'oneshop/v3/products/vs-mist-velvet-250',  1, 0),
 (37, 37, N'/images/products/cocoon-mask-100.webp',     N'oneshop/v3/products/cocoon-mask-100',     1, 0),
 (38, 38, N'/images/products/inni-mask-tea-20.webp',    N'oneshop/v3/products/inni-mask-tea-20',    1, 0),
 (39, 39, N'/images/products/loreal-micellar-400.webp', N'oneshop/v3/products/loreal-micellar-400', 1, 0),
 (40, 40, N'/images/products/cocoon-lip-05.webp',       N'oneshop/v3/products/cocoon-lip-05',       1, 0);
SET IDENTITY_INSERT dbo.product_images OFF;

/* ============================================================================
   10. store_products (Ton kho hien tai tai cac Store - phan anh Kich ban A -> H)
   ========================================================================== */
SET IDENTITY_INSERT dbo.store_products ON;
INSERT INTO dbo.store_products (store_product_id, store_id, product_id, price, quantity, reserved_quantity, status) VALUES
 -- Don 1 (Order 1: Cocoon Serum + BBIA Cheek 06 @ Store 1 Thu Duc -> Kich ban A: 1 Store du hang)
 (1,  1, 3,  129000, 40, 0, N'ACTIVE'), -- ban dau 42, ban 2 -> con 40
 (2,  1, 1,  189000, 12, 0, N'ACTIVE'), -- ban dau 13, ban 1 -> con 12

 -- Don 2 (Order 2: L'Oreal Serum @ Store 1 + Maybelline Mascara @ Store 2 -> Kich ban B: 2 Store phoi hop)
 (3,  1, 7,  329000, 20, 0, N'ACTIVE'), -- ban dau 21, ban 1 -> con 20
 (4,  2, 8,  139000, 30, 0, N'ACTIVE'), -- ban dau 32, ban 2 -> con 30

 -- Don 3 (Order 3: LRP Duo+ @ Store 1 + SKIN1004 Ampoule @ Store 2 + Anessa Milk @ Store 3 -> Kich ban C: 3 Store phoi hop)
 (5,  1, 11, 395000, 15, 0, N'ACTIVE'), -- ban dau 16, ban 1 -> con 15
 (6,  2, 21, 349000, 18, 0, N'ACTIVE'), -- ban dau 19, ban 1 -> con 18
 (7,  3, 23, 495000, 25, 0, N'ACTIVE'), -- ban dau 26, ban 1 -> con 25

 -- Don 4 (Order 4: Innisfree Toner @ Store 1 -> Pickup Thu Duc)
 (8,  1, 4,  249000, 22, 0, N'ACTIVE'), -- ban dau 23, ban 1 -> con 22

 -- Don 5 (Order 5: Cocoon Dau Goi @ Store 2 -> Pickup Go Vap completed)
 (9,  2, 5,   95000, 35, 0, N'ACTIVE'), -- ban dau 37, ban 2 -> con 35

 -- Don 6 (Order 6: Senka Whip @ Store 1 -> COD Thu Duc)
 (10, 1, 16,  89000, 53, 0, N'ACTIVE'), -- ban dau 55, ban 2 -> con 53

 -- Don 7 (Order 7: Garnier Micellar @ Store 1 + Neutrogena @ Store 2 -> Cancelled & Restored)
 (11, 1, 9,  159000, 25, 0, N'ACTIVE'), -- ban dau 25, tru 1 roi hoan 1 -> con 25
 (12, 2, 10, 299000, 18, 0, N'ACTIVE'), -- ban dau 18, tru 1 roi hoan 1 -> con 18

 -- Don 8 (Order 8: CeraVe Foaming @ Store 1 -> Pending payment)
 (13, 1, 14, 279000, 14, 0, N'ACTIVE'), -- ban dau 15, ban 1 -> con 14

 -- Kich ban D: Chi nhanh het hang mot so SKU (quantity = 0)
 (14, 1, 12, 445000,  0, 0, N'ACTIVE'), -- Thu Duc het LRP Anthelios (quantity = 0)
 (15, 2, 12, 445000, 25, 0, N'ACTIVE'), -- Go Vap con hang LRP Anthelios
 (16, 1, 13, 315000, 30, 0, N'ACTIVE'), -- Thu Duc con Cetaphil 500ml
 (17, 2, 13, 315000,  0, 0, N'ACTIVE'), -- Go Vap het Cetaphil 500ml (quantity = 0)

 -- Kich ban E: SKU co ton kho doi dao o tat ca chi nhanh (Senka Whip p16 tai Store 2, Store 3, Store 4, Store 6)
 (18, 2, 16,  89000, 48, 0, N'ACTIVE'),
 (19, 3, 16,  89000, 50, 0, N'ACTIVE'),
 (20, 4, 16,  89000, 40, 0, N'ACTIVE'), -- Store 4 (Pickup only - Kich ban H)
 (21, 6, 16,  89000, 60, 0, N'ACTIVE'), -- Store 6 (Delivery only - Kich ban H)

 -- Kich ban F: San pham chi co o dung 1 chi nhanh duy nhat (BBW Mist p33 chi co tai Store 3 Quan 7)
 (22, 3, 33, 295000, 15, 0, N'ACTIVE'),

 -- Kich ban G: Chi nhanh tam ngung hoat dong (Store 5 Hai Chau - INACTIVE)
 (23, 5, 3,  129000, 20, 0, N'INACTIVE'),
 (24, 5, 16,  89000, 15, 0, N'INACTIVE'),

 -- Cac SKU phu khac dam bao catalog phong phu
 (25, 1, 17, 285000, 20, 0, N'ACTIVE'),
 (26, 1, 18, 335000, 15, 0, N'ACTIVE'),
 (27, 2, 22, 289000, 25, 0, N'ACTIVE'),
 (28, 2, 29, 159000, 30, 0, N'ACTIVE'),
 (29, 3, 30, 159000, 22, 0, N'ACTIVE'),
 (30, 1, 31, 285000, 18, 0, N'ACTIVE'),
 (31, 2, 32, 285000, 16, 0, N'ACTIVE'),
 (32, 3, 35, 345000, 12, 0, N'ACTIVE'),
 (33, 1, 37, 115000, 25, 0, N'ACTIVE'),
 (34, 1, 38,  25000, 90, 0, N'ACTIVE'),
 (35, 2, 39, 189000, 35, 0, N'ACTIVE'),
 (36, 1, 40,  45000,  0, 0, N'INACTIVE'); -- SKU ngung ban
SET IDENTITY_INSERT dbo.store_products OFF;

/* ============================================================================
   11. carts & 12. cart_items (V3: gio hang thong nhat tham chieu product_id)
   ========================================================================== */
SET IDENTITY_INSERT dbo.carts ON;
INSERT INTO dbo.carts (cart_id, user_id) VALUES
 (1, 7), -- Gio hang cua Ha
 (2, 8); -- Gio hang cua Khanh
SET IDENTITY_INSERT dbo.carts OFF;

SET IDENTITY_INSERT dbo.cart_items ON;
INSERT INTO dbo.cart_items (cart_item_id, cart_id, product_id, store_product_id, quantity) VALUES
 (1, 1, 12, NULL, 1), -- Ha them LRP Anthelios (product_id = 12)
 (2, 1, 17, NULL, 2), -- Ha them Klairs Supple Toner (product_id = 17)
 (3, 2, 22, NULL, 1), -- Khanh them SKIN1004 Sun Serum (product_id = 22)
 (4, 2, 29, NULL, 1); -- Khanh them Romand Figfig (product_id = 29)
SET IDENTITY_INSERT dbo.cart_items OFF;

/* ============================================================================
   13. checkout_sessions
   ========================================================================== */
SET IDENTITY_INSERT dbo.checkout_sessions ON;
INSERT INTO dbo.checkout_sessions (checkout_id, user_id, total_amount, status, created_at) VALUES
 (1, 7,   447000, N'COMPLETED', DATEADD(DAY, -14, SYSDATETIME())),
 (2, 7,   607000, N'COMPLETED', DATEADD(DAY, -10, SYSDATETIME())),
 (3, 8,  1239000, N'COMPLETED', DATEADD(DAY,  -7, SYSDATETIME())),
 (4, 8,   249000, N'COMPLETED', DATEADD(DAY,  -5, SYSDATETIME())),
 (5, 9,   190000, N'COMPLETED', DATEADD(DAY,  -4, SYSDATETIME())),
 (6, 9,   178000, N'COMPLETED', DATEADD(DAY,  -2, SYSDATETIME())),
 (7, 10,  458000, N'CANCELLED', DATEADD(DAY,  -1, SYSDATETIME())),
 (8, 10,  279000, N'CREATED',   DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.checkout_sessions OFF;

/* ============================================================================
   14. orders (8 don hang mau voi cac kieu fulfillment va trang thai khac nhau)
   ========================================================================== */
SET IDENTITY_INSERT dbo.orders ON;
INSERT INTO dbo.orders (order_id, checkout_id, user_id, store_id, fulfillment_type, payment_method, payment_status, order_status, receiver_name, receiver_phone, shipping_address, pickup_code, ready_at, picked_up_at, total_amount, shipping_fee, created_at, updated_at) VALUES
 -- Don 1: DELIVERY ONLINE 1 Store (Thu Duc) -> COMPLETED
 (1, 1, 7, 1,    N'DELIVERY',     N'ONLINE',       N'PAID',   N'COMPLETED',
    N'Phạm Thu Hà', N'0911111111', N'12 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM', NULL, NULL, NULL,
    447000, 0, DATEADD(DAY, -14, SYSDATETIME()), DATEADD(DAY, -13, SYSDATETIME())),

 -- Don 2: DELIVERY ONLINE 2 Store (Thu Duc & Go Vap) -> PARTIALLY_COMPLETED (1 giao xong, 1 dang giao)
 (2, 2, 7, NULL, N'DELIVERY',     N'ONLINE',       N'PAID',   N'PARTIALLY_COMPLETED',
    N'Phạm Thu Hà', N'0911111111', N'88 Nguyễn Đình Chiểu, P. Đa Kao, Quận 1, TP.HCM', NULL, NULL, NULL,
    607000, 0, DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -9, SYSDATETIME())),

 -- Don 3: DELIVERY ONLINE 3 Store (Thu Duc, Go Vap, Quan 7) -> PROCESSING (Packed, Preparing, Confirmed)
 (3, 3, 8, NULL, N'DELIVERY',     N'ONLINE',       N'PAID',   N'PROCESSING',
    N'Đặng Quốc Khánh', N'0922222222', N'45 Quang Trung, P.10, Q. Gò Vấp, TP.HCM', NULL, NULL, NULL,
    1239000, 0, DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, SYSDATETIME())),

 -- Don 4: STORE_PICKUP Thu Duc -> PROCESSING (READY_FOR_PICKUP voi pickup_code)
 (4, 4, 8, 1,    N'STORE_PICKUP', N'ONLINE',       N'PAID',   N'PROCESSING',
    N'Đặng Quốc Khánh', N'0922222222', NULL, N'PK8391', DATEADD(DAY, -4, SYSDATETIME()), NULL,
    249000, 0, DATEADD(DAY, -5, SYSDATETIME()), DATEADD(DAY, -4, SYSDATETIME())),

 -- Don 5: STORE_PICKUP Go Vap -> COMPLETED (da nhan tai quay, thanh toan PAY_AT_STORE)
 (5, 5, 9, 2,    N'STORE_PICKUP', N'PAY_AT_STORE', N'PAID',   N'COMPLETED',
    N'Lê Hoàng Nam', N'0933333333', NULL, N'PK2746', DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -3, SYSDATETIME()),
    190000, 0, DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -3, SYSDATETIME())),

 -- Don 6: DELIVERY COD 1 Store (Thu Duc) -> CONFIRMED
 (6, 6, 9, 1,    N'DELIVERY',     N'COD',          N'UNPAID', N'CONFIRMED',
    N'Lê Hoàng Nam', N'0933333333', N'303 Nguyễn Thị Thập, P. Tân Phong, Quận 7, TP.HCM', NULL, NULL, NULL,
    178000, 0, DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME())),

 -- Don 7: DELIVERY ONLINE bi HUY truoc PREPARING (2 Store) -> CANCELLED & SIMULATED_REFUNDED
 (7, 7, 10,NULL, N'DELIVERY',     N'ONLINE',       N'REFUNDED', N'CANCELLED',
    N'Trần Thị Mai', N'0944444444', N'15 Hàng Khay, P. Tràng Tiền, Q. Hoàn Kiếm, Hà Nội', NULL, NULL, NULL,
    458000, 0, DATEADD(DAY, -1, SYSDATETIME()), DATEADD(HOUR, -20, SYSDATETIME())),

 -- Don 8: DELIVERY ONLINE cho thanh toan -> PENDING_PAYMENT
 (8, 8, 10, 1,   N'DELIVERY',     N'ONLINE',       N'UNPAID', N'PENDING_PAYMENT',
    N'Trần Thị Mai', N'0944444444', N'15 Hàng Khay, P. Tràng Tiền, Q. Hoàn Kiếm, Hà Nội', NULL, NULL, NULL,
    279000, 0, DATEADD(HOUR, -2, SYSDATETIME()), DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.orders OFF;

/* ============================================================================
   15. order_fulfillments (Kien hang dieu phoi theo Store)
   ========================================================================== */
SET IDENTITY_INSERT dbo.order_fulfillments ON;
INSERT INTO dbo.order_fulfillments (fulfillment_id, order_id, store_id, fulfillment_type, status, tracking_code, pickup_code, assigned_at, prepared_at, packed_at, ready_at, picked_up_at, shipped_at, completed_at, cancelled_at, cancel_reason, note, created_at, updated_at) VALUES
 -- Don 1: 1 Fulfillment @ Store 1 -> COMPLETED
 (1, 1, 1, N'DELIVERY',     N'COMPLETED',        N'VNPOST-TD-001', NULL,
    DATEADD(DAY, -14, SYSDATETIME()), DATEADD(DAY, -14, DATEADD(HOUR, 2, SYSDATETIME())), DATEADD(DAY, -14, DATEADD(HOUR, 4, SYSDATETIME())),
    NULL, NULL, DATEADD(DAY, -13, DATEADD(HOUR, 2, SYSDATETIME())), DATEADD(DAY, -13, SYSDATETIME()), NULL, NULL, N'Giao hàng thành công',
    DATEADD(DAY, -14, SYSDATETIME()), DATEADD(DAY, -13, SYSDATETIME())),

 -- Don 2: 2 Fulfillments (Store 1: COMPLETED, Store 2: SHIPPING)
 (2, 2, 1, N'DELIVERY',     N'COMPLETED',        N'VNPOST-TD-002', NULL,
    DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -10, DATEADD(HOUR, 2, SYSDATETIME())), DATEADD(DAY, -10, DATEADD(HOUR, 4, SYSDATETIME())),
    NULL, NULL, DATEADD(DAY, -9, DATEADD(HOUR, 2, SYSDATETIME())), DATEADD(DAY, -9, SYSDATETIME()), NULL, NULL, N'Kiện hàng 1 giao thành công',
    DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -9, SYSDATETIME())),
 (3, 2, 2, N'DELIVERY',     N'SHIPPING',         N'VNPOST-GV-002', NULL,
    DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -10, DATEADD(HOUR, 3, SYSDATETIME())), DATEADD(DAY, -10, DATEADD(HOUR, 5, SYSDATETIME())),
    NULL, NULL, DATEADD(DAY, -9, DATEADD(HOUR, 1, SYSDATETIME())), NULL, NULL, NULL, N'Kiện hàng 2 đang trên đường giao',
    DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -9, SYSDATETIME())),

 -- Don 3: 3 Fulfillments (Store 1: PACKED, Store 2: PREPARING, Store 3: CONFIRMED)
 (4, 3, 1, N'DELIVERY',     N'PACKED',           NULL, NULL,
    DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, DATEADD(HOUR, 2, SYSDATETIME())), DATEADD(DAY, -7, DATEADD(HOUR, 4, SYSDATETIME())),
    NULL, NULL, NULL, NULL, NULL, NULL, N'Đã đóng gói chờ bàn giao vận chuyển',
    DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, SYSDATETIME())),
 (5, 3, 2, N'DELIVERY',     N'PREPARING',        NULL, NULL,
    DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, DATEADD(HOUR, 1, SYSDATETIME())), NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, N'Nhân viên đang chuẩn bị hàng',
    DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, SYSDATETIME())),
 (6, 3, 3, N'DELIVERY',     N'CONFIRMED',        NULL, NULL,
    DATEADD(DAY, -7, SYSDATETIME()), NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, N'Đã tiếp nhận phân công xử lý',
    DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, SYSDATETIME())),

 -- Don 4: Store Pickup Thu Duc -> READY_FOR_PICKUP
 (7, 4, 1, N'STORE_PICKUP', N'READY_FOR_PICKUP', NULL, N'PK8391',
    DATEADD(DAY, -5, SYSDATETIME()), DATEADD(DAY, -5, DATEADD(HOUR, 2, SYSDATETIME())), NULL,
    DATEADD(DAY, -4, SYSDATETIME()), NULL, NULL, NULL, NULL, NULL, N'Hàng sẵn sàng, mã nhận PK8391',
    DATEADD(DAY, -5, SYSDATETIME()), DATEADD(DAY, -4, SYSDATETIME())),

 -- Don 5: Store Pickup Go Vap -> COMPLETED
 (8, 5, 2, N'STORE_PICKUP', N'COMPLETED',        NULL, N'PK2746',
    DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -4, DATEADD(HOUR, 1, SYSDATETIME())), NULL,
    DATEADD(DAY, -4, DATEADD(HOUR, 3, SYSDATETIME())), DATEADD(DAY, -3, SYSDATETIME()), NULL, DATEADD(DAY, -3, SYSDATETIME()), NULL, NULL, N'Khách đã nhận hàng tại quầy',
    DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -3, SYSDATETIME())),

 -- Don 6: COD Thu Duc -> CONFIRMED
 (9, 6, 1, N'DELIVERY',     N'CONFIRMED',        NULL, NULL,
    DATEADD(DAY, -2, SYSDATETIME()), NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, N'Đơn COD mới tạo hợp lệ',
    DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME())),

 -- Don 7: 2 Fulfillments CANCELLED
 (10,7, 1, N'DELIVERY',     N'CANCELLED',        NULL, NULL,
    DATEADD(DAY, -1, SYSDATETIME()), NULL, NULL,
    NULL, NULL, NULL, NULL, DATEADD(HOUR, -20, SYSDATETIME()), N'Khách hàng hủy đơn trước khi chuẩn bị', N'Đã hoàn tồn kho',
    DATEADD(DAY, -1, SYSDATETIME()), DATEADD(HOUR, -20, SYSDATETIME())),
 (11,7, 2, N'DELIVERY',     N'CANCELLED',        NULL, NULL,
    DATEADD(DAY, -1, SYSDATETIME()), NULL, NULL,
    NULL, NULL, NULL, NULL, DATEADD(HOUR, -20, SYSDATETIME()), N'Khách hàng hủy đơn trước khi chuẩn bị', N'Đã hoàn tồn kho',
    DATEADD(DAY, -1, SYSDATETIME()), DATEADD(HOUR, -20, SYSDATETIME())),

 -- Don 8: Pending payment -> CONFIRMED
 (12,8, 1, N'DELIVERY',     N'CONFIRMED',        NULL, NULL,
    DATEADD(HOUR, -2, SYSDATETIME()), NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, N'Chờ xác nhận thanh toán',
    DATEADD(HOUR, -2, SYSDATETIME()), DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.order_fulfillments OFF;

/* ============================================================================
   16. order_fulfillment_status_history
   ========================================================================== */
SET IDENTITY_INSERT dbo.order_fulfillment_status_history ON;
INSERT INTO dbo.order_fulfillment_status_history (history_id, fulfillment_id, old_status, new_status, changed_by_user_id, note, changed_at) VALUES
 -- F1 (Don 1)
 (1,  1, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -14, SYSDATETIME())),
 (2,  1, N'CONFIRMED',      N'PREPARING',        2,    N'Bắt đầu chuẩn bị hàng',      DATEADD(DAY, -14, DATEADD(HOUR, 2, SYSDATETIME()))),
 (3,  1, N'PREPARING',      N'PACKED',           2,    N'Đã đóng gói kiện hàng',      DATEADD(DAY, -14, DATEADD(HOUR, 4, SYSDATETIME()))),
 (4,  1, N'PACKED',         N'SHIPPING',         2,    N'Bàn giao đơn vị vận chuyển', DATEADD(DAY, -13, DATEADD(HOUR, 2, SYSDATETIME()))),
 (5,  1, N'SHIPPING',       N'COMPLETED',        2,    N'Giao hàng thành công',       DATEADD(DAY, -13, SYSDATETIME())),
 -- F2 (Don 2 Kiện 1)
 (6,  2, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -10, SYSDATETIME())),
 (7,  2, N'CONFIRMED',      N'PREPARING',        2,    N'Bắt đầu chuẩn bị hàng',      DATEADD(DAY, -10, DATEADD(HOUR, 2, SYSDATETIME()))),
 (8,  2, N'PREPARING',      N'PACKED',           2,    N'Đã đóng gói kiện hàng',      DATEADD(DAY, -10, DATEADD(HOUR, 4, SYSDATETIME()))),
 (9,  2, N'PACKED',         N'SHIPPING',         2,    N'Bàn giao đơn vị vận chuyển', DATEADD(DAY, -9, DATEADD(HOUR, 2, SYSDATETIME()))),
 (10, 2, N'SHIPPING',       N'COMPLETED',        2,    N'Giao hàng thành công',       DATEADD(DAY, -9, SYSDATETIME())),
 -- F3 (Don 2 Kiện 2)
 (11, 3, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -10, SYSDATETIME())),
 (12, 3, N'CONFIRMED',      N'PREPARING',        3,    N'Bắt đầu chuẩn bị hàng',      DATEADD(DAY, -10, DATEADD(HOUR, 3, SYSDATETIME()))),
 (13, 3, N'PREPARING',      N'PACKED',           3,    N'Đã đóng gói kiện hàng',      DATEADD(DAY, -10, DATEADD(HOUR, 5, SYSDATETIME()))),
 (14, 3, N'PACKED',         N'SHIPPING',         3,    N'Đang giao hàng',             DATEADD(DAY, -9, DATEADD(HOUR, 1, SYSDATETIME()))),
 -- F4, F5, F6 (Don 3)
 (15, 4, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -7, SYSDATETIME())),
 (16, 4, N'CONFIRMED',      N'PREPARING',        2,    N'Bắt đầu chuẩn bị',           DATEADD(DAY, -7, DATEADD(HOUR, 2, SYSDATETIME()))),
 (17, 4, N'PREPARING',      N'PACKED',           2,    N'Đóng gói hoàn tất',          DATEADD(DAY, -7, DATEADD(HOUR, 4, SYSDATETIME()))),
 (18, 5, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -7, SYSDATETIME())),
 (19, 5, N'CONFIRMED',      N'PREPARING',        3,    N'Bắt đầu chuẩn bị',           DATEADD(DAY, -7, DATEADD(HOUR, 1, SYSDATETIME()))),
 (20, 6, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -7, SYSDATETIME())),
 -- F7 (Don 4 Pickup)
 (21, 7, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận đơn pickup',       DATEADD(DAY, -5, SYSDATETIME())),
 (22, 7, N'CONFIRMED',      N'PREPARING',        2,    N'Chuẩn bị hàng tại cửa hàng', DATEADD(DAY, -5, DATEADD(HOUR, 2, SYSDATETIME()))),
 (23, 7, N'PREPARING',      N'READY_FOR_PICKUP', 2,    N'Cấp mã nhận PK8391',         DATEADD(DAY, -4, SYSDATETIME())),
 -- F8 (Don 5 Pickup completed)
 (24, 8, NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận đơn pickup',       DATEADD(DAY, -4, SYSDATETIME())),
 (25, 8, N'CONFIRMED',      N'PREPARING',        3,    N'Chuẩn bị hàng tại cửa hàng', DATEADD(DAY, -4, DATEADD(HOUR, 1, SYSDATETIME()))),
 (26, 8, N'PREPARING',      N'READY_FOR_PICKUP', 3,    N'Cấp mã nhận PK2746',         DATEADD(DAY, -4, DATEADD(HOUR, 3, SYSDATETIME()))),
 (27, 8, N'READY_FOR_PICKUP',N'COMPLETED',       3,    N'Xác thực mã và thu tiền tại quầy', DATEADD(DAY, -3, SYSDATETIME())),
 -- F9 (Don 6 COD)
 (28, 9, NULL,              N'CONFIRMED',        NULL, N'Tạo đơn COD tự động',        DATEADD(DAY, -2, SYSDATETIME())),
 -- F10, F11 (Don 7 Cancelled)
 (29, 10,NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -1, SYSDATETIME())),
 (30, 10,N'CONFIRMED',      N'CANCELLED',        NULL, N'Khách hủy trước khi chuẩn bị',DATEADD(HOUR, -20, SYSDATETIME())),
 (31, 11,NULL,              N'CONFIRMED',        NULL, N'Tiếp nhận điều phối tự động', DATEADD(DAY, -1, SYSDATETIME())),
 (32, 11,N'CONFIRMED',      N'CANCELLED',        NULL, N'Khách hủy trước khi chuẩn bị',DATEADD(HOUR, -20, SYSDATETIME())),
 -- F12 (Don 8 Pending)
 (33, 12,NULL,              N'CONFIRMED',        NULL, N'Tạo đơn chờ thanh toán',     DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.order_fulfillment_status_history OFF;

/* ============================================================================
   17. order_items (Gan fulfillment_id va store_product_id, snapshot gia tu Product)
   ========================================================================== */
SET IDENTITY_INSERT dbo.order_items ON;
INSERT INTO dbo.order_items (order_item_id, order_id, fulfillment_id, store_product_id, product_name, unit_price, quantity, subtotal) VALUES
 -- Don 1 (Fulfillment 1 @ Store 1 Thu Duc)
 (1,  1, 1,  1, N'Serum Bí Đao Cocoon Giảm Dầu Mụn 30ml',                   129000, 2, 258000),
 (2,  1, 1,  2, N'Má hồng dạng kem BBIA Downy Cheek #06',                    189000, 1, 189000),

 -- Don 2 (Fulfillment 2 @ Store 1 + Fulfillment 3 @ Store 2)
 (3,  2, 2,  3, N'Serum sáng mờ thâm L''Oréal Glycolic-Bright 30ml',         329000, 1, 329000),
 (4,  2, 3,  4, N'Mascara uốn mi cong dày Maybelline Hyper Curl',           139000, 2, 278000),

 -- Don 3 (Fulfillment 4 @ Store 1 + Fulfillment 5 @ Store 2 + Fulfillment 6 @ Store 3)
 (5,  3, 4,  5, N'Kem giảm mụn ngừa thâm La Roche-Posay Effaclar Duo+ 40ml',395000, 1, 395000),
 (6,  3, 5,  6, N'Tinh chất rau má phục hồi SKIN1004 Centella Ampoule 100ml',349000, 1, 349000),
 (7,  3, 6,  7, N'Sữa chống nắng kiềm dầu Anessa Perfect UV Milk 60ml',      495000, 1, 495000),

 -- Don 4 (Fulfillment 7 @ Store 1 Thu Duc Pickup)
 (8,  4, 7,  8, N'Nước hoa hồng trà xanh Innisfree Green Tea 200ml',         249000, 1, 249000),

 -- Don 5 (Fulfillment 8 @ Store 2 Go Vap Pickup)
 (9,  5, 8,  9, N'Dầu gội bưởi giảm rụng tóc Cocoon 310ml',                  95000, 2, 190000),

 -- Don 6 (Fulfillment 9 @ Store 1 Thu Duc COD)
 (10, 6, 9, 10, N'Sữa rửa mặt bọt tơ tằm Senka Perfect Whip 120g',           89000, 2, 178000),

 -- Don 7 (Fulfillment 10 @ Store 1 + Fulfillment 11 @ Store 2 Cancelled)
 (11, 7, 10,11, N'Nước tẩy trang dịu nhẹ Garnier Micellar Cleansing Water 400ml', 159000, 1, 159000),
 (12, 7, 11,12, N'Gel dưỡng ẩm Neutrogena Hydro Boost Water Gel 50g',        299000, 1, 299000),

 -- Don 8 (Fulfillment 12 @ Store 1 Thu Duc Pending)
 (13, 8, 12,13, N'Sữa rửa mặt cho da dầu CeraVe Foaming Cleanser 236ml',     279000, 1, 279000);
SET IDENTITY_INSERT dbo.order_items OFF;

/* ============================================================================
   18. payments (Gan Order, ho tro simulated refund)
   ========================================================================== */
SET IDENTITY_INSERT dbo.payments ON;
INSERT INTO dbo.payments (payment_id, order_id, method, amount, status, transaction_code, paid_at, refund_status, refund_amount, refund_requested_at, refunded_at, refund_reason, created_at) VALUES
 (1, 1, N'ONLINE',       447000, N'SUCCESS', N'VNPAY-20261001-001', DATEADD(DAY, -14, SYSDATETIME()), N'NONE',                NULL,   NULL, NULL, NULL, DATEADD(DAY, -14, SYSDATETIME())),
 (2, 2, N'ONLINE',       607000, N'SUCCESS', N'VNPAY-20261005-002', DATEADD(DAY, -10, SYSDATETIME()), N'NONE',                NULL,   NULL, NULL, NULL, DATEADD(DAY, -10, SYSDATETIME())),
 (3, 3, N'ONLINE',      1239000, N'SUCCESS', N'VNPAY-20261008-003', DATEADD(DAY,  -7, SYSDATETIME()), N'NONE',                NULL,   NULL, NULL, NULL, DATEADD(DAY,  -7, SYSDATETIME())),
 (4, 4, N'ONLINE',       249000, N'SUCCESS', N'VNPAY-20261010-004', DATEADD(DAY,  -5, SYSDATETIME()), N'NONE',                NULL,   NULL, NULL, NULL, DATEADD(DAY,  -5, SYSDATETIME())),
 (5, 5, N'PAY_AT_STORE', 190000, N'SUCCESS', NULL,                  DATEADD(DAY,  -3, SYSDATETIME()), N'NONE',                NULL,   NULL, NULL, NULL, DATEADD(DAY,  -4, SYSDATETIME())),
 (6, 6, N'COD',          178000, N'PENDING', NULL,                  NULL,                             N'NONE',                NULL,   NULL, NULL, NULL, DATEADD(DAY,  -2, SYSDATETIME())),
 (7, 7, N'ONLINE',       458000, N'SUCCESS', N'VNPAY-20261012-007', DATEADD(DAY,  -1, SYSDATETIME()), N'SIMULATED_REFUNDED', 458000, DATEADD(HOUR, -20, SYSDATETIME()), DATEADD(HOUR, -20, SYSDATETIME()), N'Khách hàng hủy đơn hợp lệ trước chuẩn bị', DATEADD(DAY, -1, SYSDATETIME())),
 (8, 8, N'ONLINE',       279000, N'PENDING', NULL,                  NULL,                             N'NONE',                NULL,   NULL, NULL, NULL, DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.payments OFF;

/* ============================================================================
   19. inventory_movements (Audit bien dong ton kho dung StoreProduct cua OrderItem)
   ========================================================================== */
SET IDENTITY_INSERT dbo.inventory_movements ON;
INSERT INTO dbo.inventory_movements (movement_id, store_product_id, type, quantity_change, quantity_before, quantity_after, reference_order_id, staff_id, note, created_at) VALUES
 -- Don 1
 (1,  1, N'ORDER',        -2, 42, 40, 1, NULL, N'Đơn hàng #1 - Trừ tồn Cocoon Serum',        DATEADD(DAY, -14, SYSDATETIME())),
 (2,  2, N'ORDER',        -1, 13, 12, 1, NULL, N'Đơn hàng #1 - Trừ tồn BBIA Cheek #06',      DATEADD(DAY, -14, SYSDATETIME())),

 -- Don 2
 (3,  3, N'ORDER',        -1, 21, 20, 2, NULL, N'Đơn hàng #2 - Trừ tồn L''Oreal Serum',       DATEADD(DAY, -10, SYSDATETIME())),
 (4,  4, N'ORDER',        -2, 32, 30, 2, NULL, N'Đơn hàng #2 - Trừ tồn Maybelline Mascara',  DATEADD(DAY, -10, SYSDATETIME())),

 -- Don 3
 (5,  5, N'ORDER',        -1, 16, 15, 3, NULL, N'Đơn hàng #3 - Trừ tồn LRP Duo+',            DATEADD(DAY, -7, SYSDATETIME())),
 (6,  6, N'ORDER',        -1, 19, 18, 3, NULL, N'Đơn hàng #3 - Trừ tồn SKIN1004 Ampoule',     DATEADD(DAY, -7, SYSDATETIME())),
 (7,  7, N'ORDER',        -1, 26, 25, 3, NULL, N'Đơn hàng #3 - Trừ tồn Anessa Milk',          DATEADD(DAY, -7, SYSDATETIME())),

 -- Don 4
 (8,  8, N'ORDER',        -1, 23, 22, 4, NULL, N'Đơn hàng #4 - Trừ tồn Innisfree Toner',     DATEADD(DAY, -5, SYSDATETIME())),

 -- Don 5
 (9,  9, N'ORDER',        -2, 37, 35, 5, NULL, N'Đơn hàng #5 - Trừ tồn Cocoon Dầu Gội',      DATEADD(DAY, -4, SYSDATETIME())),

 -- Don 6
 (10, 10,N'ORDER',        -2, 55, 53, 6, NULL, N'Đơn hàng #6 - Trừ tồn Senka Whip',          DATEADD(DAY, -2, SYSDATETIME())),

 -- Don 7 (Tru ton khi tao don, sau do hoan ton khi huy)
 (11, 11,N'ORDER',        -1, 25, 24, 7, NULL, N'Đơn hàng #7 - Trừ tồn Garnier Micellar',     DATEADD(DAY, -1, SYSDATETIME())),
 (12, 12,N'ORDER',        -1, 18, 17, 7, NULL, N'Đơn hàng #7 - Trừ tồn Neutrogena Gel',      DATEADD(DAY, -1, SYSDATETIME())),
 (13, 11,N'CANCEL_ORDER', +1, 24, 25, 7, NULL, N'Hủy đơn #7 - Hoàn tồn kho Garnier Micellar',DATEADD(HOUR, -20, SYSDATETIME())),
 (14, 12,N'CANCEL_ORDER', +1, 17, 18, 7, NULL, N'Hủy đơn #7 - Hoàn tồn kho Neutrogena Gel', DATEADD(HOUR, -20, SYSDATETIME())),

 -- Don 8
 (15, 13,N'ORDER',        -1, 15, 14, 8, NULL, N'Đơn hàng #8 - Trừ tồn CeraVe Foaming',      DATEADD(HOUR, -2, SYSDATETIME())),

 -- Bien dong kiem ke (STOCK_ADJUST co staff_id)
 (16, 1, N'STOCK_ADJUST', +5, 35, 40, NULL, 2, N'Kiểm kê định kỳ tháng 10 - nhập bù',        DATEADD(DAY, -15, SYSDATETIME()));
SET IDENTITY_INSERT dbo.inventory_movements OFF;

/* ============================================================================
   20. order_status_history (Audit timeline Order tong the)
   ========================================================================== */
SET IDENTITY_INSERT dbo.order_status_history ON;
INSERT INTO dbo.order_status_history (history_id, order_id, old_status, new_status, changed_by_user_id, note, changed_at) VALUES
 -- Don 1
 (1,  1, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn hàng online',                 DATEADD(DAY, -14, SYSDATETIME())),
 (2,  1, N'PENDING_PAYMENT',N'CONFIRMED',        NULL, N'Xác nhận thanh toán online thành công',DATEADD(DAY, -14, SYSDATETIME())),
 (3,  1, N'CONFIRMED',      N'PROCESSING',       2,    N'Bắt đầu chuẩn bị hàng',               DATEADD(DAY, -14, DATEADD(HOUR, 2, SYSDATETIME()))),
 (4,  1, N'PROCESSING',     N'SHIPPING',         2,    N'Bàn giao đơn vị vận chuyển',          DATEADD(DAY, -13, DATEADD(HOUR, 2, SYSDATETIME()))),
 (5,  1, N'SHIPPING',       N'COMPLETED',        2,    N'Đơn hàng hoàn tất',                  DATEADD(DAY, -13, SYSDATETIME())),

 -- Don 2
 (6,  2, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn hàng online',                 DATEADD(DAY, -10, SYSDATETIME())),
 (7,  2, N'PENDING_PAYMENT',N'CONFIRMED',        NULL, N'Xác nhận thanh toán online thành công',DATEADD(DAY, -10, SYSDATETIME())),
 (8,  2, N'CONFIRMED',      N'PROCESSING',       2,    N'Các chi nhánh bắt đầu chuẩn bị',      DATEADD(DAY, -10, DATEADD(HOUR, 2, SYSDATETIME()))),
 (9,  2, N'PROCESSING',     N'PARTIALLY_COMPLETED',NULL,N'Kiện 1 hoàn tất, kiện 2 đang giao',  DATEADD(DAY, -9, SYSDATETIME())),

 -- Don 3
 (10, 3, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn hàng online',                 DATEADD(DAY, -7, SYSDATETIME())),
 (11, 3, N'PENDING_PAYMENT',N'CONFIRMED',        NULL, N'Xác nhận thanh toán online thành công',DATEADD(DAY, -7, SYSDATETIME())),
 (12, 3, N'CONFIRMED',      N'PROCESSING',       2,    N'Chi nhánh đang xử lý đóng gói hàng',  DATEADD(DAY, -7, DATEADD(HOUR, 1, SYSDATETIME()))),

 -- Don 4
 (13, 4, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn nhận tại cửa hàng',          DATEADD(DAY, -5, SYSDATETIME())),
 (14, 4, N'PENDING_PAYMENT',N'CONFIRMED',        NULL, N'Thanh toán online thành công',        DATEADD(DAY, -5, SYSDATETIME())),
 (15, 4, N'CONFIRMED',      N'PROCESSING',       2,    N'Cửa hàng chuẩn bị hàng',             DATEADD(DAY, -5, DATEADD(HOUR, 2, SYSDATETIME()))),

 -- Don 5
 (16, 5, NULL,              N'CONFIRMED',        NULL, N'Đặt nhận tại cửa hàng - trả tại quầy',DATEADD(DAY, -4, SYSDATETIME())),
 (17, 5, N'CONFIRMED',      N'PROCESSING',       3,    N'Cửa hàng chuẩn bị hàng sẵn sàng',    DATEADD(DAY, -4, DATEADD(HOUR, 1, SYSDATETIME()))),
 (18, 5, N'PROCESSING',     N'COMPLETED',        3,    N'Khách nhận và thanh toán xong',      DATEADD(DAY, -3, SYSDATETIME())),

 -- Don 6
 (19, 6, NULL,              N'CONFIRMED',        NULL, N'Đơn COD được duyệt tự động',          DATEADD(DAY, -2, SYSDATETIME())),

 -- Don 7
 (20, 7, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn hàng online',                 DATEADD(DAY, -1, SYSDATETIME())),
 (21, 7, N'PENDING_PAYMENT',N'CONFIRMED',        NULL, N'Thanh toán online thành công',        DATEADD(DAY, -1, SYSDATETIME())),
 (22, 7, N'CONFIRMED',      N'CANCELLED',        NULL, N'Khách hủy đơn - kích hoạt hoàn tiền mô phỏng', DATEADD(HOUR, -20, SYSDATETIME())),

 -- Don 8
 (23, 8, NULL,              N'PENDING_PAYMENT',  NULL, N'Tạo đơn hàng chờ thanh toán',         DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.order_status_history OFF;

/* ============================================================================
   21. reviews (Gan voi order_item_id da mua thuc te)
   ========================================================================== */
SET IDENTITY_INSERT dbo.reviews ON;
INSERT INTO dbo.reviews (review_id, user_id, product_id, order_item_id, rating, comment, status, created_at, order_id, reply, replied_by, replied_at) VALUES
 -- Danh gia 1: Ha danh gia Cocoon Serum tren OrderItem 1 (Don 1 @ Store 1 Thu Duc)
 (1, 7, 3, 1, 5, N'Serum bí đao trị mụn rất tốt, hàng chính hãng giao nhanh.', N'ACTIVE', DATEADD(DAY, -12, SYSDATETIME()), 1,
    N'OneShop Thủ Đức cảm ơn bạn Hà đã tin dùng sản phẩm của chúng tôi!', 2, DATEADD(DAY, -11, SYSDATETIME())),

 -- Danh gia 2: Ha danh gia L''Oreal Serum tren OrderItem 3 (Don 2 @ Store 1 Thu Duc)
 (2, 7, 7, 3, 5, N'Serum L''Oréal sáng da thấy rõ sau 2 tuần dùng.',          N'ACTIVE', DATEADD(DAY, -8,  SYSDATETIME()), 2,
    N'Dạ cảm ơn bạn đã phản hồi tốt về sản phẩm Glycolic-Bright ạ!',      2, DATEADD(DAY, -7,  SYSDATETIME())),

 -- Danh gia 3: Nam danh gia Cocoon Dau Goi tren OrderItem 9 (Don 5 @ Store 2 Go Vap)
 (3, 9, 5, 9, 5, N'Nhận hàng tại cửa hàng Gò Vấp rất nhanh, bạn nhân viên nhiệt tình.', N'ACTIVE', DATEADD(DAY, -2, SYSDATETIME()), 5,
    N'OneShop Gò Vấp xin cảm ơn bạn Nam rất nhiều!',                      3, DATEADD(DAY, -1,  SYSDATETIME()));
SET IDENTITY_INSERT dbo.reviews OFF;

/* ============================================================================
   22. review_replies (Phan hoi tu nhan vien chi nhanh dung tham quyen)
   ========================================================================== */
SET IDENTITY_INSERT dbo.review_replies ON;
INSERT INTO dbo.review_replies (reply_id, review_id, staff_user_id, content, created_at, updated_at) VALUES
 (1, 1, 2, N'OneShop Thủ Đức cảm ơn bạn Hà đã tin dùng sản phẩm của chúng tôi!', DATEADD(DAY, -11, SYSDATETIME()), DATEADD(DAY, -11, SYSDATETIME())),
 (2, 2, 2, N'Dạ cảm ơn bạn đã phản hồi tốt về sản phẩm Glycolic-Bright ạ!',      DATEADD(DAY, -7,  SYSDATETIME()), DATEADD(DAY, -7,  SYSDATETIME())),
 (3, 3, 3, N'OneShop Gò Vấp xin cảm ơn bạn Nam rất nhiều!',                      DATEADD(DAY, -1,  SYSDATETIME()), DATEADD(DAY, -1,  SYSDATETIME()));
SET IDENTITY_INSERT dbo.review_replies OFF;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

PRINT 'OneShop V3 seed fixture loaded successfully (deterministic; rerun requires a new database).';
GO
