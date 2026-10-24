# Function & flow inventory

Kiểm kê cấu trúc; ID F là entry point, không đồng nghĩa một chức năng nghiệp vụ chỉ có một flow. Chi tiết source/hash, methods, UI, enum, branch candidates: `evidence/inventory.json`.

| ID | HTTP | Entry point | Controller:line | Method |
|---|---|---|---|---|
| F001 | GET | /admin/brands | src/main/java/com/oneshop/controller/admin/AdminBrandController.java:33 | list |
| F002 | GET | /admin/brands/{id}/edit | src/main/java/com/oneshop/controller/admin/AdminBrandController.java:41 | edit |
| F003 | POST | /admin/brands | src/main/java/com/oneshop/controller/admin/AdminBrandController.java:54 | create |
| F004 | POST | /admin/brands/{id} | src/main/java/com/oneshop/controller/admin/AdminBrandController.java:68 | update |
| F005 | POST | /admin/brands/{id}/logo | src/main/java/com/oneshop/controller/admin/AdminBrandController.java:82 | uploadLogo |
| F006 | POST | /admin/brands/{id}/logo/delete | src/main/java/com/oneshop/controller/admin/AdminBrandController.java:96 | removeLogo |
| F007 | GET | /admin/categories | src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:34 | list |
| F008 | GET | /admin/categories/{id}/edit | src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:42 | edit |
| F009 | POST | /admin/categories | src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:55 | create |
| F010 | POST | /admin/categories/{id} | src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:72 | update |
| F011 | POST | /admin/categories/{id}/image | src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:90 | uploadImage |
| F012 | POST | /admin/categories/{id}/image/delete | src/main/java/com/oneshop/controller/admin/AdminCategoryController.java:104 | removeImage |
| F013 | GET | /admin | src/main/java/com/oneshop/controller/admin/AdminDashboardController.java:25 | dashboard |
| F014 | POST | /admin/orders/{orderId}/fulfillments/{fulfillmentId}/redeliver | src/main/java/com/oneshop/controller/admin/AdminFulfillmentExceptionController.java:31 | redeliver |
| F015 | POST | /admin/orders/{orderId}/fulfillments/{fulfillmentId}/undeliverable | src/main/java/com/oneshop/controller/admin/AdminFulfillmentExceptionController.java:45 | undeliverable |
| F016 | GET | /admin/inventory | src/main/java/com/oneshop/controller/admin/AdminInventoryController.java:23 | list |
| F017 | GET | /admin/orders | src/main/java/com/oneshop/controller/admin/AdminOrderController.java:28 | list |
| F018 | GET | /admin/orders/{id} | src/main/java/com/oneshop/controller/admin/AdminOrderController.java:54 | detail |
| F019 | GET | /admin/payments | src/main/java/com/oneshop/controller/admin/AdminPaymentController.java:38 | list |
| F020 | GET | /admin/payments/{orderId:\\d+} | src/main/java/com/oneshop/controller/admin/AdminPaymentController.java:58 | detail |
| F021 | POST | /admin/payments/{orderId:\\d+}/recheck | src/main/java/com/oneshop/controller/admin/AdminPaymentController.java:65 | recheck |
| F022 | POST | /admin/payments/{orderId:\\d+}/gateway | src/main/java/com/oneshop/controller/admin/AdminPaymentController.java:72 | gateway |
| F023 | POST | /admin/payments/{orderId:\\d+}/refund | src/main/java/com/oneshop/controller/admin/AdminPaymentController.java:78 | refund |
| F024 | GET | /admin/products/suggest | src/main/java/com/oneshop/controller/admin/AdminProductController.java:42 | suggest |
| F025 | GET | /admin/products | src/main/java/com/oneshop/controller/admin/AdminProductController.java:50 | list |
| F026 | GET | /admin/products/new | src/main/java/com/oneshop/controller/admin/AdminProductController.java:72 | createForm |
| F027 | GET | /admin/products/{id}/edit | src/main/java/com/oneshop/controller/admin/AdminProductController.java:80 | editForm |
| F028 | POST | /admin/products | src/main/java/com/oneshop/controller/admin/AdminProductController.java:98 | create |
| F029 | POST | /admin/products/{id} | src/main/java/com/oneshop/controller/admin/AdminProductController.java:113 | update |
| F030 | POST | /admin/products/{id}/images | src/main/java/com/oneshop/controller/admin/AdminProductController.java:129 | uploadImage |
| F031 | POST | /admin/products/{id}/images/{imageId}/primary | src/main/java/com/oneshop/controller/admin/AdminProductController.java:143 | setPrimaryImage |
| F032 | POST | /admin/products/{id}/images/{imageId}/delete | src/main/java/com/oneshop/controller/admin/AdminProductController.java:149 | deleteImage |
| F033 | GET | /admin/reviews/suggest | src/main/java/com/oneshop/controller/admin/AdminReviewController.java:24 | suggest |
| F034 | GET | /admin/reviews | src/main/java/com/oneshop/controller/admin/AdminReviewController.java:32 | list |
| F035 | POST | /admin/reviews/{id}/status | src/main/java/com/oneshop/controller/admin/AdminReviewController.java:44 | status |
| F036 | POST | /admin/reviews/{id}/reply | src/main/java/com/oneshop/controller/admin/AdminReviewController.java:49 | reply |
| F037 | GET | /admin/staff-assignments/suggest | src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:24 | suggest |
| F038 | GET | /admin/staff-assignments | src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:32 | list |
| F039 | POST | /admin/staff-assignments | src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:40 | assign |
| F040 | POST | /admin/staff-assignments/transfer | src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:48 | transfer |
| F041 | POST | /admin/staff-assignments/{id}/status | src/main/java/com/oneshop/controller/admin/AdminStaffAssignmentController.java:57 | status |
| F042 | GET | /admin/stores/suggest | src/main/java/com/oneshop/controller/admin/AdminStoreController.java:32 | suggest |
| F043 | GET | /admin/stores | src/main/java/com/oneshop/controller/admin/AdminStoreController.java:40 | list |
| F044 | GET | /admin/stores/new | src/main/java/com/oneshop/controller/admin/AdminStoreController.java:59 | createForm |
| F045 | GET | /admin/stores/{id}/edit | src/main/java/com/oneshop/controller/admin/AdminStoreController.java:66 | editForm |
| F046 | POST | /admin/stores | src/main/java/com/oneshop/controller/admin/AdminStoreController.java:85 | create |
| F047 | POST | /admin/stores/{id} | src/main/java/com/oneshop/controller/admin/AdminStoreController.java:99 | update |
| F048 | GET | /admin/store-products | src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:47 | list |
| F049 | GET | /admin/store-products/new | src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:71 | createForm |
| F050 | GET | /admin/store-products/{id}/edit | src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:79 | editForm |
| F051 | POST | /admin/store-products | src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:91 | create |
| F052 | POST | /admin/store-products/{id} | src/main/java/com/oneshop/controller/admin/AdminStoreProductController.java:105 | update |
| F053 | GET | /admin/users/suggest | src/main/java/com/oneshop/controller/admin/AdminUserController.java:21 | suggest |
| F054 | GET | /admin/users | src/main/java/com/oneshop/controller/admin/AdminUserController.java:27 | list |
| F055 | GET | /admin/users/new | src/main/java/com/oneshop/controller/admin/AdminUserController.java:38 | createForm |
| F056 | GET | /admin/users/{id}/edit | src/main/java/com/oneshop/controller/admin/AdminUserController.java:43 | editForm |
| F057 | POST | /admin/users | src/main/java/com/oneshop/controller/admin/AdminUserController.java:50 | create |
| F058 | POST | /admin/users/{id} | src/main/java/com/oneshop/controller/admin/AdminUserController.java:58 | update |
| F059 | POST | /api/auth/login | src/main/java/com/oneshop/controller/api/AuthApiController.java:27 | login |
| F060 | POST | /api/auth/register | src/main/java/com/oneshop/controller/api/AuthApiController.java:32 | register |
| F061 | GET | /health | src/main/java/com/oneshop/controller/api/HealthController.java:12 | health |
| F062 | GET | /account | src/main/java/com/oneshop/controller/client/AccountController.java:39 | profile |
| F063 | POST | /account | src/main/java/com/oneshop/controller/client/AccountController.java:52 | updateProfile |
| F064 | GET | /login | src/main/java/com/oneshop/controller/client/AuthController.java:31 | loginForm |
| F065 | POST | /login | src/main/java/com/oneshop/controller/client/AuthController.java:37 | login |
| F066 | GET | /register | src/main/java/com/oneshop/controller/client/AuthController.java:61 | registerForm |
| F067 | POST | /register | src/main/java/com/oneshop/controller/client/AuthController.java:67 | register |
| F068 | GET | /cart | src/main/java/com/oneshop/controller/client/CartController.java:38 | view |
| F069 | POST | /cart/items | src/main/java/com/oneshop/controller/client/CartController.java:43 | add |
| F070 | POST | /cart/items/{cartItemId} | src/main/java/com/oneshop/controller/client/CartController.java:57 | update |
| F071 | POST | /cart/items/{cartItemId}/delete | src/main/java/com/oneshop/controller/client/CartController.java:71 | remove |
| F072 | GET | /checkout | src/main/java/com/oneshop/controller/client/CheckoutController.java:50 | form |
| F073 | POST | /checkout | src/main/java/com/oneshop/controller/client/CheckoutController.java:67 | placeOrder |
| F074 | GET | /checkout/{checkoutId} | src/main/java/com/oneshop/controller/client/CheckoutController.java:100 | result |
| F075 | GET | /orders | src/main/java/com/oneshop/controller/client/CustomerOrderController.java:24 | list |
| F076 | GET | /orders/{orderId} | src/main/java/com/oneshop/controller/client/CustomerOrderController.java:28 | detail |
| F077 | POST | /orders/{orderId}/cancel | src/main/java/com/oneshop/controller/client/CustomerOrderController.java:39 | cancel |
| F078 | GET | / | src/main/java/com/oneshop/controller/client/HomeController.java:47 | home |
| F079 | GET | /image-credits | src/main/java/com/oneshop/controller/client/ImageCreditsController.java:18 | imageCredits |
| F080 | GET | /orders/{orderId}/payments | src/main/java/com/oneshop/controller/client/PaymentController.java:33 | view |
| F081 | POST | /orders/{orderId}/payments/attempts | src/main/java/com/oneshop/controller/client/PaymentController.java:39 | start |
| F082 | POST | /orders/{orderId}/payments/{paymentId}/simulate | src/main/java/com/oneshop/controller/client/PaymentController.java:49 | simulate |
| F083 | POST | /orders/{orderId}/payments/{paymentId}/success | src/main/java/com/oneshop/controller/client/PaymentController.java:57 | success |
| F084 | POST | /orders/{orderId}/payments/{paymentId}/failure | src/main/java/com/oneshop/controller/client/PaymentController.java:63 | failure |
| F085 | GET | /products/suggest | src/main/java/com/oneshop/controller/client/ProductController.java:40 | suggest |
| F086 | GET | /products | src/main/java/com/oneshop/controller/client/ProductController.java:48 | list |
| F087 | GET | /products/{id} | src/main/java/com/oneshop/controller/client/ProductController.java:64 | detail |
| F088 | POST | /reviews | src/main/java/com/oneshop/controller/client/ReviewController.java:30 | submit |
| F089 | GET | /stores | src/main/java/com/oneshop/controller/client/StoreController.java:30 | finder |
| F090 | POST | /stores/select | src/main/java/com/oneshop/controller/client/StoreController.java:42 | select |
| F091 | POST | /stores/clear | src/main/java/com/oneshop/controller/client/StoreController.java:54 | clear |
| F092 | GET | /staff | src/main/java/com/oneshop/controller/staff/StaffDashboardController.java:23 | landing |
| F093 | GET | /staff/ | src/main/java/com/oneshop/controller/staff/StaffDashboardController.java:23 | landing |
| F094 | GET | /staff/dashboard | src/main/java/com/oneshop/controller/staff/StaffDashboardController.java:30 | dashboard |
| F095 | GET | /staff/fulfillments | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:41 | queue |
| F096 | GET | /staff/orders | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:41 | queue |
| F097 | GET | /staff/fulfillments/delivery | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:57 | deliveryQueue |
| F098 | GET | /staff/fulfillments/pickup | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:71 | pickupQueue |
| F099 | GET | /staff/fulfillments/{fulfillmentId:\\d+} | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:84 | detail |
| F100 | POST | /staff/fulfillments/{fulfillmentId:\\d+}/{actionPath} | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:96 | act |
| F101 | GET | /staff/orders/{orderId:\\d+} | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:121 | legacyOrder |
| F102 | GET | /staff/orders/delivery | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:126 | legacyDelivery |
| F103 | GET | /staff/orders/pickup | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:131 | legacyPickup |
| F104 | GET | /staff/pickup | src/main/java/com/oneshop/controller/staff/StaffFulfillmentController.java:131 | legacyPickup |
| F105 | GET | /staff/reviews | src/main/java/com/oneshop/controller/staff/StaffReviewController.java:23 | list |
| F106 | POST | /staff/reviews/{id}/reply | src/main/java/com/oneshop/controller/staff/StaffReviewController.java:29 | reply |
| F107 | GET | /staff/reviews/{id} | src/main/java/com/oneshop/controller/staff/StaffReviewController.java:41 | detail |
| F108 | GET | /staff/stock | src/main/java/com/oneshop/controller/staff/StaffStockController.java:32 | stock |
| F109 | GET | /staff/inventory-history | src/main/java/com/oneshop/controller/staff/StaffStockController.java:48 | historySelection |
| F110 | GET | /staff/stock/{id}/adjust | src/main/java/com/oneshop/controller/staff/StaffStockController.java:72 | adjustForm |
| F111 | POST | /staff/stock/{id}/adjust | src/main/java/com/oneshop/controller/staff/StaffStockController.java:80 | adjust |
| F112 | GET | /staff/inventory-history/{id} | src/main/java/com/oneshop/controller/staff/StaffStockController.java:92 | history |
| F113 | ANY | /decorators/client | src/main/java/com/oneshop/controller/web/DecoratorController.java:29 | client |
| F114 | ANY | /decorators/staff | src/main/java/com/oneshop/controller/web/DecoratorController.java:34 | staff |
| F115 | ANY | /decorators/admin | src/main/java/com/oneshop/controller/web/DecoratorController.java:39 | admin |
| TECH-LOGOUT | framework | POST /logout | SecurityConfig / Spring Boot | technical |
| TECH-ERROR | framework | ANY /error | SecurityConfig / Spring Boot | technical |

## Service methods

| ID | Source | Method | Inputs |
|---|---|---|---|
| M0775 | src/main/java/com/oneshop/security/service/AppUserDetails.java:29 | getUserId |  |
| M0776 | src/main/java/com/oneshop/security/service/AppUserDetails.java:33 | getFullName |  |
| M0777 | src/main/java/com/oneshop/security/service/CustomUserDetailsService.java:28 | loadUserByUsername | String identifier |
| M0778 | src/main/java/com/oneshop/security/service/CustomUserDetailsService.java:37 | loadUserById | Long userId |
| M0779 | src/main/java/com/oneshop/security/service/CustomUserDetailsService.java:42 | toDetails | User user |
| M0782 | src/main/java/com/oneshop/service/AdminOperationsService.java:36 | getChainSummary |  |
| M0783 | src/main/java/com/oneshop/service/AdminOperationsService.java:41 | getOrders | Long storeId, String q, OrderStatus orderStatus,
             OrderPaymentStatus paymentStatus, FulfillmentType fulfillmentType,
             java.time.LocalDate from, java.time.LocalDate to, int page |
| M0784 | src/main/java/com/oneshop/service/AdminOperationsService.java:59 | getOrders | Long storeId, int page |
| M0785 | src/main/java/com/oneshop/service/AdminOperationsService.java:63 | getOrdersNeedingAttention | Long storeId, int page |
| M0786 | src/main/java/com/oneshop/service/AdminOperationsService.java:69 | summary | Order o |
| M0787 | src/main/java/com/oneshop/service/AdminOperationsService.java:77 | orderQuery | String q |
| M0788 | src/main/java/com/oneshop/service/AdminOperationsService.java:83 | likePattern | String q |
| M0789 | src/main/java/com/oneshop/service/AdminOperationsService.java:91 | startOfDay | java.time.LocalDate date |
| M0790 | src/main/java/com/oneshop/service/AdminOperationsService.java:96 | endExclusiveOf | java.time.LocalDate date |
| M0791 | src/main/java/com/oneshop/service/AdminOperationsService.java:99 | getOrder | Long id |
| M0792 | src/main/java/com/oneshop/service/AdminOperationsService.java:104 | getOverview | Long storeId |
| M0793 | src/main/java/com/oneshop/service/AdminReviewService.java:30 | getReviews | int page |
| M0794 | src/main/java/com/oneshop/service/AdminReviewService.java:39 | getReviews | int page, String q, VisibilityStatus status, Boolean replied |
| M0795 | src/main/java/com/oneshop/service/AdminReviewService.java:62 | adminSpec | String q, VisibilityStatus status, Boolean replied |
| M0796 | src/main/java/com/oneshop/service/AdminReviewService.java:89 | AdminReviewStats | long total, long unanswered, long hidden |
| M0797 | src/main/java/com/oneshop/service/AdminReviewService.java:90 | stats |  |
| M0798 | src/main/java/com/oneshop/service/AdminReviewService.java:96 | setStatus | Long id, @NotNull VisibilityStatus status |
| M0799 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:33 | getAssignments | Long storeId, int page |
| M0800 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:38 | searchAssignments | Long storeId, String q, ActiveStatus status, int page |
| M0801 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:53 | likeOf | String q |
| M0802 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:63 | assign | @NotNull @Valid StaffAssignmentRequest request |
| M0803 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:83 | setStatus | Long id, @NotNull ActiveStatus status, String reason |
| M0804 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:105 | transfer | @NotNull @Valid StaffTransferRequest request |
| M0805 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:135 | requireNoOtherWorkplace | User user, Long storeId, String field |
| M0806 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:147 | getWorkingStaffIds |  |
| M0807 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:151 | requireActive | User user, Store store, ActiveStatus status |
| M0808 | src/main/java/com/oneshop/service/AdminStaffAssignmentService.java:156 | response | StaffStoreAssignment a |
| M0809 | src/main/java/com/oneshop/service/AdminUserService.java:39 | getUsers | RoleName role, int page |
| M0810 | src/main/java/com/oneshop/service/AdminUserService.java:44 | suggestions |  |
| M0811 | src/main/java/com/oneshop/service/AdminUserService.java:49 | suggestions | RoleName role |
| M0812 | src/main/java/com/oneshop/service/AdminUserService.java:58 | searchUsers | RoleName role, ActiveStatus status, String q, int page |
| M0813 | src/main/java/com/oneshop/service/AdminUserService.java:72 | likeOf | String q |
| M0814 | src/main/java/com/oneshop/service/AdminUserService.java:80 | getUser | Long id |
| M0815 | src/main/java/com/oneshop/service/AdminUserService.java:82 | getStaffAccounts |  |
| M0816 | src/main/java/com/oneshop/service/AdminUserService.java:87 | create | @NotNull @Valid AdminUserRequest request |
| M0817 | src/main/java/com/oneshop/service/AdminUserService.java:108 | update | Long id, @NotNull @Valid AdminUserRequest request |
| M0818 | src/main/java/com/oneshop/service/AdminUserService.java:137 | userName | AdminUserRequest request |
| M0819 | src/main/java/com/oneshop/service/AdminUserService.java:141 | apply | User user, AdminUserRequest request |
| M0820 | src/main/java/com/oneshop/service/AdminUserService.java:148 | user | Long id |
| M0821 | src/main/java/com/oneshop/service/AdminUserService.java:149 | notFound |  |
| M0822 | src/main/java/com/oneshop/service/AdminUserService.java:150 | response | User user |
| M0823 | src/main/java/com/oneshop/service/allocation/AllocatedLine.java:4 | AllocatedLine | Long productId, int quantity, Long storeProductId |
| M0824 | src/main/java/com/oneshop/service/allocation/AllocationDemand.java:4 | AllocationDemand | Long productId, int quantity |
| M0825 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:42 | allocate | Collection<AllocationDemand> demands, Collection<StockOffer> offers |
| M0826 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:150 | mustSplit | Long productId, int remaining, Map<Long, Map<Long, Integer>> left |
| M0827 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:160 | better | int full, long units, boolean used, long total, int bestFull, long bestUnits,                                   boolean bestUsed, long bestTotal |
| M0828 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:175 | allocateAtStore | Long storeId, Collection<AllocationDemand> demands,                                                    Collection<StockOffer> offers |
| M0829 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:200 | merge | Collection<AllocationDemand> demands |
| M0830 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:211 | byStore | Collection<StockOffer> offers |
| M0831 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:221 | coversAll | Map<Long, StockOffer> offersOfStore, Map<Long, Integer> wanted |
| M0832 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:231 | totalAvailable | Map<Long, StockOffer> offersOfStore, Collection<Long> productIds |
| M0833 | src/main/java/com/oneshop/service/allocation/AllocationEngine.java:242 | allocation | Long storeId, Map<Long, StockOffer> offersOfStore,                                               Map<Long, Integer> quantities |
| M0834 | src/main/java/com/oneshop/service/allocation/AllocationResult.java:13 | AllocationResult | List<StoreAllocation> stores, AllocationFailure failure,                                List<Long> unfulfilledProductIds |
| M0835 | src/main/java/com/oneshop/service/allocation/AllocationResult.java:16 | success | List<StoreAllocation> stores |
| M0836 | src/main/java/com/oneshop/service/allocation/AllocationResult.java:20 | failure | AllocationFailure failure, List<Long> unfulfilledProductIds |
| M0837 | src/main/java/com/oneshop/service/allocation/AllocationResult.java:24 | success |  |
| M0838 | src/main/java/com/oneshop/service/allocation/AllocationResult.java:29 | fulfillmentCount |  |
| M0839 | src/main/java/com/oneshop/service/allocation/AllocationResult.java:33 | singleStore |  |
| M0840 | src/main/java/com/oneshop/service/allocation/AllocationResult.java:42 | signature |  |
| M0841 | src/main/java/com/oneshop/service/allocation/DeliveryServiceAreaPolicy.java:26 | isDecided |  |
| M0842 | src/main/java/com/oneshop/service/allocation/DeliveryServiceAreaPolicy.java:31 | canServe | Store store, String shippingAddress |
| M0843 | src/main/java/com/oneshop/service/allocation/LockedAllocation.java:13 | LockedAllocation | AllocationResult result, Map<Long, StoreProduct> lockedStoreProducts |
| M0844 | src/main/java/com/oneshop/service/allocation/ShippingAddressValidator.java:25 | requireValid | String address |
| M0845 | src/main/java/com/oneshop/service/allocation/ShippingAddressValidator.java:42 | invalid | String message |
| M0846 | src/main/java/com/oneshop/service/allocation/ShippingFeeCalculator.java:28 | feePerOrder | FulfillmentType type |
| M0847 | src/main/java/com/oneshop/service/allocation/StockOffer.java:7 | StockOffer | Long storeProductId, Long storeId, Long productId, int available |
| M0848 | src/main/java/com/oneshop/service/allocation/StoreAllocation.java:6 | StoreAllocation | Long storeId, List<AllocatedLine> lines |
| M0849 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:107 | label |  |
| M0850 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:111 | fromKey | String key |
| M0851 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:134 | Outcome | Kind kind, OrderStatus orderStatus, boolean orderClosed, boolean partiallyClosed,                           boolean stockRestored, PartialRefundService.Result refund, int failedLeft |
| M0852 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:170 | getException | Long orderId |
| M0853 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:222 | redeliver | Long orderId, Long fulfillmentId, boolean goodsAtStore, String reason |
| M0854 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:258 | markUndeliverable | Long orderId, Long fulfillmentId, GoodsStatus goods, String reason |
| M0855 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:322 | redeliverBlocked | Order order, OrderFulfillment part |
| M0856 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:349 | undeliverableBlocked | Order order, OrderFulfillment part, List<OrderFulfillment> parts |
| M0857 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:370 | Locked | Order order, OrderFulfillment part, List<OrderFulfillment> parts |
| M0858 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:374 | lock | Long orderId, Long fulfillmentId |
| M0859 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:387 | requireFailed | Order order, OrderFulfillment part |
| M0860 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:398 | history | OrderFulfillment part, OrderFulfillmentStatus to, User admin, String note |
| M0861 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:412 | settle | Order order, List<OrderFulfillment> parts, User admin, String note |
| M0862 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:427 | failedLeft | List<OrderFulfillment> parts |
| M0863 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:431 | money | Order order, List<OrderFulfillment> parts,                                                     Map<Long, List<OrderItem>> lines, List<Payment> rows |
| M0864 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:456 | failureReason | OrderFulfillmentStatusHistory failure |
| M0865 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:466 | lastGoodsStatus | List<OrderFulfillmentStatusHistory> history |
| M0866 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:480 | sum | List<OrderItem> lines |
| M0867 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:484 | reason | String reason |
| M0868 | src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:492 | currentAdmin |  |
| M0869 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:44 | getPath |  |
| M0870 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:49 | fromPath | String path |
| M0871 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:59 | getType |  |
| M0872 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:63 | getFrom |  |
| M0873 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:67 | getTo |  |
| M0874 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:71 | getLabel |  |
| M0875 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:75 | appliesTo | FulfillmentType fulfillmentType |
| M0876 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:80 | isFailure |  |
| M0877 | src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:84 | isCompletion |  |
| M0878 | src/main/java/com/oneshop/service/fulfillment/FulfillmentQueueFilter.java:14 | FulfillmentQueueFilter | Long storeId, OrderFulfillmentStatus status, FulfillmentType type,                                      Long orderId, LocalDate from, LocalDate to, boolean openOnly |
| M0879 | src/main/java/com/oneshop/service/fulfillment/FulfillmentQueueFilter.java:17 | none |  |
| M0880 | src/main/java/com/oneshop/service/fulfillment/FulfillmentQueueFilter.java:21 | openWork |  |
| M0881 | src/main/java/com/oneshop/service/fulfillment/OrderStatusAggregator.java:50 | derive | Collection<OrderFulfillmentStatus> statuses, boolean paid |
| M0882 | src/main/java/com/oneshop/service/fulfillment/OrderStatusAggregator.java:99 | recompute | Order lockedOrder, User actor, String note |
| M0883 | src/main/java/com/oneshop/service/fulfillment/PickupAttemptLimiter.java:39 | requireNotLocked | Long fulfillmentId |
| M0884 | src/main/java/com/oneshop/service/fulfillment/PickupAttemptLimiter.java:56 | recordFailure | Long fulfillmentId |
| M0885 | src/main/java/com/oneshop/service/fulfillment/PickupAttemptLimiter.java:76 | reset | Long fulfillmentId |
| M0886 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:109 | getQueue | FulfillmentQueueFilter filter, int page |
| M0887 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:116 | getQueue | FulfillmentQueueFilter filter, int page, String customer |
| M0888 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:152 | likePattern | String customer |
| M0889 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:162 | getRecent | Collection<Long> storeIds, int size |
| M0890 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:171 | getFulfillment | Long fulfillmentId |
| M0891 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:196 | redactPickupCode | String text, OrderFulfillment fulfillment |
| M0892 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:208 | perform | Long fulfillmentId, FulfillmentAction action, String input |
| M0893 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:308 | handover | Long fulfillmentId, String pickupCode |
| M0894 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:323 | requireInScope | Long fulfillmentId, Collection<Long> assigned |
| M0895 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:335 | currentStaff |  |
| M0896 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:343 | requireWorkable | Order order |
| M0897 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:350 | blockedCode | Order order |
| M0898 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:367 | blockedMessage | String code |
| M0899 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:378 | requireMethodMatchesType | Order order, OrderFulfillment f |
| M0900 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:389 | newPickupCode | Long storeId |
| M0901 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:400 | trackingCode | String input |
| M0902 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:411 | reason | String input |
| M0903 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:419 | toQueueItems | Page<OrderFulfillment> rows |
| M0904 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:427 | toItem | OrderFulfillment f, List<OrderItem> lines |
| M0905 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:442 | openStatuses |  |
| M0906 | src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java:447 | parseDate | String text |
| M0907 | src/main/java/com/oneshop/service/ImageCreditsService.java:20 | Item | String sku, String name, String author, String obfCode, String obfPage |
| M0908 | src/main/java/com/oneshop/service/ImageCreditsService.java:23 | Credits | String verifiedOn, String license, String licenseUrl, String source, String sourceUrl, List<Item> items |
| M0909 | src/main/java/com/oneshop/service/ImageCreditsService.java:38 | getCredits |  |
| M0910 | src/main/java/com/oneshop/service/impl/AuthServiceImpl.java:54 | login | LoginRequest request |
| M0911 | src/main/java/com/oneshop/service/impl/AuthServiceImpl.java:70 | register | RegisterRequest request |
| M0912 | src/main/java/com/oneshop/service/impl/AuthServiceImpl.java:109 | normalize | String email |
| M0913 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:83 | getCart | String customerEmail |
| M0914 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:91 | getSelection | String customerEmail, Collection<Long> cartItemIds |
| M0915 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:116 | addItem | String customerEmail, AddCartItemRequest request |
| M0916 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:126 | doAddItem | String customerEmail, AddCartItemRequest request |
| M0917 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:201 | getChainAvailableStock | Long productId |
| M0918 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:207 | createCart | User user |
| M0919 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:215 | updateItemQuantity | String customerEmail, Long cartItemId, int quantity |
| M0920 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:238 | removeItem | String customerEmail, Long cartItemId |
| M0921 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:243 | ownItem | String customerEmail, Long cartItemId |
| M0922 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:250 | requirePositive | Integer quantity |
| M0923 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:260 | toResponse | List<CartItem> items |
| M0924 | src/main/java/com/oneshop/service/impl/CartServiceImpl.java:362 | purchasableSum | List<CartItemResponse> items |
| M0925 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:159 | prepare | String customerEmail, Collection<Long> cartItemIds |
| M0926 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:165 | preview | String customerEmail, Collection<Long> cartItemIds,                                            FulfillmentType fulfillmentType, PaymentMethod paymentMethod,                                            Long pickupStoreId, String shippingAddress |
| M0927 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:227 | planOf | AllocationResult result, CartResponse selection |
| M0928 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:253 | deliveryBlockedReason |  |
| M0929 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:263 | productNames | CartResponse selection |
| M0930 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:268 | failureMessage | AllocationResult result, Map<Long, String> names |
| M0931 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:284 | placeOrder | String customerEmail, PlaceOrderRequest request |
| M0932 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:394 | createOrder | CheckoutSession session, User user, PlaceOrderRequest request,                              List<StoreAllocation> stores, BigDecimal total, BigDecimal fee,                              Map<Long, CartItem> lineByProduct, LockedAllocation locked |
| M0933 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:477 | contextSignature | Long userId, FulfillmentType type, PaymentMethod method,                                            Long pickupStoreId, String address, Collection<AllocationDemand> demands,                                            AllocationResult result, BigDecimal fee, int orderCount, BigDecimal total |
| M0934 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:497 | money | BigDecimal value |
| M0935 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:501 | normalizedAddress | String address |
| M0936 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:506 | addressProblem | String address |
| M0937 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:517 | requireValidChoice | PlaceOrderRequest request |
| M0938 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:550 | requireOpenFlow | PlaceOrderRequest request |
| M0939 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:560 | deliveryPolicyPending |  |
| M0940 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:570 | getCheckout | String customerEmail, Long checkoutId |
| M0941 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:575 | toResult | CheckoutSession session |
| M0942 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:607 | itemOf | OrderItem item |
| M0943 | src/main/java/com/oneshop/service/impl/CheckoutServiceImpl.java:612 | user | String customerEmail |
| M0944 | src/main/java/com/oneshop/service/impl/CloudinaryServiceImpl.java:39 | uploadImage | MultipartFile file, String subFolder |
| M0945 | src/main/java/com/oneshop/service/impl/CloudinaryServiceImpl.java:89 | deleteImage | String publicId |
| M0946 | src/main/java/com/oneshop/service/impl/CloudinaryServiceImpl.java:107 | isInvalidImage | RuntimeException ex |
| M0947 | src/main/java/com/oneshop/service/impl/CloudinaryServiceImpl.java:117 | requireConfigured |  |
| M0948 | src/main/java/com/oneshop/service/impl/CloudinaryServiceImpl.java:124 | folderFor | String subFolder |
| M0949 | src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java:34 | requireCustomer | String email |
| M0950 | src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java:38 | getOrders | String email |
| M0951 | src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java:42 | getOrder | String email, Long id |
| M0952 | src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java:60 | cancelOrder | String email, Long id |
| M0953 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:44 | refuseWhenFulfillmentsExist | Long orderId |
| M0954 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:65 | getDeliveryOrders | String staffEmail |
| M0955 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:71 | getDeliveryOrder | String staffEmail, Long orderId |
| M0956 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:90 | startPreparingDelivery | String staffEmail, Long orderId |
| M0957 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:94 | markDeliveryPacked | String staffEmail, Long orderId |
| M0958 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:98 | markDeliveryShipping | String staffEmail, Long orderId |
| M0959 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:102 | completeDelivery | String staffEmail, Long orderId |
| M0960 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:104 | perform | String staffEmail, Long orderId, DeliveryAction action |
| M0961 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:141 | assignedIds | String staffEmail |
| M0962 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:147 | paymentAllowsFulfillment | Order order |
| M0963 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:153 | getNextAction | Order order |
| M0964 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:155 | nextAction | Order order |
| M0965 | src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java:161 | summary | Order order |
| M0966 | src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java:48 | adjustStock | Long storeProductId, int newQuantity, String userEmail, String note |
| M0967 | src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java:78 | deductForOrder | StoreProduct lockedStoreProduct, int quantity, Order order |
| M0968 | src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java:105 | restoreForCancelledOrder | Order order |
| M0969 | src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java:111 | restoreForCancelledOrder | Order order, String note |
| M0970 | src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java:117 | restoreForUndeliverableFulfillment | Order order, com.oneshop.entity.OrderFulfillment fulfillment,
                                                    String note |
| M0971 | src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java:126 | restoreLines | Order order, List<OrderItem> lines, String note |
| M0972 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:43 | transition | Long orderId, OrderStatus expectedStatus, OrderStatus targetStatus, User actor, String note |
| M0973 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:52 | confirmAfterOnlinePayment | Order order |
| M0974 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:60 | cancelAfterOnlinePaymentFailure | Order order |
| M0975 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:66 | apply | Order order, OrderStatus expected, OrderStatus target, Cause cause, User actor, String note |
| M0976 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:76 | cancelByCustomer | Order order, User customer |
| M0977 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:101 | cancelFulfillments | Order order, User actor, String note |
| M0978 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:126 | writeStatusAndHistory | Order order, OrderStatus target, User actor, String note |
| M0979 | src/main/java/com/oneshop/service/impl/OrderServiceImpl.java:150 | requireOnline | Order order |
| M0980 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:65 | getOrderPayments | String customerEmail, Long orderId |
| M0981 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:87 | createOnlinePaymentAttempt | String customerEmail, Long orderId |
| M0982 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:105 | markOnlinePaymentSuccess | String customerEmail, Long orderId, Long paymentId |
| M0983 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:110 | markOnlinePaymentFailed | String customerEmail, Long orderId, Long paymentId |
| M0984 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:116 | recordCodCollected | Order order |
| M0985 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:128 | recordPayAtStoreCollected | Order order |
| M0986 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:148 | recordOfflineCollected | Order order, OrderFulfillment fulfillment |
| M0987 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:176 | collectOffline | Order order |
| M0988 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:193 | initializeOnlineAttempt | Order order |
| M0989 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:214 | closePendingForCustomerCancellation | Order order |
| M0990 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:237 | ownLockedOrder | String customerEmail, Long orderId |
| M0991 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:246 | requirePayable | Order order |
| M0992 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:258 | requireConsistent | Payment payment, Order order |
| M0993 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:265 | orderNotFound |  |
| M0994 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:269 | transactionCode |  |
| M0995 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:272 | mockReference |  |
| M0996 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:274 | browserConfirmationRefused |  |
| M0997 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:279 | storeLabel | Order order |
| M0998 | src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java:287 | response | Payment payment |
| M0999 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:36 | refuseWhenFulfillmentsExist | Long orderId |
| M1000 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:47 | getPickupOrders | String email |
| M1001 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:51 | getPickupOrder | String email, Long id |
| M1002 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:58 | getNextAction | Order order |
| M1003 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:63 | startPreparingPickup | String email, Long id |
| M1004 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:65 | markReadyForPickup | String email, Long id |
| M1005 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:67 | completePickup | String email, Long id, String code |
| M1006 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:69 | perform | String email, Long id, PickupAction action, String code |
| M1007 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:102 | assignedIds | String email |
| M1008 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:107 | requirePickup | Order order |
| M1009 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:113 | paymentAllows | Order order |
| M1010 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:117 | alreadyCollected | Order order |
| M1011 | src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java:121 | nextAction | Order order |
| M1012 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:78 | searchProducts | ProductSearchCriteria criteria, Pageable pageable |
| M1013 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:93 | getActiveProducts | Pageable pageable |
| M1014 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:98 | getProduct | Long id |
| M1015 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:105 | getProductImages | Long productId |
| M1016 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:112 | getPrimaryImageUrls | Collection<Long> productIds |
| M1017 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:122 | countVisibleProductsByBrand |  |
| M1018 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:131 | getActiveCategories |  |
| M1019 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:137 | getActiveBrands |  |
| M1020 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:145 | getAllCategories |  |
| M1021 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:150 | getCategory | Long id |
| M1022 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:156 | createCategory | CategoryRequest request |
| M1023 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:162 | createCategory | CategoryRequest request, MultipartFile file |
| M1024 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:186 | updateCategory | Long id, CategoryRequest request |
| M1025 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:192 | updateCategory | Long id, CategoryRequest request, MultipartFile file |
| M1026 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:220 | uploadCategoryImage | Long id, MultipartFile file |
| M1027 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:241 | removeCategoryImage | Long id |
| M1028 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:253 | apply | Category category, String name, CategoryRequest request |
| M1029 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:262 | getAllBrands |  |
| M1030 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:267 | getBrand | Long id |
| M1031 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:273 | createBrand | BrandRequest request |
| M1032 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:285 | updateBrand | Long id, BrandRequest request |
| M1033 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:295 | apply | Brand brand, String name, BrandRequest request |
| M1034 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:303 | uploadBrandLogo | Long id, MultipartFile file |
| M1035 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:323 | removeBrandLogo | Long id |
| M1036 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:336 | searchAllProducts | String keyword, Pageable pageable |
| M1037 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:349 | searchAllProducts | String keyword, Long categoryId, Long brandId,
                                                         ProductStatus status, Pageable pageable |
| M1038 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:365 | getAssignableProducts |  |
| M1039 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:371 | getProductForAdmin | Long id |
| M1040 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:377 | createProduct | ProductRequest request |
| M1041 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:389 | updateProduct | Long id, ProductRequest request |
| M1042 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:399 | apply | Product product, String sku, ProductRequest request |
| M1043 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:426 | normalizeSku | String sku |
| M1044 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:434 | addProductImage | Long productId, MultipartFile file |
| M1045 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:456 | setPrimaryProductImage | Long productId, Long imageId |
| M1046 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:464 | deleteProductImage | Long productId, Long imageId |
| M1047 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:481 | category | Long id |
| M1048 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:486 | brand | Long id |
| M1049 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:491 | product | Long id |
| M1050 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:496 | image | Long productId, Long imageId |
| M1051 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:501 | deleteQuietly | String publicId |
| M1052 | src/main/java/com/oneshop/service/impl/ProductServiceImpl.java:509 | trimToNull | String value |
| M1053 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:57 | submit | String email, ReviewRequest request, Long orderId |
| M1054 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:100 | getProductReviews | Long productId |
| M1055 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:111 | canReview | String email, Long productId |
| M1056 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:116 | getProductReviewItems | String email, Long productId |
| M1057 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:129 | getOrderReviewItems | String email, Long orderId |
| M1058 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:136 | reviewItems | Long userId, List<OrderItem> received |
| M1059 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:148 | getStaffReviews | int page |
| M1060 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:155 | getStaffReview | Long id |
| M1061 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:165 | reply | Long reviewId, String text |
| M1062 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:169 | requireCustomer | String email |
| M1063 | src/main/java/com/oneshop/service/impl/ReviewServiceImpl.java:174 | notFound |  |
| M1064 | src/main/java/com/oneshop/service/impl/StoreAllocationServiceImpl.java:40 | preview | Collection<AllocationDemand> demands, FulfillmentType type, Long pickupStoreId,                                     String shippingAddress |
| M1065 | src/main/java/com/oneshop/service/impl/StoreAllocationServiceImpl.java:52 | pickupStoresCovering | Collection<AllocationDemand> demands |
| M1066 | src/main/java/com/oneshop/service/impl/StoreAllocationServiceImpl.java:69 | allocateForUpdate | Collection<AllocationDemand> demands, FulfillmentType type,                                               Long pickupStoreId, String shippingAddress |
| M1067 | src/main/java/com/oneshop/service/impl/StoreAllocationServiceImpl.java:91 | allocate | Collection<AllocationDemand> demands, List<StoreProduct> candidates,                                       FulfillmentType type, Long pickupStoreId, String shippingAddress |
| M1068 | src/main/java/com/oneshop/service/impl/StoreAllocationServiceImpl.java:107 | servesFulfillment | Store store, FulfillmentType type, String shippingAddress |
| M1069 | src/main/java/com/oneshop/service/impl/StoreAllocationServiceImpl.java:114 | productIds | Collection<AllocationDemand> demands |
| M1070 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:74 | getChainCatalog | ProductSearchCriteria criteria, Pageable pageable |
| M1071 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:90 | getRandomSellable | Long selectedStoreId, int limit, long seed |
| M1072 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:129 | pick | List<Long> candidates, int limit, long seed |
| M1073 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:136 | getStoreCatalog | Long storeId, ProductSearchCriteria criteria, Pageable pageable |
| M1074 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:150 | getProductDetail | Long productId, Long selectedStoreId |
| M1075 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:168 | searchStoreProducts | Long storeId, Long productId,
                                                                com.oneshop.entity.ActiveStatus status,
                                                                Pageable pageable |
| M1076 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:176 | searchStoreProducts | Long storeId, Long productId,
                                                                com.oneshop.entity.ActiveStatus status, String q,
                                                                Pageable pageable |
| M1077 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:195 | searchInventory | Long storeId, String q, String stockLevel, String sort,
                                                            Pageable pageable |
| M1078 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:216 | inventorySummary | Long storeId |
| M1079 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:226 | containsLike | String q |
| M1080 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:235 | getStoreProduct | Long id |
| M1081 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:241 | createStoreProduct | StoreProductRequest request, String adminEmail |
| M1082 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:270 | updateStoreProduct | Long id, StoreProductRequest request, String adminEmail |
| M1083 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:280 | storeProduct | Long id |
| M1084 | src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java:285 | noteOr | String note, String fallback |
| M1085 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:37 | getActiveStores |  |
| M1086 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:44 | getStore | Long id |
| M1087 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:51 | findStores | String provinceCity, String area |
| M1088 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:61 | getProvinceCities |  |
| M1089 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:67 | getAreas | String provinceCity |
| M1090 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:73 | matches | String filter, String value |
| M1091 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:78 | findSelectableStore | Long storeId |
| M1092 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:86 | requireSelectableStore | Long storeId |
| M1093 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:94 | getAllStores |  |
| M1094 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:101 | searchStores | String q, String area,
                                                                             ActiveStatus status, int page |
| M1095 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:122 | containsLike | String q |
| M1096 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:132 | createStore | StoreRequest request |
| M1097 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:144 | updateStore | Long id, StoreRequest request |
| M1098 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:154 | apply | Store store, String code, StoreRequest request |
| M1099 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:167 | store | Long id |
| M1100 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:175 | getAssignedStores | String staffEmail |
| M1101 | src/main/java/com/oneshop/service/impl/StoreServiceImpl.java:185 | requireAssignedStore | String staffEmail, Long storeId |
| M1102 | src/main/java/com/oneshop/service/OrderTransitionPolicy.java:21 | canCustomerCancel | OrderStatus status, FulfillmentType type, PaymentMethod method,
                                      OrderPaymentStatus payment |
| M1103 | src/main/java/com/oneshop/service/OrderTransitionPolicy.java:47 | canTransition | OrderStatus current, OrderStatus target, FulfillmentType fulfillment |
| M1104 | src/main/java/com/oneshop/service/OrderTransitionPolicy.java:53 | canTransition | OrderStatus current, OrderStatus target, FulfillmentType fulfillment, Cause cause |
| M1105 | src/main/java/com/oneshop/service/OrderViewService.java:37 | fulfillmentParts | Order order, boolean showCustomerCode |
| M1106 | src/main/java/com/oneshop/service/OrderViewService.java:57 | detail | Order order, boolean showCustomerCode, PickupAction action |
| M1107 | src/main/java/com/oneshop/service/OrderViewService.java:68 | summary | Order o, boolean showCustomerCode, PickupAction action |
| M1108 | src/main/java/com/oneshop/service/OrderViewService.java:81 | multiStoreLabel | Order o |
| M1109 | src/main/java/com/oneshop/service/OrderViewService.java:87 | historyNote | Order order, String note, boolean showCustomerCode |
| M1110 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:98 | getCases | int page |
| M1111 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:107 | getCases | PaymentCaseCategory view, String search, int page |
| M1112 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:116 | getCases | PaymentCaseCategory view, String search,
                                                    java.time.LocalDate from, java.time.LocalDate to, int page |
| M1113 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:143 | getCaseCounts |  |
| M1114 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:186 | getCase | Long orderId |
| M1115 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:218 | recheck | Long orderId |
| M1116 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:232 | settleGateway | Long orderId, boolean collected |
| M1117 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:258 | retryRefund | Long orderId |
| M1118 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:287 | ledgerOpen | Long orderId |
| M1119 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:292 | auditWhenUnchanged | Order order, User admin, String tag, PaymentOutcome outcome |
| M1120 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:300 | lockOnline | Long orderId |
| M1121 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:305 | requireOnline | Order order |
| M1122 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:313 | currentAdmin |  |
| M1123 | src/main/java/com/oneshop/service/payment/AdminPaymentService.java:323 | summary | Order order |
| M1124 | src/main/java/com/oneshop/service/payment/CustomerOrderCancellationService.java:56 | cancel | User customer, Long orderId |
| M1125 | src/main/java/com/oneshop/service/payment/CustomerOrderCancellationService.java:117 | notFound |  |
| M1126 | src/main/java/com/oneshop/service/payment/DefaultRefundSimulator.java:11 | simulate | Payment collected |
| M1127 | src/main/java/com/oneshop/service/payment/GatewayVerification.java:12 | GatewayVerification | Result result, String reference, LocalDateTime capturedAt |
| M1128 | src/main/java/com/oneshop/service/payment/GatewayVerification.java:25 | unknown | String reference |
| M1129 | src/main/java/com/oneshop/service/payment/GatewayVerification.java:29 | conclusive |  |
| M1130 | src/main/java/com/oneshop/service/payment/MockLedger.java:31 | newReference |  |
| M1131 | src/main/java/com/oneshop/service/payment/MockLedger.java:36 | reference | String code |
| M1132 | src/main/java/com/oneshop/service/payment/MockLedger.java:43 | inDoubt | String code |
| M1133 | src/main/java/com/oneshop/service/payment/MockLedger.java:47 | withDoubt | String code |
| M1134 | src/main/java/com/oneshop/service/payment/MockLedger.java:52 | stateOf | Payment payment |
| M1135 | src/main/java/com/oneshop/service/payment/MockPaymentGateway.java:39 | enabled |  |
| M1136 | src/main/java/com/oneshop/service/payment/MockPaymentGateway.java:44 | verify | Order order, List<Payment> attempts |
| M1137 | src/main/java/com/oneshop/service/payment/MockPaymentGateway.java:80 | capture | Payment attempt, LocalDateTime at |
| M1138 | src/main/java/com/oneshop/service/payment/MockPaymentGateway.java:90 | decline | Payment attempt |
| M1139 | src/main/java/com/oneshop/service/payment/MockPaymentGateway.java:101 | markInDoubt | Payment attempt, LocalDateTime submittedAt |
| M1140 | src/main/java/com/oneshop/service/payment/MockPaymentGateway.java:110 | settleInDoubt | Payment attempt, boolean collected |
| M1141 | src/main/java/com/oneshop/service/payment/OrderCancellationService.java:65 | cancelAndRestore | Order lockedOrder, String stockNote, String historyNote, User actor,
                                  OrderPaymentStatus paymentStatusAfter |
| M1142 | src/main/java/com/oneshop/service/payment/OrderRefundViewService.java:51 | forCustomer | String customerEmail, Long orderId |
| M1143 | src/main/java/com/oneshop/service/payment/OrderRefundViewService.java:59 | forAdmin | Long orderId |
| M1144 | src/main/java/com/oneshop/service/payment/OrderRefundViewService.java:64 | summary | Order order, boolean withActors |
| M1145 | src/main/java/com/oneshop/service/payment/OrderRefundViewService.java:91 | actor | PaymentRefund refund |
| M1146 | src/main/java/com/oneshop/service/payment/OrderRefundViewService.java:95 | label | PaymentRefundComponent component |
| M1147 | src/main/java/com/oneshop/service/payment/OrderStatusWriter.java:48 | write | Order lockedOrder, OrderStatus target, User actor, String note |
| M1148 | src/main/java/com/oneshop/service/payment/OrderStatusWriter.java:73 | writeResolved | Order lockedOrder, OrderStatus target, User actor, String note |
| M1149 | src/main/java/com/oneshop/service/payment/OrderStatusWriter.java:90 | audit | Order lockedOrder, User actor, String note |
| M1150 | src/main/java/com/oneshop/service/payment/OrderStatusWriter.java:101 | abbreviate | String note |
| M1151 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:62 | Result | Status status, BigDecimal goods, BigDecimal shipping, boolean fullyRefunded |
| M1152 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:63 | total |  |
| M1153 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:84 | Preview | BigDecimal goods, BigDecimal shipping |
| M1154 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:87 | preview | Order order, OrderFulfillment part, List<OrderFulfillment> parts |
| M1155 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:98 | refundUndeliverable | Order lockedOrder, OrderFulfillment part, List<OrderFulfillment> parts, User actor,                                       String reason |
| M1156 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:127 | retryFailed | Order lockedOrder |
| M1157 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:153 | shippingFee | Order order |
| M1158 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:157 | goodsValue | OrderFulfillment part |
| M1159 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:162 | collectedPayment | Order order |
| M1160 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:173 | record | Order order, Payment collected, OrderFulfillment part, PaymentRefundComponent component,                            BigDecimal amount, String reason, User actor |
| M1161 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:189 | simulate | PaymentRefund row |
| M1162 | src/main/java/com/oneshop/service/payment/PartialRefundService.java:206 | closeWhenFullyRefunded | Order order, Payment collected, String reason |
| M1163 | src/main/java/com/oneshop/service/payment/PaymentCaseCategory.java:38 | key |  |
| M1164 | src/main/java/com/oneshop/service/payment/PaymentCaseCategory.java:42 | label |  |
| M1165 | src/main/java/com/oneshop/service/payment/PaymentCaseCategory.java:47 | fromKey | String value |
| M1166 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:80 | simulate | String customerEmail, Long orderId, Long paymentId, SimulatedResult scenario |
| M1167 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:135 | reconcile | Long orderId, Trigger trigger |
| M1168 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:146 | reconcileLocked | Order order, Trigger trigger, User actor |
| M1169 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:216 | confirm | Order order, Payment row, LocalDateTime at, User actor, Trigger trigger,
                                    GatewayVerification verification |
| M1170 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:232 | requireMatchingCollection | Order order, Payment row |
| M1171 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:240 | cancelForLatePayment | Order order, Payment row, LocalDateTime at, User actor,
                                                 Trigger trigger, GatewayVerification verification,
                                                 LocalDateTime deadline |
| M1172 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:259 | lateOnCancelled | Order order, List<Payment> rows, GatewayVerification verification,
                                            User actor, Trigger trigger |
| M1173 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:286 | recordLateCollection | Order order, Payment attempt, LocalDateTime at, User actor,
                                                 Trigger trigger |
| M1174 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:314 | currentVerification | Order lockedOrder |
| M1175 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:321 | verify | Order order, List<Payment> rows |
| M1176 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:331 | lockOwnOrder | String email, Long orderId |
| M1177 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:340 | rowOf | List<Payment> rows, String reference |
| M1178 | src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:348 | notFound |  |
| M1179 | src/main/java/com/oneshop/service/payment/PaymentTimeoutService.java:30 | ScanResult | int examined, int changed, int failed |
| M1180 | src/main/java/com/oneshop/service/payment/PaymentTimeoutService.java:46 | scan |  |
| M1181 | src/main/java/com/oneshop/service/payment/PaymentTimeoutService.java:79 | changes | PaymentOutcome outcome |
| M1182 | src/main/java/com/oneshop/service/payment/PaymentTimeoutWorker.java:28 | run |  |
| M1183 | src/main/java/com/oneshop/service/payment/RefundService.java:67 | simulate | Order lockedOrder, String reason |
| M1184 | src/main/java/com/oneshop/service/payment/RefundService.java:116 | writeLedger | Order order, Payment row |
| M1185 | src/main/java/com/oneshop/service/payment/RefundService.java:134 | hasUnfinishedRefund | Long orderId |
| M1186 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:32 | paymentDeadline | Order order |
| M1187 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:44 | persistedCreatedAt | Order order |
| M1188 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:49 | reconciliationDeadline | Order order |
| M1189 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:54 | now |  |
| M1190 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:59 | expiredBefore |  |
| M1191 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:64 | isPayableNow | Order order |
| M1192 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:73 | isExpired | Order order |
| M1193 | src/main/java/com/oneshop/service/PaymentDeadlinePolicy.java:83 | allowsFulfillmentWork | Order order |
| M1194 | src/main/java/com/oneshop/service/PickupCodeService.java:17 | generate |  |
| M1195 | src/main/java/com/oneshop/service/PickupCodeService.java:23 | verify | String supplied, String stored |
| M1196 | src/main/java/com/oneshop/service/review/ReviewContent.java:9 | comment | String text |
| M1197 | src/main/java/com/oneshop/service/review/ReviewContent.java:13 | reply | String text |
| M1198 | src/main/java/com/oneshop/service/review/ReviewContent.java:17 | normalize | String text, String field, boolean required |
| M1199 | src/main/java/com/oneshop/service/review/ReviewReadMapper.java:15 | reply | Review r |
| M1200 | src/main/java/com/oneshop/service/review/ReviewReadMapper.java:19 | repliedAt | Review r |
| M1201 | src/main/java/com/oneshop/service/review/ReviewReadMapper.java:23 | storeName | Review r |
| M1202 | src/main/java/com/oneshop/service/review/ReviewReadMapper.java:28 | publicReview | Review r |
| M1203 | src/main/java/com/oneshop/service/review/ReviewReadMapper.java:33 | staffReview | Review r |
| M1204 | src/main/java/com/oneshop/service/review/ReviewReadMapper.java:42 | adminReview | Review r |
| M1205 | src/main/java/com/oneshop/service/review/ReviewReplyService.java:33 | reply | Long id, String text |
| M1206 | src/main/java/com/oneshop/service/review/ReviewReplyService.java:49 | requireScope | Review review, StaffStoreScopeService scope |
| M1207 | src/main/java/com/oneshop/service/review/ReviewSource.java:15 | consistent | OrderItem item |
| M1208 | src/main/java/com/oneshop/service/review/ReviewSource.java:26 | received | OrderItem item |
| M1209 | src/main/java/com/oneshop/service/review/ReviewSource.java:33 | verified | Review review |
| M1210 | src/main/java/com/oneshop/service/search/TextSearch.java:22 | words | String q |
| M1211 | src/main/java/com/oneshop/service/search/TextSearch.java:30 | normalize | String text |
| M1212 | src/main/java/com/oneshop/service/search/TextSearch.java:38 | matchesAll | List<String> words, String... fields |
| M1213 | src/main/java/com/oneshop/service/search/TextSearch.java:50 | slice | List<T> all, Pageable pageable |
| M1214 | src/main/java/com/oneshop/service/StaffInventoryService.java:39 | StockStats | long total, long low, long out, int lowThreshold |
| M1215 | src/main/java/com/oneshop/service/StaffInventoryService.java:55 | getStock | int page |
| M1216 | src/main/java/com/oneshop/service/StaffInventoryService.java:64 | getStock | int page, String q, String level, String sort |
| M1217 | src/main/java/com/oneshop/service/StaffInventoryService.java:88 | stockStats |  |
| M1218 | src/main/java/com/oneshop/service/StaffInventoryService.java:102 | staffOptions |  |
| M1219 | src/main/java/com/oneshop/service/StaffInventoryService.java:120 | getMovementLog | int page, String q, InventoryMovementType type,                                                              Long staffId, LocalDate from, LocalDate to |
| M1220 | src/main/java/com/oneshop/service/StaffInventoryService.java:143 | toLogView | InventoryMovement m |
| M1221 | src/main/java/com/oneshop/service/StaffInventoryService.java:155 | likePattern | String q |
| M1222 | src/main/java/com/oneshop/service/StaffInventoryService.java:164 | cleanLevel | String level |
| M1223 | src/main/java/com/oneshop/service/StaffInventoryService.java:171 | getStoreProduct | Long id |
| M1224 | src/main/java/com/oneshop/service/StaffInventoryService.java:173 | getHistory | Long id, int page |
| M1225 | src/main/java/com/oneshop/service/StaffInventoryService.java:187 | adjust | Long id, @NotNull @Valid StockAdjustRequest request |
| M1226 | src/main/java/com/oneshop/service/StaffOperationsService.java:48 | getDashboard |  |
| M1227 | src/main/java/com/oneshop/service/StaffOperationsService.java:81 | getOrders | int page |
| M1228 | src/main/java/com/oneshop/service/StaffOperationsService.java:85 | getOrder | Long orderId |
| M1229 | src/main/java/com/oneshop/service/StaffOperationsService.java:93 | getPickupQueue | int page |
| M1230 | src/main/java/com/oneshop/service/StaffOperationsService.java:101 | performDelivery | Long orderId, DeliveryAction action |
| M1231 | src/main/java/com/oneshop/service/StaffOperationsService.java:112 | performPickup | Long orderId, PickupAction action, String code |
| M1232 | src/main/java/com/oneshop/service/StaffOperationsService.java:121 | lockedStaffEmail | Long id |
| M1233 | src/main/java/com/oneshop/service/StaffOperationsService.java:136 | readOrders | Collection<Long> assignedIds, int page, int size |
| M1234 | src/main/java/com/oneshop/service/StaffStoreScopeService.java:38 | getAssignedStores |  |
| M1235 | src/main/java/com/oneshop/service/StaffStoreScopeService.java:52 | getAssignedStoreIds |  |
| M1236 | src/main/java/com/oneshop/service/StaffStoreScopeService.java:57 | requireAssignedStore | Long storeId |
| M1237 | src/main/java/com/oneshop/service/StaffStoreScopeService.java:62 | requireOrder | Long orderId |
| M1238 | src/main/java/com/oneshop/service/StaffStoreScopeService.java:70 | requireStoreProduct | Long storeProductId |

## UI actions and decision candidates

Every U/B ID is in `evidence/inventory.json` with file, line and exact source. No candidate is assumed exercised from a page HTTP 200. Semantic deduplication and condition/decision mapping remain open.

## Runtime reconciliation

| ID | HTTP | Path | Controller | Method |
|---|---|---|---|---|
| R001 | GET | / | com.oneshop.controller.client.HomeController | home |
| R002 | GET | /account | com.oneshop.controller.client.AccountController | profile |
| R003 | POST | /account | com.oneshop.controller.client.AccountController | updateProfile |
| R004 | GET | /admin/brands/{id}/edit | com.oneshop.controller.admin.AdminBrandController | edit |
| R005 | POST | /admin/brands/{id}/logo/delete | com.oneshop.controller.admin.AdminBrandController | removeLogo |
| R006 | POST | /admin/brands/{id}/logo | com.oneshop.controller.admin.AdminBrandController | uploadLogo |
| R007 | POST | /admin/brands/{id} | com.oneshop.controller.admin.AdminBrandController | update |
| R008 | GET | /admin/brands | com.oneshop.controller.admin.AdminBrandController | list |
| R009 | POST | /admin/brands | com.oneshop.controller.admin.AdminBrandController | create |
| R010 | GET | /admin/categories/{id}/edit | com.oneshop.controller.admin.AdminCategoryController | edit |
| R011 | POST | /admin/categories/{id}/image/delete | com.oneshop.controller.admin.AdminCategoryController | removeImage |
| R012 | POST | /admin/categories/{id}/image | com.oneshop.controller.admin.AdminCategoryController | uploadImage |
| R013 | POST | /admin/categories/{id} | com.oneshop.controller.admin.AdminCategoryController | update |
| R014 | GET | /admin/categories | com.oneshop.controller.admin.AdminCategoryController | list |
| R015 | POST | /admin/categories | com.oneshop.controller.admin.AdminCategoryController | create |
| R016 | GET | /admin/inventory | com.oneshop.controller.admin.AdminInventoryController | list |
| R017 | GET | /admin/orders/{id} | com.oneshop.controller.admin.AdminOrderController | detail |
| R018 | POST | /admin/orders/{orderId}/fulfillments/{fulfillmentId}/redeliver | com.oneshop.controller.admin.AdminFulfillmentExceptionController | redeliver |
| R019 | POST | /admin/orders/{orderId}/fulfillments/{fulfillmentId}/undeliverable | com.oneshop.controller.admin.AdminFulfillmentExceptionController | undeliverable |
| R020 | GET | /admin/orders | com.oneshop.controller.admin.AdminOrderController | list |
| R021 | POST | /admin/payments/{orderId:\d+}/gateway | com.oneshop.controller.admin.AdminPaymentController | gateway |
| R022 | POST | /admin/payments/{orderId:\d+}/recheck | com.oneshop.controller.admin.AdminPaymentController | recheck |
| R023 | POST | /admin/payments/{orderId:\d+}/refund | com.oneshop.controller.admin.AdminPaymentController | refund |
| R024 | GET | /admin/payments/{orderId:\d+} | com.oneshop.controller.admin.AdminPaymentController | detail |
| R025 | GET | /admin/payments | com.oneshop.controller.admin.AdminPaymentController | list |
| R026 | GET | /admin/products/new | com.oneshop.controller.admin.AdminProductController | createForm |
| R027 | GET | /admin/products/suggest | com.oneshop.controller.admin.AdminProductController | suggest |
| R028 | GET | /admin/products/{id}/edit | com.oneshop.controller.admin.AdminProductController | editForm |
| R029 | POST | /admin/products/{id}/images/{imageId}/delete | com.oneshop.controller.admin.AdminProductController | deleteImage |
| R030 | POST | /admin/products/{id}/images/{imageId}/primary | com.oneshop.controller.admin.AdminProductController | setPrimaryImage |
| R031 | POST | /admin/products/{id}/images | com.oneshop.controller.admin.AdminProductController | uploadImage |
| R032 | POST | /admin/products/{id} | com.oneshop.controller.admin.AdminProductController | update |
| R033 | GET | /admin/products | com.oneshop.controller.admin.AdminProductController | list |
| R034 | POST | /admin/products | com.oneshop.controller.admin.AdminProductController | create |
| R035 | GET | /admin/reviews/suggest | com.oneshop.controller.admin.AdminReviewController | suggest |
| R036 | POST | /admin/reviews/{id}/reply | com.oneshop.controller.admin.AdminReviewController | reply |
| R037 | POST | /admin/reviews/{id}/status | com.oneshop.controller.admin.AdminReviewController | status |
| R038 | GET | /admin/reviews | com.oneshop.controller.admin.AdminReviewController | list |
| R039 | GET | /admin/staff-assignments/suggest | com.oneshop.controller.admin.AdminStaffAssignmentController | suggest |
| R040 | POST | /admin/staff-assignments/transfer | com.oneshop.controller.admin.AdminStaffAssignmentController | transfer |
| R041 | POST | /admin/staff-assignments/{id}/status | com.oneshop.controller.admin.AdminStaffAssignmentController | status |
| R042 | GET | /admin/staff-assignments | com.oneshop.controller.admin.AdminStaffAssignmentController | list |
| R043 | POST | /admin/staff-assignments | com.oneshop.controller.admin.AdminStaffAssignmentController | assign |
| R044 | GET | /admin/store-products/new | com.oneshop.controller.admin.AdminStoreProductController | createForm |
| R045 | GET | /admin/store-products/{id}/edit | com.oneshop.controller.admin.AdminStoreProductController | editForm |
| R046 | POST | /admin/store-products/{id} | com.oneshop.controller.admin.AdminStoreProductController | update |
| R047 | GET | /admin/store-products | com.oneshop.controller.admin.AdminStoreProductController | list |
| R048 | POST | /admin/store-products | com.oneshop.controller.admin.AdminStoreProductController | create |
| R049 | GET | /admin/stores/new | com.oneshop.controller.admin.AdminStoreController | createForm |
| R050 | GET | /admin/stores/suggest | com.oneshop.controller.admin.AdminStoreController | suggest |
| R051 | GET | /admin/stores/{id}/edit | com.oneshop.controller.admin.AdminStoreController | editForm |
| R052 | POST | /admin/stores/{id} | com.oneshop.controller.admin.AdminStoreController | update |
| R053 | GET | /admin/stores | com.oneshop.controller.admin.AdminStoreController | list |
| R054 | POST | /admin/stores | com.oneshop.controller.admin.AdminStoreController | create |
| R055 | GET | /admin/users/new | com.oneshop.controller.admin.AdminUserController | createForm |
| R056 | GET | /admin/users/suggest | com.oneshop.controller.admin.AdminUserController | suggest |
| R057 | GET | /admin/users/{id}/edit | com.oneshop.controller.admin.AdminUserController | editForm |
| R058 | POST | /admin/users/{id} | com.oneshop.controller.admin.AdminUserController | update |
| R059 | GET | /admin/users | com.oneshop.controller.admin.AdminUserController | list |
| R060 | POST | /admin/users | com.oneshop.controller.admin.AdminUserController | create |
| R061 | GET | /admin | com.oneshop.controller.admin.AdminDashboardController | dashboard |
| R062 | POST | /api/auth/login | com.oneshop.controller.api.AuthApiController | login |
| R063 | POST | /api/auth/register | com.oneshop.controller.api.AuthApiController | register |
| R064 | POST | /cart/items/{cartItemId}/delete | com.oneshop.controller.client.CartController | remove |
| R065 | POST | /cart/items/{cartItemId} | com.oneshop.controller.client.CartController | update |
| R066 | POST | /cart/items | com.oneshop.controller.client.CartController | add |
| R067 | GET | /cart | com.oneshop.controller.client.CartController | view |
| R068 | GET | /checkout/{checkoutId} | com.oneshop.controller.client.CheckoutController | result |
| R069 | GET | /checkout | com.oneshop.controller.client.CheckoutController | form |
| R070 | POST | /checkout | com.oneshop.controller.client.CheckoutController | placeOrder |
| R071 | GET | /decorators/admin | com.oneshop.controller.web.DecoratorController | admin |
| R072 | GET | /decorators/client | com.oneshop.controller.web.DecoratorController | client |
| R073 | GET | /decorators/staff | com.oneshop.controller.web.DecoratorController | staff |
| R074 | GET | /error | org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController | error |
| R075 | GET | /error | org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController | errorHtml |
| R076 | GET | /health | com.oneshop.controller.api.HealthController | health |
| R077 | GET | /image-credits | com.oneshop.controller.client.ImageCreditsController | imageCredits |
| R078 | GET | /login | com.oneshop.controller.client.AuthController | loginForm |
| R079 | POST | /login | com.oneshop.controller.client.AuthController | login |
| R080 | POST | /logout | SecurityFilterChain | logout |
| R081 | POST | /orders/{orderId}/cancel | com.oneshop.controller.client.CustomerOrderController | cancel |
| R082 | POST | /orders/{orderId}/payments/attempts | com.oneshop.controller.client.PaymentController | start |
| R083 | POST | /orders/{orderId}/payments/{paymentId}/failure | com.oneshop.controller.client.PaymentController | failure |
| R084 | POST | /orders/{orderId}/payments/{paymentId}/simulate | com.oneshop.controller.client.PaymentController | simulate |
| R085 | POST | /orders/{orderId}/payments/{paymentId}/success | com.oneshop.controller.client.PaymentController | success |
| R086 | GET | /orders/{orderId}/payments | com.oneshop.controller.client.PaymentController | view |
| R087 | GET | /orders/{orderId} | com.oneshop.controller.client.CustomerOrderController | detail |
| R088 | GET | /orders | com.oneshop.controller.client.CustomerOrderController | list |
| R089 | GET | /products/suggest | com.oneshop.controller.client.ProductController | suggest |
| R090 | GET | /products/{id} | com.oneshop.controller.client.ProductController | detail |
| R091 | GET | /products | com.oneshop.controller.client.ProductController | list |
| R092 | GET | /register | com.oneshop.controller.client.AuthController | registerForm |
| R093 | POST | /register | com.oneshop.controller.client.AuthController | register |
| R094 | POST | /reviews | com.oneshop.controller.client.ReviewController | submit |
| R095 | GET | /staff/ | com.oneshop.controller.staff.StaffDashboardController | landing |
| R096 | GET | /staff/dashboard | com.oneshop.controller.staff.StaffDashboardController | dashboard |
| R097 | GET | /staff/fulfillments/delivery | com.oneshop.controller.staff.StaffFulfillmentController | deliveryQueue |
| R098 | GET | /staff/fulfillments/pickup | com.oneshop.controller.staff.StaffFulfillmentController | pickupQueue |
| R099 | POST | /staff/fulfillments/{fulfillmentId:\d+}/{actionPath} | com.oneshop.controller.staff.StaffFulfillmentController | act |
| R100 | GET | /staff/fulfillments/{fulfillmentId:\d+} | com.oneshop.controller.staff.StaffFulfillmentController | detail |
| R101 | GET | /staff/fulfillments | com.oneshop.controller.staff.StaffFulfillmentController | queue |
| R102 | GET | /staff/inventory-history/{id} | com.oneshop.controller.staff.StaffStockController | history |
| R103 | GET | /staff/inventory-history | com.oneshop.controller.staff.StaffStockController | historySelection |
| R104 | GET | /staff/orders/delivery | com.oneshop.controller.staff.StaffFulfillmentController | legacyDelivery |
| R105 | GET | /staff/orders/pickup | com.oneshop.controller.staff.StaffFulfillmentController | legacyPickup |
| R106 | GET | /staff/orders/{orderId:\d+} | com.oneshop.controller.staff.StaffFulfillmentController | legacyOrder |
| R107 | GET | /staff/orders | com.oneshop.controller.staff.StaffFulfillmentController | queue |
| R108 | GET | /staff/pickup | com.oneshop.controller.staff.StaffFulfillmentController | legacyPickup |
| R109 | POST | /staff/reviews/{id}/reply | com.oneshop.controller.staff.StaffReviewController | reply |
| R110 | GET | /staff/reviews/{id} | com.oneshop.controller.staff.StaffReviewController | detail |
| R111 | GET | /staff/reviews | com.oneshop.controller.staff.StaffReviewController | list |
| R112 | GET | /staff/stock/{id}/adjust | com.oneshop.controller.staff.StaffStockController | adjustForm |
| R113 | POST | /staff/stock/{id}/adjust | com.oneshop.controller.staff.StaffStockController | adjust |
| R114 | GET | /staff/stock | com.oneshop.controller.staff.StaffStockController | stock |
| R115 | GET | /staff | com.oneshop.controller.staff.StaffDashboardController | landing |
| R116 | POST | /stores/clear | com.oneshop.controller.client.StoreController | clear |
| R117 | POST | /stores/select | com.oneshop.controller.client.StoreController | select |
| R118 | GET | /stores | com.oneshop.controller.client.StoreController | finder |

## State / enum declarations

### DeliveryServiceAreaPolicy — src/main/java/com/oneshop/config/CheckoutProperties.java:23

```java
/** Fail-closed switch: DELIVERY checkout is refused. */
        UNDECIDED,
        /** Approved: every ACTIVE Store with {@code delivery_enabled} serves every valid address in Vietnam. */
        ALL_DELIVERY_ENABLED_STORES
```

### ShippingFeePolicy — src/main/java/com/oneshop/config/CheckoutProperties.java:31

```java
/** Fail-closed switch: DELIVERY checkout is refused. */
        UNDECIDED,
        /** Approved: {@link #getShippingFeePerOrder()} per DELIVERY Order, whatever its number of Fulfillments. */
        FIXED_PER_ORDER
```

### GatewayAnswer — src/main/java/com/oneshop/controller/admin/AdminPaymentController.java:84

```java
COLLECTED, NOT_COLLECTED
```

### CartItemStatus — src/main/java/com/oneshop/dto/response/CartItemStatus.java:7

```java
/** On sale at its Store and the Store has enough stock for the quantity in the cart. */
    AVAILABLE,

    /** On sale, but the Store has less stock than the quantity in the cart: the customer must lower it. */
    INSUFFICIENT_STOCK,

    /** On sale, but the Store has none left. */
    OUT_OF_STOCK,

    /** No longer on sale at that Store (StoreProduct, Store, Product, Category or Brand not ACTIVE). */
    UNAVAILABLE
```

### DeliveryAction — src/main/java/com/oneshop/dto/response/DeliveryAction.java:6

```java
PREPARE(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "prepare", "Bắt đầu chuẩn bị"),
    PACK(OrderStatus.PREPARING, OrderStatus.PACKED, "pack", "Đã đóng gói"),
    SHIP(OrderStatus.PACKED, OrderStatus.SHIPPING, "ship", "Bắt đầu giao hàng"),
    COMPLETE(OrderStatus.SHIPPING, OrderStatus.COMPLETED, "complete", "Xác nhận giao thành công");

    private final OrderStatus expectedStatus;
    private final OrderStatus targetStatus;
    private final String path;
    private final String label;

    DeliveryAction(OrderStatus expectedStatus, OrderStatus targetStatus, String path, String label) {
        this.expectedStatus = expectedStatus;
        this.targetStatus = targetStatus;
        this.path = path;
        this.label = label;
    }

    public OrderStatus getExpectedStatus() { return expectedStatus; }
    public OrderStatus getTargetStatus() { return targetStatus; }
    public String getPath() { return path; }
    public String getLabel() { return label; }
```

### PickupAction — src/main/java/com/oneshop/dto/response/PickupAction.java:5

```java
PREPARE(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "prepare", "Bắt đầu chuẩn bị"),
    READY(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, "ready", "Sẵn sàng nhận hàng"),
    COMPLETE(OrderStatus.READY_FOR_PICKUP, OrderStatus.COMPLETED, "complete", "Xác nhận khách đã nhận hàng");

    private final OrderStatus expectedStatus;
    private final OrderStatus targetStatus;
    private final String path;
    private final String label;
    PickupAction(OrderStatus expected, OrderStatus target, String path, String label) {
        this.expectedStatus = expected; this.targetStatus = target; this.path = path; this.label = label;
    }
    public OrderStatus getExpectedStatus() { return expectedStatus; }
    public OrderStatus getTargetStatus() { return targetStatus; }
    public String getPath() { return path; }
    public String getLabel() { return label; }
```

### ActiveStatus — src/main/java/com/oneshop/entity/ActiveStatus.java:4

```java
ACTIVE,
    INACTIVE
```

### CheckoutStatus — src/main/java/com/oneshop/entity/CheckoutStatus.java:3

```java
CREATED,
    PARTIAL,
    COMPLETED,
    CANCELLED
```

### FulfillmentType — src/main/java/com/oneshop/entity/FulfillmentType.java:3

```java
DELIVERY,
    STORE_PICKUP
```

### InventoryMovementType — src/main/java/com/oneshop/entity/InventoryMovementType.java:3

```java
ORDER,
    CANCEL_ORDER,
    STOCK_ADJUST
```

### OrderFulfillmentStatus — src/main/java/com/oneshop/entity/OrderFulfillmentStatus.java:4

```java
CONFIRMED,
    PREPARING,
    PACKED,
    SHIPPING,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED,
    FAILED
```

### OrderPaymentStatus — src/main/java/com/oneshop/entity/OrderPaymentStatus.java:4

```java
UNPAID,
    PAID,
    FAILED,
    REFUNDED
```

### OrderStatus — src/main/java/com/oneshop/entity/OrderStatus.java:4

```java
PENDING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    PREPARING,
    PACKED,
    SHIPPING,
    READY_FOR_PICKUP,
    PARTIALLY_COMPLETED,
    COMPLETED,
    CANCELLED,
    NEEDS_ATTENTION
```

### PaymentMethod — src/main/java/com/oneshop/entity/PaymentMethod.java:3

```java
ONLINE,
    COD,
    PAY_AT_STORE
```

### PaymentRefundComponent — src/main/java/com/oneshop/entity/PaymentRefundComponent.java:4

```java
/** the value of the goods of one Fulfillment that could not be delivered */
    GOODS,
    /** the one shipping fee of the Order, refunded only when nothing at all was delivered */
    SHIPPING,
    /** the whole collected amount at once (customer cancel before preparation; legacy refunds) */
    WHOLE_ORDER
```

### PaymentRefundStatus — src/main/java/com/oneshop/entity/PaymentRefundStatus.java:4

```java
NONE,
    PENDING_SIMULATION,
    SIMULATED_REFUNDED,
    SIMULATION_FAILED
```

### PaymentStatus — src/main/java/com/oneshop/entity/PaymentStatus.java:4

```java
PENDING,
    SUCCESS,
    FAILED
```

### ProductStatus — src/main/java/com/oneshop/entity/ProductStatus.java:3

```java
ACTIVE,
    INACTIVE,
    HIDDEN
```

### RoleName — src/main/java/com/oneshop/entity/RoleName.java:4

```java
CUSTOMER,
    STAFF,
    ADMIN
```

### VisibilityStatus — src/main/java/com/oneshop/entity/VisibilityStatus.java:4

```java
ACTIVE,
    HIDDEN
```

### AllocationFailure — src/main/java/com/oneshop/service/allocation/AllocationFailure.java:4

```java
NO_DEMAND,
    /** The eligible Stores together do not have the requested quantity of at least one SKU. */
    OUT_OF_STOCK,
    /** The chosen pickup Store cannot supply the whole cart. */
    INSUFFICIENT_AT_PICKUP_STORE,
    /** No Store is eligible for this fulfillment type. */
    NO_ELIGIBLE_STORE
```

### GoodsStatus — src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:93

```java
/** back at the Store and fit to sell: the lines go back into its stock */
        RETURNED("Hàng đã về chi nhánh"),
        /** lost / not recoverable: stock is not changed */
        LOST("Hàng thất lạc"),
        /** cannot be confirmed yet: the exception stays open, nothing is decided, nothing changes */
        UNCONFIRMED("Chưa xác nhận được tình trạng hàng");

        private final String label;

        GoodsStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static GoodsStatus fromKey(String key) {
            if (key == null) {
                return null;
            }
            for (GoodsStatus status : values()) {
                if (status.name().equalsIgnoreCase(key.trim())) {
                    return status;
                }
            }
            return null;
        }
```

### Kind — src/main/java/com/oneshop/service/fulfillment/AdminFulfillmentExceptionService.java:124

```java
REDELIVERY_OPENED, UNDELIVERABLE_RECORDED, AWAITING_VERIFICATION
```

### FulfillmentAction — src/main/java/com/oneshop/service/fulfillment/FulfillmentAction.java:13

```java
PREPARE("prepare", null, OrderFulfillmentStatus.CONFIRMED, OrderFulfillmentStatus.PREPARING, "Bắt đầu chuẩn bị"),
    PACK("pack", FulfillmentType.DELIVERY, OrderFulfillmentStatus.PREPARING, OrderFulfillmentStatus.PACKED, "Đã đóng gói"),
    SHIP("ship", FulfillmentType.DELIVERY, OrderFulfillmentStatus.PACKED, OrderFulfillmentStatus.SHIPPING, "Bàn giao vận chuyển"),
    MARK_READY("ready-for-pickup", FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.PREPARING, OrderFulfillmentStatus.READY_FOR_PICKUP,
            "Sẵn sàng cho khách nhận"),
    COMPLETE_DELIVERY("complete-delivery", FulfillmentType.DELIVERY, OrderFulfillmentStatus.SHIPPING, OrderFulfillmentStatus.COMPLETED,
            "Xác nhận giao thành công"),
    COMPLETE_PICKUP("complete-pickup", FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.READY_FOR_PICKUP,
            OrderFulfillmentStatus.COMPLETED, "Xác nhận khách đã nhận hàng"),
    FAIL_DELIVERY("fail-delivery", FulfillmentType.DELIVERY, OrderFulfillmentStatus.SHIPPING, OrderFulfillmentStatus.FAILED,
            "Giao hàng không thành công"),
    FAIL_PICKUP("fail-pickup", FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.READY_FOR_PICKUP, OrderFulfillmentStatus.FAILED,
            "Khách không nhận hàng");

    private final String path;
    private final FulfillmentType type;
    private final OrderFulfillmentStatus from;
    private final OrderFulfillmentStatus to;
    private final String label;

    FulfillmentAction(String path, FulfillmentType type, OrderFulfillmentStatus from, OrderFulfillmentStatus to,
                      String label) {
        this.path = path;
        this.type = type;
        this.from = from;
        this.to = to;
        this.label = label;
    }

    /** The URL segment of the POST that applies this action. */
    public String getPath() {
        return path;
    }

    /** The action behind a URL segment, or {@code null} for an unknown one. */
    public static FulfillmentAction fromPath(String path) {
        for (FulfillmentAction action : values()) {
            if (action.path.equals(path)) {
                return action;
            }
        }
        return null;
    }

    /** {@code null} means both types. */
    public FulfillmentType getType() {
        return type;
    }

    public OrderFulfillmentStatus getFrom() {
        return from;
    }

    public OrderFulfillmentStatus getTo() {
        return to;
    }

    public String getLabel() {
        return label;
    }

    public boolean appliesTo(FulfillmentType fulfillmentType) {
        return type == null || type == fulfillmentType;
    }

    /** A failure needs a written reason; it never cancels, restocks or refunds. */
    public boolean isFailure() {
        return to == OrderFulfillmentStatus.FAILED;
    }

    public boolean isCompletion() {
        return to == OrderFulfillmentStatus.COMPLETED;
    }
```

### Cause — src/main/java/com/oneshop/service/OrderTransitionPolicy.java:34

```java
FULFILLMENT, PAYMENT_SUCCESS, PAYMENT_FAILURE
```

### Result — src/main/java/com/oneshop/service/payment/CustomerOrderCancellationService.java:32

```java
CANCELLED,
        /** cancelled and the paid ONLINE amount refunded (simulated) */
        CANCELLED_AND_REFUNDED,
        /** cancelled; the simulated refund failed and stays retryable by an administrator */
        CANCELLED_REFUND_FAILED,
        /** the Order was already cancelled (a repeated request, or a payment rule cancelled it first) */
        ALREADY_CANCELLED
```

### Result — src/main/java/com/oneshop/service/payment/GatewayVerification.java:14

```java

```

### LedgerState — src/main/java/com/oneshop/service/payment/LedgerState.java:7

```java
/** Created by checkout; the customer has not completed any payment. */
    AWAITING,
    /** The customer submitted a payment and the gateway could not confirm it (UNKNOWN). Money may have moved. */
    IN_DOUBT,
    /** The gateway reports money collected at a server time; the Order has not been told yet. */
    CAPTURED,
    /** The gateway reports that nothing was collected (verified FAILED, or the attempt was closed unpaid). */
    DECLINED,
    /** The collection was applied to the Order ({@code payments.status = SUCCESS}). */
    SETTLED
```

### Status — src/main/java/com/oneshop/service/payment/PartialRefundService.java:48

```java
/** nothing was collected online: there is nothing to refund */
        NOT_APPLICABLE,
        /** refunded now (goods, and the shipping fee when nothing was delivered) */
        REFUNDED,
        /** the simulation failed; recorded and retryable, nothing moved */
        SIMULATION_FAILED
```

### PaymentCaseCategory — src/main/java/com/oneshop/service/payment/PaymentCaseCategory.java:21

```java
ATTENTION("attention", "Cần xử lý"),
    RECONCILING("reconciling", "Chờ đối soát"),
    WAITING("waiting", "Đang chờ thanh toán"),
    PAID("paid", "Đã thanh toán"),
    REFUNDED("refunded", "Đã hoàn tiền (mô phỏng)"),
    FAILED("failed", "Thất bại / đã hủy"),
    OTHER("other", "Khác");

    private final String key;
    private final String label;

    PaymentCaseCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** The list filter named by a request parameter (named by its key); {@code null} for "all" or anything unknown. */
    public static PaymentCaseCategory fromKey(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values()).filter(c -> c != OTHER && c.key.equalsIgnoreCase(value.trim())).findFirst()
                .orElse(null);
    }
```

### PaymentOutcome — src/main/java/com/oneshop/service/payment/PaymentOutcome.java:4

```java

```

### SimulatedResult — src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:49

```java
SUCCESS, FAILED, UNKNOWN
```

### Trigger — src/main/java/com/oneshop/service/payment/PaymentReconciliationService.java:51

```java
CUSTOMER_CALLBACK, TIMEOUT_SCAN, CUSTOMER_CANCEL, ADMIN_RECHECK, ADMIN_GATEWAY
```

### Result — src/main/java/com/oneshop/service/payment/RefundService.java:42

```java
/** at least one collected payment was refunded now */
        REFUNDED,
        /** everything collected was already refunded: no side effect */
        ALREADY_REFUNDED,
        /** the simulation failed; recorded as SIMULATION_FAILED, a retry is allowed */
        SIMULATION_FAILED,
        /** no verified ONLINE collection on this Order */
        NOT_APPLICABLE
```

## Runtime model

V3 được xác nhận cả source, 23-table SQL Server schema và bảy Chrome business journeys. Product.price là giá online; CartItem.product; ONLINE một Order/N Fulfillments; COD split cần consent; shipping 30.000/Order, pickup miễn phí. Legacy StoreProduct price/store selectors vẫn tồn tại nhưng không quyết định nguồn hàng V3. Staff hiện chỉ một ACTIVE Store (filtered unique index); test cũ multi-assignment mâu thuẫn policy hiện hành. Không áp kỳ vọng V2 lên V3 hoặc ngược lại.

## Missing functionality / external boundaries

Không tìm thấy SMTP/forgot-password/address CRUD controllers/cổng ngân hàng thật; địa chỉ chỉ đọc trên profile và dùng cho checkout. Không tự đánh runtime BUG cho các chức năng chưa triển khai. README trỏ một số đặc tả và runbook không có trong checkout: SPEC GAP về tài liệu.

## Detailed function records

`evidence/function-flow-records.json` chứa đủ FunctionID, Module, Actor, EntryPoint, Controller, Service, Repository, Input, Preconditions, BusinessRules, Branches, SideEffects, FailurePaths, RelatedUI, TestIDs và Coverage cho 115 khai báo route. Dependency links là phân tích tĩnh từ field/interface, không giả làm runtime trace; mỗi điểm chưa reconcile được ghi rõ. `inventory.json` giữ tất cả methods, U/B IDs, file hashes và state declarations.

