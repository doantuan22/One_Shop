# Issue Register — Confirmed Findings

Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.

Ba finding confirmed, **0 CRITICAL / 0 HIGH / 3 MEDIUM / 0 LOW**. Không remediation. Priority dưới đây là thứ tự xử lý trước khi tuyên bố phù hợp roadmap/demo, không thay đổi severity thành một lỗi core giao dịch.

## FSA-01 — Customer review sau COMPLETED chưa được triển khai

| Field | Detail |
| --- | --- |
| Severity / priority | MEDIUM / P1 — functionality/completeness |
| Module | Customer Product/Order/Review; khác Admin review management |
| Source / line | [ReviewService.java](../../../src/main/java/com/oneshop/service/ReviewService.java) lines4–6; [ReviewServiceImpl.java](../../../src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java) lines6–9; [Roadmap V2](../../OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang%20%281%29.md) line126, §5 actor Customer / §8.1 ReviewService / P1review |
| Description | Service interface và implementation rỗng; không Customer Review controller, submit/display action trong Order/Product templates. Admin chỉ ACTIVE/HIDDEN review đã tồn tại |
| Reproduction | Login Customer fixture, checkout và complete Order9809 chứa Product503. Mở /orders/9809 và /products/503. Tìm action đánh giá; POST review candidate routes với đúng hidden CSRF |
| Expected | Theo §5 Customer được đánh giá sau COMPLETED; service xác minh đã mua, rating/unique ownership và trả feedback |
| Actual | reviewActions=[], không form/link/API trong toàn endpoint inventory. POST /products/503/reviews và /reviews valid-CSRF đều404; DB không new Review |
| Evidence | browser-journey.json records customer-completed-no-review / review-endpoints-missing; screenshot tương ứng; inventory.json; source skeleton. Đã loại false-positive CSRF403 preliminary probe bằng recheck valid token404 |
| Root cause | **Confirmed:** chỉ bootstrap skeleton dành “later phase”, chưa có nghiệp vụ/customer UI. Không suy đoán lỗi JPA hoặc quyền đã làm route mất |
| Impact | Luồng hậu mua bị thiếu, reviews hiện chỉ seed/Admin; không đạt toàn bộ tác nhân của Roadmap dù BR/TC core PASS. Roadmap xếp review P1, không gọi là lỗi checkout HIGH |
| Minimal recommendation | Trong remediation riêng: dùng Review entity/repository hiện có, service kiểm owned COMPLETED order chứa SKU, validated rating/comment, unique user-product rule, Customer form/display qua layout hiện có; basic Admin visibility giữ nguyên. Hoặc chủ dự án ghi rõ quyết định hoãn P1 trước claim full completeness |
| Regression needed | Customer đã mua/completed create review; chưa mua/chưa COMPLETED/foreign user/Staff/Admin reject; duplicate/rating/CSRF; XSS rendering và HIDDEN visibility; actual browser completed→review |

## FSA-02 — Đăng ký Unicode password trả500 thay vì validation

| Field | Detail |
| --- | --- |
| Severity / priority | MEDIUM / P1 — valid-input registration defect |
| Module | Auth web/API validation→BCrypt |
| Source / line | [RegisterRequest.java](../../../src/main/java/com/oneshop/dto/request/RegisterRequest.java) lines22–24; [AuthServiceImpl.java](../../../src/main/java/com/oneshop/service/impl/AuthServiceImpl.java) line75; [auth/register.html](../../../src/main/resources/templates/auth/register.html) password field; AuthController only handles business validation |
| Description | @Size(8..72) kiểm độ dài String, không UTF8 bytes. BCrypt encoder giới hạn72 bytes; register gọi encode trực tiếp, exception không chuyển thành field validation |
| Reproduction | Guest /register, email chưa có, họtên/phone hợp lệ, password `"ấ".repeat(30)` (30 ký tự,90 UTF8 bytes). Click Đăng ký |
| Expected | Request được xử lý nhất quán với contract: nếu vượt giới hạn encoder, trả validation có thông báo rõ/password field; không server500 |
| Actual | HTTP500 generic error page “Đã có lỗi xảy ra”; local log `IllegalArgumentException: password cannot be more than 72 bytes`; không User mới |
| Evidence | browser-security-probes.json cuối; browser-network.json POST /register500; screenshots/registration-unicode-error.png; application.log original lines2538–2545; fixture-db.json unicode=[]; registration500 extraction in verified-findings.json |
| Root cause | **Confirmed:** validator/encoder đo khác đơn vị; service không byte guard. AdminUserService đã có guard UTF8 byte cho password create, có thể tái sử dụng quy ước thay vì cơ chế auth mới |
| Impact | Một số mật khẩu hợp lệ theo UI/backend size làm signup gián đoạn. Không thấy ghi DB một phần, bypass, secret leak hoặc auth failure cho password thông thường |
| Minimal recommendation | Remediation riêng: guard UTF8<=72 trước encode cho registration/service, field error và wording rõ giới hạn. Giữ BCrypt; không truncate lặng lẽ hoặc thay hashing algorithm để né test |
| Regression needed | ASCII72 bytes accepted/73 rejected; Vietnamese/multibyte boundary72/75 bytes; normal registration/role CUSTOMER; web error field và API4xx không500/no inserted row; basic login regression |

## FSA-03 — Deploy contract dùng Docker trái Roadmap V2

| Field | Detail |
| --- | --- |
| Severity / priority | MEDIUM / P1 — scope/deployment compliance gate |
| Module | Deployment descriptor/documentation, chưa deploy |
| Source / line | [render.yaml](../../../render.yaml) lines4–5 `runtime: docker`/dockerfilePath; [Dockerfile](../../../Dockerfile) lines2,14 build/runtime images; [README](../../../README.md) lines458,585–590. [Roadmap](../../OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang%20%281%29.md) line26; [13 phases](../../OneShop_PhanChiaGiaiDoan_ThucHien_TheoRoadmapV2.md) line1174 “Không thêm Docker chỉ để deploy” |
| Description / reproduction | Đọc runtime descriptor và hướng dẫn deploy hiện hành, đối chiếu giới hạn Roadmap. Không cần thực thi Docker hoặc deploy để xác nhận mismatch |
| Expected | Strategy triển khai nằm trong phạm vi chốt hoặc có explicit approved exception cập nhật nguồn chuẩn |
| Actual | Docker là deployment path duy nhất được descriptor/README hướng dẫn, có Dockerfile multi-stage. Không có exception approval trong tài liệu được cung cấp cho audit |
| Evidence | Actual source files/dòng và tracked SHA manifest; verified-findings.json chứa citations. **Static confirmed**, không báo Docker build/Render runtime lỗi đã xảy ra |
| Root cause | **Confirmed documentary/config mismatch**; không biết lý do lịch sử chọn Docker hoặc có approval ngoài context hay không |
| Impact | Gate Phase13 “không công nghệ ngoài scope” chưa đạt; có thể dùng deployment path trái yêu cầu học thuật. Không ảnh hưởng stock/payment transactions đã kiểm trên Java jar |
| Minimal recommendation | Chốt yêu cầu trước Phase13: align descriptors/docs với path được roadmap cho phép, hoặc chủ dự án phê duyệt/document exception. Không tự đổi platform/stack, không coi Docker là chuẩn Roadmap vì README có nó |
| Regression needed | Nếu descriptor thay: build artifact Java 21, runtime env/profile/PORT/health/SQL cloud/Cloudinary/JWT HTTPS smoke test ở môi trường được phép; nếu approved exception: consistency/document check. Không deploy trong audit |

## Những giới hạn không phải confirmed findings

TC17 live current/Brand live chưa chạy; target cloud/profile prod chưa kiểm; unbounded Customer Order list/overview8counts-perStore chưa đo impact; primary image upload concurrency và provider/SQL crash compensation chưa tái hiện; full accessibility/contrast/scanner/dependency CVEs chưa kiểm. Không nâng chúng thành CRITICAL/HIGH hoặc tuyên bố PASS/defect dựa trên source alone.

Không phát hiện confirmed oversell, double restore, history duplication, cross-Store unauthorized mutation, negativequantity, total tampering, hard-delete existing history hoặc secret exposure trong scenario hiện tại. Giới hạn này không thay thế exhaustive security/transaction assurance.
