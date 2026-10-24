# Bug report — OneShop, 24/10/2026

Chỉ ghi CONFIRMED khi đã có runtime/test evidence hợp lệ. SQL dùng database mới `oneshop_qa_20261024_test`. Không sửa production code. Không biến lỗi fixture/test cũ thành bug nghiệp vụ. Test IDs cụ thể theo từng lượt có trong `TEST_EXECUTION_REPORT.md`; tên regression method dưới đây ổn định giữa các lần chạy.

## QA-001 — CRITICAL — Profile ghi khôi phục trạng thái/quyền vừa bị Admin thu hồi

- **Classification:** BUG / CONFIRMED; Auth, Account, Admin User; hai biến thể cùng root cause.
- **Code:** `src/main/java/com/oneshop/controller/client/AccountController.java:53-68` (`updateProfile`), `entity/User.java` không có version; `AdminUserService.update` có lock nhưng profile không dùng cùng protocol.
- **Tests:** `QaAuditRaceDatabaseIntegrationTest#profileMustNotReactivateAnAccountDisabledDuringRequest`, `#profileMustNotRestoreAStaffRoleRevokedDuringRequest` (mỗi biến thể lặp 3 lần mỗi suite run).
- **Preconditions:** Staff seed ACTIVE, có assignment ACTIVE; request profile đã được authenticate, đọc User cũ nhưng chưa save. Admin đăng nhập riêng.
- **Input / steps:** (1) gửi HTTP `POST /account`, CSRF/JWT hợp lệ, `fullName=QA Concurrent Profile&phone=0912345678`; (2) test-only barrier dừng ngay trước repository save; (3) Admin update thật commit `status=INACTIVE`, hoặc `role=CUSTOMER`; (4) cho request profile tiếp tục save và commit; (5) SQL đọc lại và request Staff tiếp theo.
- **Expected:** chỉ full_name/phone thay đổi; giữ nguyên quyết định khóa/thu hồi role của Admin, hoặc từ chối stale write bằng conflict. Tài khoản bị khóa không truy cập Staff tiếp được.
- **Actual:** HTTP profile **302**; SQL `ACTIVE → INACTIVE → ACTIVE`; biến thể role `role_id 2 → 1 → 2`. Trong biến thể khóa tài khoản, request tiếp `/staff/dashboard` trả **200**. Biến thể role vẫn có assignment bị revoke, nên không tuyên bố đã vượt được mọi Store scope.
- **Evidence:** `evidence/races/account-status-*.json`, `account-role-*.json` **chỉ các file `httpStatus: 302`**. File `httpStatus: 500` thuộc harness lỗi ban đầu, không phải bằng chứng bug (xem HARNESS_NOTES).
- **Root cause:** entity detached chứa cả role/status cũ được `JpaRepository.save` merge toàn bộ vào row mới. Không có optimistic version, patch update hoặc transaction/lock chung với Admin.
- **Impact:** chính sách revoke JWT đọc từ DB bị vô hiệu bởi một request profile đang chạy; mất cập nhật security của Admin.
- **Suggested fix:** cập nhật chọn lọc đúng hai cột profile trong service có transaction; phối hợp lock/version với Admin; tái kiểm tra trạng thái hiện tại. Không merge toàn bộ User từ snapshot cũ.

## QA-002 — MEDIUM — Hai transaction chọn ảnh chính tạo hai ảnh primary

- **Classification:** BUG / CONFIRMED; Product Image.
- **Code:** `service/impl/ProductServiceImpl.java:454-460` (`setPrimaryProductImage`), `database/01_schema.sql` bảng product_images không có filtered unique index primary/product.
- **Test:** `QaAuditRaceDatabaseIntegrationTest#parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary` (3 lần/run).
- **Preconditions:** một Product có ảnh A primary, B/C non-primary. Ảnh test chỉ là metadata `example.invalid`, không upload Cloudinary.
- **Steps:** hai transaction service thật đọc list ảnh khi A vẫn primary; transaction 1 chọn B và commit; transaction 2 tiếp tục từ list cũ, chọn C và commit. Hai transaction overlap thực sự, mỗi transaction dùng connection/persistence context riêng; barrier chỉ cố định lịch chạy.
- **Expected:** sau cả hai commit, đúng một ảnh primary, hoặc một transaction conflict và rollback.
- **Actual SQL:** A=false, **B=true, C=true**; `bothTransactionsCommitted=true`. Không exception sản phẩm; lỗi bất biến dữ liệu.
- **Evidence:** `evidence/races/primary-image-*.json` (before/after và commit outcome).
- **Root cause:** dirty checking chỉ UPDATE entity mà transaction thấy thay đổi. Transaction 2 thấy B=false trong snapshot cũ, nên không ghi B=false để xóa lựa chọn transaction 1. Không lock Product chung hoặc constraint ngăn duplicate.
- **Impact:** ảnh catalog có thể phụ thuộc thứ tự query, primary không còn duy nhất. Không khẳng định mất kho/tiền.
- **Suggested fix:** serialize thao tác theo Product; enforce một primary bằng SQL filtered unique index và xử lý conflict/flush ordering an toàn.

## QA-003 — MEDIUM — Upload thành công nhưng transaction rollback để lại file mồ côi

- **Classification:** BUG / CONFIRMED tại integration boundary (external storage được mô phỏng an toàn).
- **Code:** `service/impl/ProductServiceImpl.java:434-451` (`addProductImage`).
- **Test:** `QaAuditImageRollbackDatabaseIntegrationTest#uploadedProductFileMustBeCompensatedWhenTransactionRollsBack` (2 lần/run).
- **Preconditions:** database có một ảnh Product; Cloudinary test double có storage ledger rỗng.
- **Steps / input:** upload fake file qua ProductService; external upload tạo `qa-audit/new`; JPA saveAndFlush ghi metadata thật; sau khi method trả về, đánh rollback-only ở ranh giới transaction; SQL xác nhận rollback.
- **Expected:** metadata và external file nhất quán sau lỗi cuối transaction; file mới phải được compensation khi rollback.
- **Actual:** số row SQL trước/sau đều **1**, nhưng ledger vẫn còn **qa-audit/new**; không gọi cleanup khi rollback xảy ra sau flush.
- **Evidence:** `evidence/image-rollback/product-orphan-*.json`; Surefire assertion `rolled-back upload must not leak an unreferenced external file`.
- **Root cause:** try/catch chỉ bắt lỗi trong thân method; transaction commit/rollback xảy ra ngoài catch. Không đăng ký afterCompletion cleanup.
- **Impact:** orphan assets/quota và metadata không phản ánh tài nguyên đã upload. Không tuyên bố có file mồ côi thật trong tenant hiện hữu vì không gọi live Cloudinary.
- **Suggested fix:** compensation theo transaction completion; giữ retryable cleanup log/outbox nếu provider delete lỗi.

## QA-004 — HIGH — Xóa ảnh cũ trước commit làm DB trỏ tới file đã mất khi rollback

- **Classification:** BUG / CONFIRMED tại integration boundary; Brand Image / transaction.
- **Code:** `service/impl/ProductServiceImpl.java:301-319` (`uploadBrandLogo`); cùng pattern cần kiểm tra tiếp ở category replace/delete và product delete, chưa gán CONFIRMED cho tất cả những method đó.
- **Test:** `QaAuditImageRollbackDatabaseIntegrationTest#replacingBrandImageMustNotDeleteOldFileBeforeCommit` (2 lần/run).
- **Preconditions:** Brand tham chiếu `qa-audit/old`; storage test ledger có old.
- **Steps:** upload new, flush metadata SQL thật, method gọi delete old; inject rollback tại ranh giới transaction sau method; đọc SQL và ledger.
- **Expected:** rollback giữ nguyên tham chiếu old **và file old**; new được dọn. Việc xóa file old chỉ nên xảy ra sau commit thành công.
- **Actual:** SQL rollback về **qa-audit/old**; storage chỉ còn **qa-audit/new**, old đã bị xóa.
- **Evidence:** `evidence/image-rollback/brand-dangling-*.json`; không có HTTP vì injection ở service transaction.
- **Root cause:** external delete không nằm trong SQL transaction nhưng được thực thi trước commit; rollback không phục hồi được binary.
- **Impact:** tài nguyên ảnh cũ bị mất trong khi DB vẫn tham chiếu. Provider đã mock; SQL rollback thật; chưa gây xóa bất kỳ file Cloudinary thật nào.
- **Suggested fix:** defer old-file deletion đến afterCommit, compensate new-file upload khi rollback; quản lý retry cleanup.

## QA-005 — MEDIUM — Page trong phạm vi int vẫn gây HTTP 500 do offset vượt giới hạn JPA

- **Classification:** BUG / CONFIRMED; Catalog và nhiều Admin/Staff readers.
- **Code:** `controller/client/ProductController.java:49-57`, các reader dùng `PageRequest.of(Math.max(0,page),size)` rồi gửi offset cho Spring Data; chi tiết stack của từng endpoint trong evidence.
- **Test:** `QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries`.
- **Steps:** guest `GET /products?page=2147483646` hoặc `2147483647`; tương tự với Admin hợp lệ ở `/admin/products`, `/admin/stores`, `/admin/store-products`, `/admin/inventory`, `/admin/orders`, `/admin/payments`; Staff ở `/staff/reviews`. Test thêm ngay quanh offset tối đa của page size 12/20 và nhánh có/không keyword.
- **Expected:** empty page hoặc 4xx validation có kiểm soát; không 500 vì input người dùng.
- **Actual:** **500**, `InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE (2147483647)`. Có keyword, một số reader đi qua in-memory slice và không lỗi; cho thấy nhánh query có hành vi khác nhau.
- **Evidence:** `evidence/boundaries/*.json` có request/expected/actual/timestamp; `evidence/boundary-log-excerpts.txt`.
- **DB:** read-only, không khẳng định dữ liệu bị ghi sai.
- **Root cause:** clamp page âm nhưng không giới hạn offset `page * pageSize` theo technical limit của JPA.
- **Suggested fix:** validate offset dùng long trước khi tạo query; trả 400 hoặc empty page thống nhất giữa hai nhánh.

## QA-006 — MEDIUM — Ngày kết thúc 9999-12-31 gây HTTP 500

- **Classification:** BUG / CONFIRMED; Admin Orders/Payments.
- **Code:** `service/AdminOperationsService.java:96-97` (`endExclusiveOf`); `service/payment/AdminPaymentService.java:126,137`.
- **Steps:** Admin `GET /admin/orders?from=0001-01-01&to=9999-12-31`; lặp với `/admin/payments`.
- **Expected:** xử lý giới hạn SQL datetime2 hoặc từ chối input bằng validation 4xx.
- **Actual:** **500**, SQL Server `One or more values is out of range of values for the datetime2 SQL Server data type.`
- **Root cause:** tính bound exclusive bằng `to.plusDays(1)` tạo năm **10000**; LocalDate hỗ trợ nhưng SQL Server datetime2 không hỗ trợ.
- **Evidence:** same boundary testcase/evidence JSON và log excerpt như QA-005; hai endpoint độc lập.
- **Impact / fix:** reader bị lỗi ở boundary hợp lệ của kiểu SQL; validate khoảng hỗ trợ hoặc xử lý upper bound tối đa không cộng ngày vượt giới hạn. Không có business write.

## QA-007 — LOW — Tiêu đề tab catalog bị escape HTML hai lần

- **Classification:** BUG / CONFIRMED; UI/SiteMesh.
- **Code:** `templates/product/list.html:7`, `controller/web/DecoratorController.java:48`, `templates/layouts/client.html:10`.
- **Test:** `QaAuditScreenBrowserDatabaseIntegrationTest#roleScreensAndSmallUiActions`; checkpoints `catalog browser title decodes entities /products` và `/products?q=CeraVe`.
- **Steps:** Chrome thật truy cập hai URL trên; đọc `document.title` (không chỉ response HTML).
- **Expected:** `Mỹ phẩm & Chăm sóc sắc đẹp - OneShop | OneShop`.
- **Actual:** literal **`Mỹ phẩm &amp; Chăm sóc sắc đẹp - OneShop | OneShop`** trong `document.title`; HTTP 200, không thay đổi DB.
- **Evidence:** `evidence/screens-additional-results.json` và bản rerun trong `screens-reproduce-results.json`; message ghi chính xác title và hai URL.
- **Root cause:** title đã chứa entity khi SiteMesh extract, rồi Thymeleaf `th:text` escape lại. Cần decode an toàn title dạng text tại một ranh giới duy nhất; không chuyển bừa thành unescaped HTML.
- **Impact:** nhãn tab/SEO/hiển thị nhỏ; không ảnh hưởng tiền/kho.

## QA-008 — MEDIUM — Ảnh danh mục Trang điểm trong seed trả HTTP 404

- **Classification:** BUG / CONFIRMED về tham chiếu tài nguyên seed hiện tại; không kết luận nguyên nhân xóa asset từ bên ngoài.
- **Code/data:** `database/02_seed.sql:99` lưu `categories.image_url` và `image_public_id` cho danh mục Trang điểm; home/category admin dùng URL này.
- **Steps:** cài mới database từ canonical schema/seed; mở trang home và `/admin/categories` bằng Chrome; thu `Network.responseReceived` cho các image request.
- **Expected:** ảnh danh mục seed tham chiếu tài nguyên tồn tại, request trả 200 và render được; hoặc dùng placeholder có chủ đích.
- **Actual:** URL gốc và URL có transformation đều **404** lặp nhiều request: `https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803748/oneshop/seed/categories/trang-diem.webp` và biến thể `f_auto,q_auto,w_800`. Không phải chỉ favicon 404.
- **Evidence:** `networkFailures` trong `evidence/screens-additional-results.json` và rerun. Đây là lỗi được phát hiện từ network evidence; checkpoint render HTTP 200 không chứng minh mọi ảnh thành công.
- **Root cause:** seed reference hiện không resolve được ở provider. Chưa có quyền/tenant test để xác minh asset đã bị xóa, sai version hay sai public_id; những giả thuyết đó giữ UNVERIFIED.
- **Impact:** ảnh category bị hỏng tại UI dùng dữ liệu cài mới. Không ghi/chỉnh/sửa tài nguyên Cloudinary thật trong audit.
- **Suggested fix:** xác minh reference với chủ tenant, dùng asset tồn tại có quyền sử dụng hoặc ảnh local phù hợp; thêm browser assertion image naturalWidth thay vì chỉ page status.

## Các kết quả không được đánh thành BUG sản phẩm

| ID | Classification | Evidence / quyết định |
|---|---|---|
| SC-001 | SPEC CONFLICT / test fixture | 10 baseline ERROR chèn assignment ACTIVE thứ hai bị `UX_ssa_one_active_store_per_user` từ chối; một assertion Phase11 kỳ vọng 302 cho thao tác này nhưng runtime render validation 200. Canonical SQL + Admin service hiện áp dụng một Staff/một Store. Không bỏ index hay sửa production để test cũ PASS. |
| SC-002 | SPEC CONFLICT / UNVERIFIED | Hai Catalog finder tests kỳ vọng không còn select form theo V3, nhưng UI hiện vẫn có năm select form cho Store ACTIVE, legacy select endpoint được giữ và catalog/checkout V3 không phụ thuộc cookie. Chưa có đặc tả hiện hành đầy đủ để đánh nút này là bug. |
| TC-001 | Test expectation drift | Category admin test kiểm tra đúng nguyên văn heading/hint cũ; image-error test kiểm tra nguyên văn câu Cloudinary cũ. Chưa đủ bằng chứng lỗi nghiệp vụ chỉ từ text assertion. |
| SG-001 | SPEC GAP — documentation | Nhiều link README tới V3 spec/runbook/deep audit không tồn tại trong checkout hiện tại. Cần khôi phục tài liệu/policy chính thức; không tự chế nghiệp vụ. |
| UV-001 | UNVERIFIED | Real Cloudinary asset handling/live cleanup chưa chạy; chỉ mock integration boundary. Không có cổng thanh toán ngân hàng thật. |

`favicon.ico` 404 cũng được ghi trong network evidence, phân loại technical asset gap nhỏ, không cộng thêm thành bug logic nghiệp vụ. Không đánh đồng lỗi favicon với QA-008 (ảnh category).
