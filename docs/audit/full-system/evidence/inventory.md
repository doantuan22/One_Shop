# Inventory evidence

Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. Full paths and source lines are captured mechanically, not a coverage assertion.

## Endpoint action annotations

| Verb | Path(s) | Controller source / line / method |
| --- | --- | --- |
| GET | `/admin/brands` | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java:36` — `list` |
| GET | `/admin/brands/{id}/edit` | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java:42` — `edit` |
| POST | `/admin/brands` | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java:53` — `create` |
| POST | `/admin/brands/{id}` | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java:67` — `update` |
| POST | `/admin/brands/{id}/logo` | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java:81` — `uploadLogo` |
| POST | `/admin/brands/{id}/logo/delete` | `src/main/java/com/oneshop/controller/admin/AdminBrandController.java:95` — `removeLogo` |
| GET | `/admin/categories` | `src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:30` — `list` |
| GET | `/admin/categories/{id}/edit` | `src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:36` — `edit` |
| POST | `/admin/categories` | `src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:47` — `create` |
| POST | `/admin/categories/{id}` | `src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:61` — `update` |
| GET | `/admin` | `src/main/java/com/oneshop/controller/admin/AdminDashboardController.java:25` — `dashboard` |
| GET | `/admin/inventory` | `src/main/java/com/oneshop/controller/admin/AdminInventoryController.java:18` — `list` |
| GET | `/admin/orders` | `src/main/java/com/oneshop/controller/admin/AdminOrderController.java:15` — `list` |
| GET | `/admin/orders/{id}` | `src/main/java/com/oneshop/controller/admin/AdminOrderController.java:20` — `detail` |
| GET | `/admin/products` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:39` — `list` |
| GET | `/admin/products/new` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:48` — `createForm` |
| GET | `/admin/products/{id}/edit` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:54` — `editForm` |
| POST | `/admin/products` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:68` — `create` |
| POST | `/admin/products/{id}` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:83` — `update` |
| POST | `/admin/products/{id}/images` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:99` — `uploadImage` |
| POST | `/admin/products/{id}/images/{imageId}/primary` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:113` — `setPrimaryImage` |
| POST | `/admin/products/{id}/images/{imageId}/delete` | `src/main/java/com/oneshop/controller/admin/AdminProductController.java:119` — `deleteImage` |
| GET | `/admin/reviews` | `src/main/java/com/oneshop/controller/admin/AdminReviewController.java:14` — `list` |
| POST | `/admin/reviews/{id}/status` | `src/main/java/com/oneshop/controller/admin/AdminReviewController.java:18` — `status` |
| GET | `/admin/staff-assignments` | `src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:22` — `list` |
| POST | `/admin/staff-assignments` | `src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:27` — `assign` |
| POST | `/admin/staff-assignments/{id}/status` | `src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:35` — `status` |
| GET | `/admin/stores` | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java:30` — `list` |
| GET | `/admin/stores/new` | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java:36` — `createForm` |
| GET | `/admin/stores/{id}/edit` | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java:43` — `editForm` |
| POST | `/admin/stores` | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java:62` — `create` |
| POST | `/admin/stores/{id}` | `src/main/java/com/oneshop/controller/admin/AdminStoreController.java:76` — `update` |
| GET | `/admin/store-products` | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:45` — `list` |
| GET | `/admin/store-products/new` | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:57` — `createForm` |
| GET | `/admin/store-products/{id}/edit` | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:65` — `editForm` |
| POST | `/admin/store-products` | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:78` — `create` |
| POST | `/admin/store-products/{id}` | `src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:92` — `update` |
| GET | `/admin/users` | `src/main/java/com/oneshop/controller/admin/AdminUserController.java:20` — `list` |
| GET | `/admin/users/new` | `src/main/java/com/oneshop/controller/admin/AdminUserController.java:24` — `createForm` |
| GET | `/admin/users/{id}/edit` | `src/main/java/com/oneshop/controller/admin/AdminUserController.java:29` — `editForm` |
| POST | `/admin/users` | `src/main/java/com/oneshop/controller/admin/AdminUserController.java:36` — `create` |
| POST | `/admin/users/{id}` | `src/main/java/com/oneshop/controller/admin/AdminUserController.java:44` — `update` |
| POST | `/api/auth/login` | `src/main/java/com/oneshop/controller/api/AuthApiController.java:27` — `login` |
| POST | `/api/auth/register` | `src/main/java/com/oneshop/controller/api/AuthApiController.java:32` — `register` |
| GET | `/health` | `src/main/java/com/oneshop/controller/api/HealthController.java:12` — `?` |
| GET | `/login` | `src/main/java/com/oneshop/controller/client/AuthController.java:31` — `loginForm` |
| POST | `/login` | `src/main/java/com/oneshop/controller/client/AuthController.java:37` — `login` |
| GET | `/register` | `src/main/java/com/oneshop/controller/client/AuthController.java:61` — `registerForm` |
| POST | `/register` | `src/main/java/com/oneshop/controller/client/AuthController.java:67` — `register` |
| GET | `/cart` | `src/main/java/com/oneshop/controller/client/CartController.java:38` — `view` |
| POST | `/cart/items` | `src/main/java/com/oneshop/controller/client/CartController.java:43` — `add` |
| POST | `/cart/items/{cartItemId}` | `src/main/java/com/oneshop/controller/client/CartController.java:57` — `update` |
| POST | `/cart/items/{cartItemId}/delete` | `src/main/java/com/oneshop/controller/client/CartController.java:71` — `remove` |
| GET | `/checkout` | `src/main/java/com/oneshop/controller/client/CheckoutController.java:49` — `form` |
| POST | `/checkout` | `src/main/java/com/oneshop/controller/client/CheckoutController.java:56` — `placeOrder` |
| GET | `/checkout/{checkoutId}` | `src/main/java/com/oneshop/controller/client/CheckoutController.java:81` — `result` |
| GET | `/orders` | `src/main/java/com/oneshop/controller/client/CustomerOrderController.java:19` — `list` |
| GET | `/orders/{orderId}` | `src/main/java/com/oneshop/controller/client/CustomerOrderController.java:23` — `detail` |
| POST | `/orders/{orderId}/cancel` | `src/main/java/com/oneshop/controller/client/CustomerOrderController.java:32` — `cancel` |
| GET | `/` | `src/main/java/com/oneshop/controller/client/HomeController.java:9` — `home` |
| GET | `/orders/{orderId}/payments` | `src/main/java/com/oneshop/controller/client/PaymentController.java:28` — `view` |
| POST | `/orders/{orderId}/payments/attempts` | `src/main/java/com/oneshop/controller/client/PaymentController.java:34` — `start` |
| POST | `/orders/{orderId}/payments/{paymentId}/success` | `src/main/java/com/oneshop/controller/client/PaymentController.java:40` — `success` |
| POST | `/orders/{orderId}/payments/{paymentId}/failure` | `src/main/java/com/oneshop/controller/client/PaymentController.java:46` — `failure` |
| GET | `/products` | `src/main/java/com/oneshop/controller/client/ProductController.java:34` — `list` |
| GET | `/products/{id}` | `src/main/java/com/oneshop/controller/client/ProductController.java:54` — `detail` |
| GET | `/stores` | `src/main/java/com/oneshop/controller/client/StoreController.java:30` — `finder` |
| POST | `/stores/select` | `src/main/java/com/oneshop/controller/client/StoreController.java:42` — `select` |
| POST | `/stores/clear` | `src/main/java/com/oneshop/controller/client/StoreController.java:54` — `clear` |
| GET | `/staff`; `/staff/` | `src/main/java/com/oneshop/controller/staff/StaffDashboardController.java:23` — `landing` |
| GET | `/staff/dashboard` | `src/main/java/com/oneshop/controller/staff/StaffDashboardController.java:30` — `dashboard` |
| GET | `/staff/orders/delivery` | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java:23` — `list` |
| GET | `/staff/orders/delivery/{orderId}` | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java:29` — `detail` |
| POST | `/staff/orders/delivery/{orderId}/prepare` | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java:35` — `prepare` |
| POST | `/staff/orders/delivery/{orderId}/pack` | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java:41` — `pack` |
| POST | `/staff/orders/delivery/{orderId}/ship` | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java:47` — `ship` |
| POST | `/staff/orders/delivery/{orderId}/complete` | `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java:53` — `complete` |
| GET | `/staff/orders` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:21` — `orders` |
| GET | `/staff/orders/{orderId}` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:27` — `order` |
| POST | `/staff/orders/{orderId}/delivery/prepare` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:37` — `prepareDelivery` |
| POST | `/staff/orders/{orderId}/delivery/pack` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:39` — `packDelivery` |
| POST | `/staff/orders/{orderId}/delivery/ship` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:41` — `shipDelivery` |
| POST | `/staff/orders/{orderId}/delivery/complete` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:43` — `completeDelivery` |
| POST | `/staff/orders/{orderId}/pickup/prepare` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:45` — `preparePickup` |
| POST | `/staff/orders/{orderId}/pickup/ready` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:47` — `readyPickup` |
| POST | `/staff/orders/{orderId}/pickup/complete` | `src/main/java/com/oneshop/controller/staff/StaffOrderController.java:49` — `completePickup` |
| GET | `/staff/orders/pickup` | `src/main/java/com/oneshop/controller/staff/StaffPickupController.java:18` — `list` |
| GET | `/staff/orders/pickup/{orderId}` | `src/main/java/com/oneshop/controller/staff/StaffPickupController.java:23` — `detail` |
| POST | `/staff/orders/pickup/{orderId}/prepare` | `src/main/java/com/oneshop/controller/staff/StaffPickupController.java:28` — `prepare` |
| POST | `/staff/orders/pickup/{orderId}/ready` | `src/main/java/com/oneshop/controller/staff/StaffPickupController.java:32` — `ready` |
| POST | `/staff/orders/pickup/{orderId}/complete` | `src/main/java/com/oneshop/controller/staff/StaffPickupController.java:36` — `complete` |
| GET | `/staff/pickup` | `src/main/java/com/oneshop/controller/staff/StaffPickupQueueController.java:13` — `queue` |
| GET | `/staff/stock` | `src/main/java/com/oneshop/controller/staff/StaffStockController.java:22` — `stock` |
| GET | `/staff/inventory-history` | `src/main/java/com/oneshop/controller/staff/StaffStockController.java:24` — `historySelection` |
| GET | `/staff/stock/{id}/adjust` | `src/main/java/com/oneshop/controller/staff/StaffStockController.java:33` — `adjustForm` |
| POST | `/staff/stock/{id}/adjust` | `src/main/java/com/oneshop/controller/staff/StaffStockController.java:40` — `adjust` |
| GET | `/staff/inventory-history/{id}` | `src/main/java/com/oneshop/controller/staff/StaffStockController.java:51` — `history` |

Additional framework/filter-managed endpoints: `POST /logout` (SecurityConfig/Spring Security logout handler, verified by actual UI); `/error` (Spring Boot error controller); static `/css`, `/js`, `/images`, `/vendor`. These are not counted among application controller mapping annotations.

## Services / interfaces

- `src/main/java/com/oneshop/service/AdminOperationsService.java`
- `src/main/java/com/oneshop/service/AdminReviewService.java`
- `src/main/java/com/oneshop/service/AdminStaffAssignmentService.java`
- `src/main/java/com/oneshop/service/AdminUserService.java`
- `src/main/java/com/oneshop/service/AuthService.java`
- `src/main/java/com/oneshop/service/CartService.java`
- `src/main/java/com/oneshop/service/CheckoutService.java`
- `src/main/java/com/oneshop/service/CloudinaryService.java`
- `src/main/java/com/oneshop/service/CustomerOrderService.java`
- `src/main/java/com/oneshop/service/DeliveryFulfillmentService.java`
- `src/main/java/com/oneshop/service/InventoryService.java`
- `src/main/java/com/oneshop/service/OrderService.java`
- `src/main/java/com/oneshop/service/OrderTransitionPolicy.java`
- `src/main/java/com/oneshop/service/OrderViewService.java`
- `src/main/java/com/oneshop/service/PaymentService.java`
- `src/main/java/com/oneshop/service/PickupCodeService.java`
- `src/main/java/com/oneshop/service/PickupFulfillmentService.java`
- `src/main/java/com/oneshop/service/ProductService.java`
- `src/main/java/com/oneshop/service/ReviewService.java`
- `src/main/java/com/oneshop/service/StaffInventoryService.java`
- `src/main/java/com/oneshop/service/StaffOperationsService.java`
- `src/main/java/com/oneshop/service/StaffStoreScopeService.java`
- `src/main/java/com/oneshop/service/StoreProductService.java`
- `src/main/java/com/oneshop/service/StoreService.java`
- `src/main/java/com/oneshop/service/impl/AuthServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/CartServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/CloudinaryServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/OrderServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/ProductServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/StoreServiceImpl.java`

## Templates / fragments / layouts

- `src/main/resources/templates/admin/brands.html`
- `src/main/resources/templates/admin/categories.html`
- `src/main/resources/templates/admin/dashboard.html`
- `src/main/resources/templates/admin/fragments.html`
- `src/main/resources/templates/admin/order-detail.html`
- `src/main/resources/templates/admin/orders.html`
- `src/main/resources/templates/admin/product-form.html`
- `src/main/resources/templates/admin/products.html`
- `src/main/resources/templates/admin/reviews.html`
- `src/main/resources/templates/admin/staff-assignments.html`
- `src/main/resources/templates/admin/store-form.html`
- `src/main/resources/templates/admin/store-product-form.html`
- `src/main/resources/templates/admin/store-products.html`
- `src/main/resources/templates/admin/stores.html`
- `src/main/resources/templates/admin/user-form.html`
- `src/main/resources/templates/admin/users.html`
- `src/main/resources/templates/auth/login.html`
- `src/main/resources/templates/auth/register.html`
- `src/main/resources/templates/cart/index.html`
- `src/main/resources/templates/checkout/index.html`
- `src/main/resources/templates/checkout/result.html`
- `src/main/resources/templates/error-message.html`
- `src/main/resources/templates/error.html`
- `src/main/resources/templates/fragments/admin.html`
- `src/main/resources/templates/fragments/assets.html`
- `src/main/resources/templates/fragments/components.html`
- `src/main/resources/templates/fragments/footer.html`
- `src/main/resources/templates/fragments/header.html`
- `src/main/resources/templates/fragments/navbar.html`
- `src/main/resources/templates/fragments/staff.html`
- `src/main/resources/templates/home/index.html`
- `src/main/resources/templates/layouts/admin.html`
- `src/main/resources/templates/layouts/client.html`
- `src/main/resources/templates/layouts/staff.html`
- `src/main/resources/templates/orders/detail.html`
- `src/main/resources/templates/orders/fragments.html`
- `src/main/resources/templates/orders/index.html`
- `src/main/resources/templates/payment/index.html`
- `src/main/resources/templates/product/detail.html`
- `src/main/resources/templates/product/list.html`
- `src/main/resources/templates/staff/dashboard.html`
- `src/main/resources/templates/staff/delivery/detail.html`
- `src/main/resources/templates/staff/delivery/fragments.html`
- `src/main/resources/templates/staff/delivery/index.html`
- `src/main/resources/templates/staff/orders/detail.html`
- `src/main/resources/templates/staff/orders/fragments.html`
- `src/main/resources/templates/staff/orders/index.html`
- `src/main/resources/templates/staff/pickup/detail.html`
- `src/main/resources/templates/staff/pickup/index.html`
- `src/main/resources/templates/staff/pickup/queue.html`
- `src/main/resources/templates/staff/stock/adjust.html`
- `src/main/resources/templates/staff/stock/history.html`
- `src/main/resources/templates/staff/stock/index.html`
- `src/main/resources/templates/store/list.html`

## Phase report files found

- `docs/Phase10_1_StaffStoreScopeFoundation_Report.md`
- `docs/Phase10_2_StaffDashboardOrderOperations_Report.md`
- `docs/Phase10_3_FulfillmentPickupOperations_Report.md`
- `docs/Phase10_4_StoreInventoryOperations_Report.md`
- `docs/Phase10_5_IntegrationVerification_Report.md`
- `docs/Phase11_AdminChain_Report.md`
- `docs/Phase12_HistoryHardening_Report.md`
- `docs/Phase9_1_PaymentFoundation_Report.md`
- `docs/Phase9_2_OrderStateMachine_Report.md`
- `docs/Phase9_3_DeliveryFlow_Report.md`
- `docs/Phase9_4_StorePickupFlow_Report.md`
- `docs/Phase9_5_IntegrationVerification_Report.md`

## Test and support Java files

- `src/test/java/com/oneshop/AbstractIntegrationTest.java` (0 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/AccessControlIntegrationTest.java` (20 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/AuthDatabaseIntegrationTest.java` (11 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/AuthFlowIntegrationTest.java` (6 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CartDatabaseIntegrationTest.java` (24 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CartHttpDatabaseIntegrationTest.java` (15 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CartWebIntegrationTest.java` (5 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CatalogDatabaseIntegrationTest.java` (30 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CatalogHttpDatabaseIntegrationTest.java` (29 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CatalogWebIntegrationTest.java` (10 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CheckoutConcurrencyDatabaseIntegrationTest.java` (6 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CheckoutDatabaseIntegrationTest.java` (23 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CheckoutHttpDatabaseIntegrationTest.java` (10 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/CheckoutWebIntegrationTest.java` (6 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/DatabaseFoundationIntegrationTest.java` (6 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/DeliveryDatabaseIntegrationTest.java` (18 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/DeliveryLiveSmoke.java` (0 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/DeliveryWebIntegrationTest.java` (8 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/LayoutsIntegrationTest.java` (9 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/OneShopApplicationTests.java` (2 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/OrderStateDatabaseIntegrationTest.java` (8 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/PaymentDatabaseIntegrationTest.java` (21 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/PaymentLiveSmoke.java` (0 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/PaymentWebIntegrationTest.java` (6 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/Phase10IntegrationWebDatabaseTest.java` (8 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/Phase11AdminDatabaseIntegrationTest.java` (20 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/Phase12CloudinaryLiveVerification.java` (1 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/Phase12HistoryHardeningDatabaseIntegrationTest.java` (16 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/Phase9IntegrationLiveSmoke.java` (0 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/Phase9IntegrationScenario.java` (0 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/Phase9IntegrationWebDatabaseTest.java` (10 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/PickupDatabaseIntegrationTest.java` (18 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/PickupLiveSmoke.java` (0 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/PickupWebIntegrationTest.java` (10 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/security/jwt/JwtServiceTest.java` (5 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/SeedGuard.java` (0 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/CartServiceRetryTest.java` (4 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/CloudinaryServiceTest.java` (2 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/CodPaymentServiceTest.java` (6 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/DeliveryFulfillmentServiceTest.java` (10 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/InventoryRestoreTest.java` (4 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/OrderServiceTest.java` (5 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/OrderTransitionPolicyTest.java` (2 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/OrderViewServiceTest.java` (1 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/PayAtStorePaymentServiceTest.java` (6 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/PaymentServiceTest.java` (10 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/service/PickupFulfillmentServiceTest.java` (14 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/StaffFulfillmentOperationsDatabaseIntegrationTest.java` (15 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/StaffInventoryDatabaseIntegrationTest.java` (18 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/StaffOperationsDatabaseIntegrationTest.java` (11 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/StaffStoreScopeDatabaseIntegrationTest.java` (9 test annotations; parameterized executions differ)
- `src/test/java/com/oneshop/WebRoutesIntegrationTest.java` (7 test annotations; parameterized executions differ)
