# Phase 10.5 – Integration + Verification

Ngày kiểm chứng: **07/10/2026**. Phạm vi: Staff Store Operations Phase 10.1–10.4.

Trạng thái: **PHASE 10.5 DONE – PHASE 10 DONE**. Dừng trước Phase 11.

## Thay đổi và lỗi phát hiện

- Thêm `src/test/java/com/oneshop/Phase10IntegrationWebDatabaseTest.java`: **12 test integration** mới.
- Không phát hiện bug production thuộc Phase 10 qua integration và full regression; không cần sửa production.
- Không sửa production endpoint/service/repository, test cũ, schema hoặc domain; không thêm feature Staff/Admin/WMS/BI.
- Suite mới dùng Tomcat thật, production controllers, JWT/CSRF, services/repositories/transactions và SQL Server thật;
  không mock business rule hoặc DB, không dùng test-only HTTP adapter và không có annotation/assumption skip.
- Test dùng lại fixture/cleanup Phase 9. Sau mỗi test, đối chiếu exact rows/fields/timestamps của chín bảng business,
  metadata User/Store/assignment và Product. Assignment/User tạm được khôi phục trước cleanup; không reseed IDENTITY.
- Build ban đầu gặp `AccessDeniedException` khi javac đóng JAR trong cache Maven ngoài workspace.
  Dùng bản sao cache tại `logs/phase10_5/maven-repository` trong workspace, chạy offline cùng dependency;
  không sửa POM hoặc bỏ kiểm tra. Kiểm thời gian audit tuân theo độ chính xác `DATETIME2(0)` hiện có.

## Kiểm chứng integration

| Nhóm | Minh chứng | Kết quả đã xác minh |
| --- | --- | --- |
| TC-11 | `tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource` (4 test) | Staff A mở Order B: 404, không trả resource/code. Cả 7 fulfillment POST và Stock Adjustment của B: 403; DB trước/sau giống hệt. |
| TC-13 | `tc13PhysicalCountCreatesOneCompleteAuditAndImmediatelyUpdatesDashboardAndExactStock` | Quantity đúng; đúng một STOCK_ADJUST với StoreProduct/before/after/change/authenticated staff/note/time/reference null. Các row/field ngoài mục tiêu giữ nguyên. |
| Scope | TC-11, absent/inactive assignment (2 test), multiple assignments + revoke | Resource id trong URL, query, form/body, JSON GET body và cookie không mở rộng quyền. Không assignment/inactive: 403 trên mọi Staff data route/write. `/staff` landing chỉ cảnh báo, không trả dữ liệu. JWT cũ mất quyền ngay khi revoke. |
| Dashboard/Order/Pickup/Stock | `verifyReads` chạy trước/sau các mutation trong cùng checkout | Metric từng Store, queue IDs/total, pickup states và exact quantity được đối chiếu SQL độc lập; read không ghi DB. Multi-assignment chỉ thấy đúng tập Store. |
| Full flow | `staffOperationsFollowOneMultiStoreCheckoutThroughInventoryDashboardDeliveryAndBothPickups` | Một checkout, ba Store: DELIVERY/COD, PICKUP/ONLINE, PICKUP/PAY_AT_STORE; đúng state/history/actor/timestamps, sibling isolation và OrderItem snapshot. |
| Pickup payment | Full flow và duplicate HTTP completion race | ONLINE chưa PAID bị chặn. Code sai không đổi DB; code đúng hoàn tất. PAY_AT_STORE có một receipt SUCCESS đúng tổng tiền, paid_at trước/đồng thời picked_up_at và history COMPLETED, cùng transaction. Staff không nhìn thấy stored code. |
| Payment failure | `failedOnlineSiblingUpdatesOnlyItsStoreDashboardAndInventoryWhileOtherStaffOrdersContinue` | Chỉ đơn ONLINE mục tiêu CANCELLED/FAILED và hoàn đúng một đơn vị stock; sibling tiếp tục xử lý; dashboard/pickup/history đúng Store. |
| Race HTTP | Adjustment cùng checkout; hai request pickup completion cùng Order | Stock ledger ORDER/STOCK_ADJUST nối tiếp chính xác; checkout snapshot một đơn vị. Pickup có một 302, một 400, đúng một receipt/history completion, không đổi kho hoặc sibling. |
| Rollback/race chi tiết | Suite 10.3/10.4 giữ nguyên | Rollback READY/PAY_AT_STORE/COD; lỗi sau UPDATE stock và INSERT movement SQL rollback cả stock/audit/timestamps; outer rollback và concurrent adjustment kiểm serial before/after. |
| Regression/security | Tất cả suite Phase 1–10.4 | Kiểm lại invalid transition, ownership, roles, CSRF cookie/Bearer/encoded path, pagination/total, inactive Store/User, exact-stock privacy và audit. |

## Test đã chạy

Baseline trước thay đổi: **105/105 PASS, 0 FAIL/ERROR, 0 SKIP**:

| Suite | Test |
| --- | ---: |
| StaffStoreScopeDatabaseIntegrationTest (10.1) | 17 |
| StaffOperationsDatabaseIntegrationTest (10.2) | 23 |
| StaffFulfillmentOperationsDatabaseIntegrationTest (10.3) | 36 |
| StaffInventoryDatabaseIntegrationTest (10.4) | 29 |

Suite integration 10.5 chạy riêng: **12/12 PASS, 0 FAIL/ERROR, 0 SKIP**.

Full regression **clean package**, kết thúc lúc **09:00:55 ngày 07/10/2026 (UTC+7)**:

- **42 suites, 864 tests: 863 PASS, 0 FAIL, 0 ERROR, 1 SKIP**; **BUILD SUCCESS**, thời gian 02:42.
- **117/117 test Phase 10 PASS**: 17 (10.1) + 23 (10.2) + 36 (10.3) + 29 (10.4) + 12 (10.5); không suite/test Phase 10 nào bị skip.
- 852 test Phase 1–10.4 giữ nguyên: 851 PASS và 1 skip baseline. Không sửa test cũ để né lỗi.
- Skip duy nhất: `CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf`.
  `assumeFalse(cloudinaryProperties.isConfigured())` có sẵn để tránh upload Cloudinary thật khi đã có credentials;
  đúng baseline 10.4, không thay đổi điều kiện hoặc cấu hình để skip.
- Đối chiếu trực tiếp từng Surefire XML, bắt buộc đủ năm suite Phase 10 và đúng số test, failures/errors/skips bằng 0.
- JAR build lại: `target/oneshop.jar` (63,873,823 bytes). Schema dev tiếp tục `ddl-auto=validate`.

```powershell
# SQL Server dev có schema + seed hiện tại; .env cấu hình DB. Không chạy đồng thời writer/test suite khác.
mvn -o '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' '-Dtest=StaffStoreScopeDatabaseIntegrationTest,StaffOperationsDatabaseIntegrationTest,StaffFulfillmentOperationsDatabaseIntegrationTest,StaffInventoryDatabaseIntegrationTest' test
mvn -o -e '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' '-Dtest=Phase10IntegrationWebDatabaseTest' test
# Sau khi copy cache vào workspace để tránh lỗi quyền JAR của môi trường:
mvn -o '-Dmaven.repo.local=D:\One_Shop\logs\phase10_5\maven-repository' clean package
```

Suite acceptance 10.5 cố ý yêu cầu SQL Server thật; thiếu DB/seed làm test thất bại thay vì skip.
Không sửa điều kiện chạy của các suite cũ.

Evidence local: `target-phase10_5-baseline.log`, `target-phase10_5-compile-diagnostic.log`,
`target-phase10_5-regression-diagnostic.log`, `target-phase10_5-regression.log`, `target/surefire-reports/TEST-*.xml`,
`target/phase10_5-verification-summary.json`. Log build cuối cùng xác nhận clean/compile/test/JAR/repackage đều thành công.

## Kết luận

- **TC-11 PASS. TC-13 PASS.**
- **Staff scope, Dashboard/Order/Fulfillment/Pickup/Inventory integration PASS.**
- Các test không phát hiện cross-Store leak/mutation, lỗi state transition, pickup payment, inventory audit hoặc rollback/race.
- **Full regression PASS – Maven clean package BUILD SUCCESS.**
- **PHASE 10.5 DONE. PHASE 10 DONE.**

Không commit/deploy hoặc triển khai Phase 11.
