"""Produce audit-only reports from captured local evidence; does not run tests or mutate SQL."""
from pathlib import Path
import json, re
here=Path(__file__).parent; reports=here.parent
def load(name): return json.loads((here/name).read_text(encoding='utf-8-sig'))
def write(name,text):
 text=text.replace('%20(1).md','%20%281%29.md').replace('line125,','line126,')
 text=re.sub(r'(\d)(Store|Order|SKU|TOTAL|PASS|FAIL|ERROR|SKIP|bytes|groups|suites|conditionalSKIP|CANCEL_ORDER)',r'\1 \2',text)
 for old,new in {'SQLServer':'SQL Server','SpringBoot':'Spring Boot','Java21':'Java 21','Maven3.9.16':'Maven 3.9.16','33Phase12hardeningPASS':'33 Phase12 hardening PASS','44suites':'44 suites','currentlive':'current live','historicalProductlivePASS':'historical Product live PASS','Product/Brand upload':'Product/Brand upload','liveBrand/prodprovider':'live Brand/provider prod','public/demo registration':'public/demo registration','chưarerun':'chưa rerun','roleCUSTOMER':'role CUSTOMER','chưacompleted':'chưa COMPLETED','foreignuser':'foreign user','valid-CSRF404':'valid CSRF → 404','live current Product/Brand':'live current Product/Brand','Order9812/CODCONFIRMED':'Order9812/COD CONFIRMED','key/secret':'key/secret','enabledtrusted':'enabled/trusted','JWTHTTPS':'JWT HTTPS','localhostactual':'localhost actual','sensitive values exactscan0match':'sensitive values exact scan: 0 match','prodJWTcookieSecuretrue/INFOlogging':'prod JWT cookie Secure=true / logging INFO','SQL-cloud':'SQL cloud','profileprod':'profile prod','cloudconnection':'cloud connection','targetdemo':'target demo','Cloudinaryaccountpermissions':'Cloudinary account permissions','localTrustServerCertificatetrue':'local trustServerCertificate=true','cloudcertificate':'cloud certificate','targetsсhema':'target schema','targetschema':'target schema','tạiproxy':'tại proxy','predeploy':'pre-deploy','demo seed':'demo seed'}.items(): text=text.replace(old,new)
 (reports/name).write_text(text.strip()+'\n',encoding='utf8')
inv=load('inventory.json'); reg=load('regression-summary.json'); db=load('db-before.json')
header='Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.\n\n'
scope='''Kiểm chứng trên Windows, Java 21, Maven 3.9.16, Spring Boot 3.5.16, SQL Server `localhost:1433/oneshop`, Standard Developer Edition 17.0.1000.7. Server audit chỉ bind `127.0.0.1:8787`, profile dev; Chrome headless 155 qua CDP `127.0.0.1:9224`. Hai tiến trình do audit tạo đã dừng.

Các mutation dùng test fixture/SeedGuard hiện hữu hoặc fixture mới mang marker `AUD261009FSA`, ba Store mới và hai User mới. Không dùng stock/cart/order hiện hữu cho hành trình ghi dữ liệu. Cleanup chỉ nhắm fixture; [db-comparison.json](evidence/db-comparison.json) xác nhận 19/19 bảng giữ nguyên số hàng và hash toàn bộ nội dung, bao gồm IDs/timestamps. IDENTITY có thể tăng do fixture; không reset counter. [source-comparison.json](evidence/source-comparison.json): 353 file tracked nguyên trạng, Git chỉ có thư mục audit mới.
'''
evidence='''| Evidence | Nội dung / mức kiểm chứng |
| --- | --- |
| [inventory.json](evidence/inventory.json), [inventory.md](evidence/inventory.md) | 97 action mapping annotations (98 verb/path pairs do `/staff` có alias `/staff/`), 19 entity/table, 54 templates/fragments/layouts, 38 service Java files, 52 test/support files. Các số này không đồng nghĩa mọi file là một màn hình hoặc suite chạy |
| [regression-summary.json](evidence/regression-summary.json) | 44 suites / 927 test executions, từng method và PASS/FAIL/ERROR/SKIP từ Surefire XML vừa chạy |
| [build-summary.json](evidence/build-summary.json) | Lệnh thật, BUILD SUCCESS, 15:42:46 +07:00, 02:03 phút, artifact/hash |
| [database-inspection.json](evidence/database-inspection.json), [database-summary.json](evidence/database-summary.json), [mapping-comparison.json](evidence/mapping-comparison.json) | SELECT actual schema/constraints/indexes; đối chiếu 141 JPA fields (kể cả inherited) với141 SQL columns, không drift về type/nullability/length/decimal precision-scale |
| [browser-survey.json](evidence/browser-survey.json), [browser-network.json](evidence/browser-network.json) | 46 route/ngữ cảnh × 3 viewport = 138 DOM surveys, actual HTTP/network và trạng thái decorator/form |
| [browser-journey.json](evidence/browser-journey.json) | 74 records, Customer/Staff/Admin đi qua UI production và SQL thật; ghi rõ fixture IDs, kết quả mỗi bước |
| [browser-additional.json](evidence/browser-additional.json), [browser-additional-network.json](evidence/browser-additional-network.json) | 19 records: race Admin/Staff, JWT cookie flags đã bỏ giá trị, user disable, CSRF/ownership, XSS, filter forms, validation, mobile toggle |
| [fixture-db.json](evidence/fixture-db.json), [journey-db-checks.json](evidence/journey-db-checks.json) | DB sau UI mutations, trước cleanup; 14 assertions nghiệp vụ đều true, không có password/hash/JWT |
| [screenshots/](evidence/screenshots/) | 137 screenshots thực tế; không phải mockup |
| [historical-cloudinary.json](evidence/historical-cloudinary.json) | Trích log Phase12 cũ: live Product upload/provider/CDN/delete 1 PASS, 15:11:16 +07:00; **không rerun** |
| [secret-log-scan.json](evidence/secret-log-scan.json) | Không thấy giá trị DB_PASSWORD/JWT_SECRET/Cloudinary key/secret đang cấu hình trong audit logs; exact matching ≥8 ký tự, không chứng minh không có mọi loại secret |
'''
write('00_Audit_Executive_Summary.md', '# OneShop — Full System Audit\n\n'+header+'''**AUDIT COMPLETED. Kết luận: NOT READY theo phạm vi Roadmap V2 chưa được điều chỉnh.** Các luồng P0 về mua hàng, payment mô phỏng, fulfillment, inventory và phân quyền hoạt động trong môi trường đã kiểm chứng. Không phát hiện CRITICAL/HIGH trong các scenario đã chạy. Ba finding MEDIUM vẫn mở; audit không sửa lỗi.

| Severity | Confirmed findings |
| --- | ---: |
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 3 |
| LOW | 0 |

| ID | Finding | Ý nghĩa trước Phase 13 |
| --- | --- | --- |
| FSA-01 | Customer không có luồng đánh giá sau COMPLETED; ReviewService/Impl là skeleton | Thiếu chức năng tác nhân Customer trong Roadmap §5/§8.1, P1. Không phải lỗi Admin review hoặc lỗi stock. Cần hoàn thiện hoặc ghi rõ quyết định hoãn P1; không tuyên bố full roadmap complete |
| FSA-02 | Mật khẩu Unicode 30 ký tự / 90 byte vượt BCrypt, đăng ký trả HTTP 500 | Input hợp lệ theo DTO được đưa tới encoder không được guard. Cần validation có thông báo đúng trước public demo signup |
| FSA-03 | Dockerfile/render.yaml/README chọn Docker trái giới hạn Roadmap | Điều kiện scope cho Phase13 “không thêm Docker chỉ để deploy” chưa đạt. Cần chốt cấu hình tuân thủ roadmap hoặc phê duyệt thay đổi phạm vi; audit không tự đổi platform |

Chi tiết tái hiện, dòng source, root cause, mức tác động và hướng sửa tối thiểu: [06_Issue_Register.md](06_Issue_Register.md). NOT READY là kết luận về completeness/scope hiện tại, **không** là tuyên bố đã tìm thấy lỗi nghiêm trọng trong giao dịch.

''' + scope + '''
## Regression và acceptance

Lệnh thực chạy: `mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' clean package`.

**927 TOTAL, 926 PASS, 0 FAIL, 0 ERROR, 1 SKIP; BUILD SUCCESS**, 44 suites. 33 test Phase12 hardening PASS, 0 SKIP. Baseline 928 có thêm `Phase12CloudinaryLiveVerification`, ngoài Surefire default patterns. Audit không chạy class opt-in này vì user cấm live upload/delete khi chưa cấp phép. Chênh lệch không do sửa/bỏ test. Một SKIP cũ là nhánh Cloudinary *chưa configured* khi environment hiện tại đã configured.

TC-01–TC-16 và TC-18: **17 PASS** từ regression SQL/HTTP vừa chạy, bổ sung UI journey. TC-17: **NOT VERIFIED cho việc live rerun hiện tại**; adapter mock + SQL PASS, có historical Product live PASS, Brand live riêng chưa xác minh. Không gộp test lịch sử vào tổng test hiện tại. [Ma trận đầy đủ](05_Integration_Test_Coverage.md).

## Phạm vi và nguồn chuẩn

Đối chiếu Roadmap V2 đủ BR-01–18, flows §6, schema §7, service/UI §8–9, P0/P1/P2 §11, 13 giai đoạn, TC-01–18 và scope prohibitions. Đã đọc [Roadmap](../../OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang%20(1).md), [13 giai đoạn](../../OneShop_PhanChiaGiaiDoan_ThucHien_TheoRoadmapV2.md), README, database README/01–05 SQL, các phase reports hiện hữu và test/source liên quan. Không chạy 00_run_all/schema/seed.

Có 12 reports: Phase9.1–9.5, Phase10.1–10.5, Phase11, Phase12, cùng audit Phase11/12. **Không tìm thấy báo cáo riêng Phase1–8**; các giai đoạn này đối chiếu bằng README/schema/source/tests vừa chạy, không suy đoán nội dung tài liệu thiếu. Inventory liệt kê chính xác file report.

Các giới hạn: chưa chạy app trên Render/SQL Server cloud/HTTPS prod; không browser-test live upload/brand replace; chưa benchmark tải lớn, execution plans, mọi lịch deadlock, image primary concurrency, full accessibility bằng assistive technology. Không security certification/dependency CVE scan. Các hạng mục này ghi NOT VERIFIED, không được chuyển thành finding suy đoán hoặc PASS từ baseline.

''' + evidence + '''
## Trả lời tám câu hỏi của yêu cầu

1. **Mức hoàn thiện:** BR-01–18 và P0 core được xác minh; chưa full Roadmap vì Customer review P1 thiếu và deploy strategy trái giới hạn Docker.
2. **Xuyên suốt:** catalog → cart ba Store → checkout ba Order → ONLINE demo/COD/PAY_AT_STORE → delivery/pickup → COMPLETED chạy qua Chrome; cancellation/repeat cũng đúng. Nhánh review sau completed bị đứt.
3. **Frontend/backend:** các form core, filter, CSRF, snapshot và state hiển thị đồng bộ với SQL. Signup Unicode lệch validation và thiếu review UI/backend. Không thấy overflow toàn trang trong 138 khảo sát viewport.
4. **Database:** 19 bảng, 26 FK/40 CHECK đều enabled/trusted; các query integrity không có vi phạm. Atomicity/race/rollback tests PASS và UI fixture ledger đúng. Không khẳng định an toàn cho mọi tải/môi trường khác.
5. **CRITICAL/HIGH:** không finding confirmed ở hai mức này trong phạm vi kiểm chứng.
6. **Thiếu/chưa xác minh:** FSA-01–03; TC17 current live và Brand live; cloud/prod deployment, scale/performance, accessibility nâng cao chưa xác minh.
7. **Trước Phase13:** xử lý signup FSA-02; đóng quyết định phạm vi FSA-03; hoàn thiện review hoặc ghi nhận hoãn P1 FSA-01. Sau remediation chạy lại coverage liên quan/full regression, giữ dữ liệu hiện có. Live/cloud kiểm chứng khi được phép ở môi trường demo.
8. **Go/No-Go:** **NOT READY** theo roadmap hiện tại. Không deploy hoặc triển khai Phase13 trong audit. AUDIT COMPLETED ≠ SYSTEM PASSED.
''')

road=Path('docs/OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang (1).md').read_text(encoding='utf8')
brs=re.findall(r'\| (BR-\d+) \| ([^\n]+?) \|',road)
brproof={1:'TC01 SQL + catalog-three-stores: Product503 tại Stores47/48/49',2:'TC08/13/14; UI stock/price và ledger 819–821',3:'TC02/03; selected-store-A/B/C',4:'TC01/02/03 và selected Store detail/price',5:'TC04; cùng SKU thành 3 CartItem nguồn StoreProduct khác nhau',6:'TC05; cart-three-groups groupCount=3',7:'TC06; checkout6281 → Orders9809/9810/9811',8:'TC15; A DELIVERY/COD, B PICKUP/ONLINE, C PICKUP/PAY_AT_STORE',9:'TC07/08 concurrency & tampering SQL/HTTP tests',10:'Order tạo CONFIRMED tự động trước Staff prepare; ONLINE sau payment success',11:'TC10; pickup B/C ready → mã Customer → Staff complete',12:'TC12; Order9812 hoàn 819:11→12 một lần',13:'TC16; 16 history fixture entries, timeline/status checks',14:'TC11; revoke assignment397 cùng JWT →404, reactivate→200',15:'DTO/HTTP tests không exact quantity; Staff/Admin stock fields và UI survey',16:'TC03 SQL/cart tests; selected A→B→C vẫn giữ CartItems tại nguồn cũ',17:'TC18; Store47/Product503/StoreProduct819 INACTIVE vẫn còn Order/history',18:'TC15; Payments của ba Order độc lập, checkout chỉ group'}
brtable='| BR | Quy tắc Roadmap | Verification hiện tại | Status |\n| --- | --- | --- | --- |\n'+''.join(f'| {code} | {text} | {brproof[int(code[3:])]} | PASS |\n' for code,text in brs)
write('01_Backend_Business_Logic_Audit.md','# Backend & Business Logic Audit\n\n'+header+'''## Kiến trúc và dữ liệu

Controller client/staff/admin/api nhận Principal/path/query/DTO, BindingResult và tạo ViewModel; scan không có repository injection hoặc ghi OrderStatus/quantity vào entity từ Controller. AdminStoreProductController.setPrice/setQuantity phục vụ form, không là inventory writer. Form editing ghim Store/Product hiện hữu, service thực hiện business validation. API Auth trả AuthResponse/UserResponse; không trả User entity/passwordHash. Security filter/interceptor chạy trước controller, DTO mapping trong transaction khi open-in-view=false.

97 action annotations được inventory kèm file/dòng/method, 19 repositories/19 mapped entities. 38 service Java files gồm interface, implementation và concrete service; không suy ra 38 module. Core writers là CheckoutServiceImpl, OrderServiceImpl/OrderTransitionPolicy, PaymentServiceImpl, InventoryServiceImpl. OrderViewService chỉ snapshot/timeline reader, transaction MANDATORY.

New Admin services có @PreAuthorize ADMIN; CRUD Catalog/Store Phase6 được bảo vệ bởi SecurityConfig route ADMIN. Không khẳng định mọi phương thức trong ProductService có method-level annotation. Customer read dùng trusted Principal email + ownership repository, cancel có thêm method authorization; Staff dịch vụ lấy scope từ SecurityContext/ACTIVE DB assignments.

## Ma trận BR-01–18

''' + brtable + '''
Các PASS trên là quy tắc lõi được current regression/dynamic evidence kiểm chứng; chúng không bao hàm Customer review, Cloudinary live hoặc mọi chức năng P1. Xem [TC table](05_Integration_Test_Coverage.md) để truy vết exact method.

## Actor/function coverage

| Actor / module | Implementation & actual verification | Kết quả |
| --- | --- | --- |
| Guest auth/catalog/finder | AuthController/AuthService, Product/Store services; survey home/login/register/catalog/detail/StoreFinder; catalog HTTP/SQL/filter/selected context tests | Core PASS; Unicode registration FAIL FSA-02 |
| Customer cart/selection | CartService; repeated add 819 gives quantity2, thêm820/821 thành ba groups; UI checkbox group chọn item thật, backend own cart tests | PASS |
| Customer checkout/payment | CheckoutService/PaymentService; checkout6281 có total474000, Orders độc lập 222000/121000/131000; ONLINE B thanh toán trước Staff, offline thu lúc completion | PASS |
| Customer order/cancel | CustomerOrderService/OrderTransitionPolicy, trước PREPARING và UNPAID; Order9812 CONFIRMED→CANCELLED, duplicate không ghi lại, completed cancel400 | PASS |
| Customer review | ReviewService interface và Impl rỗng; completed order và product không có review action, valid CSRF POST hai candidate paths404 | **FAIL FSA-01**; chưa có route khác trong inventory |
| Staff multi-Store | Scope service/resolver đọc User/Role/assignment/Store ACTIVE hiện tại; fixture Staff270 có ba assignments; revoke397 chặn Order9809 với JWT cũ, reactivate phục hồi | PASS |
| Staff fulfillment/pickup | Production Delivery/Pickup services; DELIVERY prepare/pack/ship/complete, PICKUP prepare/ready/code/complete qua form UI. Repeat completion được reject, không duplicate payment/history | PASS |
| Staff inventory | StaffInventoryService + writer InventoryService; physical count8→12 actor270; history đúng `/staff/inventory-history/{id}` | PASS |
| Admin Store/assignment/catalog | Tạo Store/category/brand/SKU/StoreProduct/User/assignments qua UI; metadata và SQL kiểm lại; tái sử dụng CRUD Phase6 | PASS cho scope hiện hữu |
| Admin inventory/orders/overview | AdminOperationsService + reused StoreProduct query; store filter qua GET form UI, current tests stable pagination/counts, overview so với independent SQL | PASS |
| Admin User/Role/Review | Current Phase11 SQL/HTTP tests role revocation, password/role validation, review ACTIVE/HIDDEN chỉ visibility; UI list/forms/User disable-reactivate | PASS; không thay thế Customer review |
| Product image/Brand logo | ProductService/CloudinaryService, actual form có CSRF; mock-adapter SQL regression PASS, historical Product live evidence; current live không rerun | Live **NOT VERIFIED** hiện tại |

## Transaction/state/history

Checkout lock cart, lock StoreProduct PK tăng dần, reload price/availability, tạo session/orders/items/deduct/movements/initial history rồi xóa chỉ selected items trong cùng transaction. Initial order creation có entry old_status=NULL; subsequent transitions dùng policy/common history writer. ONLINE PENDING_PAYMENT→CONFIRMED chỉ sau success; failure restore target order. Không Staff approval gate trước checkout.

Cancel lock Order PK → close pending payment attempts → lock stocks tăng dần → restore đúng OrderItem → CANCEL_ORDER → CANCELLED/history. Nội bộ MANDATORY, outer customer cancellation REQUIRED. CANCELLED lặp là no-op; PAID/PREPARING trở đi bị reject. Rule do user xác nhận trước Phase12, không refund. Legacy offline pending receipts được phân biệt; không suy ra mọi PENDING payment đều là ONLINE.

State/history và SQL constraints không cho quantity âm, fulfillment/payment pair trái rule; SQL tests đã inject exception **sau real flush** rồi kiểm rollback exact rows/timestamps. [Concurrency table](03_Database_Integrity_Performance_Audit.md). Không Redis/queue/distributed lock, WMS hoặc audit table mới.

## Code quality / mismatch

ReviewServiceImpl skeleton là chức năng chưa triển khai thực tế, không coi là đã DONE vì có entity/service tên đúng (FSA-01). Auth Unicode encoder exception không được chuyển thành field validation (FSA-02). README deployment Docker lệch roadmap (FSA-03). Không sửa workaround/tests để hợp thức hóa các kết quả. Older repository read helpers còn hiện diện; chưa phát hiện endpoint dùng chúng để bỏ ownership/scope. Không tạo finding “dead code” chỉ từ tên hoặc import.

Cloudinary compensation không phải transaction phân tán: upload lỗi persist có deleteQuietly, brand replace cleanup old asset best effort. Sau database commit failure/provider delete failure vẫn có cửa sổ không atomic giữa provider và SQL; current tests kiểm các failure cases hiện hữu, crash-recovery mọi cửa sổ **NOT VERIFIED**. Không gọi đây là lỗi đã tái hiện.

Inventory chi tiết endpoint/service/entity/template: [inventory.md](evidence/inventory.md); exact test outcomes: [regression-summary.json](evidence/regression-summary.json).
''')

survey=load('browser-survey.json')['results']
screenrows='| Actor | Route/ngữ cảnh đã render | Desktop / tablet / mobile |\n| --- | --- | --- |\n'+''.join(f"| {r['role']} | `{r['route']}` | 1440×900 / 768×1024 / 390×844 |\n" for r in survey if r['device']=='desktop')
write('02_Frontend_UI_UX_Audit.md','# Frontend / UI / UX Audit\n\n'+header+'''## Phương pháp và giới hạn

Chrome **thật** 155 headless, CDP qua Node builtin WebSocket; không có Playwright/Puppeteer nên không cài dependency. Screenshot là raster từ Page.captureScreenshot. Browser navigation, radio/checkbox change, button.click/requestSubmit và form submit thực qua Thymeleaf/SiteMesh/controllers. Browser fetch chỉ dùng negative probes/duplicate/race đã ghi nhãn; không gọi đó là click UI. Không chọn mock page/data renderer.

Survey: 46 route/ngữ cảnh ×3 viewport, 138 DOM records. Có intentional404 (missing product/cross-Store order); một probe đoán sai `/staff/stock/1/history` trả404 — **không là broken UI link**. Link/redirect production đúng `/staff/inventory-history/{id}`, đã mở và screenshot ở additional records. 137 PNG gồm survey/journey/extra probes. Không tuyên bố đã click mọi permutation của 97 endpoints.

## Visual/layout và accessibility cơ bản

Đã xem trực quan screenshots Client catalog mobile, Staff desktop, Admin inventory mobile; DOM/PNG còn lại được capture để reviewer kiểm lại. SiteMesh chọn đúng client/staff/admin; Bootstrap/navbar/table/card/forms hoạt động. Mobile navbar toggle đổi `collapse` → `collapse show`; desktop sidebar/Store badges hiển thị đúng scope. Survey không có **overflow toàn trang**, broken loaded images hoặc unlabeled native controls. Các bảng Admin dùng vùng scroll ngang nội bộ trên mobile; quantity nằm ngoài viewport đầu nhưng có thể scroll, không phải dữ liệu bị mất. Catalog seed không có ảnh Product (0 product_images), placeholder lớn là empty image state có chủ đích, không là CDN lỗi.

Form labels, accessible selection labels, invalid-feedback, semantic fieldsets, error/empty states được kiểm bằng DOM và một số screenshot. Chưa đo toàn bộ contrast/keyboard focus order/screen-reader, chưa cuộn mọi lazy image hoặc mọi breakpoint; **NOT VERIFIED** cho WCAG compliance và mọi ảnh lazy chưa load. Không lấy kết quả `brokenImages=[]` làm chứng minh Cloudinary CDN hiện tại PASS.

## Functional workflows

| Workflow | UI thực tế / DB evidence | Kết quả |
| --- | --- | --- |
| Customer đăng ký/login/logout | Fixture register→login→home; logout→/login?logout, ONESHOP_TOKEN removed. Invalid email/name/password/phone có feedback | PASS thông thường; Unicode case500 FSA-02 |
| Catalog→detail→selected Store→cart | SKU503 ở Store47/48/49 giá111k/121k/131k; đổi Store không đổi lines trước, repeated add quantity2 | PASS |
| Cart selection→checkout | `.os-cart-group-toggle` chọn cả Store, item flags/total thay đổi; 3 groups → preview 3 blocks, fulfillment/payment radio JS đúng pairing | PASS |
| Checkout→payment→delivery | Ba orders từ một checkout; B ONLINE attempt/success; A COD prepare/pack/ship/complete, Paid at completion | PASS |
| Pickup | B/C ready; Customer code chỉ hiển thị khi ready; Staff input code complete, repeat không có transition/payment thứ hai | PASS |
| Customer cancel | CONFIRMED unpaid order9812 form CSRF→CANCELLED; repeat backend no-op, completed reject400 | PASS |
| Admin quản trị | Create Store/Category/Brand/Product/StoreProduct/Staff/assignment; price edit; inventory/order store filters GET submit; soft-disable giữ lịch sử | PASS cho actions đã chạy |
| Staff vận hành | Login→3Store dashboard→order actions→stock count→history; same JWT revoke/reactivate và user inactive/reactivate | PASS |
| Admin Review/User | Actual lists/edit controls + current SQL/HTTP test basic moderation and role/status changes | PASS; live UI moderation seed không bị sửa |
| Customer completed→review | Completed order/customer product detail không có action đánh giá; ReviewService skeleton, no customer controller | **FAIL FSA-01** |
| Image upload/replace/delete | UI form/action/CSRF render; không submit live trong audit. Current mocked provider tests, historical live Product | **NOT VERIFIED hiện tại** cho live UI/provider |

Tất cả mutation UI dùng fixture mới; [fixture-db.json](evidence/fixture-db.json) sau mutation khớp UI: 3 COMPLETED/PAID, 1 CANCELLED/UNPAID, 3 successful per-order payments, 16 history/11 inventory movements.

## Filter, pagination, validation/error

Admin order filter Store48 chỉ có fixture Order9810, inventory Store49 chỉ có SKU503/stock821 cùng dữ liệu Store đó; current DB pagination test `orderStockUserAssignmentReviewPaginationKeepsFiltersAndStableTotals` kiểm >20 fixtures, stable sort/totals/filter preservation. Browser baseline ít rows không có link trang kế; **không tuyên bố đã click next-page trên dataset không có next-page**. Catalog `q=AUDIT_NOT_FOUND_261009` empty result; missing Product404 và foreign order404 không lộ dữ liệu.

Web POST form trong actual surveys có `_csrf`; logout cũng có. Negative CSRF403 xác minh backend. Validation lỗi ordinary render feedback; Unicode signup500 là field validation mismatch confirmed. Preliminary security probes dùng raw cookie với XOR CSRF trả403 cho guessed review paths; đã kiểm lại bằng rendered hidden token ở journey và nhận404. Không dùng probe403 ban đầu để kết luận route review thiếu.

Không tìm thấy success banner giả ở những mutation đã kiểm: create/update/cancel/read-after-write khớp SQL. Chưa thử mọi modal/confirmation với mọi dataset hoặc UI lỗi provider live; không phát sinh finding suy đoán. Không redesign/tự sửa màu sắc/layout.

## Screenshot chọn lọc

- [Catalog mobile](evidence/screenshots/guest-_products-mobile.png), [Staff dashboard desktop](evidence/screenshots/staff-_staff-desktop.png), [Admin inventory mobile](evidence/screenshots/admin-_admin_inventory-mobile.png).
- [Unicode đăng ký500](evidence/screenshots/registration-unicode-error.png).
- [History mobile sau Admin/Staff race](evidence/screenshots/additional-mobile-history.png), [Mobile navbar mở](evidence/screenshots/additional-mobile-navbar-toggle.png).
- Completed/no-review, checkout ba Order, pickup ready/completed, cancellation và disabled-history có PNG theo số record trong [screenshots](evidence/screenshots/), đối chiếu label ở browser-journey.json.

## Danh sách survey đầy đủ

''' + screenrows)

tablerows='| SQL table | JPA entity | Rows gốc trước/sau |\n| --- | --- | ---: |\n'+''.join(f"| `{e['table']}` | `{Path(e['file']).name}` | {db[e['table']]['count']} |\n" for e in sorted(inv['entities'],key=lambda e:e['table']))
write('03_Database_Integrity_Performance_Audit.md','# SQL Server Integrity, Performance & Concurrency Audit\n\n'+header+scope+'''
## Schema thực tế / mappings

SQL Server Developer Edition là dev/test đã xác minh, 19 bảng đúng Roadmap §7.2; **141 columns, 26 FK, 40 CHECK, 29 unique indexes (bao gồm 19 PK), 35 indexes tổng**. Mọi FK/CHECK enabled + trusted, FK delete/update NO_ACTION. [mapping-comparison.json](evidence/mapping-comparison.json) đối chiếu đủ141 JPA mapped fields (kể cả inherited timestamps) với141 SQL columns: type/nullability/length/decimal precision-scale đều khớp, không unmapped column. Entity @Table/@Column/@JoinColumn và NVARCHAR/BigDecimal/enum mappings được đối chiếu, Hibernate dev `ddl-auto=validate` startup và DatabaseFoundation tests PASS. Validate không thay thế kiểm constraint nên kiểm actual sys catalog độc lập.

UNIQUE: users.email, products.sku, stores.code, StoreProduct(Store,Product), Cart(user), CartItem(cart,storeProduct), Staff assignment(user,store), cùng các lookup keys. CHECK quantity/price>=0, item qty>0 và subtotal=price×qty, rating1–5, enum status, fulfillment/payment pairs, delivery address, pickup timestamps/code constraints, inventory arithmetic/type/reference/staff, successful payment paidAt. Product=SKU riêng, không ProductVariant; ảnh chỉ URL/public_id, không binary column.

Sáu index non-unique theo Roadmap trên StoreProduct(store/status/quantity), StoreProduct(product/status), Order(store/status/created), Order(user/created), InventoryMovement(StoreProduct/created), History(Order/changed). Actual index columns/filter/uniqueness được lưu đầy đủ ở database-inspection.json, không chỉ đọc script.

''' + tablerows + '''
## Data integrity hiện có

Mười nhóm query độc lập đều **0 violations**: disabled/untrusted constraints; order/session total vs sum children; item Store/subtotal; negative price/stock; duplicate ORDER/CANCEL_ORDER per order/SP; broken history chain; order status vs last history; inventory arithmetic/continuity/last quantity; duplicate primary image; binary image columns. FK enabled/trusted kiểm orphan quan hệ trên dữ liệu hiện hữu.

Current Phase12 `existingSqlDataHasCompleteTimelinesConservedStockAndNoDuplicateOrderMovements` kiểm thêm ledger deduction/restore vs item snapshots và payment method/amount/state/paidAt, với phân biệt legacy offline pending; PASS. Seed Order5 Store5 INACTIVE COMPLETED vẫn tồn tại/customer-admin readable. CheckoutSession dùng làm group/total, không là payment source; source convention giữ CREATED, roadmap §7.3 mô tả các status nhưng không yêu cầu derive aggregate status mỗi transition — không tự phát minh rule mới.

UI fixture: checkout6281 gồm Orders9809/9810/9811, một Store mỗi Order, total474000; extra cancel order9812 total222000. Snapshot9809 giữ unit111000/qty2/subtotal222000 sau SP price222000. Có16 nối tiếp status histories, 3 payment SUCCESS đúng method/amount, 11 movements. Cancel chỉ hoàn819:11→12 một lần. Admin/Staff race tại820:9→20(actorAdmin1)→17(actorStaff270), đúng ledger và stock cuối. 14 assertions trong journey-db-checks.json PASS.

Cleanup không reseed và không xóa historical entity của seed. Hai lần so sánh sau regression và sau browser fixtures đều exact match 19 bảng. Script cleanup audit hard-delete **chỉ dữ liệu mới disposable**, không phải production delete endpoint; mỗi fixture được marker/ownership giới hạn trong transaction, abort nếu lẫn foreign order hoặc unexpected provider asset.

## Transaction / concurrency results

| Scenario yêu cầu | Current execution evidence | Kết quả |
| --- | --- | --- |
| Hai Customer mua SKU cuối | CheckoutConcurrencyDatabaseIntegrationTest.tc07_twoCustomersRacingForTheLastUnitNeverOversell / larger orders | PASS: một commit hợp lệ, không stock âm |
| Lỗi giữa multi-Store checkout | failureAfterTheFirstStoreGroupWasWrittenRollsBackTheWholeCheckout | PASS: session/orders/items/movement/history/cart/stock rollback |
| Payment callback đồng thời | PaymentDatabaseIntegrationTest + Phase9 integration concurrent duplicate/conflicting result methods; Phase12 cancel-payment variants | PASS: current persisted invariant/history verified |
| Duplicate concurrent cancel | Phase12.simultaneousDuplicateHttpCancellationsRestoreExactlyOnce | PASS: một restore/history; UI sequential repeat cũng no-op |
| Cancel vs Staff prepare | Phase12.cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit PREPARE case | PASS: thắng hợp lệ CANCELLED hoặc PREPARING, không half-write |
| Cancel vs payment success/failure | Cùng test SUCCESS/FAILURE cases | PASS: late result không thu/hoàn kho lại |
| Concurrent stock adjustments | StaffInventoryDatabaseIntegrationTest.concurrentAdjustmentsKeepSerialBeforeAfterValuesAndOneMovementPerActualChange | PASS: serial counts/movements, không lost audit |
| Admin edit vs Staff adjustment | Phase12.adminStockEditMustLockBeforeLoadingQuantityToPreserveConcurrentMovementLedger; thêm actual two-role HTTP race820 qua browser | PASS: primary load đã lock; UI race9→20→17, cả hai actors đúng |
| Duplicate pickup completion | PickupDatabase/StaffFulfillmentOperations/Phase10 concurrency tests; UI repeat B/C | PASS: một completion/payment/history |
| Exception sau flush | Phase12 cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry 4 cases; failureOnSecondItemRestoration; OrderState history failure | PASS: real SQL flush rồi exception, toàn transaction rollback; retry đúng |

Test spies dùng scheduling/failure injection, không mock persistence outcome hay business state machine. SQL connections/transactions thực, không H2. Không stress không giới hạn. Scenario thắng/thua có timeout; không observed deadlock/transaction blocking bug trong lịch đã chạy, **không bảo đảm mọi interleaving**.

## Query/performance review

Catalog page/StoreProduct/Admin orders/Staff stock/queues dùng SQL predicate, pagination/count và stable tie-breaking IDs; EntityGraph/join fetch và primary-image bulk read tránh tải mỗi ảnh/Store qua query rời trên catalog. Filter không chỉ ở JS/UI. Current pagination regression tạo dataset nhiều hơn20 và kiểm count/page/filter chính xác. Browser current seed nhỏ không chứng minh tính scalable.

Hai giới hạn có bằng chứng source nhưng **chưa measured impact**: CustomerOrderServiceImpl.getOrders→List toàn bộ history khách, OrderRepository.findByUserEmailOrderByCreatedAtDescIdDesc không pageable; AdminOperationsService.getOverview chạy tám count queries mỗi Store (source lines45–52), scale tuyến tính theo Store. Vì chỉ5 seed/8 fixture Store, chưa có bằng chứng chậm đáng kể; không tạo severity/performance finding hoặc đề nghị thêm index dựa trên suy đoán.

OrderDetail history actor có lazy reads theo các actor cần hiển thị; chưa chạy bounded query-count test/execution plan hoặc production-size benchmark. Long catalog/image provider operations trong transaction có thể kéo dài lifetime, nhưng lock wait latency/P95/provider outage/crash-compensation **NOT VERIFIED**. Product image primary concurrency không nằm trong current stock-race suite; no duplicate trên DB hiện có (0 images) không chứng minh race-free. Không thêm schema/index hay chạy extended-event/stress gây tác động DB.
''')

write('04_Security_Audit.md','# Security Audit\n\n'+header+'''## Authentication

JwtService dùng signing key từ JWT_SECRET (tối thiểu32 UTF8 bytes), JJWT verifyWith + signed claims + expiration; JwtServiceTest 5 cases và AccessControl/Auth suites vừa chạy PASS. JwtAuthenticationFilter reload UserDetails từ DB mỗi request, role từ current UserDetails thay vì tin JWT role claim/Store client; invalid/expired/disabled token để anonymous. Không log token/secret trong filter, chỉ exception class.

Chrome cookie actual local dev: **ONESHOP_TOKEN HttpOnly=true, SameSite=Lax, Path=/, Secure=false**, phù hợp localhost HTTP/dev. XSRF-TOKEN không HttpOnly vì CSRF repository cần browser-readable token, không phải authentication cookie. Prod source default JWT_COOKIE_SECURE=true; actual HTTPS cookie/proxy behavior **NOT VERIFIED**, không báo Secure prod PASS từ dev.

Ordinary signup/login/logout qua UI PASS. User status INACTIVE làm JWT Staff đang dùng mất quyền; active lại cùng JWT có quyền theo DB. Phase11 current role/status tests kiểm role đổi, assignments bị vô hiệu khi rời STAFF và không tự bật lại. Logout xóa cookie browser; JWT stateless không có global token denylist, không khẳng định đã thu hồi mọi copy của token sau logout.

Unicode signup FSA-02 trả500, không tạo User; stack trace hiện trong local server log nhưng response web generic500 không có exception/DB secret. Đây là input-validation defect, chưa thấy bypass hoặc dữ liệu hỏng.

## Authorization, isolation, IDOR

| Request / actor | Current evidence | Kết quả |
| --- | --- | --- |
| Guest tới Admin/Staff/Orders | Browser fetch follow redirect cuối `/login`200; AccessControl protected route tests | Bị yêu cầu đăng nhập; **không** coi final200 là truy cập Admin |
| Customer tới Admin/Staff | Survey/probes403; current parameterized AccessControl tests | PASS |
| Staff tới Admin/Customer orders routes |403 ở protected role route | PASS |
| Admin tới Staff/Customer routes |403; Admin có /admin/orders chain-wide | PASS; không tự inherit STAFF role |
| Customer foreign Order | Peer customer tới fixture Order9809 trả404; owned order200 | PASS |
| Staff foreign Store/forged selector | Phase10 tc11 variants endpoint thật; forced query/header/cookie/body không mở scope | PASS |
| Revoke assignment với JWT cũ | Assignment397 ACTIVE→INACTIVE; same JWT Order9809 chuyển200→404; reactivate200 | PASS |
| User inactive với JWT cũ | Chrome additional user disable/reactivate; current Phase11/12 tests | PASS |
| Admin mutation bởi Customer | Actual POST /admin/users với validCSRF403, không tạo user | PASS |
| Direct Admin service invocation | Phase11.newAdminServicesEnforceRoleEvenWithoutHttp | PASS với Admin services mới; không gán kết quả này cho mọi legacy catalog helper |

Store scope lấy từ ACTIVE assignment/User STAFF/Store trong DB tại backend; scoped repositories có store IDs từ resolver. Admin filter Store không áp assignment nhưng vẫn ROLE_ADMIN. SelectedStore là browsing context, không là permission. INACTIVE Store không nhận checkout mới, Customer/Admin vẫn đọc history; Staff active-store scope không mở lại Store đã inactive.

## CSRF và request tampering

SecurityConfig cookie CSRF vẫn bật cho web mutations; stateless session strategy giữ token đúng cho nhiều tab. Bearer API exemptions chỉ request mang Authorization Bearer và không phải protected Order/Staff order/stock/fulfillment form paths. Payment/cancel/Staff forms yêu cầu CSRF cả Bearer và percent-encoded parsed path. Current Phase12/StaffInventory/Phase11 tests kiểm các đường này PASS.

Browser form survey/journey dùng hidden `_csrf` đúng rendered token. Missing token POST cancel403; valid token pass normal flow. Cookie CSRF raw token với mặc định XOR handler không được dùng thay hidden token trong negative-review recheck; audit driver đã phân biệt probe ban đầu403 và current valid-CSRF404.

Checkout DTO không cho client xác định unitPrice/total/status/owner; production service reload locked StoreProduct price/quantity. Current TC08 test sửa payload vẫn dùng giá DB, TC14 snapshot không bị repricing. Admin StoreProduct identity Store/Product ghim theo existing row; forged identity không chuyển stock/Order sang Store khác. Cancel chỉ nhận OrderId/principal, body quantity/store/price/actor không là nguồn dữ liệu. Stock quantity negative/missing/note invalid reject backend, no partial mutation.

## XSS, files, exceptions và secret configuration

Stored XSS probe chỉ dùng fixture Category description `<script>window.__auditXss=1</script>`. Admin render giữ literal value, scriptExecuted=false; Admin review current `reviewManagementOnlyChangesVisibilityAndEscapesContent` cũng PASS. Thymeleaf text/field escaping và queries tham số được đọc; không báo chứng nhận chống mọi stored/reflected XSS/SQL injection. Không chạy destructive/security attack ngoài localhost.

CloudinaryService check empty/contentType `image/`, resource_type=image; multipart server giới hạn5MB file/6MB request; URL/publicId DTO persisted. Client-supplied MIME không là kiểm bytes độc lập; live spoofed-MIME/provider validation, SVG security, all file variants **NOT VERIFIED** trong audit vì không được phép provider mutations. Current mocked adapter/service validation tests PASS, không suy diễn thành exploit confirmed hoặc live secure PASS.

JWT/SQL/Cloudinary credentials lấy từ environment; `.env` ignored, `.env.example` không có giá trị thật. Secret-sensitive values exact scan trong audit logs không có match. Không in/log giá trị token, password, signing secret trong evidence JSON; browser metadata giữ cookie flags, không values. Một số Spring/Tomcat DEBUG logs trong runtime dev không được coi là production config; tracked prod root/oneshop INFO, SQL logging false, errors generic. Chưa kiểm Git history toàn bộ, entropy secrets, dependency CVEs, production logging/headers/CSP/rate limit và target Render config.

FSA-02 là finding security-adjacent validation MEDIUM. Không confirmed privilege escalation, IDOR cross-Store, JWT forge bypass, leak secret hoặc CSRF bypass trong các probes/tests. [Issue register](06_Issue_Register.md) chứa finding đã chứng minh, không nâng giới hạn kiểm chứng thành finding.
''')

tc_rows=[
('01','Một SKU tại3 Store, availability/giá global đúng','ProductService/StoreProduct query; CatalogDatabaseIntegrationTest.tc01_chainCatalogShowsEachSkuOnceWithPriceAndAvailabilityPerStore','Browser catalog-three-stores + detail-global; fixture SP819/820/821','SKU503 xuất hiện một lần, Store47/48/49 giá111000/121000/131000','Đúng catalog và SQL; không exact quantity client','PASS'),
('02','Chọn Store A rồi tìm chỉ StoreProduct A','CatalogDatabaseIntegrationTest.tc02_storeCatalogOnlyReflectsTheStoreProductsOfThatStore; CatalogHttpDatabaseIntegrationTest.tc02_selectingAStoreSwitchesTheCatalogToThatStoreOnly','Current SQL/HTTP assertions; UI chọn StoreA trong detail','Catalog/query A không có dữ liệu Store khác','Current suites PASS, selected Store được render đúng','PASS'),
('03','Detail đổi Store, cart cũ không đổi nguồn','CatalogDatabaseIntegrationTest.tc03_productDetailFollowsTheSelectedStoreAndLeavesCartsAlone; CatalogHttpDatabaseIntegrationTest.tc03_productDetailChangesPriceAndAvailabilityWithTheSelectedStore','UI selected-store-A/B/C và CartItem IDs9739/9740/9741','Giá thay theo Store, CartItem cũ giữ819/820/821','Giá111k/121k/131k, cart nguồn cũ không chuyển','PASS'),
('04','Add cùng StoreProduct2 lần','CartDatabaseIntegrationTest.tc04_addingTheSameStoreProductTwiceGivesOneLineWithTheSummedQuantity; CartHttpDatabaseIntegrationTest.tc04_twoSimultaneousAddsOfTheSameStoreProductStillGiveOneLine','UI same-store-product-added-twice, order9809 qty2','Một CartItem quantity2, không duplicate pair','Một line819 qty2; current concurrent test PASS','PASS'),
('05','Cart3 Store UI3 groups','CartDatabaseIntegrationTest.tc05_aCustomerBuildsACartAcrossThreeStores; CartHttpDatabaseIntegrationTest.tc05_cartPageShowsOneGroupPerStoreWithItsOwnLines','Browser cart-three-groups/group-selection','3 Store sections và item/group toggles đúng','groupCount=3, selected preview3groups','PASS'),
('06','Checkout3 Store tạo3 Order đúng một Store','CheckoutDatabaseIntegrationTest.tc06_checkoutOfThreeStoresCreatesOneSessionAndOneOrderPerStore; CheckoutHttpDatabaseIntegrationTest.tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices','UI checkout-created-three-orders + fixture-db','Một checkout→3orders, total/stock/cart atomic','Checkout6281→9809/9810/9811 Stores47/48/49,total474000','PASS'),
('07','Hai khách mua SKU cuối không oversell','CheckoutConcurrencyDatabaseIntegrationTest.tc07_twoCustomersRacingForTheLastUnitNeverOversell; tc07_whenStockCoversOnlyOneOfTwoLargerOrdersOnlyOneIsPlaced','Current SQL race tests, riêng transactions/locks thật','Chỉ đủ tồn thì commit, quantity>=0, loser không half-write','Cả hai current executions PASS; DB negativeStock0','PASS'),
('08','Client chỉnh giá vẫn dùng price DB','CheckoutDatabaseIntegrationTest.tc08_priceComesFromTheStoreProductAtTheMomentOfCheckoutNotFromTheCartPage; CheckoutHttpDatabaseIntegrationTest.tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices','Current HTTP forged payload + SQL persisted item/amount assertions','Không tin unitPrice/total/owner/status','Current SQL/HTTP tests PASS; normal browser totals khớpDB','PASS'),
('09','DELIVERY COD đủ lifecycle','DeliveryDatabaseIntegrationTest.tc09CodLifecyclePreservesInventorySnapshotsAndCreatesPaymentOnlyAtCompletion; Delivery/Phase9/Phase10 HTTP suites','Browser delivery-prepare/pack/ship/complete; order9809','CONFIRMED→PREPARING→PACKED→SHIPPING→COMPLETED, thu COD khi complete','5 history entries đúng, COD SUCCESS amount222000/PAID','PASS'),
('10','PICKUP PAY_AT_STORE/mã nhận','PickupDatabaseIntegrationTest.tc10AndOnlineLifecyclePreserveInventoryAndSnapshots; Phase9IntegrationWebDatabaseTest.tc15FullHttpLifecycleWithOptionalCatalogChanges','Browser C prepare/ready/customer-code/Staff complete; duplicate probe','CONFIRMED→PREPARING→READY_FOR_PICKUP→COMPLETED, verifycode/paidAt','Order9811 COMPLETED/PAID, code+ready/picked timestamps; duplicate không ghi thêm','PASS'),
('11','Staff A không mở Order B','Phase10IntegrationWebDatabaseTest.tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource; Phase11AdminDatabaseIntegrationTest.assignmentRevocationAndReactivationAffectTheSameOldJwtImmediately','Current cross-Store read/write tests; Chrome revoke/reactivate397','Backend reject bất kể selector/query/header/body/JWT cũ','Forbidden/404, mutation unchanged; Chrome revoke404/reactivate200','PASS'),
('12','Hủy hợp lệ+đúng stock+movement','Phase12HistoryHardeningDatabaseIntegrationTest.customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings; simultaneousDuplicateHttpCancellationsRestoreExactlyOnce','Browser cancel9812 + duplicate; SQL movement/history/stock; rollback injected-after-flush current tests','CANCELLED atomic, restore819 quantity1 đúng một lần','11→12, 1CANCEL_ORDER/1cancel history; completed reject400','PASS'),
('13','Staff adjust+STOCK_ADJUST','Phase10IntegrationWebDatabaseTest.tc13PhysicalCountCreatesOneCompleteAuditAndImmediatelyUpdatesDashboardAndExactStock; StaffInventoryDatabaseIntegrationTest.concurrentAdjustmentsKeepSerialBeforeAfterValuesAndOneMovementPerActualChange','UI Staff adjust8198→12; Admin/Staff actual HTTP race8209→20→17','Exact physical count + before/change/after/actor/note audit','Movement hợp lệ, continuity và stock cuối khớp','PASS'),
('14','Đổi giá sau mua giữ snapshot','CheckoutDatabaseIntegrationTest.tc14_orderItemsKeepTheirSnapshotWhenPriceAndProductNameChangeLater; CheckoutHttpDatabaseIntegrationTest.tc14_resultPageKeepsShowingTheSnapshotAfterThePriceChanges','UI admin-price-edit + customer order9809 + SQL','SP price thay, order.unitPrice/name/total giữ lúc checkout','SP111000→222000; order unit111000×2=subtotal222000','PASS'),
('15','Payment/status từng Order độc lập trong checkout','Phase9IntegrationWebDatabaseTest.tc15FullHttpLifecycleWithOptionalCatalogChanges; mixedFailedPaymentRestoresOnlyItsOrderAndSiblingsStillCompleteOverHttp','Browser A COD/B ONLINE/C PAY_AT_STORE; fixture checkout/payment records','3Order method/status riêng; fail chỉ target restore','B thanh toán trước; A/C thu completion; 3 payment SUCCESS mỗi Order đúng amount; failure current regression PASS','PASS'),
('16','History đủ transition','OrderStateDatabaseIntegrationTest.validTransitionHasOneHistoryActorTimestampNoteAndNoSideEffects; historyFailureRollsBackAlreadyFlushedOrderAndOptionalFlushedHistory; concurrentDuplicateHasOneWinnerAndExactlyOneAudit','Current SQL timeline audit + fixture history16 entries','NULL initial→chain nối đúng actor/time/note; last status=order','0 broken timeline/status issues; browser history+SQL checks PASS','PASS'),
('17','Ảnh tại Cloudinary, SQL URL/public_id','ProductService/CloudinaryService; CatalogDatabaseIntegrationTest.tc17_productImageGoesToCloudinaryAndTheDatabaseKeepsOnlyUrlAndPublicId; tc17_brandLogoIsStoredAsUrlAndPublicIdAndTheOldFileIsRemoved','Current adapter mock+realSQL PASS; historical Phase12CloudinaryLiveVerification1PASS, không rerun','Product/Brand upload/primary/delete/replace/compensation đúng; provider thật verified khi cho phép','DB không binary; mock path PASS. Product live trước audit verified; live current Product/Brand chưa rerun','NOT VERIFIED'),
('18','INACTIVE Store không nhậnOrder mới, lịch sử còn','Phase11AdminDatabaseIntegrationTest.tc18InactiveStoreRejectsNewCheckoutButAdminAndCustomerKeepHistoricalOrders; productStoreAndStoreProductWithHistoryAreSoftDisabledAndNotHardDeleted; Phase12 cancellationAfterStoreIsInactiveStillRestoresTheOriginalStockAndPreservesHistory','Browser Store47/Product503/SP819 inactive; historic order9809 customer+adminread; new add rejected; current checkout negative test','Block new sale/checkout, không delete lịch sử/FK','Historical rows/read giữ; current checkout reject; seed5/baseline rows nguyên trạng','PASS')]
tc_table='| TC | Requirement | Implementation / existing tests (vừa rerun) | Dynamic verification / evidence | Expected | Actual | Status |\n| --- | --- | --- | --- | --- | --- | --- |\n'+''.join('| '+' | '.join(('TC-'+row[0],)+row[1:])+' |\n' for row in tc_rows)
suites='| Suite | TOTAL | PASS | FAIL | ERROR | SKIP |\n| --- | ---: | ---: | ---: | ---: | ---: |\n'+''.join(f"| `{s['name'].replace('com.oneshop.','')}` | {s['tests']} | {s['tests']-s['failures']-s['errors']-s['skipped']} | {s['failures']} | {s['errors']} | {s['skipped']} |\n" for s in reg['suites'])
write('05_Integration_Test_Coverage.md','# Integration & Acceptance Coverage\n\n'+header+'''## Kết quả chạy mới

**927 executions / 926 PASS / 0 FAIL / 0 ERROR / 1 SKIP, 44 suites, Maven clean package BUILD SUCCESS.** Surefire XML và Maven log khớp. [regression-summary.json](evidence/regression-summary.json) giữ tên từng method/status; [build-summary.json](evidence/build-summary.json) giữ command/time/artifact SHA256. Java21/Maven3.9.16 offline cache, không đổi POM/Surefire excludes/test. Auto-review cho Maven escalation do Windows sandbox ZipFS; không có rejection.

Baseline historical15:11:16 có928/927PASS/1SKIP, 45suites vì explicit opt-in `Phase12CloudinaryLiveVerification`. Current default clean package không include class ngoài patterns, không upload/delete live. Không cộng historical1PASS vào current totals để giả lập928. 33 Phase12 hardening tests current PASS không SKIP. SKIP duy nhất `CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf` dùng assumeFalse nhánh “chưa configured”, environment đã configured; giữ nguyên.

Các suite unit dùng mocks và graph cases; DB/HTTP suite dùng production services/repositories/SQL Server, có mock Cloudinary adapter hoặc spy failure injection được ghi rõ. 927 là tổng execution mọi loại, **không phải 927 SQL integration tests**. Browser/audit14assertions là verification riêng, không JUnit tests mới. Không thêm/sửa tests trong src/test.

## TC-01–18

Mọi tên class trong bảng trỏ tới source `src/test/java/com/oneshop/`; các package security/service có thư mục tương ứng trong inventory. Status là kiểm chứng **hiện tại**, không mặc định từ Phase12 report. Một TC có nhiều coverage; expected/actual tóm tắt, exact persisted assertions ở tests/current XML summary và JSON browser/SQL evidence.

''' + tc_table + '''
**17 PASS, 0 FAIL, 1 NOT VERIFIED (TC17 live current), 0 BLOCKED.** Review/customer signup Unicode là extra-roadmap checks FAIL và findings FSA-01/02; TC-01–18 không có case cho chúng. Đây là coverage blind spot: tất cả core TC PASS cũng không chứng minh toàn bộ tác nhân/full roadmap DONE.

## E2E kiểm chứng bằng UI

| Journey | Concrete evidence | Outcome |
| --- | --- | --- |
| Customer catalog→detail→cart→checkout→payment→completed | SKU503, Stores47/48/49, cart9739–9741, checkout6281, orders9809–9811; browser-journey labels/PNGs + fixture-db | 3 Store-specific orders và total474000; A COD delivery completed, B ONLINE pickup completed, C PAY_AT_STORE pickup completed |
| Pickup ready→code→Staff verification | B/C ready pages, Customer code, Staff complete form; duplicate request captured | Completion/payment/history đúng một lần; current negative-code tests PASS |
| Staff login→queue→fulfillment→inventory | Staff270/assignments397–399; stock819 adjust8→12; real history endpoint/additional mobile screenshot | Backend/SQL/UI nhất quán; revoke/reactivate áp JWT đang dùng |
| Admin Store→Staff→SKU/SP→overview→orders | Actual create/update/filter forms, metadata captured; soft-disabled original fixture rows/readhistory | Quản trị scope hiện hữu hoạt động, không CRUD song song |
| Customer cancel/repeat | Order9812/CODCONFIRMED, stock81911→12, 1movement/1history; completed cancel400 | PASS; production atomic paths current flush/rollback tests hỗ trợ |
| Completed→review | Completed9809 + detail Product, reviewActions[], valid-CSRF404; service skeleton | FAIL FSA-01, không có Customer implementation |

### Lưu ý chất lượng audit driver

Một lỗi escape regex của script CDP dừng bước đọc IDs sau khi checkout đã commit; không phải lỗi OneShop. Driver resume cùng browser contexts/fixtures, không tạo lại checkout. Log journey-resume và combined JSON ghi bước tiếp theo; DB vẫn đúng 3 orders/1session. Route history đoán sai404 được kiểm lại bằng route production, không tạo finding. Không sửa test application để che các lỗi audit tooling này.

## Cloudinary evidence boundaries

- Current `CatalogDatabaseIntegrationTest`: CloudinaryService mocked adapter, ProductService/JPA/SQL thật. Product images và Brand logos URL/publicId, primary/delete/replace/compensation PASS. `CloudinaryServiceTest` kiểm adapter validation, không provider live.
- Historical Product live: `Phase12CloudinaryLiveVerification`, root target-phase12-package.log có1PASS/0SKIP. ReportPhase12 mô tả HTTP AdminJWT+CSRF multipart→Cloudinary→SQL→CDN metadata→delete fixture. [historical-cloudinary.json](evidence/historical-cloudinary.json) trích exact suite result.
- Audit này **không** upload/delete asset live, không riêng Brand live test, không current CDN asset check. DB product_images0 sau cleanup. Không báo TC17 current live PASS chỉ vì credentials tồn tại hay mock test PASS.

## Coverage còn giới hạn

Target Render/SQL Server cloud/prod HTTPS not tested; provider failure/crash timing vượt mocked cases; image primary concurrent uploads; scale benchmarks/execution plans; screen reader/full accessibility; every UI modal/path permutation. Bounded stock/payment/order races đã chạy, không exhaustive concurrency proof. Các giới hạn được giữ NOT VERIFIED, không sửa scope hoặc bỏ test để tăng kết quả.

## Suite totals

''' + suites)

write('06_Issue_Register.md','# Issue Register — Confirmed Findings\n\n'+header+'''Ba finding confirmed, **0 CRITICAL / 0 HIGH / 3 MEDIUM / 0 LOW**. Không remediation. Priority dưới đây là thứ tự xử lý trước khi tuyên bố phù hợp roadmap/demo, không thay đổi severity thành một lỗi core giao dịch.

## FSA-01 — Customer review sau COMPLETED chưa được triển khai

| Field | Detail |
| --- | --- |
| Severity / priority | MEDIUM / P1 — functionality/completeness |
| Module | Customer Product/Order/Review; khác Admin review management |
| Source / line | [ReviewService.java](../../../src/main/java/com/oneshop/service/ReviewService.java) lines4–6; [ReviewServiceImpl.java](../../../src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java) lines6–9; [Roadmap V2](../../OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang%20(1).md) line125, §5 actor Customer / §8.1 ReviewService / P1review |
| Description | Service interface và implementation rỗng; không Customer Review controller, submit/display action trong Order/Product templates. Admin chỉ ACTIVE/HIDDEN review đã tồn tại |
| Reproduction | Login Customer fixture, checkout và complete Order9809 chứa Product503. Mở /orders/9809 và /products/503. Tìm action đánh giá; POST review candidate routes với đúng hidden CSRF |
| Expected | Theo §5 Customer được đánh giá sau COMPLETED; service xác minh đã mua, rating/unique ownership và trả feedback |
| Actual | reviewActions=[], không form/link/API trong toàn endpoint inventory. POST /products/503/reviews và /reviews valid-CSRF đều404; DB không new Review |
| Evidence | browser-journey.json records customer-completed-no-review / review-endpoints-missing; screenshot tương ứng; inventory.json; source skeleton. Đã loại false-positive CSRF403 preliminary probe bằng recheck valid token404 |
| Root cause | **Confirmed:** chỉ bootstrap skeleton dành “later phase”, chưa có nghiệp vụ/customer UI. Không suy đoán lỗi JPA hoặc quyền đã làm route mất |
| Impact | Luồng hậu mua bị thiếu, reviews hiện chỉ seed/Admin; không đạt toàn bộ tác nhân của Roadmap dù BR/TC core PASS. Roadmap xếp review P1, không gọi là lỗi checkout HIGH |
| Minimal recommendation | Trong remediation riêng: dùng Review entity/repository hiện có, service kiểm owned COMPLETED order chứa SKU, validated rating/comment, unique user-product rule, Customer form/display qua layout hiện có; basic Admin visibility giữ nguyên. Hoặc chủ dự án ghi rõ quyết định hoãn P1 trước claim full completeness |
| Regression needed | Customer đã mua/completed create review; chưa mua/chưacompleted/foreignuser/Staff/Admin reject; duplicate/rating/CSRF; XSS rendering và HIDDEN visibility; actual browser completed→review |

## FSA-02 — Đăng ký Unicode password trả500 thay vì validation

| Field | Detail |
| --- | --- |
| Severity / priority | MEDIUM / P1 — valid-input registration defect |
| Module | Auth web/API validation→BCrypt |
| Source / line | [RegisterRequest.java](../../../src/main/java/com/oneshop/dto/request/RegisterRequest.java) lines22–24; [AuthServiceImpl.java](../../../src/main/java/com/oneshop/service/impl/AuthServiceImpl.java) line75; [auth/register.html](../../../src/main/resources/templates/auth/register.html) password field; AuthController only handles business validation |
| Description | @Size(8..72) kiểm độ dài String, không UTF8 bytes. BCrypt encoder giới hạn72 bytes; register gọi encode trực tiếp, exception không chuyển thành field validation |
| Reproduction | Guest /register, email chưa có, họtên/phone hợp lệ, password `"ấ".repeat(30)` (30 ký tự,90 UTF8bytes). Click Đăng ký |
| Expected | Request được xử lý nhất quán với contract: nếu vượt giới hạn encoder, trả validation có thông báo rõ/password field; không server500 |
| Actual | HTTP500 generic error page “Đã có lỗi xảy ra”; local log `IllegalArgumentException: password cannot be more than 72 bytes`; không User mới |
| Evidence | browser-security-probes.json cuối; browser-network.json POST /register500; screenshots/registration-unicode-error.png; application.log original lines2538–2545; fixture-db.json unicode=[]; registration500 extraction in verified-findings.json |
| Root cause | **Confirmed:** validator/encoder đo khác đơn vị; service không byte guard. AdminUserService đã có guard UTF8 byte cho password create, có thể tái sử dụng quy ước thay vì cơ chế auth mới |
| Impact | Một số mật khẩu hợp lệ theo UI/backend size làm signup gián đoạn. Không thấy ghi DB một phần, bypass, secret leak hoặc auth failure cho password thông thường |
| Minimal recommendation | Remediation riêng: guard UTF8<=72 trước encode cho registration/service, field error và wording rõ giới hạn. Giữ BCrypt; không truncate lặng lẽ hoặc thay hashing algorithm để né test |
| Regression needed | ASCII72bytes accepted/73 rejected; Vietnamese/multibyte boundary72/75bytes; normal registration/roleCUSTOMER; web error field và API4xx không500/no inserted row; basic login regression |

## FSA-03 — Deploy contract dùng Docker trái Roadmap V2

| Field | Detail |
| --- | --- |
| Severity / priority | MEDIUM / P1 — scope/deployment compliance gate |
| Module | Deployment descriptor/documentation, chưa deploy |
| Source / line | [render.yaml](../../../render.yaml) lines4–5 `runtime: docker`/dockerfilePath; [Dockerfile](../../../Dockerfile) lines2,14 build/runtime images; [README](../../../README.md) lines458,585–590. [Roadmap](../../OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang%20(1).md) line26; [13 phases](../../OneShop_PhanChiaGiaiDoan_ThucHien_TheoRoadmapV2.md) line1174 “Không thêm Docker chỉ để deploy” |
| Description / reproduction | Đọc runtime descriptor và hướng dẫn deploy hiện hành, đối chiếu giới hạn Roadmap. Không cần thực thi Docker hoặc deploy để xác nhận mismatch |
| Expected | Strategy triển khai nằm trong phạm vi chốt hoặc có explicit approved exception cập nhật nguồn chuẩn |
| Actual | Docker là deployment path duy nhất được descriptor/README hướng dẫn, có Dockerfile multi-stage. Không có exception approval trong tài liệu được cung cấp cho audit |
| Evidence | Actual source files/dòng và tracked SHA manifest; verified-findings.json chứa citations. **Static confirmed**, không báo Docker build/Render runtime lỗi đã xảy ra |
| Root cause | **Confirmed documentary/config mismatch**; không biết lý do lịch sử chọn Docker hoặc có approval ngoài context hay không |
| Impact | Gate Phase13 “không công nghệ ngoài scope” chưa đạt; có thể dùng deployment path trái yêu cầu học thuật. Không ảnh hưởng stock/payment transactions đã kiểm trên Java jar |
| Minimal recommendation | Chốt yêu cầu trước Phase13: align descriptors/docs với path được roadmap cho phép, hoặc chủ dự án phê duyệt/document exception. Không tự đổi platform/stack, không coi Docker là chuẩn Roadmap vì README có nó |
| Regression needed | Nếu descriptor thay: build artifact Java21, runtime env/profile/PORT/health/SQL-cloud/Cloudinary/JWTHTTPS smoke test ở môi trường được phép; nếu approved exception: consistency/document check. Không deploy trong audit |

## Những giới hạn không phải confirmed findings

TC17 live current/Brand live chưa chạy; target cloud/profileprod chưa kiểm; unbounded Customer Order list/overview8counts-perStore chưa đo impact; primary image upload concurrency và provider/SQL crash compensation chưa tái hiện; full accessibility/contrast/scanner/dependency CVEs chưa kiểm. Không nâng chúng thành CRITICAL/HIGH hoặc tuyên bố PASS/defect dựa trên source alone.

Không phát hiện confirmed oversell, double restore, history duplication, cross-Store unauthorized mutation, negativequantity, total tampering, hard-delete existing history hoặc secret exposure trong scenario hiện tại. Giới hạn này không thay thế exhaustive security/transaction assurance.
''')

write('07_Phase13_Readiness.md','# Phase13 Readiness — Audit Only\n\n'+header+'''**Recommendation: NOT READY theo Roadmap V2 hiện tại.** Core mua hàng đã hoạt động, không có confirmed CRITICAL/HIGH. No-Go ở đây do scope deployment chưa tuân thủ và các gap cần được xử lý/chốt trước claim full completeness; không dùng kết quả này để tự triển khai hoặc sửa production.

## Các điều kiện đã đạt

| Condition | Evidence |
| --- | --- |
| Monolith stack/runtime hiện tại | SpringBoot3.5.16/Java21/JPA/SQLServer/Thymeleaf/Bootstrap/SiteMesh/JWT/Cloudinary giữ nguyên, source353file unchanged |
| Build artifact | Current clean package BUILD SUCCESS, target/oneshop.jar 63,918,707 bytes; SHA256 trong build-summary.json; jar thực khởi động localhost |
| Regression core |927TOTAL/926PASS/0FAIL/0ERROR/1conditionalSKIP,33Phase12hardeningPASS,44suites |
| Chain demo data |5Store (4active/1inactive),7SKU,21StoreProduct, Customer/Staff/Admin,5 historicalOrder; actualSQL counts/relationships |
| Full UI core demo |3Store fixture→3Order→payment/DELIVERY/PICKUP→completed; cancel/revoke/filter/stock/history workflows actualChrome+SQL |
| Data consistency/atomicity |enabledtrusted FK/CHECK, zero integrity query violations; current concurrency/flushrollback tests; all19table exactbaseline aftercleanup |
| Secrets configuration skeleton |DB/JWT/Cloudinary env placeholders, .env ignored, prodJWTcookieSecuretrue/INFOlogging default; auditlog sensitive values exactscan0match |
| Prod connection skeleton |JDBC encrypt=true, trustServerCertificatefalse default; PORT env; Hikari bounded pool5/timeout10000; JPA none default; prod Thymeleaf cache+forward headers strategy |
| Health endpoint |/health localhost actual200 `status:UP`, intentionally liveness withoutDB; không coi là readiness SQLCloud/Cloudinary |

## Điều kiện chưa đạt / phải chốt

1. **FSA-03 scope blocker:** render.yaml runtimeDocker và README chỉ dẫn Docker trái Phase13 prohibition. Phải chốt descriptor tuân thủ hoặc explicit exception có nguồn chuẩn. Không có bằng chứng runtime deploy fail; có bằng chứng scope mismatch.
2. **FSA-02 public signup:** Unicode valid-size500 cần validation đúng encoder và regression; lỗi MEDIUM nhưng nên sửa trước public/demo registration.
3. **FSA-01 completeness:** Customer review P1 thiếu. Hoàn thiện trong remediation được giao hoặc ghi rõ hoãn P1; chưa được báo “toàn bộ Roadmap đã hoàn thiện”. Nó không tự làm core TC-01–18 FAIL.
4. **TC17 currentlive chưarerun:** mock+SQL PASS/historicalProductlivePASS không bằng currentliveBrand/prodprovider verification. User đã giới hạn provider mutation; audit không xin phép để mở scope hoặc upload asset. Khi được giao Phase13 verification cần credentials/permission và fixture cleanup đúng.

## Target production/cloud — NOT VERIFIED

- Chưa có kiểm chứng tài khoản/network/firewall/tls/database quyền/schema trên SQLServer cloud; localTrustServerCertificatetrue không chứng minh cloudcertificate đúng. Dùng nguồnschema hiện có để kiểm compatibility, không chạy destructiveschema/seed trên DB có dữ liệu.
- Chưa chạy Docker build/Render deploy và không sửa Render configuration. Java21 artifact localPASS không chứng minh deployTARGETPASS.
- Chưa xác nhận target environment secret injection, actual profileprod, HTTPS/forwardheaders/Securecookie/cookieCSRF tạiproxy, cloudconnection stable hay Cloudinaryaccountpermissions.
- Prod default ddl-auto=none, optional JPA_DDL_AUTO env có thể override; predeploy review cần giữ schema do SQLscripts quản lý, không suy ra đã validate targetschema khi dùng none. Không thay env hiện tại.
- Health liveness không phụ thuộcDB có chủ đích, chưa đo outage handling/readiness; timeout/pool là cấu hình, không đảm bảo cloud availability.
- Demo seed có password công khai `OneShop@123` theo databaseREADME, chỉdemo. Chưa xác nhận targetaccount/password policy hoặc productioncredentialreadiness; audit không reset user passwords/dữ liệu.
- Không benchmark capacity, imageprimary race, provider-crash recovery mọi cửa sổ hoặc full accessibility. Không tự thêm platform/module/tool thay thế.

## Đề xuất thứ tự sau audit

Giao remediation riêng FSA-02; quyết định FSA-03; chốt FSA-01/P1. Các thay đổi tối thiểu đúng root cause, dùng model/stack hiện hữu, giữ acceptance fail nếu chưa sửa. Sau đó rerun coverage mới và regression (default927; opt-inlive chỉ khi được phép), đảm bảo DB before/after. Chỉ khi scope/defects đã đóng mới đánh giá lại Go/No-Go rồi chuẩn bị targetdemo được giao. Đây là khuyến nghị, không implementation/approval flow tự tạo.

**AUDIT COMPLETED — NOT READY. Không triển khai Phase13, không deploy.** [Executive summary](00_Audit_Executive_Summary.md) và [Issue register](06_Issue_Register.md) là nguồn kết luận; limits ghi ở đây không được diễn giải thành live/prod đã PASS.
''')

inventory_md='# Inventory evidence\n\nCommit: `'+inv['commit']+'`. Full paths and source lines are captured mechanically, not a coverage assertion.\n\n## Endpoint action annotations\n\n| Verb | Path(s) | Controller source / line / method |\n| --- | --- | --- |\n'
inventory_md+=''.join(f"| {e['verb']} | {'; '.join('`'+p+'`' for p in e['paths'])} | `{e['file']}:{e['line']}` — `{e['method']}` |\n" for e in inv['endpoints'])
inventory_md+='\nAdditional framework/filter-managed endpoints: `POST /logout` (SecurityConfig/Spring Security logout handler, verified by actual UI); `/error` (Spring Boot error controller); static `/css`, `/js`, `/images`, `/vendor`. These are not counted among application controller mapping annotations.\n'
for title,key in [('Services / interfaces','services'),('Templates / fragments / layouts','templates'),('Phase report files found','reports')]:
 inventory_md+='\n## '+title+'\n\n'+''.join('- `'+p+'`\n' for p in sorted(inv[key]))
inventory_md+='\n## Test and support Java files\n\n'+''.join('- `'+t['file']+'` ('+str(t['annotations'])+' test annotations; parameterized executions differ)\n' for t in inv['tests'])
(here/'inventory.md').write_text(inventory_md,encoding='utf8')
findings={'confirmed':[{'id':'FSA-01','severity':'MEDIUM','evidence':'Review skeleton + actual completed fixture no action + valid-CSRF404'},{'id':'FSA-02','severity':'MEDIUM','evidence':'Chrome POST register500; log BCrypt password cannot be more than72bytes; SQL user absent'},{'id':'FSA-03','severity':'MEDIUM','evidence':'render.yaml4–5 Docker vs Roadmap26/phase13line1174'}],'critical':0,'high':0,'medium':3,'low':0,'auditStatus':'AUDIT COMPLETED','readiness':'NOT READY','currentTC':{'PASS':17,'FAIL':0,'NOT VERIFIED':1,'BLOCKED':0}}
raw=(here/'application.log').read_text(encoding='utf8',errors='replace')
findings['unicodeExceptionEvidence']=[l for l in raw.splitlines() if 'password cannot be more than 72 bytes' in l][:3]
(here/'verified-findings.json').write_text(json.dumps(findings,ensure_ascii=False,indent=2),encoding='utf8')
print('Wrote8reports + inventory evidence; no production/test/schema changes.')
