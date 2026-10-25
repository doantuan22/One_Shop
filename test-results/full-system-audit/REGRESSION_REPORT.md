# REGRESSION REPORT (bằng chứng: test PASS trên SQL Server thật, run14)

| Nhóm | Test đại diện (PASS) |
|---|---|
| Auth/JWT/CSRF/authorization, IDOR, route guard | AuthDatabase, AccessControl, QaAuditRoute (141 route), QaAuditBoundary |
| Catalog/Store/availability | CatalogDatabase, CatalogHttp, QaFixPaging |
| Cart, Checkout, Store Allocation | CartDatabase/CartHttp, CheckoutV3Database, CheckoutV3SplitAndFee, CheckoutV3Concurrency, CheckoutV3Http |
| ONLINE 1 Order nhiều Fulfillment; COD 1 Store; COD nhiều Store cần consent; STORE_PICKUP | CheckoutV3Database be03_02/14/15/16/17, CheckoutV3Http, PickupDatabase |
| Payment success/failure/retry/idempotency, timeout, hoàn tiền mô phỏng | PaymentSettlement (37), PaymentConcurrency (12), PaymentHttp, PartialRefund, PaymentGatewayDisabled, PaymentProductionRuntimeConfig |
| Order status aggregation, Fulfillment | Fulfillment*Database, FulfillmentException |
| Staff Store scope, tồn kho | StaffStoreScope, StaffInventory, StaffOperations (trừ test đa phân công) |
| Hủy đơn/hoàn kho đúng 1 lần | BrowserV3 FAILED/REFUND + `cancelRestores==1`, kiểm tra SQL `double_cancel_restore=0` |
| Review + Staff Reply | Review*Database, ReviewV3Http/Concurrency (trừ 1 test đa phân công) |
| Return, Promotion | OrderReturnDatabase, PromotionDatabase |
| Admin CRUD, Cloudinary/media, security âm tính | Phase11Admin, CatalogHttp, QaFixImageLifecycle, QaAuditImageRollback |

12 bất biến V3: 1–6 và 8–12 có test tự động PASS; bất biến 7 (Staff chỉ xử lý Store được phân công) PASS với phân công 1 chi nhánh, kịch bản đa phân công là NEEDS_DECISION.
Chưa kiểm chứng: gateway thanh toán thật và Cloudinary thật (mock trong test). Việc ánh xạ bất biến → test ở trên dựa trên tên/mô tả test, chưa đọc lại từng assertion.
