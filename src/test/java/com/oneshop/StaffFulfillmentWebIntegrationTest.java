package com.oneshop;

import com.oneshop.dto.response.FulfillmentDetailResponse;
import com.oneshop.dto.response.FulfillmentHistoryResponse;
import com.oneshop.dto.response.FulfillmentQueueItemResponse;
import com.oneshop.dto.response.OrderItemResponse;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.fulfillment.FulfillmentAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * V3-BE-04 Staff web layer with the real Tomcat, security, SiteMesh and Thymeleaf, and the Fulfillment service mocked
 * (so UI, CSRF, binding and error mapping are verified in isolation; real data is covered by
 * {@link FulfillmentHttpDatabaseIntegrationTest}).
 *
 * <p>These cases replace the Staff cases of the V2 {@code DeliveryWebIntegrationTest} and
 * {@code PickupWebIntegrationTest}, whose routes (order-level {@code /staff/orders/delivery/**},
 * {@code /staff/orders/pickup/**}) no longer exist. The mapping is in {@code V3_BE04_TEST_RESULTS.md}.
 */
class StaffFulfillmentWebIntegrationTest extends AbstractIntegrationTest {

    private static final String STAFF = "staff@oneshop.test";
    private static final String ROOT = "/staff/fulfillments";
    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String FORGED = "storeId=999&store_id=2&userId=1&staffEmail=admin&orderStatus=COMPLETED"
            + "&status=COMPLETED&targetStatus=COMPLETED&paymentStatus=PAID&amount=1&transactionCode=HACK";

    @Autowired
    private JwtService jwt;
    @Autowired
    private CustomUserDetailsService users;

    @BeforeEach
    void scope() {
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of(new StoreResponse(1L, "TD", "OneShop Thủ Đức",
                "1 Đường thử", "HCM", "Thủ Đức", null, null, true, true, ActiveStatus.ACTIVE)));
    }

    private String cookie(RoleName role) {
        User user = new User();
        user.setEmail(STAFF);
        user.setPasswordHash("x");
        user.setRole(new Role(role));
        when(userRepository.findByEmail(STAFF)).thenReturn(Optional.of(user));
        withId(user);
        return "ONESHOP_TOKEN=" + jwt.generateToken(users.loadUserByUsername(STAFF));
    }

    private static List<FulfillmentAction> actionsFor(FulfillmentType type, OrderFulfillmentStatus status) {
        return java.util.Arrays.stream(FulfillmentAction.values())
                .filter(a -> a.appliesTo(type) && a.getFrom() == status).toList();
    }

    private FulfillmentQueueItemResponse row(FulfillmentType type, OrderFulfillmentStatus status,
                                             List<FulfillmentAction> actions, String blocked) {
        return new FulfillmentQueueItemResponse(10L, 77L, 1L, "OneShop Thủ Đức", type, status, OrderStatus.CONFIRMED,
                type == FulfillmentType.DELIVERY ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE,
                OrderPaymentStatus.UNPAID, "Nguyễn Khách", "Người nhận", 1, 2, LocalDateTime.now(), actions, blocked);
    }

    private FulfillmentDetailResponse detail(FulfillmentQueueItemResponse summary, boolean codeIssued) {
        return new FulfillmentDetailResponse(summary, "0912345678",
                summary.fulfillmentType() == FulfillmentType.DELIVERY ? "1 Đường thử" : null, null, codeIssued,
                new BigDecimal("175000"), null, null, null, null, null, null, null, null, null,
                List.of(new OrderItemResponse("Snapshot <script>alert(1)</script>", new BigDecimal("175000"), 1,
                        new BigDecimal("175000"))),
                List.of(new FulfillmentHistoryResponse(OrderFulfillmentStatus.CONFIRMED,
                        OrderFulfillmentStatus.PREPARING, "Nhân viên A", "Bắt đầu chuẩn bị", LocalDateTime.now())));
    }

    private HttpResponse<String> postAs(String cookie, String path, String body) throws Exception {
        HttpResponse<String> page = get("/login");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(path, FORM, body + (body.isEmpty() ? "" : "&") + "_csrf=" + csrf.group(1),
                "Cookie", cookie + "; " + tokenCookie);
    }

    /**
     * Returns the page's visible main content. Leak checks must not scan header attributes such as the random CSRF
     * token, whose Base64 value can incidentally contain marker substrings.
     */
    private static String userVisibleContent(String html) {
        Matcher main = Pattern.compile("(?s)<main\\b[^>]*>(.*?)</main>").matcher(html);
        assertThat(main.find()).as("page has a main content area").isTrue();
        return main.group(1);
    }

    // ================================================================== tests

    @Test
    @DisplayName("queue and detail use the Staff layout, the Fulfillment's own lines and escape snapshot text")
    void queueAndDetailUseStaffLayoutAndSafeHtml() throws Exception {
        String staff = cookie(RoleName.STAFF);
        FulfillmentQueueItemResponse row = row(FulfillmentType.DELIVERY, OrderFulfillmentStatus.CONFIRMED,
                List.of(FulfillmentAction.PREPARE), null);
        when(staffFulfillmentService.getQueue(any(), anyInt())).thenReturn(new PageImpl<>(List.of(row)));

        HttpResponse<String> queue = get(ROOT + "?storeId=999&status=bogus", "Cookie", staff);
        // a status that is not an enum value is a bad request; a Store id is only a filter checked by the service
        assertThat(queue.statusCode()).isEqualTo(400);
        queue = get(ROOT + "?storeId=1", "Cookie", staff);
        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(queue.body()).contains("data-layout=\"staff\"", "data-fulfillment-id=\"10\"", "data-order-id=\"77\"",
                "OneShop Thủ Đức", "Nguyễn Khách", "Bắt đầu chuẩn bị", "id=\"fulfillments-body\"");

        when(staffFulfillmentService.getFulfillment(10L)).thenReturn(detail(row, false));
        HttpResponse<String> page = get(ROOT + "/10", "Cookie", staff);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("0912345678", "1 Đường thử", "Nhân viên A", "Snapshot &lt;script&gt;",
                "Phần giao #10")
                .doesNotContain("<script>alert(1)</script>", "name=\"targetStatus\"", "name=\"amount\"",
                        "name=\"storeId\"", "name=\"orderStatus\"");
    }

    @ParameterizedTest
    @EnumSource(FulfillmentAction.class)
    @DisplayName("the detail offers exactly the actions of the state, each with CSRF, and no other step")
    void showsOnlyTheStatesActionsWithCsrf(FulfillmentAction action) throws Exception {
        List<FulfillmentAction> offered = actionsFor(action.getType() == null ? FulfillmentType.DELIVERY
                : action.getType(), action.getFrom());
        FulfillmentType type = action.getType() == null ? FulfillmentType.DELIVERY : action.getType();
        when(staffFulfillmentService.getFulfillment(10L)).thenReturn(detail(row(type, action.getFrom(), offered, null),
                action.getFrom() == OrderFulfillmentStatus.READY_FOR_PICKUP));

        HttpResponse<String> page = get(ROOT + "/10", "Cookie", cookie(RoleName.STAFF));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains(ROOT + "/10/" + action.getPath(), action.getLabel(), "name=\"_csrf\"");
        for (FulfillmentAction other : FulfillmentAction.values()) {
            if (!offered.contains(other)) {
                assertThat(page.body()).doesNotContain(ROOT + "/10/" + other.getPath() + "\"");
            }
        }
        if (offered.contains(FulfillmentAction.COMPLETE_PICKUP)) {
            assertThat(page.body()).contains("name=\"pickupCode\"");
        } else {
            assertThat(page.body()).doesNotContain("name=\"pickupCode\"");
        }
        if (action.isFailure()) {
            assertThat(page.body()).contains("name=\"reason\"");
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderFulfillmentStatus.class, names = {"COMPLETED", "CANCELLED", "FAILED"})
    @DisplayName("a finished Fulfillment has no action at all")
    void terminalHasNoAction(OrderFulfillmentStatus status) throws Exception {
        when(staffFulfillmentService.getFulfillment(10L))
                .thenReturn(detail(row(FulfillmentType.DELIVERY, status, List.of(), null), false));

        HttpResponse<String> page = get(ROOT + "/10", "Cookie", cookie(RoleName.STAFF));

        assertThat(page.statusCode()).isEqualTo(200);
        for (FulfillmentAction action : FulfillmentAction.values()) {
            assertThat(page.body()).doesNotContain(ROOT + "/10/" + action.getPath());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "NEEDS_ATTENTION", "CANCELLED"})
    @DisplayName("an Order held by the payment gate shows the reason and no action")
    void heldOrderHasNoAction(OrderStatus held) throws Exception {
        FulfillmentQueueItemResponse base = row(FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.CONFIRMED,
                List.of(), "Đơn hàng đang chờ thanh toán; chưa thể chuẩn bị hàng.");
        FulfillmentQueueItemResponse summary = new FulfillmentQueueItemResponse(base.fulfillmentId(), base.orderId(),
                base.storeId(), base.storeName(), base.fulfillmentType(), base.status(), held, PaymentMethod.ONLINE,
                OrderPaymentStatus.UNPAID, base.customerName(), base.receiverName(), 1, 1, base.assignedAt(),
                List.of(), base.blockedReason());
        when(staffFulfillmentService.getFulfillment(10L)).thenReturn(detail(summary, false));

        HttpResponse<String> page = get(ROOT + "/10", "Cookie", cookie(RoleName.STAFF));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("id=\"fulfillment-blocked\"", "chưa thể chuẩn bị hàng");
        for (FulfillmentAction action : FulfillmentAction.values()) {
            assertThat(page.body()).doesNotContain(ROOT + "/10/" + action.getPath());
        }
    }

    @Test
    @DisplayName("the staff pickup page never prints a pickup code, only that one was issued")
    void pickupCodeIsNeverPrinted() throws Exception {
        FulfillmentQueueItemResponse row = row(FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.READY_FOR_PICKUP,
                actionsFor(FulfillmentType.STORE_PICKUP, OrderFulfillmentStatus.READY_FOR_PICKUP), null);
        when(staffFulfillmentService.getFulfillment(10L)).thenReturn(detail(row, true));

        HttpResponse<String> page = get(ROOT + "/10", "Cookie", cookie(RoleName.STAFF));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Đã tạo", "name=\"pickupCode\"").doesNotContain("id=\"pickup-code\"");
    }

    @Test
    @DisplayName("fixed POSTs use the principal; only tracking code, pickup code and reason are read")
    void fixedPostsBindOnlyThreeInputs() throws Exception {
        String staff = cookie(RoleName.STAFF);
        String body = FORGED + "&trackingCode=TRK-1&pickupCode=ABCD2345&reason=" + java.net.URLEncoder.encode(
                "Khách không nghe máy", java.nio.charset.StandardCharsets.UTF_8);
        for (FulfillmentAction action : FulfillmentAction.values()) {
            assertThat(postAs(staff, ROOT + "/10/" + action.getPath(), body).statusCode())
                    .as(action.name()).isEqualTo(302);
        }

        verify(staffFulfillmentService).perform(10L, FulfillmentAction.PREPARE, null);
        verify(staffFulfillmentService).perform(10L, FulfillmentAction.PACK, null);
        verify(staffFulfillmentService).perform(10L, FulfillmentAction.SHIP, "TRK-1");
        verify(staffFulfillmentService).perform(10L, FulfillmentAction.MARK_READY, null);
        verify(staffFulfillmentService).perform(10L, FulfillmentAction.COMPLETE_DELIVERY, null);
        verify(staffFulfillmentService).perform(10L, FulfillmentAction.COMPLETE_PICKUP, "ABCD2345");
        verify(staffFulfillmentService).perform(10L, FulfillmentAction.FAIL_DELIVERY, "Khách không nghe máy");
        verify(staffFulfillmentService).perform(10L, FulfillmentAction.FAIL_PICKUP, "Khách không nghe máy");
    }

    @Test
    @DisplayName("the generic handover URL is a fixed POST that passes only the pickup code")
    void handoverBindsOnlyPickupCode() throws Exception {
        assertThat(postAs(cookie(RoleName.STAFF), ROOT + "/10/handover", FORGED + "&pickupCode=ABCD2345")
                .statusCode()).isEqualTo(302);

        verify(staffFulfillmentService).handover(10L, "ABCD2345");
    }

    @Test
    @DisplayName("every write requires CSRF, even with a Bearer header and percent-encoded path segments")
    void everyWriteRequiresCsrfEvenWithBearer() throws Exception {
        String staff = cookie(RoleName.STAFF);
        String bearer = "Bearer " + staff.substring("ONESHOP_TOKEN=".length());
        for (FulfillmentAction action : FulfillmentAction.values()) {
            String path = ROOT + "/10/" + action.getPath();
            assertThat(post(path, FORM, "", "Cookie", staff).statusCode()).isEqualTo(403);
            assertThat(post(path, FORM, "", "Authorization", bearer).statusCode()).isEqualTo(403);
            assertThat(post(path, FORM, "_csrf=WRONG", "Cookie", staff + "; XSRF-TOKEN=REAL").statusCode())
                    .isEqualTo(403);
            for (String root : List.of("/%73taff/fulfillments", "/staff/%66ulfillments")) {
                assertThat(post(root + "/10/" + action.getPath(), FORM, "", "Authorization", bearer).statusCode())
                        .as("CSRF is required for percent-encoded segments of the same route").isEqualTo(403);
            }
        }
        verifyNoInteractions(staffFulfillmentService);
    }

    @Test
    @DisplayName("customer, admin, guest and Staff without an assignment can neither read nor change")
    void roleAndAssignmentBoundaries() throws Exception {
        for (RoleName role : List.of(RoleName.CUSTOMER, RoleName.ADMIN)) {
            String account = cookie(role);
            assertThat(get(ROOT, "Cookie", account).statusCode()).isEqualTo(403);
            assertThat(get(ROOT + "/10", "Cookie", account).statusCode()).isEqualTo(403);
            for (FulfillmentAction action : FulfillmentAction.values()) {
                assertThat(postAs(account, ROOT + "/10/" + action.getPath(), "").statusCode()).isEqualTo(403);
            }
        }
        assertThat(get(ROOT).statusCode()).isEqualTo(302);
        assertThat(postAs("", ROOT + "/10/prepare", "").statusCode()).isEqualTo(302);
        String staff = cookie(RoleName.STAFF);
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of());
        assertThat(get(ROOT, "Cookie", staff).statusCode()).isEqualTo(403);
        assertThat(postAs(staff, ROOT + "/10/prepare", "").statusCode()).isEqualTo(403);
        verifyNoInteractions(staffFulfillmentService);
    }

    @Test
    @DisplayName("GET cannot mutate, an unknown action does not exist and there is no generic status endpoint")
    void getCannotMutateAndNoGenericTransition() throws Exception {
        String staff = cookie(RoleName.STAFF);
        for (FulfillmentAction action : FulfillmentAction.values()) {
            assertThat(get(ROOT + "/10/" + action.getPath(), "Cookie", staff).statusCode()).isEqualTo(405);
        }
        assertThat(postAs(staff, ROOT + "/10/transition", "targetStatus=COMPLETED").statusCode()).isEqualTo(404);
        assertThat(postAs(staff, ROOT + "/10/status", "orderStatus=COMPLETED").statusCode()).isEqualTo(404);
        assertThat(postAs(staff, "/staff/orders/77/delivery/prepare", "").statusCode()).isIn(404, 405);
        verifyNoInteractions(staffFulfillmentService);
    }

    @Test
    @DisplayName("errors are friendly: 404, 403, 409 (with the business message), 400 and no SQL or code in the page")
    void friendlyErrorsWithoutLeaks() throws Exception {
        String staff = cookie(RoleName.STAFF);
        when(staffFulfillmentService.getFulfillment(10L))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy phần giao hàng."));
        assertThat(get(ROOT + "/10", "Cookie", staff).statusCode()).isEqualTo(404);

        doThrow(new AccessDeniedException("PRIVATE scope")).when(staffFulfillmentService)
                .perform(10L, FulfillmentAction.PREPARE, null);
        HttpResponse<String> page = postAs(staff, ROOT + "/10/prepare", "");
        assertThat(page.statusCode()).isEqualTo(403);
        assertThat(page.body()).contains("không được phân công").doesNotContain("PRIVATE scope");

        doThrow(new FulfillmentConflictException(FulfillmentConflictException.PAYMENT_PENDING,
                "Đơn hàng đang chờ thanh toán; chưa thể chuẩn bị hàng.")).when(staffFulfillmentService)
                .perform(10L, FulfillmentAction.PACK, null);
        page = postAs(staff, ROOT + "/10/pack", "");
        assertThat(page.statusCode()).isEqualTo(409);
        assertThat(page.body()).contains("chưa thể chuẩn bị hàng");

        doThrow(new BadRequestException("Mã nhận hàng không hợp lệ.")).when(staffFulfillmentService)
                .perform(10L, FulfillmentAction.COMPLETE_PICKUP, "WRONG999");
        page = postAs(staff, ROOT + "/10/complete-pickup", "pickupCode=WRONG999");
        assertThat(page.statusCode()).isEqualTo(400);
        assertThat(page.body()).doesNotContain("WRONG999");

        for (RuntimeException failure : List.of(new CannotAcquireLockException("SECRET SQL STACK ABCD2345"),
                new DataIntegrityViolationException("PRIVATE CONSTRAINT ABCD2345"))) {
            doThrow(failure).when(staffFulfillmentService).perform(10L, FulfillmentAction.SHIP, null);
            page = postAs(staff, ROOT + "/10/ship", "");
            assertThat(page.statusCode()).isEqualTo(409);
            assertThat(page.body()).contains("đang được xử lý");
            assertThat(userVisibleContent(page.body())).doesNotContain("SECRET", "PRIVATE", "ABCD2345", "SQL");
        }
    }

    @Test
    @DisplayName("V2 order URLs are read-only redirects to the Fulfillment queue")
    void legacyUrlsRedirect() throws Exception {
        String staff = cookie(RoleName.STAFF);
        HttpResponse<String> order = get("/staff/orders/77", "Cookie", staff);
        assertThat(order.statusCode()).isEqualTo(302);
        assertThat(order.headers().firstValue("Location").orElse("")).contains("/staff/fulfillments?orderId=77");
        assertThat(get("/staff/orders/delivery", "Cookie", staff).headers().firstValue("Location").orElse(""))
                .endsWith("/staff/fulfillments/delivery");
        assertThat(get("/staff/pickup", "Cookie", staff).headers().firstValue("Location").orElse(""))
                .endsWith("/staff/fulfillments/pickup");
        assertThat(get("/staff/orders/pickup", "Cookie", staff).headers().firstValue("Location").orElse(""))
                .endsWith("/staff/fulfillments/pickup");
    }

    @Test
    @DisplayName("the sidebar has the Fulfillment links and marks exactly one entry current")
    void sidebarMarksOneCurrentEntry() throws Exception {
        String staff = cookie(RoleName.STAFF);
        for (String path : List.of(ROOT, ROOT + "/delivery", ROOT + "/pickup")) {
            HttpResponse<String> page = get(path, "Cookie", staff);
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.body()).contains("href=\"/staff/fulfillments\"", "href=\"/staff/fulfillments/delivery\"",
                    "href=\"/staff/fulfillments/pickup\"");
            assertThat(page.body().split("aria-current=\"page\"", -1).length - 1).as(path).isEqualTo(1);
        }
    }
}
