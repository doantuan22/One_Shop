# Phase 11 – Audit trước triển khai

Audit ngày 09/10/2026, trước khi bổ sung code Phase 11. Baseline: commit `cf5111e` (Phase 10 DONE).

| Chức năng | Hiện trạng Phase 5/6 | Quyết định Phase 11 |
| --- | --- | --- |
| Authorization | SecurityConfig bảo vệ `/admin/**`, `/api/admin/**` bằng ROLE_ADMIN; JWT đọc User/Role từ DB mỗi request, CSRF form | Giữ nguyên, service Admin mới thêm method security |
| Store / Store Finder | AdminStoreController + StoreService có list/create/update, code/name/address/provinceCity/area/phone/openingHours/deliveryEnabled/pickupEnabled/status | Tái sử dụng hoàn toàn |
| Category / Brand | AdminCategoryController, AdminBrandController + ProductService; status ACTIVE/HIDDEN, logo Cloudinary | Tái sử dụng hoàn toàn |
| Product/SKU / Image | AdminProductController + ProductService, SKU bất biến khi sửa; ảnh qua CloudinaryService, ProductImage chỉ URL/public_id | Tái sử dụng hoàn toàn, không ProductVariant |
| StoreProduct / tồn | AdminStoreProductController + StoreProductService: phân trang, Store/Product filter, SKU/price/exact quantity/status; Store/Product bất biến khi sửa; điều chỉnh qua InventoryService + movement | Tái sử dụng; thêm đường dẫn inventory overview và liên kết theo Store |
| Order | Phase 9 có OrderViewService đọc snapshot/payment/history; chưa có Admin list/detail/filter | Thêm reader Admin toàn hệ thống, dùng lại OrderViewService |
| Staff assignment | Entity/repository/scope resolver có sẵn; chưa có Admin quản lý | Thêm gán/reactivate/soft-unassign, không sửa scope Phase 10 |
| User / Role | Entity + RoleName CUSTOMER/STAFF/ADMIN có sẵn, đăng ký chỉ CUSTOMER; chưa có quản trị | Thêm tạo tài khoản, sửa thông tin/role/status; không expose hash/token |
| Review | Entity/repository có sẵn; ReviewServiceImpl skeleton | Thêm list, ACTIVE/HIDDEN trong phạm vi Admin; không thêm customer review module |
| Overview | AdminDashboardController và template hiện là trang mẫu | Thay dữ liệu mẫu bằng thống kê đơn giản từng Store, gồm Store INACTIVE |

Schema 19 bảng và model Phase 1–10 giữ nguyên. Không thêm nghiệp vụ nhập/chuyển kho, gateway, fulfillment Admin, customer cancellation hoặc Phase 12. Store INACTIVE bị chặn ở catalog/cart/checkout hiện có; lịch sử truy theo Order/Store không lọc ACTIVE. Kiểm chứng lại bằng test Phase 11.
