# OneShop – Database (Phase 2)

SQL Server schema theo Roadmap V2 mục 7 (19 bảng). Chỉ gồm schema, constraint, index và seed – chưa có code Java.

| Thứ tự | File | Nội dung |
| --- | --- | --- |
| 0 | `00_run_all.sql` | Chạy 01→05 (SQLCMD mode) |
| 1 | `01_create_database.sql` | Tạo database `oneshop` nếu chưa có |
| 2 | `02_create_tables.sql` | Drop + tạo 19 bảng, PK/FK/UNIQUE/CHECK |
| 3 | `03_create_indexes.sql` | 6 index theo Roadmap 7.4 |
| 4 | `04_seed_data.sql` | 5 Store, 7 SKU, StoreProduct, 3 checkout / 5 order, payment, movement, history |
| 5 | `05_verify.sql` | Kiểm tra tự động, `THROW` nếu sai |

## Chạy

Từ thư mục `database/`:

```
sqlcmd -S localhost -E -C -f 65001 -b -i 00_run_all.sql
```

(SQL login: thay `-E` bằng `-U <user> -P <password>`.) Cần `-f 65001` để đọc UTF-8. Chạy lại được nhiều lần: `02` drop/tạo lại bảng, `04` tự dọn dữ liệu cũ. **Lưu ý: chạy lại sẽ xóa dữ liệu hiện có trong database `oneshop`.**

Khớp `application.properties` (`databaseName=oneshop`). Tài khoản demo: mật khẩu `OneShop@123` (BCrypt) – chỉ dùng demo.

## Quy ước tự bổ sung (tài liệu không nêu chi tiết)

Chỉ có 19 bảng của Roadmap 7.2; không thêm bảng nào. Các bảng ngoài 8 bảng có cột trọng tâm ở mục 7.3 dùng cột tối thiểu theo quan hệ mục 7.1:

- **Cột bổ sung:** `roles.description`; `users.full_name/phone/status/created_at/updated_at`; `customer_addresses` (receiver_name, receiver_phone, address_line, is_default, created_at); `staff_store_assignments.status/assigned_at`; `categories.description/status`; `brands.description/logo_url/logo_public_id/status` (Roadmap 3.2: ảnh thương hiệu trên Cloudinary); `product_images` (image_url, public_id, is_primary, sort_order, created_at); `carts`/`cart_items` timestamps; `reviews` (rating, comment, status, created_at); `stores.created_at/updated_at`.
- **Giá trị enum quy ước:** `orders.payment_status` = UNPAID/PAID/FAILED (FAILED phục vụ luồng ONLINE thất bại, Roadmap 6.4); `payments.status` = PENDING/SUCCESS/FAILED; Store/StoreProduct/assignment/user = ACTIVE/INACTIVE; Product = ACTIVE/INACTIVE/HIDDEN; Category/Brand/Review = ACTIVE/HIDDEN. Các enum có trong tài liệu (order_status, fulfillment_type, payment_method, inventory type, checkout status) dùng nguyên văn.
- **CHECK nghiệp vụ dẫn xuất từ tài liệu:** trên `orders` (DELIVERY chỉ COD/ONLINE, STORE_PICKUP chỉ PAY_AT_STORE/ONLINE – Roadmap 6.4; `pickup_*` chỉ STORE_PICKUP – 7.3; READY_FOR_PICKUP có `pickup_code` + `ready_at` – 6.6; PACKED/SHIPPING chỉ DELIVERY – 6.5/6.6; PENDING_PAYMENT chỉ ONLINE; DELIVERY có `shipping_address`); trên `inventory_movements` (before/after >= 0, after = before + change; ORDER âm, CANCEL_ORDER dương và có `reference_order_id`; STOCK_ADJUST có `staff_id` – 6.7/6.8); `order_items` (quantity > 0, subtotal = unit_price × quantity); `payments` (SUCCESS có `paid_at`); `cart_items.quantity > 0`.
- **`order_items.store_product_id` (giữ):** cần để hủy Order hoàn tồn đúng StoreProduct và ghi `InventoryMovement` (BR-12, Roadmap 6.7). Đây là FK bắt buộc cho nghiệp vụ đã chốt, không phải cột dư.
- **`order_items.product_sku` (đã loại):** Roadmap 6.3 chỉ yêu cầu snapshot `product_name, unit_price, quantity, subtotal`; SKU luôn truy được qua `store_product_id → products.sku` và SKU không đổi. Bảng `order_items` còn: order_item_id, order_id, store_product_id, product_name, unit_price, quantity, subtotal.
- **Ngoài phạm vi schema (Service kiểm tra ở phase sau):** item cùng Store với Order, transition trạng thái, Store scope của Staff, chỉ Staff role mới được assignment, đánh giá chỉ sau COMPLETED. `05_verify.sql` chỉ kiểm tra dữ liệu seed.
- **Seed:** chưa có `product_images` (cần URL/public_id Cloudinary thật ở Phase 6).
