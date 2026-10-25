# DATABASE INTEGRITY REPORT

## An toàn môi trường
- Mọi lần chạy trỏ DB `oneshop_*_test`; `TestDatabaseSafetyInitializer` từ chối tên khác. Không có lệnh ghi/ALTER/DROP/TRUNCATE nào lên `oneshop`, `oneshop_audit_v2` hay DB test có sẵn.
- DB tạo trong lượt này (disposable): `oneshop_fixture_20261025_test` (schema + seed kịch bản; DB chạy suite) và `oneshop_demoseed_20261025_test` (schema + seed demo; chỉ cho RealProductSeedIntegrityTest/ImageCredits). Hai DB tạm đầu tiên của lượt này (một cái nạp seed bị mojibake) đã DROP; chỉ DROP DB do chính lượt này tạo.
- App thủ công cho browser chạy cổng 8099 với `--spring.datasource.url=…oneshop_fixture_20261025_test`, xác minh bằng `sysprocesses` chỉ kết nối DB đó. Tiến trình live (cổng 8080 / khóa `oneshop.jar`) không bị động tới.

## Schema vs Entity
- `order_items.discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0` + `CK_order_items_discount (0 ≤ discount_amount ≤ subtotal)` có trong DB mới; Hibernate `validate` thành công; 30 bảng, 29 entity/repository.

## Kiểm tra SQL trực tiếp sau toàn bộ suite (`evidence/sql-integrity-fixture.txt`)
| Kiểm tra | Kết quả |
|---|---:|
| FK / CHECK bị tắt hoặc không tin cậy | 0 / 0 |
| `DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS` | 0 vi phạm |
| Tồn kho âm / reserved > quantity | 0 |
| order_items không có fulfillment | 0 |
| payment mồ côi | 0 |
| Order có >1 Payment SUCCESS | 0 |
| Hoàn kho CANCEL_ORDER lặp cho cùng đơn+SKU | 0 |
| Nhân viên có >1 phân công ACTIVE | 0 |
| Số đơn sau suite | 8 (= seed) |

Chưa làm: checksum từng bảng trước/sau toàn suite, thứ tự test ngẫu nhiên.
