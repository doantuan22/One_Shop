/* =============================================================================
   OneShop - DEMO data (catalog, branches, demo accounts, opening stock, one promotion).

   NOT for production. Real catalog facts (names, sizes, listing prices) and CC BY-SA licensed packaging photos on
   Cloudinary; branches, accounts and stock are synthetic and labelled as such. Orders, payments and reviews are NOT
   in this file: the opt-in profile `demo-seed` creates them through the application services.

   Prerequisites : database/schema/01_schema.sql, database/seed/01_reference_data.sql
   Usage         : sqlcmd -f 65001 -b -d <db> -v SeedPasswordHash="<bcrypt hash>" -i database/seed/20_demo_data.sql
                   (generate the hash with scripts/db/New-PasswordHash.ps1; the plain password is never stored here)
   Safety        : runs in one transaction; refuses a database that holds any other users/stores/products;
                   re-running on a database that already holds exactly this catalog changes nothing.
   Ids are fixed (IDENTITY_INSERT) on purpose: the fixture is only ever loaded into an empty database.
   ============================================================================= */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
DECLARE @passwordHash nvarchar(100)=N'$(SeedPasswordHash)';
IF @passwordHash NOT LIKE N'$2%$%' OR LEN(@passwordHash)<>60 THROW 51202,'Provide the application PasswordEncoder (BCrypt) hash with -v SeedPasswordHash=...',1;
IF (SELECT COUNT(*) FROM dbo.roles WHERE (role_id=1 AND name=N'CUSTOMER') OR (role_id=2 AND name=N'STAFF') OR (role_id=3 AND name=N'ADMIN'))<>3
    THROW 51205,'Run database/seed/01_reference_data.sql first (roles are missing).',1;
DECLARE @expected TABLE(product_id bigint,category_id bigint,brand_id bigint,sku nvarchar(100),name nvarchar(255),description nvarchar(max),price decimal(18,2),original_price decimal(18,2),status nvarchar(20));
INSERT INTO @expected VALUES
 (1, 1, 1, N'CERAVE-CREAM-340', N'Kem dưỡng ẩm CeraVe Moisturising Cream 340g', N'Kem dưỡng ẩm CeraVe Moisturising Cream dung tích 340g. Theo nhãn bao bì: dành cho da thường đến da khô, chứa 3 ceramide thiết yếu.', 450000, NULL, N'ACTIVE'),
 (2, 1, 1, N'CERAVE-CREAM-177', N'Kem dưỡng ẩm CeraVe Moisturising Cream 177ml', N'Kem dưỡng ẩm CeraVe Moisturising Cream dạng tuýp 177ml. Theo nhãn bao bì: dành cho da khô đến rất khô, chứa 3 ceramide thiết yếu và hyaluronic acid, không mùi.', 370000, NULL, N'ACTIVE'),
 (3, 4, 1, N'CERAVE-FOAM-88', N'Sữa rửa mặt CeraVe Foaming Cleanser 88ml', N'Sữa rửa mặt tạo bọt CeraVe Foaming Cleanser 88ml (3 FL OZ). Theo nhãn bao bì: dành cho da thường đến da dầu, kiểm soát dầu.', 180000, NULL, N'ACTIVE'),
 (4, 4, 1, N'CERAVE-HYDRA-236', N'Sữa rửa mặt CeraVe Hydrating Cleanser 236ml', N'Sữa rửa mặt CeraVe Hydrating Cleanser 236ml. Theo nhãn bao bì: dành cho da thường đến da khô, làm sạch và giúp phục hồi hàng rào bảo vệ da, chứa 3 ceramide thiết yếu và hyaluronic acid.', 340000, NULL, N'ACTIVE'),
 (5, 1, 2, N'CETAPHIL-CREAM-453', N'Kem dưỡng ẩm Cetaphil Moisturising Cream 453g', N'Kem dưỡng ẩm Cetaphil Moisturising Cream 453g. Theo nhãn bao bì: dành cho da khô đến rất khô, da nhạy cảm; dưỡng ẩm 48 giờ và giúp phục hồi hàng rào da.', 510000, NULL, N'ACTIVE'),
 (6, 4, 2, N'CETAPHIL-GENTLE-473', N'Sữa rửa mặt Cetaphil Gentle Skin Cleanser 473ml', N'Sữa rửa mặt Cetaphil Gentle Skin Cleanser 473ml (16 FL OZ). Theo nhãn bao bì: dành cho da khô đến thường, da nhạy cảm; chứa glycerin và vitamin B5, B3.', 433000, NULL, N'ACTIVE'),
 (7, 3, 3, N'BIODERMA-ATODERM-500', N'Kem dưỡng ẩm Bioderma Atoderm Intensive Baume 500ml', N'Kem dưỡng Bioderma Atoderm Intensive Baume 500ml. Theo nhãn bao bì: dưỡng làm dịu, phục hồi lipid, dành cho da rất khô, dễ kích ứng đến da cơ địa.', 765000, NULL, N'ACTIVE'),
 (8, 1, 3, N'BIODERMA-SEBIUM-PR-30', N'Kem dưỡng Bioderma Sébium Pore Refiner 30ml', N'Kem dưỡng Bioderma Sébium Pore Refiner 30ml. Theo nhãn bao bì: chăm sóc lỗ chân lông to, dành cho da hỗn hợp đến da dầu.', 435000, NULL, N'ACTIVE'),
 (9, 5, 3, N'BIODERMA-SENSIBIO-250', N'Nước tẩy trang Bioderma Sensibio H2O 250ml', N'Nước tẩy trang Bioderma Sensibio H2O 250ml. Theo nhãn bao bì: dung dịch micellar làm sạch và làm dịu da nhạy cảm, dùng cho mặt và mắt, không cần rửa lại.', 445000, NULL, N'ACTIVE'),
 (10, 4, 4, N'LRP-EFFACLAR-MP-200', N'Gel rửa mặt La Roche-Posay Effaclar Micro-Peeling Purifying Gel 200ml', N'Gel rửa mặt La Roche-Posay Effaclar Micro-Peeling Purifying Gel 200ml. Theo nhãn bao bì: gel làm sạch micro-peeling cho mặt và cơ thể, dành cho da có khuyết điểm dai dẳng.', 515000, NULL, N'ACTIVE'),
 (11, 4, 5, N'SIMPLE-PURIFY-150', N'Sữa rửa mặt Simple Purifying Gel Wash 150ml', N'Gel rửa mặt Simple Purifying Gel Wash 150ml. Theo nhãn bao bì: dành cho da dầu, da dễ nổi mụn; chứa chiết xuất thyme, kẽm và witch hazel; không chứa xà phòng.', 138000, NULL, N'ACTIVE'),
 (12, 4, 5, N'SIMPLE-REFRESH-150', N'Sữa rửa mặt Simple Kind To Skin Refreshing Facial Wash Gel 150ml', N'Gel rửa mặt Simple Kind To Skin Refreshing Facial Wash 150ml. Theo nhãn bao bì: dành cho mọi loại da, 100% không chứa xà phòng, không hương liệu và màu nhân tạo.', 138000, NULL, N'ACTIVE'),
 (13, 4, 5, N'SIMPLE-MICELLAR-150', N'Gel rửa mặt Simple Micellar Gel Wash Water Boost 150ml', N'Gel rửa mặt Simple Micellar Gel Wash Water Boost 150ml. Theo nhãn bao bì: dành cho da khô, cấp ẩm (Pentavitin), 100% không chứa xà phòng.', 138000, NULL, N'ACTIVE'),
 (14, 9, 6, N'MAYB-CONCEALER-15', N'Kem che khuyết điểm Maybelline Fit Me Concealer 15 Fair 6.8ml', N'Kem che khuyết điểm Maybelline Fit Me Concealer, màu 15 Fair, dung tích 6.8ml.', 188000, NULL, N'ACTIVE'),
 (15, 2, 6, N'MAYB-LIFTER-003', N'Son bóng Maybelline Lifter Gloss 003 Moon 5.4ml', N'Son bóng Maybelline Lifter Gloss màu 003 Moon, dung tích 5.4ml. Theo nhãn bao bì: chứa hyaluronic acid.', 218000, NULL, N'ACTIVE'),
 (16, 2, 6, N'MAYB-MATTEINK-15', N'Son kem lì Maybelline SuperStay Matte Ink 15 Lover 5ml', N'Son kem lì Maybelline SuperStay Matte Ink màu 15 Lover, dung tích 5ml.', 205000, NULL, N'ACTIVE'),
 (17, 2, 6, N'MAYB-MATTEINK-20', N'Son kem lì Maybelline SuperStay Matte Ink 20 Pioneer 5ml', N'Son kem lì Maybelline SuperStay Matte Ink màu 20 Pioneer, dung tích 5ml.', 205000, NULL, N'ACTIVE'),
 (18, 2, 6, N'MAYB-MATTEINK-65', N'Son kem lì Maybelline SuperStay Matte Ink 65 Seductress 5ml', N'Son kem lì Maybelline SuperStay Matte Ink màu 65 Seductress, dung tích 5ml.', 268000, NULL, N'ACTIVE'),
 (19, 9, 6, N'MAYB-SS30H-110', N'Kem nền Maybelline SuperStay Active Wear 30H 110 Porcelain 30ml', N'Kem nền Maybelline SuperStay Active Wear lên đến 30 giờ, màu 110 Porcelain, dung tích 30ml.', 298000, NULL, N'ACTIVE'),
 (20, 10, 7, N'VASELINE-LIP-ROSY-7', N'Son dưỡng môi Vaseline Lip Therapy Rosy Lips 7g', N'Sáp dưỡng môi Vaseline Lip Therapy Rosy Lips (môi hồng) dạng hũ nhỏ 7g.', 90000, NULL, N'INACTIVE'),
 (21, 10, 7, N'VASELINE-LIP-ORIG-48', N'Son dưỡng môi Vaseline Lip Therapy Original 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Original (không màu) 4.8g.', 71000, NULL, N'ACTIVE'),
 (22, 10, 7, N'VASELINE-LIP-ALOE-48', N'Son dưỡng môi Vaseline Lip Therapy Aloe Vera 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Aloe Vera (lô hội) 4.8g.', 71000, NULL, N'ACTIVE'),
 (23, 10, 7, N'VASELINE-LIP-COCOA-48', N'Son dưỡng môi Vaseline Lip Therapy Cocoa Butter 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Cocoa Butter (bơ cacao) 4.8g.', 71000, NULL, N'ACTIVE'),
 (24, 10, 7, N'VASELINE-LIP-ROSY-48', N'Son dưỡng môi Vaseline Lip Therapy Rosy Lips 4.8g', N'Son dưỡng môi dạng thỏi Vaseline Lip Therapy Rosy Lips (môi hồng) 4.8g.', 71000, NULL, N'ACTIVE'),
 (25, 3, 7, N'VASELINE-JELLY-COCOA-100', N'Sáp dưỡng ẩm Vaseline Cocoa Butter Moisturising Jelly 100ml', N'Sáp dưỡng ẩm Vaseline Cocoa Butter Moisturising Jelly (Rich Conditioning) dung tích 100ml.', 129000, NULL, N'ACTIVE'),
 (26, 3, 7, N'VASELINE-JELLY-ORIG-100', N'Sáp dưỡng ẩm Vaseline Pure Petroleum Jelly Original 100ml', N'Sáp dưỡng ẩm Vaseline Pure Petroleum Jelly Original dung tích 100ml.', 115000, NULL, N'ACTIVE'),
 (27, 3, 8, N'NIVEA-CREME-150', N'Kem dưỡng ẩm Nivea Creme 150ml', N'Kem dưỡng ẩm Nivea Creme hộp thiếc xanh, dung tích 150ml.', 172000, NULL, N'ACTIVE'),
 (28, 4, 9, N'OS-COSRX-LOWPH-150', N'Gel rửa mặt COSRX Low pH Good Morning Gel Cleanser 150ml', N'Gel rửa mặt độ pH thấp, giúp làm sạch bụi bẩn và dầu thừa. Công thức có dầu tràm trà và betaine salicylate; tuýp 150ml.', 298000, NULL, N'ACTIVE'),
 (29, 8, 9, N'OS-COSRX-BHA-100', N'Dung dịch COSRX BHA Blackhead Power Liquid 100ml', N'Dung dịch chăm sóc da chứa betaine salicylate, hỗ trợ loại bỏ tế bào chết và làm sạch lỗ chân lông. Chai 100ml, dùng sau bước làm sạch.', 598000, NULL, N'ACTIVE'),
 (30, 8, 9, N'OS-COSRX-TONER-C-150', N'Toner COSRX Refresh AHA BHA Vitamin C Daily Toner 150ml', N'Nước cân bằng da COSRX Refresh với AHA, BHA và dẫn xuất vitamin C, hỗ trợ làm mịn bề mặt da. Dạng lỏng, chai 150ml.', 578000, NULL, N'ACTIVE'),
 (31, 6, 9, N'OS-COSRX-PEPTIDE-150', N'Tinh chất COSRX The 6 Peptide Skin Booster Serum 150ml', N'Tinh chất chứa phức hợp peptide, hỗ trợ dưỡng ẩm và làm mịn da. Kết cấu lỏng nhẹ, dùng trong chu trình dưỡng da hằng ngày; chai 150ml.', 690000, NULL, N'ACTIVE'),
 (32, 7, 9, N'OS-COSRX-ALOE-SPF50-50', N'Kem chống nắng COSRX Aloe Soothing Sun Cream SPF50+ PA+++ 50ml', N'Kem chống nắng với chiết xuất lô hội, bảo vệ da với SPF50+ PA+++ và bổ sung độ ẩm. Tuýp 50ml, thoa lại theo hướng dẫn trên bao bì.', 398000, NULL, N'ACTIVE'),
 (33, 7, 9, N'OS-COSRX-ULTRALIGHT-50', N'Kem chống nắng COSRX Ultra-Light Invisible Sunscreen SPF50+ PA++++ 50ml', N'Kem chống nắng COSRX Ultra-Light Invisible Sunscreen, kết cấu nhẹ, chỉ số SPF50+ PA++++. Tuýp 50ml dùng ở bước cuối của chu trình chăm sóc da ban ngày.', 498000, NULL, N'ACTIVE'),
 (34, 6, 10, N'OS-VICHY-M89-50', N'Dưỡng chất Vichy Minéral 89 Skin Fortifying 50ml', N'Dưỡng chất dạng gel chứa nước khoáng Vichy và hyaluronic acid, giúp bổ sung độ ẩm, chăm sóc hàng rào bảo vệ da. Chai 50ml.', 1250000, NULL, N'ACTIVE'),
 (35, 4, 10, N'OS-VICHY-NORMADERM-200', N'Gel rửa mặt Vichy Normaderm Phytosolution Intensive Purifying Gel 200ml', N'Gel rửa mặt Normaderm Phytosolution cho da dầu, giúp loại bỏ bụi bẩn và bã nhờn. Công thức chứa salicylic acid và zinc gluconate; chai 200ml.', 535000, NULL, N'ACTIVE'),
 (36, 4, 10, N'OS-VICHY-NORMADERM-400', N'Gel rửa mặt Vichy Normaderm Phytosolution Intensive Purifying Gel 400ml', N'Gel làm sạch cho da dầu với khoáng chất và salicylic acid, hỗ trợ loại bỏ bã nhờn và bụi bẩn. Chai vòi nhấn 400ml.', 785000, NULL, N'ACTIVE'),
 (37, 7, 11, N'OS-EUCERIN-OILCONTROL-50', N'Kem chống nắng Eucerin Sun Gel-Cream Oil Control Dry Touch SPF50+ 50ml', N'Kem chống nắng dạng gel-cream dành cho da dầu, hỗ trợ kiểm soát bóng nhờn và bảo vệ UVA/UVB với SPF50+. Chai 50ml, phiên bản không màu.', 539000, NULL, N'ACTIVE'),
 (38, 5, 12, N'OS-GARNIER-MICELLAR-PINK-400', N'Nước tẩy trang Garnier SkinActive Micellar Cleansing Water da nhạy cảm 400ml', N'Nước tẩy trang micellar Garnier nắp hồng dành cho da nhạy cảm, giúp làm sạch lớp trang điểm và bụi bẩn. Chai 400ml.', 209000, NULL, N'ACTIVE'),
 (39, 5, 12, N'OS-GARNIER-OILINFUSED-400', N'Nước tẩy trang Garnier SkinActive Micellar Oil-Infused Cleansing Water 400ml', N'Nước tẩy trang hai lớp Garnier Oil-Infused với dầu argan, hỗ trợ làm sạch lớp trang điểm lâu trôi. Chai 400ml; lắc đều trước khi dùng.', 239000, NULL, N'ACTIVE'),
 (40, 5, 3, N'OS-BIODERMA-SEBIUM-500', N'Nước tẩy trang Bioderma Sébium H2O 500ml', N'Nước tẩy trang micellar Bioderma Sébium H2O cho da hỗn hợp và da dầu, giúp làm sạch dầu thừa và lớp trang điểm. Chai 500ml.', 560000, NULL, N'ACTIVE');
IF EXISTS(SELECT 1 FROM dbo.products)
BEGIN
    IF EXISTS(SELECT sku,name,price,status,category_id,brand_id FROM @expected EXCEPT SELECT sku,name,price,status,category_id,brand_id FROM dbo.products)
       OR EXISTS(SELECT sku,name,price,status,category_id,brand_id FROM dbo.products EXCEPT SELECT sku,name,price,status,category_id,brand_id FROM @expected)
        THROW 51203,'Existing catalog differs from the demo catalog; refusing to merge.',1;
    PRINT 'Demo catalog already loaded; no duplicate records created.';
    RETURN;
END;
IF EXISTS(SELECT 1 FROM dbo.users) OR EXISTS(SELECT 1 FROM dbo.stores)
    THROW 51204,'Users or stores already exist (for example the bootstrap administrator); the demo data only loads into an otherwise empty database.',1;
BEGIN TRANSACTION;
SET IDENTITY_INSERT dbo.users ON;
INSERT INTO dbo.users (user_id,role_id,email,user_name,password_hash,full_name,phone,status) VALUES
 (1, 3, N'admin@example.com', N'admin', @passwordHash, N'Nguyễn Hoàng Minh', NULL, N'ACTIVE'),
 (2, 2, N'staff.thuduc@example.com', N'staff_thuduc', @passwordHash, N'Trần Quốc Bảo', NULL, N'ACTIVE'),
 (3, 2, N'staff.govap@example.com', N'staff_govap', @passwordHash, N'Lê Thị Thu Hằng', NULL, N'ACTIVE'),
 (4, 2, N'staff.quan7@example.com', N'staff_quan7', @passwordHash, N'Phạm Đức Khoa', NULL, N'ACTIVE'),
 (5, 2, N'staff.quan10@example.com', N'staff_quan10', @passwordHash, N'Võ Ngọc Anh', NULL, N'ACTIVE'),
 (6, 2, N'staff.binhthanh@example.com', N'staff_binhthanh', @passwordHash, N'Đặng Văn Long', NULL, N'ACTIVE'),
 (7, 1, N'khachhang1@example.com', N'khachhang1', @passwordHash, N'Nguyễn Thị Lan Anh', NULL, N'ACTIVE'),
 (8, 1, N'khachhang2@example.com', N'khachhang2', @passwordHash, N'Trần Minh Khôi', NULL, N'ACTIVE'),
 (9, 1, N'khachhang3@example.com', N'khachhang3', @passwordHash, N'Lê Phương Thảo', NULL, N'ACTIVE'),
 (10, 1, N'khachhang4@example.com', N'khachhang4', @passwordHash, N'Phạm Gia Huy', NULL, N'ACTIVE'),
 (11, 1, N'khachhang5@example.com', N'khachhang5', @passwordHash, N'Hoàng Thu Trang', NULL, N'ACTIVE'),
 (12, 1, N'khachhang6@example.com', N'khachhang6', @passwordHash, N'Vũ Quang Đạt', NULL, N'ACTIVE');
SET IDENTITY_INSERT dbo.users OFF;
SET IDENTITY_INSERT dbo.stores ON;
INSERT INTO dbo.stores (store_id,code,name,address,province_city,area,phone,opening_hours,delivery_enabled,pickup_enabled,status) VALUES
 (1, N'OS-THUDUC', N'OneShop Thủ Đức', N'Điểm mô phỏng OneShop Thủ Đức — địa chỉ kiểm thử, không tiếp khách thực tế', N'TP. Hồ Chí Minh', N'Thủ Đức', NULL, N'08:00 - 22:00 (mô phỏng)', 1, 1, N'ACTIVE'),
 (2, N'OS-GOVAP', N'OneShop Gò Vấp', N'Điểm mô phỏng OneShop Gò Vấp — địa chỉ kiểm thử, không tiếp khách thực tế', N'TP. Hồ Chí Minh', N'Gò Vấp', NULL, N'08:00 - 22:00 (mô phỏng)', 1, 1, N'ACTIVE'),
 (3, N'OS-QUAN7', N'OneShop Quận 7', N'Điểm mô phỏng OneShop Quận 7 — địa chỉ kiểm thử, không tiếp khách thực tế', N'TP. Hồ Chí Minh', N'Quận 7', NULL, N'08:00 - 22:00 (mô phỏng)', 1, 1, N'ACTIVE'),
 (4, N'OS-QUAN10', N'OneShop Quận 10', N'Điểm mô phỏng OneShop Quận 10 — địa chỉ kiểm thử, không tiếp khách thực tế', N'TP. Hồ Chí Minh', N'Quận 10', NULL, N'08:00 - 22:00 (mô phỏng)', 0, 1, N'ACTIVE'),
 (5, N'OS-BINHTHANH', N'OneShop Bình Thạnh', N'Điểm mô phỏng OneShop Bình Thạnh — địa chỉ kiểm thử, không tiếp khách thực tế', N'TP. Hồ Chí Minh', N'Bình Thạnh', NULL, N'08:00 - 22:00 (mô phỏng)', 1, 0, N'INACTIVE');
SET IDENTITY_INSERT dbo.stores OFF;
SET IDENTITY_INSERT dbo.staff_store_assignments ON;
INSERT INTO dbo.staff_store_assignments (assignment_id,user_id,store_id,status) VALUES
 (1, 2, 1, N'ACTIVE'),
 (2, 3, 2, N'ACTIVE'),
 (3, 4, 3, N'ACTIVE'),
 (4, 5, 4, N'ACTIVE'),
 (5, 6, 5, N'INACTIVE');
SET IDENTITY_INSERT dbo.staff_store_assignments OFF;
SET IDENTITY_INSERT dbo.categories ON;
INSERT INTO dbo.categories (category_id,name,description,image_url,image_public_id,status) VALUES
 (1, N'Kem dưỡng da', N'Danh mục kem dưỡng da', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866338/oneshop/seed/products/cerave-cream-340-0f8c8feb0afa.jpg', NULL, N'ACTIVE'),
 (2, N'Son môi', N'Danh mục son môi', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866367/oneshop/seed/products/mayb-lifter-003-8bfb4c5d478f.jpg', NULL, N'ACTIVE'),
 (3, N'Chăm sóc cơ thể', N'Danh mục chăm sóc cơ thể', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866350/oneshop/seed/products/bioderma-atoderm-500-fa0eb2e00eaa.jpg', NULL, N'ACTIVE'),
 (4, N'Sữa rửa mặt', N'Danh mục sữa rửa mặt', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866341/oneshop/seed/products/cerave-foam-88-bbf62028b091.jpg', NULL, N'ACTIVE'),
 (5, N'Tẩy trang', N'Danh mục tẩy trang', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866354/oneshop/seed/products/bioderma-sensibio-250-b2f51c4f12d9.jpg', NULL, N'ACTIVE'),
 (6, N'Serum & tinh chất', N'Danh mục serum & tinh chất', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866409/oneshop/seed/products/os-cosrx-peptide-150-338e3018d36b.jpg', NULL, N'ACTIVE'),
 (7, N'Kem chống nắng', N'Danh mục kem chống nắng', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866412/oneshop/seed/products/os-cosrx-aloe-spf50-50-c5261d821b0d.jpg', NULL, N'ACTIVE'),
 (8, N'Toner & tẩy tế bào chết', N'Danh mục toner & tẩy tế bào chết', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866404/oneshop/seed/products/os-cosrx-bha-100-42916f840f68.jpg', NULL, N'ACTIVE'),
 (9, N'Kem nền & che khuyết điểm', N'Danh mục kem nền & che khuyết điểm', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866366/oneshop/seed/products/mayb-concealer-15-35c51e331498.jpg', NULL, N'ACTIVE'),
 (10, N'Dưỡng môi', N'Danh mục dưỡng môi', N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866382/oneshop/seed/products/vaseline-lip-orig-48-b7ba522fa74c.jpg', NULL, N'ACTIVE');
SET IDENTITY_INSERT dbo.categories OFF;
SET IDENTITY_INSERT dbo.brands ON;
INSERT INTO dbo.brands (brand_id,name,description,logo_url,logo_public_id,status) VALUES
 (1, N'CeraVe', N'Thương hiệu mỹ phẩm CeraVe', NULL, NULL, N'ACTIVE'),
 (2, N'Cetaphil', N'Thương hiệu mỹ phẩm Cetaphil', NULL, NULL, N'ACTIVE'),
 (3, N'Bioderma', N'Thương hiệu mỹ phẩm Bioderma', NULL, NULL, N'ACTIVE'),
 (4, N'La Roche-Posay', N'Thương hiệu mỹ phẩm La Roche-Posay', NULL, NULL, N'ACTIVE'),
 (5, N'Simple', N'Thương hiệu mỹ phẩm Simple', NULL, NULL, N'ACTIVE'),
 (6, N'Maybelline New York', N'Thương hiệu mỹ phẩm Maybelline New York', NULL, NULL, N'ACTIVE'),
 (7, N'Vaseline', N'Thương hiệu mỹ phẩm Vaseline', NULL, NULL, N'ACTIVE'),
 (8, N'Nivea', N'Thương hiệu mỹ phẩm Nivea', NULL, NULL, N'ACTIVE'),
 (9, N'COSRX', N'Thương hiệu mỹ phẩm COSRX', NULL, NULL, N'ACTIVE'),
 (10, N'Vichy', N'Thương hiệu mỹ phẩm Vichy', NULL, NULL, N'ACTIVE'),
 (11, N'Eucerin', N'Thương hiệu mỹ phẩm Eucerin', NULL, NULL, N'ACTIVE'),
 (12, N'Garnier', N'Thương hiệu mỹ phẩm Garnier', NULL, NULL, N'ACTIVE');
SET IDENTITY_INSERT dbo.brands OFF;
SET IDENTITY_INSERT dbo.products ON;
INSERT INTO dbo.products (product_id,category_id,brand_id,sku,name,description,price,original_price,status) SELECT * FROM @expected;
SET IDENTITY_INSERT dbo.products OFF;
SET IDENTITY_INSERT dbo.product_images ON;
INSERT INTO dbo.product_images (image_id,product_id,image_url,public_id,is_primary,sort_order) VALUES
 (1, 1, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866338/oneshop/seed/products/cerave-cream-340-0f8c8feb0afa.jpg', N'oneshop/seed/products/cerave-cream-340-0f8c8feb0afa', 1, 0),
 (2, 2, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866339/oneshop/seed/products/cerave-cream-177-e6ccabcbbf90.jpg', N'oneshop/seed/products/cerave-cream-177-e6ccabcbbf90', 1, 0),
 (3, 3, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866341/oneshop/seed/products/cerave-foam-88-bbf62028b091.jpg', N'oneshop/seed/products/cerave-foam-88-bbf62028b091', 1, 0),
 (4, 4, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866343/oneshop/seed/products/cerave-hydra-236-d5ba76a797e3.jpg', N'oneshop/seed/products/cerave-hydra-236-d5ba76a797e3', 1, 0),
 (5, 5, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866345/oneshop/seed/products/cetaphil-cream-453-c949a5dc3d11.jpg', N'oneshop/seed/products/cetaphil-cream-453-c949a5dc3d11', 1, 0),
 (6, 6, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866348/oneshop/seed/products/cetaphil-gentle-473-32551d961195.jpg', N'oneshop/seed/products/cetaphil-gentle-473-32551d961195', 1, 0),
 (7, 7, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866350/oneshop/seed/products/bioderma-atoderm-500-fa0eb2e00eaa.jpg', N'oneshop/seed/products/bioderma-atoderm-500-fa0eb2e00eaa', 1, 0),
 (8, 8, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866352/oneshop/seed/products/bioderma-sebium-pr-30-92af0ed74243.jpg', N'oneshop/seed/products/bioderma-sebium-pr-30-92af0ed74243', 1, 0),
 (9, 9, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866354/oneshop/seed/products/bioderma-sensibio-250-b2f51c4f12d9.jpg', N'oneshop/seed/products/bioderma-sensibio-250-b2f51c4f12d9', 1, 0),
 (10, 10, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866357/oneshop/seed/products/lrp-effaclar-mp-200-9f7c099c91d1.jpg', N'oneshop/seed/products/lrp-effaclar-mp-200-9f7c099c91d1', 1, 0),
 (11, 11, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866359/oneshop/seed/products/simple-purify-150-76a3ad27fd95.jpg', N'oneshop/seed/products/simple-purify-150-76a3ad27fd95', 1, 0),
 (12, 12, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866362/oneshop/seed/products/simple-refresh-150-c17de535cd18.jpg', N'oneshop/seed/products/simple-refresh-150-c17de535cd18', 1, 0),
 (13, 13, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866364/oneshop/seed/products/simple-micellar-150-8f202b5494ea.jpg', N'oneshop/seed/products/simple-micellar-150-8f202b5494ea', 1, 0),
 (14, 14, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866366/oneshop/seed/products/mayb-concealer-15-35c51e331498.jpg', N'oneshop/seed/products/mayb-concealer-15-35c51e331498', 1, 0),
 (15, 15, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866367/oneshop/seed/products/mayb-lifter-003-8bfb4c5d478f.jpg', N'oneshop/seed/products/mayb-lifter-003-8bfb4c5d478f', 1, 0),
 (16, 16, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866371/oneshop/seed/products/mayb-matteink-15-52346ab0c43d.jpg', N'oneshop/seed/products/mayb-matteink-15-52346ab0c43d', 1, 0),
 (17, 17, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866373/oneshop/seed/products/mayb-matteink-20-f2e139788b15.jpg', N'oneshop/seed/products/mayb-matteink-20-f2e139788b15', 1, 0),
 (18, 18, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866375/oneshop/seed/products/mayb-matteink-65-d32fb3080da5.jpg', N'oneshop/seed/products/mayb-matteink-65-d32fb3080da5', 1, 0),
 (19, 19, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866377/oneshop/seed/products/mayb-ss30h-110-44e562d1669b.jpg', N'oneshop/seed/products/mayb-ss30h-110-44e562d1669b', 1, 0),
 (20, 20, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866379/oneshop/seed/products/vaseline-lip-rosy-7-c2d1fd82d038.jpg', N'oneshop/seed/products/vaseline-lip-rosy-7-c2d1fd82d038', 1, 0),
 (21, 21, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866382/oneshop/seed/products/vaseline-lip-orig-48-b7ba522fa74c.jpg', N'oneshop/seed/products/vaseline-lip-orig-48-b7ba522fa74c', 1, 0),
 (22, 22, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866384/oneshop/seed/products/vaseline-lip-aloe-48-af4281876379.jpg', N'oneshop/seed/products/vaseline-lip-aloe-48-af4281876379', 1, 0),
 (23, 23, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866386/oneshop/seed/products/vaseline-lip-cocoa-48-610615414556.jpg', N'oneshop/seed/products/vaseline-lip-cocoa-48-610615414556', 1, 0),
 (24, 24, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866389/oneshop/seed/products/vaseline-lip-rosy-48-da20a0f42eac.jpg', N'oneshop/seed/products/vaseline-lip-rosy-48-da20a0f42eac', 1, 0),
 (25, 25, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866391/oneshop/seed/products/vaseline-jelly-cocoa-100-3b100652e812.jpg', N'oneshop/seed/products/vaseline-jelly-cocoa-100-3b100652e812', 1, 0),
 (26, 26, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866393/oneshop/seed/products/vaseline-jelly-orig-100-df696bfe2ddc.jpg', N'oneshop/seed/products/vaseline-jelly-orig-100-df696bfe2ddc', 1, 0),
 (27, 27, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866397/oneshop/seed/products/nivea-creme-150-18c9d8324ace.jpg', N'oneshop/seed/products/nivea-creme-150-18c9d8324ace', 1, 0),
 (28, 28, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866401/oneshop/seed/products/os-cosrx-lowph-150-76371a76a822.jpg', N'oneshop/seed/products/os-cosrx-lowph-150-76371a76a822', 1, 0),
 (29, 29, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866404/oneshop/seed/products/os-cosrx-bha-100-42916f840f68.jpg', N'oneshop/seed/products/os-cosrx-bha-100-42916f840f68', 1, 0),
 (30, 30, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866407/oneshop/seed/products/os-cosrx-toner-c-150-c5c5e74e35b8.jpg', N'oneshop/seed/products/os-cosrx-toner-c-150-c5c5e74e35b8', 1, 0),
 (31, 31, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866409/oneshop/seed/products/os-cosrx-peptide-150-338e3018d36b.jpg', N'oneshop/seed/products/os-cosrx-peptide-150-338e3018d36b', 1, 0),
 (32, 32, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866412/oneshop/seed/products/os-cosrx-aloe-spf50-50-c5261d821b0d.jpg', N'oneshop/seed/products/os-cosrx-aloe-spf50-50-c5261d821b0d', 1, 0),
 (33, 33, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866414/oneshop/seed/products/os-cosrx-ultralight-50-262b3aff6011.jpg', N'oneshop/seed/products/os-cosrx-ultralight-50-262b3aff6011', 1, 0),
 (34, 34, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866416/oneshop/seed/products/os-vichy-m89-50-fcb3095cf4c8.jpg', N'oneshop/seed/products/os-vichy-m89-50-fcb3095cf4c8', 1, 0),
 (35, 35, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866419/oneshop/seed/products/os-vichy-normaderm-200-03d83c996e83.jpg', N'oneshop/seed/products/os-vichy-normaderm-200-03d83c996e83', 1, 0),
 (36, 36, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866423/oneshop/seed/products/os-vichy-normaderm-400-90ae81e3e2f1.jpg', N'oneshop/seed/products/os-vichy-normaderm-400-90ae81e3e2f1', 1, 0),
 (37, 37, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866426/oneshop/seed/products/os-eucerin-oilcontrol-50-148a2ff7f7cb.jpg', N'oneshop/seed/products/os-eucerin-oilcontrol-50-148a2ff7f7cb', 1, 0),
 (38, 38, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866428/oneshop/seed/products/os-garnier-micellar-pink-400-0f8e71320c3c.jpg', N'oneshop/seed/products/os-garnier-micellar-pink-400-0f8e71320c3c', 1, 0),
 (39, 39, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866430/oneshop/seed/products/os-garnier-oilinfused-400-6ddf55775f22.jpg', N'oneshop/seed/products/os-garnier-oilinfused-400-6ddf55775f22', 1, 0),
 (40, 40, N'https://res.cloudinary.com/dfyfpuguj/image/upload/c_pad,w_800,h_800,b_white,f_auto,q_auto/v1792866434/oneshop/seed/products/os-bioderma-sebium-500-ea96b99187ec.jpg', N'oneshop/seed/products/os-bioderma-sebium-500-ea96b99187ec', 1, 0);
SET IDENTITY_INSERT dbo.product_images OFF;
SET IDENTITY_INSERT dbo.store_products ON;
INSERT INTO dbo.store_products (store_product_id,store_id,product_id,price,quantity,reserved_quantity,status) VALUES
 (1, 1, 1, NULL, 26, 0, N'ACTIVE'),
 (2, 2, 1, NULL, 37, 0, N'ACTIVE'),
 (3, 3, 1, NULL, 48, 0, N'ACTIVE'),
 (4, 1, 2, NULL, 33, 0, N'ACTIVE'),
 (5, 1, 3, NULL, 40, 0, N'ACTIVE'),
 (6, 2, 3, NULL, 51, 0, N'ACTIVE'),
 (7, 3, 3, NULL, 9, 0, N'ACTIVE'),
 (8, 4, 3, NULL, 20, 0, N'ACTIVE'),
 (9, 5, 3, NULL, 31, 0, N'INACTIVE'),
 (10, 1, 4, NULL, 47, 0, N'ACTIVE'),
 (11, 2, 4, NULL, 58, 0, N'ACTIVE'),
 (12, 3, 4, NULL, 16, 0, N'ACTIVE'),
 (13, 4, 4, NULL, 27, 0, N'ACTIVE'),
 (14, 5, 4, NULL, 38, 0, N'INACTIVE'),
 (15, 1, 5, NULL, 54, 0, N'ACTIVE'),
 (16, 2, 5, NULL, 12, 0, N'ACTIVE'),
 (17, 3, 5, NULL, 23, 0, N'ACTIVE'),
 (18, 1, 6, NULL, 8, 0, N'ACTIVE'),
 (19, 2, 6, NULL, 19, 0, N'ACTIVE'),
 (20, 3, 6, NULL, 30, 0, N'ACTIVE'),
 (21, 4, 6, NULL, 41, 0, N'ACTIVE'),
 (22, 5, 6, NULL, 52, 0, N'INACTIVE'),
 (23, 1, 7, NULL, 15, 0, N'ACTIVE'),
 (24, 2, 7, NULL, 26, 0, N'ACTIVE'),
 (25, 3, 7, NULL, 2, 0, N'ACTIVE'),
 (26, 1, 8, NULL, 22, 0, N'ACTIVE'),
 (27, 2, 8, NULL, 33, 0, N'ACTIVE'),
 (28, 3, 8, NULL, 44, 0, N'ACTIVE'),
 (29, 2, 9, NULL, 40, 0, N'ACTIVE'),
 (30, 1, 10, NULL, 36, 0, N'ACTIVE'),
 (31, 2, 10, NULL, 47, 0, N'ACTIVE'),
 (32, 3, 10, NULL, 58, 0, N'ACTIVE'),
 (33, 4, 10, NULL, 16, 0, N'ACTIVE'),
 (34, 5, 10, NULL, 27, 0, N'INACTIVE'),
 (35, 1, 11, NULL, 43, 0, N'ACTIVE'),
 (36, 2, 11, NULL, 54, 0, N'ACTIVE'),
 (37, 3, 11, NULL, 12, 0, N'ACTIVE'),
 (38, 4, 11, NULL, 23, 0, N'ACTIVE'),
 (39, 5, 11, NULL, 34, 0, N'INACTIVE'),
 (40, 1, 12, NULL, 50, 0, N'ACTIVE'),
 (41, 2, 12, NULL, 8, 0, N'ACTIVE'),
 (42, 3, 12, NULL, 19, 0, N'ACTIVE'),
 (43, 4, 12, NULL, 30, 0, N'ACTIVE'),
 (44, 5, 12, NULL, 41, 0, N'INACTIVE'),
 (45, 1, 14, NULL, 11, 0, N'ACTIVE'),
 (46, 2, 14, NULL, 22, 0, N'ACTIVE'),
 (47, 3, 14, NULL, 2, 0, N'ACTIVE'),
 (48, 1, 15, NULL, 18, 0, N'ACTIVE'),
 (49, 2, 15, NULL, 29, 0, N'ACTIVE'),
 (50, 3, 15, NULL, 40, 0, N'ACTIVE'),
 (51, 4, 15, NULL, 51, 0, N'ACTIVE'),
 (52, 5, 15, NULL, 9, 0, N'INACTIVE'),
 (53, 1, 16, NULL, 25, 0, N'ACTIVE'),
 (54, 2, 16, NULL, 36, 0, N'ACTIVE'),
 (55, 3, 16, NULL, 47, 0, N'ACTIVE'),
 (56, 1, 17, NULL, 32, 0, N'ACTIVE'),
 (57, 2, 17, NULL, 43, 0, N'ACTIVE'),
 (58, 3, 17, NULL, 54, 0, N'ACTIVE'),
 (59, 1, 18, NULL, 39, 0, N'ACTIVE'),
 (60, 2, 18, NULL, 50, 0, N'ACTIVE'),
 (61, 3, 18, NULL, 8, 0, N'ACTIVE'),
 (62, 4, 18, NULL, 19, 0, N'ACTIVE'),
 (63, 5, 18, NULL, 30, 0, N'INACTIVE'),
 (64, 1, 19, NULL, 0, 0, N'ACTIVE'),
 (65, 2, 19, NULL, 0, 0, N'ACTIVE'),
 (66, 3, 19, NULL, 0, 0, N'ACTIVE'),
 (67, 1, 20, NULL, 0, 0, N'INACTIVE'),
 (68, 2, 20, NULL, 0, 0, N'INACTIVE'),
 (69, 3, 20, NULL, 0, 0, N'INACTIVE'),
 (70, 4, 20, NULL, 0, 0, N'INACTIVE'),
 (71, 5, 20, NULL, 0, 0, N'INACTIVE'),
 (72, 1, 21, NULL, 60, 0, N'ACTIVE'),
 (73, 2, 21, NULL, 18, 0, N'ACTIVE'),
 (74, 3, 21, NULL, 2, 0, N'ACTIVE'),
 (75, 4, 21, NULL, 40, 0, N'ACTIVE'),
 (76, 5, 21, NULL, 51, 0, N'INACTIVE'),
 (77, 1, 22, NULL, 14, 0, N'ACTIVE'),
 (78, 2, 22, NULL, 25, 0, N'ACTIVE'),
 (79, 3, 22, NULL, 36, 0, N'ACTIVE'),
 (80, 4, 22, NULL, 47, 0, N'ACTIVE'),
 (81, 5, 22, NULL, 58, 0, N'INACTIVE'),
 (82, 1, 23, NULL, 21, 0, N'ACTIVE'),
 (83, 2, 23, NULL, 32, 0, N'ACTIVE'),
 (84, 3, 23, NULL, 43, 0, N'ACTIVE'),
 (85, 4, 23, NULL, 54, 0, N'ACTIVE'),
 (86, 5, 23, NULL, 12, 0, N'INACTIVE'),
 (87, 1, 24, NULL, 28, 0, N'ACTIVE'),
 (88, 2, 24, NULL, 39, 0, N'ACTIVE'),
 (89, 3, 24, NULL, 50, 0, N'ACTIVE'),
 (90, 4, 24, NULL, 8, 0, N'ACTIVE'),
 (91, 5, 24, NULL, 19, 0, N'INACTIVE'),
 (92, 1, 25, NULL, 35, 0, N'ACTIVE'),
 (93, 2, 25, NULL, 46, 0, N'ACTIVE'),
 (94, 3, 25, NULL, 57, 0, N'ACTIVE'),
 (95, 1, 26, NULL, 42, 0, N'ACTIVE'),
 (96, 2, 26, NULL, 53, 0, N'ACTIVE'),
 (97, 3, 26, NULL, 11, 0, N'ACTIVE'),
 (98, 3, 27, NULL, 18, 0, N'ACTIVE'),
 (99, 1, 28, NULL, 56, 0, N'ACTIVE'),
 (100, 2, 28, NULL, 14, 0, N'ACTIVE'),
 (101, 3, 28, NULL, 2, 0, N'ACTIVE'),
 (102, 4, 28, NULL, 36, 0, N'ACTIVE'),
 (103, 5, 28, NULL, 47, 0, N'INACTIVE'),
 (104, 2, 29, NULL, 1, 0, N'ACTIVE'),
 (105, 1, 30, NULL, 17, 0, N'ACTIVE'),
 (106, 2, 30, NULL, 28, 0, N'ACTIVE'),
 (107, 3, 30, NULL, 39, 0, N'ACTIVE'),
 (108, 4, 30, NULL, 50, 0, N'ACTIVE'),
 (109, 5, 30, NULL, 8, 0, N'INACTIVE'),
 (110, 1, 31, NULL, 24, 0, N'ACTIVE'),
 (111, 2, 31, NULL, 35, 0, N'ACTIVE'),
 (112, 3, 31, NULL, 46, 0, N'ACTIVE'),
 (113, 1, 32, NULL, 31, 0, N'ACTIVE'),
 (114, 2, 32, NULL, 42, 0, N'ACTIVE'),
 (115, 3, 32, NULL, 53, 0, N'ACTIVE'),
 (116, 1, 33, NULL, 38, 0, N'ACTIVE'),
 (117, 2, 33, NULL, 49, 0, N'ACTIVE'),
 (118, 3, 33, NULL, 60, 0, N'ACTIVE'),
 (119, 4, 33, NULL, 18, 0, N'ACTIVE'),
 (120, 5, 33, NULL, 29, 0, N'INACTIVE'),
 (121, 1, 34, NULL, 45, 0, N'ACTIVE'),
 (122, 2, 34, NULL, 56, 0, N'ACTIVE'),
 (123, 3, 34, NULL, 14, 0, N'ACTIVE'),
 (124, 1, 35, NULL, 52, 0, N'ACTIVE'),
 (125, 2, 35, NULL, 10, 0, N'ACTIVE'),
 (126, 3, 35, NULL, 2, 0, N'ACTIVE'),
 (127, 4, 35, NULL, 32, 0, N'ACTIVE'),
 (128, 5, 35, NULL, 43, 0, N'INACTIVE'),
 (129, 1, 36, NULL, 59, 0, N'ACTIVE'),
 (130, 2, 36, NULL, 17, 0, N'ACTIVE'),
 (131, 3, 36, NULL, 28, 0, N'ACTIVE'),
 (132, 4, 36, NULL, 39, 0, N'ACTIVE'),
 (133, 5, 36, NULL, 50, 0, N'INACTIVE'),
 (134, 1, 37, NULL, 13, 0, N'ACTIVE'),
 (135, 2, 37, NULL, 24, 0, N'ACTIVE'),
 (136, 3, 37, NULL, 35, 0, N'ACTIVE'),
 (137, 1, 38, NULL, 20, 0, N'ACTIVE'),
 (138, 2, 38, NULL, 31, 0, N'ACTIVE'),
 (139, 3, 38, NULL, 42, 0, N'ACTIVE'),
 (140, 4, 38, NULL, 53, 0, N'ACTIVE'),
 (141, 5, 38, NULL, 11, 0, N'INACTIVE'),
 (142, 1, 39, NULL, 27, 0, N'ACTIVE'),
 (143, 2, 39, NULL, 38, 0, N'ACTIVE'),
 (144, 3, 39, NULL, 49, 0, N'ACTIVE'),
 (145, 4, 39, NULL, 60, 0, N'ACTIVE'),
 (146, 5, 39, NULL, 18, 0, N'INACTIVE'),
 (147, 1, 40, NULL, 34, 0, N'ACTIVE'),
 (148, 2, 40, NULL, 45, 0, N'ACTIVE'),
 (149, 3, 40, NULL, 56, 0, N'ACTIVE'),
 (150, 4, 40, NULL, 14, 0, N'ACTIVE'),
 (151, 5, 40, NULL, 25, 0, N'INACTIVE');
SET IDENTITY_INSERT dbo.store_products OFF;
INSERT INTO dbo.inventory_movements(store_product_id,type,quantity_change,quantity_before,quantity_after,staff_id,note)
SELECT store_product_id,N'STOCK_ADJUST',quantity,0,quantity,1,N'[Dữ liệu mô phỏng] Nhập tồn đầu kỳ seed đã kiểm tra.' FROM dbo.store_products WHERE quantity>0;
SET IDENTITY_INSERT dbo.customer_addresses ON;
INSERT INTO dbo.customer_addresses (address_id,user_id,receiver_name,receiver_phone,address_line,is_default) VALUES
 (1, 7, N'Nguyễn Thị Lan Anh', N'0900000001', N'Khu giao nhận mô phỏng OneShop, Phường Thủ Đức, TP. Hồ Chí Minh, Việt Nam', 1),
 (2, 8, N'Trần Minh Khôi', N'0900000002', N'Khu giao nhận mô phỏng OneShop, Phường Thủ Đức, TP. Hồ Chí Minh, Việt Nam', 1),
 (3, 9, N'Lê Phương Thảo', N'0900000003', N'Khu giao nhận mô phỏng OneShop, Phường Thủ Đức, TP. Hồ Chí Minh, Việt Nam', 1),
 (4, 10, N'Phạm Gia Huy', N'0900000004', N'Khu giao nhận mô phỏng OneShop, Phường Thủ Đức, TP. Hồ Chí Minh, Việt Nam', 1),
 (5, 11, N'Hoàng Thu Trang', N'0900000005', N'Khu giao nhận mô phỏng OneShop, Phường Thủ Đức, TP. Hồ Chí Minh, Việt Nam', 1),
 (6, 12, N'Vũ Quang Đạt', N'0900000006', N'Khu giao nhận mô phỏng OneShop, Phường Thủ Đức, TP. Hồ Chí Minh, Việt Nam', 1);
SET IDENTITY_INSERT dbo.customer_addresses OFF;
INSERT INTO dbo.promotions(code,name,description,discount_type,discount_value,max_discount_amount,min_order_amount,usage_limit,used_count,per_user_limit,start_at,end_at,status) VALUES
(N'DEMO10',N'Ưu đãi demo 10%',N'[Dữ liệu mô phỏng] Kiểm thử mã ưu đãi.',N'PERCENT',10,50000,200000,100,0,2,DATEADD(DAY,-1,SYSDATETIME()),DATEADD(DAY,60,SYSDATETIME()),N'ACTIVE');
COMMIT TRANSACTION;
PRINT 'Demo catalog, licensed images, demo accounts and opening inventory loaded. Orders are created separately through the application services (profile demo-seed).';
