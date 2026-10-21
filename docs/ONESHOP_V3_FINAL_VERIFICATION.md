# OneShop V3 — Báo cáo xác minh cuối cùng

**Ngày xác minh:** 20–21/10/2026 (ICT)  
**Nhánh:** `tuntun` — HEAD `bc96717`  
**Kết luận:** **READY** cho nghiệm thu V3 trên môi trường kiểm thử, với các giới hạn P2 được ghi rõ bên dưới.

## 1. Tóm tắt điều hành

| Hạng mục | Kết quả |
|---|---|
| Full test suite (`clean verify`) | **PASS** — 95 suite, **1.667 test**, 0 failure, 0 error, 0 skipped; BUILD SUCCESS; 5:24 |
| Browser E2E bằng Chrome thật | **PASS** — 6 nhóm kịch bản, 21 ảnh bằng chứng, đơn/thanh toán/hoàn tiền/tồn kho đều đúng |
| Responsive 375/768/1440 px | **PASS** — **135 capture** trên 45 route thuộc guest/customer/staff/admin; 0 lỗi HTTP, 0 cuộn ngang |
| An toàn dữ liệu production | **PASS** — không ghi/migrate/seed `oneshop`; mọi ghi dữ liệu chỉ trên database `*_test` dùng một lần |
| Kết luận phát hành | **READY** — không còn P0/P1 mở |

## 2. Môi trường và phạm vi

- **Ứng dụng:** OneShop V3, Spring Boot 3.5.16, Java 21.0.12.1, profile `dev`, cổng `8095`.
- **Cơ sở dữ liệu:** Microsoft SQL Server 2025 (17.0.1145.4).
- **Trình duyệt:** Google Chrome 155.0.8059.84, điều khiển qua Chrome DevTools Protocol.
- **Database full suite:** `oneshop_v3_final_20261020_test`, tạo mới hoàn toàn từ `database/01_schema.sql` + `database/02_seed.sql`.
- **Database E2E:** `oneshop_v3_e2e_20261020_test`, tạo mới hoàn toàn từ cùng hai script chuẩn.
- **Cấu hình E2E:** `ONESHOP_PAYMENT_MOCK_GATEWAY_ENABLED=true`, `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=false`, `JWT_COOKIE_SECURE=false` (localhost HTTP).
- **Production `oneshop`:** chỉ đọc. Không chạy migration, seed, reset hay bất kỳ lệnh ghi nào. Phiên JDBC của ứng dụng chỉ trỏ tới database E2E (xác nhận bằng `sys.dm_exec_sessions`).

`oneshop_test` cũ không được dùng làm nguồn nghiệm thu vì dữ liệu legacy không khớp seed/schema hiện hành. Việc này không ảnh hưởng sản phẩm: schema chuẩn 23 bảng đã được kiểm tra và dựng lại sạch trên database dùng một lần.

## 3. Full test suite

Lệnh đã chạy:

```powershell
.\mvnw.cmd -B clean verify
```

Kết quả từ Maven và 95 file Surefire XML:

| Nhóm kiểm thử | Suite | Test | Kết quả |
|---|---:|---:|---|
| Unit / service / thuật toán | 41 | 726 | PASS |
| Web MVC / template / bảo mật route | 12 | 113 | PASS |
| Database integration trên SQL Server test DB | 41 | 824 | PASS |
| Template contract | 1 | 4 | PASS |
| **Tổng** | **95** | **1.667** | **0 failure / 0 error / 0 skipped** |

- **Log:** `verify_run_finaldb2_20261020_232842.log`
- **Kết quả cuối log:** `Tests run: 1667, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, `Total time: 05:24 min`.
- **Surefire:** `target/surefire-reports` có 95 suite và tổng 1.667 test khớp log.

### 3.1. Flake đã xử lý ở gốc

`StaffFulfillmentWebIntegrationTest.friendlyErrorsWithoutLeaks` từng flake vì token CSRF ngẫu nhiên có thể chứa chuỗi `SQL`, và assertion kiểm tra rò rỉ quét cả thuộc tính trong `<header>`. Cách sửa:

- Giữ nguyên toàn bộ kiểm tra không rò rỉ (`SECRET`, `PRIVATE`, `ABCD2345`, `SQL`).
- Chỉ giới hạn phạm vi quét vào nội dung người dùng nhìn thấy trong `<main>` bằng helper `userVisibleContent()`.
- Không xóa test, không nới lỏng assertion nghiệp vụ.

Sau khi sửa, database full-suite được tạo lại sạch và toàn bộ 1.667 test chạy lại thành công.

## 4. Browser E2E với thao tác thật

Ứng dụng chạy trên `oneshop_v3_e2e_20261020_test`. Khách hàng, nhân viên và admin đăng nhập bằng tài khoản seed; các form thật được điền và gửi, không chỉ chụp trang GET.

### 4.1. Kịch bản và kết quả

| Nhóm | Thao tác thật | Kết quả xác minh | Bằng chứng chính |
|---|---|---|---|
| A. ONLINE đa chi nhánh | Thêm 2 sản phẩm từ Store 1 và Store 2, checkout ONLINE, thanh toán mock | Order **#9** = 845.000 ₫; đúng **1 Order, 2 fulfillment** (#13 Store 1, #14 Store 2); chuyển `PENDING_PAYMENT → CONFIRMED → PAID` | `01`–`04` |
| B. Cô lập phạm vi Store | Staff Store 1 mở fulfillment Store 2 và ngược lại | Cả hai chiều đều **HTTP 403**; Staff chỉ thao tác được phần của Store mình | `05`–`07` |
| C. Giao một phần + hoàn tiền | Store 1 hoàn tất giao; Store 2 báo giao thất bại; Admin xác nhận không giao được | Fulfillment #13 `COMPLETED`, #14 `CANCELLED`; hoàn mô phỏng **445.000 ₫**; giữ lại **400.000 ₫** (370.000 ₫ hàng đã giao + 30.000 ₫ phí giao) | `08`, `09`, `09b`, `10` |
| D. Khách thấy hoàn tiền + hủy đơn chưa trả | Mở chi tiết Order, hủy một Order ONLINE chưa thanh toán | Khách thấy đã trả 845.000 ₫, đã hoàn 445.000 ₫, giữ lại 400.000 ₫; Order **#10** chuyển `CANCELLED`, tồn kho được hoàn | `11`, `11b`, `12`, `13` |
| E. Nhận tại cửa hàng | Tạo Order pickup Store 1, Staff báo sẵn sàng, khách đọc mã, Staff nhập mã bàn giao | Order **#11**, fulfillment **#16**; mã **L25PWEQA**; cả Staff và khách thấy `COMPLETED` | `14`–`17` |
| F. COD tách đơn theo Store | Checkout 2 sản phẩm từ 2 Store với COD, tick đồng ý tách đơn | Tạo **2 Order độc lập**: **#12** (Store 1, 400.000 ₫) và **#13** (Store 2, 475.000 ₫); mỗi Order có phí giao 30.000 ₫; cả hai `CONFIRMED` | `18`, `19` |

### 4.2. Kiểm tra trạng thái cuối trong database E2E

| Đối tượng | Trạng thái cuối |
|---|---|
| Order #9 | `COMPLETED`, `PAID`, tổng 845.000 ₫ |
| Fulfillment #13 / #14 | `COMPLETED` / `CANCELLED` |
| `payment_refunds` | Bản ghi #2: 445.000 ₫, `SIMULATED_REFUNDED`, gắn fulfillment #14 |
| Order #10 | `CANCELLED`, `UNPAID`; có movement `CANCEL_ORDER` hoàn kho |
| Order #11 / fulfillment #16 | `COMPLETED`; pickup code đã dùng để bàn giao |
| Order #12 / #13 | `CONFIRMED`; COD; thuộc hai Store khác nhau |
| Tồn kho | Product 2 / Store 1 = 11; Product 9 / Store 1 = 0; Product 9 / Store 2 = 24; không còn reserved |

- **Kết quả JSON:** `screenshots/e2e-final-20261020/e2e-result.json` — `"result": "PASS"`.
- **Ảnh:** 19 ảnh trong lượt E2E + 2 ảnh bổ sung tập trung vào ledger hoàn tiền (`09b`, `11b`) = **21 ảnh**.
- **Console:** chỉ có 2 lỗi tài nguyên 403, đúng là hai phép thử cô lập Store có chủ đích; không có exception JavaScript.

## 5. Responsive 375 / 768 / 1440 px

Bộ capture dùng cùng ứng dụng và dữ liệu E2E, đăng nhập thật cho từng vai trò. Customer có thêm một thao tác thật: thêm sản phẩm vào giỏ và mở form checkout; không gửi đơn, không đổi tồn kho hay thanh toán.

| Vai trò | Route | Capture | Kết quả |
|---|---:|---:|---|
| Guest | 7 | 21 | 200 OK, không cuộn ngang |
| Customer | 7 | 21 | 200 OK, không cuộn ngang |
| Staff Store 1 | 10 | 30 | 200 OK, không cuộn ngang |
| Admin | 21 | 63 | 200 OK, không cuộn ngang |
| **Tổng** | **45** | **135** | **0 failure** |

Phạm vi chính:

- **Guest:** trang chủ, catalog, chi tiết sản phẩm, cửa hàng, đăng nhập, đăng ký, image credits.
- **Customer:** giỏ hàng, danh sách đơn, Order hoàn tất một phần, Order pickup đã nhận, trang thanh toán, kết quả checkout, form checkout thật.
- **Staff:** dashboard, hàng đợi fulfillment, giao hàng, pickup, chi tiết fulfillment, tồn kho, lịch sử kho, điều chỉnh kho, đánh giá.
- **Admin:** dashboard, đơn hàng, chi tiết đơn, thanh toán, chi tiết thanh toán, sản phẩm, form sản phẩm, sản phẩm theo chi nhánh, cửa hàng, danh mục, thương hiệu, người dùng, đánh giá, phân công nhân viên, tồn kho toàn chuỗi.

Kết quả ghi tại `screenshots/responsive-final-20261020/responsive-result.json`:

- `captureCount: 135`, `failures: 0`, `overflowCount: 0`.
- Mọi route trả `httpStatus: 200`, có `<main>` và không phát sinh cuộn ngang thực tế (`scrollX = 0`).

**Ghi chú P2 thị giác:** trang chủ dùng dải full-bleed `100vw`; khi trình duyệt desktop có scrollbar cổ điển, `scrollWidth` lớn hơn vùng nội dung khoảng 8 px nhưng `body { overflow-x: clip }` giữ `scrollX = 0`, không có thanh cuộn ngang và không cản thao tác. Đây là khác biệt kỹ thuật đã biết, không phải lỗi chặn nghiệm thu.

## 6. Ma trận P0 / P1 / P2

| ID | Mức | Hạng mục | Kết quả | Bằng chứng |
|---|---|---|---|---|
| P0-01 | P0 | Toàn bộ test suite | **PASS** — 1.667/1.667 | Maven log + Surefire |
| P0-02 | P0 | Order ONLINE đa Store, thanh toán mock | **PASS** — Order #9, 2 fulfillment, PAID | E2E A, ảnh `01`–`04` |
| P0-03 | P0 | Cô lập dữ liệu theo Store | **PASS** — 403 cả hai chiều | E2E B, ảnh `05`–`07` |
| P0-04 | P0 | Giao một phần, hoàn tiền, giữ phí giao | **PASS** — hoàn 445.000 ₫, giữ 400.000 ₫ | E2E C, `09b` |
| P0-05 | P0 | Hủy đơn chưa thanh toán và hoàn kho | **PASS** — Order #10 `CANCELLED`, có `CANCEL_ORDER` | E2E D, DB movement |
| P0-06 | P0 | Pickup code và bàn giao tại cửa hàng | **PASS** — mã L25PWEQA, fulfillment #16 `COMPLETED` | E2E E, `15`–`17` |
| P0-07 | P0 | COD tách đơn theo Store | **PASS** — Order #12/#13 độc lập, đúng phí | E2E F, `18`–`19` |
| P0-08 | P0 | An toàn production | **PASS** — không ghi `oneshop`; JDBC chỉ tới DB E2E | `sys.dm_exec_sessions`, `sys.databases` |
| P1-01 | P1 | Responsive khách hàng 375/768/1440 | **PASS** — 21 capture | Responsive manifest |
| P1-02 | P1 | Responsive Staff/Admin 375/768/1440 | **PASS** — 93 capture | Responsive manifest |
| P1-03 | P1 | Console thanh toán/hoàn tiền Admin | **PASS** — ledger, payment, refund hiển thị đúng | ảnh `09b`, `10` |
| P1-04 | P1 | Khách nhìn thấy hoàn tiền | **PASS** — đã trả/đã hoàn/giữ lại đúng | ảnh `11b` |
| P2-01 | P2 | `oneshop_test` legacy không khớp seed | **Đã giảm thiểu** — dùng DB dùng một lần dựng từ script chuẩn | Mục 2 |
| P2-02 | P2 | Full-bleed `100vw` bị cắt ~8 px dưới scrollbar cổ điển | **Chấp nhận** — không cuộn ngang, không ảnh hưởng thao tác | Mục 5 |
| P2-03 | P2 | Thanh toán chỉ là mock gateway | **Đúng phạm vi V3** — không có cổng thật | `application.properties`, E2E A |
| P2-04 | P2 | Không kiểm thử tải/đa người dùng thực, email/SMS thật | **Ngoài phạm vi** nghiệm thu chức năng này | — |

## 7. File thay đổi / bổ sung trong lượt cuối

- `src/test/java/com/oneshop/StaffFulfillmentWebIntegrationTest.java` — sửa flake CSRF, giữ nguyên kiểm tra rò rỉ.
- `scripts/lib/cdp.mjs` — helper Chrome DevTools Protocol.
- `scripts/e2e_final_verification.mjs` — kịch bản E2E thao tác thật.
- `scripts/responsive_final_verification.mjs` — ma trận responsive 45 route × 3 kích thước.
- `screenshots/e2e-final-20261020/` — 21 ảnh + `e2e-result.json`.
- `screenshots/responsive-final-20261020/` — 135 ảnh + `responsive-result.json`.
- `docs/ONESHOP_UI_UX_FIX_REPORT.md` — cập nhật kết quả cuối.
- `docs/ONESHOP_V3_FINAL_VERIFICATION.md` — báo cáo này.

Các thay đổi UI/UX đang có trong working tree từ các vòng trước (checkout, catalog, cart, header/navbar, staff/admin templates, CSS, test fixture) được **giữ nguyên**, không reset hay ghi đè. Không thay đổi `database/01_schema.sql`, `database/02_seed.sql`, cấu hình production hay công nghệ.

## 8. Danh mục bằng chứng

| Bằng chứng | Đường dẫn |
|---|---|
| Full suite log | `verify_run_finaldb2_20261020_232842.log` |
| Surefire XML | `target/surefire-reports/TEST-*.xml` (95 file) |
| E2E result | `screenshots/e2e-final-20261020/e2e-result.json` |
| E2E ảnh | `screenshots/e2e-final-20261020/*.png` (21 file) |
| Responsive result | `screenshots/responsive-final-20261020/responsive-result.json` |
| Responsive ảnh | `screenshots/responsive-final-20261020/*.png` (135 file) |
| Ứng dụng E2E log | `e2e_app_20261020_run2.log` |
| Trạng thái DB E2E | Truy vấn trong mục 4.2 (Order #9–#13, refund #2, tồn kho) |

## 9. Kết luận

**READY.** OneShop V3 đáp ứng các tiêu chí nghiệm thu chức năng trong phạm vi đã nêu:

- Toàn bộ 1.667 test tự động xanh trên database dựng sạch từ schema/seed chuẩn.
- Các luồng P0 được kiểm chứng bằng thao tác trình duyệt thật: đặt hàng đa Store, thanh toán, cô lập Store, giao một phần, hoàn tiền, hủy đơn, pickup code và COD tách đơn.
- Giao diện khách hàng, Staff và Admin không có lỗi responsive chặn thao tác tại 375/768/1440 px.
- Production `oneshop` không bị ghi, migrate hay seed trong suốt quá trình.

Các giới hạn còn lại đều là P2 đã biết: thanh toán mô phỏng theo đúng thiết kế V3, thiếu kiểm thử tải/email/SMS thật, database `oneshop_test` legacy không dùng cho nghiệm thu, và hiện tượng cắt full-bleed ~8 px dưới scrollbar cổ điển không gây cuộn ngang. Không có P0/P1 mở.
