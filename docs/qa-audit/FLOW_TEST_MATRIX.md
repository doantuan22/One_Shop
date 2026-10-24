# Function → flow → branch → execution matrix

Tất cả route runtime có guard mapping. Đây KHÔNG phải chứng nhận full business/happy-path/condition coverage. Route ANY chỉ được probe GET trong bảng này; hai handler /error khác produces được kiểm kê riêng trong JSON. Static resources do Spring handler phục vụ không phải từng MVC controller mapping.

| Function | Entry point | Flow / branch | Test source | Execution | Evidence |
|---|---|---|---|---|---|
| R001 | GET / | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R002 | GET /account | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R003 | POST /account | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R004 | GET /admin/brands/{id}/edit | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R005 | POST /admin/brands/{id}/logo/delete | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R006 | POST /admin/brands/{id}/logo | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R007 | POST /admin/brands/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R008 | GET /admin/brands | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R009 | POST /admin/brands | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R010 | GET /admin/categories/{id}/edit | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R011 | POST /admin/categories/{id}/image/delete | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R012 | POST /admin/categories/{id}/image | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R013 | POST /admin/categories/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R014 | GET /admin/categories | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R015 | POST /admin/categories | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R016 | GET /admin/inventory | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R017 | GET /admin/orders/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R018 | POST /admin/orders/{orderId}/fulfillments/{fulfillmentId}/redeliver | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R019 | POST /admin/orders/{orderId}/fulfillments/{fulfillmentId}/undeliverable | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R020 | GET /admin/orders | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R021 | POST /admin/payments/{orderId:\d+}/gateway | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R022 | POST /admin/payments/{orderId:\d+}/recheck | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R023 | POST /admin/payments/{orderId:\d+}/refund | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R024 | GET /admin/payments/{orderId:\d+} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R025 | GET /admin/payments | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R026 | GET /admin/products/new | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R027 | GET /admin/products/suggest | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R028 | GET /admin/products/{id}/edit | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R029 | POST /admin/products/{id}/images/{imageId}/delete | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R030 | POST /admin/products/{id}/images/{imageId}/primary | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R031 | POST /admin/products/{id}/images | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R032 | POST /admin/products/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R033 | GET /admin/products | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R034 | POST /admin/products | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R035 | GET /admin/reviews/suggest | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R036 | POST /admin/reviews/{id}/reply | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R037 | POST /admin/reviews/{id}/status | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R038 | GET /admin/reviews | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R039 | GET /admin/staff-assignments/suggest | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R040 | POST /admin/staff-assignments/transfer | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R041 | POST /admin/staff-assignments/{id}/status | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R042 | GET /admin/staff-assignments | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R043 | POST /admin/staff-assignments | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R044 | GET /admin/store-products/new | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R045 | GET /admin/store-products/{id}/edit | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R046 | POST /admin/store-products/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R047 | GET /admin/store-products | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R048 | POST /admin/store-products | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R049 | GET /admin/stores/new | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R050 | GET /admin/stores/suggest | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R051 | GET /admin/stores/{id}/edit | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R052 | POST /admin/stores/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R053 | GET /admin/stores | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R054 | POST /admin/stores | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R055 | GET /admin/users/new | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R056 | GET /admin/users/suggest | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R057 | GET /admin/users/{id}/edit | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R058 | POST /admin/users/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R059 | GET /admin/users | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R060 | POST /admin/users | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R061 | GET /admin | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R062 | POST /api/auth/login | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R063 | POST /api/auth/register | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R064 | POST /cart/items/{cartItemId}/delete | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R065 | POST /cart/items/{cartItemId} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R066 | POST /cart/items | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R067 | GET /cart | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R068 | GET /checkout/{checkoutId} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R069 | GET /checkout | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R070 | POST /checkout | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R071 | GET /decorators/admin | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R072 | GET /decorators/client | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R073 | GET /decorators/staff | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R074 | GET /error | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R075 | GET /error | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R076 | GET /health | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R077 | GET /image-credits | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R078 | GET /login | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R079 | POST /login | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R080 | POST /logout | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R081 | POST /orders/{orderId}/cancel | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R082 | POST /orders/{orderId}/payments/attempts | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R083 | POST /orders/{orderId}/payments/{paymentId}/failure | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R084 | POST /orders/{orderId}/payments/{paymentId}/simulate | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R085 | POST /orders/{orderId}/payments/{paymentId}/success | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R086 | GET /orders/{orderId}/payments | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R087 | GET /orders/{orderId} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R088 | GET /orders | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R089 | GET /products/suggest | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R090 | GET /products/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R091 | GET /products | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R092 | GET /register | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R093 | POST /register | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R094 | POST /reviews | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R095 | GET /staff/ | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R096 | GET /staff/dashboard | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R097 | GET /staff/fulfillments/delivery | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R098 | GET /staff/fulfillments/pickup | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R099 | POST /staff/fulfillments/{fulfillmentId:\d+}/{actionPath} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R100 | GET /staff/fulfillments/{fulfillmentId:\d+} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R101 | GET /staff/fulfillments | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R102 | GET /staff/inventory-history/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R103 | GET /staff/inventory-history | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R104 | GET /staff/orders/delivery | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R105 | GET /staff/orders/pickup | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R106 | GET /staff/orders/{orderId:\d+} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R107 | GET /staff/orders | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R108 | GET /staff/pickup | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R109 | POST /staff/reviews/{id}/reply | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R110 | GET /staff/reviews/{id} | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R111 | GET /staff/reviews | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R112 | GET /staff/stock/{id}/adjust | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R113 | POST /staff/stock/{id}/adjust | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R114 | GET /staff/stock | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R115 | GET /staff | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R116 | POST /stores/clear | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R117 | POST /stores/select | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |
| R118 | GET /stores | anonymous; wrong roles + valid JWT/CSRF where protected | QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards | PASS | evidence/routes/ |

## Deeper execution mapping

| Module / Flow | Tests | Evidence / coverage caveat |
|---|---|---|
| Auth / JWT / CSRF / profile | Auth*, AccessControl*, AccountControllerTest, QaAuditRace* | baseline/additional-results.json; profile race violates revocation |
| Catalog / Category / Brand / Store | Catalog*, ProductRequestValidationTest, CategoryServiceTest | baseline-results.json; stale UI expectations and finder spec conflict remain |
| Cart ownership / quantity / concurrent writes | Cart*, CentralCartIntegrationTest | baseline-results.json; no claim every UI quantity event covered |
| Allocation / checkout / split / consent / fault / last stock | Checkout*, AllocationEngineTest, QaAuditAllocationInvariantTest | SQL-backed suites vs pure generated tests distinguished |
| Order / Fulfillment / legal & illegal transitions | OrderState*, Fulfillment*, StaffFulfillment*, Delivery*, Pickup* | baseline-results.json; multi-assignment scenarios blocked by canonical constraint |
| Payment / retry / cancel / refund / worker engine | Payment*, PartialRefund*, AdminAttention* | baseline-results.json; simulation only, worker engine not cadence |
| Admin users / assignments / dashboards / inventory | Phase11*, Admin*, Staff* | baseline-results.json; complete unique use-case mapping still open |
| Reviews / reply / moderation / race | Review*, Phase9*, Phase10* | baseline-results.json; conflicting multi-assignment fixtures not PASS |
| Images / primary race / transaction rollback | CloudinaryServiceTest, Phase12History*, QaAuditRace*, QaAuditImageRollback* | actual SQL + simulated provider; no live upload |
| All HTTP readers page/date boundaries | QaAuditBoundaryDatabaseIntegrationTest | evidence/boundaries/: concrete inputs and HTTP status |
| Browser small actions / modals / logout / navigation | QaAuditScreenBrowserDatabaseIntegrationTest | evidence/screens/results.json; individual checkpoint details |
| Browser cross-module order/payment/review journeys | BrowserV3DatabaseIntegrationTest | evidence/browser/: 7 modes + SQL assertions |

## Non-reconciled source candidates

1430 B IDs và 1113 U IDs có file:line/source trong inventory.json. Tất cả còn cần mapping semantic chính xác tới testcase/condition. Không đánh PASS chỉ vì dòng code được chạy. Các F/M/U/B không bị xóa khỏi inventory vì thiếu coverage.
