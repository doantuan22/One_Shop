/* =============================================================================
   OneShop V3 - 02_seed.sql (bo du lieu mau OneShop V3, san pham my pham THAT)

   Du lieu THAT: ten san pham, thuong hieu, dung tich, mau/shade, gia tham khao (kem URL nguon va ngay xac minh 20/10/2026)
   va anh bao bi (anh CC BY-SA 3.0 cua Open Beauty Facts, da tai len Cloudinary). Nguon va ghi cong: docs/seed/.
   Du lieu MO PHONG (khong phai so lieu cua nha ban le that): ton kho tung chi nhanh (store_products), 6 chi nhanh,
   tai khoan, don hang, thanh toan (cong Mock), hoan tien mo phong, bien dong kho, review va phan hoi.
   Review/phan hoi mau duoc danh dau "[Du lieu mo phong]"; khong phai danh gia cua khach hang that.

   Bao gom: 8 Brands, 4 Categories, 27 SKU (products.price la gia online thong nhat), 6 Stores,
   ton kho da chi nhanh phu 8 kich ban (A -> H), 8 Don hang mau (Online 1/2/3 Store, Store Pickup, COD, Cancelled + Simulated
   Refund, Pending Payment) voi Fulfillment, lich su, InventoryMovement va Payment nhat quan.
   Chi chay tren database moi tao bang 01_schema.sql. Script khong DROP/xoa du lieu.
   ========================================================================== */
USE [$(DatabaseName)];
GO
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

-- Check every table: an empty roles lookup does not prove that the database is empty.
DECLARE @seedHasData bit = 0;
DECLARE @seedPreflight nvarchar(max);
SELECT @seedPreflight = STRING_AGG(CAST(N'IF EXISTS (SELECT 1 FROM '
    + QUOTENAME(s.name) + N'.' + QUOTENAME(t.name) + N') SET @hasData = 1;' AS nvarchar(max)), NCHAR(10))
FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE t.is_ms_shipped = 0;
EXEC sys.sp_executesql @seedPreflight, N'@hasData bit OUTPUT', @hasData = @seedHasData OUTPUT;
IF @seedHasData = 1
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
   6. categories (anh danh muc: Cloudinary oneshop/seed/categories hoac anh tinh cua giao dien)
   ========================================================================== */
SET IDENTITY_INSERT dbo.categories ON;
INSERT INTO dbo.categories (category_id, name, description, image_url, image_public_id, status) VALUES
 (1, N'Chăm sóc da', N'Kem dưỡng, tinh chất và chăm sóc da mặt', N'/images/site/categories/category-skincare.webp', NULL, N'ACTIVE'),
 (2, N'Trang điểm', N'Son, kem nền, kem che khuyết điểm', N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803748/oneshop/seed/categories/trang-diem.webp', N'oneshop/seed/categories/trang-diem', N'ACTIVE'),
 (3, N'Chăm sóc cơ thể', N'Kem dưỡng thể, sáp dưỡng ẩm', N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803739/oneshop/seed/categories/cham-soc-co-the.jpg', N'oneshop/seed/categories/cham-soc-co-the', N'ACTIVE'),
 (4, N'Tẩy trang & làm sạch', N'Sữa rửa mặt, gel rửa mặt, nước tẩy trang', N'https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803745/oneshop/seed/categories/tay-trang-va-lam-sach.jpg', N'oneshop/seed/categories/tay-trang-va-lam-sach', N'ACTIVE');
SET IDENTITY_INSERT dbo.categories OFF;
/* ============================================================================
   7. brands (chua co logo co quyen su dung da xac minh -> logo NULL, giao dien hien thi ten dang text)
   ========================================================================== */
SET IDENTITY_INSERT dbo.brands ON;
INSERT INTO dbo.brands (brand_id, name, description, logo_url, logo_public_id, status) VALUES
 (1, N'CeraVe', N'Thương hiệu chăm sóc da', NULL, NULL, N'ACTIVE'),
 (2, N'Cetaphil', N'Thương hiệu chăm sóc da', NULL, NULL, N'ACTIVE'),
 (3, N'Bioderma', N'Thương hiệu dược mỹ phẩm', NULL, NULL, N'ACTIVE'),
 (4, N'La Roche-Posay', N'Thương hiệu dược mỹ phẩm', NULL, NULL, N'ACTIVE'),
 (5, N'Simple', N'Thương hiệu chăm sóc da', NULL, NULL, N'ACTIVE'),
 (6, N'Maybelline New York', N'Thương hiệu trang điểm', NULL, NULL, N'ACTIVE'),
 (7, N'Vaseline', N'Thương hiệu sáp dưỡng ẩm và dưỡng môi', NULL, NULL, N'ACTIVE'),
 (8, N'Nivea', N'Thương hiệu chăm sóc da và cơ thể', NULL, NULL, N'ACTIVE');
SET IDENTITY_INSERT dbo.brands OFF;
/* ============================================================================
   8. products (SKU that; gia tham khao thuc te, url nguon trong docs/seed/products.json; gia online thong nhat toan chuoi)
   ========================================================================== */
SET IDENTITY_INSERT dbo.products ON;
INSERT INTO dbo.products (product_id, category_id, brand_id, sku, name, description, price, original_price, status) VALUES
 (1, 1, 1, N'CERAVE-CREAM-340', N'Kem dưỡng ẩm CeraVe Moisturising Cream 340g', N'Kem dưỡng ẩm CeraVe Moisturising Cream dung tích 340g. Theo nhãn bao bì: dành cho da thường đến da khô, chứa 3 ceramide thiết yếu.', 450000, NULL, N'ACTIVE'),
 (2, 1, 1, N'CERAVE-CREAM-177', N'Kem dưỡng ẩm CeraVe Moisturising Cream 177ml', N'Kem dưỡng ẩm CeraVe Moisturising Cream dạng tuýp 177ml. Theo nhãn bao bì: dành cho da khô đến rất khô, chứa 3 ceramide thiết yếu và hyaluronic acid, không mùi.', 370000, NULL, N'ACTIVE'),
 (3, 4, 1, N'CERAVE-FOAM-88', N'Sữa rửa mặt CeraVe Foaming Cleanser 88ml', N'Sữa rửa mặt tạo bọt CeraVe Foaming Cleanser 88ml (3 FL OZ). Theo nhãn bao bì: dành cho da thường đến da dầu, kiểm soát dầu.', 180000, NULL, N'ACTIVE'),
 (4, 4, 1, N'CERAVE-HYDRA-236', N'Sữa rửa mặt CeraVe Hydrating Cleanser 236ml', N'Sữa rửa mặt CeraVe Hydrating Cleanser 236ml. Theo nhãn bao bì: dành cho da thường đến da khô, làm sạch và giúp phục hồi hàng rào bảo vệ da, chứa 3 ceramide thiết yếu và hyaluronic acid.', 340000, NULL, N'ACTIVE'),
 (5, 1, 2, N'CETAPHIL-CREAM-453', N'Kem dưỡng ẩm Cetaphil Moisturising Cream 453g', N'Kem dưỡng ẩm Cetaphil Moisturising Cream 453g. Theo nhãn bao bì: dành cho da khô đến rất khô, da nhạy cảm; dưỡng ẩm 48 giờ và giúp phục hồi hàng rào da.', 510000, NULL, N'ACTIVE'),
 (6, 4, 2, N'CETAPHIL-GENTLE-473', N'Sữa rửa mặt Cetaphil Gentle Skin Cleanser 473ml', N'Sữa rửa mặt Cetaphil Gentle Skin Cleanser 473ml (16 FL OZ). Theo nhãn bao bì: dành cho da khô đến thường, da nhạy cảm; chứa glycerin và vitamin B5, B3.', 433000, NULL, N'ACTIVE'),
 (7, 3, 3, N'BIODERMA-ATODERM-500', N'Kem dưỡng ẩm Bioderma Atoderm Intensive Baume 500ml', N'Kem dưỡng Bioderma Atoderm Intensive Baume 500ml. Theo nhãn bao bì: dưỡng làm dịu, phục hồi lipid, dành cho da rất khô, dễ kích ứng đến da cơ địa.', 765000, NULL, N'ACTIVE'),
 (8, 1, 3, N'BIODERMA-SEBIUM-PR-30', N'Kem dưỡng Bioderma Sébium Pore Refiner 30ml', N'Kem dưỡng Bioderma Sébium Pore Refiner 30ml. Theo nhãn bao bì: chăm sóc lỗ chân lông to, dành cho da hỗn hợp đến da dầu.', 435000, NULL, N'ACTIVE'),
 (9, 4, 3, N'BIODERMA-SENSIBIO-250', N'Nước tẩy trang Bioderma Sensibio H2O 250ml', N'Nước tẩy trang Bioderma Sensibio H2O 250ml. Theo nhãn bao bì: dung dịch micellar làm sạch và làm dịu da nhạy cảm, dùng cho mặt và mắt, không cần rửa lại.', 445000, NULL, N'ACTIVE'),
 (10, 4, 4, N'LRP-EFFACLAR-MP-200', N'Gel rửa mặt La Roche-Posay Effaclar Micro-Peeling Purifying Gel 200ml', N'Gel rửa mặt La Roche-Posay Effaclar Micro-Peeling Purifying Gel 200ml. Theo nhãn bao bì: gel làm sạch micro-peeling cho mặt và cơ thể, dành cho da có khuyết điểm dai dẳng.', 515000, NULL, N'ACTIVE'),
 (11, 4, 5, N'SIMPLE-PURIFY-150', N'Sữa rửa mặt Simple Purifying Gel Wash 150ml', N'Gel rửa mặt Simple Purifying Gel Wash 150ml. Theo nhãn bao bì: dành cho da dầu, da dễ nổi mụn; chứa chiết xuất thyme, kẽm và witch hazel; không chứa xà phòng.', 138000, NULL, N'ACTIVE'),
 (12, 4, 5, N'SIMPLE-REFRESH-150', N'Sữa rửa mặt Simple Kind To Skin Refreshing Facial Wash Gel 150ml', N'Gel rửa mặt Simple Kind To Skin Refreshing Facial Wash 150ml. Theo nhãn bao bì: dành cho mọi loại da, 100% không chứa xà phòng, không hương liệu và màu nhân tạo.', 138000, NULL, N'ACTIVE'),
 (13, 4, 5, N'SIMPLE-MICELLAR-150', N'Gel rửa mặt Simple Micellar Gel Wash Water Boost 150ml', N'Gel rửa mặt Simple Micellar Gel Wash Water Boost 150ml. Theo nhãn bao bì: dành cho da khô, cấp ẩm (Pentavitin), 100% không chứa xà phòng.', 138000, NULL, N'ACTIVE'),
 (14, 2, 6, N'MAYB-CONCEALER-15', N'Kem che khuyết điểm Maybelline Fit Me Concealer 15 Fair 6.8ml', N'Kem che khuyết điểm Maybelline Fit Me Concealer, màu 15 Fair, dung tích 6.8ml.', 178000, NULL, N'ACTIVE'),
 (15, 2, 6, N'MAYB-LIFTER-003', N'Son bóng Maybelline Lifter Gloss 003 Moon 5.4ml', N'Son bóng Maybelline Lifter Gloss màu 003 Moon, dung tích 5.4ml. Theo nhãn bao bì: chứa hyaluronic acid.', 218000, NULL, N'ACTIVE'),
 (16, 2, 6, N'MAYB-MATTEINK-15', N'Son kem lì Maybelline SuperStay Matte Ink 15 Lover 5ml', N'Son kem lì Maybelline SuperStay Matte Ink màu 15 Lover, dung tích 5ml.', 268000, NULL, N'ACTIVE'),
 (17, 2, 6, N'MAYB-MATTEINK-20', N'Son kem lì Maybelline SuperStay Matte Ink 20 Pioneer 5ml', N'Son kem lì Maybelline SuperStay Matte Ink màu 20 Pioneer, dung tích 5ml.', 268000, NULL, N'ACTIVE'),
 (18, 2, 6, N'MAYB-MATTEINK-65', N'Son kem lì Maybelline SuperStay Matte Ink 65 Seductress 5ml', N'Son kem lì Maybelline SuperStay Matte Ink màu 65 Seductress, dung tích 5ml.', 268000, NULL, N'ACTIVE'),
 (19, 2, 6, N'MAYB-SS30H-110', N'Kem nền Maybelline SuperStay Active Wear 30H 110 Porcelain 30ml', N'Kem nền Maybelline SuperStay Active Wear lên đến 30 giờ, màu 110 Porcelain, dung tích 30ml.', 298000, NULL, N'ACTIVE'),
 (20, 2, 7, N'VASELINE-LIP-ROSY-7', N'Son dưỡng môi Vaseline Lip Therapy Rosy Lips 7g', N'Sáp dưỡng môi Vaseline Lip Therapy Rosy Lips (môi hồng) dạng hũ nhỏ 7g. (Trạng thái ngừng bán tại OneShop là dữ liệu mô phỏng để kiểm thử.)', 90000, NULL, N'INACTIVE'),
 (21, 2, 7, N'VASELINE-LIP-ORIG-48', N'Son dưỡng môi Vaseline Lip Therapy Original 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Original (không màu) 4.8g.', 71000, NULL, N'ACTIVE'),
 (22, 2, 7, N'VASELINE-LIP-ALOE-48', N'Son dưỡng môi Vaseline Lip Therapy Aloe Vera 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Aloe Vera (lô hội) 4.8g.', 71000, NULL, N'ACTIVE'),
 (23, 2, 7, N'VASELINE-LIP-COCOA-48', N'Son dưỡng môi Vaseline Lip Therapy Cocoa Butter 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Cocoa Butter (bơ cacao) 4.8g.', 71000, NULL, N'ACTIVE'),
 (24, 2, 7, N'VASELINE-LIP-ROSY-48', N'Son dưỡng môi Vaseline Lip Therapy Rosy Lips 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Rosy Lips (môi hồng) 4.8g.', 71000, NULL, N'ACTIVE'),
 (25, 3, 7, N'VASELINE-JELLY-COCOA-100', N'Sáp dưỡng ẩm Vaseline Cocoa Butter Moisturising Jelly 100ml', N'Sáp dưỡng ẩm Vaseline Cocoa Butter Moisturising Jelly (Rich Conditioning) dung tích 100ml.', 129000, NULL, N'ACTIVE'),
 (26, 3, 7, N'VASELINE-JELLY-ORIG-100', N'Sáp dưỡng ẩm Vaseline Pure Petroleum Jelly Original 100ml', N'Sáp dưỡng ẩm Vaseline Pure Petroleum Jelly Original dung tích 100ml.', 115000, NULL, N'ACTIVE'),
 (27, 3, 8, N'NIVEA-CREME-150', N'Kem dưỡng ẩm Nivea Creme 150ml', N'Kem dưỡng ẩm Nivea Creme hộp thiếc xanh, dung tích 150ml.', 172000, NULL, N'ACTIVE');
SET IDENTITY_INSERT dbo.products OFF;
/* ============================================================================
   9. product_images (anh that CC BY-SA 3.0 tu Open Beauty Facts, luu tren Cloudinary oneshop/v3/products (ban goc); URL hien thi them nen trang vuong c_pad,w_800,h_800; ghi cong: docs/seed/IMAGE_CREDITS.md)
   ========================================================================== */
SET IDENTITY_INSERT dbo.product_images ON;
INSERT INTO dbo.product_images (image_id, product_id, image_url, public_id, is_primary, sort_order) VALUES
 (1, 1, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488079/oneshop/v3/products/cerave-cream-340.jpg', N'oneshop/v3/products/cerave-cream-340', 1, 0),
 (2, 2, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488082/oneshop/v3/products/cerave-cream-177.jpg', N'oneshop/v3/products/cerave-cream-177', 1, 0),
 (3, 3, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488083/oneshop/v3/products/cerave-foam-88.jpg', N'oneshop/v3/products/cerave-foam-88', 1, 0),
 (4, 4, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488085/oneshop/v3/products/cerave-hydra-236.jpg', N'oneshop/v3/products/cerave-hydra-236', 1, 0),
 (5, 5, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488087/oneshop/v3/products/cetaphil-cream-453.jpg', N'oneshop/v3/products/cetaphil-cream-453', 1, 0),
 (6, 6, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488089/oneshop/v3/products/cetaphil-gentle-473.jpg', N'oneshop/v3/products/cetaphil-gentle-473', 1, 0),
 (7, 7, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488091/oneshop/v3/products/bioderma-atoderm-500.jpg', N'oneshop/v3/products/bioderma-atoderm-500', 1, 0),
 (8, 8, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488093/oneshop/v3/products/bioderma-sebium-pr-30.jpg', N'oneshop/v3/products/bioderma-sebium-pr-30', 1, 0),
 (9, 9, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488096/oneshop/v3/products/bioderma-sensibio-250.jpg', N'oneshop/v3/products/bioderma-sensibio-250', 1, 0),
 (10, 10, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488099/oneshop/v3/products/lrp-effaclar-mp-200.jpg', N'oneshop/v3/products/lrp-effaclar-mp-200', 1, 0),
 (11, 11, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488101/oneshop/v3/products/simple-purify-150.jpg', N'oneshop/v3/products/simple-purify-150', 1, 0),
 (12, 12, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488103/oneshop/v3/products/simple-refresh-150.jpg', N'oneshop/v3/products/simple-refresh-150', 1, 0),
 (13, 13, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488105/oneshop/v3/products/simple-micellar-150.jpg', N'oneshop/v3/products/simple-micellar-150', 1, 0),
 (14, 14, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488107/oneshop/v3/products/mayb-concealer-15.jpg', N'oneshop/v3/products/mayb-concealer-15', 1, 0),
 (15, 15, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488108/oneshop/v3/products/mayb-lifter-003.jpg', N'oneshop/v3/products/mayb-lifter-003', 1, 0),
 (16, 16, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488111/oneshop/v3/products/mayb-matteink-15.jpg', N'oneshop/v3/products/mayb-matteink-15', 1, 0),
 (17, 17, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488113/oneshop/v3/products/mayb-matteink-20.jpg', N'oneshop/v3/products/mayb-matteink-20', 1, 0),
 (18, 18, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488115/oneshop/v3/products/mayb-matteink-65.jpg', N'oneshop/v3/products/mayb-matteink-65', 1, 0),
 (19, 19, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488117/oneshop/v3/products/mayb-ss30h-110.jpg', N'oneshop/v3/products/mayb-ss30h-110', 1, 0),
 (20, 20, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488119/oneshop/v3/products/vaseline-lip-rosy-7.jpg', N'oneshop/v3/products/vaseline-lip-rosy-7', 1, 0),
 (21, 21, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488121/oneshop/v3/products/vaseline-lip-orig-48.jpg', N'oneshop/v3/products/vaseline-lip-orig-48', 1, 0),
 (22, 22, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488123/oneshop/v3/products/vaseline-lip-aloe-48.jpg', N'oneshop/v3/products/vaseline-lip-aloe-48', 1, 0),
 (23, 23, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488125/oneshop/v3/products/vaseline-lip-cocoa-48.jpg', N'oneshop/v3/products/vaseline-lip-cocoa-48', 1, 0),
 (24, 24, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488128/oneshop/v3/products/vaseline-lip-rosy-48.jpg', N'oneshop/v3/products/vaseline-lip-rosy-48', 1, 0),
 (25, 25, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488130/oneshop/v3/products/vaseline-jelly-cocoa-100.jpg', N'oneshop/v3/products/vaseline-jelly-cocoa-100', 1, 0),
 (26, 26, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488131/oneshop/v3/products/vaseline-jelly-orig-100.jpg', N'oneshop/v3/products/vaseline-jelly-orig-100', 1, 0),
 (27, 27, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792488134/oneshop/v3/products/nivea-creme-150.jpg', N'oneshop/v3/products/nivea-creme-150', 1, 0);
SET IDENTITY_INSERT dbo.product_images OFF;
/* ============================================================================
   10. store_products (TON KHO MO PHONG tai cac Store - khong phai so lieu cua nha ban le; phan anh kich ban A -> H)
   ========================================================================== */
SET IDENTITY_INSERT dbo.store_products ON;
INSERT INTO dbo.store_products (store_product_id, store_id, product_id, price, quantity, reserved_quantity, status) VALUES
 (1, 1, 11, 138000, 40, 0, N'ACTIVE'),
 (2, 1, 15, 218000, 12, 0, N'ACTIVE'),
 (3, 1, 5, 510000, 20, 0, N'ACTIVE'),
 (4, 2, 17, 268000, 30, 0, N'ACTIVE'),
 (5, 1, 8, 435000, 15, 0, N'ACTIVE'),
 (6, 2, 7, 765000, 18, 0, N'ACTIVE'),
 (7, 3, 10, 515000, 25, 0, N'ACTIVE'),
 (8, 1, 4, 340000, 22, 0, N'ACTIVE'),
 (9, 2, 21, 71000, 35, 0, N'ACTIVE'),
 (10, 1, 12, 138000, 53, 0, N'ACTIVE'),
 (11, 1, 3, 180000, 25, 0, N'ACTIVE'),
 (12, 2, 14, 178000, 18, 0, N'ACTIVE'),
 (13, 1, 2, 370000, 14, 0, N'ACTIVE'),
 (14, 1, 9, 445000, 0, 0, N'ACTIVE'),
 (15, 2, 9, 445000, 25, 0, N'ACTIVE'),
 (16, 1, 6, 433000, 30, 0, N'ACTIVE'),
 (17, 2, 6, 433000, 0, 0, N'ACTIVE'),
 (18, 2, 12, 138000, 48, 0, N'ACTIVE'),
 (19, 3, 12, 138000, 50, 0, N'ACTIVE'),
 (20, 4, 12, 138000, 40, 0, N'ACTIVE'),
 (21, 6, 12, 138000, 60, 0, N'ACTIVE'),
 (22, 3, 27, 172000, 15, 0, N'ACTIVE'),
 (23, 5, 11, 138000, 20, 0, N'INACTIVE'),
 (24, 5, 12, 138000, 15, 0, N'INACTIVE'),
 (25, 1, 1, 450000, 18, 0, N'ACTIVE'),
 (26, 3, 1, 450000, 12, 0, N'ACTIVE'),
 (27, 6, 1, 450000, 4, 0, N'ACTIVE'),
 (28, 2, 3, 180000, 30, 0, N'ACTIVE'),
 (29, 1, 16, 268000, 25, 0, N'ACTIVE'),
 (30, 3, 16, 268000, 3, 0, N'ACTIVE'),
 (31, 1, 19, 298000, 0, 0, N'ACTIVE'),
 (32, 2, 19, 298000, 0, 0, N'ACTIVE'),
 (33, 2, 8, 435000, 9, 0, N'ACTIVE'),
 (34, 3, 7, 765000, 11, 0, N'ACTIVE'),
 (35, 1, 24, 71000, 80, 0, N'ACTIVE'),
 (36, 3, 24, 71000, 70, 0, N'ACTIVE'),
 (37, 4, 24, 71000, 45, 0, N'ACTIVE'),
 (38, 2, 22, 71000, 60, 0, N'ACTIVE'),
 (39, 3, 22, 71000, 55, 0, N'ACTIVE'),
 (40, 1, 23, 71000, 40, 0, N'ACTIVE'),
 (41, 4, 23, 71000, 30, 0, N'ACTIVE'),
 (42, 1, 25, 129000, 24, 0, N'ACTIVE'),
 (43, 2, 25, 129000, 12, 0, N'ACTIVE'),
 (44, 1, 26, 115000, 30, 0, N'ACTIVE'),
 (45, 3, 26, 115000, 28, 0, N'ACTIVE'),
 (46, 6, 26, 115000, 20, 0, N'ACTIVE'),
 (47, 1, 20, 90000, 0, 0, N'INACTIVE');
SET IDENTITY_INSERT dbo.store_products OFF;
/* ============================================================================
   11. carts & 12. cart_items (V3: gio hang thong nhat tham chieu product_id)
   ========================================================================== */
SET IDENTITY_INSERT dbo.carts ON;
INSERT INTO dbo.carts (cart_id, user_id) VALUES
 (1, 7),
 (2, 8);
SET IDENTITY_INSERT dbo.carts OFF;

SET IDENTITY_INSERT dbo.cart_items ON;
INSERT INTO dbo.cart_items (cart_item_id, cart_id, product_id, store_product_id, quantity) VALUES
 (1, 1, 9, NULL, 1), -- Ha: Bioderma Sensibio H2O (het hang tai Thu Duc, con tai Go Vap)
 (2, 1, 1, NULL, 2), -- Ha: CeraVe Moisturising Cream 340g
 (3, 2, 16, NULL, 1), -- Khanh: Maybelline Matte Ink 15 (con hang o 2 chi nhanh, 1 chi nhanh sap het)
 (4, 2, 27, NULL, 1); -- Khanh: Nivea Creme 150ml (chi mot chi nhanh co hang)
SET IDENTITY_INSERT dbo.cart_items OFF;
/* ============================================================================
   13. checkout_sessions
   ========================================================================== */
SET IDENTITY_INSERT dbo.checkout_sessions ON;
INSERT INTO dbo.checkout_sessions (checkout_id, user_id, total_amount, status, created_at) VALUES
 (1, 7, 524000, N'COMPLETED', DATEADD(DAY, -14, SYSDATETIME())),
 (2, 7, 1076000, N'COMPLETED', DATEADD(DAY, -10, SYSDATETIME())),
 (3, 8, 1745000, N'COMPLETED', DATEADD(DAY, -7, SYSDATETIME())),
 (4, 8, 340000, N'COMPLETED', DATEADD(DAY, -5, SYSDATETIME())),
 (5, 9, 142000, N'COMPLETED', DATEADD(DAY, -4, SYSDATETIME())),
 (6, 9, 306000, N'COMPLETED', DATEADD(DAY, -2, SYSDATETIME())),
 (7, 10, 388000, N'CANCELLED', DATEADD(DAY, -1, SYSDATETIME())),
 (8, 10, 400000, N'CREATED', DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.checkout_sessions OFF;
/* ============================================================================
   14. orders (8 don hang mau voi cac kieu fulfillment va trang thai khac nhau)
   ========================================================================== */
SET IDENTITY_INSERT dbo.orders ON;
INSERT INTO dbo.orders (order_id, checkout_id, user_id, store_id, fulfillment_type, payment_method, payment_status, order_status, receiver_name, receiver_phone, shipping_address, pickup_code, ready_at, picked_up_at, total_amount, shipping_fee, created_at, updated_at) VALUES
 -- Don 1: DELIVERY ONLINE 1 Store (phi giao hang 30.000d/don) (Thu Duc) -> COMPLETED
 (1, 1, 7, 1,    N'DELIVERY',     N'ONLINE',       N'PAID',   N'COMPLETED',
    N'Phạm Thu Hà', N'0911111111', N'12 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM', NULL, NULL, NULL,
    524000, 30000, DATEADD(DAY, -14, SYSDATETIME()), DATEADD(DAY, -13, SYSDATETIME())),

 -- Don 2: DELIVERY ONLINE 2 Store (Thu Duc & Go Vap) -> PARTIALLY_COMPLETED (1 giao xong, 1 dang giao)
 (2, 2, 7, NULL, N'DELIVERY',     N'ONLINE',       N'PAID',   N'PARTIALLY_COMPLETED',
    N'Phạm Thu Hà', N'0911111111', N'88 Nguyễn Đình Chiểu, P. Đa Kao, Quận 1, TP.HCM', NULL, NULL, NULL,
    1076000, 30000, DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -9, SYSDATETIME())),

 -- Don 3: DELIVERY ONLINE 3 Store (Thu Duc, Go Vap, Quan 7) -> PROCESSING (Packed, Preparing, Confirmed)
 (3, 3, 8, NULL, N'DELIVERY',     N'ONLINE',       N'PAID',   N'PROCESSING',
    N'Đặng Quốc Khánh', N'0922222222', N'45 Quang Trung, P.10, Q. Gò Vấp, TP.HCM', NULL, NULL, NULL,
    1745000, 30000, DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, SYSDATETIME())),

 -- Don 4: STORE_PICKUP Thu Duc -> PROCESSING (READY_FOR_PICKUP voi pickup_code)
 (4, 4, 8, 1,    N'STORE_PICKUP', N'ONLINE',       N'PAID',   N'PROCESSING',
    N'Đặng Quốc Khánh', N'0922222222', NULL, N'PK8391', DATEADD(DAY, -4, SYSDATETIME()), NULL,
    340000, 0, DATEADD(DAY, -5, SYSDATETIME()), DATEADD(DAY, -4, SYSDATETIME())),

 -- Don 5: STORE_PICKUP Go Vap -> COMPLETED (da nhan tai quay, thanh toan PAY_AT_STORE)
 (5, 5, 9, 2,    N'STORE_PICKUP', N'PAY_AT_STORE', N'PAID',   N'COMPLETED',
    N'Lê Hoàng Nam', N'0933333333', NULL, N'PK2746', DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -3, SYSDATETIME()),
    142000, 0, DATEADD(DAY, -4, SYSDATETIME()), DATEADD(DAY, -3, SYSDATETIME())),

 -- Don 6: DELIVERY COD 1 Store (Thu Duc) -> CONFIRMED
 (6, 6, 9, 1,    N'DELIVERY',     N'COD',          N'UNPAID', N'CONFIRMED',
    N'Lê Hoàng Nam', N'0933333333', N'303 Nguyễn Thị Thập, P. Tân Phong, Quận 7, TP.HCM', NULL, NULL, NULL,
    306000, 30000, DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME())),

 -- Don 7: DELIVERY ONLINE bi HUY truoc PREPARING (2 Store) -> CANCELLED & SIMULATED_REFUNDED
 (7, 7, 10,NULL, N'DELIVERY',     N'ONLINE',       N'REFUNDED', N'CANCELLED',
    N'Trần Thị Mai', N'0944444444', N'15 Hàng Khay, P. Tràng Tiền, Q. Hoàn Kiếm, Hà Nội', NULL, NULL, NULL,
    388000, 30000, DATEADD(DAY, -1, SYSDATETIME()), DATEADD(HOUR, -20, SYSDATETIME())),

 -- Don 8: DELIVERY ONLINE cho thanh toan -> PENDING_PAYMENT
 (8, 8, 10, 1,   N'DELIVERY',     N'ONLINE',       N'UNPAID', N'PENDING_PAYMENT',
    N'Trần Thị Mai', N'0944444444', N'15 Hàng Khay, P. Tràng Tiền, Q. Hoàn Kiếm, Hà Nội', NULL, NULL, NULL,
    400000, 30000, DATEADD(HOUR, -2, SYSDATETIME()), DATEADD(HOUR, -2, SYSDATETIME()));
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
   17. order_items (snapshot gia tu Product; nguon Store khop Fulfillment va StoreProduct)
   ========================================================================== */
SET IDENTITY_INSERT dbo.order_items ON;
INSERT INTO dbo.order_items (order_item_id, order_id, fulfillment_id, store_product_id, product_name, unit_price, quantity, subtotal) VALUES
 (1, 1, 1, 1, N'Sữa rửa mặt Simple Purifying Gel Wash 150ml', 138000, 2, 276000),
 (2, 1, 1, 2, N'Son bóng Maybelline Lifter Gloss 003 Moon 5.4ml', 218000, 1, 218000),
 (3, 2, 2, 3, N'Kem dưỡng ẩm Cetaphil Moisturising Cream 453g', 510000, 1, 510000),
 (4, 2, 3, 4, N'Son kem lì Maybelline SuperStay Matte Ink 20 Pioneer 5ml', 268000, 2, 536000),
 (5, 3, 4, 5, N'Kem dưỡng Bioderma Sébium Pore Refiner 30ml', 435000, 1, 435000),
 (6, 3, 5, 6, N'Kem dưỡng ẩm Bioderma Atoderm Intensive Baume 500ml', 765000, 1, 765000),
 (7, 3, 6, 7, N'Gel rửa mặt La Roche-Posay Effaclar Micro-Peeling Purifying Gel 200ml', 515000, 1, 515000),
 (8, 4, 7, 8, N'Sữa rửa mặt CeraVe Hydrating Cleanser 236ml', 340000, 1, 340000),
 (9, 5, 8, 9, N'Son dưỡng môi Vaseline Lip Therapy Original 4.8g', 71000, 2, 142000),
 (10, 6, 9, 10, N'Sữa rửa mặt Simple Kind To Skin Refreshing Facial Wash Gel 150ml', 138000, 2, 276000),
 (11, 7, 10, 11, N'Sữa rửa mặt CeraVe Foaming Cleanser 88ml', 180000, 1, 180000),
 (12, 7, 11, 12, N'Kem che khuyết điểm Maybelline Fit Me Concealer 15 Fair 6.8ml', 178000, 1, 178000),
 (13, 8, 12, 13, N'Kem dưỡng ẩm CeraVe Moisturising Cream 177ml', 370000, 1, 370000);
SET IDENTITY_INSERT dbo.order_items OFF;
/* ============================================================================
   18. payments (cong thanh toan Mock/mo phong - khong co giao dich that; ma giao dich MOCK-SEED-*)
   ========================================================================== */
SET IDENTITY_INSERT dbo.payments ON;
INSERT INTO dbo.payments (payment_id, order_id, method, amount, status, transaction_code, paid_at, refund_status, refund_amount, refund_requested_at, refunded_at, refund_reason, created_at) VALUES
 (1, 1, N'ONLINE', 524000, N'SUCCESS', N'MOCK-SEED-0001', DATEADD(DAY, -14, SYSDATETIME()), N'NONE', NULL, NULL, NULL, NULL, DATEADD(DAY, -14, SYSDATETIME())),
 (2, 2, N'ONLINE', 1076000, N'SUCCESS', N'MOCK-SEED-0002', DATEADD(DAY, -10, SYSDATETIME()), N'NONE', NULL, NULL, NULL, NULL, DATEADD(DAY, -10, SYSDATETIME())),
 (3, 3, N'ONLINE', 1745000, N'SUCCESS', N'MOCK-SEED-0003', DATEADD(DAY, -7, SYSDATETIME()), N'NONE', NULL, NULL, NULL, NULL, DATEADD(DAY, -7, SYSDATETIME())),
 (4, 4, N'ONLINE', 340000, N'SUCCESS', N'MOCK-SEED-0004', DATEADD(DAY, -5, SYSDATETIME()), N'NONE', NULL, NULL, NULL, NULL, DATEADD(DAY, -5, SYSDATETIME())),
 (5, 5, N'PAY_AT_STORE', 142000, N'SUCCESS', NULL, DATEADD(DAY, -3, SYSDATETIME()), N'NONE', NULL, NULL, NULL, NULL, DATEADD(DAY, -4, SYSDATETIME())),
 (6, 6, N'COD', 306000, N'PENDING', NULL, NULL, N'NONE', NULL, NULL, NULL, NULL, DATEADD(DAY, -2, SYSDATETIME())),
 (7, 7, N'ONLINE', 388000, N'SUCCESS', N'MOCK-SEED-0007', DATEADD(DAY, -1, SYSDATETIME()), N'SIMULATED_REFUNDED', 388000, DATEADD(HOUR, -20, SYSDATETIME()), DATEADD(HOUR, -20, SYSDATETIME()), N'Khách hàng hủy đơn hợp lệ trước chuẩn bị (hoàn tiền mô phỏng)', DATEADD(DAY, -1, SYSDATETIME())),
 (8, 8, N'ONLINE', 400000, N'PENDING', NULL, NULL, N'NONE', NULL, NULL, NULL, NULL, DATEADD(HOUR, -2, SYSDATETIME()));
SET IDENTITY_INSERT dbo.payments OFF;
/* ============================================================================
   19. inventory_movements (so cai ton kho mo phong, nhat quan so hoc voi store_products)
   ========================================================================== */
SET IDENTITY_INSERT dbo.inventory_movements ON;
INSERT INTO dbo.inventory_movements (movement_id, store_product_id, type, quantity_change, quantity_before, quantity_after, reference_order_id, staff_id, note, created_at) VALUES
 (1, 1, N'STOCK_ADJUST', +7, 35, 42, NULL, 2, N'Kiểm kê định kỳ tháng 10 - nhập bù (số liệu mô phỏng)', DATEADD(DAY, -15, SYSDATETIME())),
 (2, 1, N'ORDER', -2, 42, 40, 1, NULL, N'Đơn hàng #1 - Trừ tồn Sữa rửa mặt Simple Purifying Gel Wash 150ml', DATEADD(DAY, -14, SYSDATETIME())),
 (3, 2, N'ORDER', -1, 13, 12, 1, NULL, N'Đơn hàng #1 - Trừ tồn Son bóng Maybelline Lifter Gloss 003 Moon 5.4ml', DATEADD(DAY, -14, SYSDATETIME())),
 (4, 3, N'ORDER', -1, 21, 20, 2, NULL, N'Đơn hàng #2 - Trừ tồn Kem dưỡng ẩm Cetaphil Moisturising Cream 453g', DATEADD(DAY, -10, SYSDATETIME())),
 (5, 4, N'ORDER', -2, 32, 30, 2, NULL, N'Đơn hàng #2 - Trừ tồn Son kem lì Maybelline SuperStay Matte Ink 20 Pioneer 5ml', DATEADD(DAY, -10, SYSDATETIME())),
 (6, 5, N'ORDER', -1, 16, 15, 3, NULL, N'Đơn hàng #3 - Trừ tồn Kem dưỡng Bioderma Sébium Pore Refiner 30ml', DATEADD(DAY, -7, SYSDATETIME())),
 (7, 6, N'ORDER', -1, 19, 18, 3, NULL, N'Đơn hàng #3 - Trừ tồn Kem dưỡng ẩm Bioderma Atoderm Intensive Baume 500ml', DATEADD(DAY, -7, SYSDATETIME())),
 (8, 7, N'ORDER', -1, 26, 25, 3, NULL, N'Đơn hàng #3 - Trừ tồn Gel rửa mặt La Roche-Posay Effaclar Micro-Peeling Purifying Gel 200ml', DATEADD(DAY, -7, SYSDATETIME())),
 (9, 8, N'ORDER', -1, 23, 22, 4, NULL, N'Đơn hàng #4 - Trừ tồn Sữa rửa mặt CeraVe Hydrating Cleanser 236ml', DATEADD(DAY, -5, SYSDATETIME())),
 (10, 9, N'ORDER', -2, 37, 35, 5, NULL, N'Đơn hàng #5 - Trừ tồn Son dưỡng môi Vaseline Lip Therapy Original 4.8g', DATEADD(DAY, -4, SYSDATETIME())),
 (11, 10, N'ORDER', -2, 55, 53, 6, NULL, N'Đơn hàng #6 - Trừ tồn Sữa rửa mặt Simple Kind To Skin Refreshing Facial Wash Gel 150ml', DATEADD(DAY, -2, SYSDATETIME())),
 (12, 11, N'ORDER', -1, 25, 24, 7, NULL, N'Đơn hàng #7 - Trừ tồn Sữa rửa mặt CeraVe Foaming Cleanser 88ml', DATEADD(DAY, -1, SYSDATETIME())),
 (13, 12, N'ORDER', -1, 18, 17, 7, NULL, N'Đơn hàng #7 - Trừ tồn Kem che khuyết điểm Maybelline Fit Me Concealer 15 Fair 6.8ml', DATEADD(DAY, -1, SYSDATETIME())),
 (14, 13, N'ORDER', -1, 15, 14, 8, NULL, N'Đơn hàng #8 - Trừ tồn Kem dưỡng ẩm CeraVe Moisturising Cream 177ml', DATEADD(HOUR, -2, SYSDATETIME())),
 (15, 11, N'CANCEL_ORDER', +1, 24, 25, 7, NULL, N'Hủy đơn #7 - Hoàn tồn kho Sữa rửa mặt CeraVe Foaming Cleanser 88ml', DATEADD(HOUR, -20, SYSDATETIME())),
 (16, 12, N'CANCEL_ORDER', +1, 17, 18, 7, NULL, N'Hủy đơn #7 - Hoàn tồn kho Kem che khuyết điểm Maybelline Fit Me Concealer 15 Fair 6.8ml', DATEADD(HOUR, -20, SYSDATETIME()));
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
   21. reviews - DU LIEU MO PHONG (tai khoan demo @example.com), danh dau "[Du lieu mo phong]"; khong phai danh gia cua khach hang that
   ========================================================================== */
SET IDENTITY_INSERT dbo.reviews ON;
INSERT INTO dbo.reviews (review_id, user_id, product_id, order_item_id, rating, comment, status, created_at, order_id, reply, replied_by, replied_at) VALUES
 (1, 7, 11, 1, 5, N'[Dữ liệu mô phỏng] Đóng gói cẩn thận, giao hàng nhanh.', N'ACTIVE', DATEADD(DAY, -12, SYSDATETIME()), 1,
    N'[Dữ liệu mô phỏng] OneShop Thủ Đức cảm ơn bạn đã đánh giá.', 2, DATEADD(DAY, -11, SYSDATETIME())),
 (2, 7, 5, 3, 4, N'[Dữ liệu mô phỏng] Sản phẩm đúng mô tả, hộp nguyên vẹn.', N'ACTIVE', DATEADD(DAY, -8, SYSDATETIME()), 2,
    N'[Dữ liệu mô phỏng] Cảm ơn bạn đã phản hồi.', 2, DATEADD(DAY, -7, SYSDATETIME())),
 (3, 9, 21, 9, 5, N'[Dữ liệu mô phỏng] Nhận hàng tại cửa hàng Gò Vấp nhanh, nhân viên hỗ trợ tốt.', N'ACTIVE', DATEADD(DAY, -2, SYSDATETIME()), 5,
    N'[Dữ liệu mô phỏng] OneShop Gò Vấp xin cảm ơn bạn.', 3, DATEADD(DAY, -1, SYSDATETIME()));
SET IDENTITY_INSERT dbo.reviews OFF;
/* ============================================================================
   22. review_replies (phan hoi mo phong cua nhan vien chi nhanh dung tham quyen)
   ========================================================================== */
SET IDENTITY_INSERT dbo.review_replies ON;
INSERT INTO dbo.review_replies (reply_id, review_id, staff_user_id, content, created_at, updated_at) VALUES
 (1, 1, 2, N'[Dữ liệu mô phỏng] OneShop Thủ Đức cảm ơn bạn đã đánh giá.', DATEADD(DAY, -11, SYSDATETIME()), DATEADD(DAY, -11, SYSDATETIME())),
 (2, 2, 2, N'[Dữ liệu mô phỏng] Cảm ơn bạn đã phản hồi.', DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, -7, SYSDATETIME())),
 (3, 3, 3, N'[Dữ liệu mô phỏng] OneShop Gò Vấp xin cảm ơn bạn.', DATEADD(DAY, -1, SYSDATETIME()), DATEADD(DAY, -1, SYSDATETIME()));
SET IDENTITY_INSERT dbo.review_replies OFF;
/* ============================================================================
   18b. payment_refunds (OPEN-10.1): the refund ledger mirrors every refund recorded on payments.refund_*
   ========================================================================== */
INSERT INTO dbo.payment_refunds (payment_id, order_id, fulfillment_id, component, amount, status, reason,
                                 requested_by_user_id, requested_at, refunded_at)
SELECT p.payment_id, p.order_id, NULL, N'WHOLE_ORDER', COALESCE(p.refund_amount, p.amount), p.refund_status,
       COALESCE(p.refund_reason, N'Hoàn tiền mô phỏng (dữ liệu có sẵn)'), NULL,
       COALESCE(p.refund_requested_at, p.created_at),
       CASE WHEN p.refund_status = N'SIMULATED_REFUNDED'
            THEN COALESCE(p.refunded_at, p.refund_requested_at, p.created_at) END
FROM dbo.payments p
WHERE p.refund_status <> N'NONE' AND COALESCE(p.refund_amount, p.amount) > 0;

/* ============================================================================
   Kiem tra du lieu ngay trong giao dich: neu vi pham bat ky, THROW va ROLLBACK toan bo seed.
   ========================================================================== */
IF (SELECT COUNT(*) FROM dbo.products WHERE status = N'ACTIVE') < 20 THROW 50020, 'Expected at least 20 active products.', 1;
IF EXISTS (SELECT 1 FROM dbo.products WHERE price <= 0 AND status = N'ACTIVE') THROW 50021, 'Active product has non-positive price.', 1;
IF EXISTS (SELECT 1 FROM dbo.products p WHERE (SELECT COUNT(*) FROM dbo.product_images i WHERE i.product_id = p.product_id AND i.is_primary = 1) <> 1) THROW 50022, 'Every product needs exactly one primary image.', 1;
IF EXISTS (SELECT 1 FROM dbo.product_images WHERE image_url NOT LIKE N'https://res.cloudinary.com/%' OR public_id NOT LIKE N'oneshop/v3/products/%') THROW 50023, 'Product image is not a OneShop Cloudinary asset.', 1;
IF (SELECT COUNT(*) FROM dbo.products p WHERE p.status = N'ACTIVE' AND NOT EXISTS (SELECT 1 FROM dbo.store_products sp WHERE sp.product_id = p.product_id)) < 1 THROW 50024, 'Expected at least one catalog product that no Store sells yet.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.store_products WHERE quantity = 0 AND status = N'ACTIVE') THROW 50030, 'Scenario D failed: no sold-out Store product.', 1;
IF NOT EXISTS (SELECT product_id FROM dbo.store_products WHERE quantity >= 30 GROUP BY product_id HAVING COUNT(*) >= 3) THROW 50031, 'Scenario E failed: no product with abundant stock in >= 3 Stores.', 1;
IF NOT EXISTS (SELECT product_id FROM dbo.store_products WHERE quantity > 0 GROUP BY product_id HAVING COUNT(*) = 1) THROW 50032, 'Scenario F failed: no single-Store product.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.stores WHERE status = N'INACTIVE') THROW 50033, 'Scenario G failed: no inactive Store.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.stores WHERE delivery_enabled = 0 AND pickup_enabled = 1) OR NOT EXISTS (SELECT 1 FROM dbo.stores WHERE delivery_enabled = 1 AND pickup_enabled = 0) THROW 50034, 'Scenario H failed.', 1;
IF NOT EXISTS (SELECT order_id FROM dbo.order_fulfillments GROUP BY order_id HAVING COUNT(DISTINCT store_id) >= 3) THROW 50041, '3-Store Fulfillment order missing.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items oi JOIN dbo.order_fulfillments f ON f.fulfillment_id = oi.fulfillment_id JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id WHERE f.store_id <> sp.store_id) THROW 50042, 'OrderItem Store differs from its StoreProduct Store.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items oi JOIN dbo.order_fulfillments f ON f.fulfillment_id = oi.fulfillment_id WHERE f.order_id <> oi.order_id) THROW 50043, 'OrderItem order differs from its Fulfillment order.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items oi JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id JOIN dbo.products p ON p.product_id = sp.product_id WHERE oi.unit_price <> p.price) THROW 50044, 'OrderItem unit price differs from the Product price.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items WHERE subtotal <> unit_price * quantity) THROW 50050, 'Order item subtotal mismatch.', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o WHERE o.total_amount <> (SELECT SUM(subtotal) FROM dbo.order_items WHERE order_id = o.order_id) + o.shipping_fee) THROW 50051, 'Order total mismatch.', 1;
IF EXISTS (SELECT 1 FROM dbo.orders WHERE fulfillment_type = N'DELIVERY' AND shipping_fee <> 30000) OR EXISTS (SELECT 1 FROM dbo.orders WHERE fulfillment_type = N'STORE_PICKUP' AND shipping_fee <> 0) THROW 50053, 'Shipping fee rule violated (DELIVERY 30.000, pickup 0).', 1;
IF EXISTS (SELECT 1 FROM dbo.payments p JOIN dbo.orders o ON o.order_id = p.order_id WHERE p.amount <> o.total_amount) THROW 50052, 'Payment amount differs from the order total.', 1;
IF EXISTS (SELECT 1 FROM dbo.payment_refunds r JOIN dbo.payments p ON p.payment_id = r.payment_id WHERE r.status <> N'SIMULATION_FAILED' AND r.amount > p.amount) THROW 50054, 'Refund exceeds the collected amount.', 1;
IF EXISTS (SELECT 1 FROM dbo.inventory_movements WHERE quantity_after <> quantity_before + quantity_change) THROW 50060, 'Inventory movement arithmetic mismatch.', 1;
IF EXISTS (SELECT 1 FROM dbo.store_products sp WHERE sp.quantity <> (SELECT TOP 1 quantity_after FROM dbo.inventory_movements m WHERE m.store_product_id = sp.store_product_id ORDER BY m.movement_id DESC)
           AND EXISTS (SELECT 1 FROM dbo.inventory_movements m WHERE m.store_product_id = sp.store_product_id)) THROW 50061, 'Last InventoryMovement does not equal the StoreProduct quantity.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_fulfillments WHERE fulfillment_type = N'STORE_PICKUP' AND status IN (N'READY_FOR_PICKUP', N'COMPLETED') AND (pickup_code IS NULL OR LEN(pickup_code) < 6)) THROW 50070, 'Pickup Fulfillment without a pickup code.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.orders o JOIN dbo.payments p ON p.order_id = o.order_id WHERE o.order_status = N'CANCELLED' AND p.refund_status = N'SIMULATED_REFUNDED' AND p.refund_amount > 0) THROW 50071, 'Cancelled online order without a simulated refund.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.reviews WHERE order_item_id IS NOT NULL) OR NOT EXISTS (SELECT 1 FROM dbo.review_replies) THROW 50080, 'Reviews / replies missing.', 1;
IF EXISTS (SELECT 1 FROM dbo.reviews WHERE comment NOT LIKE N'[[]Dữ liệu mô phỏng]%') THROW 50081, 'Demo review is not marked as simulated data.', 1;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_not_trusted = 1) OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_not_trusted = 1) THROW 50090, 'Untrusted constraint found.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

PRINT 'OneShop V3 seed loaded successfully (real products + simulated demo data; verified inside the transaction).';
GO
