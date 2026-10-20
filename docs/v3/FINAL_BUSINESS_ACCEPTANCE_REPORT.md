# OneShop V3 — Final Business Acceptance Report

Ngày: 20/10/2026

## 0. Kết luận

> **BUSINESS V3 ACCEPTANCE: PASS**
>
> Chủ dự án đã xác nhận cho phép migration (20/10/2026). Migration `20261020_open101_payment_refunds.sql` đã được áp dụng thành công trên `oneshop` sau khi mọi điều kiện tiên quyết được kiểm tra lại; 22 bảng cũ giống hệt trước migration; `payment_refunds` đúng định nghĩa và đúng 1 bản ghi `WHOLE_ORDER` của Payment 7 (458.000đ); ứng dụng khởi động (worker tắt) và đọc dữ liệu đúng, **không ghi gì**. Các bộ test và E2E được chạy trên bản sao độc lập có schema tương đương (không ghi dữ liệu thử lên `oneshop`).
>
> Phạm vi PASS: nghiệp vụ V3 đã chốt (gồm OPEN-10, OPEN-10.1). Các giới hạn đã biết ở mục 7 được ghi nhận, không chặn nghiệm thu. Giai đoạn phát triển nghiệp vụ V3 kết thúc tại đây; UI/UX chờ yêu cầu riêng.

| Điều kiện nghiệm thu | Trạng thái | Bằng chứng |
| --- | --- | --- |
| Backup đầy đủ + xác minh + khôi phục được | **ĐẠT** | mục 2 |
| Migration thử trên bản sao: chạy được, idempotent, không phá dữ liệu | **ĐẠT** | mục 3 |
| Đối chiếu dữ liệu trước/sau | **ĐẠT** (bản sao và `oneshop` thật) | mục 3, 8 |
| Schema ↔ ứng dụng tương thích (khởi động trên schema sau migration; từ chối khi chưa migrate) | **ĐẠT** | mục 4 |
| Toàn bộ test 0 FAIL / 0 ERROR / 0 SKIPPED | **ĐẠT** (1.653/1.653 trên schema sau migration) | mục 5 |
| 4 role + E2E HTTP + Browser E2E + phân quyền | **ĐẠT** | mục 6 |
| Nhất quán tài chính / tồn kho / trạng thái | **ĐẠT** | mục 6 |
| Migration đã áp dụng trên `oneshop` | **ĐẠT** | mục 8 |

## 1. Trạng thái `oneshop` trước migration (chỉ đọc)

- SQL Server 2025 (17.0.1145.4), `oneshop` ONLINE, recovery FULL. Không có phiên kết nối người dùng nào tới `oneshop`/`oneshop_test` (chỉ tiến trình language server của VS Code, không kết nối CSDL). Không có transaction mở. Worker timeout mặc định **OFF** (`ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=false`), không có scheduled job nào khác ghi dữ liệu.
- Schema: 22 bảng, **chưa có** `payment_refunds`; DEC-26 đã có (`UX_reviews_order_item`, `UX_reviews_legacy_user_product`). FK/CHECK: 0 bị vô hiệu hóa hoặc không tin cậy.
- Dữ liệu: users 10, orders 8, order_fulfillments 12, order_items 13, payments 8 (1 có hoàn tiền: payment 7, 458.000đ, `SIMULATED_REFUNDED`), store_products 36, inventory_movements 16, reviews 3.
- Các đầu dò toàn vẹn đều = 0: đơn không có fulfillment, tồn âm, đơn có >1 payment SUCCESS, hoàn > đã thu, hoàn nhưng đơn không `REFUNDED`.
- Cấu trúc `oneshop` **giống hệt** cài mới từ `01_schema.sql` (trừ `payment_refunds`); dữ liệu cùng số dòng với bộ seed (khác nhau chỉ ở giá trị thời gian do seed dùng ngày tương đối).

**Migration sẽ làm đúng 3 việc:** (1) tạo `dbo.payment_refunds` + khóa ngoại (payment, order, fulfillment, user), CHECK và chỉ mục (PK, 3 unique có lọc, 2 chỉ mục thường); (2) backfill đúng **1** dòng `WHOLE_ORDER` từ `payments.refund_*` của payment 7; (3) kiểm số dòng `payments`/`orders` trước-sau rồi COMMIT (một transaction, `XACT_ABORT ON`, lỗi → tự rollback, không đổi gì). Không sửa/xóa dòng nào của 22 bảng hiện có.

## 2. Backup

- File: `C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\oneshop_pre_open101_20261020.bak` (COPY_ONLY, CHECKSUM, 1.130 trang, tên mới — không ghi đè file nào; **không xóa**).
- `RESTORE VERIFYONLY ... WITH CHECKSUM`: *valid*.
- Khôi phục thật thành `oneshop_mig_copy_20261020_test` (do lần này tạo): **cấu trúc, số dòng và checksum từng bảng giống hệt `oneshop`** (so sánh bằng `docs/v3/acceptance-evidence/fingerprint.sql`).

## 3. Migration thử trên bản sao

| Kiểm tra | Kết quả |
| --- | --- |
| Chạy lần 1 | "1 existing refund(s) recorded in the ledger. Nothing else was changed." |
| Fingerprint 22 bảng gốc trước vs sau (cột, FK, CHECK, chỉ mục, số dòng, checksum nội dung, tổng tiền/tồn) | **Giống hệt** (diff rỗng) |
| Chạy lần 2 (idempotent) | Giống hệt lần 1, vẫn đúng 1 dòng sổ cái |
| Dòng backfill | `WHOLE_ORDER`, 458.000, `SIMULATED_REFUNDED`, khớp `payments.refund_amount/reason/refunded_at` |
| `payment_refunds` so với cài mới (26 đối tượng: cột, FK, CHECK, chỉ mục) | **Giống hệt** |
| Tổng số bảng / FK / CHECK không tin cậy | 23 / 0 / 0 |
| Đối chiếu payments, orders, tồn kho trước/sau | Số dòng + checksum + tổng tiền + tổng tồn **không đổi** |

Bằng chứng: `docs/v3/acceptance-evidence/oneshop_fingerprint_before.txt`, `migrated_copy_fingerprint_after.txt`.

### Phương án khôi phục
1. Migration là một transaction: nếu lỗi giữa chừng, **không có thay đổi nào** được commit.
2. Sau khi áp dụng, nếu phát hiện vấn đề: `20261020_open101_payment_refunds_rollback.sql` chỉ xóa bảng khi bảng chỉ chứa dòng backfill `WHOLE_ORDER` khớp `payments.refund_*` (đã thử: bị chặn bằng THROW 50712 khi có hoàn một phần). Nếu không an toàn → **dừng và báo cáo**, không tự rollback.
3. Khôi phục từ backup: **không** ghi đè `oneshop` tự động. Khôi phục sang tên mới (`RESTORE ... WITH MOVE`, đã chứng minh hoạt động) để đối chiếu; ghi đè `oneshop` chỉ khi chủ dự án chấp thuận riêng.

## 4. Tương thích schema ↔ ứng dụng

- Trên bản sao **chưa** migrate: ứng dụng từ chối khởi động (`Schema-validation: missing table [payment_refunds]`), không ghi gì.
- Trên bản sao **đã** migrate: khởi động, `/health` = UP, log "Payment timeout worker is OFF (default)", đọc dữ liệu cũ đúng (đơn 7 hiển thị hoàn 458.000đ mô phỏng, Payment Console xếp vào "đã hoàn", không sinh "cần xử lý" giả). `ddl-auto=validate` + `V3SchemaCompatibilityVerifier` (3 chỉ mục sổ cái) đạt.

## 5. Bộ test

| Bộ | Kết quả |
| --- | --- |
| `./mvnw test` toàn bộ (Unit, Integration, Concurrency, Security, HTTP) trên **bản sao đã migrate** | **1.653 test — 0 FAIL, 0 ERROR, 0 SKIPPED** (BUILD SUCCESS) |
| Cùng bộ trên DB cài mới (lần chạy trước) | 1.653 — 0/0/0 |
| Không sửa, xóa, skip, nới assertion nào trong đợt nghiệm thu này | — |

Không có thay đổi mã nguồn trong đợt này (chỉ thêm tài liệu, bằng chứng, kịch bản kiểm thử).

## 6. Nghiệm thu nghiệp vụ

Tất cả chạy trên bản sao độc lập (khôi phục từ backup + migration), không trên `oneshop`.

| Nhóm | Phương pháp | Kết quả |
| --- | --- | --- |
| Customer: catalog/giá chung, giỏ chung, phân bổ Store, ONLINE 1 Order/1 Payment/N Fulfillment, COD, pickup, hủy, theo dõi, review | `qa2` (94), `qa5` (93, **bật worker chủ động**: timeout 15', idempotency, hoàn tồn một lần), + 1.653 test | PASS (xem ghi chú) |
| Staff: phân quyền theo Store, chuyển trạng thái, xác minh pickup, tồn/InventoryMovement, báo giao thất bại, không thao tác Store khác | `qa3` (218), `qa4` (33) | PASS (xem ghi chú) |
| Admin: CRUD, giá chung/tồn Store, Dashboard "Cần Admin xử lý", Payment & đối soát, OPEN-10, OPEN-10.1 (RETURNED/LOST/UNCONFIRMED, toàn phần/một phần) | `qa6` (99, worker OFF mặc định, chờ 75s chứng minh không ghi nền), `qa8` (50), `ro` (16 chỉ đọc dữ liệu cũ) | PASS |
| Browser E2E (Chrome headless thật): form Admin, hộp xác nhận, UNCONFIRMED→LOST, trang khách desktop 1440 + phone 390, không tràn ngang, phân quyền 403/404 | `browser_e2e.mjs` | **14/14 PASS** |
| Tính nhất quán | Đầu dò SQL sau E2E: không tồn âm, không đơn >1 payment SUCCESS, tổng sổ cái ≤ đã thu mỗi payment, mọi payment `SIMULATED_REFUNDED` đều có dòng sổ cái, không đơn `NEEDS_ATTENTION` mà không còn phần FAILED | PASS |

**Ghi chú về các FAIL trong kịch bản khám phá cũ** (đã truy nguyên, *không phải lỗi sản phẩm*):
- `qa` 69/70: `/health` trả 406 khi script gửi `Accept: text/html` (endpoint trả JSON; `curl` thường = 200).
- `qa2` 93/94 "merged quantity = 5": script giả định giỏ trống; kiểm tra trực tiếp: thêm SP3 ×2 rồi ×3 → **1 dòng, số lượng 5**.
- `qa3` 216/218 "inactive product hidden/404": script sửa sản phẩm với `originalPrice` 120.000 < giá mới 150.000 nên bị validation từ chối; gửi lại hợp lệ → sản phẩm INACTIVE, khách/guest nhận **404**.
- `qa5`/`qa6` lần đầu: chạy sau `qa2–qa4` trên cùng DB nên lệch dữ liệu cứng (tồn); dựng lại bản sao sạch từ backup và chạy riêng → **93/93** và **99/99**.

### Quy tắc nghiệp vụ trọng yếu đã kiểm chứng
- Không âm kho; hoàn tồn đúng một lần (RETURNED +1; LOST/UNCONFIRMED không đổi); không trùng Order/Payment/Refund (chỉ mục unique + test đồng thời); hoàn tiền không vượt đã thu; phí giao hoàn đúng một lần và chỉ khi không còn phần nào được giao; UNCONFIRMED giữ ngoại lệ mở.
- Đơn `COMPLETED` một phần hiển thị rõ "Hoàn tất một phần: có phần không giao được"; mục Review chỉ mở cho OrderItem đã giao (phần bị hủy không có form đánh giá).
- Worker tắt mặc định (log + 75s không ghi nền); khi bật chủ động xử lý timeout đúng và idempotent.

## 7. Giới hạn đã biết và được chấp nhận / vấn đề còn tồn tại

1. **Lịch sử trạng thái đơn trên trang khách** hiển thị tên người thao tác và các ghi chú kỹ thuật dạng `[ADMIN_UNDELIVERABLE]…` (hành vi có từ trước OPEN-10.1: tên nhân viên đã hiển thị). Mục "Hoàn tiền (mô phỏng)" thì không hiện tên. *Đính chính báo cáo OPEN-10.1 mục 5: câu "không hiện tên Admin/Staff" chỉ đúng cho mục hoàn tiền, không đúng cho danh sách lịch sử.* **Ghi nhận cho đợt UI/UX tiếp theo** (theo chỉ đạo của chủ dự án: không mở rộng phạm vi migration/nghiệm thu để sửa; chưa sửa).
2. Khách hủy đơn sau khi đã hoàn tồn một phần bị chặn (an toàn, chưa hỗ trợ).
3. Nhiều phần FAILED chỉ xảy ra qua dữ liệu dựng (Staff bị chặn khi đơn `NEEDS_ATTENTION`).
4. "Hoàn tất một phần" là dấu hiệu dẫn xuất trên `COMPLETED`, không phải trạng thái lưu.
5. Dọn dẹp ngoài ý muốn đợt trước: lệnh xóa DB theo mẫu đã xóa các DB kiểm thử cũ (xem `OPEN10_1_PARTIAL_REFUND_REPORT.md` mục 8). **Lần này** mọi DB được xóa theo **tên chính xác** và chỉ các DB do lần này tạo (`oneshop_mig_copy_20261020_test`, `oneshop_mig_pre_20261020_test`, `oneshop_acc_fresh_20261020_test`); `oneshop` và `oneshop_test` còn nguyên; backup còn nguyên.

## 8. Migration thật trên `oneshop` (đã thực hiện, 20/10/2026)

Thực hiện sau khi chủ dự án xác nhận bằng văn bản. Thứ tự và kết quả:

| # | Bước | Kết quả |
| --- | --- | --- |
| 1 | Fingerprint `oneshop` ngay trước migration so với trạng thái đã backup | **Giống hệt** (`oneshop_fingerprint_premigration_recheck.txt`) → được phép tiếp tục |
| 2 | Backup còn hợp lệ (`RESTORE VERIFYONLY … CHECKSUM`) | *valid* |
| 3 | Không có ghi dữ liệu | 0 phiên kết nối người dùng tới `oneshop`/`oneshop_test`, 0 transaction mở, không cổng ứng dụng nào lắng nghe (chỉ language server của IDE, không kết nối CSDL), worker không bật (`ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED` không đặt → mặc định `false`) |
| 4 | Chạy đúng `20261020_open101_payment_refunds.sql` (SHA-256 `6c4b6a822e866a8164a037e922fb50cdd9e214dcd3f988d4542d6cbb30f42a25`, tệp đã thử trên bản sao) | "OPEN-10.1 applied: dbo.payment_refunds ready; 1 existing refund(s) recorded in the ledger. Nothing else was changed." — exit 0 |
| 5 | Đối chiếu 22 bảng cũ (cột, FK, CHECK, chỉ mục, số dòng, checksum nội dung từng bảng, tổng tiền/tồn, đầu dò toàn vẹn) | **Giống hệt trước migration** (diff rỗng; `oneshop_fingerprint_after_real_migration.txt`) |
| 6 | Tổng số bảng / FK vô hiệu hoặc không tin cậy / CHECK vô hiệu hoặc không tin cậy | 23 / 0 / 0 |
| 7 | `payment_refunds`: định nghĩa | Giống hệt định nghĩa cài mới (26 đối tượng: cột, FK, CHECK, chỉ mục) |
| 8 | Chỉ mục | `PK_payment_refunds`, `UX_payment_refunds_goods/_shipping/_whole` (unique có lọc theo `component`), `IX_payment_refunds_order`, `IX_payment_refunds_payment` |
| 9 | Bản ghi backfill | 1 dòng: `refund_id=1, payment_id=7, order_id=7, fulfillment_id=NULL, WHOLE_ORDER, 458000.00, SIMULATED_REFUNDED`, `requested_at/refunded_at = 2026-10-12 04:20:06`; khớp `payments.refund_amount/reason/refunded_at` |
| 10 | Thanh toán, đơn, tồn kho | payments 8 dòng / 3.647.000đ / hoàn 458.000đ; orders 8 dòng / 3.647.000đ; tồn 938, giữ chỗ 0 — **không đổi** |
| 11 | Khởi động ứng dụng trên `oneshop`, worker tắt | `/health` UP; log "Payment timeout worker is OFF (default)"; `ddl-auto=validate` + `V3SchemaCompatibilityVerifier` đạt (`app_startup_on_oneshop.log`) |
| 12 | Kiểm tra chỉ đọc (không tạo dữ liệu): 4 vai trò đăng nhập, `/admin`, danh sách/chi tiết đơn 1–8, Payment Console, sản phẩm/cửa hàng/người dùng, `/orders`, `/products`, `/staff` | 31/33 PASS; đơn 7 hiển thị hoàn 458.000đ mô phỏng, xếp "đã hoàn", không sinh "cần xử lý" giả. 2 mục "FAIL" là chi tiết thanh toán của đơn 5 (PAY_AT_STORE) và 6 (COD) trả 409: **đúng thiết kế** — Payment Console chỉ áp dụng đơn ONLINE (`requireOnline` → "Đơn hàng này không sử dụng thanh toán trực tuyến"); kịch bản kiểm tra giả định sai, không phải lỗi |
| 13 | Fingerprint cuối sau khi dừng ứng dụng | **Giống hệt ngay sau migration** → ứng dụng không ghi gì trong lúc khởi động và kiểm tra đọc |
| 14 | `oneshop_test` | Không bị tác động (không có `payment_refunds`, không phiên kết nối); không xóa/rollback/restore database nào |

Ghi chú: phiên "1" thấy ở bước đối chiếu là chính câu lệnh kiểm tra của sqlcmd.
Phương án khôi phục ở mục 3 vẫn hiệu lực (backup còn nguyên; rollback script chỉ chạy được khi bảng chỉ chứa bản ghi backfill) nhưng **không** phải dùng đến.

## 9. Danh sách thay đổi trong đợt nghiệm thu

Chỉ thêm tệp (không đổi mã ứng dụng, không đổi schema ngoài migration OPEN-10.1 đã có):
- `docs/v3/FINAL_BUSINESS_ACCEPTANCE_REPORT.md` (tệp này)
- `docs/v3/acceptance-evidence/` (fingerprint trước/sau trên bản sao và trên `oneshop` thật, `fingerprint.sql`, log khởi động trên `oneshop`, kịch bản HTTP/Browser E2E; không chứa thông tin đăng nhập CSDL)
- Schema của `oneshop` (thay đổi duy nhất): bảng `dbo.payment_refunds` + 1 bản ghi backfill, theo `20261020_open101_payment_refunds.sql`
- Backup (ngoài Git): `…\MSSQL\Backup\oneshop_pre_open101_20261020.bak`
