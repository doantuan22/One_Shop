package com.oneshop;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.DeliveryFulfillmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Tomcat, SiteMesh and security, with mocked domain for isolated UI/CSRF/route verification. */
class DeliveryWebIntegrationTest extends AbstractIntegrationTest {
    private static final String STAFF = "staff@oneshop.test";
    private static final String PATH = "/staff/orders/delivery";
    @MockitoBean private DeliveryFulfillmentService delivery;
    @Autowired private JwtService jwt;
    @Autowired private CustomUserDetailsService users;

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
        return "ONESHOP_TOKEN=" + jwt.generateToken(users.loadUserByUsername(STAFF));
    }

    private DeliveryOrderResponse row(OrderStatus status, DeliveryAction action) {
        return new DeliveryOrderResponse(10L, 1L, "OneShop Thủ Đức", LocalDateTime.now(), FulfillmentType.DELIVERY,
                PaymentMethod.COD, OrderPaymentStatus.UNPAID, status, "Người nhận", "0912345678", "1 Đường thử",
                new BigDecimal("175000"), action);
    }

    private void detail(DeliveryOrderResponse row) {
        when(delivery.getDeliveryOrder(STAFF, 10L)).thenReturn(new DeliveryOrderDetailResponse(row,
                List.of(new OrderItemResponse("Snapshot <script>alert(1)</script>", new BigDecimal("175000"), 1, new BigDecimal("175000"))),
                List.of(new OrderHistoryResponse(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "Nhân viên A", "Đã nhận xử lý", LocalDateTime.now())), List.of()));
    }

    private HttpResponse<String> postAs(String cookie, String suffix, String body) throws Exception {
        var page = get("/login");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return post(PATH + suffix, "application/x-www-form-urlencoded", body + "&_csrf=" + csrf.group(1),
                "Cookie", cookie + "; " + tokenCookie);
    }

    @Test
    void listAndDetailUseStaffLayoutSnapshotsAuditAndSafeHtml() throws Exception {
        String staff = cookie(RoleName.STAFF);
        when(delivery.getDeliveryOrders(STAFF)).thenReturn(List.of(row(OrderStatus.CONFIRMED, DeliveryAction.PREPARE)));
        var page = get(PATH + "?storeId=999", "Cookie", staff);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-layout=\"staff\"", "OneShop Thủ Đức", "175.000 ₫", "Người nhận", PATH + "/10/prepare",
                "href=\"" + PATH + "\"");
        verify(delivery).getDeliveryOrders(STAFF);
        detail(row(OrderStatus.PREPARING, DeliveryAction.PACK));
        page = get(PATH + "/10", "Cookie", staff);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("0912345678", "1 Đường thử", "Nhân viên A", "Đã nhận xử lý", "Snapshot &lt;script&gt;")
                .doesNotContain("<script>alert(1)</script>", "name=\"targetStatus\"", "name=\"amount\"", "name=\"storeId\"");
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void showsOnlyFixedNextActionAndCsrf(DeliveryAction action) throws Exception {
        detail(row(action.getExpectedStatus(), action));
        var page = get(PATH + "/10", "Cookie", cookie(RoleName.STAFF));
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains(PATH + "/10/" + action.getPath(), action.getLabel(), "name=\"_csrf\"");
        for (var other : DeliveryAction.values()) if (other != action) assertThat(page.body()).doesNotContain(PATH + "/10/" + other.getPath());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "CONFIRMED", "COMPLETED", "CANCELLED"})
    void blockedPaymentOrTerminalHasNoAction(OrderStatus status) throws Exception {
        detail(row(status, null));
        var page = get(PATH + "/10", "Cookie", cookie(RoleName.STAFF));
        assertThat(page.statusCode()).isEqualTo(200);
        for (var action : DeliveryAction.values()) assertThat(page.body()).doesNotContain(PATH + "/10/" + action.getPath());
    }

    @Test
    void fixedPostsUsePrincipalAndIgnoreClientStoreActorStatusAmountMethodCode() throws Exception {
        String staff = cookie(RoleName.STAFF);
        String forged = "storeId=999&userId=1&staffEmail=admin&targetStatus=COMPLETED&expectedStatus=SHIPPING&amount=1&method=PAY_AT_STORE&transactionCode=HACK";
        for (var action : DeliveryAction.values()) assertThat(postAs(staff, "/10/" + action.getPath(), forged).statusCode()).isEqualTo(302);
        verify(delivery).startPreparingDelivery(STAFF, 10L);
        verify(delivery).markDeliveryPacked(STAFF, 10L);
        verify(delivery).markDeliveryShipping(STAFF, 10L);
        verify(delivery).completeDelivery(STAFF, 10L);
    }

    @Test
    void everyWriteRequiresCsrfEvenWithBearer() throws Exception {
        String staff = cookie(RoleName.STAFF);
        String bearer = "Bearer " + staff.substring("ONESHOP_TOKEN=".length());
        for (var action : DeliveryAction.values()) {
            String path = PATH + "/10/" + action.getPath();
            assertThat(post(path, "application/x-www-form-urlencoded", "", "Cookie", staff).statusCode()).isEqualTo(403);
            assertThat(post(path, "application/x-www-form-urlencoded", "", "Authorization", bearer).statusCode()).isEqualTo(403);
            for (String encodedRoot : List.of("/%73taff/orders/delivery", "/staff/%6frders/delivery", "/staff/orders/%64elivery")) {
                assertThat(post(encodedRoot + "/10/" + action.getPath(), "application/x-www-form-urlencoded", "", "Authorization", bearer).statusCode())
                        .as("CSRF is required for the same route with percent-encoded path segments").isEqualTo(403);
            }
        }
        verifyNoInteractions(delivery);
    }

    @Test
    void customerAdminGuestAndStaffWithoutAssignmentCannotReadOrMutate() throws Exception {
        for (var role : List.of(RoleName.CUSTOMER, RoleName.ADMIN)) {
            String account = cookie(role);
            assertThat(get(PATH, "Cookie", account).statusCode()).isEqualTo(403);
            assertThat(get(PATH + "/10", "Cookie", account).statusCode()).isEqualTo(403);
            for (var action : DeliveryAction.values()) assertThat(postAs(account, "/10/" + action.getPath(), "").statusCode()).isEqualTo(403);
        }
        assertThat(get(PATH).statusCode()).isEqualTo(302);
        for (var action : DeliveryAction.values()) assertThat(postAs("", "/10/" + action.getPath(), "").statusCode()).isEqualTo(302);
        String staff = cookie(RoleName.STAFF);
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of());
        assertThat(get(PATH, "Cookie", staff).statusCode()).isEqualTo(403);
        for (var action : DeliveryAction.values()) assertThat(postAs(staff, "/10/" + action.getPath(), "").statusCode()).isEqualTo(403);
        verifyNoInteractions(delivery);
    }

    @Test
    void getCannotMutateAndThereIsNoGenericTransitionEndpoint() throws Exception {
        String staff = cookie(RoleName.STAFF);
        for (var action : DeliveryAction.values()) assertThat(get(PATH + "/10/" + action.getPath(), "Cookie", staff).statusCode()).isEqualTo(405);
        assertThat(postAs(staff, "/10/transition", "targetStatus=COMPLETED").statusCode()).isEqualTo(404);
        verifyNoInteractions(delivery);
    }

    @Test
    void unknownOrderScopeAndDatabaseFailuresShowFriendlyErrorsWithoutSql() throws Exception {
        String staff = cookie(RoleName.STAFF);
        when(delivery.getDeliveryOrder(STAFF, 10L)).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn giao hàng"));
        assertThat(get(PATH + "/10", "Cookie", staff).statusCode()).isEqualTo(404);
        doThrow(new AccessDeniedException("PRIVATE scope")).when(delivery).startPreparingDelivery(STAFF, 10L);
        var page = postAs(staff, "/10/prepare", "");
        assertThat(page.statusCode()).isEqualTo(403);
        assertThat(page.body()).contains("không được phân công").doesNotContain("PRIVATE scope");
        doThrow(new CannotAcquireLockException("SECRET SQL STACK")).when(delivery).startPreparingDelivery(STAFF, 10L);
        page = postAs(staff, "/10/prepare", "");
        assertThat(page.statusCode()).isEqualTo(409);
        assertThat(page.body()).contains("đang được xử lý").doesNotContain("SECRET SQL STACK");
        doThrow(new DataIntegrityViolationException("PRIVATE CONSTRAINT")).when(delivery).completeDelivery(STAFF, 10L);
        page = postAs(staff, "/10/complete", "");
        assertThat(page.statusCode()).isEqualTo(409);
        assertThat(page.body()).contains("Không thể lưu kết quả").doesNotContain("PRIVATE CONSTRAINT");
    }
}
